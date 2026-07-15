package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم السلالم حسب الكود السعودي SBC 304-2018
 *
 * SBC 304 يعتمد على ACI 318 مع تعديلات سعودية محددة:
 * - معاملات تحميل: 1.2D + 1.6L (SBC 304)
 * - نسبة التسليح الأدنى أعلى: ρmin = 0.002 × b × d (المناخ الحار)
 * - غطاء خرساني أكبر: 40mm داخلي، 65mm خارجي (بيئة مالحة/تآكل)
 * - في المناطق الساحلية: زيادة الغطاء 10mm، مراعاة أسياك مطليمة بالإيبوكسي
 * - فحص زلزالي إضافي إن وجد
 *
 * المراجع:
 * - SBC 304-2018 البند 9 (الانحناء)
 * - SBC 304-2018 البند 11 (القص)
 * - SBC 304-2018 البند 7.7 (الغطاء الخرساني)
 * - SBC 304-2018 البند 21 (متطلبات زلزالية)
 */
class SBCStaircase : StaircaseDesign {

    companion object {
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val GAMMA_D = 1.2       // SBC 304
        private const val GAMMA_L = 1.6       // SBC 304
        private const val COVER_INTERIOR = 40.0   // mm - SBC (أعلى من ACI)
        private const val COVER_EXTERIOR = 65.0   // mm - بيئة مالحة
        private const val COVER_COASTAL = 75.0    // mm - مناطق ساحلية
        private const val MIN_WAIST = 130.0        // mm (SBC أقل سماكة أعلى)
        private const val MAX_RISER = 178.0        // mm (7")
        private const val MIN_GOING = 279.0        // mm (11")
        private const val COMFORT_2R_PLUS_G_MIN = 580.0  // mm
        private const val COMFORT_2R_PLUS_G_MAX = 640.0  // mm
        private const val SBC_MIN_STEEL_RATIO = 0.002  // ρmin = 0.002 (أعلى من ACI بسبب المناخ)
        private const val E_S = 200000.0
    }

    override fun designStaircase(input: StaircaseInput): StaircaseResult {
        // SBC 304 يعتمد على ACI 318 مع تعديلات سعودية
        // نحسب من الصفر بالمعاملات السعودية المحددة
        val safetyChecks = mutableListOf<StairSafetyCheck>()
        val codeNotes = mutableListOf<String>()

        // ========== 1. التصميم الهندسي (نفس ACI) ==========
        var numRisers: Int
        var numTreads: Int
        var riserMm: Double
        var goingMm: Double

        if (input.riserCount > 0 && input.going > 0) {
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            goingMm = input.going
        } else if (input.riserCount > 0) {
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            val idealGoing = 610.0 - 2 * riserMm
            goingMm = idealGoing.coerceIn(MIN_GOING, 300.0)
        } else if (input.going > 0) {
            goingMm = input.going
            val treadsEstimate = (input.span * 1000.0 / goingMm).toInt().coerceIn(3, 40)
            numTreads = treadsEstimate
            numRisers = numTreads + 1
            riserMm = input.totalRise * 1000.0 / numRisers
            val targetRiser = (610.0 - goingMm) / 2.0
            val adjustedRisers = round(input.totalRise * 1000.0 / targetRiser).toInt().coerceIn(5, 40)
            if (abs(adjustedRisers - numRisers) > 1) {
                numRisers = adjustedRisers
                numTreads = numRisers - 1
                riserMm = input.totalRise * 1000.0 / numRisers
            }
        } else {
            var bestRiser = 175.0
            var bestDiff = Double.MAX_VALUE
            for (n in 5..40) {
                val r = input.totalRise * 1000.0 / n
                val t = n - 1
                val g = if (t > 0) input.span * 1000.0 / t else 300.0
                if (r <= MAX_RISER && g >= MIN_GOING) {
                    val comfort = 2 * r + g
                    val diff = abs(comfort - 610.0)
                    if (diff < bestDiff) { bestDiff = diff; bestRiser = r }
                }
            }
            bestRiser = bestRiser.coerceIn(150.0, MAX_RISER)
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

        // ========== 2. مقاومة الخرسانة (SBC) ==========
        val waistThickness = max(input.waistThickness, MIN_WAIST)
        val cover = COVER_INTERIOR // SBC: 40mm interior
        val stirrupEstimate = 10.0
        val effectiveDepth = waistThickness - cover - stirrupEstimate / 2 - 6.0

        // SBC 304: f'c = 0.67 × fcu / 1.5 (SBC conversion - more conservative than ACI's 0.8)
        val fc = 0.67 * input.fcu / 1.5
        val beta1 = calculateBeta1(fc)

        codeNotes.add("f'c = 0.67 × fcu / 1.5 = ${String.format("%.1f", fc)} MPa (SBC conversion)")
        codeNotes.add("β₁ = ${String.format("%.2f", beta1)}")
        codeNotes.add("SBC cover = ${cover.toInt()}mm (interior, exceeds ACI for hot climate)")

        safetyChecks.add(StairSafetyCheck(
            name = "Waist Thickness",
            value = waistThickness, limit = MIN_WAIST, unit = "mm",
            isSafe = waistThickness >= MIN_WAIST,
            description = "Minimum waist thickness per SBC 304"
        ))

        // ========== 3. الأحمال ==========
        val horizontalLoad = GAMMA_D * input.deadLoad / cosTheta.coerceAtLeast(0.1) + GAMMA_L * input.liveLoad
        val factoredOnSlope = horizontalLoad * cosTheta

        codeNotes.add("Factored horizontal load = ${String.format("%.2f", horizontalLoad)} kN/m²")
        codeNotes.add("Load factors: 1.2D + 1.6L per SBC 304-2018")

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
        codeNotes.add("Design moment Mu = ${String.format("%.2f", adjustedMoment)} kN.m/m")
        codeNotes.add("Design shear Vu = ${String.format("%.2f", adjustedShear)} kN/m")

        // ========== 5. تصميم الانحناء (Rn-ρ with SBC modifications) ==========
        val b = 1000.0
        val d = effectiveDepth
        val Mu = adjustedMoment * 1e6

        val denominator = PHI_FLEXURE * b * d * d
        val Rn = if (denominator > 0) Mu / denominator else 0.0

        val m = if (fc > 0) input.fy / (0.85 * fc) else 0.0
        val rho = if (m > 0 && (1 - 2 * m * Rn / input.fy) >= 0) {
            (1 - sqrt(1 - 2 * m * Rn / input.fy)) / m
        } else {
            0.0
        }

        val rhoMaxTc = 0.85 * beta1 * (fc / input.fy) * 0.375
        val epsilonCu = 0.003
        val epsilonY = input.fy / E_S
        val rhoBal = 0.85 * beta1 * (fc / input.fy) * (epsilonCu / (epsilonCu + epsilonY))

        codeNotes.add("Rn = ${String.format("%.2f", Rn)} MPa")
        codeNotes.add("ρ = ${String.format("%.4f", rho)}, ρ_bal = ${String.format("%.4f", rhoBal)}")

        var astRequired = rho * b * d

        // SBC 304: الحد الأدنى أعلى - 0.002 × b × d (مناخ حار)
        val minSteelRatio = SBC_MIN_STEEL_RATIO
        val minSteelArea = minSteelRatio * b * d

        if (astRequired < minSteelArea) {
            astRequired = minSteelArea
            codeNotes.add("SBC minimum steel applied: ${String.format("%.0f", minSteelArea)} mm²/m (0.002 × b × d)")
            codeNotes.add("SBC 304-2018: Higher ρmin for hot/arid climate durability")
        }

        safetyChecks.add(StairSafetyCheck(
            name = "Flexure (ρ ≤ ρ_max_tc)",
            value = rho, limit = rhoMaxTc, unit = "",
            isSafe = rho <= rhoMaxTc,
            description = "SBC 304-2018 Section 9.3.3.1"
        ))

        // اختيار حديد رئيسي
        val mainRebar = selectBars(astRequired)
        val mainRebarArea = parseBarArea(mainRebar)
        val rhoProvided = mainRebarArea / (b * d)

        // حديد التوزيع = 20% من الرئيسي
        val distAreaRequired = 0.2 * mainRebarArea
        val distAreaMin = 0.002 * b * waistThickness
        val distAreaFinal = max(distAreaRequired, distAreaMin)
        val distributionRebar = selectDistBars(distAreaFinal, input.stairWidth)
        val distributionRebarArea = parseDistBarArea(distributionRebar, input.stairWidth)

        codeNotes.add("Main reinforcement: $mainRebar (${String.format("%.0f", mainRebarArea)} mm²/m)")
        codeNotes.add("Distribution: $distributionRebar (${String.format("%.0f", distributionRebarArea)} mm²)")

        // ========== 6. تصميم القص ==========
        val Vu = adjustedShear * 1000.0
        val Vc = 0.17 * 1.0 * sqrt(fc) * b * d
        val phiVc = PHI_SHEAR * Vc / 1000.0

        val maxVs = 0.66 * sqrt(fc) * b * d / 1000.0
        val maxShearCapacity = phiVc + PHI_SHEAR * maxVs

        val shearSafe = (Vu / 1000.0) <= maxShearCapacity
        safetyChecks.add(StairSafetyCheck(
            name = "Shear Capacity",
            value = adjustedShear, limit = maxShearCapacity, unit = "kN/m",
            isSafe = shearSafe,
            description = "SBC 304-2018 Section 11"
        ))

        val requiredStirrups: String
        val stirrupDiameter: Double
        val stirrupSpacing: Double

        if ((Vu / 1000.0) <= phiVc * 0.5) {
            requiredStirrups = "None (concrete capacity sufficient)"
            stirrupDiameter = 0.0
            stirrupSpacing = 0.0
            codeNotes.add("φVc = ${String.format("%.2f", phiVc)} kN/m ≥ Vu = ${String.format("%.2f", adjustedShear)} kN/m")
        } else {
            val minAvs = max(
                0.062 * sqrt(fc) * b / input.fy,
                0.35 * b / input.fy
            ) * 1000.0

            if ((Vu / 1000.0) <= phiVc) {
                // Minimum shear reinforcement
                stirrupDiameter = 10.0
                val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4.0
                stirrupSpacing = (stirrupArea * 1000.0 / minAvs).coerceIn(50.0, 300.0)
                requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c (min)"
                codeNotes.add("Minimum shear reinforcement per SBC 304")
            } else {
                val Vs = ((Vu / 1000.0) - phiVc) / PHI_SHEAR
                val asvRequired = Vs * 1000.0 / (input.fy * d) * 1000.0
                val asvFinal = max(asvRequired, minAvs)

                stirrupDiameter = 10.0
                val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4.0
                stirrupSpacing = (stirrupArea * 1000.0 / asvFinal).coerceIn(50.0, 300.0)
                requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c"
                codeNotes.add("Shear reinforcement required: $requiredStirrups")
            }
        }

        // ========== 7. فحص الانحراف (SBC = ACI) ==========
        val basicRatio = 16.0
        val actualRatio = (span * 1000) / waistThickness
        val allowableRatio = basicRatio
        val deflectionRatio = actualRatio / allowableRatio

        val allowableDeflection = (span * 1000) / 240.0
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
            description = "SBC 304-2018 Section 9.5: L/d ≤ ${String.format("%.1f", allowableRatio)}"
        ))

        // ========== 8. فحوصات هندسية ==========
        safetyChecks.add(StairSafetyCheck(
            name = "2R + G (Comfort)",
            value = 2 * riserMm + goingMm, limit = COMFORT_2R_PLUS_G_MAX, unit = "mm",
            isSafe = 2 * riserMm + goingMm in COMFORT_2R_PLUS_G_MIN..COMFORT_2R_PLUS_G_MAX,
            description = "SBC comfort formula: 580 ≤ 2R+G ≤ 640 mm"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Riser ≤ 178mm",
            value = riserMm, limit = MAX_RISER, unit = "mm",
            isSafe = riserMm <= MAX_RISER,
            description = "Maximum riser height per SBC 304"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Going ≥ 279mm",
            value = goingMm, limit = MIN_GOING, unit = "mm",
            isSafe = goingMm >= MIN_GOING,
            description = "Minimum going per SBC 304"
        ))

        // ========== 9. SBC-specific: ملاحظات البيئة المالحة ==========
        codeNotes.add("SBC 304-2018: Section 9 (Flexure)")
        codeNotes.add("SBC 304-2018: Section 11 (Shear)")
        codeNotes.add("SBC 304-2018: Section 7.7 (Cover requirements)")
        codeNotes.add("SBC 304-2018: Higher ρmin = 0.002 for hot/arid climate durability")
        codeNotes.add("Note: For coastal areas, increase cover to ${COVER_COASTAL.toInt()}mm")
        codeNotes.add("Note: Epoxy-coated bars recommended in corrosive environments per SBC 304")
        codeNotes.add("Note: Seismic provisions (SBC 304 Section 21) may require additional checks")

        // ========== 10. فحص زلزالي أساسي ==========
        // SBC 304 البند 21: فحص بسيط للنسب الزلزالية
        if (input.stairWidth < 1000.0) {
            codeNotes.add("SBC 304 Seismic: Stair width < 1.0m may need special detailing")
        }

        return StaircaseResult(
            isSafe = safetyChecks.all { it.isSafe },
            designCode = DesignCode.SBC,
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
            reinforcementRatio = rhoProvided,
            minSteelRatio = minSteelRatio,
            shearCapacity = phiVc,
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

    private fun calculateBeta1(fc: Double): Double {
        return when {
            fc <= 28 -> 0.85
            fc >= 55 -> 0.65
            else -> 0.85 - (0.05 * (fc - 28) / 7)
        }
    }

    /**
     * اختيار أسياخ رئيسية (مقاسات السوق السعودي)
     */
    private fun selectBars(requiredArea: Double): String {
        val availableBars = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)
        val barDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            val numBars = ceil(requiredArea / area).toInt()
            val spacing = 1000.0 / numBars
            spacing >= 100.0 && spacing <= 200.0
        } ?: 16.0

        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredArea / barArea).toInt().coerceAtLeast(5)
        val spacing = 1000.0 / numBars
        return "Ø${barDia.toInt()} @ ${spacing.toInt()} mm c/c"
    }

    private fun selectDistBars(requiredAreaTotal: Double, stairWidthM: Double): String {
        val availableBars = listOf(10.0, 12.0, 16.0)
        val barDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            val numBars = ceil(requiredAreaTotal / area).toInt()
            numBars in 3..20
        } ?: 10.0

        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredAreaTotal / barArea).toInt().coerceAtLeast(4)
        val spacing = (stairWidthM * 1000.0 / numBars)
        return "Ø${barDia.toInt()} @ ${spacing.toInt()} mm c/c"
    }

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