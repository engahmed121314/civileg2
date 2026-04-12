package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlin.math.PI

/**
 * جميع أنواع القطاعات المعدنية
 */
sealed class SteelSectionType(val displayName: String, val codeReference: String) : Parcelable {
    
    /**
     * قطاع I أو H
     */
    @Parcelize
    data class ISection(
        val depth: Double,        // mm - h
        val flangeWidth: Double,  // mm - bf
        val flangeThickness: Double, // mm - tf
        val webThickness: Double, // mm - tw
        val grade: SteelGrade
    ) : SteelSectionType("I/H Section", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع C (Channel)
     */
    @Parcelize
    data class CSection(
        val depth: Double,
        val flangeWidth: Double,
        val flangeThickness: Double,
        val webThickness: Double,
        val grade: SteelGrade
    ) : SteelSectionType("C Channel", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع L (Angle)
     */
    @Parcelize
    data class LSection(
        val legA: Double,
        val legB: Double,
        val thickness: Double,
        val grade: SteelGrade
    ) : SteelSectionType("L Angle", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع أنبوبي Circular Hollow
     */
    @Parcelize
    data class CHS(
        val outerDiameter: Double,
        val thickness: Double,
        val grade: SteelGrade
    ) : SteelSectionType("Circular Hollow Section", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع مربع/مستطيل مجوف RHS/SHS
     */
    @Parcelize
    data class RHS(
        val width: Double,
        val height: Double,
        val thickness: Double,
        val grade: SteelGrade
    ) : SteelSectionType("Rectangular Hollow Section", "AISC 360-B4 / ECP 205-3")
    
    /**
     * قطاع T
     */
    @Parcelize
    data class TSection(
        val flangeWidth: Double,
        val flangeThickness: Double,
        val webDepth: Double,
        val webThickness: Double,
        val grade: SteelGrade
    ) : SteelSectionType("T Section", "AISC 360-B4")
    
    /**
     * قطاع مركب Built-up
     */
    @Parcelize
    data class BuiltUp(
        val sections: List<SteelSectionType>,
        val connectionType: @RawValue ConnectionType
    ) : SteelSectionType("Built-up Section", "AISC 360-E6")
    
    fun getArea(): Double = when (this) {
        is ISection -> 2 * flangeWidth * flangeThickness + (depth - 2 * flangeThickness) * webThickness
        is CSection -> 2 * flangeWidth * flangeThickness + (depth - 2 * flangeThickness) * webThickness
        is LSection -> (legA + legB - thickness) * thickness
        is CHS -> PI * (outerDiameter * outerDiameter - (outerDiameter - 2 * thickness) * (outerDiameter - 2 * thickness)) / 4
        is RHS -> 2 * (width + height - 2 * thickness) * thickness
        is TSection -> flangeWidth * flangeThickness + webDepth * webThickness
        is BuiltUp -> sections.sumOf { it.getArea() }
    }
}

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
