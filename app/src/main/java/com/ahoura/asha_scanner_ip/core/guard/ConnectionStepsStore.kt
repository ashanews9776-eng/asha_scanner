package com.ahoura.asha_scanner_ip.core.guard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ConnectionPhase {
    IDLE,
    STARTING,
    OUTER,
    INNER,
    BRIDGING,
    CONNECTED,
    FAILED,
}

enum class StepState {
    RUNNING,
    DONE,
    FAILED,
    RETRYING,
}

data class ConnectionStep(
    val text: String,
    val state: StepState,
)

data class ConnectionStepsStatus(
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val progressPercent: Int = 0,
    val detail: String = "",
    val outerLabel: String = "",
    val steps: List<ConnectionStep> = emptyList(),
) {
    companion object {
        val Idle = ConnectionStepsStatus()
    }
}

object ConnectionStepsStore {
    private val mutableStatus = MutableStateFlow(ConnectionStepsStatus.Idle)
    val status: StateFlow<ConnectionStepsStatus> = mutableStatus.asStateFlow()

    fun update(
        phase: ConnectionPhase,
        progressPercent: Int = 0,
        detail: String = "",
        outerLabel: String = mutableStatus.value.outerLabel,
    ) {
        val current = mutableStatus.value
        mutableStatus.value = ConnectionStepsStatus(
            phase = phase,
            progressPercent = progressPercent.coerceIn(0, 100),
            detail = detail,
            outerLabel = outerLabel,
            steps = current.steps,
        )
        if (detail.isNotBlank() && !detail.equals("null", ignoreCase = true)) note(detail)
        if (phase == ConnectionPhase.CONNECTED) finish(StepState.DONE)
        if (phase == ConnectionPhase.FAILED) finish(StepState.FAILED)
    }

    fun pulse(text: String) {
        val clean = text.trim().take(180)
        if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) return
        val current = mutableStatus.value
        val steps = current.steps.toMutableList()
        val last = steps.lastOrNull()
        if (last != null && last.state == StepState.RUNNING && last.text.startsWith("Scan ")) {
            if (last.text == clean) return
            steps[steps.lastIndex] = last.copy(text = clean)
            mutableStatus.value = current.copy(steps = steps)
            return
        }
        note(clean)
    }

    fun scan(text: String, outcome: String) {
        val clean = text.trim().take(180)
        if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) return
        val state = when (outcome) {
            "trying" -> StepState.RUNNING
            "accepted" -> StepState.DONE
            else -> StepState.FAILED
        }
        val current = mutableStatus.value
        val steps = current.steps.toMutableList()
        val last = steps.lastOrNull()
        if (last != null && last.state == StepState.RUNNING && last.text.startsWith("Scan ")) {
            steps[steps.lastIndex] = ConnectionStep(clean, state)
            mutableStatus.value = current.copy(steps = steps)
            return
        }
        if (last != null && last.state == StepState.RUNNING) {
            steps[steps.lastIndex] = last.copy(state = StepState.DONE)
        }
        steps += ConnectionStep(clean, state)
        mutableStatus.value = current.copy(steps = steps.takeLast(32))
    }

    fun note(text: String, state: StepState = StepState.RUNNING) {
        val clean = text.trim().take(180)
        if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) return
        val current = mutableStatus.value
        val steps = current.steps.toMutableList()
        val last = steps.lastOrNull()
        if (last != null && last.text == clean) return
        if (last != null && last.state == StepState.RUNNING) {
            steps[steps.lastIndex] = last.copy(state = StepState.DONE)
        }
        steps += ConnectionStep(clean, state)
        mutableStatus.value = current.copy(steps = steps.takeLast(32))
    }

    fun note(text: String, failed: Boolean) {
        note(text, if (failed) StepState.FAILED else StepState.RUNNING)
    }

    fun failLast(errorText: String? = null) {
        val current = mutableStatus.value
        val steps = current.steps.toMutableList()
        val last = steps.lastOrNull()
        if (last != null) {
            steps[steps.lastIndex] = if (!errorText.isNullOrBlank()) {
                ConnectionStep(errorText.trim().take(180), StepState.FAILED)
            } else {
                last.copy(state = StepState.FAILED)
            }
            mutableStatus.value = current.copy(steps = steps)
        }
    }

    private fun finish(state: StepState) {
        val current = mutableStatus.value
        val last = current.steps.lastOrNull() ?: return
        if (last.state != StepState.RUNNING) return
        val steps = current.steps.toMutableList()
        steps[steps.lastIndex] = last.copy(state = state)
        mutableStatus.value = current.copy(steps = steps)
    }

    fun reset() {
        mutableStatus.value = ConnectionStepsStatus.Idle
    }

    fun onNativeEvent(json: String) {
        val event = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (event.optString("type")) {
            "log" -> {
                val message = event.optString("message")
                if (message.isNotBlank()) {
                    stageLine(message)?.let { line ->
                        if (line.startsWith("Scan ")) {
                            scan(line, line.substringAfterLast(' '))
                        } else {
                            val isRetry = line.startsWith("Every saved") ||
                                line.startsWith("Tunnel did not") ||
                                line.startsWith("UDP/1701 did not") ||
                                line.startsWith("No usable MASQUE") ||
                                line.startsWith("Saved addresses failed") ||
                                line.contains("refused")
                            if (isRetry) {
                                note(line, StepState.RETRYING)
                            } else {
                                note(line, StepState.RUNNING)
                            }
                        }
                    }
                }
            }
            "status" -> {
                val status = event.optString("status")
                val rawDetail = event.optString("detail")
                val detail = if (rawDetail.isBlank() || rawDetail.equals("null", ignoreCase = true)) "" else rawDetail
                when (status) {
                    "connected" -> update(ConnectionPhase.CONNECTED, 100, detail.ifBlank { "Connection established" })
                    "failed" -> update(ConnectionPhase.FAILED, 0, detail.ifBlank { "Connection failed" })
                }
            }
        }
    }

    private fun scanPulse(message: String): String {
        val scanning = message.startsWith("Scanning ")
        val body = if (scanning) {
            message.removePrefix("Scanning ").substringBefore(" ")
        } else {
            message.removePrefix("Rejected ").substringBefore(":")
        }
        val why = when {
            scanning -> "trying"
            message.contains("status 400") -> "400"
            message.contains("ACCESS_DENIED") -> "access-denied"
            message.contains("CERTIFICATE") -> "certificate"
            message.contains("timeout") -> "timeout"
            message.contains("reset") -> "reset"
            message.contains("unexpected EOF") -> "eof"
            else -> "other"
        }
        return "Scan $body $why"
    }

    private fun stageLine(message: String): String? = when {
        message.startsWith("Key:") -> message.removePrefix("Key:").trim().let { "Key $it" }
        message.startsWith("Clean HTTP/2 list:") -> message
        message.startsWith("Clean HTTP/2 list is empty. Starting") -> "Clean list empty. HTTP/2 scan is starting."
        message.startsWith("Trying saved clean address") -> message
        message.startsWith("Saved clean address accepted") -> message
        message.startsWith("Saved clean address rejected") -> message
        message.startsWith("Every saved address failed. Scan was not started") -> message
        message.startsWith("Every saved address failed. Starting") -> "Saved addresses failed. HTTP/2 scan is starting."
        message.startsWith("Scanning HTTP/2") -> message
        message.startsWith("Saved clean HTTP/2 address") -> message
        message.startsWith("Scanning ") -> scanPulse(message)
        message.startsWith("Rejected ") -> scanPulse(message)
        message.startsWith("HTTP/2 selected") -> "HTTP/2 started"
        message.startsWith("MASQUE accepted") -> message
        message.startsWith("MASQUE start") -> message
        message.startsWith("Tunnel did not stay up") -> message
        message.startsWith("MASQUE: dialing known gateways on UDP/1701") -> "Trying UDP/1701"
        message.startsWith("MASQUE: no known gateway accepted UDP/1701") -> "UDP/1701 did not answer"
        message.startsWith("MASQUE: UDP/1701 did not answer") -> "UDP/1701 did not answer, trying TCP"
        message.startsWith("No usable MASQUE") -> message
        message.startsWith("MASQUE identity refused") -> "Certificate refused. Requesting a new one."
        message.startsWith("Certificate ") -> message
        message.startsWith("Certificate refused during") -> message
        message.startsWith("This certificate was refused") -> message
        message.startsWith("New certificate saved") -> message
        message.startsWith("New MASQUE certificate issued") -> "New certificate issued. Retrying the gateways."
        message.startsWith("Could not refresh the MASQUE certificate") -> message
        else -> null
    }
}
