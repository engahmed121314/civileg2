package com.civileg.app.domain.entities

import android.os.Parcel
import android.os.Parcelable
import com.civileg.core.calculations.entities.*
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
    
    abstract fun getArea(): Double
    abstract fun getDepth(): Double
    abstract fun getWidth(): Double
    abstract fun getWebThickness(): Double
    abstract fun getFlangeThickness(): Double

    val weight: Double get() = getArea() * 7.85e-3
    
    abstract fun getIx(): Double
    abstract fun getIy(): Double
    abstract fun getSx(): Double
    abstract fun getSy(): Double
    abstract fun getZx(): Double
    abstract fun getZy(): Double
    abstract fun getJ(): Double
    abstract fun getCw(): Double

    val rx: Double get() = if (getArea() > 0) sqrt(getIx() / getArea()) else 0.0
    val ry: Double get() = if (getArea() > 0) sqrt(getIy() / getArea()) else 0.0

    @Parcelize
    data class ISection(
        val h: Double,
        val bf: Double,
        val tf: Double,
        val tw: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("I/H Section", customName ?: "I ${h.toInt()}x${bf.toInt()}", "AISC 360-B4 / ECP 205-3") {
        override fun getArea() = 2 * bf * tf + (h - 2 * tf) * tw
        override fun getDepth() = h
        override fun getWidth() = bf
        override fun getWebThickness() = tw
        override fun getFlangeThickness() = tf
        override fun getIx() = (bf * h.pow(3) - (bf - tw) * (h - 2 * tf).pow(3)) / 12.0
        override fun getIy() = 2.0 * tf * bf.pow(3) / 12.0 + (h - 2 * tf) * tw.pow(3) / 12.0
        override fun getSx() = getIx() / (h / 2.0)
        override fun getSy() = getIy() / (bf / 2.0)
        override fun getZx() = getSx() * 1.12
        override fun getZy() = getSy() * 1.12
        override fun getJ() = (2.0 * bf * tf.pow(3) + (h - 2 * tf) * tw.pow(3)) / 3.0
        override fun getCw() = getIy() * (h - tf).pow(2) / 4.0
    }
    
    @Parcelize
    data class CSection(
        val h: Double,
        val bf: Double,
        val tf: Double,
        val tw: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("C Channel", customName ?: "C ${h.toInt()}x${bf.toInt()}", "AISC 360-B4 / ECP 205-3") {
        override fun getArea() = 2 * bf * tf + (h - 2 * tf) * tw
        override fun getDepth() = h
        override fun getWidth() = bf
        override fun getWebThickness() = tw
        override fun getFlangeThickness() = tf
        override fun getIx() = (bf * h.pow(3) - (bf - tw) * (h - 2 * tf).pow(3)) / 12.0
        override fun getIy() = 2.0 * (tf * bf.pow(3) / 12.0) + (h - 2 * tf) * tw.pow(3) / 12.0
        override fun getSx() = getIx() / (h / 2.0)
        override fun getSy() = getIy() / (bf / 2.0)
        override fun getZx() = getSx() * 1.10
        override fun getZy() = getSy() * 1.10
        override fun getJ() = (2.0 * bf * tf.pow(3) + (h - 2 * tf) * tw.pow(3)) / 3.0
        override fun getCw() = bf * tf.pow(3) * (h - 2 * tf).pow(2) / 4.0
    }
    
    @Parcelize
    data class LSection(
        val legA: Double,
        val legB: Double,
        val thickness: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("L Angle", customName ?: "L ${legA.toInt()}x${legB.toInt()}x${thickness.toInt()}", "AISC 360-B4 / ECP 205-3") {
        override fun getArea() = (legA + legB - thickness) * thickness
        override fun getDepth() = legA
        override fun getWidth() = legB
        override fun getWebThickness() = thickness
        override fun getFlangeThickness() = thickness
        override fun getIx(): Double {
            val A = getArea()
            if (A <= 0) return 0.0
            val yBar = (legA * thickness * (legA / 2.0) + (legB - thickness) * thickness * (thickness / 2.0)) / A
            return (thickness * legA.pow(3) / 12.0 + legA * thickness * (legA / 2.0 - yBar).pow(2)) +
                   ((legB - thickness) * thickness.pow(3) / 12.0 + (legB - thickness) * thickness * (yBar - thickness / 2.0).pow(2))
        }
        override fun getIy(): Double {
            val A = getArea()
            if (A <= 0) return 0.0
            val xBar = ((legA - thickness / 2.0) * thickness * (legA - thickness / 2.0) / 2.0 + (legB - thickness) * thickness * (thickness / 2.0 + (legB - thickness) / 2.0)) / A
            return (legA * thickness.pow(3) / 12.0 + legA * thickness * ((legA - thickness / 2.0) / 2.0 - xBar).pow(2)) +
                   ((legB - thickness) * thickness.pow(3) / 12.0 + (legB - thickness) * thickness * (thickness / 2.0 + (legB - thickness) / 2.0 - xBar).pow(2))
        }
        override fun getSx() = getIx() / (legA / 2.0)
        override fun getSy(): Double {
            val A = getArea()
            val xBar = ((legA - thickness / 2.0) * thickness * (legA - thickness / 2.0) / 2.0 + (legB - thickness) * thickness * (thickness / 2.0 + (legB - thickness) / 2.0)) / A
            val xMax = maxOf(xBar, legA - xBar)
            return if (xMax > 0) getIy() / xMax else 0.0
        }
        override fun getZx() = getSx() * 1.05
        override fun getZy() = getSy() * 1.05
        override fun getJ() = (legA + legB - thickness) * thickness.pow(3) / 3.0
        override fun getCw() = 0.0
    }
    
    @Parcelize
    data class CHS(
        val outerDiameter: Double,
        val thickness: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Circular Hollow Section", customName ?: "CHS Ø${outerDiameter.toInt()}x${thickness.toInt()}", "AISC 360-B4 / ECP 205-3") {
        override fun getArea() = PI * (outerDiameter * outerDiameter - (outerDiameter - 2 * thickness) * (outerDiameter - 2 * thickness)) / 4
        override fun getDepth() = outerDiameter
        override fun getWidth() = outerDiameter
        override fun getWebThickness() = thickness
        override fun getFlangeThickness() = thickness
        override fun getIx() = PI / 64.0 * (outerDiameter.pow(4) - (outerDiameter - 2 * thickness).pow(4))
        override fun getIy() = getIx()
        override fun getSx() = getIx() / (outerDiameter / 2.0)
        override fun getSy() = getSx()
        override fun getZx() = getSx() * 1.12
        override fun getZy() = getZx()
        override fun getJ() = 2.0 * PI * ((outerDiameter - thickness) / 2.0).pow(3) * thickness
        override fun getCw() = 0.0
    }
    
    @Parcelize
    data class RHS(
        val w: Double,
        val height: Double,
        val thickness: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Rectangular Hollow Section", customName ?: "RHS ${w.toInt()}x${height.toInt()}x${thickness.toInt()}", "AISC 360-B4 / ECP 205-3") {
        override fun getArea() = 2 * (w + height - 2 * thickness) * thickness
        override fun getDepth() = height
        override fun getWidth() = w
        override fun getWebThickness() = thickness
        override fun getFlangeThickness() = thickness
        override fun getIx() = (w * height.pow(3) - (w - 2 * thickness) * (height - 2 * thickness).pow(3)) / 12.0
        override fun getIy() = (height * w.pow(3) - (height - 2 * thickness) * (w - 2 * thickness).pow(3)) / 12.0
        override fun getSx() = getIx() / (height / 2.0)
        override fun getSy() = getIy() / (w / 2.0)
        override fun getZx() = getSx() * 1.08
        override fun getZy() = getSy() * 1.08
        override fun getJ(): Double {
            val hw = height - 2 * thickness
            val bw = w - 2 * thickness
            val A = hw * bw
            val peri = 2.0 * (hw + bw)
            return if (peri > 0) 4.0 * A * A * thickness / peri else 0.0
        }
        override fun getCw() = 0.0
    }
    
    @Parcelize
    data class TSection(
        val flangeWidth: Double,
        val ft: Double,
        val webDepth: Double,
        val wt: Double,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("T Section", customName ?: "T ${flangeWidth.toInt()}x${webDepth.toInt()}", "AISC 360-B4") {
        override fun getArea() = flangeWidth * ft + webDepth * wt
        override fun getDepth() = webDepth + ft
        override fun getWidth() = flangeWidth
        override fun getWebThickness() = wt
        override fun getFlangeThickness() = ft
        override fun getIx(): Double {
            val totalH = webDepth + ft
            val yBar = (flangeWidth * ft * (totalH - ft / 2.0) + wt * webDepth * (webDepth / 2.0)) / getArea()
            return (flangeWidth * ft.pow(3) / 12.0 + flangeWidth * ft * (totalH - ft / 2.0 - yBar).pow(2)) +
                   (wt * webDepth.pow(3) / 12.0 + wt * webDepth * (webDepth / 2.0 - yBar).pow(2))
        }
        override fun getIy() = flangeWidth.pow(3) * ft / 12.0 + webDepth * wt.pow(3) / 12.0
        override fun getSx(): Double {
            val totalH = webDepth + ft
            val yBar = (flangeWidth * ft * (totalH - ft / 2.0) + wt * webDepth * (webDepth / 2.0)) / getArea()
            return maxOf(getIx() / yBar, getIx() / (totalH - yBar))
        }
        override fun getSy() = getIy() / (flangeWidth / 2.0)
        override fun getZx() = getSx() * 1.05
        override fun getZy() = getSy() * 1.05
        override fun getJ() = (flangeWidth * ft.pow(3) + webDepth * wt.pow(3)) / 3.0
        override fun getCw() = 0.0
    }
    
    @Parcelize
    data class PlateGirder(
        val h: Double,
        val bfTop: Double,
        val bfBot: Double,
        val tfTop: Double,
        val tfBot: Double,
        val tw: Double,
        val stiffenerSpacing: Double = 0.0,
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Plate Girder", customName ?: "PG ${h.toInt()}x${tw.toInt()}", "AISC 360-F / ECP 205") {
        override fun getArea() = bfTop * tfTop + bfBot * tfBot + (h - tfTop - tfBot) * tw
        override fun getDepth() = h
        override fun getWidth() = maxOf(bfTop, bfBot)
        override fun getWebThickness() = tw
        override fun getFlangeThickness() = maxOf(tfTop, tfBot)
        override fun getIx(): Double {
            val Aft = bfTop * tfTop
            val Afb = bfBot * tfBot
            val Aweb = (h - tfTop - tfBot) * tw
            val yBar = (Afb * tfBot / 2.0 + Aweb * (tfBot + (h - tfTop - tfBot) / 2.0) + Aft * (h - tfTop / 2.0)) / getArea()
            return (bfTop * tfTop.pow(3) / 12.0 + Aft * (h - tfTop / 2.0 - yBar).pow(2)) +
                   (tw * (h - tfTop - tfBot).pow(3) / 12.0 + Aweb * (tfBot + (h - tfTop - tfBot) / 2.0 - yBar).pow(2)) +
                   (bfBot * tfBot.pow(3) / 12.0 + Afb * (tfBot / 2.0 - yBar).pow(2))
        }
        override fun getIy() = bfTop.pow(3) * tfTop / 12.0 + bfBot.pow(3) * tfBot / 12.0 + (h - tfTop - tfBot) * tw.pow(3) / 12.0
        override fun getSx() = getIx() / (h / 2.0)
        override fun getSy() = getIy() / (getWidth() / 2.0)
        override fun getZx() = getSx() * 1.12
        override fun getZy() = getSy() * 1.12
        override fun getJ() = (bfTop * tfTop.pow(3) + bfBot * tfBot.pow(3) + (h - tfTop - tfBot) * tw.pow(3)) / 3.0
        override fun getCw() = getIy() * (h - (tfTop + tfBot)/2.0).pow(2) / 4.0
    }

    @Parcelize
    data class Pipe(
        val outerDiameter: Double,
        val wallThickness: Double,
        val pipeSchedule: String = "Std",
        val grade: SteelGrade,
        val customName: String? = null
    ) : SteelSectionType("Pipe", customName ?: "Pipe Ø${outerDiameter.toInt()} ${pipeSchedule}", "ASTM A53 / A106") {
        override fun getArea() = PI * (outerDiameter * outerDiameter - (outerDiameter - 2 * wallThickness) * (outerDiameter - 2 * wallThickness)) / 4
        override fun getDepth() = outerDiameter
        override fun getWidth() = outerDiameter
        override fun getWebThickness() = wallThickness
        override fun getFlangeThickness() = wallThickness
        override fun getIx() = PI / 64.0 * (outerDiameter.pow(4) - (outerDiameter - 2 * wallThickness).pow(4))
        override fun getIy() = getIx()
        override fun getSx() = getIx() / (outerDiameter / 2.0)
        override fun getSy() = getSx()
        override fun getZx() = getSx() * 1.12
        override fun getZy() = getZx()
        override fun getJ() = 2.0 * PI * ((outerDiameter - wallThickness) / 2.0).pow(3) * wallThickness
        override fun getCw() = 0.0
    }

    @Parcelize
    data class BuiltUp(
        val sections: List<SteelSectionType>,
        val connectionType: @RawValue ConnectionType,
        val customName: String? = null
    ) : SteelSectionType("Built-up Section", customName ?: "Built-up", "AISC 360-E6") {
        override fun getArea() = sections.sumOf { it.getArea() }
        override fun getDepth() = sections.maxOfOrNull { it.getDepth() } ?: 0.0
        override fun getWidth() = sections.maxOfOrNull { it.getWidth() } ?: 0.0
        override fun getWebThickness() = 0.0
        override fun getFlangeThickness() = 0.0
        override fun getIx() = 0.0
        override fun getIy() = 0.0
        override fun getSx() = 0.0
        override fun getSy() = 0.0
        override fun getZx() = 0.0
        override fun getZy() = 0.0
        override fun getJ() = 0.0
        override fun getCw() = 0.0
    }
}

val SteelSectionType.depth: Double get() = getDepth()
val SteelSectionType.width: Double get() = getWidth()
val SteelSectionType.webThickness: Double get() = getWebThickness()
val SteelSectionType.flangeThickness: Double get() = getFlangeThickness()
val SteelSectionType.area: Double get() = getArea()
val SteelSectionType.ix: Double get() = getIx()
val SteelSectionType.iy: Double get() = getIy()
val SteelSectionType.sx: Double get() = getSx()
val SteelSectionType.sy: Double get() = getSy()
val SteelSectionType.zx: Double get() = getZx()
val SteelSectionType.zy: Double get() = getZy()
val SteelSectionType.j: Double get() = getJ()
val SteelSectionType.cw: Double get() = getCw()

enum class SteelGrade(val displayName: String, val fy: Double, val fu: Double, val codeReference: String) : Parcelable {
    ST37("St37", 240.0, 360.0, "ECP 205-2.1"),
    ST44("St44", 280.0, 440.0, "ECP 205-2.1"),
    ST52("ST52", 360.0, 520.0, "ECP 205-2.1"),
    A36("A36", 250.0, 400.0, "AISC 360-A3.1"),
    A572_G50("A572 Gr.50", 345.0, 450.0, "AISC 360-A3.1"),
    A992("A992", 345.0, 450.0, "AISC 360-A3.1"),
    S275("S275", 275.0, 430.0, "EN 10025"),
    S355("S355", 355.0, 510.0, "EN 10025");
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { dest.writeString(name) }
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<SteelGrade> {
            override fun createFromParcel(parcel: Parcel) = valueOf(parcel.readString()!!)
            override fun newArray(size: Int) = arrayOfNulls<SteelGrade>(size)
        }
    }
}

sealed class ConnectionType : Parcelable {
    @Parcelize data class Welded(val weldType: WeldType, val weldSize: Double, val weldLength: Double, val electrodeType: ElectrodeType) : ConnectionType()
    @Parcelize data class Bolted(val boltDiameter: Double, val boltGrade: BoltGrade, val numberOfBolts: Int, val boltPattern: BoltPattern, val connectionType: BoltConnectionType) : ConnectionType()
    @Parcelize data class Pressed(val pressForce: Double, val contactArea: Double, val surfaceTreatment: String) : ConnectionType()
    @Parcelize data class Hybrid(val welded: Welded, val bolted: Bolted) : ConnectionType()
}

enum class WeldType : Parcelable {
    FILLET, GROOVE, PLUG, SLOT;
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { dest.writeInt(ordinal) }
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<WeldType> {
            override fun createFromParcel(parcel: Parcel) = WeldType.entries[parcel.readInt()]
            override fun newArray(size: Int) = arrayOfNulls<WeldType>(size)
        }
    }
}

enum class ElectrodeType(val displayName: String, val tensileStrength: Double) : Parcelable {
    E60XX("E60XX", 414.0), E70XX("E70XX", 482.0), E80XX("E80XX", 552.0), E90XX("E90XX", 621.0);
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { dest.writeInt(ordinal) }
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<ElectrodeType> {
            override fun createFromParcel(parcel: Parcel) = ElectrodeType.entries[parcel.readInt()]
            override fun newArray(size: Int) = arrayOfNulls<ElectrodeType>(size)
        }
    }
}

enum class BoltGrade(val displayName: String, val fy: Double, val fu: Double, val codeReference: String) : Parcelable {
    GRADE_4_6("Grade 4.6", 240.0, 400.0, "ECP 205-5.1"), GRADE_8_8("Grade 8.8", 640.0, 800.0, "ECP 205-5.1"), GRADE_10_9("Grade 10.9", 900.0, 1000.0, "ECP 205-5.1"), A325("A325", 660.0, 830.0, "AISC 360-J3"), A490("A490", 900.0, 1040.0, "AISC 360-J3");
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { dest.writeInt(ordinal) }
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<BoltGrade> {
            override fun createFromParcel(parcel: Parcel) = BoltGrade.entries[parcel.readInt()]
            override fun newArray(size: Int) = arrayOfNulls<BoltGrade>(size)
        }
    }
}

enum class BoltPattern : Parcelable {
    SINGLE_ROW, DOUBLE_ROW, STAGGERED, GRID;
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { dest.writeInt(ordinal) }
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<BoltPattern> {
            override fun createFromParcel(parcel: Parcel) = BoltPattern.entries[parcel.readInt()]
            override fun newArray(size: Int) = arrayOfNulls<BoltPattern>(size)
        }
    }
}

enum class BoltConnectionType : Parcelable {
    BEARING, SLIP_CRITICAL, TENSION, COMBINED;
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { dest.writeInt(ordinal) }
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<BoltConnectionType> {
            override fun createFromParcel(parcel: Parcel) = BoltConnectionType.entries[parcel.readInt()]
            override fun newArray(size: Int) = arrayOfNulls<BoltConnectionType>(size)
        }
    }
}

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
    val deflectionCheck: @RawValue DeflectionCheckResult? = null,
    val weight: Double,
    val cost: Double,
    val warnings: List<String>,
    val codeNotes: List<String>,
    val designCode: DesignCode? = null,
    val trace: @RawValue DesignTrace = DesignTrace()
) : Parcelable

@Parcelize enum class SteelMemberType : Parcelable { COLUMN, BEAM, BRACING, TRUSS_MEMBER, GIRDERS }
@Parcelize data class ConnectionDesignResult(val connectionType: ConnectionType, val capacity: Double, val appliedForce: Double, val utilizationRatio: Double, val isSafe: Boolean, val detailedCalculations: String, val warnings: List<String>, val codeNotes: List<String>) : Parcelable
@Parcelize data class BucklingCheckResult(val slendernessRatio: Double, val criticalStress: Double, val bucklingMode: BucklingMode, val isSafe: Boolean, val codeReference: String) : Parcelable
@Parcelize enum class BucklingMode : Parcelable { FLEXURAL, TORSIONAL, FLEXURAL_TORSIONAL, LOCAL }
