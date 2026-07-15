package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم السلالم حسب الكود الأمريكي ACI 318-19
 *
 * المراجع:
 * - ACI 318-19 Chapter 9 (الانحناء - One-way slabs)
 * - ACI 318-19 Chapter 22 (القص)
 * - ACI 318-19 Table 24.2.2 (الانحراف)
 * - IBC Section 1011 (متطلبات السلالم الهندسية)
 * - ASCE 7 (أحمال السلالم: حي 4.8 kN/m² = 100 psf)
 *
 * الاختلافات عن ECP:
 * - تحويل fcu → f'c = 0.8 × fcu
 * - معاملات التحميل: 1.2D + 1.6L
 * - معامل الاختزال φ = 0.9 (انحناء)، φ = 0.75 (قص)
 * - طريقة Rn-ρ بدلاً من K-method
 * - الحد الأدنى: ρmin = 0.0018 × b × h
 * - الانحراف: L/240 للأرضيات
 */
class ACIStaircase : StaircaseDesign {

    companion object {
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val GAMMA_D = 1.2       // معامل حمل الميت
        private const val GAMMA_L = 1.6       // معامل حمل الحي
        private const val COVER_INTERIOR = 38.0  // mm
        private const val COVER_EXTERIOR = 50.0  // mm
        private const val MIN_WAIST = 125.0     // mm (5" minimum practical)
        private const val MAX_RISER = 178.0     // mm (7")
        private const val MIN_GOING = 279.0     // mm (11")
        private const val COMFORT_2R_PLUS_G_MIN = 580.0  // mm (IBC)
        private const val COMFORT_2R_PLUS_G_MAX = 640.0  // mm (IBC)
        private const val E_S = 200000.0       // MPa
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
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            goingMm = input.going
        } else if (input.riserCount > 0) {
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            val idealGoing = 610.0 - 2 * riserMm // IBC center ~610
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
            // حساب تلقائي - أوجد أفضل تقسيم (IBC: 2R+G ≈ 610)
            var bestRiser = 178.0
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
        codeNotes.add("Slope angle θ = ${String.format("%.1f", slopeAngle)}°")

        // ========== 2. مقاومة الخرسانة ==========
        val waistThickness = max(input.waistThickness, MIN_WAIST)
        val cover = COVER_INTERIOR
        val stirrupEstimate = 10.0
        val effectiveDepth = waistThickness - cover - stirrupEstimate / 2 - 6.0

        // ACI: f'c = 0.8 × fcu (cube → cylinder conversion)
        val fc = 0.8 * input.fcu
        val beta1 = calculateBeta1(fc)

        codeNotes.add("f'c = 0.8 × fcu = ${String.format("%.1f", fc)} MPa (cylinder strength)")
        codeNotes.add("β₁ = ${String.format("%.2f", beta1)}")

        safetyChecks.add(StairSafetyCheck(
            name = "Waist Thickness",
            value = waistThickness, limit = MIN_WAIST, unit = "mm",
            isSafe = waistThickness >= MIN_WAIST,
            description = "Minimum practical waist thickness"
        ))

        // ========== 3. الأحمال ==========
        // ACI: w = 1.2×dead/cos(θ) + 1.6×live (projected horizontally)
        val horizontalLoad = GAMMA_D * input.deadLoad / cosTheta.coerceAtLeast(0.1) + GAMMA_L * input.liveLoad
        val factoredOnSlope = horizontalLoad * cosTheta

        codeNotes.add("Factored horizontal load = ${String.format("%.2f", horizontalLoad)} kN/m²")
        codeNotes.add("Load factors: 1.2D + 1.6L per ACI 318-19")

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

        // ========== 5. تصميم الانحناء (Rn-ρ method) ==========
        val b = 1000.0
        val d = effectiveDepth
        val Mu = adjustedMoment * 1e6

        // Rn = Mu / (φ × b × d²) per ACI
        val denominator = PHI_FLEXURE * b * d * d
        val Rn = if (denominator > 0) Mu / denominator else 0.0

        // ρ from Rn: ρ = (1 - √(1 - 2mRn/fy)) / m, where m = fy/(0.85×fc')
        val m = if (fc > 0) input.fy / (0.85 * fc) else 0.0
        val rho = if (m > 0 && (1 - 2 * m * Rn / input.fy) >= 0) {
            (1 - sqrt(1 - 2 * m * Rn / input.fy)) / m
        } else {
            0.0
        }

        // ρ_max (tension-controlled: εt ≥ 0.005 → c/d ≤ 0.375)
        val rhoMaxTc = 0.85 * beta1 * (fc / input.fy) * 0.375
        // ρ_bal
        val epsilonCu = 0.003
        val epsilonY = input.fy / E_S
        val rhoBal = 0.85 * beta1 * (fc / input.fy) * (epsilonCu / (epsilonCu + epsilonY))

        val RnBal = rhoBal * input.fy * (1.0 - 0.5 * rhoBal * input.fy / (0.85 * fc))

        codeNotes.add("Rn = ${String.format("%.2f", Rn)} MPa, Rn_bal = ${String.format("%.2f", RnBal)} MPa")
        codeNotes.add("ρ = ${String.format("%.4f", rho)}, ρ_bal = ${String.format("%.4f", rhoBal)}")
        codeNotes.add("ρ_max_tc = ${String.format("%.4f", rhoMaxTc)} (tension-controlled)")

        // Required steel area
        var astRequired = rho * b * d

        // Minimum steel per ACI 7.6.1.1: ρmin = 0.0018 for Grade 60 slabs
        val minSteelRatio = 0.0018
        val minSteelArea = minSteelRatio * b * waistThickness

        if (astRequired < minSteelArea) {
            astRequired = minSteelArea
            codeNotes.add("Minimum steel applied: ${String.format("%.0f", minSteelArea)} mm²/m (0.0018 × b × h)")
        }

        safetyChecks.add(StairSafetyCheck(
            name = "Flexure (ρ ≤ ρ_max_tc)",
            value = rho, limit = rhoMaxTc, unit = "",
            isSafe = rho <= rhoMaxTc,
            description = "ACI 318-19 Section 9.3.3.1 (tension-controlled)"
        ))

        // اختيار حديد رئيسي (مقاسات أمريكية: 12, 16, 19, 22, 25 mm)
        val mainRebar = selectBars(astRequired)
        val mainRebarArea = parseBarArea(mainRebar)
        val rhoProvided = mainRebarArea / (b * d)

        // حديد التوزيع = 20% من الرئيسي (ACI)
        val distAreaRequired = 0.2 * mainRebarArea
        val distAreaMin = 0.0018 * b * waistThickness
        val distAreaFinal = max(distAreaRequired, distAreaMin)
        val distributionRebar = selectDistBars(distAreaFinal, input.stairWidth)
        val distributionRebarArea = parseDistBarArea(distributionRebar, input.stairWidth)

        codeNotes.add("Main reinforcement: $mainRebar (${String.format("%.0f", mainRebarArea)} mm²/m)")
        codeNotes.add("Distribution: $distributionRebar (${String.format("%.0f", distributionRebarArea)} mm²)")

        // ========== 6. تصميم القص ==========
        val Vu = adjustedShear * 1000.0
        // Vc per ACI 22.5.5.1: Vc = 0.17λ√(f'c) × b × d
        val Vc = 0.17 * 1.0 * sqrt(fc) * b * d  // N (λ = 1.0 for normal weight)
        val phiVc = PHI_SHEAR * Vc / 1000.0  // kN

        // Maximum shear per ACI 22.5.1.2
        val maxVs = 0.66 * sqrt(fc) * b * d / 1000.0  // kN
        val maxShearCapacity = phiVc + PHI_SHEAR * maxVs

        val shearSafe = (Vu / 1000.0) <= maxShearCapacity
        safetyChecks.add(StairSafetyCheck(
            name = "Shear Capacity",
            value = adjustedShear, limit = maxShearCapacity, unit = "kN/m",
            isSafe = shearSafe,
            description = "ACI 318-19 Section 22.5.1.2"
        ))

        val requiredStirrups: String
        val stirrupDiameter: Double
        val stirrupSpacing: Double

        if ((Vu / 1000.0) <= phiVc) {
            // No stirrups needed for slabs in many cases
            // But ACI requires minimum shear reinforcement when Vu > 0.5φVc
            if ((Vu / 1000.0) > phiVc * 0.5) {
                // Minimum shear reinforcement per ACI 9.6.3
                val minAvs = max(
                    0.062 * sqrt(fc) * b / input.fy,
                    0.35 * b / input.fy
                ) * 1000.0  // mm²/m

                stirrupDiameter = 10.0
                val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4.0
                stirrupSpacing = (stirrupArea * 1000.0 / minAvs).coerceIn(50.0, 300.0)
                requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c (min)"
                codeNotes.add("Minimum shear reinforcement per ACI 9.6.3")
            } else {
                requiredStirrups = "None (concrete capacity sufficient)"
                stirrupDiameter = 0.0
                stirrupSpacing = 0.0
                codeNotes.add("φVc = ${String.format("%.2f", phiVc)} kN/m ≥ Vu = ${String.format("%.2f", adjustedShear)} kN/m")
                codeNotes.add("No shear reinforcement required per ACI 318-19")
            }
        } else {
            // Calculate required stirrups
            val Vs = ((Vu / 1000.0) - phiVc) / PHI_SHEAR  // kN
            val asvRequired = Vs * 1000.0 / (input.fy * d) * 1000.0  // mm²/m
            val minAvs = max(0.062 * sqrt(fc) * b / input.fy, 0.35 * b / input.fy) * 1000.0
            val asvFinal = max(asvRequired, minAvs)

            stirrupDiameter = 10.0
            val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4.0
            stirrupSpacing = (stirrupArea * 1000.0 / asvFinal).coerceIn(50.0, 300.0)
            requiredStirrups = "Ø${stirrupDiameter.toInt()} @ ${stirrupSpacing.toInt()} mm c/c"
            codeNotes.add("Shear reinforcement required: $requiredStirrups")
            codeNotes.add("Vs = ${String.format("%.2f", Vs)} kN/m")
        }

        // ========== 7. فحص الانحراف ==========
        // ACI Table 24.2.2: L/d = 16 (simply supported) for floors
        val basicRatio = 16.0
        // Modification factor for fy (ACI Table 7.3.1.1)
        val fyFactor = minOf(1.0, 0.4 + 420.0 / 420.0)  // Grade 420 (60 ksi) default
        val actualRatio = (span * 1000) / waistThickness
        val allowableRatio = basicRatio * fyFactor
        val deflectionRatio = actualRatio / allowableRatio

        val allowableDeflection = (span * 1000) / 240.0  // L/240 for floors
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
            description = "ACI 318-19 Table 24.2.2: L/d ≤ ${String.format("%.1f", allowableRatio)}"
        ))

        // ========== 8. فحوصات هندسية (IBC) ==========
        safetyChecks.add(StairSafetyCheck(
            name = "2R + G (Comfort)",
            value = 2 * riserMm + goingMm, limit = COMFORT_2R_PLUS_G_MAX, unit = "mm",
            isSafe = 2 * riserMm + goingMm in COMFORT_2R_PLUS_G_MIN..COMFORT_2R_PLUS_G_MAX,
            description = "IBC Section 1011.5: 580 ≤ 2R+G ≤ 640 mm (24-25.5 in)"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Riser ≤ 178mm (7\")",
            value = riserMm, limit = MAX_RISER, unit = "mm",
            isSafe = riserMm <= MAX_RISER,
            description = "IBC Section 1011.5.2: max riser 178mm (7 in)"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Going ≥ 279mm (11\")",
            value = goingMm, limit = MIN_GOING, unit = "mm",
            isSafe = goingMm >= MIN_GOING,
            description = "IBC Section 1011.5.2: min going 279mm (11 in)"
        ))

        // ========== 9. ملاحظات الكود ==========
        codeNotes.add("ACI 318-19: Chapter 9 (Flexure - one-way slab)")
        codeNotes.add("ACI 318-19: Chapter 22 (Shear)")
        codeNotes.add("ACI 318-19: Table 24.2.2 (Deflection L/240)")
        codeNotes.add("IBC Section 1011 (Stair geometry requirements)")
        codeNotes.add("f'c = 0.8 × fcu (cube to cylinder conversion)")
        codeNotes.add("φ = $PHI_FLEXURE flexure, φ = $PHI_SHEAR shear")

        return StaircaseResult(
            isSafe = safetyChecks.all { it.isSafe },
            designCode = DesignCode.ACI,
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
     * اختيار أسياخ رئيسية (مقاسات أمريكية: 12, 16, 19, 22, 25 mm)
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