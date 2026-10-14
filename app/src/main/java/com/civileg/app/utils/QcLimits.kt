package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode

data class QcLimit(
    val value: Double,
    val unit: String,
    val reference: String
)

enum class ElementType { SLAB, BEAM, COLUMN, FOUNDATION, WALL }

enum class ExposureClass { C0, C1, C2 }

object QcLimits {

    fun minCover(element: ElementType, code: DesignCode): QcLimit = when (code) {
        DesignCode.ECP -> when (element) {
            ElementType.SLAB -> QcLimit(20.0, "mm", "ECP 203 §4.2")
            ElementType.BEAM -> QcLimit(40.0, "mm", "ECP 203 §4.2")
            ElementType.COLUMN -> QcLimit(40.0, "mm", "ECP 203 §4.2")
            ElementType.FOUNDATION -> QcLimit(75.0, "mm", "ECP 203 §4.2")
            ElementType.WALL -> QcLimit(20.0, "mm", "ECP 203 §4.2")
        }
        DesignCode.ACI -> when (element) {
            ElementType.SLAB -> QcLimit(20.0, "mm", "ACI 318-19 §20.6.1.4.2")
            ElementType.BEAM -> QcLimit(40.0, "mm", "ACI 318-19 §20.6.1.4.2")
            ElementType.COLUMN -> QcLimit(40.0, "mm", "ACI 318-19 §20.6.1.4.2")
            ElementType.FOUNDATION -> QcLimit(75.0, "mm", "ACI 318-19 §20.6.1.4.2")
            ElementType.WALL -> QcLimit(20.0, "mm", "ACI 318-19 §20.6.1.4.2")
        }
        DesignCode.SBC -> when (element) {
            ElementType.SLAB -> QcLimit(20.0, "mm", "SBC 304-2018")
            ElementType.BEAM -> QcLimit(40.0, "mm", "SBC 304-2018")
            ElementType.COLUMN -> QcLimit(40.0, "mm", "SBC 304-2018")
            ElementType.FOUNDATION -> QcLimit(75.0, "mm", "SBC 304-2018")
            ElementType.WALL -> QcLimit(20.0, "mm", "SBC 304-2018")
        }
    }

    fun slumpRange(code: DesignCode): ClosedFloatingPointRange<Double> = when (code) {
        DesignCode.ECP -> 25.0..100.0
        DesignCode.ACI -> 25.0..150.0
        DesignCode.SBC -> 25.0..120.0
    }

    fun airContent(code: DesignCode): ClosedFloatingPointRange<Double> = 3.0..8.0

    fun compactionMin(): QcLimit = QcLimit(95.0, "%", "AASHTO T99 / ASTM D698")

    fun vmaMin(): QcLimit = QcLimit(17.0, "%", "AASHTO / NCHRP")

    fun boltSpacingMin(d: Double): QcLimit = QcLimit(2.66 * d, "mm", "AISC 360-22 §J3.3")

    fun developmentLength(d: Double, fy: Double, fcu: Double, code: DesignCode): QcLimit = when (code) {
        DesignCode.ECP -> QcLimit(0.5 * fy / Math.sqrt(fcu) * d, "mm", "ECP 203 §4.2")
        DesignCode.ACI -> QcLimit(0.5 * fy / Math.sqrt(fcu) * d, "mm", "ACI 318-19 §25.4.2")
        DesignCode.SBC -> QcLimit(0.5 * fy / Math.sqrt(fcu) * d, "mm", "SBC 304-2018")
    }

    fun lapSpliceLength(d: Double, fy: Double, fcu: Double, code: DesignCode, spliceClass: String): QcLimit {
        val ld = developmentLength(d, fy, fcu, code).value
        val factor = if (spliceClass == "A") 1.0 else 1.3
        return QcLimit(factor * ld, "mm", "ACI 318-19 §25.5.2")
    }
}