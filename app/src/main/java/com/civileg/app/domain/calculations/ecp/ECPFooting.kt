package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
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
 * - ECP 203-2020 البند 4-2-1-2 (الحد الأدنى للتسليح)
 */
class ECPFooting : FootingDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val GAMMA_CONCRETE = 25.0 // kN/m³
        // معاملات التحميل حسب ECP 203 البند 2-3-1
        private const val GAMMA_G = 1.4   // الحمل الميت
        private const val GAMMA_Q = 1.6   // الحمل الحي
        // حد ادنى نسبة التسليح في القواعد ECP 203 جدول 4-8
        private const val MIN_REIN_RATIO = 0.0015 // 0.15% للقواعد
        // أقل سماكة للقواعد ECP 203
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

        // 1. فحص السمك الأدنى
        if (footingDepth < MIN_THICKNESS) {
            warnings.add("أقل سماكة للقاعدة 300 مم حسب ECP 203 البند 7-1")
        }

        // 2. تحويل الأحمال للخدمة (لحساب أبعاد القاعدة)
        val factor = loadCombination.getFactorForCode(DesignCode.ECP)
        val P_service = axialLoad / factor
        val Mx_service = momentX / factor
        val My_service = momentY / factor

        // 3. حساب أبعاد القاعدة المنفصلة
        // A_req = P / (SBC - gamma_c * t)
        val t_m = footingDepth / 1000.0
        val netSBC = soilBearingCapacity - GAMMA_CONCRETE * t_m
        val A_req = if (netSBC > 0) P_service / netSBC else P_service / soilBearingCapacity

        // 4. نسبة العرض للطول
        val L_ratio = sqrt(columnDepth / columnWidth * 1.2)
        var B_m = A_req / L_ratio
        var L_m = L_ratio * B_m

        // 5. تطبيق الحدود (حدود الجار)
        if (constraints.isCornerColumn) {
            val maxDim = min(
                constraints.maxLeft ?: (columnWidth * 3),
                constraints.maxTop ?: (columnDepth * 3)
            )
            B_m = min(B_m, maxDim / 1000.0)
            L_m = min(L_m, maxDim / 1000.0)
        } else if (constraints.isEdgeColumn) {
            val maxProj = constraints.maxRight ?: (columnWidth * 2)
            L_m = min(L_m, (columnWidth + 2 * maxProj) / 1000.0)
        }

        // 6. تقريب الأبعاد لأقرب 50 مم
        val B = ceil(B_m * 1000 / 50.0) * 50.0
        val L = ceil(L_m * 1000 / 50.0) * 50.0
        val A_actual = B * L / 1e6

        // 7. ضغط التربة مع الانحراف
        val q_avg = P_service / A_actual
        val ex = Mx_service / P_service
        val ey = My_service / P_service
        val q_max_x = q_avg * (1 + 6 * ex / (B / 1000.0))
        val q_max_y = q_avg * (1 + 6 * ey / (L / 1000.0))
        val q_max = max(q_max_x, q_max_y)
        val q_min = min(
            q_avg * (1 - 6 * ex / (B / 1000.0)),
            q_avg * (1 - 6 * ey / (L / 1000.0))
        )

        if (q_max > soilBearingCapacity) {
            warnings.add("ضغط التربة الأقصى %.1f kPa يتجاوز قدرة التربة %.1f kPa".format(q_max, soilBearingCapacity))
        }
        if (q_min < 0) {
            warnings.add("انفصال القاعدة عن التربة - زِد الأبعاد")
        }

        // 8. السمك الفعال
        val d = footingDepth - getMinCover() - 10.0

        // 9. عزم الانحناء في الاتجاه القصير والطويل
        val cantX = (B - columnWidth) / 2.0 / 1000.0  // م
        val cantY = (L - columnDepth) / 2.0 / 1000.0  // م
        val Mu_x = q_avg * (L / 1000.0) * cantX * cantX / 2.0  // kN.m
        val Mu_y = q_avg * (B / 1000.0) * cantY * cantY / 2.0  // kN.m

        // 10. فحص القص الأحادي عند بعد d/2 من وجه العمود (ECP 203 البند 4-3-1-2)
        val Vu_x = q_avg * (L / 1000.0) * max(cantX - d / 2000.0, 0.0)  // kN
        val Vu_y = q_avg * (B / 1000.0) * max(cantY - d / 2000.0, 0.0)  // kN

        // One-way shear capacity ECP 203: qcu = 0.24 * sqrt(fcu/gamma_c)
        val qcu = 0.24 * sqrt(fcu / GAMMA_C)  // MPa
        val Vc_x = qcu * (L / 1000.0) * d / 1000.0 * 1000.0  // kN
        val Vc_y = qcu * (B / 1000.0) * d / 1000.0 * 1000.0  // kN

        if (Vu_x > Vc_x) {
            warnings.add("قص أحادي X يتجاوز القدرة - زِد السمك")
        }
        if (Vu_y > Vc_y) {
            warnings.add("قص أحادي Y يتجاوز القدرة - زِد السمك")
        }

        // 11. فحص قص الاختراق
        val punchingCheck = checkPunchingShear(fcu, columnWidth, columnDepth, d, axialLoad, loadCombination)

        // 12. تصميم التسليح
        val Mu_x_per_m = Mu_x / (L / 1000.0)
        val Mu_y_per_m = Mu_y / (B / 1000.0)

        val reinfX = calculateFootingReinforcement(fcu, fy, B, L, d, Mu_x_per_m, FootingDirection.SHORT)
        val reinfY = calculateFootingReinforcement(fcu, fy, B, L, d, Mu_y_per_m, FootingDirection.LONG)

        // 13. التسليح التوزيعي (distribution steel) - 20% من التسليح الرئيسي
        val mainAs = max(reinfX.astRequired, reinfY.astRequired)
        val distAs = 0.20 * mainAs
        val distBar = selectBarDiameter(distAs.coerceAtLeast(0.0))
        val distBarArea = PI * distBar * distBar / 4.0
        val distBarsPerMeter = if (distAs > 0) ceil(distAs / distBarArea).toInt() else 0
        val distSpacing = if (distBarsPerMeter > 0) {
            floor(1000.0 / distBarsPerMeter).coerceIn(100.0, 250.0)
        } else {
            200.0
        }

        // 14. التسليح الرئيسي = الأكبر
        val mainReinf = if (reinfX.astRequired >= reinfY.astRequired) reinfX else reinfY

        codeNotes.add("ECP 203-2020: Isolated Footing Design")
        codeNotes.add("B=%.0fxL=%.0f mm, d=%.0f mm".format(B, L, d))
        codeNotes.add("q_avg=%.1f, q_max=%.1f, q_min=%.1f kPa".format(q_avg, q_max, q_min))
        codeNotes.add("Mu_x=%.1f, Mu_y=%.1f kN.m".format(Mu_x, Mu_y))
        codeNotes.add("Main: %s".format(mainReinf.barString))
        if (distBarsPerMeter > 0) {
            codeNotes.add("Distribution: %d dia %d @ %dmm".format(
                distBarsPerMeter, distBar.toInt(), distSpacing.toInt()
            ))
        }
        codeNotes.add("One-way shear capacity: %.2f MPa".format(qcu))

        return FootingDesignResult(
            requiredWidth = B,
            requiredLength = L,
            requiredThickness = footingDepth,
            soilPressure = q_avg,
            maxSoilPressure = q_max,
            reinforcement = mainReinf,
            punchingShearCheck = punchingCheck,
            isSafe = q_max <= soilBearingCapacity
                && q_min >= 0
                && Vu_x <= Vc_x
                && Vu_y <= Vc_y
                && punchingCheck.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== التحقق من قص الاختراق =====================
    override fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double,
        loadCombination: LoadCombination
    ): ShearCheckResult {
        // محيط الاختراق عند بعد d/2 من وجه العمود (ECP 203 البند 4-3-2)
        val bo = 2.0 * (columnWidth + columnDepth) + 4.0 * effectiveDepth
        // القوة القاطعة الفعالة (بعد خصم رد فعل التربة داخل المحيط)
        val punchArea = (columnWidth + 2.0 * effectiveDepth) * (columnDepth + 2.0 * effectiveDepth)
        val V_punch = punchingShearForce * 0.90

        // ضغط القص المُطبَّق
        val qp_applied = (V_punch * 1000.0) / (bo * effectiveDepth)
        // القدرة القصية لقص الاختراق ECP 203: qp = 0.316 * sqrt(fcu/gamma_c)
        val qp_capacity = 0.316 * sqrt(fcu / GAMMA_C)  // MPa

        val isSafe = qp_applied <= qp_capacity

        val warnings = mutableListOf<String>()
        if (!isSafe) {
            warnings.add("قص الاختراق %.2f MPa > %.2f MPa - زِد سمك القاعدة".format(qp_applied, qp_capacity))
        }

        return ShearCheckResult(
            appliedShear = qp_applied,
            shearCapacity = qp_capacity,
            isSafe = isSafe,
            utilizationRatio = qp_applied / qp_capacity,
            criticalSection = effectiveDepth / 2.0,  // المسافة من وجه العمود (d/2)
            criticalPerimeter = bo,
            warnings = warnings
        )
    }

    // ===================== حساب التسليح =====================
    override fun calculateFootingReinforcement(
        fcu: Double,
        fy: Double,
        footingWidth: Double,
        footingLength: Double,
        effectiveDepth: Double,
        designMoment: Double,  // kN.m/m
        direction: FootingDirection
    ): ReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. K-method (ECP 203 البند 4-2-2-1)
        val Mu = designMoment * 1e6  // N.mm/m
        val b = 1000.0  // mm per meter width
        val d = effectiveDepth

        // K = Mu / (fcu * b * d^2)
        val K = Mu / (fcu * b * d * d)

        // K_bal for tension-controlled (fcu=25, fy=360): 0.186
        val K_bal = 0.186

        if (K > K_bal) {
            warnings.add("K=%.3f > K_bal=%.3f - زِد العمق الفعال".format(K, K_bal))
        }

        // 2. حساب z (ذراع القوة)
        // z = d * (0.5 + sqrt(0.25 - K / 1.25))
        val z = d * (0.5 + sqrt(max(0.0, 0.25 - K / 1.25)))

        // 3. As = Mu / (fy/gamma_s * z)
        val asRequired = Mu / (fy / GAMMA_S * z)

        // 4. الحد الأدنى للتسليح (ECP 203 جدول 4-8)
        val asMin = MIN_REIN_RATIO * b * d

        val asFinal = max(asRequired, asMin)

        // 5. اختيار القضبان
        val barDiameter = selectBarDiameter(asFinal)
        val barArea = PI * barDiameter * barDiameter / 4.0
        val nominalBars = ceil(asFinal / barArea).toInt()
        val nominalSpacing = floor(1000.0 / nominalBars)

        // 6. المسافة بين القضبان (ECP 203 البند 7-1-3-3)
        val maxSpacing = min(250.0, min(3.0 * d, 750.0))
        val finalSpacing = max(nominalSpacing, 100.0).coerceAtMost(maxSpacing)
        val actualBars = ceil(1000.0 / finalSpacing).toInt()
        val asProvided = actualBars * barArea

        // 7. نسبة التسليح
        val rho = asProvided / (b * d)
        if (rho > 0.04) {
            warnings.add("نسبة التسليح %.1f%% تتجاوز الحد الأقصى".format(rho * 100))
        }

        val utilization = asRequired / asProvided
        codeNotes.add("%s: %d dia %d @ %dmm".format(
            direction.name, actualBars, barDiameter.toInt(), finalSpacing.toInt()
        ))

        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = actualBars,
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
        val factor = loadCombination.getFactorForCode(DesignCode.ECP)
        val P1_s = axialLoad1 / factor
        val P2_s = axialLoad2 / factor
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

        // 8. عزم سالب بين العمودين (تسليح علوي)
        // تقريبي: M_neg = q * e1^2 / 2 (ناتئ الجانب الأطول)
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

        // 11. فحص قص الاختراق عند كل عمود
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
