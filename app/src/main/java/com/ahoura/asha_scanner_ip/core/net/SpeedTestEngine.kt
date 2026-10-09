package com.ahoura.asha_scanner_ip.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.TimeSource

enum class SpeedTestPhase { Idle, Ping, Download, Upload, Done, Stopped, Error }

data class SpeedSample(val mbps: Double)

data class BandwidthSample(
    val bytes: Long,
    val durationMs: Double,
    val bps: Double,
    val ping: Double,
)

data class HttpTiming(
    val bytes: Long,
    val wallNanos: Long,
    val ttfbNanos: Long,
    val serverMillis: Double,
)

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.Idle,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val loadedPingMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val liveMbps: Double = 0.0,
    val progress: Float = 0f,
    val downSamples: List<SpeedSample> = emptyList(),
    val upSamples: List<SpeedSample> = emptyList(),
    val bytesUsed: Long = 0L,
    val error: String? = null,
) {
    val bufferbloatMs: Double?
        get() {
            val idle = pingMs ?: return null
            val loaded = loadedPingMs ?: return null
            return max(0.0, loaded - idle)
        }

    val running: Boolean
        get() = phase == SpeedTestPhase.Ping ||
                phase == SpeedTestPhase.Download ||
                phase == SpeedTestPhase.Upload

    val hasResults: Boolean
        get() = pingMs != null || downloadMbps != null || uploadMbps != null
}

object SpeedTestMath {
    fun percentile(values: List<Double>, perc: Double = 0.5): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = (sorted.size - 1) * perc
        val rem = idx - floor(idx)
        if (rem == 0.0) return sorted[idx.toInt()]
        val lo = sorted[floor(idx).toInt()]
        val hi = sorted[ceil(idx).toInt()]
        return lo + (hi - lo) * rem
    }

    fun jitter(values: List<Double>): Double? {
        if (values.size < 2) return null
        var sum = 0.0
        for (i in 1 until values.size) sum += abs(values[i] - values[i - 1])
        return sum / (values.size - 1)
    }

    fun bandwidthBps(
        samples: List<BandwidthSample>,
        perc: Double,
        minDurationMs: Double,
    ): Double? {
        val usable = samples
            .filter { it.durationMs >= minDurationMs && it.bps > 0.0 }
            .map { it.bps }
        if (usable.isEmpty()) return null
        return percentile(usable, perc)
    }
}

data class SpeedMeasurement(
    val upload: Boolean,
    val bytes: Long,
    val count: Int,
    val bypassFinishRule: Boolean = false,
)

class SpeedTestEngine(private val socksPort: Int? = null) {
    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    @Volatile
    private var stopped = false

    private val downSamples = ArrayList<BandwidthSample>()
    private val upSamples = ArrayList<BandwidthSample>()
    private val idlePings = ArrayList<Double>()
    private val loadedPings = ArrayList<Double>()
    private var spentBytes = 0L

    private var downFinished = false
    private var upFinished = false

    companion object {
        const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes="
        const val UP_URL = "https://speed.cloudflare.com/__up"
        const val PING_COUNT = 10
        const val FINISH_REQUEST_MS = 1_200.0
        const val MIN_REQUEST_MS = 10.0
        const val BANDWIDTH_PERCENTILE = 0.9
        const val LATENCY_PERCENTILE = 0.5
        const val LOADED_PROBE_MS = 400L
        const val LOADED_MAX_POINTS = 15
        const val TOTAL_BUDGET_BYTES = 75L * 1000 * 1000 // 75 MB ceiling to respect cellular quota
        const val CONNECT_TIMEOUT = 8_000
        const val READ_TIMEOUT = 15_000
        private const val PING_READ_TIMEOUT = 4_000
        private const val SAMPLE_MS = 200L

        val SCHEDULE: List<SpeedMeasurement> = listOf(
            SpeedMeasurement(upload = false, bytes = 100_000, count = 1, bypassFinishRule = true),
            SpeedMeasurement(upload = false, bytes = 250_000, count = 3),
            SpeedMeasurement(upload = false, bytes = 1_000_000, count = 4),
            SpeedMeasurement(upload = false, bytes = 5_000_000, count = 4),
            SpeedMeasurement(upload = false, bytes = 10_000_000, count = 3),
            SpeedMeasurement(upload = true, bytes = 100_000, count = 1, bypassFinishRule = true),
            SpeedMeasurement(upload = true, bytes = 500_000, count = 3),
            SpeedMeasurement(upload = true, bytes = 2_000_000, count = 3),
            SpeedMeasurement(upload = true, bytes = 5_000_000, count = 3),
        )
    }

    fun cancel() {
        stopped = true
    }

    suspend fun run() {
        stopped = false
        downSamples.clear()
        upSamples.clear()
        idlePings.clear()
        loadedPings.clear()
        spentBytes = 0L
        downFinished = false
        upFinished = false

        try {
            _state.value = SpeedTestState(phase = SpeedTestPhase.Ping)
            measureIdleLatency()
            if (stopped) return finishStopped()
            if (idlePings.isEmpty()) throw IllegalStateException("Network unreachable or probe dropped")

            runSchedule()
            if (stopped) return finishStopped()

            _state.value = _state.value.copy(
                phase = SpeedTestPhase.Done,
                liveMbps = 0.0,
                progress = 1f,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = _state.value.copy(
                phase = SpeedTestPhase.Error,
                error = e.message ?: "Speed test failed",
            )
        } finally {
            if (_state.value.running) {
                _state.value = _state.value.copy(
                    phase = if (stopped) SpeedTestPhase.Stopped else SpeedTestPhase.Error,
                    liveMbps = 0.0,
                    progress = 1f,
                )
            }
        }
    }

    private fun finishStopped() {
        _state.value = _state.value.copy(phase = SpeedTestPhase.Stopped, liveMbps = 0.0, progress = 1f)
    }

    private suspend fun measureIdleLatency() {
        repeat(PING_COUNT) { i ->
            if (stopped) return
            val ms = probeLatency() ?: return@repeat
            idlePings.add(ms)
            _state.value = _state.value.copy(
                liveMbps = ms,
                pingMs = SpeedTestMath.percentile(idlePings, LATENCY_PERCENTILE),
                jitterMs = SpeedTestMath.jitter(idlePings),
                progress = (i + 1) / PING_COUNT.toFloat(),
            )
        }
    }

    private suspend fun probeLatency(): Double? {
        val t = runCatching {
            httpTimedTransfer(DOWN_URL + "0", socksPort, false, 0, CONNECT_TIMEOUT, PING_READ_TIMEOUT)
        }.getOrNull() ?: return null
        return max(0.0, t.ttfbNanos / 1_000_000.0 - t.serverMillis)
    }

    private suspend fun runSchedule() {
        val probeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val probeJob = probeScope.launch { loadedLatencyLoop() }
        try {
            for (m in SCHEDULE) {
                if (stopped) return
                if (finishedFor(m.upload)) continue
                if (spentBytes + m.bytes > TOTAL_BUDGET_BYTES) {
                    downFinished = true
                    upFinished = true
                    return
                }
                runRound(m)
            }
        } finally {
            probeJob.cancel()
            probeScope.cancel()
        }
    }

    private fun finishedFor(upload: Boolean) = if (upload) upFinished else downFinished

    private suspend fun runRound(m: SpeedMeasurement) {
        _state.value = _state.value.copy(
            phase = if (m.upload) SpeedTestPhase.Upload else SpeedTestPhase.Download,
            liveMbps = 0.0,
        )
        var minDuration = Double.MAX_VALUE

        for (i in 0 until m.count) {
            if (stopped) return
            if (spentBytes + m.bytes > TOTAL_BUDGET_BYTES) {
                downFinished = true
                upFinished = true
                return
            }
            val sample = runRequest(m) ?: continue
            minDuration = min(minDuration, sample.durationMs)
            publish(m.upload, sample, roundProgress = (i + 1) / m.count.toFloat())
        }

        if (!m.bypassFinishRule && minDuration != Double.MAX_VALUE && minDuration > FINISH_REQUEST_MS) {
            if (m.upload) upFinished = true else downFinished = true
        }
    }

    private suspend fun runRequest(m: SpeedMeasurement): BandwidthSample? {
        val url = if (m.upload) UP_URL else DOWN_URL + m.bytes
        var moved = 0L
        var tick = TimeSource.Monotonic.markNow()
        val timing: HttpTiming = runCatching {
            httpStreamTransfer(
                url = url,
                socksPort = socksPort,
                upload = m.upload,
                uploadBytes = m.bytes,
                connectTimeoutMs = CONNECT_TIMEOUT,
                readTimeoutMs = READ_TIMEOUT,
                onChunk = { delta ->
                    moved += delta
                    if (tick.elapsedNow().inWholeMilliseconds >= SAMPLE_MS) {
                        tick = TimeSource.Monotonic.markNow()
                        _state.value = _state.value.copy(bytesUsed = spentBytes + moved)
                    }
                    !stopped
                },
            )
        }.getOrNull() ?: run {
            spentBytes += moved
            _state.value = _state.value.copy(bytesUsed = spentBytes)
            return null
        }

        spentBytes += max(moved, timing.bytes)
        return toSample(m.upload, m.bytes, timing)
    }

    private fun toSample(upload: Boolean, requested: Long, t: HttpTiming): BandwidthSample? {
        val ttfbMs = t.ttfbNanos / 1_000_000.0
        val wallMs = t.wallNanos / 1_000_000.0
        val ping = max(0.0, ttfbMs - t.serverMillis)
        val payloadMs = max(0.0, wallMs - ttfbMs)
        val durationMs = if (upload) ttfbMs else ping + payloadMs
        if (durationMs <= 0.0) return null

        val payloadBytes = if (upload) requested.toDouble() * 1.005 else {
            (if (t.bytes > 0) t.bytes else requested).toDouble()
        }
        val bps = payloadBytes * 8.0 / (durationMs / 1000.0)
        return BandwidthSample(bytes = requested, durationMs = durationMs, bps = bps, ping = ping)
    }

    private fun publish(upload: Boolean, sample: BandwidthSample, roundProgress: Float) {
        val list = if (upload) upSamples else downSamples
        list.add(sample)

        val mbps = sample.bps / 1_000_000.0
        val agg = SpeedTestMath.bandwidthBps(list, BANDWIDTH_PERCENTILE, MIN_REQUEST_MS)
            ?.let { it / 1_000_000.0 }
        val graph = list.map { SpeedSample(it.bps / 1_000_000.0) }

        _state.value = _state.value.copy(
            liveMbps = mbps,
            progress = roundProgress,
            bytesUsed = spentBytes,
            downSamples = if (!upload) graph else _state.value.downSamples,
            upSamples = if (upload) graph else _state.value.upSamples,
            downloadMbps = if (!upload) agg ?: _state.value.downloadMbps else _state.value.downloadMbps,
            uploadMbps = if (upload) agg ?: _state.value.uploadMbps else _state.value.uploadMbps,
        )
    }

    private suspend fun CoroutineScope.loadedLatencyLoop() {
        while (isActive && !stopped) {
            delay(LOADED_PROBE_MS)
            if (stopped) return

            if (!_state.value.running || _state.value.phase == SpeedTestPhase.Ping) continue
            val ms = probeLatency() ?: continue
            loadedPings.add(ms)
            while (loadedPings.size > LOADED_MAX_POINTS) loadedPings.removeAt(0)
            _state.value = _state.value.copy(
                loadedPingMs = SpeedTestMath.percentile(loadedPings, LATENCY_PERCENTILE),
            )
        }
    }

    private suspend fun httpTimedTransfer(
        url: String,
        socksPort: Int?,
        upload: Boolean,
        uploadBytes: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpTiming = withContext(Dispatchers.IO) {
        val connection = if (socksPort != null) {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
            URL(url).openConnection(proxy) as HttpURLConnection
        } else {
            URL(url).openConnection() as HttpURLConnection
        }
        connection.apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AshaScanner-SpeedTest")
            setRequestProperty("Accept-Encoding", "identity")
        }
        var bytes = 0L
        val t0 = System.nanoTime()
        try {
            val buf = ByteArray(32 * 1024)
            if (upload) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(uploadBytes)
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.outputStream.use { os ->
                    var remaining = uploadBytes
                    while (remaining > 0) {
                        val n = minOf(buf.size, remaining)
                        os.write(buf, 0, n)
                        os.flush()
                        remaining -= n
                        bytes += n
                    }
                }
            }
            val code = connection.responseCode
            val ttfb = System.nanoTime() - t0
            if (code !in 200..299) throw IllegalStateException("HTTP $code")

            connection.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    if (!upload) bytes += n
                }
            }
            val wall = System.nanoTime() - t0
            val server = parseServerTimingMillis(connection.getHeaderField("server-timing"))
            HttpTiming(bytes = bytes, wallNanos = wall, ttfbNanos = ttfb, serverMillis = server)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private suspend fun httpStreamTransfer(
        url: String,
        socksPort: Int?,
        upload: Boolean,
        uploadBytes: Long,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        onChunk: (deltaBytes: Long) -> Boolean,
    ): HttpTiming = withContext(Dispatchers.IO) {
        val connection = if (socksPort != null) {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
            URL(url).openConnection(proxy) as HttpURLConnection
        } else {
            URL(url).openConnection() as HttpURLConnection
        }
        connection.apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AshaScanner-SpeedTest")
            setRequestProperty("Accept-Encoding", "identity")
        }

        var bytes = 0L
        val t0 = System.nanoTime()
        try {
            val buf = ByteArray(32 * 1024)
            if (upload) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(uploadBytes)
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.outputStream.use { os ->
                    while (bytes < uploadBytes) {
                        val n = minOf(buf.size.toLong(), uploadBytes - bytes).toInt()
                        os.write(buf, 0, n)
                        bytes += n
                        if (!onChunk(n.toLong())) break
                    }
                    os.flush()
                }
            }

            val code = connection.responseCode
            val ttfb = System.nanoTime() - t0
            if (code !in 200..299) throw IllegalStateException("HTTP $code")

            connection.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    if (!upload) {
                        bytes += n
                        if (!onChunk(n.toLong())) break
                    }
                }
            }
            val wall = System.nanoTime() - t0
            val server = parseServerTimingMillis(connection.getHeaderField("server-timing"))
            HttpTiming(bytes = bytes, wallNanos = wall, ttfbNanos = ttfb, serverMillis = server)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun parseServerTimingMillis(header: String?): Double {
        if (header.isNullOrBlank()) return 0.0
        val idx = header.indexOf("dur=", ignoreCase = true)
        if (idx < 0) return 0.0
        val tail = header.substring(idx + 4)
        val num = tail.takeWhile { it.isDigit() || it == '.' }
        return num.toDoubleOrNull() ?: 0.0
    }
}
