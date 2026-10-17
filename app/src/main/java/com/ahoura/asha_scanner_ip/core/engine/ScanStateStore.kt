package com.ahoura.asha_scanner_ip.core.engine

import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ScanSnapshot(
    val config: ScanConfig,
    val healthyResults: List<ScanResult>,
    val testedCount: Int,
    val totalCount: Int,
    val remainingTasks: List<Pair<String, Int>>,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("timestamp", timestamp)
        root.put("testedCount", testedCount)
        root.put("totalCount", totalCount)

        val hrArr = JSONArray()
        for (r in healthyResults) {
            val obj = JSONObject()
            obj.put("ip", r.ip)
            obj.put("port", r.port)
            obj.put("mode", r.mode.name)
            obj.put("healthy", r.healthy)
            obj.put("attempts", r.attempts)
            obj.put("speedTested", r.speedTested)
            obj.put("throughput", r.throughputBytesPerSec)
            obj.put("tlsOk", r.tlsOk)
            obj.put("httpStatus", r.httpStatus)
            obj.put("colo", r.colo)
            obj.put("passRate", r.passRate)
            val lArr = JSONArray()
            r.latenciesMs.forEach { lArr.put(it) }
            obj.put("latencies", lArr)
            hrArr.put(obj)
        }
        root.put("healthyResults", hrArr)

        val tasksArr = JSONArray()
        for ((ip, port) in remainingTasks) {
            val tObj = JSONObject()
            tObj.put("ip", ip)
            tObj.put("port", port)
            tasksArr.put(tObj)
        }
        root.put("remainingTasks", tasksArr)

        val cfgObj = JSONObject()
        cfgObj.put("mode", config.mode.name)
        cfgObj.put("timeoutMs", config.timeoutMs)
        cfgObj.put("concurrency", config.concurrency)
        cfgObj.put("count", config.count)
        cfgObj.put("gentle", config.gentle)
        cfgObj.put("phase2Only", config.phase2Only)
        cfgObj.put("pastedTargets", config.pastedTargets)
        cfgObj.put("sniOverride", config.sniOverride)
        val pArr = JSONArray()
        config.ports.forEach { pArr.put(it) }
        cfgObj.put("ports", pArr)
        root.put("config", cfgObj)

        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ScanSnapshot? = runCatching {
            val root = JSONObject(jsonStr)
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val tested = root.optInt("testedCount", 0)
            val total = root.optInt("totalCount", 0)

            val hrList = mutableListOf<ScanResult>()
            val hrArr = root.optJSONArray("healthyResults")
            if (hrArr != null) {
                for (i in 0 until hrArr.length()) {
                    val obj = hrArr.getJSONObject(i)
                    val latencies = mutableListOf<Long>()
                    val lArr = obj.optJSONArray("latencies")
                    if (lArr != null) {
                        for (j in 0 until lArr.length()) latencies.add(lArr.getLong(j))
                    }
                    hrList.add(
                        ScanResult(
                            ip = obj.getString("ip"),
                            port = obj.getInt("port"),
                            mode = runCatching { com.ahoura.asha_scanner_ip.core.model.ProbeMode.valueOf(obj.getString("mode")) }.getOrDefault(com.ahoura.asha_scanner_ip.core.model.ProbeMode.TLS),
                            latenciesMs = latencies,
                            attempts = obj.optInt("attempts", 1),
                            tlsOk = obj.optBoolean("tlsOk", false),
                            httpStatus = obj.optInt("httpStatus", 0),
                            colo = obj.optString("colo", ""),
                            speedTested = obj.optBoolean("speedTested", false),
                            throughputBytesPerSec = obj.optDouble("throughput", 0.0),
                            healthy = obj.optBoolean("healthy", true),
                            passRate = obj.optDouble("passRate", 1.0),
                        )
                    )
                }
            }

            val tasks = mutableListOf<Pair<String, Int>>()
            val tasksArr = root.optJSONArray("remainingTasks")
            if (tasksArr != null) {
                for (i in 0 until tasksArr.length()) {
                    val tObj = tasksArr.getJSONObject(i)
                    tasks.add(tObj.getString("ip") to tObj.getInt("port"))
                }
            }

            val cfgObj = root.optJSONObject("config")
            val config = if (cfgObj != null) {
                val portsList = mutableListOf<Int>()
                val pArr = cfgObj.optJSONArray("ports")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) portsList.add(pArr.getInt(i))
                }
                ScanConfig(
                    mode = runCatching { com.ahoura.asha_scanner_ip.core.model.ProbeMode.valueOf(cfgObj.getString("mode")) }.getOrDefault(com.ahoura.asha_scanner_ip.core.model.ProbeMode.TLS),
                    timeoutMs = cfgObj.optLong("timeoutMs", 2000L),
                    concurrency = cfgObj.optInt("concurrency", 64),
                    count = cfgObj.optInt("count", 100),
                    gentle = cfgObj.optBoolean("gentle", false),
                    phase2Only = cfgObj.optBoolean("phase2Only", false),
                    pastedTargets = cfgObj.optString("pastedTargets", ""),
                    sniOverride = cfgObj.optString("sniOverride", ""),
                    ports = if (portsList.isNotEmpty()) portsList else listOf(443),
                )
            } else {
                ScanConfig()
            }

            ScanSnapshot(
                config = config,
                healthyResults = hrList,
                testedCount = tested,
                totalCount = total,
                remainingTasks = tasks,
                timestamp = timestamp,
            )
        }.getOrNull()
    }
}

object ScanStateStore {
    private var snapshotFile: File? = null

    fun init(filesDir: File) {
        snapshotFile = File(filesDir, "scan_snapshot.json")
    }

    @Synchronized
    fun save(snapshot: ScanSnapshot) {
        runCatching {
            snapshotFile?.writeText(snapshot.toJson())
        }
    }

    @Synchronized
    fun load(): ScanSnapshot? {
        val file = snapshotFile ?: return null
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            ScanSnapshot.fromJson(file.readText())
        }.getOrNull()
    }

    @Synchronized
    fun clear() {
        runCatching {
            snapshotFile?.delete()
        }
    }

    fun hasSnapshot(): Boolean {
        val file = snapshotFile ?: return false
        return file.exists() && file.length() > 0L
    }
}
