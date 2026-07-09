package com.civileg.app.domain.calculations.sbc

import android.os.Parcelable
import com.civileg.app.domain.entities.*
import kotlinx.parcelize.Parcelize
import kotlin.math.*

/**
 * محرك تصميم المنشآت المعدنية حسب SBC 306 (الكود السعودي للمنشآت المعدنية)
 * SBC 306 Steel Design Engine
 *
 * SBC 306 مبني على AISC 360-16 مع تعديلات خاصة بالمملكة العربية السعودية تشمل:
 * - معاملات التحميل المختلفة (SBC 301 للزلازل بدلاً من ASCE 7)
 * - أحمال الرياح حسب الكود السعودي للرياح
 * - درجات الصلب الأوروبية (S275, S355) إلى جانب ASTM (A36, A572)
 * - متطلبات إضافية للحماية من التآكل في المناطق الساحلية
 * - متطلبات الحماية من الحريق حسب الكود السعودي
 * - أقل سماكة للصفائح في البيئات المؤكسدة
 *
 * المراجع:
 * - SBC 306: Saudi Building Code for Steel Structures
 * - AISC 360-16: Specification for Structural Steel Buildings
 * - SBC 301: Saudi Building Code for Seismic Loads
 * - EN 10025: Hot rolled products of structural steels
 *
 * @author Civileg Engineering Team
 */
class SBCSteelDesignEngine {

    companion object {
        // ===================== معاملات المقاومة حسب SBC 306 (تتبع AISC 360 ما لم يُعدّل) =====================
        const val PHI_TENSION = 0.90
        const val PHI_COMPRESSION = 0.90
        const val PHI_FLEXURE = 0.90
        const val PHI_SHEAR = 0.90

        // ===================== ثوابت مرونة الصلب =====================
        const val E_STEEL = 200000.0   // MPa - معامل مرونة الصلب
        const val G_STEEL = 76923.0    // MPa - معامل القص

        // ===================== معاملات التحميل حسب SBC 306 =====================
        const val SBC_DEAD_FACTOR = 1.2
        const val SBC_LIVE_FACTOR = 1.6
        const val SBC_WIND_FACTOR = 0.5   // معامل الرياح في التوافقات المركبة
        const val SBC_SEISMIC_FACTOR = 1.0

        // ===================== متطلبات البيئة حسب SBC 306 =====================
        const val SBC_COASTAL_CORROSION_ALLOWANCE = 1.1   // تخفيض 10% في المناطق الساحلية
        const val SBC_MIN_THICKNESS_CORROSIVE = 6.0       // mm - أقل سماكة صفائح في البيئات المؤكسدة

        // ===================== ثوابت إضافية =====================
        private const val PHI_BEARING_CONCRETE = 0.65   // معامل مقاومة تحمل الخرسانة (AISC J8 / SBC 306)
        private const val PHI_TENSION_BOLT = 0.75      // معامل مقاومة شد البراغي (AISC J3.2)
        private const val PHI_WELD = 0.75              // معامل مقاومة اللحام (AISC J2.4)
        private const val MIN_PLATE_THICKNESS = 12.0   // mm - أقل سماكة للوح القاعدة
    }

    // =====================================================================
    // أنواع البيانات المحلية (Local Data Types)
    // تعريف محلي لنتائج التصميم - تتبع منهجية AISC 360-16 كما تبناه SBC 306
    // =====================================================================

    /**
     * تصنيف المقطع حسب SBC 306 Table B4.1 (يتبع AISC 360-16 B4)
     */
    @Parcelize
    enum class SectionClassification(val displayNameAr: String, val codeRef: String) : Parcelable {
        COMPACT("مدمج", "SBC 306 Table B4.1a / AISC 360-B4.1"),
        NONCOMPACT("غير مدمج", "SBC 306 Table B4.1b / AISC 360-B4.1"),
        SLENDER("نحيف", "SBC 306 Table B4.1c / AISC 360-B4.1")
    }

    /**
     * نتيجة تصميم الكمرة حسب SBC 306
     */
    @Parcelize
    data class SteelFlexuralResult(
        val phiMn: Double,               // kN.m - قدرة الانحناء التصميمية
        val Mn_nominal: Double,          // kN.m - قدرة الانحناء الإسمية
        val phiVn: Double,               // kN - قدرة القص التصميمية
        val Vu: Double,                  // kN - القوة القصية المطبقة
        val Mu: Double,                  // kN.m - العزم المطبق
        val momentRatio: Double,         // نسبة الاستغلال للانحناء
        val shearRatio: Double,          // نسبة الاستغلال للقص
        val deflection: Double,              // mm - الانحراف المحسوب
        val deflectionAllowable: Double,     // mm - الانحراف المسموح
        val deflectionRatio: Double,     // نسبة الاستغلال للانحراف
        val ltbCheck: LTBCheckResult?,   // فحص الالتواء البعرضي
        val sectionClassification: SectionClassification,
        val isSafe: Boolean,
        val warnings: List<String>,
        val codeNotes: List<String>
    ) : Parcelable

    /**
     * نتيجة فحص الالتواء البعرضي (Lateral-Torsional Buckling)
     */
    @Parcelize
    data class LTBCheckResult(
        val Lb: Double,                  // mm - طول الدعم البعرضي
        val Lp: Double,                  // mm - الحد الأدنى لطول الدعم البعرضي (المنطقة البلاستيكية)
        val Lr: Double,                  // mm - الحد الأقصى (المنطقة اللدنة)
        val Mn_lt: Double,               // kN.m - قدرة الانحناء مع الالتواء البعرضي
        val phiMn_lt: Double,            // kN.m - القدرة التصميمية
        val isGoverning: Boolean,        // هل الالتواء البعرضي هو الحاكم
        val ratio: Double
    ) : Parcelable

    /**
     * نتيجة تصميم العمود حسب SBC 306
     */
    @Parcelize
    data class SteelCompressionResult(
        val phiPn: Double,               // kN - قدرة الضغط التصميمية
        val Pn: Double,                  // kN - قدرة الضغط الإسمية
        val Fcr: Double,                 // MPa - إجهاد الانبعاج الحرج
        val slendernessRatio: Double,    // نسبة النحافة
        val isSlender: Boolean,          // هل العمود نحيف؟
        val phiMn_x: Double,             // kN.m - قدرة الانحناء حول X
        val phiMn_y: Double,             // kN.m - قدرة الانحناء حول Y
        val sectionClassification: SectionClassification,
        val isSafe: Boolean,
        val warnings: List<String>,
        val codeNotes: List<String>
    ) : Parcelable

    /**
     * نتيجة فحص التحميل المركب (Axial + Flexure) حسب SBC 306 / AISC H1
     */
    @Parcelize
    data class CombinedLoadingResult(
        val interactionRatio: Double,    // نسبة التفاعل (يجب ≤ 1.0)
        val pr: Double,                  // Pu / φPn
        val mrx: Double,                 // Mux / φMnx
        val mry: Double,                 // Muy / φMny
        val equation: String,            // H1-1a أو H1-1b
        val phiPn: Double,               // kN - قدرة الضغط
        val phiMnx: Double,              // kN.m - قدرة الانحناء حول X
        val phiMny: Double,              // kN.m - قدرة الانحناء حول Y
        val isSafe: Boolean,
        val warnings: List<String>,
        val codeNotes: List<String>
    ) : Parcelable

    /**
     * نتيجة تصميم الكوبري حسب SBC 306
     */
    @Parcelize
    data class SteelBracingResult(
        val phiPn: Double,               // kN - قدرة الضغط/الشد التصميمية
        val Pn: Double,                  // kN - القدرة الإسمية
        val slendernessRatio: Double,    // نسبة النحافة
        val isTension: Boolean,          // هل يعمل كعنصر شد؟
        val connectionCapacity: Double,  // kN - قدرة الوصلة
        val isSafe: Boolean,
        val warnings: List<String>,
        val codeNotes: List<String>
    ) : Parcelable

    /**
     * توافقات التحميل حسب SBC 306
     */
    @Parcelize
    data class SBCLoadCombination(
        val name: String,                // اسم التوافقة
        val factorD: Double,             // معامل الحمل الميت
        val factorL: Double,             // معامل الحمل الحي
        val factorW: Double,             // معامل الرياح
        val factorE: Double,             // معامل الزلازل
        val Pu: Double,                  // kN - المحور الناتج
        val Mu: Double                   // kN.m - العزم الناتج (تقريبي)
    ) : Parcelable

    /**
     * نتيجة تصميم القاعدة المعدنية حسب SBC 306
     */
    @Parcelize
    data class BasePlateResult(
        val plateWidth: Double,          // mm - عرض اللوح (B)
        val plateLength: Double,         // mm - طول اللوح (L)
        val plateThickness: Double,      // mm - سماكة اللوح (tp)
        val anchorBolts: Int,            // عدد البراغي الارتكازية
        val anchorDiameter: Double,      // mm - قطر البرغي
        val anchorLength: Double,        // mm - طول التثبيت
        val isSafe: Boolean,
        val warnings: List<String>,
        val codeNotes: List<String>
    ) : Parcelable

    /**
     * أنواع البيئة حسب SBC 306
     */
    @Parcelize
    enum class SBSEnvironment(val displayNameAr: String, val description: String) : Parcelable {
        NORMAL("عادي", "بيئة عادية بدون تأثيرات خاصة"),
        COASTAL("ساحلي", "مناطق ساحلية مع رطوبة عالية وملوحة"),
        INDUSTRIAL("صناعي", "مناطق صناعية مع أبخرة كيميائية"),
        MARINE("بحري", "بيئة بحرية مباشرة مع رذاذ ملحي")
    }

    /**
     * نتيجة فحص الحماية من التآكل حسب SBC 306
     */
    @Parcelize
    data class CorrosionCheckResult(
        val requiredProtection: String,  // الحماية المطلوبة
        val isAdequate: Boolean,         // هل الحماية الحالية كافية؟
        val recommendedCoating: String,  // الطلاء الموصى به
        val notes: List<String>          // ملاحظات
    ) : Parcelable

    // =====================================================================
    // 1. تصميم الكمرات - SBC 306 Chapter F (يتبع AISC 360-16)
    // =====================================================================

    /**
     * تصميم كمرة معدنية حسب SBC 306
     * SBC 306 يتبع AISC 360-16 Chapter F مع التعديلات السعودية التالية:
     * - تطبيق معامل التآكل الساحلي إذا كانت isCoastal = true
     * - فحص نسبة العرض للارتفاع للعناصر الزلزالية حسب SBC 301
     *
     * @param section نوع المقطع المعدني
     * @param grade درجة الصلب
     * @param Mu العزم التصميمي (kN.m)
     * @param Vu القوة القصية التصميمية (kN)
     * @param w_LL الحمل الحي الموزع (kN/m) - للانحراف
     * @param span البحر (mm)
     * @param Lb طول الدعم البعرضي (mm) - 0 يعني مدعوم بالكامل
     * @param Cb معامل التحميل البعرضي (1.0 للتحميل المنتظم)
     * @param isCoastal هل يقع في منطقة ساحلية؟ (متطلب SBC 306)
     * @param isSeismic هل العنصر زلزالي؟ (متطلب SBC 301)
     * @return نتيجة تصميم الكمرة
     */
    fun designBeam(
        section: SteelSectionType,
        grade: SteelGrade,
        Mu: Double, Vu: Double,
        w_LL: Double, span: Double,
        Lb: Double = 0.0,
        Cb: Double = 1.0,
        isCoastal: Boolean = false,
        isSeismic: Boolean = false
    ): SteelFlexuralResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("SBC 306: تصميم الكمرات (Chapter F - يتبع AISC 360-16)")
        codeNotes.add("درجة الصلب: ${grade.displayName} (Fy=${grade.fy} MPa, Fu=${grade.fu} MPa)")

        // خصائص المقطع
        val d = section.depth          // mm - العمق الكلي
        val bf = section.width          // mm - عرض الشفة
        val tw = section.webThickness   // mm - سماكة الجذع
        val tf = section.flangeThickness// mm - سماكة الشفة
        val Ag = section.area           // mm² - مساحة المقطع
        val Ix = section.ix             // mm⁴ - عزم القصور حول X
        val Sx = section.sx             // mm³ - معامل المقطع المرن
        val Zx = section.zx             // mm³ - معامل المقطع اللدن
        val rx = section.rx             // mm - نصف القطر الدوراني

        codeNotes.add("المقطع: ${section.sectionName}")
        codeNotes.add("Ag=${String.format("%.0f", Ag)} mm², Ix=${String.format("%.0f", Ix)} mm⁴, Zx=${String.format("%.0f", Zx)} mm³, Sx=${String.format("%.0f", Sx)} mm³")

        // 1. تصنيف المقطع حسب SBC 306 Table B4.1 (يتبع AISC 360-16)
        val classification = classifySection(section, grade)
        codeNotes.add("تصنيف المقطع: ${classification.displayNameAr} (${classification.codeRef})")

        // 2. قدرة الانحناء - SBC 306 Chapter F2 (يتبع AISC 360-16 F2)
        val Mn_nominal = calculateNominalMomentCapacity(Zx, Sx, grade.fy, d, bf, tw, tf, classification)
        var Mn_design = Mn_nominal

        // 3. فحص الالتواء البعرضي (LTB) - AISC 360-16 F2 / SBC 306 F2
        val ltbResult = if (Lb > 0 && rx > 0) {
            calculateLTBCheck(Lb, rx, bf, d, tw, tf, Sx, Zx, Mn_nominal, grade.fy, Cb)
        } else null

        if (ltbResult != null && ltbResult.isGoverning) {
            Mn_design = min(Mn_design, ltbResult.Mn_lt)
            codeNotes.add("الالتواء البعرضي حاكم: φMn_lt=${String.format("%.1f", ltbResult.phiMn_lt)} kN.m")
        }

        // تطبيق معامل التآكل الساحلي (SBC 306 خاص)
        var coastalFactor = 1.0
        if (isCoastal) {
            coastalFactor = 1.0 / SBC_COASTAL_CORROSION_ALLOWANCE
            Mn_design *= coastalFactor
            warnings.add("تم تطبيق معامل التآكل الساحلي SBC 306: تخفيض ${(SBC_COASTAL_CORROSION_ALLOWANCE - 1) * 100}%.0f في القدرة")
            codeNotes.add("معامل التآكل الساحلي (SBC 306): ${SBC_COASTAL_CORROSION_ALLOWANCE}")
        }

        val phiMn = PHI_FLEXURE * Mn_design
        val momentRatio = Mu / phiMn.coerceAtLeast(0.001)

        // 4. قدرة القص - AISC 360-16 G2 / SBC 306 G2
        val Aw = d * tw  // mm² - مساحة الجذع
        val Vn = 0.6 * grade.fy * Aw / 1000.0  // kN
        var phiVn = PHI_SHEAR * Vn
        if (isCoastal) phiVn *= coastalFactor

        val shearRatio = Vu / phiVn.coerceAtLeast(0.001)

        // 5. فحص الانحراف - AISC 360-16 Chapter L / SBC 306
        val L_m = span / 1000.0
        val I_m4 = Ix / 1e12
        val deflection = 5 * w_LL * L_m.pow(4) / (384 * E_STEEL / 1e6 * I_m4.coerceAtLeast(1e-12)) * 1000  // mm
        val deflectionAllowable = span / 360.0
        val deflectionRatio = deflection / deflectionAllowable.coerceAtLeast(0.001)

        // 6. فحص متطلبات الزلازل (SBC 301) - نسبة العرض للارتفاع
        if (isSeismic) {
            val bfOver2tf = if (tf > 0) bf / (2 * tf) else Double.MAX_VALUE
            val seismicLimit = 52.0 * sqrt(E_STEEL / grade.fy)
            if (bfOver2tf > seismicLimit) {
                warnings.add("نسبة الشفة تجاوزت الحد الزلزالي (SBC 301): bf/2tf=${String.format("%.1f", bfOver2tf)} > ${String.format("%.1f", seismicLimit)}")
            } else {
                codeNotes.add("فحص الزلازل (SBC 301): bf/2tf=${String.format("%.1f", bfOver2tf)} ≤ ${String.format("%.1f", seismicLimit)} ✓")
            }

            val hOverTw = if (tw > 0) (d - 2 * tf) / tw else Double.MAX_VALUE
            val seismicWebLimit = if (grade.fy <= 345) 1.49 * sqrt(E_STEEL / grade.fy) else 1.12 * sqrt(E_STEEL / grade.fy)
            if (hOverTw > seismicWebLimit) {
                warnings.add("نسبة الجذع تجاوزت الحد الزلزالي (SBC 301): h/tw=${String.format("%.1f", hOverTw)} > ${String.format("%.1f", seismicWebLimit)}")
            }

            // فحص أقل سماكة في البيئة الساحلية (SBC 306)
            if (isCoastal) {
                val minThk = min(tw, tf)
                if (minThk < SBC_MIN_THICKNESS_CORROSIVE) {
                    warnings.add("أقل سماكة (${String.format("%.1f", minThk)} mm) أقل من الحد الأدنى في البيئة المؤكسدة (${String.format("%.0f", SBC_MIN_THICKNESS_CORROSIVE)} mm) حسب SBC 306")
                }
            }
        }

        // التحذيرات
        if (momentRatio > 1.0) warnings.add("العزم يتجاوز القدرة - اختر مقطع أكبر (Mu/φMn=${String.format("%.2f", momentRatio)})")
        if (shearRatio > 1.0) warnings.add("القص يتجاوز القدرة - زِد سماكة الجذع (Vu/φVn=${String.format("%.2f", shearRatio)})")
        if (deflectionRatio > 1.0) warnings.add("الانحراف يتجاوز الحد المسموح (δ/δ_allow=${String.format("%.2f", deflectionRatio)})")

        codeNotes.add("φMn=${String.format("%.1f", phiMn)} kN.m, φVn=${String.format("%.1f", phiVn)} kN")
        codeNotes.add("δ=${String.format("%.2f", deflection)} mm, δ_allow=${String.format("%.2f", deflectionAllowable)} mm")
        if (isCoastal) codeNotes.add("SBC 306: متطلبات الحماية من التآكل الساحلية مُطبّقة")
        if (isSeismic) codeNotes.add("SBC 301: متطلبات الأداء الزلزالي مُطبّقة")

        val isSafe = momentRatio <= 1.0 && shearRatio <= 1.0 && deflectionRatio <= 1.0

        return SteelFlexuralResult(
            phiMn = phiMn,
            Mn_nominal = Mn_nominal,
            phiVn = phiVn,
            Vu = Vu,
            Mu = Mu,
            momentRatio = momentRatio,
            shearRatio = shearRatio,
            deflection = deflection,
            deflectionAllowable = deflectionAllowable,
            deflectionRatio = deflectionRatio,
            ltbCheck = ltbResult,
            sectionClassification = classification,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // =====================================================================
    // 2. تصميم الأعمدة - SBC 306 Chapter E (يتبع AISC 360-16)
    // =====================================================================

    /**
     * تصميم عمود معدني حسب SBC 306
     * SBC 306 يتبع AISC 360-16 Chapter E مع التعديلات السعودية
     *
     * @param section نوع المقطع المعدني
     * @param grade درجة الصلب
     * @param Pu القوة المحورية التصميمية (kN)
     * @param Mux العزم حول X (kN.m)
     * @param Muy العزم حول Y (kN.m)
     * @param Kx معامل الطول الفعال حول X
     * @param Ky معامل الطول الفعال حول Y
     * @param Lx طول العمود حول X (mm)
     * @param Ly طول العمود حول Y (mm)
     * @param isCoastal هل في منطقة ساحلية؟
     * @param isSeismic هل العنصر زلزالي؟
     * @return نتيجة تصميم العمود
     */
    fun designColumn(
        section: SteelSectionType,
        grade: SteelGrade,
        Pu: Double, Mux: Double, Muy: Double,
        Kx: Double, Ky: Double,
        Lx: Double, Ly: Double,
        isCoastal: Boolean = false,
        isSeismic: Boolean = false
    ): SteelCompressionResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("SBC 306: تصميم الأعمدة (Chapter E - يتبع AISC 360-16)")
        codeNotes.add("درجة الصلب: ${grade.displayName} (Fy=${grade.fy} MPa, Fu=${grade.fu} MPa)")

        // خصائص المقطع
        val d = section.depth
        val bf = section.width
        val tw = section.webThickness
        val tf = section.flangeThickness
        val Ag = section.area
        val rx = section.rx

        // حساب ry تقريبي (0.3 × d للمقاطع I)
        val ry = when (section) {
            is SteelSectionType.ISection -> sqrt((bf * bf * bf * tf * 2.0 + (d - 2 * tf) * tw * tw * tw / 12.0) / Ag)
            is SteelSectionType.CHS -> section.outerDiameter / 4.0
            is SteelSectionType.RHS -> min(section.width, section.height) / 4.0
            else -> rx * 0.6
        }

        val Zx = section.zx
        val Zy = when (section) {
            is SteelSectionType.ISection -> Zx * 0.35
            is SteelSectionType.RHS -> {
                val Iy = min(section.width, section.height).let { w ->
                    val t = section.thickness
                    val hw = section.height - 2 * t
                    val bw = section.width - 2 * t
                    (section.height * section.width.pow(3) - hw * bw.pow(3)) / 12.0
                }
                if (Iy > 0 && d > 0) Iy / (section.width / 2.0) else Zx * 0.5
            }
            is SteelSectionType.CHS -> Zx
            else -> Zx * 0.5
        }

        codeNotes.add("المقطع: ${section.sectionName}")
        codeNotes.add("Ag=${String.format("%.0f", Ag)} mm², rx=${String.format("%.1f", rx)} mm, ry=${String.format("%.1f", ry)} mm")

        // تصنيف المقطع
        val classification = classifySection(section, grade)
        codeNotes.add("تصنيف المقطع: ${classification.displayNameAr} (${classification.codeRef})")

        // نسب النحافة حول المحورين
        val klxRx = if (rx > 0) Kx * Lx / rx else 0.0
        val kyRy = if (ry > 0) Ky * Ly / ry else 0.0
        val slendernessRatio = max(klxRx, kyRy)
        val governingAxis = if (klxRx >= kyRy) "X" else "Y"

        val isSlender = slendernessRatio > 200.0  // SBC 306 / AISC E2: حد النحافة الأقصى
        codeNotes.add("نسبة النحافة الحاكمة: λ=${String.format("%.1f", slendernessRatio)} (محور $governingAxis)")
        if (isSlender) warnings.add("عمود نحيف جداً (λ=${String.format("%.0f", slendernessRatio)} > 200) - غير مسموح حسب SBC 306 E2")

        // حساب إجهاد الانبعاج الحرج Fcr (AISC E3)
        val Fe = if (slendernessRatio > 0) PI * PI * E_STEEL / (slendernessRatio * slendernessRatio) else Double.MAX_VALUE
        val Fcr = when {
            slendernessRatio <= 4.71 * sqrt(E_STEEL / grade.fy) -> {
                // انبعاج غير مرن (Inelastic Buckling) - AISC E3-2
                0.658.pow(grade.fy / Fe.coerceAtLeast(0.001)) * grade.fy
            }
            else -> {
                // انبعاج مرن (Elastic Buckling) - AISC E3-3
                0.877 * Fe
            }
        }

        // قدرة الضغط
        val Pn = Fcr * Ag / 1000.0  // kN
        var phiPn = PHI_COMPRESSION * Pn

        // قدرة الانحناء حول المحورين
        var phiMn_x = PHI_FLEXURE * grade.fy * Zx / 1e6  // kN.m
        var phiMn_y = PHI_FLEXURE * grade.fy * Zy / 1e6  // kN.m

        // تطبيق معامل التآكل الساحلي (SBC 306)
        if (isCoastal) {
            val coastalFactor = 1.0 / SBC_COASTAL_CORROSION_ALLOWANCE
            phiPn *= coastalFactor
            phiMn_x *= coastalFactor
            phiMn_y *= coastalFactor
            warnings.add("تم تطبيق معامل التآكل الساحلي SBC 306: تخفيض 10% في القدرات")
            codeNotes.add("معامل التآكل الساحلي (SBC 306): ${SBC_COASTAL_CORROSION_ALLOWANCE}")
        }

        // فحص متطلبات الزلازل (SBC 301)
        if (isSeismic) {
            val klR = max(klxRx, kyRy)
            // SBC 301 / AISC 341: حد النحافة للعناصر الزلزالية
            val seismicSlendernessLimit = 4.71 * sqrt(E_STEEL / grade.fy)
            if (klR > seismicSlendernessLimit) {
                warnings.add("نسبة النحافة الزلزالية (SBC 301): λ=${String.format("%.0f", klR)} تجاوزت الحد")
            }
        }

        val axialRatio = Pu / phiPn.coerceAtLeast(0.001)
        val isSafe = axialRatio <= 1.0

        if (!isSafe) warnings.add("الضغط يتجاوز القدرة - اختر مقطع أكبر (Pu/φPn=${String.format("%.2f", axialRatio)})")

        codeNotes.add("Fcr=${String.format("%.1f", Fcr)} MPa, φPn=${String.format("%.1f", phiPn)} kN")
        codeNotes.add("φMnx=${String.format("%.1f", phiMn_x)} kN.m, φMny=${String.format("%.1f", phiMn_y)} kN.m")
        if (isSeismic) codeNotes.add("SBC 301: متطلبات الأداء الزلزالي مُطبّقة")

        return SteelCompressionResult(
            phiPn = phiPn,
            Pn = Pn,
            Fcr = Fcr,
            slendernessRatio = slendernessRatio,
            isSlender = isSlender,
            phiMn_x = phiMn_x,
            phiMn_y = phiMn_y,
            sectionClassification = classification,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // =====================================================================
    // 3. فحص التحميل المركب - SBC 306 Chapter H1 (يتبع AISC 360-16)
    // =====================================================================

    /**
     * فحص التحميل المركب (محور + انحناء ثنائي المحور) حسب SBC 306 / AISC 360-16 H1
     * معادلة H1-1a: Pu/φPn + (8/9)(Mux/φMnx + Muy/φMny) ≤ 1.0  عندما Pu/φPn ≥ 0.2
     * معادلة H1-1b: Pu/(2φPn) + (Mux/φMnx + Muy/φMny) ≤ 1.0  عندما Pu/φPn < 0.2
     *
     * @param Pu القوة المحورية (kN)
     * @param Mux العزم حول X (kN.m)
     * @param Muy العزم حول Y (kN.m)
     * @param section نوع المقطع
     * @param grade درجة الصلب
     * @param Kx معامل الطول الفعال حول X
     * @param Ky معامل الطول الفعال حول Y
     * @param Lx طول العمود حول X (mm)
     * @param Ly طول العمود حول Y (mm)
     * @param isCoastal هل في منطقة ساحلية؟
     * @return نتيجة فحص التحميل المركب
     */
    fun checkCombinedLoading(
        Pu: Double, Mux: Double, Muy: Double,
        section: SteelSectionType,
        grade: SteelGrade,
        Kx: Double, Ky: Double,
        Lx: Double, Ly: Double,
        isCoastal: Boolean = false
    ): CombinedLoadingResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("SBC 306 / AISC 360-16 H1: فحص التحميل المركب (محور + انحناء)")

        // 1. حساب قدرة الضغط
        val colResult = designColumn(section, grade, Pu, 0.0, 0.0, Kx, Ky, Lx, Ly, isCoastal, false)
        val phiPn = colResult.phiPn
        val phiMnx = colResult.phiMn_x
        val phiMny = colResult.phiMn_y

        // 2. معادلة التفاعل
        val pr = Pu / phiPn.coerceAtLeast(0.001)
        val mrx = Mux / phiMnx.coerceAtLeast(0.001)
        val mry = Muy / phiMny.coerceAtLeast(0.001)

        val (interactionRatio, equation) = if (pr >= 0.2) {
            // SBC 306 / AISC H1-1a
            val ratio = pr + (8.0 / 9.0) * (mrx + mry)
            Pair(ratio, "H1-1a")
        } else {
            // SBC 306 / AISC H1-1b
            val ratio = pr / 2.0 + (mrx + mry)
            Pair(ratio, "H1-1b")
        }

        val isSafe = interactionRatio <= 1.0

        codeNotes.add("المعادلة المستخدمة: SBC 306 $equation (AISC 360-16 $equation)")
        codeNotes.add("Pu/φPn = ${String.format("%.3f", pr)}, Mux/φMnx = ${String.format("%.3f", mrx)}, Muy/φMny = ${String.format("%.3f", mry)}")
        codeNotes.add("نسبة التفاعل = ${String.format("%.3f", interactionRatio)}")

        if (!isSafe) {
            warnings.add("معادلة التفاعل تتجاوز 1.0 (${equation}): ${String.format("%.3f", interactionRatio)} > 1.0 - زِد المقطع")
        }
        if (isCoastal) {
            codeNotes.add("SBC 306: معامل التآكل الساحلي مُطبّق على القدرات")
        }

        return CombinedLoadingResult(
            interactionRatio = interactionRatio,
            pr = pr,
            mrx = mrx,
            mry = mry,
            equation = equation,
            phiPn = phiPn,
            phiMnx = phiMnx,
            phiMny = phiMny,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // =====================================================================
    // 4. تصميم الوصلات - SBC 306 Chapter J (يتبع AISC 360-16)
    // =====================================================================

    /**
     * تصميم الوصلات المعدنية حسب SBC 306
     * SBC 306 يتبع AISC 360-16 Chapter J مع التعديلات السعودية:
     * - فحص القص للمسمار: AISC J3.6 + متطلبات SBC 306 الزلزالية
     * - فحص اللحام: AISC J2 + متطلبات SBC 306
     * - فحص القص الكتلي: AISC J4.3
     *
     * @param connectionType نوع الوصلة
     * @param appliedForce القوة المطبقة (kN)
     * @param plateThickness سماكة الصفيحة (mm)
     * @param grade درجة الصلب
     * @param isSeismic هل الوصلة زلزالية؟
     * @return نتيجة تصميم الوصلة
     */
    fun designConnection(
        connectionType: ConnectionType,
        appliedForce: Double,
        plateThickness: Double,
        grade: SteelGrade,
        isSeismic: Boolean = false
    ): ConnectionDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val calculations = StringBuilder()

        codeNotes.add("SBC 306: تصميم الوصلات (Chapter J - يتبع AISC 360-16)")
        codeNotes.add("درجة الصلب: ${grade.displayName}, سماكة الصفيحة: ${String.format("%.1f", plateThickness)} mm")

        var capacity = 0.0
        var isSafe = false

        when (connectionType) {
            is ConnectionType.Bolted -> {
                // فحص الوصلة المساميرية - AISC J3 / SBC 306
                val boltResult = designBoltedConnection(
                    connectionType, appliedForce, plateThickness, grade, isSeismic
                )
                capacity = boltResult.first
                isSafe = boltResult.second
                calculations.append(boltResult.third)
                warnings.addAll(boltResult.fourth)
                codeNotes.addAll(boltResult.fifth)
            }
            is ConnectionType.Welded -> {
                // فحص الوصلة الملحومة - AISC J2 / SBC 306
                val weldResult = designWeldedConnection(
                    connectionType, appliedForce, plateThickness, grade, isSeismic
                )
                capacity = weldResult.first
                isSafe = weldResult.second
                calculations.append(weldResult.third)
                warnings.addAll(weldResult.fourth)
                codeNotes.addAll(weldResult.fifth)
            }
            is ConnectionType.Pressed -> {
                capacity = connectionType.pressForce
                isSafe = connectionType.pressForce >= appliedForce
                calculations.append("وصلة ضغط: القدرة = ${String.format("%.1f", connectionType.pressForce)} kN\n")
                if (!isSafe) warnings.add("قدرة الوصلة أقل من القوة المطبقة")
                codeNotes.add("SBC 306 / AISC J7: وصلات الضغط")
            }
            is ConnectionType.Hybrid -> {
                // فحص الوصلة المركبة - AISC J1.7 / SBC 306
                val boltResult = designBoltedConnection(
                    connectionType.bolted, appliedForce * 0.6, plateThickness, grade, isSeismic
                )
                val weldResult = designWeldedConnection(
                    connectionType.welded, appliedForce * 0.4, plateThickness, grade, isSeismic
                )
                capacity = boltResult.first + weldResult.first
                isSafe = capacity >= appliedForce
                calculations.append("وصلة مركبة (مسامير + لحام):\n")
                calculations.append("  قدرة المسامير: ${String.format("%.1f", boltResult.first)} kN\n")
                calculations.append("  قدرة اللحام: ${String.format("%.1f", weldResult.first)} kN\n")
                calculations.append("  القدرة الإجمالية: ${String.format("%.1f", capacity)} kN\n")
                warnings.addAll(boltResult.fourth)
                warnings.addAll(weldResult.fourth)
                codeNotes.add("SBC 306 / AISC J1.7: الوصلات المركبة (لحام + مسامير)")
            }
        }

        val utilizationRatio = appliedForce / capacity.coerceAtLeast(0.001)

        return ConnectionDesignResult(
            connectionType = connectionType,
            capacity = capacity,
            appliedForce = appliedForce,
            utilizationRatio = utilizationRatio,
            isSafe = isSafe,
            detailedCalculations = calculations.toString(),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * تصميم وصلة مساميرية - AISC J3 / SBC 306
     */
    private fun designBoltedConnection(
        bolted: ConnectionType.Bolted,
        appliedForce: Double,
        plateThickness: Double,
        grade: SteelGrade,
        isSeismic: Boolean
    ): Quadruple<Double, Boolean, String, List<String>, List<String>> {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val calc = StringBuilder()

        val db = bolted.boltDiameter          // mm
        val boltGrade = bolted.boltGrade       // BoltGrade entity
        val n = bolted.numberOfBolts
        val fuBolt = boltGrade.fu              // MPa
        val fyBolt = boltGrade.fy              // MPa

        // مساحة المسمار - AISC J3.6
        val Ab = PI * db * db / 4.0           // mm²

        // مقاومة القص للمسمار الواحد - AISC J3-1
        // φRn = φ × 0.6 × Fu × Ab (إذا كانت مُخدّمة)
        val singleBoltShear = PHI_SHEAR * 0.6 * fuBolt * Ab / 1000.0  // kN
        val totalBoltShear = singleBoltShear * n

        // مقاومة التحمل - AISC J3-6
        val lc = 3.0 * db  // مسافة حافة تقريبية (3 × db)
        val rnBearing = PHI_SHEAR * 2.4 * grade.fu * db * plateThickness / 1000.0  // kN لكل مسمار
        val totalBearing = rnBearing * n

        // مقاومة الشد - AISC J3-1
        val singleBoltTension = PHI_TENSION_BOLT * 0.75 * fuBolt * Ab / 1000.0  // kN
        val totalTension = singleBoltTension * n

        // القص الكتلي (Block Shear) - AISC J4.3
        val Agv = lc * plateThickness  // mm² (مساحة القص الإجمالية تقريبية)
        val Ant = (lc - db / 2.0) * plateThickness  // mm² (مساحة الشد الصافية تقريبية)
        val Anv = (lc - db) * plateThickness  // mm² (مساحة القص الصافية تقريبية)

        val Rn_bs_yield = 0.6 * grade.fy * Agv + grade.fu * Ant
        val Rn_bs_rupture = 0.6 * grade.fu * Anv + grade.fu * Ant
        val Rn_bs = min(Rn_bs_yield, Rn_bs_rupture) / 1000.0  // kN

        // السعة الحاكمة
        val capacity = min(min(totalBoltShear, totalBearing), min(totalTension, Rn_bs))
        val isSafe = capacity >= appliedForce

        calc.append("=== فحص الوصلة المساميرية (SBC 306 / AISC J3) ===\n")
        calc.append("المسمار: Ø${String.format("%.0f", db)} mm × $n, درجة: ${boltGrade.displayName}\n")
        calc.append("مساحة المسمار: Ab = ${String.format("%.1f", Ab)} mm²\n")
        calc.append("مقاومة القص لكل مسمار: ${String.format("%.1f", singleBoltShear)} kN, الإجمالي: ${String.format("%.1f", totalBoltShear)} kN\n")
        calc.append("مقاومة التحمل لكل مسمار: ${String.format("%.1f", rnBearing)} kN, الإجمالي: ${String.format("%.1f", totalBearing)} kN\n")
        calc.append("مقاومة الشد لكل مسمار: ${String.format("%.1f", singleBoltTension)} kN, الإجمالي: ${String.format("%.1f", totalTension)} kN\n")
        calc.append("مقاومة القص الكتلي (Block Shear): ${String.format("%.1f", Rn_bs)} kN\n")
        calc.append("السعة الحاكمة: ${String.format("%.1f", capacity)} kN\n")
        calc.append("نسبة الاستغلال: ${String.format("%.2f", appliedForce / capacity.coerceAtLeast(0.001))}\n")

        codeNotes.add("AISC J3.6 / SBC 306: فحص القص للمسمار")
        codeNotes.add("AISC J4.3 / SBC 306: فحص القص الكتلي (Block Shear)")

        if (isSeismic) {
            // SBC 301: متطلبات إضافية للوصلات الزلزالية
            // الوصلة يجب أن تُطوّر قبل العنصر (capacity design)
            val overstrengthFactor = 1.1  // SBC 301 / AISC 341
            val requiredSeismicCapacity = appliedForce * overstrengthFactor
            if (capacity < requiredSeismicCapacity) {
                warnings.add("SBC 301: الوصلة الزلزالية تحتاج سعة إضافية (${String.format("%.1f", requiredSeismicCapacity)} kN معامل التعزيز)")
                codeNotes.add("SBC 301: فحص التعزيز الزلزالي (Overstrength) مطلوب")
            } else {
                codeNotes.add("SBC 301: الوصلة تحقق متطلبات التعزيز الزلزالي ✓")
            }
        }

        if (!isSafe) warnings.add("الوصلة المساميرية غير آمنة - زِد عدد المسامير أو القطر")

        return Quadruple(capacity, isSafe, calc.toString(), warnings, codeNotes)
    }

    /**
     * تصميم وصلة ملحومة - AISC J2 / SBC 306
     */
    private fun designWeldedConnection(
        welded: ConnectionType.Welded,
        appliedForce: Double,
        plateThickness: Double,
        grade: SteelGrade,
        isSeismic: Boolean
    ): Quadruple<Double, Boolean, String, List<String>, List<String>> {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val calc = StringBuilder()

        val weldSize = welded.weldSize       // mm
        val weldLength = welded.weldLength   // mm
        val electrode = welded.electrodeType // ElectrodeType
        val Fexx = electrode.tensileStrength  // MPa

        // فحص اللحام التفريغي (Fillet Weld) - AISC J2.4
        if (welded.weldType == WeldType.FILLET) {
            val throat = weldSize / sqrt(2.0)  // mm - سُمك الحلق الفعال
            val throatArea = throat * weldLength  // mm²

            // مقاومة اللحام - AISC J2-4
            // Rn = 0.6 × Fexx × Aw
            val Rn_weld = 0.6 * Fexx * throatArea / 1000.0  // kN
            val phiRn = PHI_WELD * Rn_weld

            // فحص قص القاعدة - AISC J4.2
            val baseMetalShear = 0.6 * grade.fu * plateThickness * weldLength / 1000.0  // kN
            val phiBaseMetal = PHI_SHEAR * baseMetalShear

            val capacity = min(phiRn, phiBaseMetal)
            val isSafe = capacity >= appliedForce

            calc.append("=== فحص الوصلة الملحومة (SBC 306 / AISC J2) ===\n")
            calc.append("نوع اللحام: تفريغي (Fillet Weld)\n")
            calc.append("حجم اللحام: ${String.format("%.1f", weldSize)} mm, الطول: ${String.format("%.0f", weldLength)} mm\n")
            calc.append("القطب: ${electrode.displayName} (Fexx=${String.format("%.0f", Fexx)} MPa)\n")
            calc.append("سُمك الحلق: ${String.format("%.2f", throat)} mm, المساحة: ${String.format("%.0f", throatArea)} mm²\n")
            calc.append("مقاومة اللحام: φRn = ${String.format("%.1f", phiRn)} kN\n")
            calc.append("مقاومة القاعدة: φRn = ${String.format("%.1f", phiBaseMetal)} kN\n")
            calc.append("السعة الحاكمة: ${String.format("%.1f", capacity)} kN\n")
            calc.append("نسبة الاستغلال: ${String.format("%.2f", appliedForce / capacity.coerceAtLeast(0.001))}\n")

            codeNotes.add("AISC J2.4 / SBC 306: فحص اللحام التفريغي")
            codeNotes.add("Fexx = ${String.format("%.0f", Fexx)} MPa, φ = $PHI_WELD")

            // فحص أقل حجم اللحام - AISC Table J2.4
            val minWeldSize = when {
                plateThickness <= 6.0 -> 3.0
                plateThickness <= 13.0 -> 5.0
                plateThickness <= 19.0 -> 6.0
                else -> 8.0
            }
            if (weldSize < minWeldSize) {
                warnings.add("حجم اللحام (${String.format("%.1f", weldSize)} mm) أقل من الحد الأدنى (${String.format("%.0f", minWeldSize)} mm) حسب AISC J2.4 / SBC 306")
            }

            // فحص أقصى حجم اللحام - AISC J2.2
            val maxWeldSize = plateThickness - 2.0
            if (weldSize > maxWeldSize) {
                warnings.add("حجم اللحام (${String.format("%.1f", weldSize)} mm) يتجاوز الحد الأقصى (${String.format("%.1f", maxWeldSize)} mm) حسب AISC J2.2 / SBC 306")
            }

            if (isSeismic) {
                // SBC 301: متطلبات اللحام الزلزالي
                val minSeismicWeldSize = max(minWeldSize, 6.0)
                if (weldSize < minSeismicWeldSize) {
                    warnings.add("SBC 301: اللحام الزلزالي يحتاج حجم أدنى ${String.format("%.0f", minSeismicWeldSize)} mm")
                }
                codeNotes.add("SBC 301: متطلبات اللحام الزلزالي مُطبّقة")
            }

            if (!isSafe) warnings.add("الوصلة الملحومة غير آمنة - زِد حجم أو طول اللحام")

            return Quadruple(capacity, isSafe, calc.toString(), warnings, codeNotes)
        } else {
            // لحام أخدود (Groove Weld) - AISC J2
            val weldArea = plateThickness * weldLength  // mm²
            val Rn = grade.fy * weldArea / 1000.0  // kN
            val phiRn = PHI_FLEXURE * Rn
            val isSafe = phiRn >= appliedForce

            calc.append("=== فحص اللحام الأخدود (Groove Weld) - SBC 306 / AISC J2 ===\n")
            calc.append("مساحة اللحام: ${String.format("%.0f", weldArea)} mm²\n")
            calc.append("مقاومة اللحام: φRn = ${String.format("%.1f", phiRn)} kN\n")

            codeNotes.add("AISC J2 / SBC 306: فحص اللحام الأخدود (Groove Weld)")

            return Quadruple(phiRn, isSafe, calc.toString(), warnings, codeNotes)
        }
    }

    // =====================================================================
    // 5. تصميم القواعد المعدنية - SBC 306 / AISC Design Guide 1
    // =====================================================================

    /**
     * تصميم القاعدة المعدنية حسب SBC 306
     * يتبع منهجية AISC Design Guide 1 مع مراجع SBC 306
     *
     * @param Pu القوة المحورية (kN)
     * @param Mux العزم حول X (kN.m)
     * @param Muy العزم حول Y (kN.m)
     * @param columnSection مقطع العمود
     * @param grade درجة الصلب
     * @param fcu مقاومة الخرسانة (MPa)
     * @param isGrouted هل يوجد مونة تحت القاعدة؟
     * @return نتيجة تصميم القاعدة
     */
    fun designBasePlate(
        Pu: Double,
        Mux: Double, Muy: Double,
        columnSection: SteelSectionType,
        grade: SteelGrade,
        fcu: Double,
        isGrouted: Boolean = true
    ): BasePlateResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("SBC 306: تصميم القواعد المعدنية (يتبع AISC Design Guide 1)")
        codeNotes.add("درجة الصلب: ${grade.displayName}, مقاومة الخرسانة: f'c=${String.format("%.0f", fcu)} MPa")

        val d = columnSection.depth    // mm
        val bf = columnSection.width    // mm

        // 1. حساب مقاومة تحمل الخرسانة - AISC J8 / SBC 306
        // φPp = φ × 0.85 × f'c × A1 × √(A2/A1)
        val A1_factor = if (isGrouted) 0.85 else 1.0
        val phiPp_per_area = PHI_BEARING_CONCRETE * A1_factor * fcu  // MPa

        // 2. تقدير أولي لأبعاد القاعدة
        // منهجية AISC Design Guide 1: N = √(Pu / (0.5 × φPp_per_area))
        val requiredArea = Pu * 1000.0 / (0.5 * phiPp_per_area).coerceAtLeast(0.001)  // mm²
        val N_initial = ceil(sqrt(requiredArea / 1.0))  // مبدئياً مربع

        // ضبط النسب (B ≈ 0.8N إلى 1.2N)
        var B: Double
        var N: Double
        if (Mux > 0 || Muy > 0) {
            // قاعدة مع عزم - أطول في اتجاه العزم
            N = ceil(max(N_initial, d + 200.0) / 10.0) * 10.0
            B = ceil(max(N * 0.8, bf + 150.0) / 10.0) * 10.0
        } else {
            N = ceil(max(N_initial, d + 150.0) / 10.0) * 10.0
            B = N
        }

        // تأكد أن القاعدة أكبر من المقطع
        B = max(B, bf + 100.0)
        N = max(N, d + 100.0)

        // 3. فحص ضغط الخرسانة
        val A1 = B * N  // mm²
        // تقدير A2 ≈ 4 × A1 (القاعدة في منتصف الخرسانة)
        val A2 = 4.0 * A1
        val sqrtRatio = min(sqrt(A2 / A1.coerceAtLeast(1.0)), 2.0)
        val phiPp = PHI_BEARING_CONCRETE * A1_factor * fcu * A1 * sqrtRatio / 1000.0  // kN

        codeNotes.add("أبعاد القاعدة: B×N = ${String.format("%.0f", B)}×${String.format("%.0f", N)} mm")
        codeNotes.add("A1=${String.format("%.0f", A1)} mm², √(A2/A1)=${String.format("%.2f", sqrtRatio)}")
        codeNotes.add("قدرة تحمل الخرسانة: φPp=${String.format("%.1f", phiPp)} kN")

        if (phiPp < Pu) {
            warnings.add("قدرة تحمل الخرسانة (${String.format("%.1f", phiPp)} kN) أقل من الحمل (${String.format("%.1f", Pu)} kN) - زِد أبعاد القاعدة")
            // محاولة تعديل الأبعاد
            N = ceil(N * sqrt(Pu / phiPp) * 1.1 / 10.0) * 10.0
            B = ceil(B * sqrt(Pu / phiPp) * 1.1 / 10.0) * 10.0
        }

        // 4. حساب التبريز والتأثيرات
        val m = (N - d) / 2.0  // mm - تبريز في اتجاه العمق
        val n = (B - bf) / 2.0  // mm - تبريز في اتجاه العرض
        val X = max(m, n)       // mm - التبريز الأكبر (الحاكم)

        // 5. حساب سماكة اللوح - AISC Design Guide 1
        // tp = X × √(2 × Pu / (0.9 × Fy × B × N))
        val fP = Pu * 1000.0 / (B * N)  // MPa - الضغط الفعلي
        val tp_required = X * sqrt(2.0 * fP / (PHI_FLEXURE * grade.fy)).coerceAtLeast(0.001)
        // تقريب إلى أقرب قيمة قياسية (أعلى)
        val tp = max(ceil(tp_required / 2.0) * 2.0, MIN_PLATE_THICKNESS)

        codeNotes.add("التبريز: m=${String.format("%.1f", m)} mm, n=${String.format("%.1f", n)} mm, X=${String.format("%.1f", X)} mm")
        codeNotes.add("الضغط على الخرسانة: fP=${String.format("%.2f", fP)} MPa")
        codeNotes.add("سماكة اللوح المطلوبة: tp=${String.format("%.1f", tp_required)} mm → المعتمدة: ${String.format("%.0f", tp)} mm")

        // 6. تصميم البراغي الارتكازية
        // عدد البراغي حسب SBC 306 / AISC Design Guide 1
        val anchorBolts = if (Mux > Pu * 0.05 || Muy > Pu * 0.05) 4 else 4  // أقل 4 براغي
        val anchorDiameter = when {
            Pu < 200 -> 16.0
            Pu < 500 -> 20.0
            Pu < 1000 -> 24.0
            Pu < 2000 -> 27.0
            else -> 30.0
        }
        val anchorLength = max(anchorDiameter * 12.0, 250.0)  // mm (12 × db أو 250 أيهما أكبر)

        codeNotes.add("البراغي الارتكازية: ${anchorBolts} × Ø${String.format("%.0f", anchorDiameter)} mm")
        codeNotes.add("طول التثبيت: ${String.format("%.0f", anchorLength)} mm")

        // 7. فحص السلامة
        val isSafe = phiPp >= Pu && tp >= tp_required

        if (tp < SBC_MIN_THICKNESS_CORROSIVE) {
            warnings.add("سماكة اللوح أقل من الحد الأدنى في البيئات المؤكسدة (${String.format("%.0f", SBC_MIN_THICKNESS_CORROSIVE)} mm) حسب SBC 306")
        }

        if (isGrouted) {
            codeNotes.add("SBC 306: مونة تحت القاعدة (Grout) مطلوبة - سماكة 25-75 mm")
        }

        return BasePlateResult(
            plateWidth = B,
            plateLength = N,
            plateThickness = tp,
            anchorBolts = anchorBolts,
            anchorDiameter = anchorDiameter,
            anchorLength = anchorLength,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // =====================================================================
    // 6. تصميم الكوابرج - SBC 306 Chapter C & J
    // =====================================================================

    /**
     * تصميم كوبرج معدني حسب SBC 306
     * يتبع AISC 360-16 Chapter C (الشد) و E (الضغط)
     *
     * @param section نوع المقطع
     * @param grade درجة الصلب
     * @param axialLoad الحمل المحوري (kN) - موجب للضغط، سالب للشد
     * @param unbracedLength الطول غير المدعوم (mm)
     * @param connectionType نوع الوصلة
     * @return نتيجة تصميم الكوبرج
     */
    fun designBracing(
        section: SteelSectionType,
        grade: SteelGrade,
        axialLoad: Double,
        unbracedLength: Double,
        connectionType: ConnectionType
    ): SteelBracingResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("SBC 306: تصميم الكوابرج (Chapter C & E - يتبع AISC 360-16)")
        codeNotes.add("درجة الصلب: ${grade.displayName}")

        val isTension = axialLoad < 0
        val absLoad = abs(axialLoad)

        val Ag = section.area
        val d = section.depth
        val r = when (section) {
            is SteelSectionType.ISection -> section.rx
            is SteelSectionType.CHS -> section.outerDiameter / 4.0
            is SteelSectionType.RHS -> sqrt(section.ix / Ag.coerceAtLeast(1.0))
            is SteelSectionType.LSection -> {
                // تقريبي للزاوية
                val Ix = section.ix
                sqrt(Ix / Ag.coerceAtLeast(1.0))
            }
            else -> section.rx
        }

        var Pn = 0.0
        var phiPn = 0.0

        if (isTension) {
            // === تصميم عنصر الشد - AISC D2 / SBC 306 D2 ===
            // φPn = φ × Fy × Ag (خضوع المقطع الإجمالي)
            val Pn_yield = grade.fy * Ag / 1000.0  // kN
            // فحص الشد على المساحة الصافية - AISC D3
            val An = Ag * 0.85  // تقريبي (خصم ثقوب المسامير)
            val Pn_rupture = grade.fu * An / 1000.0  // kN
            Pn = min(PHI_TENSION * Pn_yield, PHI_TENSION * Pn_rupture)
            phiPn = Pn

            codeNotes.add("عنصر شد: φFyAg=${String.format("%.1f", PHI_TENSION * Pn_yield)} kN, φFuAn=${String.format("%.1f", PHI_TENSION * Pn_rupture)} kN")
            codeNotes.add("AISC D2 / SBC 306 D2: فحص الشد")
        } else {
            // === تصميم عنصر الضغط - AISC E3 / SBC 306 E3 ===
            val slendernessRatio = if (r > 0) unbracedLength / r else 0.0
            codeNotes.add("نسبة النحافة: λ=${String.format("%.1f", slendernessRatio)}")

            // حد النحافة الأقصى للكوابرج - SBC 306 / AISC E2
            if (slendernessRatio > 200.0) {
                warnings.add("نسبة النحافة (${String.format("%.0f", slendernessRatio)}) تجاوزت الحد المسموح (200) حسب SBC 306 E2")
            }

            // إجهاد الانبعاج الحرج
            val Fe = if (slendernessRatio > 0) PI * PI * E_STEEL / (slendernessRatio * slendernessRatio) else Double.MAX_VALUE
            val Fcr = when {
                slendernessRatio <= 4.71 * sqrt(E_STEEL / grade.fy) -> {
                    0.658.pow(grade.fy / Fe.coerceAtLeast(0.001)) * grade.fy
                }
                else -> 0.877 * Fe
            }

            Pn = Fcr * Ag / 1000.0  // kN
            phiPn = PHI_COMPRESSION * Pn

            codeNotes.add("Fcr=${String.format("%.1f", Fcr)} MPa, φPn=${String.format("%.1f", phiPn)} kN")
            codeNotes.add("AISC E3 / SBC 306 E3: فحص الضغط")
        }

        // فحص قدرة الوصلة
        val connectionResult = designConnection(
            connectionType = connectionType,
            appliedForce = absLoad,
            plateThickness = section.webThickness,
            grade = grade,
            isSeismic = false
        )
        val connectionCapacity = connectionResult.capacity

        val isSafe = phiPn >= absLoad && connectionCapacity >= absLoad

        if (phiPn < absLoad) warnings.add("قدرة العنصر (${String.format("%.1f", phiPn)} kN) أقل من الحمل (${String.format("%.1f", absLoad)} kN)")
        if (connectionCapacity < absLoad) warnings.add("قدرة الوصلة (${String.format("%.1f", connectionCapacity)} kN) أقل من الحمل (${String.format("%.1f", absLoad)} kN)")

        return SteelBracingResult(
            phiPn = phiPn,
            Pn = Pn,
            slendernessRatio = if (r > 0) unbracedLength / r else 0.0,
            isTension = isTension,
            connectionCapacity = connectionCapacity,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // =====================================================================
    // 7. تصنيف المقاطع - SBC 306 Table B4.1 (يتبع AISC 360-16 B4)
    // =====================================================================

    /**
     * تصنيف المقطع المعدني حسب SBC 306 Table B4.1 (يتبع AISC 360-16 B4.1)
     * - مدمج (Compact): يتحمل العزم البلاستيكي الكامل
     * - غير مدمج (Noncompact): يتحمل العزم اللدن فقط
     * - نحيف (Slender): يحتاج تخفيض في السعة
     *
     * @param section نوع المقطع
     * @param grade درجة الصلب
     * @return تصنيف المقطع
     */
    fun classifySection(section: SteelSectionType, grade: SteelGrade): SectionClassification {
        val Fy = grade.fy
        val E = E_STEEL

        return when (section) {
            is SteelSectionType.ISection -> {
                val h = section.h
                val bf = section.bf
                val tf = section.tf
                val tw = section.tw

                // فحص الشفة: λf = (bf - tw) / (2 × tf) - AISC B4.1a Table
                val lambdaFlange = (bf - tw) / (2 * tf)
                val lambdaFlangeCompact = 0.38 * sqrt(E / Fy)
                val lambdaFlangeNoncompact = 1.0 * sqrt(E / Fy)

                // فحص الجذع: λw = (h - 2tf) / tw - AISC B4.1a Table
                val lambdaWeb = (h - 2 * tf) / tw
                val lambdaWebCompact = 3.76 * sqrt(E / Fy)
                val lambdaWebNoncompact = 5.70 * sqrt(E / Fy)

                when {
                    lambdaFlange <= lambdaFlangeCompact && lambdaWeb <= lambdaWebCompact ->
                        SectionClassification.COMPACT
                    lambdaFlange <= lambdaFlangeNoncompact && lambdaWeb <= lambdaWebNoncompact ->
                        SectionClassification.NONCOMPACT
                    else -> SectionClassification.SLENDER
                }
            }
            is SteelSectionType.CHS -> {
                // الأنابيب الدائرية - AISC B4.1b Table
                val D = section.outerDiameter
                val t = section.thickness
                val lambda = D / t
                val lambdaCompact = 0.07 * E / Fy
                val lambdaNoncompact = 0.31 * E / Fy

                when {
                    lambda <= lambdaCompact -> SectionClassification.COMPACT
                    lambda <= lambdaNoncompact -> SectionClassification.NONCOMPACT
                    else -> SectionClassification.SLENDER
                }
            }
            is SteelSectionType.RHS -> {
                // الأنابيب المربعة/المستطيلة - AISC B4.1b Table
                val b = section.width
                val h = section.height
                val t = section.thickness
                val lambdaFlange = (b - 2 * t) / t
                val lambdaWeb = (h - 2 * t) / t

                val lambdaCompact = 1.12 * sqrt(E / Fy)
                val lambdaNoncompact = 1.40 * sqrt(E / Fy)

                when {
                    lambdaFlange <= lambdaCompact && lambdaWeb <= lambdaCompact ->
                        SectionClassification.COMPACT
                    lambdaFlange <= lambdaNoncompact && lambdaWeb <= lambdaNoncompact ->
                        SectionClassification.NONCOMPACT
                    else -> SectionClassification.SLENDER
                }
            }
            is SteelSectionType.LSection -> {
                // الزوايا - AISC B4.1b Table
                val b = min(section.legA, section.legB)
                val t = section.thickness
                val lambda = b / t
                val lambdaCompact = 0.54 * sqrt(E / Fy)
                val lambdaNoncompact = 0.91 * sqrt(E / Fy)

                when {
                    lambda <= lambdaCompact -> SectionClassification.COMPACT
                    lambda <= lambdaNoncompact -> SectionClassification.NONCOMPACT
                    else -> SectionClassification.SLENDER
                }
            }
            else -> {
                // للمقاطع الأخرى (T, C, Built-up) - تصنيف تقريبي
                val tw = section.webThickness
                val tf = section.flangeThickness
                val d = section.depth
                val w = section.width

                val lambdaFlange = if (tf > 0 && w > 0) w / (2 * tf) else Double.MAX_VALUE
                val lambdaWeb = if (tw > 0) d / tw else Double.MAX_VALUE

                val lambdaCompact = 1.12 * sqrt(E / Fy)
                val lambdaNoncompact = 1.40 * sqrt(E / Fy)

                when {
                    lambdaFlange <= lambdaCompact && lambdaWeb <= lambdaCompact * 3.0 ->
                        SectionClassification.COMPACT
                    lambdaFlange <= lambdaNoncompact && lambdaWeb <= lambdaNoncompact * 3.0 ->
                        SectionClassification.NONCOMPACT
                    else -> SectionClassification.SLENDER
                }
            }
        }
    }

    // =====================================================================
    // 8. توافقات التحميل حسب SBC 306
    // =====================================================================

    /**
     * حساب توافقات التحميل حسب SBC 306
     * SBC 306 يستخدم توافقات مختلفة عن AISC 360:
     * - الرياح: SBC يرجع للكود السعودي للرياح بدلاً من ASCE 7
     * - الزلازل: SBC 301 بدلاً من ASCE 7
     * - نفس معاملات LRFD (1.2D + 1.6L, إلخ)
     *
     * @param deadLoad الحمل الميت (kN)
     * @param liveLoad الحمل الحي (kN)
     * @param windLoad حمل الرياح (kN)
     * @param seismicLoad حمل الزلازل (kN)
     * @return قائمة بتوافقات التحميل
     */
    fun getSBCDesignLoads(
        deadLoad: Double, liveLoad: Double,
        windLoad: Double = 0.0, seismicLoad: Double = 0.0
    ): List<SBCLoadCombination> {
        val combinations = mutableListOf<SBCLoadCombination>()

        // 1. SBC 306 / ASCE 7-16: 1.2D + 1.6L (الحمل الأساسي)
        combinations.add(SBCLoadCombination(
            name = "SBC 306 (1): 1.2D + 1.6L",
            factorD = 1.2, factorL = 1.6, factorW = 0.0, factorE = 0.0,
            Pu = 1.2 * deadLoad + 1.6 * liveLoad,
            Mu = 0.0 // تُحسب من التوزيع الفعلي
        ))

        // 2. SBC 306 / ASCE 7-16: 1.2D + 1.6L + 0.5W (مع رياح)
        if (windLoad > 0) {
            combinations.add(SBCLoadCombination(
                name = "SBC 306 (2): 1.2D + 1.6L + 0.5W",
                factorD = 1.2, factorL = 1.6, factorW = 0.5, factorE = 0.0,
                Pu = 1.2 * deadLoad + 1.6 * liveLoad + 0.5 * windLoad,
                Mu = 0.0
            ))
        }

        // 3. SBC 306 / ASCE 7-16: 1.2D + 0.5L + 1.6W (الرياح حاكمة)
        if (windLoad > 0) {
            combinations.add(SBCLoadCombination(
                name = "SBC 306 (3): 1.2D + 0.5L + 1.6W",
                factorD = 1.2, factorL = 0.5, factorW = 1.6, factorE = 0.0,
                Pu = 1.2 * deadLoad + 0.5 * liveLoad + 1.6 * windLoad,
                Mu = 0.0
            ))
        }

        // 4. SBC 306 / ASCE 7-16: 0.9D + 1.6W (رفع بالرياح)
        if (windLoad > 0) {
            combinations.add(SBCLoadCombination(
                name = "SBC 306 (4): 0.9D + 1.6W",
                factorD = 0.9, factorL = 0.0, factorW = 1.6, factorE = 0.0,
                Pu = 0.9 * deadLoad + 1.6 * windLoad,
                Mu = 0.0
            ))
        }

        // 5. SBC 306 / SBC 301: 1.2D + 1.0L + 1.0E (زلازل - SBC 301 بدلاً من ASCE 7)
        if (seismicLoad > 0) {
            combinations.add(SBCLoadCombination(
                name = "SBC 306 (5): 1.2D + 1.0L + 1.0E (SBC 301)",
                factorD = 1.2, factorL = 1.0, factorW = 0.0, factorE = 1.0,
                Pu = 1.2 * deadLoad + 1.0 * liveLoad + 1.0 * seismicLoad,
                Mu = 0.0
            ))
        }

        // 6. SBC 306 / SBC 301: 0.9D + 1.0E (رفع زلزالي - SBC 301)
        if (seismicLoad > 0) {
            combinations.add(SBCLoadCombination(
                name = "SBC 306 (6): 0.9D + 1.0E (SBC 301)",
                factorD = 0.9, factorL = 0.0, factorW = 0.0, factorE = 1.0,
                Pu = 0.9 * deadLoad + 1.0 * seismicLoad,
                Mu = 0.0
            ))
        }

        return combinations
    }

    // =====================================================================
    // 9. فحص الحماية من التآكل - SBC 306
    // =====================================================================

    /**
     * فحص الحماية من التآكل حسب SBC 306
     * SBC 306 يتطلب حماية إضافية في البيئات الساحلية والبحرية والصناعية
     * بالمملكة العربية السعودية
     *
     * @param section نوع المقطع المعدني
     * @param environment نوع البيئة
     * @param protectionMethod طريقة الحماية الحالية
     * @return نتيجة فحص الحماية من التآكل
     */
    fun checkCorrosionProtection(
        section: SteelSectionType,
        environment: SBSEnvironment,
        protectionMethod: String
    ): CorrosionCheckResult {
        val notes = mutableListOf<String>()

        notes.add("SBC 306: فحص الحماية من التآكل")
        notes.add("البيئة: ${environment.displayNameAr} - ${environment.description}")
        notes.add("طريقة الحماية الحالية: $protectionMethod")

        // تحديد الحماية المطلوبة حسب البيئة
        val (requiredProtection, recommendedCoating) = when (environment) {
            SBSEnvironment.NORMAL -> {
                Pair(
                    "طلاء أولي (Primer) على الأقل",
                    "طلاء ألكيد أولي (Alkyd Primer) - سماكة 35 ميكرون على الأقل"
                )
            }
            SBSEnvironment.COASTAL -> {
                Pair(
                    "نظام حماية مزدوج (طلاء + طلاء نهائي مقاوم للتآكل)",
                    "طلاء إيبوكسي أولي (Epoxy Primer) 75 ميكرون + بولي يوريثان نهائي (Polyurethane) 50 ميكرون"
                )
            }
            SBSEnvironment.INDUSTRIAL -> {
                Pair(
                    "نظام حماية صناعي (مقاوم للأبخرة الكيميائية)",
                    "طلاء إيبوكسي مُعزّز (High Build Epoxy) 125 ميكرون + طلاء حماية كيميائي"
                )
            }
            SBSEnvironment.MARINE -> {
                Pair(
                    "نظام حماية بحري متكامل (Cathodic + Coating)",
                    "طلاء زنك غني (Zinc Rich Epoxy) 75 ميكرون + إيبوكسي 125 ميكرون + بولي يوريثان 75 ميكرون + حماية كاثودية"
                )
            }
        }

        // فحص كفاية الحماية الحالية
        val isAdequate = when (environment) {
            SBSEnvironment.NORMAL -> {
                val adequateMethods = listOf(
                    "primer", "طلاء", "paint", "galvanized", "جلفنة",
                    "epoxy", "إيبوكسي", "powder", "بودرة"
                )
                adequateMethods.any { protectionMethod.contains(it, ignoreCase = true) }
            }
            SBSEnvironment.COASTAL -> {
                val requiredKeywords = listOf(
                    "epoxy", "إيبوكسي", "galvanized", "جلفنة",
                    "hot-dip", "زنك", "zinc", "marine", "بحري",
                    "polyurethane", "بولي"
                )
                val adequateMethods = listOf(
                    "epoxy primer", "إيبوكسي", "hot dip galvanized",
                    "جلفانة انغمارية", "marine paint", "طلاء بحري"
                )
                adequateMethods.any { protectionMethod.contains(it, ignoreCase = true) } ||
                        requiredKeywords.count { protectionMethod.contains(it, ignoreCase = true) } >= 2
            }
            SBSEnvironment.INDUSTRIAL -> {
                val requiredKeywords = listOf(
                    "epoxy", "إيبوكسي", "vinyl", "فينيل", "chemical",
                    "كيميائي", "acid", "حمض", "resistant", "مقاوم"
                )
                val adequateMethods = listOf(
                    "chemical resistant", "مقاوم كيميائي",
                    "high build epoxy", "vinyl ester"
                )
                adequateMethods.any { protectionMethod.contains(it, ignoreCase = true) } ||
                        requiredKeywords.count { protectionMethod.contains(it, ignoreCase = true) } >= 2
            }
            SBSEnvironment.MARINE -> {
                val requiredKeywords = listOf(
                    "cathodic", "كاثودي", "zinc rich", "زنك",
                    "marine", "بحري", "splash zone", "منطقة الرذاذ"
                )
                val adequateMethods = listOf(
                    "cathodic protection", "حماية كاثودية",
                    "zinc rich", "marine coating system", "نظام بحري"
                )
                adequateMethods.any { protectionMethod.contains(it, ignoreCase = true) } ||
                        requiredKeywords.count { protectionMethod.contains(it, ignoreCase = true) } >= 2
            }
        }

        // فحص أقل سماكة في البيئات المؤكسدة (SBC 306)
        val minThickness = section.run {
            min(webThickness, flangeThickness)
        }
        if (environment != SBSEnvironment.NORMAL && minThickness < SBC_MIN_THICKNESS_CORROSIVE) {
            notes.add("تحذير SBC 306: أقل سماكة (${String.format("%.1f", minThickness)} mm) أقل من الحد الأدنى المطلوب (${String.format("%.0f", SBC_MIN_THICKNESS_CORROSIVE)} mm) في بيئة ${environment.displayNameAr}")
        }

        if (isAdequate) {
            notes.add("✓ الحماية الحالية كافية حسب SBC 306")
        } else {
            notes.add("✗ الحماية الحالية غير كافية - يُوصى بـ: $recommendedCoating")
        }

        notes.add("SBC 306: متطلبات الصيانة الدورية حسب نوع البيئة")
        if (environment == SBSEnvironment.MARINE || environment == SBSEnvironment.COASTAL) {
            notes.add("يُنصح بفحص بصري كل 6 أشهر واختبار سماكة الطلاء سنوياً")
        } else if (environment == SBSEnvironment.INDUSTRIAL) {
            notes.add("يُنصح بفحص بصري كل سنة واختبار سنوي للطلاء")
        } else {
            notes.add("يُنصح بفحص بصري كل سنتين")
        }

        return CorrosionCheckResult(
            requiredProtection = requiredProtection,
            isAdequate = isAdequate,
            recommendedCoating = recommendedCoating,
            notes = notes
        )
    }

    // =====================================================================
    // دوال مساعدة (Private Helper Functions)
    // =====================================================================

    /**
     * حساب قدرة الانحناء الإسمية - AISC 360-16 F2 / SBC 306 F2
     */
    private fun calculateNominalMomentCapacity(
        Zx: Double, Sx: Double, Fy: Double,
        d: Double, bf: Double, tw: Double, tf: Double,
        classification: SectionClassification
    ): Double {
        return when (classification) {
            SectionClassification.COMPACT -> {
                // Mn = Fy × Zx (المقاومة البلاستيكية الكاملة) - AISC F2-1
                Fy * Zx / 1e6  // kN.m
            }
            SectionClassification.NONCOMPACT -> {
                // Mn خطي بين Fy × Zx و 0.7 × Fy × Sx - AISC F3
                val Mp = Fy * Zx / 1e6
                val My = Fy * Sx / 1e6
                min(Mp, My)
            }
            SectionClassification.SLENDER -> {
                // تخفيض إضافي للمقاطع النحيفة - AISC F5
                val My = Fy * Sx / 1e6
                // معامل تخفيض تقريبي
                val lambdaF = if (tf > 0) bf / (2 * tf) else Double.MAX_VALUE
                val lambdaP = 0.38 * sqrt(E_STEEL / Fy)
                val lambdaR = 1.0 * sqrt(E_STEEL / Fy)
                if (lambdaP < lambdaR) {
                    My * (1.0 - 0.3 * ((lambdaF - lambdaP) / (lambdaR - lambdaP)).coerceIn(0.0, 1.0))
                } else {
                    My * 0.7
                }
            }
        }
    }

    /**
     * فحص الالتواء البعرضي (Lateral-Torsional Buckling) - AISC 360-16 F2 / SBC 306 F2
     */
    private fun calculateLTBCheck(
        Lb: Double, ry: Double,
        bf: Double, d: Double, tw: Double, tf: Double,
        Sx: Double, Zx: Double,
        Mn_full: Double, Fy: Double, Cb: Double
    ): LTBCheckResult {
        // Lp = 1.76 × ry × √(E/Fy) - AISC F2-5
        val Lp = 1.76 * ry * sqrt(E_STEEL / Fy)

        // Lr تقريبي - AISC F2-6
        // rt تقريبي
        val rt = sqrt(
            if (tf > 0 && tw > 0) {
                val Iyc = tf * bf.pow(3) / 12.0
                val Cw = ((d - 2 * tf).coerceAtLeast(1.0)).pow(2) * bf * bf * tw / 12.0
                sqrt(Iyc * Cw) / Sx
            } else {
                ry * ry
            }
        )
        val Lr = 1.95 * rt * sqrt(E_STEEL / (0.7 * Fy))

        val Mn_lt = when {
            Lb <= Lp -> Mn_full  // المنطقة البلاستيكية - قدرة كاملة
            Lb <= Lr -> {
                // المنطقة اللدنة (Inelastic LTB) - AISC F2-2
                val Mn_Lr = 0.7 * Fy * Sx / 1e6  // kN.m
                Cb * Mn_full - (Cb * Mn_full - Mn_Lr) * ((Lb - Lp) / (Lr - Lp).coerceAtLeast(1.0))
            }
            else -> {
                // المنطقة المرنة (Elastic LTB) - AISC F2-3
                val Fcr_ltb = Cb * PI * PI * E_STEEL / ((Lb / ry.coerceAtLeast(1.0)).pow(2))
                Fcr_ltb * Sx / 1e6  // kN.m
            }
        }

        val phiMn_lt = PHI_FLEXURE * Mn_lt.coerceAtLeast(0.0)
        val isGoverning = Lb > Lp

        return LTBCheckResult(
            Lb = Lb,
            Lp = Lp,
            Lr = Lr,
            Mn_lt = Mn_lt,
            phiMn_lt = phiMn_lt,
            isGoverning = isGoverning,
            ratio = 1.0 / (phiMn_lt / Mn_full.coerceAtLeast(0.001))
        )
    }

    // =====================================================================
    // فئة مساعدة لتخزين نتيجة متعددة القيم (بدون استخدام Pair/Triple)
    // =====================================================================

    /**
     * فئة مساعدة لتخزين 5 قيم (نتيجة تصميم الوصلة)
     */
    private data class Quadruple<A, B, C, D, E>(
        val first: A, val second: B, val third: C, val fourth: D, val fifth: E
    )
}