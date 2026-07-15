package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم السلالم حسب الكود المصري ECP 203-2020
 *
 * المراجع:
 * - ECP 203-2020 البند 4-2 (تصميم الانحناء والقص)
 * - ECP 203-2020 البند 6-3 (الانحراف)
 * - ECP 201-2012 (أحمال السلالم: حي 4 kN/m²، ميت 6 kN/m²)
 *
 * السلم يُ Modellled كـ بلاطة باتجاه واحد (كمرة بعرض 1م)
 * مع دعم بسيط على كلا الطرفين
 */
class ECPStaircase : StaircaseDesign {

    companion object {
        private const val GAMMA_C = 1.5       // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15      // معامل أمان الحديد
        private const val GAMMA_G = 1.4       // معامل حمل الميت
        private const val GAMMA_Q = 1.6       // معامل حمل الحي
        private const val COVER_INTERIOR = 25.0  // mm - غطاء داخلي
        private const val COVER_EXTERIOR = 35.0  // mm - غطاء خارجي
        private const val MIN_WAIST = 120.0     // mm - أقل سماكة للبلاطة
        private const val MAX_RISER = 180.0     // mm - أقصى ارتفاع درجة
        private const val MIN_GOING = 250.0     // mm - أقل عرض درجة
        private const val COMFORT_2R_PLUS_G_MIN = 550.0  // mm
        private const val COMFORT_2R_PLUS_G_MAX = 700.0  // mm
        private const val E_S = 200000.0       // MPa - معامل مرونة الحديد
    }

    override fun designStaircase(input: StaircaseInput): StaircaseResult {
        val safetyChecks = mutableListOf<StairSafetyCheck>()
        val codeNotes = mutableListOf<String>()

        // ========== 1. التصميم الهندسي ==========
        var numRisers: Int
        var numTreads: Int
        var riserMm: Double
        var goingMm: Double

        if (input.riserCount > 0 && input.going > 0) {
            // المستخدم حدد كل شيء
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            goingMm = input.going
        } else if (input.riserCount > 0) {
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            val idealGoing = 620.0 - 2 * riserMm
            goingMm = idealGoing.coerceIn(MIN_GOING, 300.0)
        } else if (input.going > 0) {
            goingMm = input.going
            val treadsEstimate = (input.span * 1000.0 / goingMm).toInt().coerceIn(3, 40)
            numTreads = treadsEstimate
            numRisers = numTreads + 1
            riserMm = input.totalRise * 1000.0 / numRisers
            // تحسين ليناسب معادلة الراحة
            val targetRiser = (620.0 - goingMm) / 2.0
            val adjustedRisers = round(input.totalRise * 1000.0 / targetRiser).toInt().coerceIn(5, 40)
            if (abs(adjustedRisers - numRisers) > 1) {
                numRisers = adjustedRisers
                numTreads = numRisers - 1
                riserMm = input.totalRise * 1000.0 / numRisers
            }
        } else {
            // حساب تلقائي كامل - أوجد أفضل تقسيم
            var bestRiser = 170.0
            var bestDiff = Double.MAX_VALUE
            for (n in 5..40) {
                val r = input.totalRise * 1000.0 / n
                val t = n - 1
                val g = if (t > 0) input.span * 1000.0 / t else 300.0
                if (r <= MAX_RISER && g >= MIN_GOING) {
                    val comfort = 2 * r + g
                    val diff = abs(comfort - 625.0)
                    if (diff < bestDiff) { bestDiff = diff; bestRiser = r }
                }
            }
            bestRiser = bestRiser.coerceIn(140.0, MAX_RISER)
            numRisers = round(input.totalRise * 1000.0 / bestRiser).toInt().coerceIn(5, 40)
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            goingMm = (input.span * 1000.0 / numTreads).coerceIn(MIN_GOING, 300.0)
        }

        val inclinedLength = sqrt(input.span * input.span + input.totalRise * input.totalRise)
        val slopeAngle = toDegrees(atan(input.totalRise / input.span))
        val cosTheta = cos(toRadians(slopeAngle))

        codeNotes.add("Geometry: ${numRisers} risers × ${numTreads} treads")
        codeNotes.add("R = ${String.format("%.1f", riserMm)} mm, G = ${String.format("%.1f", goingMm)} mm")
        codeNotes.add("2R+G = ${String.format("%.0f", 2 * riserMm + goingMm)} mm")
        codeNotes.add("Slope angle θ = ${String.format("%.1f", slopeAngle)}°")

        // ========== 2. سماكة البلاطة ==========
        val waistThickness = max(input.waistThickness, MIN_WAIST)
        val cover = COVER_INTERIOR
        val stirrupEstimate = 8.0
        val effectiveDepth = waistThickness - cover - stirrupEstimate / 2 - 6.0

        safetyChecks.add(StairSafetyCheck(
            name = "Waist Thickness",
            value = waistThickness, limit = MIN_WAIST, unit = "mm",
            isSafe = waistThickness >= MIN_WAIST,
            description = "Minimum waist thickness per ECP 203"
        ))

        // ========== 3. الأحمال ==========
        // w_horiz = 1.4×dead/cos(θ) + 1.6×live (مُسقط أفقياً)
        val horizontalLoad = GAMMA_G * input.deadLoad / cosTheta.coerceAtLeast(0.1) + GAMMA_Q * input.liveLoad
        val factoredOnSlope = horizontalLoad * cosTheta  // kN/m² on slope

        codeNotes.add("Factored horizontal load = ${String.format("%.2f", horizontalLoad)} kN/m²")
        codeNotes.add("Factored load on slope = ${String.format("%.2f", factoredOnSlope)} kN/m²")

        // ========== 4. التحليل الإنشائي ==========
        val span = input.span
        val momentCoefficient = if (input.stairType == StairType.DOG_LEG) 1.0 / 10.0 else 1.0 / 8.0
        val adjustedMoment = horizontalLoad * span * span * momentCoefficient
        val adjustedShear = horizontalLoad * span / 2.0
        val reactionA = adjustedShear
        val reactionB = adjustedShear

        if (input.stairType == StairType.DOG_LEG) {
            codeNotes.add("Dog-leg stair: landing provides partial fixity, M = wL²/10")
        }
        codeNotes.add("Design moment M = ${String.format("%.2f", adjustedMoment)} kN.m/m")
        codeNotes.add("Design shear V = ${String.format("%.2f", adjustedShear)} kN/m")

        // ========== 5. تصميم الانحناء (K-method) ==========
        val b = 1000.0
        val d = effectiveDepth
        val Mu = adjustedMoment * 1e6

        val K = if (input.fcu > 0 && d > 0) Mu / (input.fcu * b * d * d) else 0.0
        val kBal = calculateKBal(input.fcu, input.fy)

        codeNotes.add("K = ${String.format("%.4f", K)}, K_bal = ${String.format("%.4f", kBal)}")

        val leverArm = if (0.25 - K / 1.25 > 0) {
            d * (0.5 + sqrt(0.25 - K / 1.25))
        } else {
            d * 0.7
        }

        val fs = input.fy / GAMMA_S
        var astRequired = if (fs > 0 && leverArm > 0) Mu / (fs * leverArm) else 0.0

        // الحد الأدنى للتسليح: 0.15% من b×d للبلاطات (ECP 203)
        val minSteelRatio = 0.0015
        val minSteelArea = max(minSteelRatio * b * d, 0.6 / input.fy.coerceAtLeast(1.0) * b * d)

        if (astRequired < minSteelArea) {
            astRequired = minSteelArea
            codeNotes.add("Minimum steel applied: ${String.format("%.0f", minSteelArea)} mm²/m (0.15% of b×d)")
        }

        safetyChecks.add(StairSafetyCheck(
            name = "Flexure (K ≤ K_bal)",
            value = K, limit = kBal, unit = "",
            isSafe = K <= kBal,
            description = "ECP 203 Section 4-2-2-1"
        ))

        // اختيار حديد رئيسي (مقاسات مترية)
        val mainRebar = selectBars(astRequired)
        val mainRebarArea = parseBarArea(mainRebar)
        val rho = mainRebarArea / (b * d)

        // حديد التوزيع = 20% من الرئيسي (ECP 203)
        val distAreaRequired = 0.2 * mainRebarArea
        val distAreaMin = 0.0012 * b * waistThickness
        val distAreaFinal = max(distAreaRequired, distAreaMin)
        val distributionRebar = selectDistBars(distAreaFinal, input.stairWidth)
        val distributionRebarArea = parseDistBarArea(distributionRebar, input.stairWidth)

        codeNotes.add("Main reinforcement: $mainRebar (${String.format("%.0f", mainRebarArea)} mm²/m)")
        codeNotes.add("Distribution: $distributionRebar (${String.format("%.0f", distributionRebarArea)} mm²/m)")

        // ========== 6. تصميم القص ==========
        val Vu = adjustedShear * 1000.0
        val qcu = 0.24 * sqrt(input.fcu) / GAMMA_C
        val concreteShearCapacity = qcu * b * d / 1000.0
        val maxShearStress = 0.7 * sqrt(input.fcu / GAMMA_C)
        val maxShearCapacity = maxShearStress * b * d / 1000.0

        val shearSafe = (Vu / 1000.0) <= maxShearCapacity
        safetyChecks.add(StairSafetyCheck(
            name = "Shear Capacity",
            value = adjustedShear, limit = maxShearCapacity, unit = "kN/m",
            isSafe = shearSafe,
            description = "ECP 203 Section 4-3-1-2"
        ))

        val requiredStirrups: String
        val stirrupDiameter: Double
        val stirrupSpacing: Double

        if ((Vu / 1000.0) <= concreteShearCapacity) {
            requiredStirrups = "None (concrete capacity sufficient)"
            stirrupDiameter = 0.0
            stirrupSpacing = 0.0
            codeNotes.add("Vc = ${String.format("%.2f", concreteShearCapacity)} kN/m ≥ Vu = ${String.format("%.2f", adjustedShear)} kN/m")
            codeNotes.add("No shear reinforcement required per ECP 203")
        } else {
            val excessShear = (Vu / 1000.0 - concreteShearCapacity) * 1000.0
            val asvRequired = excessShear / (fs * d) * 1000.0
            val minAvs = 0.0015 * b * 1000.0
            val asvFinal = max(asvRequired, minAvs)

            stirrupDiameter = 8.0
            val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4.0
            stirrupSpacing = (stirrupArea * 1000.0 / asvFinal).coerceIn(50.0, 200.0)
            requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c"
            codeNotes.add("Shear reinforcement required: $requiredStirrups")
        }

        // ========== 7. فحص الانحراف ==========
        val basicRatio = 20.0
        val rhoPercent = (rho * 100).coerceAtLeast(0.15)
        val modificationFactor = 0.55 + 0.45 / rhoPercent
        val allowableRatio = basicRatio * modificationFactor
        val actualRatio = (span * 1000) / waistThickness
        val deflectionRatio = actualRatio / allowableRatio

        val allowableDeflection = (span * 1000) / 250.0
        val calculatedDeflection = if (deflectionRatio > 1.0) {
            allowableDeflection * deflectionRatio * 1.2
        } else {
            allowableDeflection * 0.7
        }
        val deflectionOk = deflectionRatio <= 1.0

        safetyChecks.add(StairSafetyCheck(
            name = "Deflection (Span/Depth)",
            value = actualRatio, limit = allowableRatio, unit = "",
            isSafe = deflectionOk,
            description = "ECP 203 Section 6-3: L/d ≤ ${String.format("%.1f", allowableRatio)}"
        ))

        // ========== 8. فحوصات هندسية ==========
        safetyChecks.add(StairSafetyCheck(
            name = "2R + G (Comfort)",
            value = 2 * riserMm + goingMm, limit = COMFORT_2R_PLUS_G_MAX, unit = "mm",
            isSafe = 2 * riserMm + goingMm in COMFORT_2R_PLUS_G_MIN..COMFORT_2R_PLUS_G_MAX,
            description = "Comfort formula: 550 ≤ 2R+G ≤ 700 mm"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Riser ≤ 180mm",
            value = riserMm, limit = MAX_RISER, unit = "mm",
            isSafe = riserMm <= MAX_RISER,
            description = "Maximum riser height per ECP 201"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Going ≥ 250mm",
            value = goingMm, limit = MIN_GOING, unit = "mm",
            isSafe = goingMm >= MIN_GOING,
            description = "Minimum going per ECP 201"
        ))

        // ========== 9. ملاحظات الكود ==========
        codeNotes.add("ECP 203-2020: Section 4-2 (Flexure design of waist slab)")
        codeNotes.add("ECP 203-2020: Section 6-2 (Slab reinforcement rules)")
        codeNotes.add("ECP 203-2020: Section 6-3 (Deflection limits L/250)")
        codeNotes.add("ECP 201-2012: Staircase live load = 4.0 kN/m²")

        return StaircaseResult(
            isSafe = safetyChecks.all { it.isSafe },
            designCode = DesignCode.ECP,
            riser = riserMm,
            going = goingMm,
            numberOfRisers = numRisers,
            numberOfTreads = numTreads,
            slopeAngle = slopeAngle,
            inclinedLength = inclinedLength,
            factoredLoad = factoredOnSlope,
            horizontalLoad = horizontalLoad,
            maxMoment = adjustedMoment,
            maxShear = adjustedShear,
            reactionA = reactionA,
            reactionB = reactionB,
            mainRebar = mainRebar,
            mainRebarArea = mainRebarArea,
            distributionRebar = distributionRebar,
            distributionRebarArea = distributionRebarArea,
            effectiveDepth = effectiveDepth,
            reinforcementRatio = rho,
            minSteelRatio = minSteelRatio,
            shearCapacity = concreteShearCapacity,
            requiredStirrups = requiredStirrups,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            deflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            deflectionOk = deflectionOk,
            safetyChecks = safetyChecks,
            codeNotes = codeNotes
        )
    }

    // ========== دوال مساعدة ==========

    private fun calculateKBal(fcu: Double, fy: Double): Double {
        val epsilonCu = 0.003
        val epsilonY = fy / (E_S * GAMMA_S)
        val aOverD = 0.9 * epsilonCu / (epsilonCu + epsilonY)
        return (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
    }

    /**
     * اختيار أسياخ رئيسية (مقاسات مترية - ECP)
     * النتيجة بصيغة: "Ø12 @ 150 mm c/c"
     */
    private fun selectBars(requiredArea: Double): String {
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val barDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            val numBars = ceil(requiredArea / area).toInt()
            val spacing = 1000.0 / numBars
            spacing >= 100.0 && spacing <= 200.0
        } ?: 12.0

        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredArea / barArea).toInt().coerceAtLeast(5)
        val spacing = 1000.0 / numBars
        return "Ø${barDia.toInt()} @ ${spacing.toInt()} mm c/c"
    }

    /**
     * اختيار أسياخ التوزيع (عرضية على السلم)
     */
    private fun selectDistBars(requiredAreaTotal: Double, stairWidthM: Double): String {
        val availableBars = listOf(8.0, 10.0, 12.0)
        val barDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            val numBars = ceil(requiredAreaTotal / area).toInt()
            numBars in 3..20
        } ?: 8.0

        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredAreaTotal / barArea).toInt().coerceAtLeast(4)
        val spacing = (stairWidthM * 1000.0 / numBars)
        return "Ø${barDia.toInt()} @ ${spacing.toInt()} mm c/c"
    }

    /**
     * حساب مساحة الحديد الرئيسي من النص (mm²/m)
     */
    private fun parseBarArea(barString: String): Double {
        if (barString.contains("None")) return 0.0
        try {
            val parts = barString.split("Ø", " @ ")
            if (parts.size >= 3) {
                val dia = parts[1].trim().split(" ")[0].toDouble()
                val spacing = parts[2].trim().split(" ")[0].toDouble()
                val barArea = PI * dia * dia / 4.0
                return barArea * 1000.0 / spacing
            }
        } catch (e: Exception) { /* fall through */ }
        return 0.0
    }

    /**
     * حساب مساحة حديد التوزيع الإجمالي (mm²)
     */
    private fun parseDistBarArea(barString: String, stairWidthM: Double): Double {
        if (barString.contains("None")) return 0.0
        try {
            val parts = barString.split("Ø", " @ ")
            if (parts.size >= 3) {
                val dia = parts[1].trim().split(" ")[0].toDouble()
                val spacing = parts[2].trim().split(" ")[0].toDouble()
                val barArea = PI * dia * dia / 4.0
                val numBars = (stairWidthM * 1000.0 / spacing).toInt().coerceAtLeast(1)
                return numBars * barArea
            }
        } catch (e: Exception) { /* fall through */ }
        return 0.0
    }
}