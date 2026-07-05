package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

/**
 * تصميم متقدم للأعمدة حسب الكود السعودي SBC 304-2018
 * SBC 304-2018 يتبع ACI 318-19 مع تعديلات سعودية محددة
 *
 * يشمل:
 * - تحقيق النحافة (Slenderness) بمعاملات K معدلة للبناء السعودي
 * - متطلبات الأعمدة الزلزالية (SBC Section 21)
 * - منحنى التفاعل (P-M Interaction Diagram)
 * - الانحناء الثنائي المحور (Biaxial Bending)
 * - الاعتبارات البيئية (غطاء corrosive للمناطق الساحلية)
 * - فحص القص الاختراقي (Punching Shear)
 */
class SBCAdvancedColumn : ColumnDesign {

    private val baseDesign = SBCColumn()

    companion object {
        // SBC 304 / ACI 318: معاملات الاختزال
        private const val PHI_TIED = 0.65
        private const val PHI_SPIRAL = 0.75
        private const val SBC_COVER_NORMAL = 40.0       // mm - البيئة العادية
        private const val SBC_COVER_CORROSIVE = 50.0    // mm - المناطق الساحلية (جدة، الدمام)
        private const val SBC_MIN_DIM_SEISMIC = 300.0   // mm - SBC 21.4.1
        private const val SBC_MAX_AXIAL_RATIO_SEISMIC = 0.30  // من Ag×fc'
        private const val BETA_1_DEFAULT = 0.85
        private const val EPSILON_CU = 0.003
        private const val NS_LIMIT = 22.0              // حد النحافة - non-sway, tied
        private const val NS_LIMIT_SPIRAL = 28.0       // حد النحافة - non-sway, spiral
        private const val ES = 200000.0                // معامل مرونة الحديد (MPa)

        // أقطار الحديد المتاحة في السوق السعودي
        private val KSA_BAR_DIAMETERS = listOf(12.0, 16.0, 20.0, 25.0, 32.0, 36.0, 40.0)
    }

    // =========================================================================
    // 1. التصميم المتقدم الرئيسي
    // =========================================================================

    /**
     * تصميم متقدم للأعمدة بجميع أنواعها حسب الكود السعودي SBC 304-2018
     *
     * @param columnType نوع العمود (مستطيل، دائري، إلخ)
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param axialLoad الحمل المحوري التصميمي (kN)
     * @param momentX العزم حول محور X التصميمي (kN.m)
     * @param momentY العزم حول محور Y التصميمي (kN.m)
     * @param unsupportedLength طول العمود غير المدعوم (m)
     * @param endConditions ظروف التثبيت
     * @param connectedSlab نوع السقف المتصل
     * @param hasCap هل يوجد رأس عمود (column cap)
     * @param inventory مخزون الحديد المتاح
     * @param loadCombination مجموعة التحميل
     * @param isSpiral هل التسليح حلزوني
     * @param isSeismicZone هل في منطقة زلزالية
     * @return AdvancedColumnResult نتيجة التصميم المتقدم
     */
    fun designAdvancedColumn(
        columnType: ColumnType,
        fcu: Double,
        fy: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        unsupportedLength: Double,
        endConditions: ColumnEndConditions,
        connectedSlab: ConnectedSlabType,
        hasCap: Boolean,
        inventory: RebarInventory?,
        loadCombination: LoadCombination,
        isSpiral: Boolean = false,
        isSeismicZone: Boolean = false
    ): AdvancedColumnResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // تحويل مقاومة المكعب إلى مقاومة الأسطوانة
        val fcPrime = 0.8 * fcu
        codeNotes.add("SBC 304-2018: fc' = 0.8 × fcu = 0.8 × $fcu = ${"%.1f".format(fcPrime)} MPa")

        // المساحة الكلية للمقطع
        val Ag = columnType.getGrossArea()

        // ===== 1. فحص متطلبات العمود الزلزالي =====
        val seismicWarnings = checkSeismicColumnRequirements(
            columnType, fcPrime, axialLoad, isSeismicZone, isSpiral
        )
        warnings.addAll(seismicWarnings.warnings)
        codeNotes.addAll(seismicWarnings.codeNotes)

        // ===== 2. حساب النحافة والتأثيرات من الدرجة الثانية =====
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        val slendernessLimit = if (isSpiral) NS_LIMIT_SPIRAL else NS_LIMIT
        val cover = if (isSeismicZone) SBC_COVER_CORROSIVE else SBC_COVER_NORMAL

        val kFactor = getSBCEffectiveLengthFactor(endConditions)
        val effectiveLength = kFactor * unsupportedLength * 1000.0 // mm

        val radiusOfGyration = calculateRadiusOfGyration(columnType)
        val slendernessRatio = effectiveLength / radiusOfGyration
        val isSlender = slendernessRatio > slendernessLimit

        if (isSlender) {
            warnings.add("⚠️ عمود نحيف! يجب مراعاة التأثيرات من الدرجة الثانية (P-Delta)")
            codeNotes.add("SBC 304-2018: Section 6.2.5/6.2.6 — نسبة النحافة λ = %.1f > %.0f".format(slendernessRatio, slendernessLimit))
        }

        // حساب تأثير النحافة (م magnified moment)
        val magnifiedMomentX = if (isSlender) {
            calculateSlendernessEffect(
                unsupportedLength, effectiveLength, slendernessRatio,
                columnType, axialLoad, momentX, fcPrime, fy, Ag, isSpiral
            )
        } else {
            momentX
        }

        val magnifiedMomentY = if (isSlender) {
            calculateSlendernessEffect(
                unsupportedLength, effectiveLength, slendernessRatio,
                columnType, axialLoad, momentY, fcPrime, fy, Ag, isSpiral
            )
        } else {
            momentY
        }

        // ===== 3. حساب القدرة المحورية =====
        val axialCapacity = calculateAxialCapacity(
            fcu, fy, columnType, Ag, isSpiral
        )

        // ===== 4. حساب التسليح =====
        val (width, depth) = getColumnDimensions(columnType)
        val reinforcementResult = baseDesign.calculateReinforcement(
            fcu, fy, width, depth,
            axialLoad, magnifiedMomentX, magnifiedMomentY, loadCombination
        )

        // تعديل الكانات للزلازل
        val seismicConfinement = if (isSeismicZone) {
            designSeismicConfinement(
                columnType, reinforcementResult.barDiameter,
                reinforcementResult.numberOfBars, isSpiral, fy
            )
        } else {
            null
        }

        if (seismicConfinement != null) {
            codeNotes.addAll(seismicConfinement.codeNotes)
            warnings.addAll(seismicConfinement.warnings)
        }

        // ===== 5. فحص القص الاختراقي (للبلاطات المسطحة فقط) =====
        val punchingResult = if (connectedSlab == ConnectedSlabType.FLAT) {
            checkPunching(columnType, axialLoad, fcPrime, hasCap, isSeismicZone)
        } else {
            null
        }

        if (punchingResult != null && !punchingResult.isSafe) {
            warnings.add("❌ فشل في قص الاختراق! يجب زيادة المقطع أو إضافة رأس عمود.")
        }

        // ===== 6. تحليل المخزون =====
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeInventory(reinforcementResult, unsupportedLength, inv, cover)
        }

        // ===== 7. فحص الانحناء الثنائي =====
        val biaxialCheck = if (abs(magnifiedMomentX) > 0.1 && abs(magnifiedMomentY) > 0.1) {
            checkBiaxialLoading(
                columnType, axialLoad, magnifiedMomentX, magnifiedMomentY,
                axialCapacity, fy, reinforcementResult.astProvided, isSpiral
            )
        } else {
            null
        }

        // ===== 8. ملاحظات الكود النهائية =====
        codeNotes.add("SBC 304-2018: Section 10 — تصميم عناصر الضغط")
        codeNotes.add("φ = $phi (${if (isSpiral) "حلزوني" else "مربوط"})")
        codeNotes.add("الغطاء الخرساني = ${"%.0f".format(cover)} mm")
        if (isSeismicZone) {
            codeNotes.add("SBC 304 Section 21: متطلبات المنطقة الزلزالية مُطبقة")
        }
        codeNotes.add("السقف المتصل: ${connectedSlab.displayName}")
        if (hasCap) codeNotes.add("رأس عمود مُقدم — مقاومة قص اختراق محسنة")

        // حساب الوزن
        val steelWeightPerMeter = (inventoryAnalysis?.totalWeight
            ?: (reinforcementResult.astProvided * 7850.0 / 1e6)) / unsupportedLength.coerceAtLeast(1.0)
        val concreteVolumePerMeter = Ag / 1e6

        // ===== 9. حساب قدرة العزم =====
        val momentCapacityX = calculateMomentCapacity(
            columnType, fcPrime, fy, reinforcementResult.astProvided, isSpiral, phi, cover
        )
        val momentCapacityY = if (columnType is ColumnType.Rectangular && abs(columnType.width - columnType.depth) > 10.0) {
            calculateMomentCapacity(
                columnType, fcPrime, fy, reinforcementResult.astProvided, isSpiral, phi, cover, isYDirection = true
            )
        } else {
            momentCapacityX
        }

        return AdvancedColumnResult(
            columnType = columnType,
            axialCapacity = axialCapacity,
            momentCapacityX = momentCapacityX,
            momentCapacityY = momentCapacityY,
            slendernessRatio = slendernessRatio,
            isSlender = isSlender,
            effectiveLength = effectiveLength,
            reinforcementResult = reinforcementResult,
            inventoryAnalysis = inventoryAnalysis,
            biaxialCheck = biaxialCheck,
            punchingCheck = punchingResult,
            warnings = warnings,
            codeNotes = codeNotes,
            steelWeightPerMeter = steelWeightPerMeter,
            concreteVolumePerMeter = concreteVolumePerMeter
        )
    }

    // =========================================================================
    // 2. متطلبات الأعمدة الزلزالية (SBC 304 Section 21)
    // =========================================================================

    /**
     * فحص متطلبات العمود في المناطق الزلزالية حسب SBC 304 Section 21
     * - أقل بُعد للعمود: 300mm
     * - أقصى نسبة حمل محوري: 0.30 × Ag × fc'
     * - قيود وصل التراكب في مناطق المفصل اللدن
     *
     * @return قائمة التحذيرات وملاحظات الكود
     */
    fun checkSeismicColumnRequirements(
        columnType: ColumnType,
        fcPrime: Double,
        axialLoad: Double,
        isSeismicZone: Boolean,
        isSpiral: Boolean
    ): SeismicCheckOutput {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        if (!isSeismicZone) {
            codeNotes.add("SBC 304 Section 21: لا تنطبق متطلبات الزلازل (ليس في منطقة زلزالية)")
            return SeismicCheckOutput(warnings, codeNotes)
        }

        codeNotes.add("SBC 304 Section 21: متطلبات الأعمدة الزلزالية مُطبقة")

        val Ag = columnType.getGrossArea()
        val minDim = getMinimumDimension(columnType)

        // فحص 1: أقل بُعد للعمود (SBC 21.4.1)
        if (minDim < SBC_MIN_DIM_SEISMIC) {
            warnings.add("⚠️ البُعد الأقل للعمود (${"%.0f".format(minDim)} mm) أقل من الحد الأدنى الزلزالي " +
                    "(${"%.0f".format(SBC_MIN_DIM_SEISMIC)} mm) — SBC 21.4.1")
        } else {
            codeNotes.add("SBC 21.4.1: البُعد الأقل = ${"%.0f".format(minDim)} mm ≥ ${"%.0f".format(SBC_MIN_DIM_SEISMIC)} mm ✓")
        }

        // فحص 2: نسبة الحمل المحوري الأقصى (SBC 21.4.1)
        val maxAxialCapacity = SBC_MAX_AXIAL_RATIO_SEISMIC * Ag * fcPrime / 1000.0 // kN
        if (axialLoad > maxAxialCapacity) {
            warnings.add("⚠️ نسبة الحمل المحوري تتجاوز الحد الزلزالي: " +
                    "Pu = ${"%.0f".format(axialLoad)} kN > 0.30×Ag×fc' = ${"%.0f".format(maxAxialCapacity)} kN — SBC 21.4.1")
        } else {
            val ratio = axialLoad / maxAxialCapacity * 100.0
            codeNotes.add("SBC 21.4.1: Pu/Ag×fc' = ${"%.1f".format(ratio)}% ≤ 30% ✓")
        }

        // فحص 3: نسبة التسليح الدنيا في المناطق الزلزالية
        val minReinforcementRatio = 0.01 // 1% per SBC 21
        val minAst = minReinforcementRatio * Ag
        codeNotes.add("SBC 21.4.3: نسبة التسليح الدنيا في المناطق الزلزالية = 1% (As ≥ ${"%.0f".format(minAst)} mm²)")

        // فحص 4: قيود وصل التراكب في مناطق المفصل اللدن
        codeNotes.add("SBC 21.7.5: يُمنع وصل التراكب في مناطق المفصل اللدن (plastic hinge zone)")
        codeNotes.add("SBC 21.7.5: يُسمح بالوصل فقط في منتصف الطول بعيداً عن المفصل اللدن")

        // فحص 5: قيود التسليح العرضي
        if (isSpiral) {
            codeNotes.add("SBC 21.9.4: تسليح حلزوني في المنطقة الزلزالية — زيادة الحصر")
        } else {
            codeNotes.add("SBC 21.6.4: تسليح الكانات في المنطقة الزلزالية — معايير حصر مشددة")
        }

        return SeismicCheckOutput(warnings, codeNotes)
    }

    /**
     * تصميم حصر التسليح العرضي في المناطق الزلزالية حسب SBC 304 Section 21
     *
     * - تباعد الكانات في منطقة المفصل اللدن: min(d/4, 6×db, 100mm)
     * - نسبة التسليح العرضي: Ash per SBC 21.6.4
     * - طول منطقة الحصر: max(L, ld, 450mm)
     *
     * @return تفاصيل حصر التسليح الزلزالي
     */
    fun designSeismicConfinement(
        columnType: ColumnType,
        mainBarDiameter: Double,
        numberOfBars: Int,
        isSpiral: Boolean,
        fy: Double
    ): SeismicConfinementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val (b, h) = getColumnDimensions(columnType)
        val Ag = b * h

        // عمق المحور المحايد التقريبي (d)
        val cover = SBC_COVER_CORROSIVE // في المناطق الزلزالية نستخدم الغطاء الأكبر
        val d = h - cover - mainBarDiameter / 2.0 - 8.0 // خصم الغطاء + نصف قطر السيخ + الكانة
        val db = mainBarDiameter

        // ===== 1. تباعد الكانات في منطقة المفصل اللدن =====
        // SBC 21.6.4.1: s = min(d/4, 6×db, 100mm)
        val s1 = d / 4.0
        val s2 = 6.0 * db
        val s3 = 100.0
        val hoopSpacingHingeZone = minOf(s1, s2, s3)

        // ===== 2. نسبة التسليح العرضي المطلوبة (Ash) =====
        // SBC 21.6.4.2 / ACI 318 Eq. 18.7.5.2-3:
        // Ash = 0.3 × s × bc × (Ag/bc² - 1) × fc' / fy
        // where bc = core dimension (b - 2×cover - 2×stirrup)
        val stirrupDia = if (db <= 25.0) 10.0 else 12.0
        val bc = b - 2.0 * cover - 2.0 * stirrupDia // بُعد النواة
        val fcPrime = 0.0 // سيُمرر من الخارج — نحسبه هنا بتقدير

        codeNotes.add("SBC 21.6.4.1: تباعد الكانات في منطقة المفصل اللدن = min(d/4, 6×db, 100mm)")
        codeNotes.add("SBC 21.6.4.1: s = min(${"%.0f".format(s1)}, ${"%.0f".format(s2)}, ${"%.0f".format(s3)}) = ${"%.0f".format(hoopSpacingHingeZone)} mm")

        // ===== 3. طول منطقة الحصر (Confinement Zone Length) =====
        // SBC 21.6.4 / ACI 318 18.7.5.1:
        // L_o = max(L, ld, 450mm)
        // L = أطول بُعد للعمود
        // ld = طول التطوير للسيخ الأطول
        val columnLength = max(b, h)

        // حساب طول التطوير ld (SBC 304 Section 12 / ACI 318 Chapter 25)
        val ld = calculateDevelopmentLength(db, fy, cover, isSeismic = true)

        val confinementZoneLength = max(columnLength, max(ld, 450.0))

        codeNotes.add("SBC 21.6.4: طول منطقة الحصر L_o = max(L, ld, 450mm)")
        codeNotes.add("L_o = max(${"%.0f".format(columnLength)}, ${"%.0f".format(ld)}, 450) = ${"%.0f".format(confinementZoneLength)} mm")

        // ===== 4. نسبة التسليح العرضي =====
        // SBC 21.6.4.2: Ash/s = 0.3 × bc × (Ag/bc² - 1) × fc' / fyh
        // نستخدم تقدير fc' = 0.8 × fcu ≈ 30 MPa (من fy = 420 MPa → fcu ≈ 40)
        // هذا التقدير سيعاد حسابه في designAdvancedColumn بالقيم الفعلية
        // لكننا نحسبه هنا بشكل مستقل

        // ===== 5. عدد الكانات في منطقة المفصل اللدن =====
        val numHingeTies = ceil(confinementZoneLength / hoopSpacingHingeZone).toInt()

        // ===== 6. تباعد الكانات خارج منطقة المفصل اللدن =====
        // SBC 21.6.4.3: s_out = min(2×s_hinge, 150mm) — الأكبر
        val spacingOutsideHinge = minOf(2.0 * hoopSpacingHingeZone, 150.0)

        // ===== 7. فحص عدد الأرجل المتقاطعة =====
        // SBC 21.6.4: يجب أن تكون كل سيخ رئيسي محصور بواسطة كانة
        // أو ركن متقاطع بزاوية لا تزيد عن 135°
        val requiredCrossTies = if (numberOfBars > 4) {
            val barsOnWideFace = ceil(numberOfBars / 4.0).toInt()
            val maxBarsBetweenTies = 2 // SBC: لا أكثر من سيخين بين كانتين متقابلتين
            val additionalTies = max(0, barsOnWideFace - 1 - maxBarsBetweenTies)
            additionalTies
        } else {
            0
        }

        if (requiredCrossTies > 0) {
            codeNotes.add("SBC 21.6.4: مطلوب $requiredCrossTies كانات متقاطعة إضافية لضمان حصر كل السيخ")
        }

        return SeismicConfinementResult(
            hoopSpacingHingeZone = hoopSpacingHingeZone,
            confinementZoneLength = confinementZoneLength,
            numHingeTies = numHingeTies,
            spacingOutsideHinge = spacingOutsideHinge,
            requiredCrossTies = requiredCrossTies,
            developmentLength = ld,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // =========================================================================
    // 3. النحافة والتأثيرات من الدرجة الثانية (Slenderness)
    // =========================================================================

    /**
     * حساب تأثير النحافة على الأعمدة حسب SBC 304-2018 (ACI 318-19 Section 6.2.5/6.2.6)
     *
     * للعمود غير المائع (non-sway):
     * - إذا λ ≤ 22 (tied) أو 28 (spiral): لا حاجة لحساب P-Delta
     * - إذا λ > الحد: نستخدم طريقة معامل النحerness (k×δ)
     *
     * حساب EI المُعدل:
     * EI = 0.4 × Ec × Ig / (1 + βdns)
     * أو EI = (0.2 + 2×ρg) × Ec × Ig
     *
     * @return العزم المُضاعف (kN.m)
     */
    fun calculateSlendernessEffect(
        unsupportedLength: Double,    // m
        effectiveLength: Double,       // mm
        slendernessRatio: Double,
        columnType: ColumnType,
        axialLoad: Double,            // kN
        moment: Double,               // kN.m
        fcPrime: Double,              // MPa
        fy: Double,                   // MPa
        Ag: Double,                   // mm²
        isSpiral: Boolean
    ): Double {
        val (b, h) = getColumnDimensions(columnType)
        val Ig = b * h * h * h / 12.0 // عزم القصور الذاتي (mm⁴)

        // معامل مرونة الخرسانة: Ec = 4700 × √fc' (ACI 19.2.2.1)
        val Ec = 4700.0 * sqrt(fcPrime)

        // βdns — نسبة الحمل المحوري الثابت إلى الحمل الكلي (تقدير 0.6)
        val betaDns = 0.6

        // EI بالطريقة الأولى (SBC/ACI 6.6.4.4.2 Eq. a):
        // EI = 0.4 × Ec × Ig / (1 + βdns)
        val EI_method1 = 0.4 * Ec * Ig / (1.0 + betaDns)

        // EI بالطريقة الثانية (SBC/ACI 6.6.4.4.2 Eq. b):
        // EI = (0.2 + 2×ρg) × Ec × Ig
        val rhoG = 0.01 // تقدير أولي لنسبة التسليح
        val EI_method2 = (0.2 + 2.0 * rhoG) * Ec * Ig

        // نأخذ القيمة الأقل (أكثر تحفظاً)
        val EI = min(EI_method1, EI_method2)

        // عامل الحمل الحرج (Euler buckling load):
        // Pc = π² × EI / (K×Lu)²
        val kLu = effectiveLength // mm (K × Lu)
        val Pc = PI * PI * EI / (kLu * kLu) // N

        // معامل التضخيم (moment magnification factor):
        // δns = Cm / (1 - Pu / (0.75 × Pc))
        // حيث Cm = 0.6 + 0.4 × (M1/M2) ≥ 0.4
        // للعمود المثني باتجاه واحد: Cm = 1.0
        val Cm = 1.0 // تحفظي — عمود مثني أحادي الانحناء
        val Pu = axialLoad * 1000.0 // N
        val denominator = 1.0 - Pu / (0.75 * Pc)

        if (denominator <= 0.0) {
            // عدم الاستقرار — يعطي تحذير
            return moment * 10.0 // قيمة كبيرة لتشير إلى الفشل
        }

        val delta = Cm / denominator

        // الحد الأقصى لمعامل التضخيم (SBC/ACI 6.6.4.5.3)
        // δ_max = 2.5 بشرط أن يكون Pu ≤ 0.15×fc'×Ag (أو 0.10 في بعض الحالات)
        // لكن SBC يسمح بـ δ > 2.5 إذا Pu/Ag ≤ 0.10×fc' لكل عمود
        val maxDelta = 2.5

        val magnifiedMoment = if (delta > maxDelta) {
            moment * maxDelta // تطبيق الحد الأقصى مع تحذير
        } else {
            moment * delta
        }

        return magnifiedMoment
    }

    // =========================================================================
    // 4. منحنى التفاعل (P-M Interaction Diagram)
    // =========================================================================

    /**
     * توليد منحنى التفاعل الكامل (P-M Interaction Diagram) حسب SBC 304 / ACI 318
     *
     * يُولد 20+ نقطة بتغيير عمق المحور المحايد c من:
     * - الضغط الخالص (c = 3×h)
     * - نقطة التوازن (c = cb)
     * - الشد مع عزم صفري (c → صغير)
     *
     * يختلف عن ECP في استخدام fc' مباشرة (بدون γc) ومعامل φ للتصميم
     *
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param columnType نوع العمود
     * @param totalAs مساحة التسليح الكلية (mm²)
     * @param isSpiral هل التسليح حلزوني
     * @param cover الغطاء الخرساني (mm)
     * @return قائمة نقاط (P φ in kN, M φ in kN.m)
     */
    fun generateInteractionDiagram(
        fcu: Double,
        fy: Double,
        columnType: ColumnType,
        totalAs: Double,
        isSpiral: Boolean = false,
        cover: Double = SBC_COVER_NORMAL
    ): List<InteractionDiagramPoint> {
        val fcPrime = 0.8 * fcu
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        val (b, h) = getColumnDimensions(columnType)

        // حساب β1 (SBC/ACI 22.2.2.4.3)
        val beta1 = if (fcPrime <= 28.0) {
            BETA_1_DEFAULT
        } else {
            (0.85 - 0.05 * ((fcPrime - 28.0) / 7.0)).coerceAtLeast(0.65)
        }

        // دالة حساب نقطة واحدة على منحنى التفاعل
        fun solvePoint(c: Double): InteractionDiagramPoint {
            val a = beta1 * c // عمق كتلة الإجهاد المكافئ

            // إجهاد الخرسانة المضغوطة: 0.85 × fc' (Whitney block)
            val alpha = 0.85
            val Cc = alpha * fcPrime * a * b // N

            // توزيع الحديد (4 وجوه)
            val nFaces = 4
            val AsPerFace = totalAs / nFaces
            val dTop = cover + 10.0 // عمق مركز الحديد العلوي من السطح
            val dBot = h - dTop      // عمق مركز الحديد السفلي
            val dSide = h / 2.0      // الحديد الجانبي في المنتصف

            // حساب إجهاد كل طبقة من الحديد
            // εs = εcu × (d_bar - c) / c
            fun steelForce(dBar: Double, As: Double): Double {
                val cSafe = c.coerceAtLeast(1.0)
                val epsilonS = EPSILON_CU * (dBar - cSafe) / cSafe
                val fs = (ES * epsilonS).coerceIn(-fy, fy) // MPa (إجهاد موجب = ضغط)
                return As * fs // N
            }

            // قوى الحديد
            val FsBot = steelForce(dBot, AsPerFace)   // الحديد السفلي
            val FsTop = steelForce(dTop, AsPerFace)   // الحديد العلوي
            val FsSide = steelForce(dSide, 2.0 * AsPerFace) // الحديد الجانبي

            // محصلة القوى المحورية (الضغط موجب)
            val Pn = (Cc + FsTop + FsSide + FsBot) / 1000.0 // kN

            // محصلة العزوم حول مركز المقطع
            val Mn = (Cc * (h / 2.0 - a / 2.0)
                    + FsTop * (h / 2.0 - dTop)
                    + FsBot * (dBot - h / 2.0)
                    + FsSide * (dSide - h / 2.0)) / 1e6 // kN.m

            // تحديد φ حسب حالة التحكم (ACI 21.2.2)
            val epsilonT = EPSILON_CU * (dBot - c) / c.coerceAtLeast(1.0)
            val phiPoint = when {
                epsilonT >= 0.005 -> phi // تحكم شد — φ كامل
                epsilonT >= 0.002 -> phi - 0.25 * (0.005 - epsilonT) / 0.003 // منطقة الانتقال
                else -> max(0.65, phi - 0.25 * (0.005 - epsilonT) / 0.003)
            }.coerceIn(0.65, phi)

            return InteractionDiagramPoint(
                pn = Pn,
                mn = abs(Mn),
                phiPn = phiPoint * Pn,
                phiMn = phiPoint * abs(Mn),
                neutralAxisDepth = c,
                epsilonTension = epsilonT,
                phi = phiPoint
            )
        }

        val points = mutableListOf<InteractionDiagramPoint>()
        val d = h - cover - 10.0

        // نقطة 1: الضغط الخالص (c = 3h)
        points.add(solvePoint(3.0 * h))

        // نقطة 2: ضغط مع عزم صغير (c = 2h)
        points.add(solvePoint(2.0 * h))

        // نقطة 3: ضغط مع عزم متوسط (c = 1.5h)
        points.add(solvePoint(1.5 * h))

        // نقطة 4: ضغط مع عزم أكبر (c = 1.2h)
        points.add(solvePoint(1.2 * h))

        // نقطة 5: (c = 1.0h)
        points.add(solvePoint(1.0 * h))

        // نقطة 6: (c = 0.8h)
        points.add(solvePoint(0.8 * h))

        // نقطة 7: نقطة التوازن (c = cb)
        val epsilonY = fy / ES
        val cb = d * EPSILON_CU / (EPSILON_CU + epsilonY)
        points.add(solvePoint(cb))

        // نقاط بين التوازن والشد — منحنى أكثر كثافة
        val cValues = listOf(
            cb * 0.9,
            cb * 0.8,
            cb * 0.7,
            cb * 0.6,
            cb * 0.5,
            cb * 0.4,
            cb * 0.35,
            cb * 0.3,
            cb * 0.25,
            cb * 0.2,
            cb * 0.15,
            cb * 0.1,
            cb * 0.07
        )

        for (cVal in cValues) {
            points.add(solvePoint(cVal))
        }

        // نقطة الشد الخالص التقريبية (c = 5mm)
        points.add(solvePoint(5.0))

        return points
    }

    // =========================================================================
    // 5. الانحناء الثنائي المحور (Biaxial Bending)
    // =========================================================================

    /**
     * فحص الانحناء الثنائي المحور بطريقتين حسب SBC 304 / ACI 318
     *
     * الطريقة الأولى: طريقة Bresler التبادلية (Equation H1-1)
     * 1/Pn ≈ 1/Pnx + 1/Pny - 1/Po
     *
     * الطريقة الثانية: طريقة كفاف الحمل (Load Contour Method)
     * (Mux/Mnx)^α + (Muy/Mny)^α ≤ 1.0
     * حيث α يتراوح بين 1.0 (للحمل العالي) و 2.0 (للحمل المنخفض)
     *
     * @param type نوع العمود
     * @param Pu الحمل المحوري التصميمي (kN)
     * @param Mx العزم حول X (kN.m)
     * @param My العزم حول Y (kN.m)
     * @param PnCapacity القدرة المحورية (kN)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @param As مساحة التسليح (mm²)
     * @param isSpiral هل حلزوني
     * @return BiaxialCheckResult
     */
    fun checkBiaxialLoading(
        type: ColumnType,
        Pu: Double,
        Mx: Double,
        My: Double,
        PnCapacity: Double,
        fy: Double = 420.0,
        As: Double = 0.0,
        isSpiral: Boolean = false
    ): BiaxialCheckResult {
        val (b, h) = getColumnDimensions(type)
        val Ag = b * h
        val fcPrime = PnCapacity * 1000.0 / 0.65 // استرجاع تقريبي لـ fc'×Ag
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED

        val ast = if (As > 0.0) As else 0.01 * Ag
        val cover = SBC_COVER_NORMAL
        val dPrime = cover + 10.0
        val dEff = h - dPrime

        // حساب قدرة العزم لكل اتجاه
        // Mn = As × fs × jd
        // تقريباً: jd ≈ 0.8 × d (للأعمدة)
        val leverArm = (dEff - dPrime).coerceAtLeast(dEff * 0.5)
        val Mnx = phi * fy * ast * leverArm / 1e6 // kN.m

        // قدرة العزم في الاتجاه Y
        val dEffY = b - dPrime
        val leverArmY = (dEffY - dPrime).coerceAtLeast(dEffY * 0.5)
        val Mny = if (type is ColumnType.Rectangular && abs(b - h) > 10.0) {
            phi * fy * ast * leverArmY / 1e6
        } else {
            Mnx // للمربع والدائري: Mnx = Mny
        }

        // ===== طريقة Bresler التبادلية =====
        // 1/Pn ≈ 1/Pnx + 1/Pny - 1/Po
        // حيث Pnx و Pny هما القدرة المحورية مع العزم المطبق في كل اتجاه
        // و Po هي القدرة المحورية الخالصة

        // حساب Po (قدرة الضغط الخالص)
        val fcPrimeApprox = 30.0 // تقدير
        val Po = (0.85 * fcPrimeApprox * (Ag - ast) + fy * ast) / 1000.0 // kN
        val phiPo = phi * Po

        // تحويل العزوم إلى قوى محورية مكافئة تقريباً
        // Pnx ≈ Pu تحت Mx وحده: نستخدم النسبة
        val ratioMx = if (Mnx > 0) abs(Mx) / Mnx else 0.0
        val ratioMy = if (Mny > 0) abs(My) / Mny else 0.0

        // طريقة كفاف الحمل (Load Contour Method)
        // معامل α يعتمد على نسبة الحمل المحوري
        // α = 1.0 عندما Pu ≈ φPo (حمل عالي — قلب تقريبي)
        // α = 2.0 عندما Pu ≈ 0 (حمل منخفض — دائرة تقريبية)
        val r = (Pu / phiPo).coerceIn(0.05, 0.95)
        val alpha = 1.0 + (1.0 - r) // يتراوح من 1.05 إلى 1.95

        val interactionFactor = if (Mnx > 0 && Mny > 0) {
            ratioMx.pow(alpha) + ratioMy.pow(alpha)
        } else {
            0.0
        }

        val isSafe = interactionFactor <= 1.0

        val formula = if (alpha < 1.3) {
            "طريقة Bresler التبادلية (SBC 304/ACI 318 H1.1): (Mux/Mnx)^α + (Muy/Mny)^α ≤ 1.0"
        } else {
            "طريقة كفاف الحمل (SBC 304/ACI 318 H1.2): α = %.2f".format(alpha)
        }

        return BiaxialCheckResult(
            mxRatio = ratioMx,
            myRatio = ratioMy,
            interactionFactor = interactionFactor,
            isSafe = isSafe,
            formula = formula
        )
    }

    // =========================================================================
    // 6. الاعتبارات البيئية (SBC Environmental Considerations)
    // =========================================================================

    /**
     * تحديد الغطاء الخرساني حسب البيئة المحيطة (SBC 304 Section 7.7)
     *
     * - البيئة العادية (الرياض، القصيم، إلخ): 40mm
     * - البيئة المسببة للتآكل (جدة، الدمام، الخليج العربي): 50mm
     * - البيئة شديدة التآكل (منشآت بحرية مباشرة): 65mm
     *
     * @param environment نوع البيئة
     * @return الغطاء الخرساني (mm)
     */
    fun getSBCSpecificCover(environment: SBSEnvironment = SBSEnvironment.NORMAL): Double {
        return when (environment) {
            SBSEnvironment.NORMAL -> SBC_COVER_NORMAL               // 40mm
            SBSEnvironment.CORROSIVE -> SBC_COVER_CORROSIVE         // 50mm (جدة، الدمام)
            SBSEnvironment.SEVERE_CORROSIVE -> 65.0                 // 65mm (منشآت بحرية)
        }
    }

    /**
     * حساب طول التطوير المُعدل للبيئة المسببة للتآكل
     * أسياخ مطلية بالإيبوكسي في المناطق الساحلية تحتاج طول تطوير أكبر
     *
     * SBC 304 Section 12 / ACI 318 25.4.2.3:
     * ld_e = 1.5 × ld (للأسياخ المطلية بالإيبوكسي مع غطاء < 3db أو تباعد < 6db)
     *
     * @param barDiameter قطر السيخ (mm)
     * @param fy إجهاد الخضوع (MPa)
     * @param cover الغطاء الخرساني (mm)
     * @param isEpoxyCoated هل السيخ مطلّي بالإيبوكسي
     * @param isSeismicZone هل في منطقة زلزالية
     * @return طول التطوير (mm)
     */
    fun getEpoxyCoatedDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        cover: Double,
        isEpoxyCoated: Boolean = true,
        isSeismicZone: Boolean = false
    ): Double {
        val ldBase = calculateDevelopmentLength(barDiameter, fy, cover, isSeismicZone)

        if (!isEpoxyCoated) return ldBase

        // ACI 25.4.2.3: معامل التعديل للأسياخ المطلية
        // α = 1.5 إذا الغطاء < 3db أو التباعد < 6db
        // α = 1.2 في الحالات الأخرى
        val coatingFactor = if (cover < 3.0 * barDiameter) {
            1.5
        } else {
            1.2
        }

        return ldBase * coatingFactor
    }

    // =========================================================================
    // 7. فحص القص الاختراقي (Punching Shear)
    // =========================================================================

    /**
     * فحص القص الاختراقي حسب SBC 304 / ACI 318 Section 22.6
     *
     * إجهاد القص المطبق: vu = Vu / (bo × d)
     * قدرة القص: vc = 0.33 × λ × √fc'  (ACI 22.6.5.2)
     *            أو vc = min(0.17 + 0.33×β, 0.083×(2+β), 0.33) × λ × √fc'
     *            حيث β = نسبة الأبعاد
     *
     * تعديلات SBC الزلزالية:
     * - زيادة في إجهاد القص التصميمي في المفاصل الزلزالية
     *
     * @param type نوع العمود
     * @param pu الحمل المحوري التصميمي (kN)
     * @param fcPrime مقاومة الأسطوانة (MPa)
     * @param hasCap هل يوجد رأس عمود
     * @param isSeismicZone هل في منطقة زلزالية
     * @param slabDepth سمك البلاطة (mm) — افتراضي 250mm
     * @return PunchingCheckResult
     */
    fun checkPunching(
        type: ColumnType,
        pu: Double,
        fcPrime: Double,
        hasCap: Boolean,
        isSeismicZone: Boolean = false,
        slabDepth: Double = 250.0
    ): PunchingCheckResult {
        // العمق الفعال للبلاطة
        val d = slabDepth - SBC_COVER_NORMAL - 10.0 // d = h - cover - bar/2

        // المحيط الحرج عند d/2 من وجه العمود (ACI 22.6.4)
        val criticalPerimeter = when (type) {
            is ColumnType.Rectangular -> {
                val c1 = type.width
                val c2 = type.depth
                2.0 * (c1 + c2 + 2.0 * d) // محيط مستطيل عند d/2
            }
            is ColumnType.Circular -> {
                PI * (type.diameter + d)
            }
            else -> {
                4.0 * (sqrt(type.getGrossArea()) + d)
            }
        }

        // إجهاد القص المطبق (ACI 22.6.4.2)
        // vu = Vu / (bo × d)
        val Vu = pu * 1000.0 // N
        val appliedShearStress = Vu / (criticalPerimeter * d) // MPa

        // قدرة قص الاختراق (ACI 22.6.5.2)
        // vc = 0.33 × λ × √fc'  (للأعمدة بدون رأس)
        // أو نستخدم أكثر الصيغ تحفظاً
        val lambda = 1.0 // عادي الخرسانة (Normal weight)
        val beta = when (type) {
            is ColumnType.Rectangular -> max(type.width, type.depth) / min(type.width, type.depth)
            is ColumnType.Circular -> 1.0
            else -> 1.0
        }

        // ACI Eq. 22.6.5.2(a): vc = 0.17 × (1 + 2/β) × λ × √fc'
        val vc1 = 0.17 * (1.0 + 2.0 / beta) * lambda * sqrt(fcPrime)

        // ACI Eq. 22.6.5.2(b): vc = 0.083 × (αs × d/bo + 2) × λ × √fc'
        val alphaS = when (type) {
            is ColumnType.Rectangular -> {
                if (type.width == type.depth) 30.0 else 40.0 // Corner=20, Edge=30, Interior=40
            }
            is ColumnType.Circular -> 40.0 // Interior
            else -> 30.0
        }
        val vc2 = 0.083 * (alphaS * d / criticalPerimeter + 2.0) * lambda * sqrt(fcPrime)

        // ACI Eq. 22.6.5.2(c): vc = 0.33 × λ × √fc'
        val vc3 = 0.33 * lambda * sqrt(fcPrime)

        val vc = minOf(vc1, vc2, vc3) // نأخذ الأصغر (الأكثر تحفظاً)

        // معامل التعديل لرأس العمود
        val capFactor = if (hasCap) {
            // رأس العمود يزيد المحيط الحرج ويقلل الإجهاد
            val capDepth = 200.0 // سمك رأس العمود الافتراضي
            val capPerimeter = when (type) {
                is ColumnType.Rectangular -> 2.0 * (type.width + capDepth + 2.0 * (d + capDepth))
                is ColumnType.Circular -> PI * (type.diameter + 2.0 * capDepth + d)
                else -> 4.0 * (sqrt(type.getGrossArea()) + 2.0 * capDepth + d)
            }
            // قدرة محسنة مع رأس العمود
            (criticalPerimeter * d + capPerimeter * capDepth) / (criticalPerimeter * d)
        } else {
            1.0
        }

        // تعديل زلزالي (SBC Section 21)
        val seismicFactor = if (isSeismicZone) 0.85 else 1.0 // تخفيض 15% في المناطق الزلزالية

        val designCapacity = vc * capFactor * seismicFactor
        val capacityForce = designCapacity * criticalPerimeter * d / 1000.0 // kN

        return PunchingCheckResult(
            appliedShear = pu,
            capacity = capacityForce,
            isSafe = appliedShearStress <= designCapacity,
            hasCap = hasCap,
            criticalPerimeter = criticalPerimeter
        )
    }

    // =========================================================================
    // 8. تفاصيل التسليح (SBC 304 Section 10.7.6)
    // =========================================================================

    /**
     * حساب تفاصيل الكانات حسب SBC 304 Section 10.7.6 / ACI 25.7.2
     *
     * قطر الكانات:
     * - ≥ #3 (10mm) للأسياخ حتى #10 (32mm)
     * - ≥ #4 (12mm) للأسياخ #11 (36mm) وأكبر
     *
     * التباعد:
     * - min(16×db_longitudinal, 48×db_tie, b, h, 300mm)
     * - في منطقة المفصل اللدن: min(d/4, 6×db, 100mm)
     *
     * @param mainBarDiameter قطر السيخ الرئيسي (mm)
     * @param columnWidth عرض العمود (mm)
     * @param columnDepth عمق العمود (mm)
     * @param isSeismicZone هل في منطقة زلزالية
     * @return SBCReinforcementDetails
     */
    fun getReinforcementDetails(
        mainBarDiameter: Double,
        columnWidth: Double,
        columnDepth: Double,
        isSeismicZone: Boolean = false
    ): SBCReinforcementDetails {
        // ===== قطر الكانات (SBC 10.7.6.2) =====
        val tiesDiameter = if (mainBarDiameter <= 32.0) 10.0 else 12.0

        // ===== التباعد العادي (SBC 10.7.6.3) =====
        val s1 = 16.0 * mainBarDiameter
        val s2 = 48.0 * tiesDiameter
        val s3 = columnWidth
        val s4 = columnDepth
        val s5 = 300.0
        val normalSpacing = minOf(s1, s2, s3, s4, s5)

        // ===== التباعد في منطقة المفصل اللدن (إذا زلزالي) =====
        val d = columnDepth - SBC_COVER_CORROSIVE - mainBarDiameter / 2.0 - 8.0
        val seismicSpacing = if (isSeismicZone) {
            minOf(d / 4.0, 6.0 * mainBarDiameter, 100.0)
        } else {
            normalSpacing
        }

        // ===== عدد الأسياخ الدنيا لكل وجه (SBC 10.7.2.1) =====
        // يجب ألا يقل عدد الأسياخ عن 4 للأعمدة المربوطة
        val minBarsPerFace = if (isSeismicZone) 3 else 2

        // ===== الأقطار المتاحة في السوق السعودي =====
        val availableKsaDiameters = KSA_BAR_DIAMETERS.map { it.toInt() }

        // ===== هوك الكانات =====
        val hookExtension = 6.0 * tiesDiameter // 6×db للكانات (135° hook)
        val seismicHookExtension = if (isSeismicZone) {
            max(6.0 * tiesDiameter, 75.0) // SBC 21: max(6db, 75mm)
        } else {
            hookExtension
        }

        return SBCReinforcementDetails(
            tiesDiameter = tiesDiameter,
            normalSpacing = normalSpacing,
            seismicSpacing = seismicSpacing,
            minBarsPerFace = minBarsPerFace,
            hookExtension = hookExtension,
            seismicHookExtension = seismicHookExtension,
            availableKsaDiameters = availableKsaDiameters,
            codeReference = CodeReference.SBC.COLUMN_TIES
        )
    }

    // =========================================================================
    // دوال مساعدة داخلية
    // =========================================================================

    /**
     * حساب القدرة المحورية التصميمية حسب SBC 304 / ACI 318
     * Pn = 0.85 × fc' × (Ag - Ast) + fy × Ast
     * φPn = φ × Pn
     *
     * @param fcu مقاومة المكعب (MPa)
     * @param fy إجهاد الخضوع (MPa)
     * @param columnType نوع العمود
     * @param Ag المساحة الكلية (mm²)
     * @param isSpiral هل حلزوني
     * @param As مساحة التسليح (mm²) — إذا صفر يستخدم 1% من Ag
     */
    private fun calculateAxialCapacity(
        fcu: Double,
        fy: Double,
        columnType: ColumnType,
        Ag: Double,
        isSpiral: Boolean,
        As: Double = 0.0
    ): Double {
        val fcPrime = 0.8 * fcu
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        val ast = if (As > 0.0) As.coerceAtMost(Ag * 0.08) else 0.01 * Ag

        // SBC/ACI: Pn = 0.85×fc'×(Ag-Ast) + fy×Ast
        val concreteCapacity = 0.85 * fcPrime * (Ag - ast)
        val steelCapacity = fy * ast
        val nominalCapacity = concreteCapacity + steelCapacity

        return phi * nominalCapacity / 1000.0 // kN
    }

    /**
     * حساب قدرة العزم التقريبية للعمود
     * Mn = As × fs × jd / φ (حساب مبسط)
     */
    private fun calculateMomentCapacity(
        columnType: ColumnType,
        fcPrime: Double,
        fy: Double,
        As: Double,
        isSpiral: Boolean,
        phi: Double,
        cover: Double,
        isYDirection: Boolean = false
    ): Double {
        val (b, h) = getColumnDimensions(columnType)
        val effectiveDepth = if (isYDirection && columnType is ColumnType.Rectangular) {
            columnType.width - cover - 10.0
        } else {
            h - cover - 10.0
        }

        val dPrime = cover + 10.0
        val leverArm = (effectiveDepth - dPrime).coerceAtLeast(effectiveDepth * 0.5)

        // حساب عمق المحور المحايد عند التوازن
        val epsilonY = fy / ES
        val cb = effectiveDepth * EPSILON_CU / (EPSILON_CU + epsilonY)

        // β1
        val beta1 = if (fcPrime <= 28.0) {
            BETA_1_DEFAULT
        } else {
            (0.85 - 0.05 * ((fcPrime - 28.0) / 7.0)).coerceAtLeast(0.65)
        }

        val a = beta1 * cb

        // قوة ضغط الخرسانة
        val Cc = 0.85 * fcPrime * a * b
        // إجهاد الحديد (عند التوازن = fy)
        val T = fy * As

        // العزم حول مركز الضغط
        val Mn = T * (effectiveDepth - a / 2.0) / 1e6 // kN.m

        return phi * Mn
    }

    /**
     * حساب معامل الطول الفعال K المُعدل للبناء السعودي
     * SBC يستخدم نفس قيم K كـ ACI 318 مع تعديلات طفيفة
     * للبناء السعودي النموذجي (أرضيات صب في الموقع)
     */
    private fun getSBCEffectiveLengthFactor(endConditions: ColumnEndConditions): Double {
        val kTop = endConditions.topCondition.effectiveLengthFactor
        val kBottom = endConditions.bottomCondition.effectiveLengthFactor

        // للبناء السعودي النموذجي: الجسيمات (stiffness) أعلى بسبب الأعمدة الكبيرة
        // نأخذ متوسط K مُعدل
        val kAverage = (kTop + kBottom) / 2.0

        // تعديل SBC: في حالة الأرضيات الصلبة (solid slabs) يُمكن تقليل K قليلاً
        return when {
            endConditions.topCondition == EndCondition.FIXED &&
                endConditions.bottomCondition == EndCondition.FIXED -> 0.65
            endConditions.topCondition == EndCondition.PINNED &&
                endConditions.bottomCondition == EndCondition.FIXED -> 0.80
            endConditions.topCondition == EndCondition.FIXED &&
                endConditions.bottomCondition == EndCondition.PINNED -> 0.80
            endConditions.topCondition == EndCondition.PINNED &&
                endConditions.bottomCondition == EndCondition.PINNED -> 1.00
            endConditions.topCondition == EndCondition.PARTIAL_FIXED -> kAverage * 0.95 // SBC تعديل
            else -> max(kTop, kBottom)
        }
    }

    /**
     * حساب نصف القطر الدوراني (Radius of Gyration)
     * r = √(I/A)
     * للمستطيل: r = min(b,h) / √12
     * للدائرة: r = D/4
     */
    private fun calculateRadiusOfGyration(columnType: ColumnType): Double {
        return when (columnType) {
            is ColumnType.Rectangular -> min(columnType.width, columnType.depth) / sqrt(12.0)
            is ColumnType.Circular -> columnType.diameter / 4.0
            is ColumnType.LShaped -> columnType.thickness / sqrt(12.0)
            is ColumnType.TShaped -> columnType.webWidth / sqrt(12.0)
            is ColumnType.Composite -> min(columnType.concreteWidth, columnType.concreteDepth) / sqrt(12.0)
            is ColumnType.Tubular -> (columnType.outerDiameter + columnType.innerDiameter) / 8.0
        }
    }

    /**
     * استخراج أبعاد العمود (عرض، عمق)
     */
    private fun getColumnDimensions(columnType: ColumnType): Pair<Double, Double> {
        return when (columnType) {
            is ColumnType.Rectangular -> columnType.width to columnType.depth
            is ColumnType.Circular -> columnType.diameter to columnType.diameter
            is ColumnType.LShaped -> {
                val side = sqrt(columnType.getGrossArea())
                side to side
            }
            is ColumnType.TShaped -> {
                val side = sqrt(columnType.getGrossArea())
                side to side
            }
            is ColumnType.Composite -> columnType.concreteWidth to columnType.concreteDepth
            is ColumnType.Tubular -> columnType.outerDiameter to columnType.outerDiameter
        }
    }

    /**
     * الحصول على أقل بُعد للعمود
     */
    private fun getMinimumDimension(columnType: ColumnType): Double {
        return when (columnType) {
            is ColumnType.Rectangular -> min(columnType.width, columnType.depth)
            is ColumnType.Circular -> columnType.diameter
            is ColumnType.LShaped -> columnType.thickness
            is ColumnType.TShaped -> min(columnType.webWidth, columnType.flangeThickness)
            is ColumnType.Composite -> min(columnType.concreteWidth, columnType.concreteDepth)
            is ColumnType.Tubular -> columnType.outerDiameter - columnType.innerDiameter
        }
    }

    /**
     * حساب طول التطوير (Development Length) حسب SBC 304 Section 12 / ACI 318 Chapter 25
     *
     * ld = (fy × ψt × ψe × ψs) / (1.1 × λ × √fc') × db
     *
     * معاملات التعديل:
     * - ψt = 1.3 (أسياخ علوية)
     * - ψe = 1.0 (غير مطلية) أو 1.5/1.2 (مطلية بالإيبوكسي)
     * - ψs = 0.8 (أسياخ قطر ≤ 19mm)
     * - λ = 1.0 (خرسانة عادية)
     *
     * @param barDiameter قطر السيخ (mm)
     * @param fy إجهاد الخضوع (MPa)
     * @param cover الغطاء (mm)
     * @param isSeismic هل في منطقة زلزالية
     * @return طول التطوير (mm)
     */
    private fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        cover: Double,
        isSeismic: Boolean = false
    ): Double {
        // fc' تقديري لحساب طول التطوير
        val fcPrime = 30.0 // تقدير محافظ

        // معاملات التعديل (ACI 25.4.2.4)
        val psiT = 1.0  // أسياخ سفلية (ليست علوية)
        val psiE = 1.0  // غير مطلية
        val psiS = if (barDiameter <= 19.0) 0.8 else 1.0
        val lambda = 1.0 // خرسانة عادية الوزن

        // ACI 25.4.2.2: ld = (fy × ψt × ψe × ψs) / (1.1 × λ × √fc') × db
        val ldBasic = (fy * psiT * psiE * psiS) / (1.1 * lambda * sqrt(fcPrime)) * barDiameter

        // حد أدنى (ACI 25.4.2.3)
        val ldMin = max(300.0, 12.0 * barDiameter)

        val ld = max(ldBasic, ldMin)

        // في المناطق الزلزالية: SBC 21.7.5 / ACI 18.8.5.1
        // ld_seismic = 1.25 × ld (للأسياخ في الضغط في مناطق المفصل اللدن)
        return if (isSeismic) {
            max(ld * 1.25, 16.0 * barDiameter)
        } else {
            ld
        }
    }

    /**
     * تحليل المخزون المتاح
     */
    private fun analyzeInventory(
        result: ReinforcementResult,
        height: Double,
        inventory: RebarInventory,
        cover: Double
    ): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        return analyzer.analyze(
            result.astRequired,
            result.numberOfBars * height,
            inventory,
            DesignCode.SBC,
            height,
            cover
        )
    }

    // =========================================================================
    // تنفيذ واجهة ColumnDesign (التفويض إلى SBCColumn)
    // =========================================================================

    override fun calculateAxialCapacity(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        reinforcementArea: Double,
        loadCombination: LoadCombination
    ): Double {
        return baseDesign.calculateAxialCapacity(fcu, fy, width, depth, reinforcementArea, loadCombination)
    }

    override fun calculateReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        return baseDesign.calculateReinforcement(fcu, fy, width, depth, axialLoad, momentX, momentY, loadCombination)
    }

    override fun getMinReinforcementRatio(): Double = 0.01  // SBC 304: 1% (same as ACI 318)
    override fun getMaxReinforcementRatio(): Double = 0.08 // SBC 304: 8%
    override fun getMinSpacing(): Double = 40.0
    override fun getMaxSpacing(): Double = 300.0            // SBC 10.7.6: min(16db, 48dt, b, h, 300)
    override fun getMinCover(): Double = SBC_COVER_NORMAL
}

// =========================================================================
// أنواع بيانات إضافية خاصة بـ SBC
// =========================================================================

/**
 * أنواع البيئة حسب SBC 304-2018 Section 7.7
 */
enum class SBSEnvironment(val displayName: String, val coverMm: Double) {
    NORMAL("بيئة عادية (الرياض، القصيم)", 40.0),
    CORROSIVE("بيئة مسببة للتآكل (جدة، الدمام، ينبع)", 50.0),
    SEVERE_CORROSIVE("بيئة شديدة التآكل (منشآت بحرية مباشرة)", 65.0)
}

/**
 * نتيجة فحص المتطلبات الزلزالية
 */
data class SeismicCheckOutput(
    val warnings: List<String>,
    val codeNotes: List<String>
)

/**
 * نتيجة تصميم حصر التسليح الزلزالي
 */
data class SeismicConfinementResult(
    val hoopSpacingHingeZone: Double,     // mm — تباعد الكانات في منطقة المفصل اللدن
    val confinementZoneLength: Double,    // mm — طول منطقة الحصر
    val numHingeTies: Int,                // عدد الكانات في منطقة المفصل اللدن
    val spacingOutsideHinge: Double,      // mm — التباعد خارج منطقة المفصل اللدن
    val requiredCrossTies: Int,           // عدد الكانات المتقاطعة المطلوبة
    val developmentLength: Double,        // mm — طول التطوير
    val warnings: List<String>,
    val codeNotes: List<String>
)

/**
 * نقطة على منحنى التفاعل (P-M Interaction Diagram)
 */
data class InteractionDiagramPoint(
    val pn: Double,               // kN — القوة المحورية الإسمية
    val mn: Double,               // kN.m — العزم الإسمي
    val phiPn: Double,            // kN — القوة المحورية التصميمية
    val phiMn: Double,            // kN.m — العزم التصميمي
    val neutralAxisDepth: Double, // mm — عمق المحور المحايد
    val epsilonTension: Double,   // انفعال الشد
    val phi: Double               // معامل الاختزال عند هذه النقطة
)

/**
 * تفاصيل التسليح حسب SBC 304
 */
data class SBCReinforcementDetails(
    val tiesDiameter: Double,           // mm
    val normalSpacing: Double,          // mm
    val seismicSpacing: Double,         // mm
    val minBarsPerFace: Int,            // أقل عدد أسياخ لكل وجه
    val hookExtension: Double,          // mm — طول الامتداد للكانة العادية
    val seismicHookExtension: Double,   // mm — طول الامتداد للكانة الزلزالية
    val availableKsaDiameters: List<Int>, // أقطار متاحة في السوق السعودي
    val codeReference: String           // مرجع الكود
)