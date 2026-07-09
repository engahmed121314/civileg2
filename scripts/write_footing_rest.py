#!/usr/bin/env python3
"""Append the missing part of ECPFooting.kt"""

filepath = '/home/z/my-project/civileg2/app/src/main/java/com/civileg/app/domain/calculations/ecp/ECPFooting.kt'

# Read current file
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Find the truncation point - cut at line 302
lines = content.split('\n')
# Keep lines 1-302 (0-indexed: 0-301)
kept = '\n'.join(lines[:302])

rest = '''
    /**
     * حساب أقل سمك مطلوب لقص الاختراق
     */
    private fun calcMinDepthForPunching(fcu: Double, colW: Double, colD: Double, Pu: Double): Double {
        val qp = 0.316 * sqrt(fcu / GAMMA_C) // MPa
        val b0 = 2.0 * (colW + colD + 4.0 * 1.0) // تقريبي
        // Pu*1000 = qp * b0 * d => d = Pu*1000 / (qp * b0)
        return (Pu * 1000.0) / (qp * b0)
    }

    private enum class Direction { X, Y }

    // ===================== قص الاختراق =====================
    override fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double,
        loadCombination: LoadCombination
    ): ShearCheckResult {
        val b0 = 2.0 * ((columnWidth + effectiveDepth) + (columnDepth + effectiveDepth))
        val capacity = getPunchingShearCapacity(fcu, b0, effectiveDepth)
        val actualStress = (punchingShearForce * 1000.0) / (b0 * effectiveDepth)

        return ShearCheckResult(
            appliedShear = actualStress,
            shearCapacity = capacity,
            isSafe = actualStress <= capacity,
            utilizationRatio = actualStress / capacity,
            criticalPerimeter = b0
        )
    }

    override fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double {
        // ECP 203 البند 4-3-2: qp = 0.316 * sqrt(fcu / gamma_c) MPa
        return 0.316 * sqrt(fcu / GAMMA_C)
    }

    // ===================== تسليح القاعدة =====================
    override fun calculateFootingReinforcement(
        fcu: Double,
        fy: Double,
        footingWidth: Double,
        footingLength: Double,
        effectiveDepth: Double,
        designMoment: Double,
        direction: FootingDirection
    ): ReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val b = 1000.0 // mm - عرض الشريحة = 1m
        val Mu = designMoment * 1e6 // N.mm

        // طريقة K حسب ECP 203
        val fc = 0.67 * fcu / GAMMA_C
        val fs = fy / GAMMA_S

        val K = Mu / (fc * b * effectiveDepth * effectiveDepth)
        val K_bal = 0.186 // لـ fcu=25, fy=360

        if (K > K_bal) {
            warnings.add("المقطع مفرط التسليح - زِد سمك القاعدة")
        }

        // ذراع العزم ECP 203 البند 4-2-1
        val kFactor = K / 0.9
        val leverArm = if (kFactor < 0.25) {
            effectiveDepth * (0.5 + sqrt(0.25 - kFactor))
        } else {
            effectiveDepth * 0.7
        }

        // مساحة التسليح المطلوبة mm2/m
        var asRequired = Mu / (fs * leverArm)

        // الحد الأدنى للتسليح ECP 203 البند 7-1
        val asMin = max(
            MIN_REIN_RATIO * b * effectiveDepth,
            0.26 * (fcu / fy) * b * effectiveDepth / (GAMMA_C * GAMMA_S)
        )
        if (asRequired < asMin) {
            asRequired = asMin
            codeNotes.add("تم تطبيق التسليح الأدنى (%.0f mm2/m)".format(asMin))
        }

        // اختيار السيخ
        val barDiameter = selectBarDiameter(asRequired)
        val barArea = PI * barDiameter * barDiameter / 4
        val barsPerMeter = ceil(asRequired / barArea).toInt().coerceIn(5, 25)
        val spacing = (1000.0 / barsPerMeter).let { ceil(it / 10.0) * 10.0 }
        // أقصى تباعد ECP 203 البند 7-1-3
        val maxSpacing = min(300.0, 3.0 * effectiveDepth)
        val finalSpacing = spacing.coerceAtMost(maxSpacing)
        val asProvided = (1000.0 / finalSpacing) * barArea

        val utilization = asRequired / asProvided
        codeNotes.add("%s: %d dia %d @ %dmm".format(direction.name, (1000.0 / finalSpacing).toInt(), barDiameter.toInt(), finalSpacing.toInt()))

        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = barsPerMeter,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = utilization <= 1.0 && K <= K_bal,
            utilizationRatio = utilization,
            spacing = finalSpacing,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== القاعدة المشتركة =====================
    override fun designCombinedFooting(
        fcu: Double,
        fy: Double,
        axialLoad1: Double,
        axialLoad2: Double,
        distanceBetweenColumns: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. الأحمال عند مستوى الخدمة
        val P1_s = axialLoad1 / loadCombination.factor
        val P2_s = axialLoad2 / loadCombination.factor
        val totalP_s = P1_s + P2_s
        val L_dist = distanceBetweenColumns / 1000.0

        // 2. موقع محصلة القوى من العمود الأول
        val xR = P2_s * L_dist / totalP_s

        // 3. حساب طول القاعدة (محصلة في المنتصف)
        val areaRequired = totalP_s * 1.1 / soilBearingCapacity
        val minProjection = 0.15 // m
        val length_m = max(sqrt(areaRequired * 2.5), 2.0 * xR + 2.0 * minProjection)
        val length = ceil(length_m * 1000 / 50.0) * 50.0

        // عرض القاعدة
        val width_m = areaRequired / (length / 1000.0)
        val width = ceil(width_m * 1000 / 50.0) * 50.0

        // 4. النواتئ
        val L_actual = length / 1000.0
        val e1 = L_actual - xR
        val e2 = xR

        // 5. ضغط التربة
        val A = length * width / 1e6
        val qu_s = totalP_s / A
        val qu_u = (axialLoad1 + axialLoad2) / A

        // 6. السمك الفعال
        val d = footingDepth - getMinCover() - 10.0

        // 7. عزم موجب عند النواتئ (تسليح سفلي)
        val proj1 = e1.coerceAtLeast(0.1)
        val proj2 = e2.coerceAtLeast(0.1)
        val Mu_pos1 = qu_u * (width / 1000.0) * proj1 * proj1 / 2.0
        val Mu_pos2 = qu_u * (width / 1000.0) * proj2 * proj2 / 2.0

        // 8. عزم سالب بين العمودين (تسليح علوي) - تقريبي
        val negMoment = qu_u * (width / 1000.0) * proj1 * proj1 / 2.0

        // 9. تصميم التسليح السفلي
        val maxPosMoment = max(Mu_pos1, Mu_pos2)
        val reinfBottom = calculateFootingReinforcement(
            fcu, fy, width, length, d,
            maxPosMoment / (width / 1000.0), FootingDirection.SHORT
        )

        // 10. تصميم التسليح العلوي
        val reinfTop = if (negMoment > 0) {
            calculateFootingReinforcement(
                fcu, fy, width, length, d,
                negMoment / (width / 1000.0), FootingDirection.SHORT
            )
        } else {
            ReinforcementResult(
                astRequired = 0.0, astProvided = 0.0, barDiameter = 12.0,
                numberOfBars = 0, tiesDiameter = 0.0, tiesSpacing = 0.0,
                isSafe = true, utilizationRatio = 0.0
            )
        }

        // 11. فحص قص الاختراق
        val punching1 = checkPunchingShear(fcu, 400.0, 400.0, d, axialLoad1, loadCombination)
        val punching2 = checkPunchingShear(fcu, 400.0, 400.0, d, axialLoad2, loadCombination)

        codeNotes.add("ECP 203: Combined Footing Design")
        codeNotes.add("Resultant from Col-1: %.0f mm".format(xR * 1000))
        codeNotes.add("Length=%.0f mm, Width=%.0f mm".format(length, width))
        codeNotes.add("Bottom: %s".format(reinfBottom.barString))
        codeNotes.add("Top: %s".format(reinfTop.barString))

        return FootingDesignResult(
            requiredWidth = width,
            requiredLength = length,
            requiredThickness = footingDepth,
            soilPressure = qu_u,
            maxSoilPressure = qu_u,
            reinforcement = reinfBottom,
            punchingShearCheck = if (punching1.utilizationRatio > punching2.utilizationRatio) punching1 else punching2,
            isSafe = punching1.isSafe && punching2.isSafe && qu_s <= soilBearingCapacity,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== اللبشة Raft Foundation =====================
    override fun designRaftFoundation(
        fcu: Double,
        fy: Double,
        totalLoads: Double,
        totalArea: Double,
        moments: Pair<Double, Double>,
        soilBearingCapacity: Double,
        raftThickness: Double
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. ضغط التربة
        val P_service = totalLoads / 1.5
        val q_avg = P_service / totalArea

        val B = sqrt(totalArea)
        val Sx = B * B * B / 6.0
        val q_momentX = abs(moments.first) / Sx
        val q_momentY = abs(moments.second) / Sx
        val q_max = q_avg + q_momentX + q_momentY
        val q_min = q_avg - q_momentX - q_momentY

        if (q_max > soilBearingCapacity) {
            warnings.add("ضغط التربة الأقصى يتجاوز قدرة التربة!")
        }
        if (q_min < 0) {
            warnings.add("انفصال اللبشة عن التربة")
        }

        // 2. السمك الفعال
        val d = raftThickness - getMinCover() - 10.0

        // 3. معاملات البلاطة المستمرة (Rigid Method)
        val q_ult = totalLoads / totalArea * 1.5
        val L_strip = B * 1000.0

        // عزم موجب (وسط البحر): M = q*L^2 / 10
        val Mu_pos = q_ult * (L_strip / 1000.0).pow(2) / 10.0
        // عزم سالب (عند الدعامات): M = q*L^2 / 12
        val Mu_neg = q_ult * (L_strip / 1000.0).pow(2) / 12.0

        // 4. تصميم التسليح السفلي
        val reinfBotX = calculateFootingReinforcement(fcu, fy, 1000.0, L_strip, d, Mu_pos, FootingDirection.SHORT)
        val reinfBotY = calculateFootingReinforcement(fcu, fy, L_strip, 1000.0, d, Mu_pos, FootingDirection.LONG)

        // 5. تصميم التسليح العلوي
        val reinfTopX = calculateFootingReinforcement(fcu, fy, 1000.0, L_strip, d, Mu_neg, FootingDirection.SHORT)
        val reinfTopY = calculateFootingReinforcement(fcu, fy, L_strip, 1000.0, d, Mu_neg, FootingDirection.LONG)

        // 6. فحص القص
        val punchingCheck = checkPunchingShear(fcu, 400.0, 400.0, d, totalLoads * 0.2, LoadCombination.DEAD_LIVE)

        codeNotes.add("ECP 203: Raft Foundation (Rigid Method)")
        codeNotes.add("Average pressure: %.1f kPa".format(q_avg))
        codeNotes.add("Bot X: %s, Y: %s".format(reinfBotX.barString, reinfBotY.barString))
        codeNotes.add("Top X: %s, Y: %s".format(reinfTopX.barString, reinfTopY.barString))
        codeNotes.add("M+ = qL2/10 = %.1f kN.m/m".format(Mu_pos))
        codeNotes.add("M- = qL2/12 = %.1f kN.m/m".format(Mu_neg))

        val reinf = if (reinfBotX.astRequired >= reinfBotY.astRequired) reinfBotX else reinfBotY

        return FootingDesignResult(
            requiredWidth = B * 1000.0,
            requiredLength = B * 1000.0,
            requiredThickness = raftThickness,
            soilPressure = q_avg,
            maxSoilPressure = q_max,
            reinforcement = reinf,
            punchingShearCheck = punchingCheck,
            isSafe = q_max <= soilBearingCapacity && q_min >= 0 && punchingCheck.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== قبعة الركائز Pile Cap =====================
    override fun designPileCap(
        fcu: Double,
        fy: Double,
        pileLoad: Double,
        numberOfPiles: Int,
        pileDiameter: Double,
        columnLoads: Double
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. ترتيب الركائز
        val pileLayout = when {
            numberOfPiles <= 1 -> PileLayout.ONE
            numberOfPiles <= 2 -> PileLayout.TWO
            numberOfPiles <= 4 -> PileLayout.FOUR
            numberOfPiles <= 6 -> PileLayout.SIX
            else -> PileLayout.NINE
        }

        // 2. أبعاد القبعة
        val spacing = max(3.0 * pileDiameter, 750.0)
        val edgeDist = pileDiameter + 100.0

        val capW = when (pileLayout) {
            PileLayout.ONE -> pileDiameter + 2 * edgeDist
            PileLayout.TWO, PileLayout.FOUR -> spacing + 2 * edgeDist
            PileLayout.SIX, PileLayout.NINE -> 2 * spacing + 2 * edgeDist
        }.let { ceil(it / 50.0) * 50.0 }
        val capL = when (pileLayout) {
            PileLayout.ONE, PileLayout.TWO -> pileDiameter + 2 * edgeDist
            PileLayout.FOUR -> spacing + 2 * edgeDist
            PileLayout.SIX, PileLayout.NINE -> 2 * spacing + 2 * edgeDist
        }.let { ceil(it / 50.0) * 50.0 }

        // 3. السمك
        val minThickness = max(spacing / 3.0, 400.0)
        val capThickness = ceil(minThickness / 50.0) * 50.0
        val d = capThickness - getMinCover() - 10.0

        // 4. فحص قص الاختراق عند العمود
        val colSize = 400.0
        val punchingResult = checkPunchingShear(fcu, colSize, colSize, d, columnLoads, LoadCombination.DEAD_LIVE)

        // 5. فحص قص الركيزة
        val pilePerimeter = PI * pileDiameter
        val loadPerPile = columnLoads / numberOfPiles
        val pileShearStress = (loadPerPile * 1000.0) / (pilePerimeter * d)
        val pileShearCap = 0.316 * sqrt(fcu / GAMMA_C)

        if (pileShearStress > pileShearCap) {
            warnings.add("قص الركيزة يتجاوز القدرة - زِد سمك القبعة")
        }

        // 6. عزم الانحناء (ناتئ من وجه العمود لوجه الركيزة)
        val projection = (spacing / 2.0 + pileDiameter / 2.0 - colSize / 2.0) / 1000.0
        val Mu = loadPerPile * projection

        // 7. تصميم التسليح
        val reinf = calculateFootingReinforcement(fcu, fy, capW, capL, d, Mu / (capW / 1000.0), FootingDirection.SHORT)

        // 8. فحص زاوية الخرسانة المضغوطة (Truss Analogy)
        val strutAngle = atan2(d, projection * 1000.0) * 180.0 / PI
        if (strutAngle < 40.0) {
            warnings.add("زاوية الخرسانة المضغوطة أقل من 40 درجة - زِد السمك")
        }

        codeNotes.add("ECP 203: Pile Cap (Truss Analogy)")
        codeNotes.add("Layout: %s (%d piles)".format(pileLayout.name, numberOfPiles))
        codeNotes.add("Spacing: %.0f mm (3x pile dia)".format(spacing))
        codeNotes.add("Strut angle: %.1f deg (min 40)".format(strutAngle))
        codeNotes.add("Load/pile: %.0f kN".format(loadPerPile))
        codeNotes.add("Bottom: %s".format(reinf.barString))

        return FootingDesignResult(
            requiredWidth = capW,
            requiredLength = capL,
            requiredThickness = capThickness,
            soilPressure = loadPerPile,
            maxSoilPressure = pileLoad,
            reinforcement = reinf,
            punchingShearCheck = punchingResult,
            isSafe = punchingResult.isSafe && pileShearStress <= pileShearCap && strutAngle >= 40.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun getMinFootingThickness(): Double = MIN_THICKNESS
    override fun getMinCover(): Double = 50.0

    private fun selectBarDiameter(asRequired: Double): Double {
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        return availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4
            val barsPerMeter = ceil(asRequired / area).toInt()
            barsPerMeter in 5..20
        } ?: 16.0
    }

    private enum class PileLayout { ONE, TWO, FOUR, SIX, NINE }
}'''

# Write complete file
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(kept + rest)

print(f'Written {len(kept) + len(rest)} chars to {filepath}')
print(f'Kept: {len(kept)} chars, Added: {len(rest)} chars')
