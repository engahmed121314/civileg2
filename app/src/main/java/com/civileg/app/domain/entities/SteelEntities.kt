package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlin.math.*

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
     * قطاع جسر لوح ملحوم Plate Girder
     * Supports different top/bottom flanges (asymmetric girders)
     */
    @Parcelize
    data class PlateGirder(
        val h: Double,              // mm - overall depth
        val bfTop: Double,          // mm - top flange width
        val bfBot: Double,          // mm - bottom flange width
        val tfTop: Double,          // mm - top flange thickness
        val tfBot: Double,          // mm - bottom flange thickness
        val tw: Double,             // mm - web thickness
        val stiffenerSpacing: Double = 0.0,  // mm - transverse stiffener spacing (0 = none)
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Plate Girder", customName ?: "PG ${h.toInt()}x${tw.toInt()}", "AISC 360-F / ECP 205")

    /**
     * أنبوب حديدي ASTM Pipe (Std, XS, XXS wall thicknesses)
     */
    @Parcelize
    data class Pipe(
        val outerDiameter: Double,  // mm (nominal)
        val wallThickness: Double, // mm
        val pipeSchedule: String = "Std",  // Std, XS, XXS, Sch40, Sch80, etc.
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Pipe", customName ?: "Pipe Ø${outerDiameter.toInt()} ${pipeSchedule}", "ASTM A53 / A106")

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
        is PlateGirder -> bfTop * tfTop + bfBot * tfBot + (h - tfTop - tfBot) * tw
        is Pipe -> PI * (outerDiameter * outerDiameter - (outerDiameter - 2 * wallThickness) * (outerDiameter - 2 * wallThickness)) / 4
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
        is SteelSectionType.PlateGirder -> h
        is SteelSectionType.Pipe -> outerDiameter
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
        is SteelSectionType.PlateGirder -> maxOf(bfTop, bfBot)
        is SteelSectionType.Pipe -> outerDiameter
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
        is SteelSectionType.PlateGirder -> tw
        is SteelSectionType.Pipe -> wallThickness
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
        is SteelSectionType.PlateGirder -> maxOf(tfTop, tfBot)
        is SteelSectionType.Pipe -> wallThickness
        is SteelSectionType.BuiltUp -> 0.0
    }

val SteelSectionType.area: Double get() = getArea()
val SteelSectionType.weight: Double get() = area * 7.85e-3 // kg/m (mm2 * 7.85e-6 kg/mm3 * 1000 mm/m)
/** عزم القصور حول المحور القوي (X) - mm⁴ */
val SteelSectionType.ix: Double
    get() = when (this) {
        is SteelSectionType.ISection -> calculateIxISection(h, bf, tw, tf)
        is SteelSectionType.CSection -> calculateIxISection(h, bf, tw, tf) * 0.85  // تقريبي للقناة
        is SteelSectionType.RHS -> calculateIxRHS(width, height, thickness)
        is SteelSectionType.CHS -> PI / 64.0 * (outerDiameter.pow(4) - (outerDiameter - 2 * thickness).pow(4))
        is SteelSectionType.LSection -> calculateIxAngle(legA, legB, thickness)
        is SteelSectionType.TSection -> calculateIxTSection(flangeWidth, flangeThickness, webDepth, webThickness)
        is SteelSectionType.PlateGirder -> calculateIxPlateGirder(h, bfTop, bfBot, tfTop, tfBot, tw)
        is SteelSectionType.Pipe -> PI / 64.0 * (outerDiameter.pow(4) - (outerDiameter - 2 * wallThickness).pow(4))
        is SteelSectionType.BuiltUp -> 0.0
    }

/** معامل المقطع المرن حول X - mm³ */
val SteelSectionType.sx: Double
    get() = when (this) {
        is SteelSectionType.ISection -> ix / (h / 2.0)
        is SteelSectionType.CSection -> ix / (h / 2.0)
        is SteelSectionType.RHS -> ix / (height / 2.0)
        is SteelSectionType.CHS -> ix / (outerDiameter / 2.0)
        is SteelSectionType.LSection -> ix / (legA / 2.0)
        is SteelSectionType.TSection -> ix / (webDepth + flangeThickness) * 2.0
        is SteelSectionType.PlateGirder -> ix / (h / 2.0)  // approximate using max depth
        is SteelSectionType.Pipe -> ix / (outerDiameter / 2.0)
        is SteelSectionType.BuiltUp -> 0.0
    }

/** نصف القطر الدوراني حول X - mm */
val SteelSectionType.rx: Double
    get() = if (getArea() > 0) sqrt(ix / getArea()) else 0.0

/** معامل المقطع اللدن حول X - mm³ (تقريبي: 1.12 × Sx للمقاطع المدمجة) */
val SteelSectionType.zx: Double
    get() = when (this) {
        is SteelSectionType.ISection -> sx * 1.12  // تقريبي للمقاطع المدرفلة
        is SteelSectionType.CSection -> sx * 1.10
        is SteelSectionType.RHS -> sx * 1.08
        is SteelSectionType.CHS -> sx * 1.12
        is SteelSectionType.LSection -> sx * 1.05
        is SteelSectionType.TSection -> sx * 1.05
        is SteelSectionType.PlateGirder -> sx * 1.12  // welded I-section shape factor
        is SteelSectionType.Pipe -> sx * 1.12
        is SteelSectionType.BuiltUp -> 0.0
    }

val SteelSectionType.rootRadius: Double
    get() = when (this) {
        is SteelSectionType.ISection -> if (h <= 300) 8.0 else if (h <= 500) 12.0 else 20.0
        is SteelSectionType.CSection -> if (h <= 300) 6.0 else 10.0
        else -> 0.0
    }

val SteelSectionType.flangeSlope: Double
    get() = when (this) {
        is SteelSectionType.ISection -> 0.08  // ~4.5° تقريبياً
        is SteelSectionType.CSection -> 0.08
        else -> 0.0
    }

/** حساب عزم القصور لمقطع I - mm⁴ */
private fun calculateIxISection(h: Double, b: Double, tw: Double, tf: Double): Double {
    val hw = h - 2 * tf  // ارتفاع الجذع الصافي
    return (b * h.pow(3) - (b - tw) * hw.pow(3)) / 12.0
}

/** حساب عزم القصور لمقطع RHS - mm⁴ */
private fun calculateIxRHS(w: Double, h: Double, t: Double): Double {
    val hw = h - 2 * t
    val bw = w - 2 * t
    return (w * h.pow(3) - bw * hw.pow(3)) / 12.0
}

/** حساب عزم القصور لزاوية متساوية الساقين تقريباً - mm⁴ */
private fun calculateIxAngle(a: Double, b: Double, t: Double): Double {
    // حول محور موازي لساق b (المسطح العلوي)
    val A = (a + b - t) * t
    val yBar = (a * a + (b - t) * t / 2.0) / (2.0 * (a + b - t))
    return (a * t * (t / 2.0).pow(2) + (b - t) * t * (t / 2.0 + t / 2.0).pow(2) + 
            t * a.pow(3) / 12.0 + (b - t) * t.pow(3) / 12.0)  // مبسط
}

/** حساب عزم القصور لقطاع Plate Girder (ممكن غير متماثل) - mm⁴ */
private fun calculateIxPlateGirder(h: Double, bfTop: Double, bfBot: Double, tfTop: Double, tfBot: Double, tw: Double): Double {
    val Aft = bfTop * tfTop
    val Afb = bfBot * tfBot
    val Aweb = (h - tfTop - tfBot) * tw
    val Atotal = Aft + Afb + Aweb

    // Neutral axis from bottom
    val yBar = if (Atotal > 0) {
        (Afb * tfBot / 2.0 + Aweb * (tfBot + (h - tfTop - tfBot) / 2.0) + Aft * (h - tfTop / 2.0)) / Atotal
    } else 0.0

    val IxTop = bfTop * tfTop.pow(3) / 12.0 + Aft * (h - tfTop / 2.0 - yBar).pow(2)
    val IxWeb = tw * (h - tfTop - tfBot).pow(3) / 12.0 + Aweb * (tfBot + (h - tfTop - tfBot) / 2.0 - yBar).pow(2)
    val IxBot = bfBot * tfBot.pow(3) / 12.0 + Afb * (tfBot / 2.0 - yBar).pow(2)
    return IxTop + IxWeb + IxBot
}

/** حساب عزم القصور لمقطع T - mm⁴ */
private fun calculateIxTSection(bf: Double, tf: Double, dw: Double, tw: Double): Double {
    val totalH = dw + tf
    val yBar = (bf * tf * (totalH - tf / 2.0) + tw * dw * (dw / 2.0)) / (bf * tf + tw * dw)
    val IxFlange = bf * tf.pow(3) / 12.0 + bf * tf * (totalH - tf / 2.0 - yBar).pow(2)
    val IxWeb = tw * dw.pow(3) / 12.0 + tw * dw * (dw / 2.0 - yBar).pow(2)
    return IxFlange + IxWeb
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
