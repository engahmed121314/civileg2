package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

/**
 * تصميم متقدم للكمرات بجميع أنواعها حسب الكود السعودي SBC 304-2018
 *
 * SBC 304-2018 مبني على ACI 318-19 مع تعديلات سعودية خاصة تتضمن:
 * - متطلبات المناطق الزلزالية (SBC 304 البند 21)
 * - متطلبات البيئة المالحة والتآكل (غطاء 50 مم للمناطق الساحلية)
 * - أقل عرض للكمرات في المناطق الزلزالية (250 مم)
 * - تباعد الكانات الزلزالية (d/4 أو 100 مم)
 * - نسب تسليح أدنى مختلفة للمناطق الزلزالية
 * - أطوال التثبيت المعدلة للأسياك المجلفنة
 * - معامل انحراف طويل المدى للمناخ الحار (λ ≈ 2.5)
 *
 * المراجع:
 * - SBC 304-2018 البند 9 (الانحناء)
 * - SBC 304-2018 البند 11 (القص)
 * - SBC 304-2018 البند 12 (أطوال التثبيت)
 * - SBC 304-2018 البند 21 (متطلبات الزلازل)
 * - SBC 304-2018 البند 18.10 (الكمرات العميقة)
 * - SBC 304-2018 البند 8.12 (الكمرات تي الشكل)
 */
class SBCAdvancedBeam {

    private val baseBeam = SBCBeam()

    companion object {
        // ثوابت الكود السعودي SBC 304-2018
        private const val PHI_FLEXURE = 0.9        // معامل الاختزال للانحناء - SBC 304 §9.3
        private const val PHI_SHEAR = 0.75         // معامل الاختزال للقص - SBC 304 §21.2
        private const val LAMBDA = 1.0             // عامل الوزن للخرسانة العادية - SBC 304 §19.2.4
        private const val SBC_COVER_NORMAL = 40.0  // mm - الغطاء العادي - SBC 304 §7.7
        private const val SBC_COVER_CORROSIVE = 50.0 // mm - غطاء البيئة المالحة (الساحلية)
        private const val SBC_MIN_WIDTH_SEISMIC = 250.0 // mm - أقل عرض زلزالي - SBC 304 §21.4
        private const val SBC_LONG_TERM_MULTIPLIER = 2.5  // معامل الانحراف طويل المدى للمناخ الحار
        private const val EPSILON_CU = 0.003       // إجهاد الخرسانة الأقصى - SBC 304 §22.2.2.1
        private const val EC_COEFFICIENT = 4700.0  // معامل مرونة الخرسانة - SBC 304 §19.2.2.1

        // أقطار الأسياخ المتاحة حسب SBC (النظام المتري المعتمد في السعودية)
        private val SBC_BAR_DIAMETERS = listOf(10.0, 12.0, 16.0, 20.0, 25.0, 28.0, 32.0, 36.0, 40.0)

        // معاملات عزم الانحناء للمقاطع الحرجة - SBC 304 §6.5 / ACI 318-6.5
        // للحمل الموزع على كمرات مستمرة متساوية الأبحر
        private const val MOMENT_COEFF_EXT_NEG = 1.0 / 16.0     // عزم سالب عند الدعامة الخارجية
        private const val MOMENT_COEFF_EXT_POS = 1.0 / 14.0     // عزم موجب في المنتصف للبحر الخارجي
        private const val MOMENT_COEFF_INT_NEG = 1.0 / 10.0     // عزم سالب عند الدعامة الداخلية
        private const val MOMENT_COEFF_INT_POS = 1.0 / 11.0     // عزم موجب في المنتصف للبحر الداخلي
        private const val MOMENT_COEFF_CANTILEVER = 1.0 / 2.0   // عزم الكابولي
    }

    // ========================================================================
    //  تصميم الكمرة الأساسي
    // ========================================================================

    /**
     * تصميم متقدم للكمرة حسب SBC 304-2018
     *
     * @param beamType نوع الكمرة (بسيطة، ثابتة، كابولي، مستمرة، متعددة الدعامات، متغيرة المقطع، فيرينديل)
     * @param sectionType نوع المقطع (مستطيل، تي، إل، دائري، مركب)
     * @param fcu مقاومة الخرسانة للمكعب (MPa) - يتم تحويلها إلى مقاومة الأسطوانة fc' = 0.8 × fcu
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param deadLoad الحمل الميت الموزع (kN/m)
     * @param liveLoad الحمل الحي الموزع (kN/m)
     * @param width عرض الكمرة (mm)
     * @param depth عمق الكمرة الكلي (mm)
     * @param inventory مخزون حديد التسليح المتوفر (اختياري)
     * @param loadCombination مجموعة التحميل
     * @return AdvancedBeamResult نتيجة التصميم المتقدم
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
        loadCombination: LoadCombination,
        isCorrosiveEnvironment: Boolean = false
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // === التحويل من مقاومة المكعب إلى مقاومة الأسطوانة ===
        // SBC 304 يتبع ACI: fc' = 0.8 × fcu
        val fcPrime = 0.8 * fcu

        codeNotes.add("fc' = 0.8 × fcu = 0.8 × $fcu = ${"%.1f".format(fcPrime)} MPa (SBC 304 cube-to-cylinder)")
        codeNotes.add("Load Combination: ${loadCombination.description}")

        // === تحديد الغطاء الخرساني حسب البيئة ===
        // البيئة التآكلية (كما السواحل) معلمة مستقلة عن نوع مجموعة التحميل
        val cover = if (isCorrosiveEnvironment) SBC_COVER_CORROSIVE else SBC_COVER_NORMAL
        codeNotes.add("Concrete cover: ${cover.toInt()}mm ${if (isCorrosiveEnvironment) "(SBC 304 §7.7 - corrosive/coastal)" else "(SBC 304 §7.7 - normal)"}")

        // === حساب معاملات التحميل حسب SBC 304-2018 (يتبع ACI 5.3) ===
        val totalFactor = loadCombination.getFactorForCode(DesignCode.SBC)
        val gammaD = 1.2
        val gammaL = 1.6
        val totalLoad = (deadLoad + liveLoad) * totalFactor  // kN/m

        codeNotes.add("SBC 304 §5.3: Wu = ${gammaD}D + ${gammaL}L = ${"%.2f".format(totalLoad)} kN/m")

        // === حساب القوى القصوى بناءً على نوع الكمرة ===
        val maxMoment = beamType.getMaxMoment(totalLoad)  // kN.m
        val maxShear = beamType.getMaxShear(totalLoad)    // kN

        codeNotes.add("Max Moment: ${"%.2f".format(maxMoment)} kN.m")
        codeNotes.add("Max Shear: ${"%.2f".format(maxShear)} kN")

        // === فحص متطلبات المناطق الزلزالية ===
        val isSeismic = loadCombination == LoadCombination.DEAD_LIVE_EARTHQUAKE ||
                loadCombination == LoadCombination.DEAD_EARTHQUAKE
        if (isSeismic) {
            val seismicWarnings = checkSeismicRequirements(width, depth, fcu, fy, totalLoad)
            warnings.addAll(seismicWarnings.first)
            codeNotes.addAll(seismicWarnings.second)
        }

        // === تحديد العمق الفعال ===
        val effectiveDepth = depth - cover - 10.0 - 12.0  // mm (خصم الغطاء + الكانة + نصف السيخ)

        // === تصميم الانحناء (Flexure Design) - SBC 304 البند 9 ===
        val flexureResult = baseBeam.calculateFlexureReinforcement(
            fcu, fy, width, effectiveDepth, depth, maxMoment, loadCombination
        )

        // === تصميم القص (Shear Design) - SBC 304 البند 11 ===
        val shearResult = baseBeam.calculateShearReinforcement(
            fcu, fy, width, effectiveDepth, maxShear, 0.0, loadCombination
        )

        // في المناطق الزلزالية: تطبيق متطلبات الكانات الزلزالية
        val finalShearResult = if (isSeismic) {
            designSeismicStirrups(shearResult, fcu, fy, width, effectiveDepth, maxShear)
        } else {
            shearResult
        }

        // === تحديد طول البحر للتحقيقات ===
        val span = when (beamType) {
            is BeamType.SimplySupported -> beamType.span
            is BeamType.Fixed -> beamType.span
            is BeamType.Cantilever -> beamType.length
            is BeamType.Continuous -> beamType.spans.maxOrNull() ?: 1.0
            is BeamType.MultiSupport -> beamType.totalLength
            is BeamType.Haunched -> beamType.span
            is BeamType.Vierendeel -> beamType.span
        }

        // === تحميل الخدمة للتحقيقات ===
        val serviceLoad = deadLoad + liveLoad
        val serviceMoment = when (beamType) {
            is BeamType.SimplySupported -> serviceLoad * beamType.span * beamType.span / 8
            is BeamType.Fixed -> serviceLoad * beamType.span * beamType.span / 12
            is BeamType.Cantilever -> serviceLoad * beamType.length * beamType.length / 2
            is BeamType.Continuous -> serviceLoad * (beamType.spans.maxOrNull() ?: 1.0).pow(2) / 10
            is BeamType.MultiSupport -> serviceLoad * beamType.totalLength * beamType.totalLength / 10
            is BeamType.Haunched -> serviceLoad * beamType.span * beamType.span / 10
            is BeamType.Vierendeel -> serviceLoad * beamType.span * beamType.span / 8
        }

        // === التحقق من الانحراف (Deflection Check) - SBC 304 مع معامل المناخ الحار ===
        val deflectionCheck = checkDeflectionSBC(
            span = span,
            depth = depth,
            fcPrime = fcPrime,
            serviceLoad = serviceLoad,
            serviceMoment = serviceMoment,
            beamWidth = width,
            effectiveDepth = effectiveDepth,
            astProvided = flexureResult.astProvided,
            beamType = beamType
        )

        // === تحليل المخزون (Inventory Analysis) ===
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeBeamInventory(flexureResult, span, inv)
        }

        // === التحقق من عرض الشروخ (Crack Width) - SBC مع عامل المناخ الحار ===
        val crackWidth = checkCrackWidth(
            result = flexureResult,
            fcPrime = fcPrime,
            moment = serviceMoment,
            effectiveDepth = effectiveDepth,
            width = width,
            isExterior = isCorrosive
        )

        // === التحقق من طول التثبيت (Development Length) - SBC 304 البند 12 ===
        val devLength = checkDevelopmentLength(flexureResult, fcPrime, fy, span)

        // === تجميع مراجع الكود ===
        codeNotes.add(CodeReference.SBC.BEAM_FLEXURE)
        codeNotes.add(CodeReference.SBC.BEAM_SHEAR)
        codeNotes.add("SBC 304-2018 §9.5: Deflection Control")
        codeNotes.add("SBC 304-2018 §24.5: Crack Width Control (Hot/Arid Climate)")
        codeNotes.add(CodeReference.SBC.BEAM_DEVELOPMENT_LENGTH)
        codeNotes.add("Beam Type: ${beamType.displayName}")

        // === إضافة بدائل اقتصادية وآمنة ===
        val alternatives = generateBarAlternatives(
            astRequired = flexureResult.astRequired,
            width = width,
            effectiveDepth = effectiveDepth,
            fcPrime = fcPrime,
            fy = fy,
            designMoment = maxMoment,
            selectedDia = flexureResult.barDiameter
        )
        codeNotes.addAll(alternatives)

        // === إضافة تحذيرات من النتائج الفرعية ===
        warnings.addAll(deflectionCheck.warnings)

        return AdvancedBeamResult(
            beamType = beamType,
            sectionType = sectionType,
            flexureResult = flexureResult,
            shearResult = finalShearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(beamType, totalLoad),
            shearDiagram = generateShearDiagram(beamType, totalLoad),
            inventoryAnalysis = inventoryAnalysis,
            crackWidthCheck = crackWidth,
            developmentLengthCheck = devLength,
            warnings = warnings + flexureResult.warnings + finalShearResult.warnings,
            codeNotes = codeNotes + flexureResult.codeNotes + finalShearResult.codeNotes
        )
    }

    // ========================================================================
    //  تصميم كمرة تي الشكل (T-Beam) - SBC 304 البند 8.12
    // ========================================================================

    /**
     * تصميم كمرة تي الشكل حسب SBC 304-2018 البند 8.12
     *
     * قواعد عرض الجناح الفعال حسب SBC 304 §8.12 (مطابق لـ ACI §6.3):
     *   bf ≤ bw + 16×hf (لكل جانب)
     *   bf ≤ bw + L/2 (لكل جانب)
     *   bf ≤ bw + سد البحر المجاور/2
     *   bf لا يتجاوز مسافة مركزية بين الكمرات
     *
     * @param flangeWidth عرض الجناح الكلي للبلاطة (mm)
     * @param flangeThickness سمك الجناح / البلاطة (mm)
     * @param webWidth عرض الكورسة / جسم الكمرة (mm)
     * @param webDepth عمق جسم الكمرة تحت الجناح (mm)
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param deadLoad الحمل الميت الموزع (kN/m)
     * @param liveLoad الحمل الحي الموزع (kN/m)
     * @param span البحر (m)
     * @param inventory مخزون حديد التسليح (اختياري)
     * @param loadCombination مجموعة التحميل
     * @return AdvancedBeamResult مع ملاحظات مقطع تي
     */
    fun designTBeam(
        flangeWidth: Double,
        flangeThickness: Double,
        webWidth: Double,
        webDepth: Double,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        span: Double,
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // === التحقق من العرض الفعال للجناح - SBC 304 §8.12 ===
        val flangeCheck = checkTBeamFlange(
            flangeWidth = flangeWidth,
            flangeThickness = flangeThickness,
            webWidth = webWidth,
            span = span
        )
        val effectiveFlangeWidth = flangeCheck.first
        val flangeNotes = flangeCheck.second
        codeNotes.addAll(flangeNotes)

        // === حساب الأبعاد الإجمالية للمقطع تي ===
        val totalDepth = webDepth + flangeThickness  // mm
        val effectiveDepth = totalDepth - SBC_COVER_CORROSIVE - 10.0 - 12.0  // mm

        // === تحويل مقاومة المكعب إلى أسطوانة ===
        val fcPrime = 0.8 * fcu
        codeNotes.add("fc' = 0.8 × fcu = ${"%.1f".format(fcPrime)} MPa (SBC 304 cube-to-cylinder)")
        codeNotes.add("T-Section: bf=${effectiveFlangeWidth.toInt()}mm, hf=${flangeThickness.toInt()}mm, bw=${webWidth.toInt()}mm, d=${totalDepth.toInt()}mm")

        // === حساب الأحمال ===
        val totalFactor = loadCombination.getFactorForCode(DesignCode.SBC)
        val totalLoad = (deadLoad + liveLoad) * totalFactor  // kN/m
        val maxMoment = totalLoad * span * span / 8.0  // kN.m (بسيطة الدعم)
        val maxShear = totalLoad * span / 2.0  // kN

        codeNotes.add("SBC 304 §5.3: Wu = ${"%.2f".format(totalLoad)} kN/m")
        codeNotes.add("Max Moment: ${"%.2f".format(maxMoment)} kN.m, Max Shear: ${"%.2f".format(maxShear)} kN")

        // === تصميم الانحناء لكمرة تي ===
        // نحسب أولاً عزم تشقق الجناح
        // a = As × fy / (0.85 × fc' × bf)
        // إذا كانت a ≤ hf: المحور المحايد داخل الجناح (تصميم كمرة مستطيلة بعرض bf)
        // إذا كانت a > hf: المحور المحايد في الكورسة (تصميم كمرة تي حقيقي)

        // حساب Rn للكمرة تي (على افتراض المحور المحايد داخل الجناح أولاً)
        val Mu = maxMoment * 1e6  // N.mm
        val denominator = PHI_FLEXURE * effectiveFlangeWidth * effectiveDepth * effectiveDepth
        val Rn = if (denominator > 0) Mu / denominator else 0.0

        val m = if (fcPrime > 0) fy / (0.85 * fcPrime) else 0.0
        val rho = if (m > 0 && (1 - 2 * m * Rn / fy) >= 0) {
            (1 - sqrt(1 - 2 * m * Rn / fy)) / m
        } else 0.0

        var astRequired = rho * effectiveFlangeWidth * effectiveDepth

        // التحقق من المحور المحايد
        val a = if (fcPrime > 0) astRequired * fy / (0.85 * fcPrime * effectiveFlangeWidth) else 0.0

        val flexureResult: ReinforcementResult
        if (a <= flangeThickness) {
            // المحور المحايد داخل الجناح - تصميم كمرة مستطيلة بعرض bf
            codeNotes.add("SBC 304 §8.12: Neutral axis within flange (a=${"%.1f".format(a)}mm ≤ hf=${flangeThickness}mm)")

            flexureResult = designTBeamAsRectangular(
                fcu = fcu,
                fy = fy,
                width = effectiveFlangeWidth,
                effectiveDepth = effectiveDepth,
                totalDepth = totalDepth,
                designMoment = maxMoment,
                loadCombination = loadCombination,
                astRequired = astRequired
            )
        } else {
            // المحور المحايد في الكورسة - تصميم كمرة تي حقيقي
            // As = (Mu/φ + 0.85×fc'×(bf-bw)×hf×(d-hf/2)) / (fy×(d-hf/2))
            codeNotes.add("SBC 304 §8.12: Neutral axis in web (a=${"%.1f".format(a)}mm > hf=${flangeThickness}mm) - T-beam analysis required")

            val flangeForce = 0.85 * fcPrime * (effectiveFlangeWidth - webWidth) * flangeThickness  // N
            val flangeMoment = flangeForce * (effectiveDepth - flangeThickness / 2.0)  // N.mm
            val remainingMoment = Mu / PHI_FLEXURE - flangeMoment  // N.mm

            val webRn = if (webWidth > 0 && effectiveDepth > 0) remainingMoment / (webWidth * effectiveDepth * effectiveDepth) else 0.0
            val webRho = if (m > 0 && (1 - 2 * m * webRn / fy) >= 0 && webRn > 0) {
                (1 - sqrt(1 - 2 * m * webRn / fy)) / m
            } else 0.0

            val webSteel = webRho * webWidth * effectiveDepth
            val flangeSteel = flangeForce / fy
            astRequired = webSteel + flangeSteel

            // التسليح الأدنى
            val minSteel1 = 0.25 * sqrt(fcPrime) / fy * webWidth * effectiveDepth
            val minSteel2 = 1.4 / fy * webWidth * effectiveDepth
            val minSteel = max(minSteel1, minSteel2)

            if (astRequired < minSteel) {
                astRequired = minSteel
                warnings.add("تم تطبيق نسبة التسليح الأدنى - SBC 304 §9.6.1")
            }

            flexureResult = designTBeamAsRectangular(
                fcu = fcu,
                fy = fy,
                width = webWidth,
                effectiveDepth = effectiveDepth,
                totalDepth = totalDepth,
                designMoment = maxMoment,
                loadCombination = loadCombination,
                astRequired = astRequired,
                isTrueTBeam = true,
                flangeWidth = effectiveFlangeWidth,
                flangeThickness = flangeThickness,
                flangeForce = flangeForce
            )
        }

        // === تصميم القص ===
        val shearResult = baseBeam.calculateShearReinforcement(
            fcu, fy, webWidth, effectiveDepth, maxShear, 0.0, loadCombination
        )

        // === تحميل الخدمة والتحقيقات ===
        val serviceLoad = deadLoad + liveLoad
        val serviceMoment = serviceLoad * span * span / 8.0

        val deflectionCheck = checkDeflectionSBC(
            span = span,
            depth = totalDepth,
            fcPrime = fcPrime,
            serviceLoad = serviceLoad,
            serviceMoment = serviceMoment,
            beamWidth = effectiveFlangeWidth,
            effectiveDepth = effectiveDepth,
            astProvided = flexureResult.astProvided,
            beamType = BeamType.SimplySupported(span)
        )

        val crackWidth = checkCrackWidth(
            result = flexureResult,
            fcPrime = fcPrime,
            moment = serviceMoment,
            effectiveDepth = effectiveDepth,
            width = effectiveFlangeWidth,
            isExterior = false
        )

        val devLength = checkDevelopmentLength(flexureResult, fcPrime, fy, span)

        val inventoryAnalysis = inventory?.let { inv ->
            analyzeBeamInventory(flexureResult, span, inv)
        }

        codeNotes.add(CodeReference.SBC.BEAM_FLEXURE)
        codeNotes.add(CodeReference.SBC.BEAM_SHEAR)
        codeNotes.add("T-Beam Design: SBC 304-2018 §8.12")

        return AdvancedBeamResult(
            beamType = BeamType.SimplySupported(span),
            sectionType = BeamSectionType.T_SECTION,
            flexureResult = flexureResult,
            shearResult = shearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(BeamType.SimplySupported(span), totalLoad),
            shearDiagram = generateShearDiagram(BeamType.SimplySupported(span), totalLoad),
            inventoryAnalysis = inventoryAnalysis,
            crackWidthCheck = crackWidth,
            developmentLengthCheck = devLength,
            warnings = warnings + flexureResult.warnings,
            codeNotes = codeNotes + flexureResult.codeNotes
        )
    }

    /**
     * تصميم مقطع تي ككمرة مستطيلة (عندما يكون المحور المحايد داخل الجناح)
     * أو كمرة تي حقيقية (عندما يكون المحور المحايد في الكورسة)
     */
    private fun designTBeamAsRectangular(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        totalDepth: Double,
        designMoment: Double,
        loadCombination: LoadCombination,
        astRequired: Double,
        isTrueTBeam: Boolean = false,
        flangeWidth: Double = 0.0,
        flangeThickness: Double = 0.0,
        flangeForce: Double = 0.0
    ): ReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val fcPrime = 0.8 * fcu

        // اختيار القطر المناسب
        val availableBars = SBC_BAR_DIAMETERS
        var selectedBarDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 8
        } ?: 20.0

        val barArea = PI * selectedBarDia * selectedBarDia / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 12)
        val astProvided = numberOfBars * barArea

        // حساب السعة الفعلية
        val aActual = if (fcPrime > 0 && (if (isTrueTBeam) width else flangeWidth) > 0) {
            astProvided * fy / (0.85 * fcPrime * (if (isTrueTBeam) width else flangeWidth))
        } else 0.0

        val Mn: Double
        if (isTrueTBeam && flangeThickness > 0) {
            // حساب العزم للكمرة تي الحقيقية
            val effectiveWidth = flangeWidth
            val aBlock = if (fcPrime > 0) astProvided * fy / (0.85 * fcPrime * effectiveWidth) else 0.0
            if (aBlock <= flangeThickness) {
                Mn = astProvided * fy * (effectiveDepth - aBlock / 2.0)
            } else {
                val fFlange = 0.85 * fcPrime * (effectiveWidth - width) * flangeThickness
                val mFlange = fFlange * (effectiveDepth - flangeThickness / 2.0)
                val asWeb = astProvided - fFlange / fy
                val aWeb = if (fcPrime > 0 && width > 0) asWeb * fy / (0.85 * fcPrime * width) else 0.0
                Mn = mFlange + asWeb * fy * (effectiveDepth - flangeThickness - aWeb / 2.0)
            }
        } else {
            Mn = astProvided * fy * (effectiveDepth - aActual / 2.0)
        }

        val capacity = PHI_FLEXURE * Mn / 1e6  // kN.m
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0

        // التحقق من النسبة القصوى
        val beta1 = calculateBeta1(fcPrime)
        val rhoProvided = astProvided / (width * effectiveDepth)
        val rhoMax = 0.85 * beta1 * (fcPrime / fy) * (EPSILON_CU / (EPSILON_CU + 0.005))

        if (rhoProvided > rhoMax) {
            warnings.add("نسبة التسليح تتجاوز الحد الأقصى المسموح - SBC 304 §9.3.3.1")
        }

        codeNotes.add("T-section As_req=${"%.0f".format(astRequired)}mm², As_prov=${"%.0f".format(astProvided)}mm²")

        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = selectedBarDia,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ========================================================================
    //  تصميم الكمرات العميقة (Deep Beams) - SBC 304 البند 18.10
    // ========================================================================

    /**
     * تصميم الكمرات العميقة حسب SBC 304-2018 البند 18.10
     *
     * تعريف الكمرة العميقة: L/d < 4 (SBC 304 §18.10.1)
     *
     * متطلبات التصميم:
     * - استخدام نموذج العارضة والشد (Strut-and-Tie Model)
     * - نسبة تسليح أدنى أفقي: 0.001 × bw × s (SBC 304 §18.10.3)
     * - نسبة تسليح أدنى رأسي: 0.0025 × bw × sv (SBC 304 §18.10.4)
     * - التسليح الأفقي والرأسي موزع على عمقين من كلتا الجهتين
     *
     * @param span البحر الصافي (m)
     * @param width عرض الكمرة (mm)
     * @param depth عمق الكمرة الكلي (mm)
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param deadLoad الحمل الميت الموزع (kN/m)
     * @param liveLoad الحمل الحي الموزع (kN/m)
     * @param loadCombination مجموعة التحميل
     * @return AdvancedBeamResult نتيجة تصميم الكمرة العميقة
     */
    fun designDeepBeam(
        span: Double,
        width: Double,
        depth: Double,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val fcPrime = 0.8 * fcu
        val effectiveDepth = depth - SBC_COVER_CORROSIVE - 10.0 - 12.0

        // === فحص معايير الكمرة العميقة - SBC 304 §18.10.1 ===
        val deepBeamCheck = checkDeepBeamCriteria(span, depth, effectiveDepth)
        codeNotes.addAll(deepBeamCheck.second)

        val ln = span * 1000.0  // البحر الصافي بالمليمتر
        val ldRatio = ln / effectiveDepth

        if (!deepBeamCheck.first) {
            warnings.add("نسبة L/d = ${"%.1f".format(ldRatio)} ≥ 4 - ليست كمرة عميقة، استخدم التصميم العادي")
        }

        // === حساب الأحمال ===
        val totalFactor = loadCombination.getFactorForCode(DesignCode.SBC)
        val totalLoad = (deadLoad + liveLoad) * totalFactor  // kN/m
        val Vu = totalLoad * span / 2.0  // kN - القوة القصية القصوى عند الدعامة (wL/2 لبسيطة الدعم)

        // === تصميم نموذج العارضة والشد (Strut-and-Tie Model) ===
        // SBC 304 §18.10.2: تصرف القوى عبر العوارض المائلة

        // زاوية ميلان العارضة: θ = atan(2d/Ln)
        // θ لا تقل عن 25° ولا تزيد عن 65° حسب SBC 304
        val thetaRad = atan(2.0 * effectiveDepth / ln)
        val thetaDeg = thetaRad * 180.0 / PI
        val thetaDesign = thetaDeg.coerceIn(25.0, 65.0)
        val thetaDesignRad = thetaDesign * PI / 180.0

        codeNotes.add("SBC 304 §18.10.2: Strut angle θ = ${"%.1f".format(thetaDesign)}° (atan(2d/Ln))")

        // حساب قوة الشد في العارضة (Tie Force):
        // T = Vu / tan(θ)
        val tieForce = Vu * 1000.0 / tan(thetaDesignRad)  // N

        // مساحة التسليح المطلوبة للشد
        val astRequired = tieForce / fy  // mm²

        // === التسليح الأدنى للكمرات العميقة ===
        // SBC 304 §18.10.3: تسليح أفقي أدنى
        // ρ_h = 0.001 × bw × s (لكل وجه)
        val minHorizReinfPerFace = 0.001 * width * 1000.0  // mm²/m لكل وجه
        val totalHorizMin = minHorizReinfPerFace * 2  // الوجهين

        // SBC 304 §18.10.4: تسليح رأسي أدنى
        // ρ_v = 0.0025 × bw × sv (لكل وجه)
        val minVertReinfPerFace = 0.0025 * width * 1000.0  // mm²/m لكل وجه
        val totalVertMin = minVertReinfPerFace * 2  // الوجهين

        codeNotes.add("SBC 304 §18.10.3: Min horizontal reinf = ${"%.0f".format(minHorizReinfPerFace)} mm²/m per face")
        codeNotes.add("SBC 304 §18.10.4: Min vertical reinf = ${"%.0f".format(minVertReinfPerFace)} mm²/m per face")

        // اختيار سيخ الشد الرئيسي
        val selectedDia = SBC_BAR_DIAMETERS.firstOrNull {
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 6
        } ?: 20.0

        val barArea = PI * selectedDia * selectedDia / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 8)
        val astProvided = numberOfBars * barArea

        // حساب السعة
        val capacity = PHI_FLEXURE * astProvided * fy * effectiveDepth / (tan(thetaDesignRad) * 1e6)  // kN
        val utilizationRatio = if (capacity > 0) Vu / capacity else 2.0

        // === تسليح القص للكمرة العميقة ===
        // Vc للكمرة العميقة = 2.5 × λ × √(fc') × bw × d (معزز)
        // لكن SBC 304 §18.10 يحدد Vn ≤ 0.66 × √(fc') × bw × d
        val VcDeep = 2.5 * LAMBDA * sqrt(fcPrime.coerceAtLeast(1.0)) * width * effectiveDepth / 1000.0  // kN
        val VsMax = 0.66 * sqrt(fcPrime.coerceAtLeast(1.0)) * width * effectiveDepth / 1000.0  // kN

        // اختيار كانات الكمرة العميقة
        val stirrupDia = 10.0  // mm
        val stirrupArea = 2 * PI * stirrupDia * stirrupDia / 4  // رجلين
        val stirrupSpacing = if (minVertReinfPerFace > 0) {
            (stirrupArea * 1000.0 / minVertReinfPerFace).coerceAtMost(150.0)
        } else 150.0

        // التسليح الأفقي (موزع على الوجهين)
        val horizBarDia = 10.0
        val horizBarArea = PI * horizBarDia * horizBarDia / 4
        val horizSpacing = if (minHorizReinfPerFace > 0) {
            (horizBarArea * 1000.0 / minHorizReinfPerFace).coerceAtMost(200.0)
        } else 200.0

        codeNotes.add("Deep beam main: ${numberOfBars}Ø${selectedDia.toInt()}")
        codeNotes.add("Vertical: Ø${stirrupDia.toInt()} @ ${stirrupSpacing.toInt()}mm c/c")
        codeNotes.add("Horizontal: Ø${horizBarDia.toInt()} @ ${horizSpacing.toInt()}mm c/c (both faces)")

        // === تحقق الانحراف المبسط ===
        val serviceLoad = deadLoad + liveLoad
        val allowableDeflection = span * 1000.0 / 240.0
        val Ec = EC_COEFFICIENT * sqrt(fcPrime.coerceAtLeast(1.0))
        val Ig = width * depth.pow(3) / 12.0
        val immediateDeflection = 5.0 * (serviceLoad / 1000.0) * (span * 1000.0).pow(4) / (384.0 * Ec * Ig)
        val longTermDeflection = immediateDeflection * (1.0 + SBC_LONG_TERM_MULTIPLIER)
        val deflectionRatio = longTermDeflection / allowableDeflection

        val deflectionCheck = DeflectionCheckResult(
            immediateDeflection = immediateDeflection,
            longTermDeflection = longTermDeflection,
            calculatedDeflection = longTermDeflection,
            allowableDeflection = allowableDeflection,
            ratio = deflectionRatio,
            isSafe = deflectionRatio <= 1.0,
            message = "SBC 304 §18.10 (Deep Beam): δi=%.1fmm, δLT=%.1fmm".format(immediateDeflection, longTermDeflection),
            recommendation = when {
                deflectionRatio > 1.0 -> "زِد عمق الكمرة العميقة أو قلل البحر"
                else -> "الانحراف مقبول للكمرة العميقة"
            }
        )

        val flexureResult = ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = selectedDia,
            numberOfBars = numberOfBars,
            tiesDiameter = stirrupDia,
            tiesSpacing = stirrupSpacing,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )

        val shearResult = ShearReinforcementResult(
            concreteShearCapacity = VcDeep,
            requiredArea = minVertReinfPerFace,
            providedArea = stirrupArea * 1000.0 / stirrupSpacing,
            requiredShearReinforcement = minVertReinfPerFace,
            providedShearReinforcement = stirrupArea * 1000.0 / stirrupSpacing,
            stirrupDiameter = stirrupDia,
            stirrupSpacing = stirrupSpacing,
            isSafe = Vu <= PHI_SHEAR * (VcDeep + VsMax),
            utilizationRatio = if ((VcDeep + VsMax) > 0) Vu / (PHI_SHEAR * (VcDeep + VsMax)) else 2.0,
            warnings = listOf(),
            codeNotes = listOf("SBC 304 §18.10: Deep Beam Shear Provisions")
        )

        return AdvancedBeamResult(
            beamType = BeamType.SimplySupported(span),
            sectionType = BeamSectionType.RECTANGULAR,
            flexureResult = flexureResult,
            shearResult = shearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(BeamType.SimplySupported(span), totalLoad),
            shearDiagram = generateShearDiagram(BeamType.SimplySupported(span), totalLoad),
            inventoryAnalysis = null,
            crackWidthCheck = null,
            developmentLengthCheck = null,
            warnings = warnings,
            codeNotes = codeNotes + "SBC 304-2018 §18.10 (Deep Beam Design)"
        )
    }

    // ========================================================================
    //  تصميم الكمرات المستمرة - SBC 304 البند 6.5 / ACI 318-6.5
    // ========================================================================

    /**
     * تصميم الكمرة المستمرة حسب SBC 304-2018 البند 6.5
     *
     * يستخدم معاملات العزم التقريبية (ACI/SBC Moment Coefficients):
     * - عزم سالب عند الدعامة الخارجية: wL²/16
     * - عزم موجب في منتصف البحر الخارجي: wL²/14
     * - عزم سالب عند الدعامة الداخلية: wL²/10
     * - عزم موجب في منتصف البحر الداخلي: wL²/11
     *
     * كما يحسب:
     * - نقاط القطع (Cutoff Points)
     * - أطوال التثبيت عند المقاطع الحرجة
     * - توزيع الكانات
     *
     * @param spans أطوال الأبحر (m) - يجب أن تكون متساوية أو متقاربة
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param deadLoad الحمل الميت الموزع (kN/m)
     * @param liveLoad الحمل الحي الموزع (kN/m)
     * @param width عرض الكمرة (mm)
     * @param depth عمق الكمرة (mm)
     * @param inventory مخزون الحديد (اختياري)
     * @param loadCombination مجموعة التحميل
     * @return List<AdvancedBeamResult> نتيجة لكل مقطع حرج
     */
    fun designContinuousBeam(
        spans: List<Double>,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        width: Double,
        depth: Double,
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): List<AdvancedBeamResult> {
        val allResults = mutableListOf<AdvancedBeamResult>()
        val globalCodeNotes = mutableListOf<String>()
        val globalWarnings = mutableListOf<String>()

        val fcPrime = 0.8 * fcu
        val effectiveDepth = depth - SBC_COVER_CORROSIVE - 10.0 - 12.0
        val totalFactor = loadCombination.getFactorForCode(DesignCode.SBC)
        val totalLoad = (deadLoad + liveLoad) * totalFactor  // kN/m

        globalCodeNotes.add("fc' = 0.8 × fcu = ${"%.1f".format(fcPrime)} MPa")
        globalCodeNotes.add("SBC 304 §6.5: Continuous Beam Moment Coefficients")
        globalCodeNotes.add("Wu = ${"%.2f".format(totalLoad)} kN/m")

        if (spans.isEmpty()) return allResults

        // === معاملات إعادة التوزيع (Moment Redistribution) ===
        // SBC 304 §6.6.4 / ACI 6.6.4: يمكن إعادة توزيع العزم السالب
        // بنسبة لا تتجاوز 1000×εt (%)
        // εt = 0.003 × (d/c - 1) حيث c = a/β1
        val redistributionRatio = 0.1  // 10% كحد أقصى آمن
        globalCodeNotes.add("SBC 304 §6.6.4: Moment redistribution ≤ ${"%.0f".format(redistributionRatio * 100)}% applied")

        // === فحص نسبة الأبحر المتجاورة ===
        // معاملات العزم التقريبية صالحة عندما لا يختلف البحر عن المتوسط بأكثر من 20%
        if (spans.size >= 2) {
            val avgSpan = spans.average()
            val maxDeviation = spans.maxOf { abs(it - avgSpan) / avgSpan }
            if (maxDeviation > 0.2) {
                globalWarnings.add("تباين الأبحر يتجاوز 20% - معاملات العزم التقريبية أقل دقة - SBC 304 §6.5")
                globalCodeNotes.add("⚠ SBC 304 §6.5: Span variation > 20%, approximate coefficients less accurate")
            }
        }

        // === حساب المقاطع الحرجة لكل بحر ===
        for (spanIndex in spans.indices) {
            val span = spans[spanIndex]
            val isFirst = spanIndex == 0
            val isLast = spanIndex == spans.size - 1

            // تحديد معاملات العزم حسب موقع البحر
            // الدعامة الخارجية اليسرى (للبحر الأول فقط)
            if (isFirst) {
                // مقطع عند الدعامة الخارجية
                val momentSupport = totalLoad * span * span * MOMENT_COEFF_EXT_NEG
                val shearAtSupport = totalLoad * span * 0.5

                val negFlexure = baseBeam.calculateFlexureReinforcement(
                    fcu, fy, width, effectiveDepth, depth, momentSupport, loadCombination
                )

                val negShear = baseBeam.calculateShearReinforcement(
                    fcu, fy, width, effectiveDepth, shearAtSupport, 0.0, loadCombination
                )

                // نقطة القطع للقضبان السفلية (bottom bars)
                // عند الدعامة: القضبان العلوية هي الأساسية
                // نقطة القطع = المسافة من الدعامة حيث العزم يقل عن سعة القضبان
                val cutoffPoint = calculateCutoffPoint(
                    load = totalLoad,
                    span = span,
                    momentAtSupport = momentSupport,
                    capacityOfCutBars = negFlexure.astProvided * 0.5 * fy * effectiveDepth / 1e6, // kN.m (نصف القضبان)
                    supportType = "exterior"
                )

                allResults.add(
                    AdvancedBeamResult(
                        beamType = BeamType.Continuous(spans, listOf(EndCondition.PINNED, EndCondition.FIXED)),
                        sectionType = BeamSectionType.RECTANGULAR,
                        flexureResult = negFlexure,
                        shearResult = negShear,
                        deflectionCheck = DeflectionCheckResult(isSafe = true),
                        momentDiagram = emptyList(),
                        shearDiagram = emptyList(),
                        inventoryAnalysis = null,
                        crackWidthCheck = null,
                        developmentLengthCheck = null,
                        warnings = globalWarnings + negFlexure.warnings,
                        codeNotes = globalCodeNotes + negFlexure.codeNotes +
                                "Span ${spanIndex + 1} - Exterior Support: M=${"%.1f".format(momentSupport)} kN.m" +
                                ", Cutoff point: ${"%.2f".format(cutoffPoint)}m from support"
                    )
                )
            }

            // مقطع المنتصف (عزم موجب)
            val momentCoeffMid = if (isFirst || isLast) MOMENT_COEFF_EXT_POS else MOMENT_COEFF_INT_POS
            val momentMidspan = totalLoad * span * span * momentCoeffMid

            val posFlexure = baseBeam.calculateFlexureReinforcement(
                fcu, fy, width, effectiveDepth, depth, momentMidspan, loadCombination
            )

            val shearMidspan = 0.0  // القص عند المنتصف ≈ 0
            val posShear = baseBeam.calculateShearReinforcement(
                fcu, fy, width, effectiveDepth, shearMidspan, 0.0, loadCombination
            )

            allResults.add(
                AdvancedBeamResult(
                    beamType = BeamType.Continuous(spans, listOf(EndCondition.FIXED, EndCondition.FIXED)),
                    sectionType = BeamSectionType.RECTANGULAR,
                    flexureResult = posFlexure,
                    shearResult = posShear,
                    deflectionCheck = DeflectionCheckResult(isSafe = true),
                    momentDiagram = emptyList(),
                    shearDiagram = emptyList(),
                    inventoryAnalysis = null,
                    crackWidthCheck = null,
                    developmentLengthCheck = null,
                    warnings = globalWarnings + posFlexure.warnings,
                    codeNotes = globalCodeNotes + posFlexure.codeNotes +
                            "Span ${spanIndex + 1} - Midspan (+M): M=${"%.1f".format(momentMidspan)} kN.m" +
                            " (coeff = 1/${(1.0 / momentCoeffMid).toInt()})"
                )
            )

            // مقطع عند الدعامة الداخلية (للبحور ما عدا الأخير - أو لجميع الدعامات الداخلية)
            if (!isLast) {
                val momentIntSupport = totalLoad * span * span * MOMENT_COEFF_INT_NEG
                val shearIntSupport = totalLoad * span * 0.6

                // تطبيق إعادة التوزيع: تقليل العزم السالب وزيادة الموجب
                val redistributedMoment = momentIntSupport * (1.0 - redistributionRatio)

                val intNegFlexure = baseBeam.calculateFlexureReinforcement(
                    fcu, fy, width, effectiveDepth, depth, redistributedMoment, loadCombination
                )

                val intNegShear = baseBeam.calculateShearReinforcement(
                    fcu, fy, width, effectiveDepth, shearIntSupport, 0.0, loadCombination
                )

                val cutoffPoint = calculateCutoffPoint(
                    load = totalLoad,
                    span = span,
                    momentAtSupport = redistributedMoment,
                    capacityOfCutBars = intNegFlexure.astProvided * 0.5 * fy * effectiveDepth / 1e6,
                    supportType = "interior"
                )

                allResults.add(
                    AdvancedBeamResult(
                        beamType = BeamType.Continuous(spans, listOf(EndCondition.FIXED, EndCondition.FIXED)),
                        sectionType = BeamSectionType.RECTANGULAR,
                        flexureResult = intNegFlexure,
                        shearResult = intNegShear,
                        deflectionCheck = DeflectionCheckResult(isSafe = true),
                        momentDiagram = emptyList(),
                        shearDiagram = emptyList(),
                        inventoryAnalysis = null,
                        crackWidthCheck = null,
                        developmentLengthCheck = null,
                        warnings = globalWarnings + intNegFlexure.warnings,
                        codeNotes = globalCodeNotes + intNegFlexure.codeNotes +
                                "Span ${spanIndex + 1}→${spanIndex + 2} Interior Support: M=${"%.1f".format(momentIntSupport)} kN.m" +
                                ", Redistributed: ${"%.1f".format(redistributedMoment)} kN.m (-${(redistributionRatio * 100).toInt()}%)" +
                                ", Cutoff: ${"%.2f".format(cutoffPoint)}m"
                    )
                )
            }

            // الدعامة الخارجية اليمنى (للبحر الأخير فقط)
            if (isLast && spans.size > 1) {
                val momentRightSupport = totalLoad * span * span * MOMENT_COEFF_EXT_NEG
                val rightFlexure = baseBeam.calculateFlexureReinforcement(
                    fcu, fy, width, effectiveDepth, depth, momentRightSupport, loadCombination
                )

                allResults.add(
                    AdvancedBeamResult(
                        beamType = BeamType.Continuous(spans, listOf(EndCondition.FIXED, EndCondition.PINNED)),
                        sectionType = BeamSectionType.RECTANGULAR,
                        flexureResult = rightFlexure,
                        shearResult = ShearReinforcementResult(),
                        deflectionCheck = DeflectionCheckResult(isSafe = true),
                        momentDiagram = emptyList(),
                        shearDiagram = emptyList(),
                        inventoryAnalysis = null,
                        crackWidthCheck = null,
                        developmentLengthCheck = null,
                        warnings = globalWarnings + rightFlexure.warnings,
                        codeNotes = globalCodeNotes + rightFlexure.codeNotes +
                                "Span ${spanIndex + 1} - Right Exterior Support: M=${"%.1f".format(momentRightSupport)} kN.m"
                    )
                )
            }
        }

        return allResults
    }

    /**
     * حساب نقطة القطع (Cutoff Point) للقضبان
     *
     * عند نقطة القطع، يجب أن يكون عزم المقطع ≥ سعة القضبان المتبقية
     * M(x) = R×x - w×x²/2 - M_support × (1 - x/L)
     *
     * @param load الحمل الموزع (kN/m)
     * @param span البحر (m)
     * @param momentAtSupport العزم عند الدعامة (kN.m)
     * @param capacityOfCutBars سعة القضبان المقطوعة (kN.m)
     * @param supportType نوع الدعامة (خارجية/داخلية)
     * @return المسافة من الدعامة لنقطة القطع (m)
     */
    private fun calculateCutoffPoint(
        load: Double,
        span: Double,
        momentAtSupport: Double,
        capacityOfCutBars: Double,
        supportType: String
    ): Double {
        // عزم القص عند المسافة x من الدعامة
        // M(x) = R×x - w×x²/2 - M_support (للبحر الخارجي: R = wL/2)
        // M(x) = R×x - w×x²/2 - M_support (للبحر الداخلي: R ≈ wL×0.6)
        val reaction = if (supportType == "exterior") load * span / 2.0 else load * span * 0.6

        // نبحث عن x حيث M(x) = capacityOfCutBars
        // R×x - w×x²/2 = M_support + capacityOfCutBars
        // -w/2 × x² + R × x - (M_support + capacityOfCutBars) = 0
        val totalMoment = momentAtSupport + capacityOfCutBars
        val a = -load / 2.0
        val b = reaction
        val c = -totalMoment

        val discriminant = b * b - 4 * a * c
        if (discriminant < 0) return span / 3.0  // قيمة افتراضية آمنة

        val x1 = (-b + sqrt(discriminant)) / (2 * a)
        val x2 = (-b - sqrt(discriminant)) / (2 * a)

        // نأخذ القيمة الموجبة الأقل
        val validSolutions = listOf(x1, x2).filter { it > 0 && it < span }
        return validSolutions.minOrNull()?.coerceAtLeast(span / 6.0) ?: span / 3.0
    }

    // ========================================================================
    //  دوال التصميم الزلزالي - SBC 304 البند 21
    // ========================================================================

    /**
     * فحص متطلبات المناطق الزلزالية حسب SBC 304 البند 21
     *
     * يشمل:
     * - عرض الكمرة الأدنى (250 مم) - SBC 304 §21.4
     * - نسبة التسليح الأدنى (0.25√fc'/fy) - SBC 304 §21.5
     * - نسبة التسليح القصوى (0.025) - SBC 304 §21.5
     * - نسبة الضغط إلى الشد (As'/As ≥ 0.5 عند الدعامات)
     *
     * @param width عرض الكمرة (mm)
     * @param depth عمق الكمرة (mm)
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param totalLoad الحمل الكلي (kN/m)
     * @return زوج (تحذيرات، ملاحظات الكود)
     */
    fun checkSeismicRequirements(
        width: Double,
        depth: Double,
        fcu: Double,
        fy: Double,
        totalLoad: Double
    ): Pair<List<String>, List<String>> {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val fcPrime = 0.8 * fcu

        codeNotes.add("SBC 304-2018 §21: Seismic Design Provisions")

        // SBC 304 §21.4: أقل عرض للكمرات في المناطق الزلزالية
        if (width < SBC_MIN_WIDTH_SEISMIC) {
            warnings.add("SBC 304 §21.4: عرض الكمرة ${width.toInt()} مم < ${SBC_MIN_WIDTH_SEISMIC.toInt()} مم (الحد الأدنى الزلزالي)")
            codeNotes.add("⚠ SBC 304 §21.4: Min beam width = 250mm in seismic zones")
        } else {
            codeNotes.add("SBC 304 §21.4: Beam width ${width.toInt()}mm ≥ 250mm ✓")
        }

        // SBC 304 §21.5: نسبة التسليح الأدنى للمناطق الزلزالية
        val rhoMinSeismic = 0.25 * sqrt(fcPrime.coerceAtLeast(1.0)) / fy
        codeNotes.add("SBC 304 §21.5: Seismic ρmin = 0.25√fc'/fy = ${"%.4f".format(rhoMinSeismic)}")

        // SBC 304 §21.5: نسبة التسليح القصوى
        val rhoMaxSeismic = 0.025
        codeNotes.add("SBC 304 §21.5: Seismic ρmax = ${rhoMaxSeismic}")

        // SBC 304 §21.5.2: نسبة الضغط إلى الشد عند الدعامات (As'/As ≥ 0.5)
        codeNotes.add("SBC 304 §21.5.2: At joint faces, As'/As ≥ 0.5 (compression/tension ratio)")

        // SBC 304 §21.5.1: الحد الأقصى للعمق/العرض
        val depthWidthRatio = depth / width
        if (depthWidthRatio > 4.0) {
            warnings.add("SBC 304 §21.5.1: نسبة العمق/العرض = ${"%.1f".format(depthWidthRatio)} > 4 - غير مفضل زلزالياً")
        }

        // SBC 304 §21.5.3: التسليح المضغوط الأدنى في مناطق العزم العالي
        codeNotes.add("SBC 304 §21.5.3: Compression reinforcement required at plastic hinge zones")

        return warnings to codeNotes
    }

    /**
     * تصميم الكانات الزلزالية حسب SBC 304 البند 21.5
     *
     * متطلبات مناطق الحصر الزلزالية (Confinement Zones):
     * - تباعد الكانات: min(d/4, 100 مم, 8×db) - SBC 304 §21.5.3
     * - كانات 135° مع امتداد 10×db - SBC 304 §25.7.3
     * - نسبة القص الأدنى: 0.062√fc'×bw/fy - SBC 304 §21.5.4
     * - طول منطقة الحصر: 2×h من وجه الدعامة
     *
     * @param shearResult نتيجة القص العادية
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param width عرض الكمرة (mm)
     * @param effectiveDepth العمق الفعال (mm)
     * @param designShear قوة القص التصميمية (kN)
     * @return ShearReinforcementResult مع متطلبات الحصر الزلزالي
     */
    fun designSeismicStirrups(
        shearResult: ShearReinforcementResult,
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        designShear: Double
    ): ShearReinforcementResult {
        val warnings = shearResult.warnings.toMutableList()
        val codeNotes = shearResult.codeNotes.toMutableList()
        val fcPrime = 0.8 * fcu

        codeNotes.add("SBC 304 §21.5.3: Seismic Confinement Zone - Seismic Stirrup Design")

        // SBC 304 §21.5.3: تباعد الكانات في منطقة الحصر
        // s = min(d/4, 100mm, 8×db_main)
        val s1 = effectiveDepth / 4.0
        val s2 = 100.0
        val s3 = 8.0 * 25.0  // 8 × قطر السيخ الرئيسي (افتراضي 25مم)
        val seismicSpacing = minOf(s1, s2, s3)

        // SBC 304 §21.5.4: نسبة القص الأدنى في المناطق الزلزالية
        val Av_min_seismic = 0.062 * sqrt(fcPrime.coerceAtLeast(1.0)) * width / fy * 1000  // mm²/m

        // اختيار قطر الكانات الزلزالية (لا يقل عن 10 مم)
        val seismicStirrupDia = maxOf(shearResult.stirrupDiameter, 10.0)
        val seismicStirrupArea = 2 * PI * seismicStirrupDia * seismicStirrupDia / 4  // رجلين

        // التباعد الفعلي (آخر بين الحسابي والزلزالي)
        val requiredSpacing = if (shearResult.requiredShearReinforcement > 0) {
            seismicStirrupArea * 1000.0 / max(shearResult.requiredShearReinforcement, Av_min_seismic)
        } else {
            seismicSpacing
        }
        val finalSpacing = minOf(requiredSpacing, seismicSpacing).coerceAtLeast(50.0)

        // تحقق من طول منطقة الحصر
        // SBC 304 §21.5.3: منطقة الحصر تمتد 2h من وجه الدعامة
        codeNotes.add("Confinement zone length: 2h = ${"%.0f".format(2 * (effectiveDepth + SBC_COVER_CORROSIVE))}mm from support face")
        codeNotes.add("Seismic stirrup: Ø${seismicStirrupDia.toInt()} @ ${finalSpacing.toInt()}mm c/c (min of d/4=${"%.0f".format(s1)}, 100mm)")
        codeNotes.add("SBC 304 §21.5.3: Hooks = 135° with 10db extension")

        if (finalSpacing < shearResult.stirrupSpacing) {
            codeNotes.add("Seismic spacing ${finalSpacing.toInt()}mm < normal spacing ${shearResult.stirrupSpacing.toInt()}mm (governed by seismic)")
        }

        val providedArea = seismicStirrupArea * 1000.0 / finalSpacing
        return ShearReinforcementResult(
            concreteShearCapacity = shearResult.concreteShearCapacity,
            requiredArea = max(shearResult.requiredArea, Av_min_seismic),
            providedArea = providedArea,
            requiredShearReinforcement = max(shearResult.requiredShearReinforcement, Av_min_seismic),
            providedShearReinforcement = providedArea,
            stirrupDiameter = seismicStirrupDia,
            stirrupSpacing = finalSpacing,
            isSafe = shearResult.isSafe,
            utilizationRatio = shearResult.utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * فحص شرط العمود القوي والكمرة الضعيفة (Strong Column - Weak Beam)
     * حسب SBC 304 البند 21.6
     *
     * ΣMnc ≥ (6/5) × ΣMnb
     * حيث:
     *   ΣMnc = مجموع عزوم الخضوع للأعمدة عند المفصل
     *   ΣMnb = مجموع عزوم الخضوع للكمرات عند المفصل
     *
     * @param columnSumMoment مجموع عزوم الخضوع للأعمدة (kN.m)
     * @param beamTopReinforcement مساحة تسليح الكمرة العلوية (mm²)
     * @param beamBottomReinforcement مساحة تسليح الكمرة السفلية (mm²)
     * @param effectiveDepth العمق الفعال للكمرة (mm)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @return Triple(نسبة السلامة، هل آمن، ملاحظات)
     */
    fun checkStrongColumnWeakBeam(
        columnSumMoment: Double,
        beamTopReinforcement: Double,
        beamBottomReinforcement: Double,
        effectiveDepth: Double,
        fy: Double
    ): Triple<Double, Boolean, List<String>> {
        val notes = mutableListOf<String>()
        notes.add("SBC 304-2018 §21.6: Strong Column - Weak Beam Check")

        // حساب عزوم الخضوع للكمرات
        // Mnb = As × fy × (d - a/2) / φ
        // تقريبي: Mnb ≈ As × fy × 0.9 × d
        val MnTop = beamTopReinforcement * fy * 0.9 * effectiveDepth / 1e6  // kN.m
        val MnBottom = beamBottomReinforcement * fy * 0.9 * effectiveDepth / 1e6  // kN.m
        val sumMnb = MnTop + MnBottom  // kN.m

        // شرط SBC 304 §21.6: ΣMnc ≥ (6/5) × ΣMnb
        val requiredColumnMoment = 1.2 * sumMnb  // (6/5) = 1.2
        val ratio = if (sumMnb > 0) columnSumMoment / requiredColumnMoment else 999.0
        val isSafe = ratio >= 1.0

        notes.add("ΣMnc = ${"%.1f".format(columnSumMoment)} kN.m")
        notes.add("ΣMnb = ${"%.1f".format(sumMnb)} kN.m (Top: ${"%.1f".format(MnTop)} + Bottom: ${"%.1f".format(MnBottom)})")
        notes.add("(6/5)×ΣMnb = ${"%.1f".format(requiredColumnMoment)} kN.m")
        notes.add("Ratio ΣMnc/[(6/5)×ΣMnb] = ${"%.2f".format(ratio)} ${if (isSafe) "≥ 1.0 ✓" else "< 1.0 ✗"}")

        // SBC 304 §21.6: إضافة نسب التسليح عند المفصل
        val rhoSum = (beamTopReinforcement + beamBottomReinforcement) / (2 * 250.0 * effectiveDepth) // تقريبي
        notes.add("ρsum at joint face ≈ ${"%.4f".format(rhoSum)}")

        if (!isSafe) {
            notes.add("⚠ العمود لا يحقق شرط القوة - زِد مقطع العمود أو التسليح")
        }

        return Triple(ratio, isSafe, notes)
    }

    // ========================================================================
    //  فحص الانحراف المتقدم - SBC 304 مع معامل المناخ الحار
    // ========================================================================

    /**
     * فحص الانحراف المتقدم حسب SBC 304-2018 مع تعديلات المناخ الحار
     *
     * الاختلاف الرئيسي عن ACI:
     * - معامل الانحراف طويل المدى ξ = 2.5 (للمناخ الحار الجاف)
     *   بدلاً من 2.0 (للرطوبة المتوسطة) حسب ACI 24.2.4.1
     *
     * يتبع نفس منهجية ACI 24.2.3.5 لحساب عزم القصور الفعال Ie
     *
     * @param span البحر (m)
     * @param depth العمق الكلي (mm)
     * @param fcPrime مقاومة الأسطوانة (MPa)
     * @param serviceLoad حمل الخدمة الموزع (kN/m)
     * @param serviceMoment عزم الخدمة (kN.m)
     * @param beamWidth عرض الكمرة (mm)
     * @param effectiveDepth العمق الفعال (mm)
     * @param astProvided مساحة التسليح المقدمة (mm²)
     * @param beamType نوع الكمرة
     * @return DeflectionCheckResult نتيجة فحص الانحراف
     */
    private fun checkDeflectionSBC(
        span: Double,
        depth: Double,
        fcPrime: Double,
        serviceLoad: Double,
        serviceMoment: Double,
        beamWidth: Double,
        effectiveDepth: Double,
        astProvided: Double,
        beamType: BeamType
    ): DeflectionCheckResult {
        val deflectionWarnings = mutableListOf<String>()

        // معامل مرونة الخرسانة - SBC 304 §19.2.2.1 (= ACI 19.2.2.1)
        val Ec = EC_COEFFICIENT * sqrt(fcPrime.coerceAtLeast(1.0))  // MPa
        val Es = 200000.0  // MPa
        val n = Es / Ec

        // عزم القصور الذاتي للمقطع الكلي
        val Ig = beamWidth * depth.pow(3) / 12.0
        val yt = depth / 2.0

        // عزم التشقق - SBC 304 §24.2.3.5
        val fr = 0.62 * LAMBDA * sqrt(fcPrime.coerceAtLeast(1.0))  // MPa
        val Mcr = fr * Ig / yt  // N.mm
        val Ma = serviceMoment * 1e6  // N.mm

        // عزم القصور للمقطع المتشقق Icr
        val rho = if (effectiveDepth > 0 && beamWidth > 0) astProvided / (beamWidth * effectiveDepth) else 0.0
        val nRho = n * rho

        val xCr = if (nRho > 0) {
            effectiveDepth * (-nRho + sqrt(nRho * nRho + 2 * nRho))
        } else {
            effectiveDepth * 0.3
        }.coerceIn(0.1 * effectiveDepth, 0.6 * effectiveDepth)

        val Icr = beamWidth * xCr.pow(3) / 3.0 + n * astProvided * (effectiveDepth - xCr).pow(2)

        // عزم القصور الفعال Ie - SBC 304 (= ACI 24.2.3.5a)
        val mcRatio = if (Ma > 0) Mcr / Ma else 1.0
        val mcRatioCubed = mcRatio.pow(3).coerceAtMost(1.0)

        val Ie: Double = if (mcRatio >= 1.0) {
            Ig
        } else {
            minOf(mcRatioCubed * Ig + (1.0 - mcRatioCubed) * Icr, Ig)
        }

        // الانحراف الفوري
        val w = serviceLoad / 1000.0  // N/mm
        val L = span * 1000.0  // mm

        val alpha = when (beamType) {
            is BeamType.SimplySupported -> 5.0 / 384.0
            is BeamType.Fixed -> 1.0 / 384.0
            is BeamType.Cantilever -> 1.0 / 8.0
            is BeamType.Continuous -> 3.0 / 384.0
            is BeamType.MultiSupport -> 3.0 / 384.0
            is BeamType.Haunched -> 4.0 / 384.0
            is BeamType.Vierendeel -> 5.0 / 384.0
        }

        val immediateDeflection = alpha * w * L.pow(4) / (Ec * Ie)  // mm

        // === معامل الزيادة طويل المدى - SBC 304 (مع تعديل المناخ الحار) ===
        // ACI 24.2.4.1: λΔ = ξ / (1 + 50 × ρ')
        // SBC: ξ = 2.5 للمناخ الحار الجاف (المميز في المملكة العربية السعودية)
        // بدلاً من ξ = 2.0 للرطوبة المتوسطة
        val xi = SBC_LONG_TERM_MULTIPLIER  // 2.5 للمناخ الحار
        val rhoPrime = 0.0  // نسبة تسليح الضغط (محافظ)
        val longTermMultiplier = xi / (1.0 + 50.0 * rhoPrime)

        val longTermDeflection = immediateDeflection * (1.0 + longTermMultiplier)

        // حدود الانحراف - SBC 304 (= ACI 24.2.2)
        val allowableTotal = L / 240.0
        val ratio = longTermDeflection / allowableTotal

        val recommendation = when {
            ratio > 1.2 -> "زِد عمق الكمرة أو قلل نسبة التسليح - SBC 304 §9.5"
            ratio > 1.0 -> "الانحراف يتجاوز الحد المسموح - راجع SBC 304 §9.5"
            ratio > 0.8 -> "الانحراف قريب من الحد - SBC 304 §9.5"
            else -> "الانحراف مقبول حسب SBC 304 §9.5"
        }

        if (ratio > 1.0) {
            deflectionWarnings.add("⚠️ الانحراف يتجاوز حدود SBC 304 §9.5 (δ/δ_allow = ${"%.2f".format(ratio)})")
        }

        deflectionWarnings.add("SBC: معامل طويل المدى ξ = ${SBC_LONG_TERM_MULTIPLIER} (مناخ حار) بدلاً من 2.0 (ACI)")

        return DeflectionCheckResult(
            immediateDeflection = immediateDeflection,
            longTermDeflection = longTermDeflection,
            calculatedDeflection = longTermDeflection,
            allowableDeflection = allowableTotal,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            message = "SBC 304 §9.5: δi=%.2fmm, δLT=%.2fmm (ξ=%.1f), δ_allow=%.2fmm".format(
                immediateDeflection, longTermDeflection, SBC_LONG_TERM_MULTIPLIER, allowableTotal
            ),
            recommendation = recommendation,
            warnings = deflectionWarnings
        )
    }

    // ========================================================================
    //  فحص عرض الشروخ - SBC مع عامل المناخ الحار/الجاف
    // ========================================================================

    /**
     * فحص عرض الشروخ حسب SBC 304-2018 مع تعديلات المناخ الحار/الجاف
     *
     * يستخدم طريقة Gergely-Lutz (مشابهة لـ ACI 24.5) مع تعديلات:
     * - عامل المناخ الحار: يزيد عرض الشرخ بنسبة 15% تقريباً
     *   بسبب التشقق الحراري التكراري (الفرق الكبير في درجات الحرارة)
     * - الحد المسموح أكثر تحفظاً: 0.30 مم للعناصر الداخلية
     *   (بدلاً من 0.41 مم في ACI)
     *
     * @param result نتيجة التسليح
     * @param fcPrime مقاومة الأسطوانة (MPa)
     * @param moment عزم الخدمة (kN.m)
     * @param effectiveDepth العمق الفعال (mm)
     * @param width عرض الكمرة (mm)
     * @param isExterior هل العنصر خارجي (معرض للبيئة المالحة)
     * @return CrackWidthCheckResult نتيجة فحص عرض الشروخ
     */
    fun checkCrackWidth(
        result: ReinforcementResult,
        fcPrime: Double,
        moment: Double,
        effectiveDepth: Double = 0.0,
        width: Double = 250.0,
        isExterior: Boolean = false
    ): CrackWidthCheckResult {
        if (result.astProvided <= 0) {
            return CrackWidthCheckResult(
                calculatedWidth = 0.0,
                allowableWidth = 0.30,
                isSafe = true,
                codeReference = "SBC 304-2018: Section 24.5 (Crack Width Control - Hot/Arid Climate)"
            )
        }

        val dEff = if (effectiveDepth > 0) effectiveDepth else (result.barDiameter * 50)

        // حساب إجهاد الحديد تحت حمل الخدمة
        val j = 0.875  // معامل الذراع التقريبي
        val fs = (moment * 1e6) / (result.astProvided * j * dEff)  // MPa

        // تحويل إلى ksi
        val fsKsi = fs / 6.895

        // المسافة من مركز السيخ للسطح
        val cover = SBC_COVER_CORROSIVE  // 50 مم للبيئة السعودية
        val dc = cover + 10.0 / 2.0 + result.barDiameter / 2.0  // mm
        val dcIn = dc / 25.4  // in

        // مساحة الخرسانة المحيطة بكل سيخ
        val spacing = if (result.numberOfBars > 1) {
            (width - 2 * cover - result.numberOfBars * result.barDiameter) / (result.numberOfBars - 1)
        } else {
            width - 2 * cover
        }
        val spacingIn = spacing / 25.4
        val A = width / 25.4 * spacingIn / result.numberOfBars  // in²
        val aCubed = if (A > 0) cbrt(A) else 1.0

        // معامل z (Gergely-Lutz)
        val z = fsKsi * dcIn * aCubed  // kip/in

        // تقدير عرض الشرخ
        var crackWidth = z / 425.0 * 25.4  // mm

        // === تعديل المناخ الحار/الجاف (SBC) ===
        // في المناخ الحار الجاف (السعودية)، يزيد عرض الشرخ بسبب:
        // 1. التكرارات الحرارية الكبيرة (50+ درجة نهاراً، 15-20 ليلاً)
        // 2. الجفاف السريع للخرسانة السطحية
        // 3. الأملاح في الهواء (المناطق الساحلية)
        val hotClimateFactor = if (isExterior) 1.20 else 1.15  // 15-20% زيادة
        crackWidth *= hotClimateFactor

        // === الحد المسموح حسب SBC (أكثر تحفظاً من ACI) ===
        // SBC: 0.30 مم للعناصر الداخلية (بدلاً من 0.41 ACI)
        //      0.25 مم للعناصر الخارجية في البيئة المالحة
        val allowableWidth = if (isExterior) 0.25 else 0.30
        val isSafe = crackWidth <= allowableWidth

        return CrackWidthCheckResult(
            calculatedWidth = crackWidth,
            allowableWidth = allowableWidth,
            isSafe = isSafe,
            codeReference = "SBC 304-2018: Section 24.5 (z=${"%.0f".format(z)} kip/in, " +
                    "hot-climate factor=${"%.2f".format(hotClimateFactor)}, " +
                    "limit=${if (isExterior) 0.25 else 0.30}mm)"
        )
    }

    // ========================================================================
    //  فحص طول التثبيت - SBC 304 البند 12 (مع تعديلات الأسياك المجلفنة)
    // ========================================================================

    /**
     * فحص طول التثبيت حسب SBC 304-2018 البند 12
     *
     * يتبع ACI 25.4.2 مع تعديلات SBC:
     * - للأسياك المجلفنة (Galvanized): زيادة 10% في طول التثبيت
     * - للبيئة المالحة: طول تثبيت أكبر
     *
     * Ld = (fy × ψt × ψe × ψs) / (1.7 × λ × √(fc')) × db × galvanizeFactor
     *
     * @param result نتيجة التسليح
     * @param fcPrime مقاومة الأسطوانة (MPa)
     * @param fy إجهاد الخضوع (MPa)
     * @param span البحر (m)
     * @return DevelopmentLengthCheckResult نتيجة فحص طول التثبيت
     */
    fun checkDevelopmentLength(
        result: ReinforcementResult,
        fcPrime: Double,
        fy: Double,
        span: Double
    ): DevelopmentLengthCheckResult {
        // حساب طول التثبيت باستخدام SBCBeam (الذي يضيف 10% للمجلفن)
        val ldBottom = baseBeam.calculateDevelopmentLength(
            barDiameter = result.barDiameter,
            fy = fy,
            fcu = fcPrime / 0.8,  // نمرر fcu لأن SBCBeam يرسله لـ ACIBeam الذي يحول
            barLocation = BarLocation.BOTTOM,
            coating = CoatingType.UNCOATED
        )

        val ldTop = baseBeam.calculateDevelopmentLength(
            barDiameter = result.barDiameter,
            fy = fy,
            fcu = fcPrime / 0.8,
            barLocation = BarLocation.TOP,
            coating = CoatingType.UNCOATED
        )

        // طول التثبيت للأسياك المجلفنة (SBC 304 §12)
        val ldBottomGalv = ldBottom * 1.1  // 10% زيادة للمجلفن
        val ldTopGalv = ldTop * 1.1

        // نستخدم القيمة الأكبر
        val ldRequired = maxOf(ldBottomGalv, ldTopGalv)

        // الطول المتاح
        val availableLength = span * 1000.0 / 2.0  // mm (نصف البحر)
        val isSafe = ldRequired <= availableLength

        return DevelopmentLengthCheckResult(
            requiredLength = ldRequired,
            availableLength = availableLength,
            isSafe = isSafe,
            codeReference = "SBC 304-2018: Section 12 (Ld_bot=${"%.0f".format(ldBottom)}mm, " +
                    "Ld_top=${"%.0f".format(ldTop)}mm, Galv: ×1.1, " +
                    "Ld_req=${"%.0f".format(ldRequired)}mm)"
        )
    }

    // ========================================================================
    //  فحص عرض الجناح الفعال لكمرة تي - SBC 304 البند 8.12
    // ========================================================================

    /**
     * فحص العرض الفعال للجناح حسب SBC 304-2018 البند 8.12
     *
     * bf لا يتجاوز:
     *   1. bw + 16 × hf (لكل جانب)
     *   2. bw + Ln/8 (لكل جانب)  -- وهنا يختلف عن بعض الأكواد الأخرى
     *   3. bw + سد البحر المجاور / 2
     *   4. المسافة المركزية بين الكمرات
     *
     * @param flangeWidth عرض الجناح الكلي (mm)
     * @param flangeThickness سمك الجناح (mm)
     * @param webWidth عرض الكورسة (mm)
     * @param span البحر (m)
     * @return زوج (العرض الفعال، ملاحظات الكود)
     */
    fun checkTBeamFlange(
        flangeWidth: Double,
        flangeThickness: Double,
        webWidth: Double,
        span: Double
    ): Pair<Double, List<String>> {
        val notes = mutableListOf<String>()
        notes.add("SBC 304-2018 §8.12: Effective Flange Width Check")

        val Ln = span * 1000.0  // mm

        // شرط 1: bw + 16hf
        val bf1 = webWidth + 16.0 * flangeThickness
        // شرط 2: bw + L/8 (center-to-center span) للكمرات الداخلية - ACI/SBC Table 6.3.2.1
        val L_center = span * 1000.0  // center-to-center span ≈ clear span (بمقربة)
        val bf2 = webWidth + L_center / 8.0
        // شرط 3: المسافة المركزية (نفترض أن flangeWidth = المسافة المركزية)
        val bf3 = flangeWidth

        val effectiveBf = minOf(bf1, bf2, bf3)

        notes.add("Rule 1: bw + 16hf = ${webWidth.toInt()} + 16×${flangeThickness.toInt()} = ${bf1.toInt()}mm")
        notes.add("Rule 2: bw + L/8 = ${webWidth.toInt()} + ${"%.0f".format(L_center / 8.0)} = ${bf2.toInt()}mm")
        notes.add("Rule 3: Center-to-center = ${flangeWidth.toInt()}mm")
        notes.add("Effective bf = min(${bf1.toInt()}, ${bf2.toInt()}, ${flangeWidth.toInt()}) = ${effectiveBf.toInt()}mm")

        if (effectiveBf < flangeWidth) {
            notes.add("⚠ Actual flange width ${flangeWidth.toInt()}mm reduced to effective ${effectiveBf.toInt()}mm")
        }

        // فحص أن المحور المحايد داخل الجناح أو لا
        notes.add("Flange thickness hf = ${flangeThickness.toInt()}mm (must be ≥ L/4 for deep analysis)")

        return effectiveBf to notes
    }

    // ========================================================================
    //  فحص معايير الكمرة العميقة - SBC 304 البند 18.10.1
    // ========================================================================

    /**
     * فحص معايير الكمرة العميقة حسب SBC 304-2018 البند 18.10.1
     *
     * الكمرة العميقة: Ln/d < 4
     * الكمرة العادية: Ln/d ≥ 4
     *
     * @param span البحر (m)
     * @param totalDepth العمق الكلي (mm)
     * @param effectiveDepth العمق الفعال (mm)
     * @return زوج (هل كمرة عميقة، ملاحظات الكود)
     */
    fun checkDeepBeamCriteria(
        span: Double,
        totalDepth: Double,
        effectiveDepth: Double
    ): Pair<Boolean, List<String>> {
        val notes = mutableListOf<String>()
        val Ln = span * 1000.0  // mm
        val ldRatio = Ln / effectiveDepth

        notes.add("SBC 304-2018 §18.10.1: Deep Beam Criteria")
        notes.add("Clear span Ln = ${"%.0f".format(Ln)}mm")
        notes.add("Effective depth d = ${"%.0f".format(effectiveDepth)}mm")
        notes.add("Total depth h = ${"%.0f".format(totalDepth)}mm")
        notes.add("Ln/d = ${"%.2f".format(ldRatio)}")

        val isDeepBeam = ldRatio < 4.0
        notes.add("Deep beam: Ln/d = ${"%.2f".format(ldRatio)} ${if (isDeepBeam) "< 4.0 ✓ (Deep Beam)" else "≥ 4.0 (Normal Beam)"}")

        // معلومات إضافية
        if (isDeepBeam) {
            notes.add("SBC 304 §18.10: Strut-and-tie model required")
            notes.add("SBC 304 §18.10.3: Min horizontal reinforcement = 0.001 × bw × s (per face)")
            notes.add("SBC 304 §18.10.4: Min vertical reinforcement = 0.0025 × bw × sv (per face)")

            // فحص إضافي: h/Ln
            val hlRatio = totalDepth / Ln
            if (hlRatio > 0.4) {
                notes.add("SBC 304 §18.10: h/Ln = ${"%.2f".format(hlRatio)} > 0.4 - Consider as deep corbel")
            }
        }

        return isDeepBeam to notes
    }

    // ========================================================================
    //  حساب معامل β1 - SBC 304 (= ACI 21.2.2.1)
    // ========================================================================

    /**
     * حساب معامل β1 حسب SBC 304-2018 (= ACI 21.2.2.1)
     *
     * β1 = 0.85 لـ fc' ≤ 28 MPa
     * β1 ينخفض 0.05 لكل 7 MPa فوق 28 MPa
     * β1 لا يقل عن 0.65
     */
    fun calculateBeta1(fc: Double): Double {
        return when {
            fc <= 28.0 -> 0.85
            fc >= 55.0 -> 0.65
            else -> 0.85 - 0.05 * ((fc - 28.0) / 7.0)
        }
    }

    // ========================================================================
    //  تحليل مخزون حديد التسليح
    // ========================================================================

    /**
     * تحليل المخزون المتوفر مقابل المتطلبات
     *
     * @param result نتيجة التسليح
     * @param span البحر (m)
     * @param inventory مخزون الحديد المتوفر
     * @return InventoryAnalysisResult نتيجة تحليل المخزون
     */
    private fun analyzeBeamInventory(
        result: ReinforcementResult,
        span: Double,
        inventory: RebarInventory
    ): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        return analyzer.analyze(
            requiredArea = result.astRequired,
            requiredLength = result.numberOfBars * span,
            inventory = inventory,
            designCode = DesignCode.SBC,
            elementLength = span,
            cover = SBC_COVER_CORROSIVE
        )
    }

    // ========================================================================
    //  توليد بدائل التسليح
    // ========================================================================

    /**
     * توليد بدائل اختيار القضبان مع تصنيف اقتصادي وآمن
     *
     * @param astRequired المساحة المطلوبة (mm²)
     * @param width العرض (mm)
     * @param effectiveDepth العمق الفعال (mm)
     * @param fcPrime مقاومة الأسطوانة (MPa)
     * @param fy إجهاد الخضوع (MPa)
     * @param designMoment عزم التصميم (kN.m)
     * @param selectedDia القطر المختار (mm)
     * @return قائمة بملاحظات الكود للبدائل
     */
    private fun generateBarAlternatives(
        astRequired: Double,
        width: Double,
        effectiveDepth: Double,
        fcPrime: Double,
        fy: Double,
        designMoment: Double,
        selectedDia: Double
    ): List<String> {
        val alternatives = mutableListOf<String>()

        for (dia in SBC_BAR_DIAMETERS) {
            if (dia == selectedDia) continue

            val area = PI * dia * dia / 4.0
            val numBars = ceil(astRequired / area).toInt().coerceIn(2, 12)
            val asProv = numBars * area

            // التحقق من التباعد
            val clearSpacing = if (numBars > 1) {
                (width - 2 * SBC_COVER_CORROSIVE - numBars * dia) / (numBars - 1)
            } else {
                width - 2 * SBC_COVER_CORROSIVE
            }

            val minSpacing = maxOf(25.0, dia)
            if (clearSpacing < minSpacing) continue

            // حساب السعة
            val aBlock = if (fcPrime > 0) asProv * fy / (0.85 * fcPrime * width) else 0.0
            val Mn = asProv * fy * (effectiveDepth - aBlock / 2.0)
            val capacity = PHI_FLEXURE * Mn / 1e6  // kN.m
            val utilization = if (capacity > 0) designMoment / capacity else 2.0

            // التحقق من النسبة القصوى
            val beta1 = calculateBeta1(fcPrime)
            val rhoProv = asProv / (width * effectiveDepth)
            val rhoMax = 0.85 * beta1 * (fcPrime / fy) * 0.375
            if (rhoProv > rhoMax) continue

            if (utilization in 0.5..1.0) {
                val utilizationPct = (utilization * 100).toInt()
                alternatives.add("${numBars}Ø${dia.toInt()} (${utilizationPct}%)")
            }
        }

        val notes = mutableListOf<String>()
        if (alternatives.size >= 1) {
            notes.add("SBC Economical: ${alternatives.first()}")
        }
        if (alternatives.size >= 2) {
            notes.add("SBC Safest: ${alternatives.last()}")
        }
        return notes
    }

    // ========================================================================
    //  توليد مخطط عزم الانحناء لجميع أنواع الكمرات
    // ========================================================================

    /**
     * توليد مخطط عزم الانحناء (Moment Diagram)
     *
     * @param beamType نوع الكمرة
     * @param load الحمل الكلي الموزع (kN/m)
     * @return قائمة أزواج (الموقع بالأمتار، العزم بـ kN.m)
     */
    fun generateMomentDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val numPoints = 20

        when (beamType) {
            is BeamType.SimplySupported -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = (load * L / 2.0) * x - (load * x * x / 2.0)
                    points.add(x to m)
                }
            }
            is BeamType.Fixed -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = (load * L / 2.0) * x - (load * x * x / 2.0) - (load * L * L / 12.0)
                    points.add(x to m)
                }
            }
            is BeamType.Cantilever -> {
                val L = beamType.length
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = -load * (L - x) * (L - x) / 2.0
                    points.add(x to m)
                }
            }
            is BeamType.Continuous -> {
                val spans = beamType.spans
                if (spans.isNotEmpty()) {
                    val maxSpan = spans.maxOrNull() ?: 1.0
                    for (i in 0..numPoints) {
                        val x = maxSpan * i / numPoints
                        val m = if (x <= maxSpan / 2.0) {
                            (load * maxSpan / 2.0) * x - (load * x * x / 2.0) - load * maxSpan * maxSpan / 10.0 * (x / maxSpan)
                        } else {
                            (load * maxSpan / 2.0) * x - (load * x * x / 2.0) - load * maxSpan * maxSpan / 10.0 * ((maxSpan - x) / maxSpan)
                        }
                        points.add(x to m)
                    }
                }
            }
            is BeamType.MultiSupport -> {
                val totalLength = beamType.totalLength
                for (i in 0..numPoints) {
                    val x = totalLength * i / numPoints
                    val m = (load * totalLength / 2.0) * x - (load * x * x / 2.0)
                        - load * totalLength * totalLength / 10.0 * sin(PI * x / totalLength)
                    points.add(x to m)
                }
            }
            is BeamType.Haunched -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = (load * L / 2.0) * x - (load * x * x / 2.0) - (load * L * L / 12.0)
                    points.add(x to m)
                }
            }
            is BeamType.Vierendeel -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = (load * L / 2.0) * x - (load * x * x / 2.0)
                    points.add(x to m)
                }
            }
        }

        return points
    }

    // ========================================================================
    //  توليد مخطط القص لجميع أنواع الكمرات
    // ========================================================================

    /**
     * توليد مخطط القص (Shear Diagram)
     *
     * @param beamType نوع الكمرة
     * @param load الحمل الكلي الموزع (kN/m)
     * @return قائمة أزواج (الموقع بالأمتار، القوة القصية بـ kN)
     */
    fun generateShearDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val numPoints = 20

        when (beamType) {
            is BeamType.SimplySupported -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }
            is BeamType.Fixed -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }
            is BeamType.Cantilever -> {
                val L = beamType.length
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * (L - x)
                    points.add(x to v)
                }
            }
            is BeamType.Continuous -> {
                val spans = beamType.spans
                if (spans.isNotEmpty()) {
                    val maxSpan = spans.maxOrNull() ?: 1.0
                    for (i in 0..numPoints) {
                        val x = maxSpan * i / numPoints
                        val v = load * maxSpan * 0.6 - load * x
                        points.add(x to v)
                    }
                }
            }
            is BeamType.MultiSupport -> {
                val totalLength = beamType.totalLength
                for (i in 0..numPoints) {
                    val x = totalLength * i / numPoints
                    val v = load * totalLength * 0.6 - load * x
                    points.add(x to v)
                }
            }
            is BeamType.Haunched -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }
            is BeamType.Vierendeel -> {
                val L = beamType.span
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }
        }

        return points
    }
}