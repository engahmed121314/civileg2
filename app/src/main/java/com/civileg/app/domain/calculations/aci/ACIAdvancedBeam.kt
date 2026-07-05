package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

/**
 * تصميم متقدم للكمرات بجميع أنواعها حسب الكود الأمريكي ACI 318-19
 *
 * يغطي هذا الفصل:
 * - تصميم الانحناء (Flexure) حسب ACI 318-19 الفصل التاسع
 * - تصميم القص (Shear) حسب ACI 318-19 الفصل الثاني والعشرين
 * - التحقق من الانحراف (Deflection) حسب ACI 318-19 القسم 24.2
 * - التحقق من عرض الشروخ (Crack Width) حسب ACI 318-19 القسم 24.5
 * - حساب طول التثبيت (Development Length) حسب ACI 318-19 القسم 25.4.2
 * - مخططات عزم الانحناء والقص لجميع أنواع الكمرات
 * - تحليل المخزون (Inventory Analysis) مع البدائل الاقتصادية والآمنة
 */
class ACIAdvancedBeam {

    companion object {
        // ثوابت الكود الأمريكي ACI 318-19
        private const val PHI_FLEXURE = 0.9       // معامل الاختزال للانحناء - ACI 21.2.1
        private const val PHI_SHEAR = 0.75        // معامل الاختزال للقص - ACI 21.2.1
        private const val LAMBDA = 1.0            // عامل الوزن للخرسانة العادية - ACI 19.2.4
        private const val EPSILON_CU = 0.003      // إجهاد الخرسانة الأقصى عند التشقق - ACI 22.2.2.1
        private const val EPSILON_T_MIN = 0.005   // الحد الأدنى لإجهاد الشد للحالة المقبولة - ACI 21.2.2
        private const val EC = 4700.0             // معامل مرونة الخرسانة (مضروب في الجذر) - ACI 19.2.2.1

        // أقطار الأسياخ المتاحة حسب ACI (النظام الأمريكي: No.4 إلى No.10)
        private val ACI_BAR_DIAMETERS = listOf(10.0, 12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0, 36.0)

        // الغطاء الخرساني الافتراضي - ACI 20.6.1
        private const val DEFAULT_COVER = 40.0  // mm
    }

    private val baseBeam = ACIBeam()

    /**
     * تصميم متقدم للكمرة حسب ACI 318-19
     *
     * @param beamType نوع الكمرة (بسيطة، ثابتة، كابولي، مستمرة، متعددة الدعامات، متغيرة المقطع، فيرينديل)
     * @param sectionType نوع المقطع (مستطيل، تي، إل، دائري، مركب)
     * @param fcu مقاومة الخرسانة للمكعب (MPa) - يتم تحويلها داخلياً إلى مقاومة الأسطوانة fc'
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
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // === التحويل من مقاومة المكعب إلى مقاومة الأسطوانة ===
        // ACI 318-19 لا يعتمد مقاومة المكعب مباشرة، لكن في الدول العربية
        // تُستخدم مقاومة المكعب fcu، لذا نحولها:
        // fc' = 0.8 × fcu (العلاقة التقريبية المعتمدة)
        val fcPrime = 0.8 * fcu

        codeNotes.add("fc' = 0.8 × fcu = 0.8 × $fcu = $fcPrime MPa (Cube to Cylinder conversion)")
        codeNotes.add("Load Combination: ${loadCombination.description}")

        // === حساب معاملات التحميل حسب ACI 318-19 القسم 5.3 ===
        val totalFactor = loadCombination.getFactorForCode(DesignCode.ACI)
        // ACI 318-19: 1.2D + 1.6L (معامل موحد تقريبي)
        val gammaD = 1.2
        val gammaL = 1.6
        val totalLoad = (deadLoad + liveLoad) * totalFactor  // kN/m

        codeNotes.add("ACI 318-19 §5.3: Wu = ${gammaD}D + ${gammaL}L = ${gammaD}×$deadLoad + ${gammaL}×$liveLoad = ${"%.2f".format(totalLoad)} kN/m")

        // === حساب القوى القصوى بناءً على نوع الكمرة ===
        val maxMoment = beamType.getMaxMoment(totalLoad)  // kN.m
        val maxShear = beamType.getMaxShear(totalLoad)    // kN

        codeNotes.add("Max Moment: ${"%.2f".format(maxMoment)} kN.m")
        codeNotes.add("Max Shear: ${"%.2f".format(maxShear)} kN")

        // === تحديد العمق الفعال ===
        // d = h - cover - stirrup_dia/2 - main_bar_dia/2 (تقريبي)
        val effectiveDepth = depth - DEFAULT_COVER - 10.0 - 12.0  // mm (خصم الغطاء والكانة ونصف السيخ)

        // === تصميم الانحناء (Flexure Design) - ACI 318-19 الفصل التاسع ===
        val flexureResult = baseBeam.calculateFlexureReinforcement(
            fcu, fy, width, effectiveDepth, depth, maxMoment, loadCombination
        )

        // === تصميم القص (Shear Design) - ACI 318-19 الفصل الثاني والعشرين ===
        val shearResult = baseBeam.calculateShearReinforcement(
            fcu, fy, width, effectiveDepth, maxShear, 0.0, loadCombination
        )

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

        // === التحقق من الانحراف (Deflection Check) - ACI 318-19 القسم 24.2 ===
        // تحميل الخدمة (Service Load) للتحقق من الانحراف
        val serviceLoad = deadLoad + liveLoad  // kN/m (بدون معاملات التحميل)
        val serviceMoment = when (beamType) {
            is BeamType.SimplySupported -> serviceLoad * beamType.span * beamType.span / 8
            is BeamType.Fixed -> serviceLoad * beamType.span * beamType.span / 12
            is BeamType.Cantilever -> serviceLoad * beamType.length * beamType.length / 2
            is BeamType.Continuous -> serviceLoad * (beamType.spans.maxOrNull() ?: 1.0).pow(2) / 10
            is BeamType.MultiSupport -> serviceLoad * beamType.totalLength * beamType.totalLength / 10
            is BeamType.Haunched -> serviceLoad * beamType.span * beamType.span / 10
            is BeamType.Vierendeel -> serviceLoad * beamType.span * beamType.span / 8
        }

        val deflectionCheck = checkDeflectionACI(
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

        // === التحقق من عرض الشروخ (Crack Width) - ACI 318-19 القسم 24.5 ===
        val crackWidth = checkCrackWidthACI(
            result = flexureResult,
            fcPrime = fcPrime,
            moment = serviceMoment,
            effectiveDepth = effectiveDepth,
            width = width,
            isExterior = false
        )

        // === التحقق من طول التثبيت (Development Length) - ACI 318-19 القسم 25.4.2 ===
        val devLength = checkDevelopmentLengthACI(flexureResult, fcPrime, fy, span)

        // === تجميع مراجع الكود ===
        codeNotes.add(CodeReference.ACI.BEAM_FLEXURE)
        codeNotes.add(CodeReference.ACI.BEAM_SHEAR)
        codeNotes.add(CodeReference.ACI.SLAB_DEFLECTION)  // مرجع الانحراف
        codeNotes.add("ACI 318-19 §24.5: Crack Width Control")
        codeNotes.add(CodeReference.ACI.BEAM_DEVELOPMENT_LENGTH)
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
            shearResult = shearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(beamType, totalLoad),
            shearDiagram = generateShearDiagram(beamType, totalLoad),
            inventoryAnalysis = inventoryAnalysis,
            crackWidthCheck = crackWidth,
            developmentLengthCheck = devLength,
            warnings = warnings + flexureResult.warnings + shearResult.warnings,
            codeNotes = codeNotes + flexureResult.codeNotes + shearResult.codeNotes
        )
    }

    // ========================================================================
    //  فحص الانحراف المتقدم حسب ACI 318-19 القسم 24.2
    // ========================================================================

    /**
     * فحص الانحراف المتقدم حسب ACI 318-19 القسم 24.2
     *
     * يستخدم عزم القصور الذاتي الفعال Ie (ACI المعادلة 24.2.3.5a):
     *   Ie = (Mcr/Ma)³ × Ig + [1 - (Mcr/Ma)³] × Icr  ≤  Ig
     *
     * حيث:
     *   Mcr = fr × Ig / yt  (عزم التشقق)
     *   fr = 0.62 × λ × √(fc')  (معامل تشقق الخرسانة - ACI 19.2.3.1)
     *   Icr = عزم القصور للمقطع المتشقق
     *
     * معامل الزيادة طويل المدى حسب ACI 24.2.4.1:
     *   λΔ = ξ / (1 + 50 × ρ')
     *   حيث ξ = 2.0 للرطوبة المتوسطة
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
    private fun checkDeflectionACI(
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

        // معامل مرونة الخرسانة - ACI 19.2.2.1
        // Ec = 4700 × √(fc')  (MPa = N/mm²)
        val Ec = EC * sqrt(fcPrime.coerceAtLeast(1.0))  // MPa
        val Es = 200000.0  // MPa - معامل مرونة الحديد

        // نسبة التعديل n = Es / Ec
        val n = Es / Ec

        // === حساب عزم القصور الذاتي للمقطع الكلي (Gross Moment of Inertia) ===
        // Ig = b × h³ / 12  (mm⁴)
        val Ig = beamWidth * depth.pow(3) / 12.0

        // المسافة من المحور المحايد إلى الطرف السفلي (للمقطع المستطيل: yt = h/2)
        val yt = depth / 2.0

        // === حساب عزم التشقق (Cracking Moment) - ACI 24.2.3.5 ===
        // Mcr = fr × Ig / yt
        // fr = معامل تشقق الخرسانة = 0.62 × λ × √(fc') - ACI 19.2.3.1
        val fr = 0.62 * LAMBDA * sqrt(fcPrime.coerceAtLeast(1.0))  // MPa
        val Mcr = fr * Ig / yt  // N.mm

        // عزم الخدمة الفعلي (Ma) بالمليمتر
        val Ma = serviceMoment * 1e6  // N.mm

        // === حساب عزم القصور للمقطع المتشقق Icr ===
        // ACI 24.2.3.5a - حساب مفصل للمقطع المتشقق
        // محور محايد المقطع المتشقق: b×x²/2 = n×As×(d-x)
        // x² + 2nρdx - 2nρd² = 0
        val rho = if (effectiveDepth > 0 && beamWidth > 0) astProvided / (beamWidth * effectiveDepth) else 0.0
        val nRho = n * rho

        // حل المعادلة التربيعية لموقع المحور المحايد
        // x = d × [-nρ + √(n²ρ² + 2nρ)]
        val xCr = if (nRho > 0) {
            effectiveDepth * (-nRho + sqrt(nRho * nRho + 2 * nRho))
        } else {
            effectiveDepth * 0.3  // قيمة افتراضية
        }.coerceIn(0.1 * effectiveDepth, 0.6 * effectiveDepth)

        // Icr = b×x³/3 + n×As×(d-x)²  (mm⁴) - ACI 24.2.3.5
        val Icr = beamWidth * xCr.pow(3) / 3.0 + n * astProvided * (effectiveDepth - xCr).pow(2)

        // === حساب عزم القصور الفعال Ie - ACI المعادلة 24.2.3.5a ===
        // Ie = (Mcr/Ma)³ × Ig + [1 - (Mcr/Ma)³] × Icr
        // بشرط أن لا يتجاوز Ig
        val mcRatio = if (Ma > 0) Mcr / Ma else 1.0
        val mcRatioCubed = mcRatio.pow(3).coerceAtMost(1.0)

        val Ie: Double
        if (mcRatio >= 1.0) {
            // المقطع لا يتشقق - نستخدم Ig
            Ie = Ig
        } else {
            // المقطع يتشقق - نستخدم المعادلة 24.2.3.5a
            Ie = mcRatioCubed * Ig + (1.0 - mcRatioCubed) * Icr
            Ie = minOf(Ie, Ig)  // لا يتجاوز عزم القصور الكلي
        }

        // === حساب الانحراف الفوري (Immediate Deflection) ===
        // δi = α × w × L⁴ / (E × Ie)
        // حيث α يعتمد على نوع الدعامات
        val w = serviceLoad / 1000.0  // kN/m → N/mm (1 kN/m = 1 N/mm)
        val L = span * 1000.0  // m → mm

        // معامل الشكل حسب نوع الكمرة
        val alpha = when (beamType) {
            is BeamType.SimplySupported -> 5.0 / 384.0    // حمل موزع - بسيطة الدعم
            is BeamType.Fixed -> 1.0 / 384.0              // حمل موزع - مثبتة الطرفين
            is BeamType.Cantilever -> 1.0 / 8.0           // حمل موزع - كابولي
            is BeamType.Continuous -> 3.0 / 384.0         // تقريبي للمستمر
            is BeamType.MultiSupport -> 3.0 / 384.0       // تقريبي
            is BeamType.Haunched -> 4.0 / 384.0           // تقريبي
            is BeamType.Vierendeel -> 5.0 / 384.0         // تقريبي
        }

        val immediateDeflection = alpha * w * L.pow(4) / (Ec * Ie)  // mm

        // === معامل الزيادة طويل المدى - ACI 24.2.4.1 ===
        // λΔ = ξ / (1 + 50 × ρ')
        // حيث:
        //   ξ = 2.0 للرطوبة المتوسطة (ACI Table 24.2.4.1)
        //   ρ' = نسبة التسليح المضغوط (نأخذها = 0 لتصميم محافظ)
        val xi = 2.0  // عامل الزمن للرطوبة المتوسطة - ACI Table 24.2.4.1
        val rhoPrime = 0.0  // نسبة تسليح الضغط (افتراض محافظ: لا يوجد تسليح ضغط)
        val longTermMultiplier = if (rhoPrime >= 0) {
            xi / (1.0 + 50.0 * rhoPrime)
        } else {
            xi
        }

        // الانحراف الكلي = الانحراف الفوري × (1 + معامل الزيادة طويل المدى)
        val longTermDeflection = immediateDeflection * (1.0 + longTermMultiplier)

        // === الحدود المسموحة للانحراف - ACI 24.2.2 ===
        // للأسقف والكمرات التي تدعمها: L/360 للانحراف الفوري بسبب الأحمال الحية
        // للانحراف الكلي (فوري + طويل المدى): L/240
        val allowableImmediate = L / 360.0  // mm - للانحراف الفوري
        val allowableTotal = L / 240.0      // mm - للانحراف الكلي

        // نستخدم الحد الأكثر تحفظاً
        val allowableDeflection = allowableTotal
        val ratio = longTermDeflection / allowableDeflection

        // === التوصيات ===
        val recommendation = when {
            ratio > 1.2 -> "زِد عمق الكمرة أو قلل نسبة التسليح - ACI 24.2.2"
            ratio > 1.0 -> "الانحراف يتجاوز الحد المسموح قليلاً - راجع ACI 24.2.2"
            ratio > 0.8 -> "الانحراف قريب من الحد - ACI 24.2.2"
            else -> "الانحراف مقبول حسب ACI 24.2.2"
        }

        if (ratio > 1.0) {
            deflectionWarnings.add("⚠️ Deflection exceeds ACI 24.2.2 limits (δ/δ_allow = ${"%.2f".format(ratio)})")
        }

        return DeflectionCheckResult(
            immediateDeflection = immediateDeflection,
            longTermDeflection = longTermDeflection,
            calculatedDeflection = longTermDeflection,
            allowableDeflection = allowableDeflection,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            message = "ACI 24.2: δi=%.2fmm, δLT=%.2fmm, δ_allow=%.2fmm".format(
                immediateDeflection, longTermDeflection, allowableDeflection
            ),
            recommendation = recommendation,
            warnings = deflectionWarnings
        )
    }

    // ========================================================================
    //  فحص عرض الشروخ حسب ACI 318-19 القسم 24.5
    // ========================================================================

    /**
     * فحص عرض الشروخ حسب ACI 318-19 القسم 24.5 (طريقة مبسطة)
     *
     * ACI 318-19 يستخدم طريقة التحكم في الشروخ عن طريق التوزيع
     * وحد أقصى لتباعد التسليح بدلاً من حساب عرض الشرخ المباشر.
     *
     * المعادلة المبسطة لعرض الشرخ (مشابهة لمعادلة Gergely-Lutz):
     *   z = fs × dc × A^(1/3) / (3 × (db × A)^(1/3))
     *   wk ≈ z / 1000  (تحويل تقريبي)
     *
     * أو بشكل مبسط أكثر:
     *   wk ≈ 0.011 × fs × ∛(dc × A)
     *
     * حيث:
     *   fs = إجهاد الحديد تحت حمل الخدمة (ksi)
     *   dc = المسافة من مركز أقرب سيخ للسطح (in.)
     *   A = مساحة الخرسانة المحيطة بكل سيخ (in.²)
     *   db = قطر السيخ (in.)
     *
     * الحدود المسموحة:
     *   z ≤ 175 kip/in للعناصر الداخلية → wk ≤ 0.41 mm
     *   z ≤ 145 kip/in للعناصر الخارجية → wk ≤ 0.33 mm
     *
     * @param result نتيجة التسليح
     * @param fcPrime مقاومة الأسطوانة (MPa)
     * @param moment عزم الخدمة (kN.m)
     * @param effectiveDepth العمق الفعال (mm)
     * @param width عرض الكمرة (mm)
     * @param isExterior هل العنصر خارجي؟
     * @return CrackWidthCheckResult نتيجة فحص عرض الشروخ
     */
    private fun checkCrackWidthACI(
        result: ReinforcementResult,
        fcPrime: Double,
        moment: Double,
        effectiveDepth: Double,
        width: Double,
        isExterior: Boolean
    ): CrackWidthCheckResult {
        if (result.astProvided <= 0 || effectiveDepth <= 0) {
            return CrackWidthCheckResult(
                calculatedWidth = 0.0,
                allowableWidth = 0.41,
                isSafe = true,
                codeReference = "ACI 318-19: Section 24.5 (Crack Width Control)"
            )
        }

        // === حساب إجهاد الحديد تحت حمل الخدمة ===
        // fs = M_service / (As × j × d)
        // حيث j ≈ 0.875 (تقريبي لحساب مبسط)
        val j = 0.875
        val fs = (moment * 1e6) / (result.astProvided * j * effectiveDepth)  // MPa

        // تحويل fs إلى ksi (1 ksi = 6.895 MPa)
        val fsKsi = fs / 6.895

        // === المسافة من مركز السيخ للسفح السفلي (dc) ===
        // dc = cover + stirrup/2 + bar_dia/2
        val dc = DEFAULT_COVER + 10.0 / 2.0 + result.barDiameter / 2.0  // mm
        val dcIn = dc / 25.4  // تحويل إلى إنش

        // === مساحة الخرسانة المحيطة بكل سيخ (A) ===
        // A = b × s / (عدد الأسياخ)
        // حيث s = التباعد بين الأسياخ
        val spacing = if (result.numberOfBars > 1) {
            (width - 2 * DEFAULT_COVER - result.numberOfBars * result.barDiameter) / (result.numberOfBars - 1)
        } else {
            width - 2 * DEFAULT_COVER
        }
        val spacingIn = spacing / 25.4  // تحويل إلى إنش
        val A = width / 25.4 * spacingIn / result.numberOfBars  // in² (لكل سيخ)
        val aCubed = if (A > 0) cbrt(A) else 1.0

        // === حساب معامل z (Gergely-Lutz) ===
        // z = fs × dc × ∛(A) (بالمقاسات الأمريكية)
        // z = fs(ksi) × dc(in) × ∛(A(in²))
        val z = fsKsi * dcIn * aCubed  // kip/in

        // === تقدير عرض الشرخ من z ===
        // العلاقة التقريبية: wk(mm) ≈ z / 425
        val crackWidth = z / 425.0 * 25.4  // تحويل إلى mm

        // === الحد المسموح حسب نوع العنصر ===
        // ACI 24.5: z ≤ 175 kip/in للعناصر الداخلية ( wk ≈ 0.41 mm )
        //            z ≤ 145 kip/in للعناصر الخارجية ( wk ≈ 0.33 mm )
        val allowableWidth = if (isExterior) 0.33 else 0.41
        val isSafe = crackWidth <= allowableWidth

        return CrackWidthCheckResult(
            calculatedWidth = crackWidth,
            allowableWidth = allowableWidth,
            isSafe = isSafe,
            codeReference = "ACI 318-19: Section 24.5 (z = ${"%.0f".format(z)} kip/in, limit = ${if (isExterior) 145 else 175} kip/in)"
        )
    }

    // ========================================================================
    //  فحص طول التثبيت حسب ACI 318-19 القسم 25.4.2
    // ========================================================================

    /**
     * فحص طول التثبيت حسب ACI 318-19 القسم 25.4.2
     *
     * طول التثبيت للقضبان المضغوطة والملساء:
     *   Ld = (fy × ψt × ψe × ψs) / (1.7 × λ × √(fc')) × db
     *
     * حيث:
     *   ψt = 1.3 للقضبان العلوية (أكثر من 300mm خرسانة تحتها)
     *   ψe = 1.2 للأسياخ المغلفة بالإيبوكسي
     *   ψs = 1.0 للقضبان ≤ No.6 (22mm), 0.8 للقضبان الأكبر
     *   λ = 1.0 للخرسانة العادية
     *   الحد الأدنى: Ld ≥ 300mm
     *
     * @param result نتيجة التسليح
     * @param fcPrime مقاومة الأسطوانة (MPa)
     * @param fy إجهاد الخضوع (MPa)
     * @param span البحر (m)
     * @return DevelopmentLengthCheckResult نتيجة فحص طول التثبيت
     */
    private fun checkDevelopmentLengthACI(
        result: ReinforcementResult,
        fcPrime: Double,
        fy: Double,
        span: Double
    ): DevelopmentLengthCheckResult {
        // حساب طول التثبيت باستخدام ACIBeam
        val ldBottom = baseBeam.calculateDevelopmentLength(
            barDiameter = result.barDiameter,
            fy = fy,
            fcu = fcPrime / 0.8,  // نمرر fcu (لأن ACIBeam يحول داخلياً)
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

        // نستخدم القيمة الأكبر (السفلي عادةً)
        val ldRequired = maxOf(ldBottom, ldTop)

        // الطول المتاح التقريبي
        // للكمرة البسيطة: نحتاج Ld/2 من كل جهة
        // للكمرة المثبتة: نحتاج Ld من كل جهة
        val availableLength = span * 1000.0 / 2.0  // mm (نصف البحر كمتاح للتمسك)

        val isSafe = ldRequired <= availableLength

        return DevelopmentLengthCheckResult(
            requiredLength = ldRequired,
            availableLength = availableLength,
            isSafe = isSafe,
            codeReference = "ACI 318-19: Section 25.4.2 (Ld_bot=${"%.0f".format(ldBottom)}mm, Ld_top=${"%.0f".format(ldTop)}mm)"
        )
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
            designCode = DesignCode.ACI,
            elementLength = span
        )
    }

    // ========================================================================
    //  توليد بدائل التسليح (اقتصادية وآمنة)
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

        for (dia in ACI_BAR_DIAMETERS) {
            if (dia == selectedDia) continue

            val area = PI * dia * dia / 4.0
            val numBars = ceil(astRequired / area).toInt().coerceIn(2, 12)
            val asProv = numBars * area

            // التحقق من التباعد - ACI 25.2
            val clearSpacing = if (numBars > 1) {
                (width - 2 * DEFAULT_COVER - numBars * dia) / (numBars - 1)
            } else {
                width - 2 * DEFAULT_COVER
            }

            // الحد الأدنى للتباعد - ACI 25.2.1
            val minSpacing = maxOf(25.0, dia, 25.0)  // max(25mm, db, حجم الركام)
            if (clearSpacing < minSpacing) continue

            // حساب السعة
            val aBlock = if (fcPrime > 0) asProv * fy / (0.85 * fcPrime * width) else 0.0
            val Mn = asProv * fy * (effectiveDepth - aBlock / 2.0)
            val capacity = PHI_FLEXURE * Mn / 1e6  // kN.m
            val utilization = if (capacity > 0) designMoment / capacity else 2.0

            // التحقق من نسبة التسليح القصوى - ACI 9.3.3.1
            val beta1 = calculateBeta1(fcPrime)
            val rhoProv = asProv / (width * effectiveDepth)
            val rhoMax = 0.85 * beta1 * (fcPrime / fy) * 0.375

            if (rhoProv > rhoMax) continue  // يتجاوز الحد الأقصى

            if (utilization in 0.5..1.0) {
                val utilizationPct = (utilization * 100).toInt()
                alternatives.add("${numBars}Ø${dia.toInt()} (${utilizationPct}% utilized)")
            }
        }

        // ترتيب حسب نسبة الاستغلال (من الأقل للأعلى = اقتصادي إلى آمن)
        val notes = mutableListOf<String>()
        if (alternatives.size >= 1) {
            notes.add("Economical alt: ${alternatives.first()}")
        }
        if (alternatives.size >= 2) {
            notes.add("Safest alt: ${alternatives.last()}")
        }
        return notes
    }

    /**
     * حساب معامل β1 حسب ACI 21.2.2.1
     * β1 = 0.85 لـ fc' ≤ 28 MPa
     * β1 ينخفض 0.05 لكل 7 MPa فوق 28 MPa
     * β1 لا يقل عن 0.65
     */
    private fun calculateBeta1(fc: Double): Double {
        return when {
            fc <= 28.0 -> 0.85
            fc >= 55.0 -> 0.65
            else -> 0.85 - 0.05 * ((fc - 28.0) / 7.0)
        }
    }

    // ========================================================================
    //  توليد مخطط عزم الانحناء لجميع أنواع الكمرات
    // ========================================================================

    /**
     * توليد مخطط عزم الانحناء (Moment Diagram)
     *
     * يُنتج قائمة من النقاط (الموقع، العزم) لكل نوع كمرة
     *
     * @param beamType نوع الكمرة
     * @param load الحمل الكلي الموزع (kN/m)
     * @return قائمة أزواج (الموقع بالأمتار، العزم بـ kN.m)
     */
    private fun generateMomentDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()

        when (beamType) {
            is BeamType.SimplySupported -> {
                // عزم الانحناء لكمرة بسيطة الدعم:
                // M(x) = (w × L / 2) × x - (w × x² / 2)
                // M_max = w × L² / 8 (عند المنتصف)
                val L = beamType.span
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = (load * L / 2.0) * x - (load * x * x / 2.0)
                    points.add(x to m)
                }
            }

            is BeamType.Fixed -> {
                // عزم الانحناء لكمرة مثبتة الطرفين:
                // M(x) = wLx/2 - wx²/2 - wL²/12
                // M_support = -wL²/12 (سالب عند الدعامات)
                // M_mid = +wL²/24 (موجب عند المنتصف)
                val L = beamType.span
                val mSupport = -load * L * L / 12.0
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = (load * L / 2.0) * x - (load * x * x / 2.0) - (load * L * L / 12.0)
                    points.add(x to m)
                }
            }

            is BeamType.Cantilever -> {
                // عزم الانحناء لكمرة كابولي:
                // M(x) = -w × (L-x)² / 2
                // M_max = -w × L² / 2 (سالب عند الدعامة)
                val L = beamType.length
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = -load * (L - x) * (L - x) / 2.0
                    points.add(x to m)
                }
            }

            is BeamType.Continuous -> {
                // مخطط مبسط لكمرة مستمرة (بحرين متساويين)
                // البحر الخارجي: تقريباً بسيط عند الطرف الحر، مستمر عند الدعامة الوسطى
                // البحر الداخلي: مستمر من الجانبين
                val spans = beamType.spans
                if (spans.isNotEmpty()) {
                    val maxSpan = spans.maxOrNull() ?: 1.0
                    val numPoints = 20

                    for (i in 0..numPoints) {
                        val x = maxSpan * i / numPoints
                        // عزم تقريبي للكمرة المستمرة
                        val m = if (x <= maxSpan / 2.0) {
                            // النصف الأول: يشبه البسيط مع تصحيح
                            (load * maxSpan / 2.0) * x - (load * x * x / 2.0) - load * maxSpan * maxSpan / 10.0 * (x / maxSpan)
                        } else {
                            // النصف الثاني: معكوس
                            (load * maxSpan / 2.0) * x - (load * x * x / 2.0) - load * maxSpan * maxSpan / 10.0 * ((maxSpan - x) / maxSpan)
                        }
                        points.add(x to m)
                    }
                }
            }

            is BeamType.MultiSupport -> {
                // مخطط مبسط لكمرة متعددة الدعامات
                val totalLength = beamType.totalLength
                val numPoints = 30
                val supportPositions = beamType.supportPositions

                for (i in 0..numPoints) {
                    val x = totalLength * i / numPoints
                    // عزم تقريبي: نعتبر كمرة مستمرة
                    val m = (load * totalLength / 2.0) * x - (load * x * x / 2.0)
                        - load * totalLength * totalLength / 10.0 * sin(PI * x / totalLength)
                    points.add(x to m)
                }
            }

            is BeamType.Haunched -> {
                // مخطط لكمرة متغيرة المقطع
                // نستخدم عزم الكمرة المثبتة كتقريب
                val L = beamType.span
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val m = (load * L / 2.0) * x - (load * x * x / 2.0) - (load * L * L / 12.0)
                    points.add(x to m)
                }
            }

            is BeamType.Vierendeel -> {
                // مخطط لكمرة فيرينديل (بدون أقطار)
                // يعامل ككمرة بسيطة الدعم كتقريب أولي
                val L = beamType.span
                val numPoints = 20
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
     * يُنتج قائمة من النقاط (الموقع، القوة القصية) لكل نوع كمرة
     *
     * @param beamType نوع الكمرة
     * @param load الحمل الكلي الموزع (kN/m)
     * @return قائمة أزواج (الموقع بالأمتار، القوة القصية بـ kN)
     */
    private fun generateShearDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()

        when (beamType) {
            is BeamType.SimplySupported -> {
                // قوة القص لكمرة بسيطة الدعم:
                // V(x) = w × L / 2 - w × x
                // V_max = +wL/2 (عند الدعامة اليسرى)
                // V = 0 (عند المنتصف)
                // V_min = -wL/2 (عند الدعامة اليمنى)
                val L = beamType.span
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }

            is BeamType.Fixed -> {
                // قوة القص لكمرة مثبتة الطرفين:
                // V(x) = w × L / 2 - w × x
                // رد الفعل = wL/2 من كل جهة
                val L = beamType.span
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }

            is BeamType.Cantilever -> {
                // قوة القص لكمرة كابولي:
                // V(x) = w × (L - x)
                // V_max = w × L (عند الدعامة)
                // V = 0 (عند الطرف الحر)
                val L = beamType.length
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * (L - x)
                    points.add(x to v)
                }
            }

            is BeamType.Continuous -> {
                // مخطط مبسط لكمرة مستمرة
                val spans = beamType.spans
                if (spans.isNotEmpty()) {
                    val maxSpan = spans.maxOrNull() ?: 1.0
                    val numPoints = 20
                    for (i in 0..numPoints) {
                        val x = maxSpan * i / numPoints
                        // قوى القص التقريبية للمستمر: رد الفعل أكبر بقليل من البسيط
                        val v = load * maxSpan * 0.6 - load * x
                        points.add(x to v)
                    }
                }
            }

            is BeamType.MultiSupport -> {
                // مخطط مبسط لكمرة متعددة الدعامات
                val totalLength = beamType.totalLength
                val numPoints = 30
                for (i in 0..numPoints) {
                    val x = totalLength * i / numPoints
                    // قوى القص التقريبية
                    val v = load * totalLength * 0.6 - load * x
                    points.add(x to v)
                }
            }

            is BeamType.Haunched -> {
                // مخطط القص لكمرة متغيرة المقطع (مثل المثبتة)
                val L = beamType.span
                val numPoints = 20
                for (i in 0..numPoints) {
                    val x = L * i / numPoints
                    val v = load * L / 2.0 - load * x
                    points.add(x to v)
                }
            }

            is BeamType.Vierendeel -> {
                // مخطط القص لكمرة فيرينديل (مثل البسيطة)
                val L = beamType.span
                val numPoints = 20
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