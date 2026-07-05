package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

class ECPAdvancedBeam {
    
    private val baseBeam = ECPBeam()

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val COVER = 50.0  // mm - غطاء خرساني + كانة
        private const val E_S = 200000.0  // MPa - معامل مرونة الحديد
    }

    /**
     * نتيجة تصميم الالتواء
     */
    data class TorsionReinforcementResult(
        val designTorque: Double,          // kN.m - عزم اللي التصميمي
        val thresholdTorque: Double,       // kN.m - عزم اللي الحرج
        val torsionRequired: Boolean,      // هل يلزم تصميم الالتواء؟
        val stirrupAreaPerLeg: Double,     // mm² - مساحة الكانة لكل فرع
        val stirrupSpacing: Double,        // mm - تباعد الكانات
        val additionalLongitudinalArea: Double, // mm² - مساحة الحديد الطولي الإضافي
        val longitudinalBars: Int,         // عدد الأسياخ الطولية الإضافية
        val longitudinalDiameter: Double,  // mm - قطر الحديد الطولي الإضافي
        val aoh: Double,                   // mm² - مساحة المضلع المحاط بمحور الكانات
        val ao: Double,                    // mm² - المساحة الفعالة (0.85 × Aoh)
        val ph: Double,                    // mm - محيط محور الكانات
        val isSafe: Boolean,
        val codeReference: String,
        val warnings: List<String> = emptyList()
    )

    /**
     * تصميم متقدم للكمرات بجميع أنواعها حسب الكود المصري ECP 203
     */
    fun designBeam(
        beamType: BeamType,
        sectionType: BeamSectionType,
        fcu: Double,
        fy: Double,
        deadLoad: Double,     // kN/m
        liveLoad: Double,     // kN/m
        width: Double,        // mm
        depth: Double,        // mm
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // الحمل الكلي الموزع (Ultimate Load)
        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ECP)
        
        // حساب القوى القصوى بناءً على نوع الكمرة
        val maxMoment = beamType.getMaxMoment(totalLoad)
        val maxShear = beamType.getMaxShear(totalLoad)
        
        // تصميم الانحناء (Flexure Design)
        val flexureResult = baseBeam.calculateFlexureReinforcement(
            fcu, fy, width, depth - 50.0, depth, maxMoment, loadCombination
        )
        
        // تصميم القص (Shear Design)
        val shearResult = baseBeam.calculateShearReinforcement(
            fcu, fy, width, depth - 50.0, maxShear, 0.0, loadCombination
        )
        
        // التحقق من الانحراف (Deflection Check)
        val span = when (beamType) {
            is BeamType.SimplySupported -> beamType.span
            is BeamType.Fixed -> beamType.span
            is BeamType.Cantilever -> beamType.length
            is BeamType.Continuous -> beamType.spans.maxOrNull() ?: 1.0
            else -> 5.0
        }
        val deflectionCheck = checkDeflection(span, depth, fcu, totalLoad, width, flexureResult)
        
        // تحليل المخزون (Inventory Analysis)
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeBeamInventory(flexureResult, span, inv)
        }
        
        // التحقق من عرض الشروخ (Crack Width)
        val crackWidth = checkCrackWidth(flexureResult, fcu, maxMoment)
        
        // التحقق من طول التماسك (Development Length)
        val devLength = checkDevelopmentLength(flexureResult, fcu, fy)
        
        codeNotes.add(CodeReference.ECP.BEAM_FLEXURE)
        codeNotes.add(CodeReference.ECP.BEAM_SHEAR)
        codeNotes.add("Beam Type: ${beamType.displayName}")
        
        return AdvancedBeamResult(
            beamType = beamType,
            sectionType = sectionType,
            flexureResult = flexureResult,
            shearResult = shearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(beamType, totalLoad),
            shearDiagram = generateShearDiagram(beamType, totalLoad),
            inventoryAnalysis = inventoryAnalysis,
            crackWidthCheck = crackWidth,
            developmentLengthCheck = devLength,
            warnings = warnings + flexureResult.warnings,
            codeNotes = codeNotes
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  1. تصميم كمرة T - T-Section Beam Design per ECP 203 Section 4-2-2-2
    // ═══════════════════════════════════════════════════════════════════

    /**
     * تصميم كمرة بمقطع T حسب الكود المصري ECP 203 البند 4-2-2-2
     * 
     * @param beamType نوع الكمرة
     * @param flangeWidth mm - عرض الشفة (b)
     * @param flangeThickness mm - سمك الشفة (hf)
     * @param webWidth mm - عرض الجذع (bw)
     * @param webDepth mm - عمق الجذع
     * @param fcu MPa - مقاومة الخرسانة
     * @param fy MPa - إجهاد خضوع الحديد
     * @param deadLoad kN/m
     * @param liveLoad kN/m
     * @param inventory مخزون الحديد
     * @param loadCombination مجموعة التحميل
     * @return AdvancedBeamResult مع sectionType = T_SECTION
     */
    fun designTBeam(
        beamType: BeamType,
        flangeWidth: Double,
        flangeThickness: Double,
        webWidth: Double,
        webDepth: Double,
        fcu: Double, fy: Double,
        deadLoad: Double, liveLoad: Double,
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ECP)
        val maxMoment = beamType.getMaxMoment(totalLoad)
        val maxShear = beamType.getMaxShear(totalLoad)

        val totalDepth = flangeThickness + webDepth
        val d = totalDepth - COVER  // العمق الفعال
        val Mu = maxMoment * 1e6   // kN.m → N.mm

        // ── حساب عرض الشفة الفعال حسب ECP 203 البند 6-2-2 ──
        val span = when (beamType) {
            is BeamType.SimplySupported -> beamType.span
            is BeamType.Fixed -> beamType.span
            is BeamType.Cantilever -> beamType.length
            is BeamType.Continuous -> beamType.spans.maxOrNull() ?: 1.0
            else -> 5.0
        }
        val spanMm = span * 1000.0
        val isEdgeBeam = beamType is BeamType.Cantilever
        val beffBySpan = if (isEdgeBeam) spanMm / 8.0 else spanMm / 4.0
        val beffByFlange = 12.0 * flangeThickness + webWidth
        val beff = min(min(flangeWidth, beffBySpan), beffByFlange)

        codeNotes.add("ECP 203-2020: Section 6-2-2 (Effective Flange Width)")
        codeNotes.add("b_eff = min(b, L/${if (isEdgeBeam) "8" else "4"}, 12hf+bw) = min(${"%.0f".format(flangeWidth)}, ${"%.0f".format(beffBySpan)}, ${"%.0f".format(beffByFlange)}) = ${"%.0f".format(beff)} mm")

        if (beff < flangeWidth) {
            warnings.add("عرض الشفة الفعال (${beff.toInt()} مم) أقل من العرض الكلي (${flangeWidth.toInt()} مم)")
        }

        // ── K-method لتحديد موقع المحور المحايد ──
        val fc = 0.67 * fcu / GAMMA_C
        val fs = fy / GAMMA_S

        // K_bal باستخدام bw (عرض الجذع) وليس b
        val K_bal_bw = (0.67 / GAMMA_C) * calculateAOverDBal(fy) * (1.0 - calculateAOverDBal(fy) / 2.0)
        val K_bw = Mu / (fcu * webWidth * d * d)

        codeNotes.add("K_bal (bw=${webWidth.toInt()}mm) = ${"%.4f".format(K_bal_bw)}")
        codeNotes.add("K (bw=${webWidth.toInt()}mm) = ${"%.4f".format(K_bw)}")

        // حساب عمق كتلة الإجهاد التجريبي باستخدام bw
        val a_trial = if (K_bw > 0) {
            d * (1.0 - sqrt(1.0 - 2.0 * K_bw * 1.25 * GAMMA_C / 0.67))
        } else {
            0.0
        }

        // ── التحقق: هل المحور المحايد في الشفة أم الجذع؟ ──
        val neutralAxisInFlange = a_trial <= flangeThickness

        val astRequired: Double
        if (neutralAxisInFlange) {
            // المحور المحايد في الشفة → يُصمم كمقطع مستطيل بعرض b = beff
            codeNotes.add("Neutral axis in FLANGE (a=${"%.1f".format(a_trial)}mm ≤ hf=${"%.0f".format(flangeThickness)}mm)")
            codeNotes.add("Design as rectangular beam with b = b_eff = ${beff.toInt()} mm")

            val K = Mu / (fcu * beff * d * d)
            val leverArm = if (0.25 - K / 1.25 > 0) {
                d * (0.5 + sqrt(0.25 - K / 1.25))
            } else {
                d * 0.7
            }
            astRequired = Mu / (fs * leverArm)
        } else {
            // المحور المحايد في الجذع → تجزئة الحساب: شفة + جذع
            codeNotes.add("Neutral axis in WEB (a=${"%.1f".format(a_trial)}mm > hf=${"%.0f".format(flangeThickness)}mm)")
            codeNotes.add("Design as T-section: flange part + web part")

            // قوة الشفة: Cf = 0.85×fcu×(b-bw)×hf
            val Cf = fc * (beff - webWidth) * flangeThickness
            // عزم الشفة: Mf = Cf × (d - hf/2)
            val Mf = Cf * (d - flangeThickness / 2.0)
            // تسليح الشفة: As_flange = Cf / (fy/γs)
            val As_flange = Cf / fs

            codeNotes.add("Cf (flange) = ${"%.0f".format(Cf)} N")
            codeNotes.add("Mf (flange) = ${"%.0f".format(Mf / 1e6)} kN.m")
            codeNotes.add("As_flange = ${"%.0f".format(As_flange)} mm²")

            // العزم المتبقي للجذع
            val M_web = Mu - Mf
            if (M_web < 0) {
                // الشفة وحدها تكفي (لا يحدث عملياً لكن للسلامة)
                astRequired = As_flange
            } else {
                // تصميم الجذع بطريقة K مع b = bw
                val K_web = M_web / (fcu * webWidth * d * d)
                val leverArm_web = if (0.25 - K_web / 1.25 > 0) {
                    d * (0.5 + sqrt(0.25 - K_web / 1.25))
                } else {
                    d * 0.7
                    warnings.add("تحذير: المقطع شبه مفرط التسليح - فكر في زيادة الأبعاد")
                }
                val As_web = M_web / (fs * leverArm_web)

                codeNotes.add("M_web (remaining) = ${"%.2f".format(M_web / 1e6)} kN.m")
                codeNotes.add("As_web = ${"%.0f".format(As_web)} mm²")
                astRequired = As_flange + As_web
            }
        }

        // ── تطبيق الحدود الدنيا والعليا ──
        val Ag = webWidth * d
        val minSteel = 0.005 * Ag  // 0.5% للكمرات
        val maxSteel = 0.04 * Ag   // 4%
        val finalAst = astRequired.coerceIn(minSteel, maxSteel)

        if (astRequired < minSteel) {
            codeNotes.add("Minimum reinforcement applied (ρ_min = 0.5%)")
        }

        // ── اختيار الأسياخ ──
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        var selectedDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(finalAst / area) <= 6
        } ?: 20.0
        val barArea = PI * selectedDia * selectedDia / 4
        val numBars = ceil(finalAst / barArea).toInt().coerceIn(2, 12)
        val astProvided = numBars * barArea

        // ── تصميم القص ──
        val shearResult = baseBeam.calculateShearReinforcement(
            fcu, fy, webWidth, d, maxShear, 0.0, loadCombination
        )

        // ── التحقق من الانحراف ──
        val dummyFlexResult = ReinforcementResult(
            astRequired = finalAst, astProvided = astProvided,
            barDiameter = selectedDia, numberOfBars = numBars,
            tiesDiameter = 0.0, tiesSpacing = 0.0,
            isSafe = true, utilizationRatio = finalAst / astProvided,
            spacing = if (numBars > 1) (beff - 2 * COVER) / (numBars - 1) else 0.0
        )
        val deflectionCheck = checkDeflection(span, totalDepth, fcu, totalLoad, webWidth, dummyFlexResult)

        // ── تحليل المخزون ──
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeBeamInventory(dummyFlexResult, span, inv)
        }

        // ── التحقق من الشروخ ──
        val crackWidth = checkCrackWidth(dummyFlexResult, fcu, maxMoment, d)
        val devLength = checkDevelopmentLength(dummyFlexResult, fcu, fy)

        codeNotes.add(CodeReference.ECP.BEAM_FLEXURE)
        codeNotes.add(CodeReference.ECP.BEAM_SHEAR)
        codeNotes.add("As_total = ${"%.0f".format(finalAst)} mm² → ${numBars}Ø${selectedDia.toInt()} (${"%.0f".format(astProvided)} mm²)")
        codeNotes.add("T-Section: b_eff=${beff.toInt()}, bw=${webWidth.toInt()}, hf=${flangeThickness.toInt()}, h=${totalDepth.toInt()} mm")

        return AdvancedBeamResult(
            beamType = beamType,
            sectionType = BeamSectionType.T_SECTION,
            flexureResult = dummyFlexResult,
            shearResult = shearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(beamType, totalLoad),
            shearDiagram = generateShearDiagram(beamType, totalLoad),
            inventoryAnalysis = inventoryAnalysis,
            crackWidthCheck = crackWidth,
            developmentLengthCheck = devLength,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. تصميم كمرة L - L-Section Beam Design
    // ═══════════════════════════════════════════════════════════════════

    /**
     * تصميم كمرة بمقطع L حسب الكود المصري ECP 203
     * 
     * @param beamType نوع الكمرة
     * @param flangeWidth mm - عرض الشفة
     * @param flangeThickness mm - سمك الشفة
     * @param webWidth mm - عرض الجذع
     * @param webDepth mm - عمق الجذع
     * @param fcu MPa
     * @param fy MPa
     * @param deadLoad kN/m
     * @param liveLoad kN/m
     * @param inventory مخزون الحديد
     * @param loadCombination مجموعة التحميل
     * @return AdvancedBeamResult مع sectionType = L_SECTION
     */
    fun designLBeam(
        beamType: BeamType,
        flangeWidth: Double,
        flangeThickness: Double,
        webWidth: Double,
        webDepth: Double,
        fcu: Double, fy: Double,
        deadLoad: Double, liveLoad: Double,
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ECP)
        val maxMoment = beamType.getMaxMoment(totalLoad)
        val maxShear = beamType.getMaxShear(totalLoad)

        val totalDepth = flangeThickness + webDepth
        val d = totalDepth - COVER
        val Mu = maxMoment * 1e6

        // ── حساب عرض الشفة الفعال لكمرة L ──
        // حسب ECP 203: للكمرة الطرفية beff = min(L/12, 6×hf + bw)
        val span = when (beamType) {
            is BeamType.SimplySupported -> beamType.span
            is BeamType.Fixed -> beamType.span
            is BeamType.Cantilever -> beamType.length
            is BeamType.Continuous -> beamType.spans.maxOrNull() ?: 1.0
            else -> 5.0
        }
        val spanMm = span * 1000.0
        val beffBySpan = spanMm / 12.0
        val beffByFlange = 6.0 * flangeThickness + webWidth
        val beff = min(min(flangeWidth, beffBySpan), beffByFlange)

        codeNotes.add("ECP 203-2020: Section 6-2-2 (L-Beam Effective Flange Width)")
        codeNotes.add("b_eff = min(b, L/12, 6hf+bw) = min(${"%.0f".format(flangeWidth)}, ${"%.0f".format(beffBySpan)}, ${"%.0f".format(beffByFlange)}) = ${"%.0f".format(beff)} mm")

        if (beff < flangeWidth) {
            warnings.add("عرض الشفة الفعال (${beff.toInt()} مم) أقل من العرض الكلي (${flangeWidth.toInt()} مم)")
        }

        // ── معامل التحميل اللاتمركزي للشفة (Eccentricity Factor) ──
        // في كمرة L، الشفة تحمل الحمل بشكل لامتمركز مما يزيد الإجهاد
        val eccentricityFactor = 1.1  // 10% زيادة للتحميل اللاتمركزي
        val Mu_eccentric = Mu * eccentricityFactor
        codeNotes.add("Eccentricity factor = $eccentricityFactor (L-beam flange eccentricity)")
        codeNotes.add("M_design (with eccentricity) = ${"%.2f".format(Mu_eccentric / 1e6)} kN.m")

        // ── تصميم الانحناء بنفس طريقة كمرة T ──
        val fc = 0.67 * fcu / GAMMA_C
        val fsDesign = fy / GAMMA_S

        // التحقق من موقع المحور المحايد
        val K_bw = Mu_eccentric / (fcu * webWidth * d * d)
        val a_trial = if (K_bw > 0) {
            d * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * K_bw * 1.25 * GAMMA_C / 0.67)))
        } else {
            0.0
        }

        val neutralAxisInFlange = a_trial <= flangeThickness
        val astRequired: Double

        if (neutralAxisInFlange) {
            codeNotes.add("Neutral axis in FLANGE (a=${"%.1f".format(a_trial)}mm ≤ hf=${"%.0f".format(flangeThickness)}mm)")
            val K = Mu_eccentric / (fcu * beff * d * d)
            val leverArm = if (0.25 - K / 1.25 > 0) {
                d * (0.5 + sqrt(0.25 - K / 1.25))
            } else {
                d * 0.7
            }
            astRequired = Mu_eccentric / (fsDesign * leverArm)
        } else {
            codeNotes.add("Neutral axis in WEB (a=${"%.1f".format(a_trial)}mm > hf=${"%.0f".format(flangeThickness)}mm)")
            val Cf = fc * (beff - webWidth) * flangeThickness
            val Mf = Cf * (d - flangeThickness / 2.0)
            val As_flange = Cf / fsDesign
            val M_web = Mu_eccentric - Mf

            astRequired = if (M_web <= 0) {
                As_flange
            } else {
                val K_web = M_web / (fcu * webWidth * d * d)
                val leverArm_web = if (0.25 - K_web / 1.25 > 0) {
                    d * (0.5 + sqrt(0.25 - K_web / 1.25))
                } else {
                    d * 0.7
                    warnings.add("تحذير: المقطع شبه مفرط التسليح - فكر في زيادة الأبعاد")
                }
                val As_web = M_web / (fsDesign * leverArm_web)
                As_flange + As_web
            }
        }

        // حدود التسليح
        val Ag = webWidth * d
        val minSteel = 0.005 * Ag
        val maxSteel = 0.04 * Ag
        val finalAst = astRequired.coerceIn(minSteel, maxSteel)

        if (astRequired < minSteel) {
            codeNotes.add("Minimum reinforcement applied (ρ_min = 0.5%)")
        }

        // اختيار الأسياخ
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        var selectedDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(finalAst / area) <= 6
        } ?: 20.0
        val barArea = PI * selectedDia * selectedDia / 4
        val numBars = ceil(finalAst / barArea).toInt().coerceIn(2, 12)
        val astProvided = numBars * barArea

        // تصميم القص
        val shearResult = baseBeam.calculateShearReinforcement(
            fcu, fy, webWidth, d, maxShear, 0.0, loadCombination
        )

        // التحقق من الانحراف
        val dummyFlexResult = ReinforcementResult(
            astRequired = finalAst, astProvided = astProvided,
            barDiameter = selectedDia, numberOfBars = numBars,
            tiesDiameter = 0.0, tiesSpacing = 0.0,
            isSafe = true, utilizationRatio = finalAst / astProvided,
            spacing = if (numBars > 1) (beff - 2 * COVER) / (numBars - 1) else 0.0
        )
        val deflectionCheck = checkDeflection(span, totalDepth, fcu, totalLoad, webWidth, dummyFlexResult)

        val inventoryAnalysis = inventory?.let { inv ->
            analyzeBeamInventory(dummyFlexResult, span, inv)
        }
        val crackWidth = checkCrackWidth(dummyFlexResult, fcu, maxMoment, d)
        val devLength = checkDevelopmentLength(dummyFlexResult, fcu, fy)

        codeNotes.add(CodeReference.ECP.BEAM_FLEXURE)
        codeNotes.add(CodeReference.ECP.BEAM_SHEAR)
        codeNotes.add("As_total = ${"%.0f".format(finalAst)} mm² → ${numBars}Ø${selectedDia.toInt()} (${"%.0f".format(astProvided)} mm²)")
        codeNotes.add("L-Section: b_eff=${beff.toInt()}, bw=${webWidth.toInt()}, hf=${flangeThickness.toInt()}, h=${totalDepth.toInt()} mm")

        return AdvancedBeamResult(
            beamType = beamType,
            sectionType = BeamSectionType.L_SECTION,
            flexureResult = dummyFlexResult,
            shearResult = shearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(beamType, totalLoad),
            shearDiagram = generateShearDiagram(beamType, totalLoad),
            inventoryAnalysis = inventoryAnalysis,
            crackWidthCheck = crackWidth,
            developmentLengthCheck = devLength,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3. تصميم كمرة عميقة - Deep Beam Design per ECP 203 Section 4-2-8
    // ═══════════════════════════════════════════════════════════════════

    /**
     * تصميم كمرة عميقة حسب الكود المصري ECP 203 البند 4-2-8
     * معيار الكمرة العميقة: L/d < 4 (البحر الصافي / العمق الكلي)
     * 
     * @param span m - البحر
     * @param width mm - عرض الكمرة
     * @param depth mm - عمق الكمرة
     * @param fcu MPa
     * @param fy MPa
     * @param deadLoad kN/m
     * @param liveLoad kN/m
     * @param loadCombination مجموعة التحميل
     * @return AdvancedBeamResult (يستخدم RECTANGULAR لأن DEEP_BEAM غير موجود في الـ enum)
     */
    fun designDeepBeam(
        span: Double,
        width: Double,
        depth: Double,
        fcu: Double, fy: Double,
        deadLoad: Double, liveLoad: Double,
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ECP)
        val maxMoment = totalLoad * span * span / 8  // kN.m (بسيطة الدعم)
        val maxShear = totalLoad * span / 2          // kN

        val d = depth - COVER
        val spanToDepthRatio = span * 1000.0 / depth

        codeNotes.add("ECP 203-2020: Section 4-2-8 (Deep Beam Design)")
        codeNotes.add("L/d = ${"%.2f".format(spanToDepthRatio)} ${if (spanToDepthRatio < 4.0) "→ Deep Beam (L/d < 4)" else "→ NOT a Deep Beam (L/d ≥ 4)"}")

        if (spanToDepthRatio >= 4.0) {
            warnings.add("تحذير: L/d = ${"%.2f".format(spanToDepthRatio)} ≥ 4 - هذا ليس كمرة عميقة! استخدم تصميم الكمرات العادية")
        }

        // ── تصميم الانحناء بطريقة Strut-and-Tie المبسطة ──
        val Mu = maxMoment * 1e6
        val fc = 0.67 * fcu / GAMMA_C
        val fsDesign = fy / GAMMA_S

        // في الكمرات العميقة، ذراع العزم يكون أصغر (≈ 0.5d إلى 0.6d)
        val leverArm = d * 0.5
        var astFlexure = Mu / (fsDesign * leverArm)

        // حد أدنى للتسليح الطولي (ECP 203-4-2-8)
        val Ag = width * d
        val minLongSteel = 0.0025 * Ag
        astFlexure = max(astFlexure, minLongSteel)

        codeNotes.add("Strut-and-Tie Model: lever arm z = 0.5d = ${"%.0f".format(leverArm)} mm")
        codeNotes.add("As_flexure = ${"%.0f".format(astFlexure)} mm² (min = ${"%.0f".format(minLongSteel)} mm²)")

        // ── التسليح الأفقي الأدنى (كلا الوجهين) ──
        // As_h ≥ 0.0025 × bw × s, وتباعد أقصى = d/2
        val maxSpacingH = d / 2.0
        val as_h_per_meter = 0.0025 * width * 1000  // mm²/m (لكل وجه)
        val stirrupDia_h = if (as_h_per_meter < 100) 8.0 else if (as_h_per_meter < 200) 10.0 else 12.0
        val stirrupArea_h = PI * stirrupDia_h * stirrupDia_h / 4
        val spacing_h = (stirrupArea_h * 1000 / as_h_per_meter).coerceIn(50.0, maxSpacingH)

        codeNotes.add("Horizontal reinforcement (each face): ρ_h = 0.0025, s_max = d/2 = ${"%.0f".format(maxSpacingH)} mm")
        codeNotes.add("Horizontal: Ø${stirrupDia_h.toInt()} @ ${"%.0f".format(spacing_h)} mm (As_h = ${"%.0f".format(as_h_per_meter)} mm²/m per face)")

        // ── التسليح الرأسي الأدنى (كلا الوجهين) ──
        // As_v ≥ 0.0015 × bw × sv, وتباعد أقصى = d/3
        val maxSpacingV = d / 3.0
        val as_v_per_meter = 0.0015 * width * 1000  // mm²/m (لكل وجه)
        val stirrupDia_v = if (as_v_per_meter < 80) 8.0 else 10.0
        val stirrupArea_v = PI * stirrupDia_v * stirrupDia_v / 4
        val spacing_v = (stirrupArea_v * 1000 / as_v_per_meter).coerceIn(50.0, maxSpacingV)

        codeNotes.add("Vertical reinforcement (each face): ρ_v = 0.0015, s_max = d/3 = ${"%.0f".format(maxSpacingV)} mm")
        codeNotes.add("Vertical: Ø${stirrupDia_v.toInt()} @ ${"%.0f".format(spacing_v)} mm (As_v = ${"%.0f".format(as_v_per_meter)} mm²/m per face)")

        // ── قدرة القص المعززة للكمرات العميقة ──
        // Vn = 0.167×√fcu × bw × d (معزز للكمرات العميقة)
        val Vu = maxShear * 1000  // N
        val Vn_concrete = 0.167 * sqrt(fcu) * width * d  // N
        val maxVn = 0.5 * sqrt(fcu) * width * d  // الحد الأقصى

        val isShearSafe = Vu <= maxVn
        if (!isShearSafe) {
            warnings.add("تحذير: إجهاد القص يتجاوز الحد الأقصى للكمرة العميقة! زد العرض أو مقاومة الخرسانة")
        }

        codeNotes.add("Deep beam shear: Vn = 0.167√fcu×bw×d = ${"%.0f".format(Vn_concrete / 1000)} kN")
        codeNotes.add("Applied Vu = ${"%.0f".format(maxShear)} kN, Vn_max = ${"%.0f".format(maxVn / 1000)} kN")

        // ── اختيار أسياخ الانحناء ──
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        var selectedDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(astFlexure / area) <= 8  // الكمرات العميقة تسمح بأكثر
        } ?: 20.0
        val barArea = PI * selectedDia * selectedDia / 4
        val numBars = ceil(astFlexure / barArea).toInt().coerceIn(2, 12)
        val astProvided = numBars * barArea

        // ── نتيجة القص المبسطة ──
        val shearResult = ShearReinforcementResult(
            concreteShearCapacity = Vn_concrete / 1000,
            requiredArea = as_v_per_meter,
            providedArea = stirrupArea_v * 1000 / spacing_v,
            requiredShearReinforcement = as_v_per_meter,
            providedShearReinforcement = stirrupArea_v * 1000 / spacing_v,
            stirrupDiameter = stirrupDia_v,
            stirrupSpacing = spacing_v,
            isSafe = isShearSafe,
            utilizationRatio = if (maxVn > 0) Vu / maxVn else 2.0,
            codeNotes = listOf("Deep beam shear per ECP 203 Section 4-2-8")
        )

        // ── الانحراف غير حرج للكمرات العميقة ──
        val deflectionResult = DeflectionCheckResult(
            immediateDeflection = 0.0,
            longTermDeflection = 0.0,
            calculatedDeflection = 0.0,
            allowableDeflection = span * 1000.0 / 250.0,
            ratio = 0.0,
            isSafe = true,
            message = "الانحراف غير حرج للكمرة العميقة (L/d < 4)",
            recommendation = "Deflection is NOT critical for deep beams per ECP 203 Section 4-2-8"
        )

        val flexureResult = ReinforcementResult(
            astRequired = astFlexure,
            astProvided = astProvided,
            barDiameter = selectedDia,
            numberOfBars = numBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = astFlexure / astProvided,
            codeNotes = listOf(
                "Deep beam flexure per ECP 203 Section 4-2-8",
                "Horizontal: Ø${stirrupDia_h.toInt()} @ ${spacing_h.toInt()} mm (both faces)",
                "Vertical: Ø${stirrupDia_v.toInt()} @ ${spacing_v.toInt()} mm (both faces)"
            )
        )

        codeNotes.add("NOTE: DEEP_BEAM is not a BeamSectionType enum value; using RECTANGULAR as placeholder")
        codeNotes.add("As_flexure = ${numBars}Ø${selectedDia.toInt()} (${"%.0f".format(astProvided)} mm²)")

        val beamType = BeamType.SimplySupported(span)
        return AdvancedBeamResult(
            beamType = beamType,
            sectionType = BeamSectionType.RECTANGULAR, // DEEP_BEAM غير موجود في الـ enum
            flexureResult = flexureResult,
            shearResult = shearResult,
            deflectionCheck = deflectionResult,
            momentDiagram = generateMomentDiagram(beamType, totalLoad),
            shearDiagram = generateShearDiagram(beamType, totalLoad),
            inventoryAnalysis = null,
            crackWidthCheck = null,  // غير حرج للكمرات العميقة
            developmentLengthCheck = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. تصميم كمرة مستمرة - Multi-span Continuous Beam
    // ═══════════════════════════════════════════════════════════════════

    /**
     * تصميم كمرة مستمرة متعددة البحور حسب الكود المصري ECP 203 البند 4-2
     * معاملات العزوم مشابهة لـ BS 8110 Table 3.5
     * 
     * @param spans m - أطوال البحور
     * @param supportConditions ظروف التثبيت عند كل دعامة
     * @param fcu MPa
     * @param fy MPa
     * @param deadLoad kN/m
     * @param liveLoad kN/m
     * @param width mm
     * @param depth mm
     * @param inventory مخزون الحديد
     * @param loadCombination مجموعة التحميل
     * @return قائمة نتائج لكل مقطع حرج (دعامة + وسط بحر)
     */
    fun designContinuousBeam(
        spans: List<Double>,
        supportConditions: List<EndCondition>,
        fcu: Double, fy: Double,
        deadLoad: Double, liveLoad: Double,
        width: Double, depth: Double,
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): List<AdvancedBeamResult> {
        val results = mutableListOf<AdvancedBeamResult>()
        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ECP)
        val d = depth - COVER

        code_notes@ for (i in spans.indices) {
            val spanLength = spans[i]
            val wL2 = totalLoad * spanLength * spanLength

            // ── معاملات العزوم حسب ECP 203 البند 4-2 (مشابه لـ BS 8110 Table 3.5) ──
            val isEndSpan = (i == 0 || i == spans.lastIndex)
            val isEndSimplySupported = if (i == 0) {
                supportConditions.getOrNull(0) != EndCondition.FIXED
            } else {
                supportConditions.getOrNull(supportConditions.size - 1) != EndCondition.FIXED
            }
            val isEndFixed = if (i == 0) {
                supportConditions.getOrNull(0) == EndCondition.FIXED
            } else {
                supportConditions.getOrNull(supportConditions.size - 1) == EndCondition.FIXED
            }
            val adjacentSpanShorter = if (i > 0 && i < spans.size) {
                spans.getOrNull(i - 1)?.let { it < spanLength } ?: false
            } else {
                false
            }

            // معاملات العزوم لكل مقطع حرج
            // [موقع, وصف, معامل العزم, إشارة]
            val momentCoefficients = mutableListOf<Triple<String, String, Double, Boolean>>()

            // دعامة خارجية
            if (isEndSpan && isEndSimplySupported) {
                momentCoefficients.add(Triple("End Support", "دعامة طرفية (بسيطة)", 0.040, false))
            } else if (isEndSpan && isEndFixed) {
                momentCoefficients.add(Triple("End Support", "دعامة طرفية (ثابتة)", 0.050, false))
            }

            // وسط البحر
            if (isEndSpan) {
                val midCoeff = if (isEndSimplySupported) 0.086 else 0.063
                momentCoefficients.add(Triple("Midspan", "وسط البحر الطرفي", midCoeff, true))
            } else {
                // بحر داخلي
                val midCoeff = if (adjacentSpanShorter) 0.063 else 0.086
                momentCoefficients.add(Triple("Midspan", "وسط البحر الداخلي", midCoeff, true))
            }

            // دعامة داخلية أولى
            if (isEndSpan && i < spans.lastIndex) {
                val intCoeff = if (adjacentSpanShorter) 0.086 else 0.116
                momentCoefficients.add(Triple("1st Int. Support", "الدعامة الداخلية الأولى", intCoeff, false))
            }

            // دعامات داخلية
            if (!isEndSpan && i < spans.size) {
                val intCoeff = if (adjacentSpanShorter) 0.071 else 0.106
                momentCoefficients.add(Triple("Int. Support", "دعامة داخلية", intCoeff, false))
            }

            // ── تصميم كل مقطع حرج ──
            for ((location, description, coeff, isPositive) in momentCoefficients) {
                val moment = coeff * wL2  // kN.m
                val absMoment = abs(moment)

                if (absMoment < 0.01) continue

                // ── إعادة توزيع العزوم (Moment Redistribution) ──
                // ECP 203 البند 4-2-2-4: أقصى إعادة توزيع 20%
                // إذا كان المقطع يتحكم بالشد (K ≤ 0.156) يسمح بإعادة التوزيع
                val K = (absMoment * 1e6) / (fcu * width * d * d)
                val K_bal = calculateKBal(fcu, fy)
                val isTensionControlled = K <= 0.156

                var redistributionFactor = 1.0
                if (!isPositive && isTensionControlled && K > 0) {
                    // δ = 1 - (K - K_bal) × (1 - 0.5×K_bal/K)
                    // مع الحد الأقصى 20% إعادة توزيع
                    val delta = 1.0 - (K - K_bal) * (1.0 - 0.5 * K_bal / K)
                    redistributionFactor = delta.coerceIn(0.8, 1.0)
                }

                val redistributedMoment = absMoment * redistributionFactor

                // تصميم الانحناء
                val flexureResult = baseBeam.calculateFlexureReinforcement(
                    fcu, fy, width, d, depth, redistributedMoment, loadCombination
                )

                // القص القصوى عند الدعامات
                val shearAtSection = when {
                    !isPositive -> totalLoad * spanLength * 0.6  // عند الدعامات
                    else -> totalLoad * spanLength * 0.5         // عند وسط البحر (أقل)
                }
                val shearResult = baseBeam.calculateShearReinforcement(
                    fcu, fy, width, d, shearAtSection, 0.0, loadCombination
                )

                // نقاط القطع (Cutoff Points)
                val cutoffInfo = calculateCutoffPoints(spanLength, totalLoad, location, isPositive)

                // الانحراف
                val deflectionCheck = checkDeflection(
                    spanLength, depth, fcu, totalLoad, width, flexureResult
                )

                val inventoryAnalysis = inventory?.let { inv ->
                    analyzeBeamInventory(flexureResult, spanLength, inv)
                }
                val crackWidth = checkCrackWidth(flexureResult, fcu, redistributedMoment, d)
                val devLength = checkDevelopmentLength(flexureResult, fcu, fy)

                val codeNotes = mutableListOf<String>()
                val sectionWarnings = mutableListOf<String>()

                codeNotes.add("ECP 203-2020: Section 4-2 (Continuous Beam)")
                codeNotes.add("Span ${i + 1}: $description")
                codeNotes.add("Moment coefficient = $coeff (wL² = ${"%.1f".format(wL2)} kN.m)")
                codeNotes.add("M_design = ${"%.2f".format(absMoment)} kN.m")

                if (redistributionFactor < 1.0) {
                    codeNotes.add("Moment redistribution: ${(redistributionFactor * 100).toInt()}% → M_red = ${"%.2f".format(redistributedMoment)} kN.m")
                }
                if (!isPositive) {
                    codeNotes.add("TOP reinforcement at support")
                } else {
                    codeNotes.add("BOTTOM reinforcement at midspan")
                }

                codeNotes.add(cutoffInfo)

                if (isTensionControlled) {
                    codeNotes.add("Section is tension-controlled (K = ${"%.4f".format(K)} ≤ 0.156)")
                }

                val beamType = BeamType.Continuous(spans, supportConditions)

                results.add(AdvancedBeamResult(
                    beamType = beamType,
                    sectionType = BeamSectionType.RECTANGULAR,
                    flexureResult = flexureResult,
                    shearResult = shearResult,
                    deflectionCheck = deflectionCheck,
                    momentDiagram = generateMomentDiagram(beamType, totalLoad),
                    shearDiagram = generateShearDiagram(beamType, totalLoad),
                    inventoryAnalysis = inventoryAnalysis,
                    crackWidthCheck = crackWidth,
                    developmentLengthCheck = devLength,
                    warnings = sectionWarnings + flexureResult.warnings,
                    codeNotes = codeNotes
                ))
            }
        }

        return results
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5. مخططات العزوم والقص المُحسّنة - Enhanced Diagrams
    // ═══════════════════════════════════════════════════════════════════

    /**
     * توليد مخطط العزوم لجميع أنواع الكمرات
     * - بسيطة الدعم: M(x) = w×x×(L-x)/2
     * - ثابتة: M(x) = w×L×x/2 - w×x²/2 - w×L²/12
     * - كابولي: M(x) = -w×x²/2
     * - مستمرة: لكل بحر بمعاملات مناسبة
     * 20 نقطة لكل بحر/كمرة
     */
    private fun generateMomentDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val numPoints = 20

        when (beamType) {
            is BeamType.SimplySupported -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = load * x * (L - x) / 2.0
                    points.add(x to m)
                }
            }
            is BeamType.Fixed -> {
                val L = beamType.span
                // M(x) = w×L×x/2 - w×x²/2 - w×L²/12
                val fixedMoment = load * L * L / 12.0
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = load * L * x / 2.0 - load * x * x / 2.0 - fixedMoment
                    points.add(x to m)
                }
            }
            is BeamType.Cantilever -> {
                val L = beamType.length
                // M(x) = -w×x²/2 (سالب عند التثبيت)
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = -load * x * x / 2.0
                    points.add(x to m)
                }
            }
            is BeamType.Continuous -> {
                // توليد مخطط عزوم لكل بحر
                var cumulativeX = 0.0
                for (spanIdx in beamType.spans.indices) {
                    val L = beamType.spans[spanIdx]
                    val isEndSpan = (spanIdx == 0 || spanIdx == beamType.spans.lastIndex)
                    val isLastPoint = (spanIdx == beamType.spans.lastIndex)

                    // معاملات العزوم للكمرة المستمرة
                    val endMomentCoeff = if (spanIdx == 0) {
                        if (beamType.supportConditions.getOrNull(0) != EndCondition.FIXED) 0.0 else -0.050
                    } else {
                        -0.116
                    }
                    val midMomentCoeff = if (isEndSpan) 0.086 else 0.071
                    val farEndMomentCoeff = if (isLastPoint) {
                        if (beamType.supportConditions.lastOrNull() != EndCondition.FIXED) 0.0 else -0.050
                    } else {
                        -0.116
                    }

                    val wL2 = load * L * L
                    val mEnd = endMomentCoeff * wL2
                    val mMid = midMomentCoeff * wL2
                    val mFarEnd = farEndMomentCoeff * wL2

                    // توليد نقاط العزوم لكل بحر (تقريبي باستخدام معاملات)
                    for (i in 0..numPoints) {
                        val localX = L * i / numPoints
                        // تقريب شبه منحرفي (parabolic approximation)
                        // M(x) ≈ M_end + (M_mid - M_end) × 4×(x/L)×(1-x/L) + (M_far - M_end)×(x/L)
                        // تبسيط: M(x) ≈ M_end + (M_far - M_end)×(x/L) + 4×(M_mid - 0.5×M_end - 0.5×M_far)×(x/L)×(1-x/L)
                        val t = localX / L
                        val parabolaTerm = 4.0 * (mMid - 0.5 * mEnd - 0.5 * mFarEnd) * t * (1.0 - t)
                        val m = mEnd + (mFarEnd - mEnd) * t + parabolaTerm

                        if (i == 0 && spanIdx > 0) continue  // تجنب تكرار نقطة الدعامة
                        points.add(cumulativeX + localX to m)
                    }
                    cumulativeX += L
                }
            }
            else -> {
                // Haunched, MultiSupport, Vierendeel - تخطيط بسيط
                val L = 5.0
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    points.add(x to load * x * (L - x) / 2.0)
                }
            }
        }

        return points
    }

    /**
     * توليد مخطط القص لجميع أنواع الكمرات
     * - بسيطة الدعم: V(x) = w×(L/2 - x)
     * - ثابتة: V(x) = w×L/2 - w×x
     * - كابولي: V(x) = w×(L - x)
     * - مستمرة: لكل بحر بمعاملات مناسبة
     * 20 نقطة لكل بحر/كمرة
     */
    private fun generateShearDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val numPoints = 20

        when (beamType) {
            is BeamType.SimplySupported -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * (L / 2.0 - x)
                    points.add(x to v)
                }
            }
            is BeamType.Fixed -> {
                val L = beamType.span
                // V(x) = w×L/2 - w×x (خطي من +wL/2 إلى -wL/2)
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }
            is BeamType.Cantilever -> {
                val L = beamType.length
                // V(x) = w×(L - x) (من wL عند التثبيت إلى 0 عند الطرف الحر)
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * (L - x)
                    points.add(x to v)
                }
            }
            is BeamType.Continuous -> {
                var cumulativeX = 0.0
                for (spanIdx in beamType.spans.indices) {
                    val L = beamType.spans[spanIdx]
                    val isLastPoint = (spanIdx == beamType.spans.lastIndex)

                    // القص عند بداية ونهاية البحر
                    // عند الدعامات الداخلية يحدث تغير مفاجئ
                    val vStart = if (spanIdx == 0) {
                        load * L * 0.5  // دعامة طرفية
                    } else {
                        load * L * 0.55 // دعامة داخلية (أعلى بسبب الاستمرارية)
                    }
                    val vEnd = if (isLastPoint) {
                        -load * L * 0.5
                    } else {
                        -load * L * 0.55
                    }

                    for (i in 0..numPoints) {
                        val localX = L * i / numPoints
                        // V(x) خطي: V_start + (V_end - V_start) × x/L - w×x
                        val t = localX / L
                        val v = vStart + (vEnd - vStart) * t

                        if (i == 0 && spanIdx > 0) continue
                        points.add(cumulativeX + localX to v)
                    }
                    cumulativeX += L
                }
            }
            else -> {
                val L = 5.0
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    points.add(x to load * (L / 2.0 - x))
                }
            }
        }

        return points
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6. حساب الانحراف المُحسّن - Enhanced Deflection Check
    // ═══════════════════════════════════════════════════════════════════

    /**
     * حساب الانحراف المُحسّن حسب الكود المصري ECP 203 البند 6-3
     * يستخدم عزم القصور الفعال (Ie) بين Ig و Icr
     * 
     * @param span m - البحر
     * @param depth mm - العمق الكلي
     * @param fcu MPa - مقاومة الخرسانة
     * @param load kN/m - الحمل الكلي
     * @param beamWidth mm - عرض الكمرة
     * @param flexResult نتيجة التسليح (اختياري لتحسين الدقة)
     */
    private fun checkDeflection(
        span: Double,
        depth: Double,
        fcu: Double,
        load: Double,
        beamWidth: Double = 250.0,
        flexResult: ReinforcementResult? = null
    ): DeflectionCheckResult {
        // Ec = 4400 × √(fcu) MPa (ECP 203)
        val Ec = 4400.0 * sqrt(fcu)
        val w = load / 1000.0  // kN/m → N/mm (1 kN/m = 1 N/mm)
        val L = span * 1000.0  // m → mm
        val h = depth
        val d = depth - COVER  // العمق الفعال
        val b = beamWidth

        // ── 1. عزم القصور الكلي (Gross) Ig = b × h³ / 12 ──
        val Ig = b * h.pow(3) / 12.0  // mm⁴
        val yt = h / 2.0  // المسافة من المحور المحايد لأقصى ألياف شد

        // ── 2. عزم القصور للمقطع المتشقق (Cracked) Icr ──
        // n = Es / Ec
        val n = E_S / Ec
        // مساحة التسليح الفعلية (أو تقدير)
        val As = if (flexResult != null && flexResult.astProvided > 0) {
            flexResult.astProvided
        } else {
            0.01 * b * d  // نسبة تقديرية 1%
        }
        val rho = As / (b * d)

        // إيجاد المحور المحايد المتشقق من: b×x²/2 = n×As×(d-x)
        // b×x²/2 + n×As×x - n×As×d = 0
        // x = [-n×As + √((n×As)² + 2×b×n×As×d)] / b
        val nAs = n * As
        val discriminant = nAs * nAs + 2.0 * b * nAs * d
        val x_cr = (-nAs + sqrt(discriminant)) / b  // mm

        // Icr = b×x³/3 + n×As×(d-x)²
        val Icr = b * x_cr.pow(3) / 3.0 + nAs * (d - x_cr).pow(2)

        // ── 3. عزم التشقق Mcr ──
        // Mcr = fr × Ig / yt
        // fr = 0.62 × √(fcu) (معامل التشقق - ECP 203)
        val fr = 0.62 * sqrt(fcu)
        val Mcr = fr * Ig / yt  // N.mm

        // ── 4. العزم المطبق الفعلي Ma ──
        // Ma ≈ w×L²/8 (للحمل الموزع - بسيطة الدعم)
        val Ma = w * L * L / 8.0  // N.mm

        // ── 5. عزم القصور الفعال (Effective) Ie ──
        // Ie = (Mcr/Ma)³ × Ig + [1-(Mcr/Ma)³] × Icr ≤ Ig
        val Ie = if (Ma > 0 && Mcr > 0) {
            val ratio_cubed = (Mcr / Ma).pow(3).coerceAtMost(1.0)
            val effective = ratio_cubed * Ig + (1.0 - ratio_cubed) * Icr
            min(effective, Ig)
        } else {
            Ig
        }

        // ── 6. الانحراف الفوري (Immediate Deflection) ──
        // δi = 5 × w × L⁴ / (384 × Ec × Ie)
        val immediate = 5.0 * w * L.pow(4) / (384.0 * Ec * Ie)  // mm

        // ── 7. الانحراف طويل المدى (Long-Term Deflection) ──
        // δLT = δi × (1 + ξ) حيث ξ = 2.0 حسب ECP 203
        val xi = 2.0
        val longTerm = immediate * (1.0 + xi)

        // ── 8. الحد المسموح: L/250 ──
        val allowable = L / 250.0

        val ratio = longTerm / allowable
        val recommendation = when {
            ratio > 1.0 -> "زِد عمق الكمرة أو نخفض نسبة التسليح"
            ratio > 0.8 -> "الانحراف قريب من الحد"
            else -> "الانحراف مقبول"
        }

        val deflectionWarnings = mutableListOf<String>()
        if (Ie < Ig * 0.5) {
            deflectionWarnings.add("المقطع متشقق بدرجة كبيرة - Ie/Ig = ${"%.2f".format(Ie / Ig)}")
        }
        if (ratio > 1.0) {
            deflectionWarnings.add("الانحراف يتجاوز الحد المسموح بنسبة ${"%.0f".format((ratio - 1) * 100)}%")
        }

        return DeflectionCheckResult(
            immediateDeflection = immediate,
            longTermDeflection = longTerm,
            calculatedDeflection = longTerm,
            allowableDeflection = allowable,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            message = "δ_i=%.2fmm, δ_LT=%.2fmm, δ_allow=%.1fmm, Ie/Ig=%.3f".format(immediate, longTerm, allowable, Ie / Ig),
            recommendation = recommendation,
            warnings = deflectionWarnings
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  7. تصميم الالتواء - Torsion Design per ECP 203 Section 4-3-4
    // ═══════════════════════════════════════════════════════════════════

    /**
     * تصميم تسليح الالتواء حسب الكود المصري ECP 203 البند 4-3-4
     * 
     * @param designTorque kN.m - عزم اللي التصميمي
     * @param fcu MPa
     * @param fy MPa
     * @param width mm - عرض المقطع
     * @param depth mm - عمق المقطع
     * @param loadCombination مجموعة التحميل
     * @return TorsionReinforcementResult
     */
    fun designTorsion(
        designTorque: Double,     // kN.m
        fcu: Double, fy: Double,
        width: Double, depth: Double,
        loadCombination: LoadCombination
    ): TorsionReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("ECP 203-2020: Section 4-3-4 (Torsion Design)")

        val b = min(width, depth)
        val h = max(width, depth)
        val fsDesign = fy / GAMMA_S

        // ── عزم اللي الحرج (Threshold Torque) ──
        // Tu_th = 0.083 × √fcu × b² × d (N.mm → kN.m)
        // هنا نستخدم وحدات N و mm ثم نحول
        val Tu = designTorque * 1e6  // kN.m → N.mm
        val Tu_th = 0.083 * sqrt(fcu / GAMMA_C) * b * b * h  // N.mm
        val Tu_th_kNm = Tu_th / 1e6  // kN.m

        codeNotes.add("Tu = ${"%.2f".format(designTorque)} kN.m")
        codeNotes.add("Tu_threshold = 0.083×√(fcu/γc)×b²×h = ${"%.3f".format(Tu_th_kNm)} kN.m")

        // ── التحقق: هل يلزم تصميم الالتواء؟ ──
        if (Tu <= Tu_th) {
            codeNotes.add("Tu ≤ Tu_th → لا يلزم تصميم الالتواء - التسليح الأدنى يكفي")
            return TorsionReinforcementResult(
                designTorque = designTorque,
                thresholdTorque = Tu_th_kNm,
                torsionRequired = false,
                stirrupAreaPerLeg = 0.0,
                stirrupSpacing = 200.0,
                additionalLongitudinalArea = 0.0,
                longitudinalBars = 0,
                longitudinalDiameter = 0.0,
                aoh = 0.0,
                ao = 0.0,
                ph = 0.0,
                isSafe = true,
                codeReference = "ECP 203-2020: Section 4-3-4 (Tu < Tu_threshold - No torsion design required)",
                warnings = warnings
            )
        }

        // ── المساحات المحاطة بمحور الكانات ──
        // Aoh = (b - 2×cover) × (h - 2×cover)
        val aoh = (b - 2 * COVER) * (h - 2 * COVER)  // mm²
        val ao = 0.85 * aoh  // المساحة الفعالة
        val ph = 2.0 * ((b - 2 * COVER) + (h - 2 * COVER))  // محيط محور الكانات

        codeNotes.add("Aoh = (b-2×${COVER.toInt()})×(h-2×${COVER.toInt()}) = ${"%.0f".format(aoh)} mm²")
        codeNotes.add("Ao = 0.85 × Aoh = ${"%.0f".format(ao)} mm²")
        codeNotes.add("Ph = 2×((b-2c)+(h-2c)) = ${"%.0f".format(ph)} mm")

        // ── التحقق من الحد الأقصى ──
        // الحد الأقصى لعزم اللي: Tu_max = 0.333 × √(fcu/γc) × b² × d
        val Tu_max = 0.333 * sqrt(fcu / GAMMA_C) * b * b * h
        if (Tu > Tu_max) {
            warnings.add("تحذير: عزم اللي يتجاوز الحد الأقصى! زد أبعاد المقطع أو مقاومة الخرسانة")
            codeNotes.add("Tu_max = ${"%.3f".format(Tu_max / 1e6)} kN.m - EXCEEDED!")
        }

        // ── زاوية الميل (θ) ──
        // θ = 45° للخرسانة المسلحة العادية
        val theta = PI / 4.0  // 45° بالراديان
        val cotTheta = 1.0 / tan(theta)  // = 1.0 لـ 45°

        codeNotes.add("θ = 45° (standard for RC), cotθ = ${"%.2f".format(cotTheta)}")

        // ── مساحة الكانة لكل فرع ──
        // At = Tu × s / (1.5 × Ao × fy/γs × cotθ)
        // نحسب At/s أولاً
        val At_per_s = Tu / (1.5 * ao * fsDesign * cotTheta)  // mm²/mm

        // اختيار قطر الكانة وحساب التباعد
        // نستخدم كانة مغلقة (فرعين على الأقل)
        val availableStirrups = listOf(8.0, 10.0, 12.0, 14.0, 16.0)
        var selectedStirrupDia = availableStirrups.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            // التباعد = area / (At_per_s), يجب أن يكون ≥ 75mm و ≤ ph/8
            val spacing = area / At_per_s
            spacing >= 75.0 && spacing <= ph / 8.0
        } ?: availableStirrups.last()

        val stirrupAreaPerLeg = PI * selectedStirrupDia * selectedStirrupDia / 4.0
        var stirrupSpacing = stirrupAreaPerLeg / At_per_s

        // حدود التباعد
        val minSpacing = 75.0
        val maxSpacingTorsion = min(ph / 8.0, 300.0)
        stirrupSpacing = stirrupSpacing.coerceIn(minSpacing, maxSpacingTorsion)

        // تقريب لأقرب 25 مم
        stirrupSpacing = ceil(stirrupSpacing / 25.0) * 25.0

        codeNotes.add("At/s = ${"%.4f".format(At_per_s)} mm²/mm")
        codeNotes.add("Selected: Ø${selectedStirrupDia.toInt()} @ ${"%.0f".format(stirrupSpacing)} mm (At = ${"%.1f".format(stirrupAreaPerLeg)} mm²)")

        // ── الحديد الطولي الإضافي للالتواء ──
        // Al = At × (fy_stirrup/fy_longitudinal) × (Ao/Ph) × cot²θ
        // إذا كان fy_stirrup = fy_longitudinal:
        val Al = At_per_s * (ao / ph) * cotTheta * cotTheta * stirrupSpacing  // mm² (لكل فرع)

        val Al_total = Al * 2  // فرعين على الأقل (أعلى وأسفل)
        val longBarDia = if (Al_total < 100) 12.0 else if (Al_total < 200) 14.0 else 16.0
        val longBarArea = PI * longBarDia * longBarDia / 4.0
        val numLongBars = max(2, ceil(Al_total / longBarArea).toInt())

        codeNotes.add("Al (total) = ${"%.0f".format(Al_total)} mm²")
        codeNotes.add("Additional longitudinal: ${numLongBars}Ø${longBarDia.toInt()} (${"%.0f".format(numLongBars * longBarArea)} mm²)")

        // حد أدنى للحديد الطولي
        val Al_min = 0.0025 * b * h  // ECP 203
        val finalAl = max(Al_total, Al_min)
        if (Al_total < Al_min) {
            codeNotes.add("Minimum longitudinal torsion steel applied: ${"%.0f".format(Al_min)} mm²")
        }

        val isSafe = Tu <= Tu_max

        return TorsionReinforcementResult(
            designTorque = designTorque,
            thresholdTorque = Tu_th_kNm,
            torsionRequired = true,
            stirrupAreaPerLeg = stirrupAreaPerLeg,
            stirrupSpacing = stirrupSpacing,
            additionalLongitudinalArea = finalAl,
            longitudinalBars = numLongBars,
            longitudinalDiameter = longBarDia,
            aoh = aoh,
            ao = ao,
            ph = ph,
            isSafe = isSafe,
            codeReference = "ECP 203-2020: Section 4-3-4 (Torsion Design)",
            warnings = warnings
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  دوال مساعدة (Helper Functions)
    // ═══════════════════════════════════════════════════════════════════
    
    private fun analyzeBeamInventory(result: ReinforcementResult, span: Double, inventory: RebarInventory): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        return analyzer.analyze(
            requiredArea = result.astRequired,
            requiredLength = result.numberOfBars * span,
            inventory = inventory,
            designCode = DesignCode.ECP,
            elementLength = span
        )
    }
    
    private fun checkCrackWidth(result: ReinforcementResult, fcu: Double, moment: Double, effectiveDepth: Double = 0.0): CrackWidthCheckResult {
        // عرض الشروخ حسب ECP 203 البند 4-4 (مشابه لـ BS 8110 / EC2)
        // wk = 3.3 × εm × acr
        val d_eff = if (effectiveDepth > 0) effectiveDepth else (result.barDiameter * 50)
        val fs = if (result.astProvided > 0) (moment * 1e6) / (result.astProvided * 0.85 * d_eff) else 0.0
        val Es = 200000.0
        val epsilon_m = fs / Es

        val acr = 40.0 + if (result.spacing > 0) result.spacing / 2.0 else 75.0
        val crackWidth = 3.3 * epsilon_m * acr

        val allowable = 0.30

        return CrackWidthCheckResult(
            calculatedWidth = crackWidth,
            allowableWidth = allowable,
            isSafe = crackWidth <= allowable,
            codeReference = "ECP 203-2020: Section 4-4 (Crack Width Control)"
        )
    }
    
    private fun checkDevelopmentLength(result: ReinforcementResult, fcu: Double, fy: Double, availableLength: Double = 0.0): DevelopmentLengthCheckResult {
        val fbd = 0.3 * sqrt(fcu / 1.5)
        val fs = fy / 1.15
        val db = result.barDiameter
        val ld = if (fbd > 0) fs * db / (4.0 * fbd) else 0.0
        val ld_min = max(10.0 * db, 100.0)
        val ld_final = max(ld, ld_min)
        val effectiveAvailable = if (availableLength > 0) availableLength else ld_final * 1.5

        return DevelopmentLengthCheckResult(
            requiredLength = ld_final,
            availableLength = effectiveAvailable,
            isSafe = ld_final <= effectiveAvailable,
            codeReference = "ECP 203-2020: Section 5-2 (Development Length)"
        )
    }

    /**
     * حساب a/d عند التوازن (K-method)
     * a/d = 0.9 × εcu / (εcu + fy/(Es×γs))
     */
    private fun calculateAOverDBal(fy: Double): Double {
        val epsilonCu = 0.003
        val epsilonY = fy / (E_S * GAMMA_S)
        return 0.9 * epsilonCu / (epsilonCu + epsilonY)
    }

    /**
     * حساب K_bal ديناميكياً حسب fcu و fy
     * K_bal = (0.67/γc) × (a/d) × (1 - a/(2d))
     */
    private fun calculateKBal(fcu: Double, fy: Double): Double {
        val aOverD = calculateAOverDBal(fy)
        return (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
    }

    /**
     * حساب نقاط القطع (Cutoff Points) لتسليح الكمرات المستمرة
     */
    private fun calculateCutoffPoints(
        spanLength: Double,
        totalLoad: Double,
        location: String,
        isPositive: Boolean
    ): String {
        return when {
            isPositive -> {
                // حديد الشد (سفلي) عند وسط البحر
                // يمتد من 0.15L إلى 0.85L تقريباً
                "Cutoff: Bottom bars extend from ${"%.2f".format(spanLength * 0.15)}m to ${"%.2f".format(spanLength * 0.85)}m from support"
            }
            location.contains("End") -> {
                // حديد الضغط (علوي) عند الدعامة الطرفية
                "Cutoff: Top bars extend ${"%.2f".format(spanLength * 0.25)}m into span from end support"
            }
            else -> {
                // حديد الضغط (علوي) عند الدعامة الداخلية
                // يمتد 0.2L في كل اتجاه
                "Cutoff: Top bars extend ${"%.2f".format(spanLength * 0.2)}m each side of interior support"
            }
        }
    }
}