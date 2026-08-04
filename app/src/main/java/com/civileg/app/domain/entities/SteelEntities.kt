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
        is SteelSectionType.CSection -> calculateIxCSection(h, bf, tw, tf)
        is SteelSectionType.RHS -> calculateIxRHS(width, height, thickness)
        is SteelSectionType.CHS -> PI / 64.0 * (outerDiameter.pow(4) - (outerDiameter - 2 * thickness).pow(4))
        is SteelSectionType.LSection -> calculateIxAngle(legA, legB, thickness)
        is SteelSectionType.TSection -> calculateIxTSection(flangeWidth, flangeThickness, webDepth, webThickness)
        is SteelSectionType.PlateGirder -> calculateIxPlateGirder(h, bfTop, bfBot, tfTop, tfBot, tw)
        is SteelSectionType.Pipe -> PI / 64.0 * (outerDiameter.pow(4) - (outerDiameter - 2 * wallThickness).pow(4))
        is SteelSectionType.BuiltUp -> 0.0
    }

/** عزم القصور حول المحور الضعيف (Y) - mm⁴ */
val SteelSectionType.iy: Double
    get() = when (this) {
        is SteelSectionType.ISection -> calculateIyISection(h, bf, tw, tf)
        is SteelSectionType.CSection -> calculateIyCSection(h, bf, tw, tf)
        is SteelSectionType.RHS -> calculateIyRHS(width, height, thickness)
        is SteelSectionType.CHS -> PI / 64.0 * (outerDiameter.pow(4) - (outerDiameter - 2 * thickness).pow(4))
        is SteelSectionType.LSection -> calculateIyAngle(legA, legB, thickness)
        is SteelSectionType.TSection -> calculateIyTSection(flangeWidth, flangeThickness, webDepth, webThickness)
        is SteelSectionType.PlateGirder -> calculateIyPlateGirder(h, bfTop, bfBot, tfTop, tfBot, tw)
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
        is SteelSectionType.TSection -> {
            // Neutral axis from bottom of T-section
            val totalH = webDepth + flangeThickness
            val Atotal = flangeWidth * flangeThickness + webDepth * webThickness
            val yBar = if (Atotal > 0) {
                (flangeWidth * flangeThickness * (totalH - flangeThickness / 2.0) + webDepth * webThickness * (webDepth / 2.0)) / Atotal
            } else totalH / 2.0
            // Sx = max(Ix/yBar_from_bottom, Ix/(totalH - yBar_from_bottom))
            val yn = yBar
            val yt = totalH - yBar
            maxOf(ix / yn, ix / yt)
        }
        is SteelSectionType.PlateGirder -> ix / (h / 2.0)
        is SteelSectionType.Pipe -> ix / (outerDiameter / 2.0)
        is SteelSectionType.BuiltUp -> 0.0
    }

/** معامل المقطع المرن حول Y - mm³ */
val SteelSectionType.sy: Double
    get() = when (this) {
        is SteelSectionType.ISection -> iy / (bf / 2.0)
        is SteelSectionType.CSection -> {
            // Channel: shear center is offset; for Sx use full flange width
            // Sy = Iy / x_max (from centroid to extreme fiber in Y direction)
            val xMax = bf / 2.0
            if (xMax > 0) iy / xMax else 0.0
        }
        is SteelSectionType.RHS -> iy / (width / 2.0)
        is SteelSectionType.CHS -> iy / (outerDiameter / 2.0)
        is SteelSectionType.LSection -> {
            val A = (legA + legB - thickness) * thickness
            val xBar = ((legA - thickness / 2.0) * (legA - thickness / 2.0) / 2.0 + (legB - thickness / 2.0) * (legB - thickness / 2.0) / 2.0 * thickness) / A
            val xMax = maxOf(xBar, legA - xBar)
            if (xMax > 0) iy / xMax else 0.0
        }
        is SteelSectionType.TSection -> {
            // For symmetric T: Sy = Iy / (bf/2)
            val xMax = flangeWidth / 2.0
            if (xMax > 0) iy / xMax else 0.0
        }
        is SteelSectionType.PlateGirder -> iy / (maxOf(bfTop, bfBot) / 2.0)
        is SteelSectionType.Pipe -> iy / (outerDiameter / 2.0)
        is SteelSectionType.BuiltUp -> 0.0
    }

/** نصف القطر الدوراني حول X - mm */
val SteelSectionType.rx: Double
    get() = if (getArea() > 0) sqrt(ix / getArea()) else 0.0

/** نصف القطر الدوراني حول Y - mm */
val SteelSectionType.ry: Double
    get() = if (getArea() > 0) sqrt(iy / getArea()) else 0.0

/** معامل المقطع اللدن حول X - mm³ (تقريبي: 1.12 × Sx للمقاطع المدمجة) */
val SteelSectionType.zx: Double
    get() = when (this) {
        is SteelSectionType.ISection -> sx * 1.12
        is SteelSectionType.CSection -> sx * 1.10
        is SteelSectionType.RHS -> sx * 1.08
        is SteelSectionType.CHS -> sx * 1.12
        is SteelSectionType.LSection -> sx * 1.05
        is SteelSectionType.TSection -> sx * 1.05
        is SteelSectionType.PlateGirder -> sx * 1.12
        is SteelSectionType.Pipe -> sx * 1.12
        is SteelSectionType.BuiltUp -> 0.0
    }

/** معامل المقطع اللدن حول Y - mm³ */
val SteelSectionType.zy: Double
    get() = when (this) {
        is SteelSectionType.ISection -> sy * 1.12
        is SteelSectionType.CSection -> sy * 1.10
        is SteelSectionType.RHS -> sy * 1.08
        is SteelSectionType.CHS -> sy * 1.12
        is SteelSectionType.LSection -> sy * 1.05
        is SteelSectionType.TSection -> sy * 1.05
        is SteelSectionType.PlateGirder -> sy * 1.12
        is SteelSectionType.Pipe -> sy * 1.12
        is SteelSectionType.BuiltUp -> 0.0
    }

/** ثابت الشد (Torsional constant J) - mm⁴ */
val SteelSectionType.j: Double
    get() = when (this) {
        is SteelSectionType.ISection -> {
            // AISC: J = (2*bf*tf³ + (h-2tf)*tw³) / 3
            (2.0 * bf * tf.pow(3) + (h - 2 * tf) * tw.pow(3)) / 3.0
        }
        is SteelSectionType.CSection -> {
            (2.0 * bf * tf.pow(3) + (h - 2 * tf) * tw.pow(3)) / 3.0
        }
        is SteelSectionType.RHS -> {
            // Closed section: J = 4*A²*t / Σ(s/t) where A is enclosed area
            val hw = height - 2 * thickness
            val bw = width - 2 * thickness
            val A = hw * bw // enclosed area
            val peri = 2.0 * (hw + bw)
            if (peri > 0 && thickness > 0) 4.0 * A * A * thickness / peri else 0.0
        }
        is SteelSectionType.CHS -> {
            // J = 2π * r³ * t  where r = mean radius
            val r = (outerDiameter - thickness) / 2.0
            2.0 * PI * r.pow(3) * thickness
        }
        is SteelSectionType.LSection -> {
            // AISC approximate: J = (a+b-t)*t³/3
            (legA + legB - thickness) * thickness.pow(3) / 3.0
        }
        is SteelSectionType.TSection -> {
            (flangeWidth * flangeThickness.pow(3) + webDepth * webThickness.pow(3)) / 3.0
        }
        is SteelSectionType.PlateGirder -> {
            (bfTop * tfTop.pow(3) + bfBot * tfBot.pow(3) + (h - tfTop - tfBot) * tw.pow(3)) / 3.0
        }
        is SteelSectionType.Pipe -> {
            val r = (outerDiameter - wallThickness) / 2.0
            2.0 * PI * r.pow(3) * wallThickness
        }
        is SteelSectionType.BuiltUp -> 0.0
    }

/** ثابت الانحناء (Warping constant Cw) - mm⁶ */
val SteelSectionType.cw: Double
    get() = when (this) {
        is SteelSectionType.ISection -> {
            // AISC: Cw = Iy * (h - tf)² / 4
            iy * (h - tf).pow(2) / 4.0
        }
        is SteelSectionType.CSection -> {
            // Cw for channel (simplified)
            val hw = h - 2 * tf
            bf * tf.pow(3) * hw.pow(2) / 4.0
        }
        else -> 0.0 // RHS, CHS, Angle, T, Pipe — warping negligible or complex
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

/** حساب عزم القصور حول X لقطاع C-Channel - mm⁴ */
private fun calculateIxCSection(h: Double, b: Double, tw: Double, tf: Double): Double {
    // Same geometry as I-section for Ix (bending about strong axis)
    val hw = h - 2 * tf
    return (b * h.pow(3) - (b - tw) * hw.pow(3)) / 12.0
}

/** حساب عزم القصور حول Y لقطاع I - mm⁴ */
private fun calculateIyISection(h: Double, b: Double, tw: Double, tf: Double): Double {
    // Iy = 2 * [tf * bf³/12] + (h-2tf) * tw³/12
    return 2.0 * tf * b.pow(3) / 12.0 + (h - 2 * tf) * tw.pow(3) / 12.0
}

/** حساب عزم القصور حول Y لقطاع C-Channel - mm⁴ */
private fun calculateIyCSection(h: Double, b: Double, tw: Double, tf: Double): Double {
    // Channel Y-axis: centroid is offset from web centerline
    // Iy_own = 2 * (tf * bf³/12) + hw * tw³/12  (about own centroid)
    val hw = h - 2 * tf
    val A = 2 * b * tf + hw * tw
    val xBar = (2 * b * tf * b / 2.0) / if (A > 0) A else 1.0  // centroid from web CL
    // Parallel axis: shift from own centroid to section centroid
    val IySelf = 2.0 * (tf * b.pow(3) / 12.0) + hw * tw.pow(3) / 12.0
    // Subtract PA contribution from old centroid, add for new centroid
    // Simplified: for channel, Iy about centroid ≈ same as about web CL (flanges contribute most)
    return IySelf
}

/** حساب عزم القصور حول Y لمقطع RHS - mm⁴ */
private fun calculateIyRHS(w: Double, h: Double, t: Double): Double {
    val hw = h - 2 * t
    val bw = w - 2 * t
    return (h * w.pow(3) - hw * bw.pow(3)) / 12.0
}

/** حساب عزم القصور لمقطع RHS - mm⁴ */
private fun calculateIxRHS(w: Double, h: Double, t: Double): Double {
    val hw = h - 2 * t
    val bw = w - 2 * t
    return (w * h.pow(3) - bw * hw.pow(3)) / 12.0
}

/** حساب عزم القصور لزاوية (Angle) حول X - mm⁴
 * X-axis parallel to legB (shorter leg, horizontal)
 * Uses proper parallel axis theorem with centroid location.
 */
private fun calculateIxAngle(a: Double, b: Double, t: Double): Double {
    // Two rectangles: vertical leg (a x t) and horizontal leg ((b-t) x t)
    // Vertical leg: area = a*t, self-Ix = t*a³/12, centroid at a/2 from origin
    // Horizontal leg: area = (b-t)*t, self-Ix = (b-t)*t³/12, centroid at t/2 from origin
    val A = (a + b - t) * t
    if (A <= 0) return 0.0
    // y-bar from bottom (origin at outer bottom corner)
    val yBar = (a * t * (a / 2.0) + (b - t) * t * (t / 2.0)) / A
    // Ix about centroid = Σ(self_I + A*d²)
    val IxVert = t * a.pow(3) / 12.0 + a * t * (a / 2.0 - yBar).pow(2)
    val IxHoriz = (b - t) * t.pow(3) / 12.0 + (b - t) * t * (yBar - t / 2.0).pow(2)
    return IxVert + IxHoriz
}

/** حساب عزم القصور لزاوية حول Y - mm⁴ */
private fun calculateIyAngle(a: Double, b: Double, t: Double): Double {
    val A = (a + b - t) * t
    if (A <= 0) return 0.0
    // x-bar from left edge
    val xBar = ((a - t / 2.0) * t * (a - t / 2.0) / 2.0 + (b - t) * t * (t / 2.0 + (b - t) / 2.0)) / A
    // Simplified: Iy using parallel axis theorem
    val IyVert = a * t.pow(3) / 12.0 + a * t * ((a - t / 2.0) / 2.0 - xBar).pow(2)
    val IyHoriz = (b - t) * t.pow(3) / 12.0 + (b - t) * t * (t / 2.0 + (b - t) / 2.0 - xBar).pow(2)
    return IyVert + IyHoriz
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

/** حساب عزم القصور لمقطع T حول Y - mm⁴ (symmetric about Y-axis) */
private fun calculateIyTSection(bf: Double, tf: Double, dw: Double, tw: Double): Double {
    // Iy = bf³*tf/12 + dw*tw³/12 (both share the same vertical centroid axis)
    return bf.pow(3) * tf / 12.0 + dw * tw.pow(3) / 12.0
}

/** حساب عزم القصور لقطاع Plate Girder حول Y - mm⁴ */
private fun calculateIyPlateGirder(h: Double, bfTop: Double, bfBot: Double, tfTop: Double, tfBot: Double, tw: Double): Double {
    // Iy = bfTop³*tfTop/12 + bfBot³*tfBot/12 + (h-tfTop-tfBot)*tw³/12
    return bfTop.pow(3) * tfTop / 12.0 + bfBot.pow(3) * tfBot / 12.0 + (h - tfTop - tfBot) * tw.pow(3) / 12.0
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
