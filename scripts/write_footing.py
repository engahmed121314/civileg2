import os

KOTLIN_FILE = '/home/z/my-project/civileg2/app/src/main/java/com/civileg/app/domain/calculations/ecp/ECPFooting.kt'

content = r'''package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult
import kotlin.math.*

/**
 * محرك تصميم القواعد حسب الكود المصري ECP 203-2020
 * يغطي: القواعد المنفصلة، المشتركة، اللبشة، وقبعات الركائز
 *
 * المراجع:
 * - ECP 203-2020 البند 7-1 (الأساسات)
 * - ECP 203-2020 البند 4-3-2 (قص الاختراق)
 * - ECP 203-2020 البند 4-3-1 (القص العادي)
 * - ECP 203-2020 البند 7-1-3 (التسليح التوزيعي)
 */
class ECPFooting : FootingDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val GAMMA_CONCRETE = 25.0 // kN/m3
        private const val MIN_REIN_RATIO = 0.0015 // 0.15% ECP 203 Table 4-8
        private const val MIN_THICKNESS = 300.0 // mm
    }

    // ===================== القاعدة المنفصلة =====================
    override fun designIsolatedFooting(
        fcu: Double,
        fy: Double,
        columnWidth: Double,
        columnDepth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination,
        constraints: BoundaryConstraints
    ): FootingDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. الأحمال عند مستوى الخدمة لحساب الأبعاد
        val P_service = axialLoad / loadCombination.factor
        val Mx_service = momentX / loadCombination.factor
        val My_service = momentY / loadCombination.factor

        // 2. تقدير أولي لوزن القاعدة (تعديل الضغط الصافي)
        val areaInitial = P_service / soilBearingCapacity
        val selfWeight = GAMMA_CONCRETE * areaInitial * (footingDepth / 1000.0) * 1.15
        val netLoad = P_service + selfWeight

        // 3. حساب الأبعاد مع مراعاة العزم
        val areaRequired = netLoad / soilBearingCapacity
        val ex = if (netLoad > 0) Mx_service / netLoad else 0.0
        val ey = if (netLoad > 0) My_service / netLoad else 0.0
        val sqrtA0 = sqrt(areaRequired)
        val areaWithMoment = areaRequired * (1.0 + 6.0 * abs(ex) / sqrtA0 + 6.0 * abs(ey) / sqrtA0)

        var width: Double
        var length: Double
        val colRatio = columnWidth / columnDepth

        if (abs(colRatio - 1.0) < 0.15) {
            val side = ceil(sqrt(areaWithMoment) * 1000 / 50.0) * 50.0
            width = side
            length = side
        } else {
            val ratio = columnWidth / columnDepth
            length = ceil(sqrt(areaWithMoment / ratio) * 1000 / 50.0) * 50.0
            width = ceil(areaWithMoment / (length / 1000.0) * 1000 / 50.0) * 50.0
        }

        // قيود الجار
        constraints.maxLeft?.let { maxL ->
            val minW = columnWidth + 2 * maxL
            if (width < minW) width = ceil(minW / 50.0) * 50.0
        }
        constraints.maxRight?.let { maxR ->
            val minW = columnWidth + 2 * maxR
            if (width < minW) width = ceil(minW / 50.0) * 50.0
        }
        constraints.maxTop?.let { maxT ->
            val minL = columnDepth + 2 * maxT
            if (length < minL) length = ceil(minL / 50.0) * 50.0
        }

        // 4. ضغط التربة التصميمي (Ultimate) مع العزم
        val A = width * length / 1e6
        val Ix = width * length.pow(3) / (12e12)
        val Iy = length * width.pow(3) / (12e12)
        val Pu = axialLoad
        val Mxu = momentX
        val Myu = momentY

        // وزن القاعدة المحسن
        val W_footing = GAMMA_CONCRETE * A * (footingDepth / 1000.0)
        val Pu_net = Pu + W_footing * loadCombination.factor * 0.4

        val basePressure = Pu_net / A
        val qMaxX = abs(Mxu) * (length / 2000.0) / Ix
        val qMaxY = abs(Myu) * (width / 2000.0) / Iy
        val maxPressure = basePressure + qMaxX + qMaxY
        val minPressure = basePressure - qMaxX - qMaxY

        if (minPressure < 0) {
            warnings.add("تحذير: ضغط التربة سالب (انفصال القاعدة) - يجب زيادة الأبعاد")
        }

        // 5. السمك الفعال
        val d = footingDepth - getMinCover() - 10.0

        // 6. فحص قص الاختراق ECP 203 البند 4-3-2
        val punchingResult = checkPunchingShear(fcu, columnWidth, columnDepth, d, Pu, loadCombination)
        if (!punchingResult.isSafe) {
            warnings.add("قص الاختراق غير آمن! يُنصح بزيادة سمك القاعدة أو درجة الخرسانة")
        }

        // 7. فحص القص العادي ECP 203 البند 4-3-1
        val oneWayShearX = checkOneWayShear(fcu, width, length, columnWidth, d, Pu_net, FootingAxis.X)
        val oneWayShearY = checkOneWayShear(fcu, width, length, columnDepth, d, Pu_net, FootingAxis.Y)
        val criticalOneWay = if (oneWayShearX.utilizationRatio > oneWayShearY.utilizationRatio) oneWayShearX else oneWayShearY
        if (!criticalOneWay.isSafe) {
            warnings.add("القص العادي غير آمن! زِد سمك القاعدة")
        }

        // 8. حساب العزم التصميمي عند وجه العمود
        val qu = Pu_net / A
        val projectionX = (width - columnWidth) / 2.0 / 1000.0
        val projectionY = (length - columnDepth) / 2.0 / 1000.0

        // مع مراعاة الضغط غير المنتظم عند وجود عزم
        val MuX = if (qMaxX > basePressure * 0.1) {
            val qEdge = basePressure + qMaxX
            val qAvgEdge = (qu + qEdge) / 2.0
            qAvgEdge * projectionX * projectionX / 2.0
        } else {
            qu * projectionX * projectionX / 2.0
        }

        val MuY = if (qMaxY > basePressure * 0.1) {
            val qEdge = basePressure + qMaxY
            val qAvgEdge = (qu + qEdge) / 2.0
            qAvgEdge * projectionY * projectionY / 2.0
        } else {
            qu * projectionY * projectionY / 2.0
        }

        // 9. تصميم التسليح
        val reinfX = calculateFootingReinforcement(fcu, fy, width, length, d, MuX, FootingDirection.SHORT)
        val reinfY = calculateFootingReinforcement(fcu, fy, width, length, d, MuY, FootingDirection.LONG)

        // 10. التسليح التوزيعي ECP 203 البند 7-1-3
        val minDistReinf = MIN_REIN_RATIO * 1000.0 * d
        val reinfY_final = if (reinfY.astProvided < minDistReinf && length > width) {
            reinfY.copy(
                astRequired = minDistReinf,
                astProvided = max(reinfY.astProvided, minDistReinf),
                warnings = reinfY.warnings + "تم تطبيق التسليح التوزيعي"
            )
        } else {
            reinfY
        }

        codeNotes.add("ECP 203-2020: Section 7-1 (Foundations)")
        codeNotes.add("Net soil pressure (ult): %.1f kPa (max: %.1f kPa)".format(qu, maxPressure))
        codeNotes.add("Projection X=%.0fmm, Y=%.0fmm".format(projectionX * 1000, projectionY * 1000))
        codeNotes.add("Mu_X=%.1f kN.m/m, Mu_Y=%.1f kN.m/m".format(MuX, MuY))
        codeNotes.add("One-way shear U.R.=%.2f".format(criticalOneWay.utilizationRatio))

        return FootingDesignResult(
            requiredWidth = width,
            requiredLength = length,
            requiredThickness = footingDepth,
            soilPressure = qu,
            maxSoilPressure = maxPressure,
            reinforcement = reinfX,
            punchingShearCheck = punchingResult,
            isSafe = punchingResult.isSafe && criticalOneWay.isSafe && minPressure >= 0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== فحص القص العادي =====================
    /**
     * فحص القص العادي على بعد d من وجه العمود
     * ECP 203 البند 4-3-1: qcu = 0.24 * sqrt(fcu / gamma_c)
     * Vc = qcu * b * d
     */
    private fun checkOneWayShear(
        fcu: Double,
        footingWidth: Double,
        footingLength: Double,
        columnDim: Double,
        d: Double,
        Pu: Double,
        axis: FootingAxis
    ): ShearCheckResult {
        val criticalLen = (max(footingWidth, footingLength) - columnDim) / 2.0 - d
        if (criticalLen <= 0) {
            return ShearCheckResult(0.0, 999.0, true, 0.0)
        }

        val A = footingWidth * footingLength / 1e6
        val qu = Pu / A
        val shearWidth = when (axis) { FootingAxis.X -> footingWidth; FootingAxis.Y -> footingLength } / 1000.0
        val Vu = qu * shearWidth * (criticalLen / 1000.0) * 1000.0

        // ECP 203: qcu = 0.24 * sqrt(fcu / gamma_c) MPa
        val qcu = 0.24 * sqrt(fcu / GAMMA_C)
        val Vc = qcu * 1000.0 * d / 1000.0

        // أقصى إجهاد قص: q_max = 0.7 * sqrt(fcu / gamma_c)
        val qMax = 0.7 * sqrt(fcu / GAMMA_C)
        val Vmax = qMax * 1000.0 * d / 1000.0

        return ShearCheckResult(
            appliedShear = Vu,
            shearCapacity = Vc,
            isSafe = Vu <= Vc,
            utilizationRatio = Vu / Vc.coerceAtLeast(0.01),
            criticalSection = criticalLen,
            warnings = if (Vu > Vmax) listOf("إجهاد القص يتجاوز الحد الأقصى - ضروري زيادة السمك") else emptyList()
        )
    }

    // ===================== قص الاختراق =====================
    override fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double,
        loadCombination: LoadCombination
    ): ShearCheckResult {
        // المحيط الحرج على بعد d/2 من وجه العمود - ECP 203 البند 4-3-2
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
        val length_m = max(sqrt(areaRequired * 2.5), 2 * xR + 2 * minProjection)
        val length = ceil(length_m * 1000 / 50.0) * 50.0

        // عرض القاعدة
        val width_m = areaRequired / (length / 1000.0)
        val width = ceil(width_m * 1000 / 50.0) * 50.0

        // 4. النواتئ
        val L_actual = length / 1000.0
        val e1 = (L_actual - xR)
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

        // 8. عزم سالب بين العمودين (تسليح علوي)
        // تقريبي: عند العمود 1
        val negMomentAtCol1 = qu_u * (width / 1000.0) * proj1 * proj1 / 2.0

        // 9. تصميم التسليح السفلي
        val maxPosMoment = max(Mu_pos1, Mu_pos2)
        val reinfBottom = calculateFootingReinforcement(
            fcu, fy, width, length, d,
            maxPosMoment / (width / 1000.0), FootingDirection.SHORT
        )

        // 10. تصميم التسليح العلوي
        val reinfTop = if (negMomentAtCol1 > 0) {
            calculateFootingReinforcement(
                fcu, fy, width, length, d,
                negMomentAtCol1 / (width / 1000.0), FootingDirection.SHORT
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
        codeNotes.add("Bottom steel: %s".format(reinfBottom.barString))
        codeNotes.add("Top steel: %s".format(reinfTop.barString))

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
            warnings.add("انفصال اللبشة عن التربة - يُنصح بزيادة السمك أو تثبيت")
        }

        // 2. السمك الفعال
        val d = raftThickness - getMinCover() - 10.0

        // 3. تحليل الشريط - معاملات البلاطة المستمرة
        val q_ult = totalLoads / totalArea * 1.5
        val L_strip = B * 1000.0

        // عزم موجب (وسط البحر): M = qL2/10
        val Mu_pos = q_ult * (L_strip / 1000.0).pow(2) / 10.0
        // عزم سالب (عند الدعامات): M = qL2/12
        val Mu_neg = q_ult * (L_strip / 1000.0).pow(2) / 12.0

        // 4. تصميم التسليح السفلي
        val reinfBotX = calculateFootingReinforcement(fcu, fy, 1000.0, L_strip, d, Mu_pos, FootingDirection.SHORT)
        val reinfBotY = calculateFootingReinforcement(fcu, fy, L_strip, 1000.0, d, Mu_pos, FootingDirection.LONG)

        // 5. تصميم التسليح العلوي
        val reinfTopX = calculateFootingReinforcement(fcu, fy, 1000.0, L_strip, d, Mu_neg, FootingDirection.SHORT)
        val reinfTopY = calculateFootingReinforcement(fcu, fy, L_strip, 1000.0, d, Mu_neg, FootingDirection.LONG)

        // 6. فحص القص
        val punchingCheck = checkPunchingShear(fcu, 400.0, 400.0, d, totalLoads * 0.2, LoadCombination.DEAD_LIVE)
        val oneWayShear = checkOneWayShear(fcu, L_strip, L_strip, 400.0, d, totalLoads * 1.5, FootingAxis.X)

        codeNotes.add("ECP 203: Raft Foundation (Rigid Method)")
        codeNotes.add("Average soil pressure: %.1f kPa".format(q_avg))
        codeNotes.add("Bottom X: %s, Y: %s".format(reinfBotX.barString, reinfBotY.barString))
        codeNotes.add("Top X: %s, Y: %s".format(reinfTopX.barString, reinfTopY.barString))
        codeNotes.add("Positive M = qL2/10 = %.1f kN.m/m".format(Mu_pos))
        codeNotes.add("Negative M = qL2/12 = %.1f kN.m/m".format(Mu_neg))

        val reinf = if (reinfBotX.astRequired >= reinfBotY.astRequired) reinfBotX else reinfBotY

        return FootingDesignResult(
            requiredWidth = B * 1000.0,
            requiredLength = B * 1000.0,
            requiredThickness = raftThickness,
            soilPressure = q_avg,
            maxSoilPressure = q_max,
            reinforcement = reinf,
            punchingShearCheck = punchingCheck,
            isSafe = q_max <= soilBearingCapacity && q_min >= 0 && punchingCheck.isSafe && oneWayShear.isSafe,
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

        val (capW, capL) = when (pileLayout) {
            PileLayout.ONE -> Pair(pileDiameter + 2 * edgeDist, pileDiameter + 2 * edgeDist)
            PileLayout.TWO -> Pair(spacing + 2 * edgeDist, pileDiameter + 2 * edgeDist)
            PileLayout.FOUR -> Pair(spacing + 2 * edgeDist, spacing + 2 * edgeDist)
            PileLayout.SIX -> Pair(2 * spacing + 2 * edgeDist, spacing + 2 * edgeDist)
            PileLayout.NINE -> Pair(2 * spacing + 2 * edgeDist, 2 * spacing + 2 * edgeDist)
        }.let { (w, l) ->
            Pair(ceil(w / 50.0) * 50.0, ceil(l / 50.0) * 50.0)
        }

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

        if (pileShearStr