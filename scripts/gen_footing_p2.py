#!/usr/bin/env python3
"""Generate clean ECPFooting.kt - Part 2: Raft, Pile Cap, Helpers (appended to Part 1)"""
import os

target = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app/domain/calculations/ecp/ECPFooting.kt"

part2 = '''
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
        val reinfBotX = calculateFootingReinforcement(
            fcu, fy, 1000.0, L_strip, d, Mu_pos, FootingDirection.SHORT
        )
        val reinfBotY = calculateFootingReinforcement(
            fcu, fy, L_strip, 1000.0, d, Mu_pos, FootingDirection.LONG
        )

        // 5. تصميم التسليح العلوي
        val reinfTopX = calculateFootingReinforcement(
            fcu, fy, 1000.0, L_strip, d, Mu_neg, FootingDirection.SHORT
        )
        val reinfTopY = calculateFootingReinforcement(
            fcu, fy, L_strip, 1000.0, d, Mu_neg, FootingDirection.LONG
        )

        // 6. فحص القص
        val punchingCheck = checkPunchingShear(
            fcu, 400.0, 400.0, d, totalLoads * 0.2, LoadCombination.DEAD_LIVE
        )

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

        // 2. أبعاد القبعة - المسافة بين الركائز >= 3x قطر الركيزة
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

        // 3. السمك الأدنى: max(spacing/3, 400mm)
        val minThickness = max(spacing / 3.0, 400.0)
        val capThickness = ceil(minThickness / 50.0) * 50.0
        val d = capThickness - getMinCover() - 10.0

        // 4. فحص قص الاختراق عند العمود
        val colSize = 400.0
        val punchingResult = checkPunchingShear(
            fcu, colSize, colSize, d, columnLoads, LoadCombination.DEAD_LIVE
        )

        // 5. فحص قص الركيزة (One-way shear at pile perimeter)
        val pilePerimeter = PI * pileDiameter
        val loadPerPile = columnLoads / numberOfPiles
        val pileShearStress = (loadPerPile * 1000.0) / (pilePerimeter * d)
        val pileShearCap = 0.316 * sqrt(fcu / GAMMA_C)

        if (pileShearStress > pileShearCap) {
            warnings.add("قص الركيزة يتجاوز القدرة - زِد سمك القبعة")
        }

        // 6. عزم الانحناء (ناتئ من وجه العمود لوسط الركيزة)
        val projection = (spacing / 2.0 + pileDiameter / 2.0 - colSize / 2.0) / 1000.0
        val Mu = loadPerPile * projection

        // 7. تصميم التسليح
        val reinf = calculateFootingReinforcement(
            fcu, fy, capW, capL, d, Mu / (capW / 1000.0), FootingDirection.SHORT
        )

        // 8. فحص زاوية الخرسانة المضغوطة (Truss Analogy - min 40 deg)
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
            isSafe = punchingResult.isSafe
                && pileShearStress <= pileShearCap
                && strutAngle >= 40.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== طرق مساعدة =====================
    override fun getMinFootingThickness(): Double = MIN_THICKNESS

    override fun getMinCover(): Double = 50.0

    override fun getPunchingShearCapacity(
        fcu: Double,
        perimeter: Double,
        effectiveDepth: Double
    ): Double {
        // qp = 0.316 * sqrt(fcu/gamma_c) * bo * d (kN)
        val qp = 0.316 * sqrt(fcu / GAMMA_C)
        return qp * perimeter * effectiveDepth / 1000.0
    }

    private fun selectBarDiameter(asRequired: Double): Double {
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        return availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4
            val barsPerMeter = ceil(asRequired / area).toInt()
            barsPerMeter in 5..20
        } ?: 16.0
    }

    /**
     * ترتيب الركائز في القبعة
     */
    private enum class PileLayout { ONE, TWO, FOUR, SIX, NINE }
}
'''

with open(target, 'a', encoding='utf-8') as f:
    f.write(part2)

print("Part 2 appended. Total: %d bytes" % os.path.getsize(target))