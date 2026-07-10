package com.ahoura.asha_scanner_ip.core.engine

import com.ahoura.asha_scanner_ip.core.model.ScanResult

enum class QualityGrade(val label: String) {
    S("S"), A("A"), B("B"), C("C"), F("F")
}

object QualityEvaluator {
    fun evaluate(r: ScanResult): QualityGrade {
        if (!r.healthy || r.loss > 0.1 || r.passRate < 0.5) return QualityGrade.F
        val ping = r.avgLatencyMs
        val speed = r.throughputMbps
        val stable = r.passRate >= 0.9
        
        return when {
            ping < 80 && speed > 20 && stable -> QualityGrade.S
            ping < 120 && speed > 10 && stable -> QualityGrade.A
            ping < 180 && speed > 3 -> QualityGrade.B
            else -> QualityGrade.C
        }
    }

    fun isGoodForGaming(r: ScanResult): Boolean {
        return r.healthy && r.avgLatencyMs < 100 && r.jitterMs < 20 && r.loss == 0.0
    }

    fun isGoodForStreaming(r: ScanResult): Boolean {
        return r.healthy && r.throughputMbps > 15
    }
    
    fun isStable(r: ScanResult): Boolean {
        return r.healthy && r.loss == 0.0 && r.jitterMs < 30
    }
}
