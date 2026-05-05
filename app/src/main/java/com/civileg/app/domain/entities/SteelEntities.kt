package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlin.math.PI

/**
 * جميع أنواع القطاعات المعدنية
 */
sealed class SteelSectionType(
    val displayName: String, 
    val sectionName: String,
    val codeReference: String
) : Parcelable {
    
    /**
     * قطاع I أو H
     */
    @Parcelize
    data class ISection(
        val h: Double,            // mm - depth
        val bf: Double,           // mm - flange width
        val tf: Double,           // mm - flange thickness
        val tw: Double,           // mm - web thickness
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("I/H Section", customName ?: "I ${h.toInt()}x${bf.toInt()}", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع C (Channel)
     */
    @Parcelize
    data class CSection(
        val h: Double,
        val bf: Double,
        val tf: Double,
        val tw: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("C Channel", customName ?: "C ${h.toInt()}x${bf.toInt()}", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع L (Angle)
     */
    @Parcelize
    data class LSection(
        val legA: Double,
        val legB: Double,
        val thickness: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("L Angle", customName ?: "L ${legA.toInt()}x${legB.toInt()}x${thickness.toInt()}", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع أنبوبي Circular Hollow
     */
    @Parcelize
    data class CHS(
        val outerDiameter: Double,
        val thickness: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Circular Hollow Section", customName ?: "CHS Ø${outerDiameter.toInt()}x${thickness.toInt()}", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع مربع/مستطيل مجوف RHS/SHS
     */
    @Parcelize
    data class RHS(
        val width: Double,
        val height: Double,
        val thickness: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Rectangular Hollow Section", customName ?: "RHS ${width.toInt()}x${height.toInt()}x${thickness.toInt()}", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع T
     */
    @Parcelize
    data class TSection(
        val flangeWidth: Double,
        val flangeThickness: Double,
        val webDepth: Double,
        val webThickness: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("T Section", customName ?: "T ${flangeWidth.toInt()}x${webDepth.toInt()}", "AISC 360-B4")
    
    /**
     * قطاع مركب Built-up
     */
    @Parcelize
    data class BuiltUp(
        val sections: List<SteelSectionType>,
        val connectionType: @RawValue ConnectionType,
        val customName: String? = null
    ) : SteelSectionType("Built-up Section", customName ?: "Built-up", "AISC 360-E6")
    
    fun getArea(): Double = when (this) {
        is ISection -> 2 * bf * tf + (h - 2 * tf) * tw
        is CSection -> 2 * bf * tf + (h - 2 * tf) * tw
        is LSection -> (legA + legB - thickness) * thickness
        is CHS -> PI * (outerDiameter * outerDiameter - (outerDiameter - 2 * thickness) * (outerDiameter - 2 * thickness)) / 4
        is RHS -> 2 * (width + height - 2 * thickness) * thickness
        is TSection -> flangeWidth * flangeThickness + webDepth * webThickness
        is BuiltUp -> sections.sumOf { it.getArea() }
    }
}

val SteelSectionType.depth: Double
    get() = when (this) {
        is SteelSectionType.ISection -> h
        is SteelSectionType.CSection -> h
        is SteelSectionType.LSection -> legA
        is SteelSectionType.CHS -> outerDiameter
        is SteelSectionType.RHS -> height
        is SteelSectionType.TSection -> webDepth + flangeThickness
        is SteelSectionType.BuiltUp -> sections.maxOfOrNull { it.depth } ?: 0.0
    }

val SteelSectionType.width: Double
    get() = when (this) {
        is SteelSectionType.ISection -> bf
        is SteelSectionType.CSection -> bf
        is SteelSectionType.LSection -> legB
        is SteelSectionType.CHS -> outerDiameter
        is SteelSectionType.RHS -> width
        is SteelSectionType.TSection -> flangeWidth
        is SteelSectionType.BuiltUp -> sections.maxOfOrNull { it.width } ?: 0.0
    }

val SteelSectionType.webThickness: Double
    get() = when (this) {
        is SteelSectionType.ISection -> tw
        is SteelSectionType.CSection -> tw
        is SteelSectionType.LSection -> thickness
        is SteelSectionType.CHS -> thickness
        is SteelSectionType.RHS -> thickness
        is SteelSectionType.TSection -> webThickness
        is SteelSectionType.BuiltUp -> 0.0
    }

val SteelSectionType.flangeThickness: Double
    get() = when (this) {
        is SteelSectionType.ISection -> tf
        is SteelSectionType.CSection -> tf
        is SteelSectionType.LSection -> thickness
        is SteelSectionType.CHS -> thickness
        is SteelSectionType.RHS -> thickness
        is SteelSectionType.TSection -> flangeThickness
        is SteelSectionType.BuiltUp -> 0.0
    }

val SteelSectionType.area: Double get() = getArea()
val SteelSectionType.weight: Double get() = area * 7.85e-3 // kg/m (mm2 * 7.85e-6 kg/mm3 * 1000 mm/m)
val SteelSectionType.ix: Double get() = 0.0
val SteelSectionType.sx: Double get() = 0.0
val SteelSectionType.rx: Double get() = 0.0
val SteelSectionType.zx: Double get() = 0.0
val SteelSectionType.rootRadius: Double get() = 0.0
val SteelSectionType.flangeSlope: Double get() = 0.0

@Parcelize
enum class SteelGrade(val displayName: String, val fy: Double, val fu: Double, val codeReference: String) : Parcelable {
    ST37("St37", 240.0, 360.0, "ECP 205-2.1"),
    ST44("St44", 280.0, 440.0, "ECP 205-2.1"),
    ST52("ST52", 360.0, 520.0, "ECP 205-2.1"),
    A36("A36", 250.0, 400.0, "AISC 360-A3.1"),
    A572_G50("A572 Gr.50", 345.0, 450.0, "AISC 360-A3.1"),
    A992("A992", 345.0, 450.0, "AISC 360-A3.1"),
    S275("S275", 275.0, 430.0, "EN 10025"),
    S355("S355", 355.0, 510.0, "EN 10025")
}

/**
 * أنواع الوصلات المعدنية
 */
sealed class ConnectionType(val displayName: String, val codeReference: String) : Parcelable {
    
    /**
     * وصلة ملحومة Welded
     */
    @Parcelize
    data class Welded(
        val weldType: WeldType,
        val weldSize: Double,     // mm
        val weldLength: Double,   // mm
        val electrodeType: ElectrodeType
    ) : ConnectionType("Welded Connection", "AISC 360-J2 / ECP 205-6")
    
    /**
     * وصلة مسامير Bolted
     */
    @Parcelize
    data class Bolted(
        val boltDiameter: Double, // mm
        val boltGrade: BoltGrade,
        val numberOfBolts: Int,
        val boltPattern: BoltPattern,
        val connectionType: BoltConnectionType
    ) : ConnectionType("Bolted Connection", "AISC 360-J3 / ECP 205-5")
    
    /**
     * وصلة بريس (مسامير ضغط) Pressed
     */
    @Parcelize
    data class Pressed(
        val pressForce: Double,   // kN
        val contactArea: Double,  // mm²
        val surfaceTreatment: String
    ) : ConnectionType("Pressed Connection", "AISC 360-J7 / Special")
    
    /**
     * وصلة مركبة (لحام + مسامير)
     */
    @Parcelize
    data class Hybrid(
        val welded: Welded,
        val bolted: Bolted
    ) : ConnectionType("Hybrid Connection", "AISC 360-J1.7")
}

@Parcelize
enum class WeldType(val displayName: String) : Parcelable {
    FILLET("Fillet Weld"),
    GROOVE("Groove Weld"),
    PLUG("Plug Weld"),
    SLOT("Slot Weld")
}

@Parcelize
enum class ElectrodeType(val displayName: String, val tensileStrength: Double) : Parcelable {
    E60XX("E60XX", 414.0),
    E70XX("E70XX", 482.0),
    E80XX("E80XX", 552.0),
    E90XX("E90XX", 621.0)
}

@Parcelize
enum class BoltGrade(val displayName: String, val fy: Double, val fu: Double, val codeReference: String) : Parcelable {
    GRADE_4_6("Grade 4.6", 240.0, 400.0, "ECP 205-5.1"),
    GRADE_8_8("Grade 8.8", 640.0, 800.0, "ECP 205-5.1 / AISC 360-J3"),
    GRADE_10_9("Grade 10.9", 900.0, 1000.0, "ECP 205-5.1"),
    A325("A325", 660.0, 830.0, "AISC 360-J3"),
    A490("A490", 900.0, 1040.0, "AISC 360-J3")
}

@Parcelize
enum class BoltPattern(val displayName: String) : Parcelable {
    SINGLE_ROW("Single Row"),
    DOUBLE_ROW("Double Row"),
    STAGGERED("Staggered"),
    GRID("Grid")
}

@Parcelize
enum class BoltConnectionType(val displayName: String) : Parcelable {
    BEARING("Bearing Type"),
    SLIP_CRITICAL("Slip-Critical"),
    TENSION("Tension"),
    COMBINED("Combined Shear-Tension")
}

/**
 * نتيجة تصميم العناصر المعدنية
 */
@Parcelize
data class SteelMemberResult(
    val sectionType: SteelSectionType,
    val memberType: SteelMemberType,
    val axialCapacity: Double,
    val flexuralCapacity: Double,
    val shearCapacity: Double,
    val utilizationRatio: Double,
    val isSafe: Boolean,
    val connectionDesign: ConnectionDesignResult?,
    val bucklingCheck: BucklingCheckResult?,
    val deflectionCheck: DeflectionCheckResult? = null,
    val weight: Double,          // kg/m
    val cost: Double,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
enum class SteelMemberType : Parcelable {
    COLUMN, BEAM, BRACING, TRUSS_MEMBER, GIRDERS
}

@Parcelize
data class ConnectionDesignResult(
    val connectionType: ConnectionType,
    val capacity: Double,
    val appliedForce: Double,
    val utilizationRatio: Double,
    val isSafe: Boolean,
    val detailedCalculations: String,
    val warnings: List<String>,
    val codeNotes: List<String>
) : Parcelable

@Parcelize
data class BucklingCheckResult(
    val slendernessRatio: Double,
    val criticalStress: Double,
    val bucklingMode: BucklingMode,
    val isSafe: Boolean,
    val codeReference: String
) : Parcelable

@Parcelize
enum class BucklingMode : Parcelable {
    FLEXURAL, TORSIONAL, FLEXURAL_TORSIONAL, LOCAL
}
