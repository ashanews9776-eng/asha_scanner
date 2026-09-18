package com.ahoura.asha_scanner_ip.core.guard

import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object ConnectionLog {
    private const val MAX_ENTRIES = 100
    private const val MAX_FILE_BYTES = 256 * 1024L
    private val entries = ArrayDeque<String>()
    private var sink: File? = null

    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val NOISE = listOf(
        "\"message\":\"updated server ",
        "\"message\":\"Memory metrics at ",
        "\"message\":\"Datastore metrics at ",
        "\"message\":\"DNS metrics at ",
        "\"message\":\"ServerEntryIterator.reset:",
        "\"message\":\"Awaited ScanServerEntries:",
        "\"message\":\"Set dial parameters for ",
        "\"message\":\"port forward failures for ",
    )

    @Synchronized
    fun bind(file: File, versionStamp: String = "") {
        sink = file
        val stampFile = File(file.parentFile, file.name + ".v")
        val previous = runCatching { stampFile.readText().trim() }.getOrDefault("")
        val stale = versionStamp.isNotEmpty() && previous != versionStamp
        if (stale || (file.exists() && file.length() > MAX_FILE_BYTES)) {
            file.delete()
            entries.clear()
        }
        if (versionStamp.isNotEmpty() && previous != versionStamp) {
            runCatching { stampFile.writeText(versionStamp) }
        }
    }

    @Synchronized
    fun record(message: String) {
        if (NOISE.any(message::contains)) return
        val line = "${stamp.format(Date())}  ${LogRedactor.redact(message)}"
        android.util.Log.i("ConnectionLog", line)
        if (entries.size == MAX_ENTRIES) entries.removeFirst()
        entries.addLast(line)
        runCatching { sink?.appendText(line + "\n") }
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        runCatching { sink?.writeText("") }
    }
}
