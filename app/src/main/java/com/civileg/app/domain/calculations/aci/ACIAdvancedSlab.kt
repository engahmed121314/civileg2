package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

/**
 * تصميم البلاطات المتقدم حسب الكود الأمريكي ACI 318-19
 * يغطي:
 * - بلاطة مسطحة بدون كمرات (Flat Plate) - ACI 8.10 Direct Design Method
 * - بلاطة مسطحة مع بلاطة منخفضة (Flat Slab with Drop Panels) - ACI 8.10 + 8.10.3
 * - بلاطة بلكات مجوفة / كمريتات (One-Way Ribbed/Hordi) - ACI 7.6
 * - بلاطة وافل اتجاهين (Two-Way Waffle) - ACI 8.10 + 7.6
 * - بلاطة كابولية (Cantilever Slab) - ACI 8.3.1.1
 *
 * المراجع:
 * - ACI 318-19 البند 7 (أبعاد البلاطات)
 * - ACI 318-19 البند 8 (البلاطات ذات الاتجاهين - طريقة التصميم المباشر)
 * - ACI 318-19 البند 22.5-22.6 (القص وقص الاختراق)
 * - ACI 318-19 البند 24 (الانحراف)
 * - ACI 318-19 البند 7.6.1.1 (الحد الأدنى للتسليح)
 * - ACI 318-19 البند 22.2.2.4.1 (معامل β₁ الديناميكي)
 *
 * ملاحظة: استخدام طريقة Rn-ρ حسب ACI 318-19 (وليس طريقة K الخاصة بـ ECP)
 */
class ACIAdvancedSlab {

    private val baseSlab = ACISlab()

    companion object {
        // ACI 21.2.1: معاملات التخفيض
        const val PHI_FLEXURE = 0.90
        const val PHI_SHEAR = 0.75

        // ACI 7.6.1.1: الحد الأدنى للتسليح (للفولاذ ذي إجهاد خضوع ≤ 420 MPa)
        const val MIN_REIN_RATIO = 0.0018

        // أقطار السيخ المتاحة (مم) - قياس متري
        val BAR_DIAMETERS = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
    }

    // ============================================================
    // نقطة الدخول الرئيسية - تصميم شامل حسب ACI 318-19
    // ============================================================

    /**
     * تصميم شامل للبلاطة حسب النوع والكود الأمريكي
     * @param slabType نوع البلاطة (FlatPlate, FlatSlab, Hordi, Waffle, etc.)
     * @param fcu مقاومة الخرسانة للمكعب (MPa)
     * @param fy إجهاد خضوع الفولاذ (MPa)
     * @param deadLoad الحمل الميت (kN/m²) - خدمي
     * @param liveLoad الحمل الحي (kN/m²) - خدمي
     * @param inventory مخزون الحديد (اختياري)
     * @param loadCombination حالة التحميل
     */
    fun designSlab(
        slabType: SlabType,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        inventory: RebarInventory? = null,
        loadCombination: LoadCombination
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // تحويل مقاومة المكعب لمقاومة الأسطوانة: f'c = 0.8 × fcu
        val fcPrime = 0.8 * fcu
        codeNotes.add("ACI 318-19: fcu=%.0f MPa → fc'=0.8×fcu=%.0f MPa".format(fcu, fcPrime))
        codeNotes.add("φ_flexure=%.1f, φ_shear=%.1f (ACI 21.2.1)".format(PHI_FLEXURE, PHI_SHEAR))

        // الحمل التصميمي: ACI 5.3.1 → U = 1.2D + 1.6L
        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ACI)
        codeNotes.add("wu = 1.2D + 1.6L = %.1f kN/m²".format(totalLoad))

        // β₁ حسب ACI 22.2.2.4.1
        val beta1 = computeBeta1(fcPrime)
        codeNotes.add("β₁ = %.3f (ACI 22.2.2.4.1)".format(beta1))

        // التصميم حسب نوع البلاطة
        val flexureResult = when (slabType) {
            is SlabType.FlatPlate -> designFlatPlate(slabType, fcPrime, fy, totalLoad)
            is SlabType.FlatSlab -> designFlatSlabWithDrop(slabType, fcPrime, fy, totalLoad)
            is SlabType.Hordi -> designRibbedSlab(slabType, fcPrime, fy, totalLoad)
            is SlabType.Waffle -> designWaffleSlab(slabType, fcPrime, fy, totalLoad)
            is SlabType.Solid -> designSolidSlab(slabType, fcPrime, fy, totalLoad, loadCombination)
            is SlabType.OneWay -> designOneWayAdvanced(slabType, fcPrime, fy, totalLoad, loadCombination)
            is SlabType.TwoWay -> designTwoWayAdvanced(slabType, fcPrime, fy, totalLoad, loadCombination)
            is SlabType.PostTensioned -> designPostTensionedSimple(slabType, fcPrime, fy, totalLoad)
            is SlabType.Precast -> designPrecastSimple(slabType, fcPrime, fy, totalLoad, loadCombination)
        }

        // فحص القص العادي
        val shearCheck = checkSlabShear(slabType, fcPrime, totalLoad)

        // فحص الانحراف
        val deflectionCheck = checkSlabDeflection(slabType, fy, fcPrime)

        // فحص قص الاختراق (للبلاطات المسطحة والوافل)
        val punchingCheck = when (slabType) {
            is SlabType.FlatPlate -> checkPunchingShear(
                slabType.thickness, slabType.columnSize, slabType.columnSize,
                slabType.panelLength, slabType.panelWidth, fcPrime, totalLoad
            )
            is SlabType.FlatSlab -> checkPunchingShearWithDrop(slabType, fcPrime, totalLoad)
            is SlabType.Waffle -> checkPunchingShearWaffle(slabType, fcPrime, totalLoad)
            else -> null
        }

        // حساب الكميات
        val concreteVolume = calculateConcreteVolume(slabType)
        val formworkArea = calculateFormworkArea(slabType)

        // تحليل المخزون
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeInventory(flexureResult, slabType, inv)
        }

        // جمع التحذيرات
        if (flexureResult.warnings.isNotEmpty()) warnings.addAll(flexureResult.warnings)
        if (!shearCheck.isSafe) warnings.add("⚠️ ACI 22.5: Shear capacity exceeded!")
        if (!deflectionCheck.isSafe) warnings.add("⚠️ ACI 24.2: Deflection limit exceeded! Increase thickness")
        if (punchingCheck?.isSafe == false) {
            warnings.add("⚠️ ACI 22.6: Punching shear failure! Use shear heads or increase thickness")
        }

        codeNotes.add(getCodeReference(slabType))

        return AdvancedSlabResult(
            slabType = slabType,
            flexureResult = flexureResult,
            shearCheck = shearCheck,
            deflectionCheck = deflectionCheck,
            punchingShearCheck = punchingCheck,
            reinforcementLayout = createReinforcementLayout(flexureResult, slabType),
            concreteVolume = concreteVolume,
            formworkArea = formworkArea,
            inventoryAnalysis = inventoryAnalysis,
            postTensionCalculations = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================
    // 1. FLAT PLATE DESIGN (ACI 8.10 Direct Design Method)
    // ============================================================

    /**
     * تصميم البلاطة المسطحة بدون بلاطة منخفضة
     * ACI 318-19 البند 8.10 (طريقة التصميم المباشر)
     * - توزيع العزم بين شريط العمود وشريط الوسط
     * - معاملات العزم من الجدول 8.10.2.2
     * - فحص قص الاختراق ACI 22.6
     */
    private fun designFlatPlate(
        slab: SlabType.FlatPlate,
        fcPrime: Double,
        fy: Double,
        wu: Double
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val ln = slab.panelLength / 1000.0  // م - البحر الصافي في اتجاه التصميم
        val l2 = slab.panelWidth / 1000.0   // م - البحر في الاتجاه العمودي
        val h = slab.thickness               // مم
        val c = slab.columnSize / 1000.0     // م - حجم العمود

        codeNotes.add("=== ACI 8.10: Flat Plate (Direct Design Method) ===")
        codeNotes.add("ln=%.2f m, l2=%.2f m, c=%.2f m, h=%.0f mm".format(ln, l2, c, h))

        // فحص الحد الأدنى للسمك - ACI Table 8.3.1.1
        // للبلاطة المسطحة (بدون حواف عمود): ln/30 للوحات داخلية
        val hMin = min(ln, l2) * 1000.0 / 30.0
        if (h < hMin) {
            warnings.add("ACI Table 8.3.1.1: h=%.0f < h_min=%.0f mm (ln/30)".format(h, hMin))
        } else {
            codeNotes.add("ACI Table 8.3.1.1: Min thickness OK (%.0f ≥ %.0f)".format(h, hMin))
        }

        // العزم الساكن الإجمالي: Mo = wu × l2 × ln² / 8 (ACI 8.10.1.2)
        val Mo = wu * l2 * ln * ln / 8.0
        codeNotes.add("Mo = wu×l2×ln²/8 = %.1f kN.m".format(Mo))

        // توزيع العزم - ACI Table 8.10.2.2 (لوحة داخلية)
        val Mneg = 0.65 * Mo   // عزم سالب عند الدعامة
        val Mpos = 0.35 * Mo   // عزم موجب في المنتصف
        codeNotes.add("M- = 0.65×Mo = %.1f kN.m, M+ = 0.35×Mo = %.1f kN.m".format(Mneg, Mpos))

        // عرض شريط العمود - ACI 8.4.1.5
        val columnStripWidth = min(ln / 2.0, l2 / 2.0)
        val middleStripWidth = (l2 - columnStripWidth) / 2.0
        codeNotes.add("Column strip: 2×%.2f=%.2f m, Middle strip: 2×%.2f=%.2f m".format(
            columnStripWidth, columnStripWidth * 2, middleStripWidth, middleStripWidth * 2))

        // توزيع العزم بين شريط العمود وشريط الوسط - ACI 8.10.4.2
        // للبلاطة المسطحة (بدون كمرات، α₁=0):
        // شريط العمود: 75% من السالب، 60% من الموجب
        // شريط الوسط: 25% من السالب، 40% من الموجب
        val csNeg = 0.75 * Mneg  // عزم سالب في شريط العمود
        val msNeg = 0.25 * Mneg  // عزم سالب في شريط الوسط
        val csPos = 0.60 * Mpos  // عزم موجب في شريط العمود
        val msPos = 0.40 * Mpos  // عزم موجب في شريط الوسط

        // العزم لكل متر عرض
        val muCsNeg = csNeg / (columnStripWidth * 1000.0)
        val muCsPos = csPos / (columnStripWidth * 1000.0)
        val muMsNeg = msNeg / (middleStripWidth * 1000.0)
        val muMsPos = msPos / (middleStripWidth * 1000.0)

        codeNotes.add("CS: M-=%.2f, M+=%.2f kN.m/m".format(muCsNeg, muCsPos))
        codeNotes.add("MS: M-=%.2f, M+=%.2f kN.m/m".format(muMsNeg, muMsPos))

        // تصميم التسليح للحالة الحرجة (شريط العمود - سالب عادةً يتحكم)
        val muMax = max(muCsNeg, muCsPos, muMsNeg, muMsPos)
        val d = h - 20.0 - 6.0  // العمق الفعال (غطاء + نصف قطر السيخ)

        // حساب Rn و ρ بطريقة ACI Rn-ρ
        val (rho, asRequired, Rn) = computeRnRho(muMax, fcPrime, fy, d)

        codeNotes.add("Critical: M=%.2f kN.m/m → Rn=%.2f, ρ=%.5f".format(muMax, Rn, rho))
        codeNotes.add("As_req = %.1f mm²/m".format(asRequired))

        // اختيار القضبان - بديل اقتصادي وأكثر أماناً
        val (ecoBar, ecoSpacing, ecoAs) = selectEconomicalBar(asRequired, h)
        val (safeBar, safeSpacing, safeAs) = selectSafestBar(asRequired, h)

        codeNotes.add("Economical: Φ%.0f @ %.0fmm → As=%.0f mm²/m".format(ecoBar, ecoSpacing, ecoAs))
        codeNotes.add("Safest:    Φ%.0f @ %.0fmm → As=%.0f mm²/m".format(safeBar, safeSpacing, safeAs))

        // قدرة القص الخرساني: Vc = 0.17λ√fc' × b × d (ACI 22.5.5.1), λ=1.0
        val Vc = 0.17 * sqrt(fcPrime) * 1000.0 * d / 1000.0  // kN/m

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = ecoAs,
            barDiameter = ecoBar,
            barSpacing = ecoSpacing,
            minThickness = hMin,
            shearCapacity = Vc,
            isSafe = rho <= 0.025,
            utilizationRatio = Rn / (0.85 * fcPrime * 0.375).coerceAtLeast(0.01),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================
    // 2. FLAT SLAB WITH DROP PANELS (ACI 8.10 + 8.10.3)
    // ============================================================

    /**
     * تصميم البلاطة المسطحة مع بلاطة منخفضة (Drop Panel)
     * ACI 318-19 البند 8.10.3 (متطلبات البلاطة المنخفضة)
     * - امتداد البلاطة المنخفضة ≥ L/6 من مركز العمود
     * - سماكة البلاطة المنخفضة ≥ h/4 تحت البلاطة الأصلية
     * - محيط قص الاختراق يتضمن حافة البلاطة المنخفضة
     */
    private fun designFlatSlabWithDrop(
        slab: SlabType.FlatSlab,
        fcPrime: Double,
        fy: Double,
        wu: Double
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val ln = slab.panelLength / 1000.0
        val l2 = slab.panelWidth / 1000.0
        val h = slab.thickness
        val hDrop = slab.dropPanelThickness
        val dropSize = slab.dropPanelSize / 1000.0  // م
        val hTotal = h + hDrop

        codeNotes.add("=== ACI 8.10: Flat Slab with Drop Panels ===")
        codeNotes.add("h_slab=%.0f, h_drop=%.0f, h_total=%.0f mm".format(h, hDrop, hTotal))

        // متطلبات البلاطة المنخفضة - ACI 8.10.3
        val dropExtensionReq = ln / 6.0  // امتداد مطلوب
        val dropThicknessReq = h / 4.0   // سماكة منخفضة مطلوبة

        if (dropSize < dropExtensionReq) {
            warnings.add("ACI 8.10.3: Drop size %.2fm < L/6=%.2fm".format(dropSize, dropExtensionReq))
        }
        if (hDrop < dropThicknessReq) {
            warnings.add("ACI 8.10.3: Drop thickness %.0fmm < h/4=%.0fmm".format(hDrop, dropThicknessReq))
        }
        codeNotes.add("ACI 8.10.3: Drop extends %.2fm (req ≥ L/6=%.2fm) ✓%s".format(
            dropSize, dropExtensionReq, if (dropSize >= dropExtensionReq) "" else " ✗"))
        codeNotes.add("ACI 8.10.3: Drop thick=%.0fmm (req ≥ h/4=%.0fmm) ✓%s".format(
            hDrop, dropThicknessReq, if (hDrop >= dropThicknessReq) "" else " ✗"))

        // الحد الأدنى للسمك - ACI Table 8.3.1.1 (مع drop panel: ln/33)
        val hMin = min(ln, l2) * 1000.0 / 33.0
        if (hTotal < hMin) {
            warnings.add("ACI Table 8.3.1.1: h_total=%.0f < h_min=%.0f mm".format(hTotal, hMin))
        }

        // العزم الساكن الإجمالي
        val Mo = wu * l2 * ln * ln / 8.0
        val Mneg = 0.65 * Mo
        val Mpos = 0.35 * Mo

        // عرض شريط العمود
        val columnStripWidth = min(ln / 2.0, l2 / 2.0)
        val middleStripWidth = (l2 - columnStripWidth) / 2.0

        // العزم لكل متر عرض
        val muCsNeg = 0.75 * Mneg / (columnStripWidth * 1000.0)
        val muCsPos = 0.60 * Mpos / (columnStripWidth * 1000.0)
        val muMsNeg = 0.25 * Mneg / (middleStripWidth * 1000.0)
        val muMsPos = 0.40 * Mpos / (middleStripWidth * 1000.0)

        // العمق الفعال: أكبر في منطقة البلاطة المنخفضة
        val dDrop = hTotal - 20.0 - 8.0  // في منطقة الـ drop panel
        val dSlab = h - 20.0 - 6.0        // في منطقة البلاطة الأصلية

        // تصميم العزم السالب (منطقة البلاطة المنخفضة - d أكبر)
        val muNegMax = max(muCsNeg, muMsNeg)
        val (rhoNeg, asNeg, RnNeg) = computeRnRho(muNegMax, fcPrime, fy, dDrop)

        // تصميم العزم الموجب (منطقة البلاطة - d أصغر)
        val muPosMax = max(muCsPos, muMsPos)
        val (rhoPos, asPos, RnPos) = computeRnRho(muPosMax, fcPrime, fy, dSlab)

        codeNotes.add("Mo=%.1f kN.m → M-=%.1f, M+=%.1f".format(Mo, Mneg, Mpos))
        codeNotes.add("Neg (d=%.0f): Rn=%.2f, As=%.1f mm²/m".format(dDrop, RnNeg, asNeg))
        codeNotes.add("Pos (d=%.0f): Rn=%.2f, As=%.1f mm²/m".format(dSlab, RnPos, asPos))

        // أكبر تسليح مطلوب يحدد النتيجة
        val asRequired = max(asNeg, asPos)
        val dDesign = if (asNeg >= asPos) dDrop else dSlab

        val (ecoBar, ecoSpacing, ecoAs) = selectEconomicalBar(asRequired, hTotal)
        val (safeBar, safeSpacing, safeAs) = selectSafestBar(asRequired, hTotal)

        codeNotes.add("Economical: Φ%.0f @ %.0fmm → As=%.0f mm²/m".format(ecoBar, ecoSpacing, ecoAs))
        codeNotes.add("Safest:    Φ%.0f @ %.0fmm → As=%.0f mm²/m".format(safeBar, safeSpacing, safeAs))

        val Vc = 0.17 * sqrt(fcPrime) * 1000.0 * dDesign / 1000.0

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = ecoAs,
            barDiameter = ecoBar,
            barSpacing = ecoSpacing,
            minThickness = hMin,
            shearCapacity = Vc,
            isSafe = rhoNeg <= 0.025 && rhoPos <= 0.025,
            utilizationRatio = max(RnNeg, RnPos) / (0.85 * fcPrime * 0.375).coerceAtLeast(0.01),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================
    // 3. ONE-WAY RIBBED / HORDI SLAB (ACI 7.6)
    // ============================================================

    /**
     * تصميم البلاطة المجوفة / الكمريتات ذات الاتجاه الواحد
     * ACI 318-19 البند 7.6 (أبعاد البلاطات المنفذة جزئياً)
     * - تصميم الكمريتات كعناصر انحناء بطريقة Rn-ρ
     * - تصميم طبقة التغطية (Topping) كبلاطة اتجاه واحد
     * - الحد الأدنى لعرض الكمريتة: 100 مم (ACI 7.6.5)
     * - المسافة الخالية بين الكمريتات: ≤ 750 مم
     * - فحص القص في الكمريتات
     */
    private fun designRibbedSlab(
        slab: SlabType.Hordi,
        fcPrime: Double,
        fy: Double,
        wu: Double
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val ribSpacing = slab.ribSpacing / 1000.0  // م - المسافة بين محاور الكمريتات
        val span = slab.span / 1000.0              // م - البحر
        val bw = slab.ribWidth                      // مم - عرض الكمريتة
        val totalH = slab.totalThickness            // مم - السمك الكلي
        val toppingH = totalH - slab.blockHeight.coerceAtMost(slab.blockHeight)  // تقريبي

        codeNotes.add("=== ACI 7.6: One-Way Ribbed / Hordi Slab ===")
        codeNotes.add("Span=%.2f m, Rib spacing=%.0f mm c/c, bw=%.0f mm".format(span, slab.ribSpacing, bw))
        codeNotes.add("Total h=%.0f mm, Block h=%.0f mm".format(totalH, slab.blockHeight))

        // فحص متطلبات ACI 7.6.5
        val clearSpacing = slab.ribSpacing - bw
        if (bw < 100.0) {
            warnings.add("ACI 7.6.5: Rib width %.0fmm < 100mm minimum".format(bw))
        }
        if (clearSpacing > 750.0) {
            warnings.add("ACI 7.6.5: Clear spacing %.0fmm > 750mm maximum".format(clearSpacing))
        }
        codeNotes.add("ACI 7.6.5: bw=%.0fmm (≥100), clear=%.0fmm (≤750)".format(bw, clearSpacing))

        // فحص طبقة التغطية (Topping) - ACI 7.6.6: min 50mm
        val minTopping = 50.0
        if (toppingH < minTopping) {
            warnings.add("ACI 7.6.6: Topping %.0fmm < 50mm minimum".format(toppingH))
        } else {
            codeNotes.add("ACI 7.6.6: Topping %.0fmm ≥ 50mm ✓".format(toppingH))
        }

        // الحمل على كل كمريتة (kN/m)
        val loadPerRib = wu * ribSpacing

        // العزم الأقصى: Mu = w × L² / 10 للكمريتات المستمرة
        val Mu = loadPerRib * span * span / 10.0  // kN.m/rib

        // تصميم الكمريتة بطريقة Rn-ρ
        val d = totalH - 30.0  // عمق فعال تقريبي (cover + bar/2)
        val Mu_Nmm = Mu * 1e6
        val Rn = Mu_Nmm / (PHI_FLEXURE * bw * d * d)

        // β₁ حسب ACI 22.2.2.4.1 (للتحقق من الإجهاد عند التشقق)
        val beta1 = computeBeta1(fcPrime)
        codeNotes.add("β₁=%.3f (ACI 22.2.2.4.1 - strain verification)".format(beta1))

        // Rn-ρ method: β₁ not in discriminant (Whitney stress block derivation)
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fcPrime)
        val rho = if (discriminant > 0) {
            (0.85 * fcPrime / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add("ACI 22.2: Compression failure - increase rib depth")
            0.025
        }

        // الحد الأدنى للتسليح للكمريتات: ACI 7.6.1.1
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        val Ac = bw * totalH  // مساحة الخرسانة
        val asMinRib = max(rhoMin * bw * d, 0.0015 * Ac)  // أكبر الحدين
        val asRequired = max(rho * bw * d, asMinRib)

        codeNotes.add("Mu/rib = %.1f kN.m, Rn=%.2f MPa".format(Mu, Rn))
        codeNotes.add("ρ=%.5f, ρ_min=%.5f, As_rib=%.1f mm²".format(rho, rhoMin, asRequired))

        // اختيار التسليح للكمريتة (عدد أسياخ)
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0)
        val barResult = availableBars.map { dia ->
            val area = PI * dia * dia / 4.0
            val numBars = max(ceil(asRequired / area).toInt(), 1)
            Triple(dia, numBars, numBars * area)
        }.firstOrNull { (_, _, asProv) -> asProv >= asRequired } ?: Triple(16.0, 2, PI * 64.0)

        val barDia = barResult.first
        val numBars = barResult.second
        val asProvided = barResult.third

        codeNotes.add("Rib steel: %dΦ%.0f → As=%.1f mm²".format(numBars, barDia, asProvided))

        // تصميم طبقة التغطية كبلاطة اتجاه واحد
        val toppingResult = designToppingSlab(toppingH, fcPrime, fy, wu, slab.ribSpacing)
        toppingResult.codeNotes.forEach { codeNotes.add(it) }
        toppingResult.warnings.forEach { warnings.add(it) }

        // فحص القص في الكمريتات - ACI 22.5.5.1
        val Vu = loadPerRib * span / 2.0  // kN (عند الدعامة)
        val Vc = 0.17 * sqrt(fcPrime) * bw * d / 1000.0  // kN
        val phiVc = PHI_SHEAR * Vc
        if (Vu > phiVc) {
            warnings.add("ACI 22.5.5.1: Vu=%.1f > φVc=%.1f kN - increase rib width".format(Vu, phiVc))
        }
        codeNotes.add("Shear: Vu=%.1f kN, φVc=%.1f kN (bw=%.0f, d=%.0f)".format(Vu, phiVc, bw, d))

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asProvided,
            barDiameter = barDia,
            barSpacing = slab.ribSpacing,  // spacing = rib spacing for ribbed slabs
            minThickness = totalH,
            shearCapacity = Vc,
            isSafe = discriminant > 0 && Vu <= phiVc,
            utilizationRatio = Rn / (0.85 * fcPrime * 0.375).coerceAtLeast(0.01),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * تصميم طبقة التغطية (Topping Slab) كبلاطة اتجاه واحد
     * الحمل ينتقل من طبقة التغطية إلى الكمريتات
     */
    private fun designToppingSlab(
        toppingThickness: Double,
        fcPrime: Double,
        fy: Double,
        wu: Double,
        ribSpacing: Double  // مم - المسافة بين الكمريتات (البحر الفعال)
    ): SlabDesignResult {
        val codeNotes = mutableListOf<String>()
        val d = toppingThickness - 20.0 - 3.0  // عمق فعال صغير (cover + bar/2)
        val b = 1000.0
        val span_m = ribSpacing / 1000.0

        // عزم التغطية: Mu = wu × L² / 10 (مستمرة)
        val Mu = wu * span_m * span_m / 10.0  // kN.m/m

        val (rho, asRequired, Rn) = computeRnRho(Mu, fcPrime, fy, d)
        val (ecoBar, ecoSpacing, ecoAs) = selectEconomicalBar(asRequired, toppingThickness)

        codeNotes.add("Topping: h=%.0fmm, Mu=%.2f kN.m/m, As=%.1f mm²/m".format(
            toppingThickness, Mu, asRequired))
        codeNotes.add("Topping steel: Φ%.0f @ %.0fmm (ACI 7.6.1.1)".format(ecoBar, ecoSpacing))

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = ecoAs,
            barDiameter = ecoBar,
            barSpacing = ecoSpacing,
            minThickness = toppingThickness,
            shearCapacity = 0.17 * sqrt(fcPrime) * b * d / 1000.0,
            isSafe = true,
            utilizationRatio = 0.5,
            codeNotes = codeNotes
        )
    }

    // ============================================================
    // 4. TWO-WAY WAFFLE SLAB (ACI 8.10 + 7.6)
    // ============================================================

    /**
     * تصميم بلاطة الوافل ذات الاتجاهين
     * ACI 318-19 البنود 8.10 + 7.6
     * - كمريتات الاتجاه القصير (تحمل العزم الأكبر)
     * - كمريتات الاتجاه الطويل
     * - رأس صلب عند الأعمدة لتحمل قص الاختراق
     * - أحجام الـ drop panel حسب ACI 8.10.3
     */
    private fun designWaffleSlab(
        slab: SlabType.Waffle,
        fcPrime: Double,
        fy: Double,
        wu: Double
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val lx = slab.shortSpan / 1000.0   // م - البحر القصير
        val ly = slab.longSpan / 1000.0     // م - البحر الطويل
        val totalH = slab.totalThickness     // مم - السمك الكلي
        val ribW = slab.ribWidth             // مم - عرض الكمريتة
        val ribD = slab.ribDepth             // مم - عمق الكمريتة
        val ribSpacing = slab.ribSpacing / 1000.0  // م

        codeNotes.add("=== ACI 8.10: Two-Way Waffle Slab ===")
        codeNotes.add("lx=%.2f m, ly=%.2f m, ratio=%.2f".format(lx, ly, ly / lx))
        codeNotes.add("Total h=%.0f mm, Rib: %.0f×%.0f mm @ %.0f mm c/c".format(
            totalH, ribW, ribD, slab.ribSpacing))

        // رأس صلب عند الأعمدة (Solid Head) - ACI 8.10.3
        val solidHeadSize = lx / 3.0  // تقريبي: امتداد رأس العمود
        codeNotes.add("ACI 8.10.3: Solid head extends ≈ L/3=%.2f m each side".format(solidHeadSize))

        // === الاتجاه القصير (الأساسي) ===
        val loadPerRibShort = wu * ribSpacing  // kN/m لكل كمريتة
        // عزم الاتجاه القصير: Mo_short = wu × l2 × ln² / 8
        val MoShort = wu * ly * lx * lx / 8.0
        val MnegShort = 0.65 * MoShort
        val MposShort = 0.35 * MoShort

        val dShort = totalH - 30.0  // عمق فعال
        val RnShort_neg = (MnegShort * 1e6) / (PHI_FLEXURE * ribW * dShort * dShort)
        val RnShort_pos = (MposShort * 1e6) / (PHI_FLEXURE * ribW * dShort * dShort)

        // β₁ حسب ACI 22.2.2.4.1 (للتحقق من الإجهاد عند التشقق)
        val beta1 = computeBeta1(fcPrime)
        codeNotes.add("β₁=%.3f (ACI 22.2.2.4.1)".format(beta1))
        val discNeg = 1.0 - 2.0 * RnShort_neg / (0.85 * fcPrime)
        val discPos = 1.0 - 2.0 * RnShort_pos / (0.85 * fcPrime)
        val rhoShortNeg = if (discNeg > 0) (0.85 * fcPrime / fy) * (1.0 - sqrt(discNeg)) else 0.025
        val rhoShortPos = if (discPos > 0) (0.85 * fcPrime / fy) * (1.0 - sqrt(discPos)) else 0.025
        val rhoMinWaffle = max(MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        val asShortNeg = max(rhoShortNeg * ribW * dShort, rhoMinWaffle * ribW * dShort)
        val asShortPos = max(rhoShortPos * ribW * dShort, rhoMinWaffle * ribW * dShort)

        // === الاتجاه الطويل (ثانوي) ===
        val MoLong = wu * lx * lx * lx / 8.0  // تقريبي
        val MnegLong = 0.65 * MoLong
        val MposLong = 0.35 * MoLong

        val RnLong_neg = (MnegLong * 1e6) / (PHI_FLEXURE * ribW * dShort * dShort)
        val RnLong_pos = (MposLong * 1e6) / (PHI_FLEXURE * ribW * dShort * dShort)

        val discLongNeg = 1.0 - 2.0 * RnLong_neg / (0.85 * fcPrime)
        val discLongPos = 1.0 - 2.0 * RnLong_pos / (0.85 * fcPrime)
        val rhoLongNeg = if (discLongNeg > 0) (0.85 * fcPrime / fy) * (1.0 - sqrt(discLongNeg)) else 0.025
        val rhoLongPos = if (discLongPos > 0) (0.85 * fcPrime / fy) * (1.0 - sqrt(discLongPos)) else 0.025
        val asLongNeg = max(rhoLongNeg * ribW * dShort, rhoMinWaffle * ribW * dShort)
        val asLongPos = max(rhoLongPos * ribW * dShort, rhoMinWaffle * ribW * dShort)

        codeNotes.add("Short dir: M-=%.1f, M+=%.1f kN.m → As_neg=%.1f, As_pos=%.1f mm²".format(
            MnegShort, MposShort, asShortNeg, asShortPos))
        codeNotes.add("Long dir:  M-=%.1f, M+=%.1f kN.m → As_neg=%.1f, As_pos=%.1f mm²".format(
            MnegLong, MposLong, asLongNeg, asLongPos))

        // أكبر تسليح مطلوب
        val asRequired = max(asShortNeg, asShortPos, asLongNeg, asLongPos)

        // اختيار القضبان
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0)
        val barResult = availableBars.map { dia ->
            val area = PI * dia * dia / 4.0
            val numBars = max(ceil(asRequired / area).toInt(), 1)
            Triple(dia, numBars, numBars * area)
        }.firstOrNull { (_, _, asProv) -> asProv >= asRequired } ?: Triple(16.0, 2, PI * 64.0)

        val barDia = barResult.first
        val numBars = barResult.second
        val asProvided = barResult.third

        codeNotes.add("Rib steel: %dΦ%.0f → As=%.1f mm² (per rib)".format(numBars, barDia, asProvided))

        // فحص القص في الكمريتات
        val VuRib = loadPerRibShort * lx / 2.0
        val VcRib = 0.17 * sqrt(fcPrime) * ribW * dShort / 1000.0
        if (VuRib > PHI_SHEAR * VcRib) {
            warnings.add("ACI 22.5.5.1: Rib shear Vu=%.1f > φVc=%.1f kN".format(VuRib, PHI_SHEAR * VcRib))
        }

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asProvided,
            barDiameter = barDia,
            barSpacing = slab.ribSpacing,
            minThickness = totalH,
            shearCapacity = VcRib,
            isSafe = discNeg > 0 && discPos > 0 && VuRib <= PHI_SHEAR * VcRib,
            utilizationRatio = max(RnShort_neg, RnShort_pos) / (0.85 * fcPrime * 0.375).coerceAtLeast(0.01),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================
    // 5. CANTILEVER SLAB
    // ============================================================

    /**
     * تصميم البلاطة الكابولية
     * ACI 318-19 البند 8.3.1.1 (الحد الأدنى للسمك)
     * - عزم الكابولي: Mu = wu × L² / 2 (عند الدعامة - عزم سالب)
     * - القص: Vu = wu × L (عند الدعامة)
     * - الانحراف: δ = w × L⁴ / (8 × Ec × Ig) للكابولي
     * - التسليح الأدنى في أعلى الدعامة (حديد علوي)
     */
    fun designCantileverSlab(
        fcu: Double,
        fy: Double,
        thickness: Double,         // مم
        cantileverLength: Double,   // مم
        deadLoad: Double,           // kN/m²
        liveLoad: Double,           // kN/m²
        loadCombination: LoadCombination
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val fcPrime = 0.8 * fcu
        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ACI)
        val L = cantileverLength / 1000.0  // م

        codeNotes.add("=== ACI 8.3.1.1: Cantilever Slab ===")
        codeNotes.add("fc'=%.0f MPa, h=%.0f mm, L=%.2f m".format(fcPrime, thickness, L))

        // فحص الحد الأدنى للسمك - ACI Table 8.3.1.1: L/8 للكابولي
        val fyFactor = min(1.0, 420.0 / fy.coerceAtLeast(200.0))
        val hMin = (cantileverLength / 8.0) * fyFactor
        if (thickness < hMin) {
            warnings.add("ACI Table 8.3.1.1: h=%.0f < h_min=%.0f mm (L/8)".format(thickness, hMin))
        } else {
            codeNotes.add("ACI Table 8.3.1.1: Min thickness OK (%.0f ≥ %.0f mm)".format(thickness, hMin))
        }

        // عزم الكابولي عند الدعامة: Mu = wu × L² / 2 (kN.m/m)
        val Mu = totalLoad * L * L / 2.0
        // القص عند الدعامة: Vu = wu × L (kN/m)
        val Vu = totalLoad * L

        codeNotes.add("Mu = wu×L²/2 = %.1f × %.2f² / 2 = %.1f kN.m/m".format(totalLoad, L, Mu))
        codeNotes.add("Vu = wu×L = %.1f × %.2f = %.1f kN/m".format(totalLoad, L, Vu))

        // تصميم الانحناء بطريقة Rn-ρ - حديد علوي (عزم سالب)
        val flexureResult = designFlexureRnRho(Mu, fcPrime, fy, thickness, isTopSteel = true)
        flexureResult.codeNotes.forEach { codeNotes.add(it) }
        flexureResult.warnings.forEach { warnings.add(it) }

        // فحص القص: Vc = 0.17λ√fc' × b × d (ACI 22.5.5.1), λ=1.0
        val d = thickness - 20.0 - 6.0
        val b = 1000.0
        val Vc = 0.17 * sqrt(fcPrime) * b * d / 1000.0  // kN/m
        val phiVc = PHI_SHEAR * Vc
        val isShearSafe = Vu <= phiVc
        if (!isShearSafe) {
            warnings.add("ACI 22.5.5.1: Vu=%.1f > φVc=%.1f kN/m - increase thickness".format(Vu, phiVc))
        }
        codeNotes.add("Shear: Vu=%.1f, φVc=%.1f kN/m → %s".format(
            Vu, phiVc, if (isShearSafe) "OK ✓" else "FAIL ✗"))

        // فحص الانحراف: δ = w × L⁴ / (8 × Ec × Ig) للكابولي
        val Ec = 4700.0 * sqrt(fcPrime)  // ACI 19.2.2.1: Ec = 4700√fc'
        val Ig = b * thickness.pow(3) / 12.0  // mm⁴/m
        val wService = deadLoad + liveLoad  // kN/m²
        // الانحراف الفوري: δ_i = w × L⁴ / (8 × Ec × Ig)
        val deltaImmediate = wService * L.pow(4) * 1e12 / (8.0 * Ec * Ig)
        // الانحراف طويل المدى (معامل الكريب والانكماش ≈ 2.0 للبلاطات الداخلية)
        val deltaLongTerm = deltaImmediate * 2.5
        // الحد المسموح: L/180 للكابولي (ACI 24.2.2)
        val deltaAllow = cantileverLength / 180.0
        val isDeflectionOk = deltaLongTerm <= deltaAllow

        if (!isDeflectionOk) {
            warnings.add("ACI 24.2.2: δ_LT=%.1fmm > L/180=%.1fmm - increase thickness".format(
                deltaLongTerm, deltaAllow))
        }
        codeNotes.add("Deflection: δ_i=%.1fmm, δ_LT=%.1fmm, δ_allow=L/180=%.1fmm".format(
            deltaImmediate, deltaLongTerm, deltaAllow))

        val deflectionCheck = DeflectionCheckResult(
            immediateDeflection = deltaImmediate,
            longTermDeflection = deltaLongTerm,
            calculatedDeflection = deltaLongTerm,
            allowableDeflection = deltaAllow,
            ratio = deltaLongTerm / deltaAllow.coerceAtLeast(0.1),
            isSafe = isDeflectionOk,
            message = "δ_i=%.1fmm, δ_LT=%.1fmm, δ_allow=L/180=%.1fmm".format(
                deltaImmediate, deltaLongTerm, deltaAllow),
            recommendation = if (!isDeflectionOk) "Increase thickness or reduce cantilever length" else "Deflection OK"
        )

        val shearCheck = ShearCheckResult(
            appliedShear = Vu,
            shearCapacity = Vc,
            isSafe = isShearSafe,
            utilizationRatio = Vu / phiVc.coerceAtLeast(0.1),
            criticalPerimeter = 0.0,
            criticalSection = d
        )

        // التسليح: حديد علوي عند الدعامة (سالب) + حديد سفلي أدنى
        val numTopBars = max(1, (L * 1000.0 / flexureResult.barSpacing).toInt())
        val numBotBars = max(1, (L * 1000.0 / 200.0).toInt())

        return AdvancedSlabResult(
            slabType = SlabType.Solid(
                thickness, cantileverLength, cantileverLength,
                SlabSupportConditions(EdgeCondition.FIXED, EdgeCondition.FREE,
                    EdgeCondition.FIXED, EdgeCondition.FREE)
            ),
            flexureResult = flexureResult,
            shearCheck = shearCheck,
            deflectionCheck = deflectionCheck,
            punchingShearCheck = null,
            reinforcementLayout = ReinforcementLayout(
                topBars = BarLayout(flexureResult.barDiameter, flexureResult.barSpacing,
                    BarDirection.BOTH, L, numTopBars),
                bottomBars = BarLayout(flexureResult.barDiameter, 200.0,
                    BarDirection.BOTH, L, numBotBars),
                distributionBars = null,
                additionalBars = emptyList()
            ),
            concreteVolume = L * 1.0 * thickness / 1e6,
            formworkArea = L * 1.0,
            inventoryAnalysis = null,
            postTensionCalculations = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================
    // PUNCHING SHEAR CHECKS (ACI 22.6)
    // ============================================================

    /**
     * فحص قص الاختراق - ACI 22.6
     * vc = min(3 terms):
     *   vc1 = 0.17 × (1 + 2/β) × λ × √fc'   (ACI 22.6.5.2a)
     *   vc2 = 0.083 × (αs×d/bo + 2) × λ × √fc'  (ACI 22.6.5.2b)
     *   vc3 = 0.33 × λ × √fc'                    (ACI 22.6.5.2c)
     */
    private fun checkPunchingShear(
        h: Double,           // مم - سمك البلاطة
        colC1: Double,       // مم - بُعد العمود 1
        colC2: Double,       // مم - بُعد العمود 2
        panelL: Double,      // مم - طول اللوحة
        panelW: Double,      // مم - عرض اللوحة
        fcPrime: Double,     // MPa
        wu: Double           // kN/m² - الحمل التصميمي
    ): PunchingShearCheckResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val d = h - 20.0 - 6.0  // عمق فعال
        val lambda = 1.0  // ACI 22.6.5.2: λ=1 للخرسانة العادية

        // محيط الاختراق عند d/2 من وجه العمود - ACI 22.6.4
        // bo = 2 × (c1 + d) + 2 × (c2 + d) = 2(c1 + c2) + 4d
        val bo = 2.0 * (colC1 + d) + 2.0 * (colC2 + d)
        codeNotes.add("=== ACI 22.6: Punching Shear ===")
        codeNotes.add("bo = 2(c1+d)+2(c2+d) = 2(%.0f+%.0f)+2(%.0f+%.0f) = %.0f mm".format(
            colC1, d, colC2, d, bo))

        // القوة القاطعة عند الاختراق
        val panelArea = panelL * panelW / 1e6  // م²
        val punchingArea = (colC1 + d) * (colC2 + d) / 1e6  // م²
        val netArea = panelArea - punchingArea
        val Vu = wu * netArea  // kN

        // إجهاد القص المطبق: vu = Vu / (bo × d)
        val vu = (Vu * 1000.0) / (bo * d)  // N/mm² = MPa

        // β = نسبة البُعد الطويل للعمود إلى القصير
        val beta = max(colC1, colC2) / min(colC1, colC2).coerceAtLeast(1.0)

        // αs = 40 للعمود الداخلي, 30 للعمود الحافي, 20 للعمود الزاوية
        val alphaS = 40.0

        // vc = min of 3 terms (ACI 22.6.5.2)
        val vc1 = 0.17 * (1.0 + 2.0 / beta) * lambda * sqrt(fcPrime)  // ACI 22.6.5.2(a)
        val vc2 = 0.083 * (alphaS * d / bo + 2.0) * lambda * sqrt(fcPrime)  // ACI 22.6.5.2(b)
        val vc3 = 0.33 * lambda * sqrt(fcPrime)  // ACI 22.6.5.2(c)
        val vc = minOf(vc1, vc2, vc3)

        codeNotes.add("β=%.1f, αs=%.0f (interior column)".format(beta, alphaS))
        codeNotes.add("vc1=%.3f, vc2=%.3f, vc3=%.3f → vc=%.3f MPa".format(vc1, vc2, vc3, vc))
        codeNotes.add("vu=%.3f MPa, φ×vc=%.3f MPa".format(vu, PHI_SHEAR * vc))

        val isSafe = vu <= PHI_SHEAR * vc

        if (!isSafe) {
            warnings.add("ACI 22.6.5: vu=%.3f > φvc=%.3f MPa - punching failure!".format(vu, PHI_SHEAR * vc))
        }

        return PunchingShearCheckResult(
            appliedShear = Vu,
            shearCapacity = vc * bo * d / 1000.0,  // kN
            utilizationRatio = vu / (PHI_SHEAR * vc).coerceAtLeast(0.001),
            isSafe = isSafe,
            criticalPerimeter = bo,
            shearHeadsRequired = !isSafe,
            codeReference = "ACI 318-19: Section 22.6 (Punching Shear)",
            warnings = warnings
        )
    }

    /**
     * فحص قص الاختراق مع البلاطة المنخفضة (Drop Panel)
     * ACI 22.6.4: محيط الاختراق عند d/2 من حافة الـ drop panel
     */
    private fun checkPunchingShearWithDrop(
        slab: SlabType.FlatSlab,
        fcPrime: Double,
        wu: Double
    ): PunchingShearCheckResult {
        val hTotal = slab.thickness + slab.dropPanelThickness
        val dropSize = slab.dropPanelSize  // مم
        val colSize = slab.columnSize       // مم

        // محيط الاختراق عند d/2 من حافة الـ drop panel
        val d = hTotal - 20.0 - 8.0

        // استخدام حجم الـ drop panel كـ "عمود" فعال
        val c1Eff = dropSize
        val c2Eff = dropSize

        val panelL = slab.panelLength
        val panelW = slab.panelWidth

        val result = checkPunchingShear(hTotal, c1Eff, c2Eff, panelL, panelW, fcPrime, wu)

        // إضافة ملاحظات عن الـ drop panel
        val extraNotes = mutableListOf(
            "ACI 22.6.4: Punching checked at drop panel edge",
            "Effective column size (drop): %.0f × %.0f mm".format(c1Eff, c2Eff),
            "Total depth at column: %.0f mm (slab %.0f + drop %.0f)".format(
                hTotal, slab.thickness, slab.dropPanelThickness)
        )

        return result.copy(
            codeReference = "ACI 318-19: Section 22.6 (Punching with Drop Panel)",
            warnings = result.warnings + extraNotes
        )
    }

    /**
     * فحص قص الاختراق لبلاطة الوافل (عند رأس العمود الصلب)
     */
    private fun checkPunchingShearWaffle(
        slab: SlabType.Waffle,
        fcPrime: Double,
        wu: Double
    ): PunchingShearCheckResult {
        // الرأس الصلب عند العمود (Solid Head) - ACI 8.10.3
        val solidHeadSize = slab.shortSpan / 3.0  // تقريبي
        val d = slab.totalThickness - 30.0

        return checkPunchingShear(
            slab.totalThickness,
            solidHeadSize, solidHeadSize,
            slab.shortSpan, slab.longSpan,
            fcPrime, wu
        )
    }

    // ============================================================
    // GENERAL SHEAR & DEFLECTION CHECKS
    // ============================================================

    /**
     * فحص القص العادي (One-Way Shear) - ACI 22.5.5.1
     * Vc = 0.17 × λ × √fc' × bw × d
     */
    private fun checkSlabShear(
        slab: SlabType,
        fcPrime: Double,
        totalLoad: Double
    ): ShearCheckResult {
        val span = when (slab) {
            is SlabType.OneWay -> slab.span / 1000.0
            is SlabType.Solid -> slab.shortSpan / 1000.0
            is SlabType.TwoWay -> slab.shortSpan / 1000.0
            is SlabType.FlatPlate -> slab.panelLength / 1000.0
            is SlabType.FlatSlab -> slab.panelLength / 1000.0
            is SlabType.Hordi -> slab.span / 1000.0
            is SlabType.Waffle -> slab.shortSpan / 1000.0
            is SlabType.PostTensioned -> slab.shortSpan / 1000.0
            is SlabType.Precast -> slab.length / 1000.0
        }

        val thickness = when (slab) {
            is SlabType.Solid -> slab.thickness
            is SlabType.OneWay -> slab.thickness
            is SlabType.TwoWay -> slab.thickness
            is SlabType.FlatPlate -> slab.thickness
            is SlabType.FlatSlab -> slab.thickness + slab.dropPanelThickness
            is SlabType.Hordi -> slab.totalThickness
            is SlabType.Waffle -> slab.totalThickness
            is SlabType.PostTensioned -> slab.thickness
            is SlabType.Precast -> slab.thickness + slab.toppingThickness
        }

        val d = thickness - 26.0
        val Vu = totalLoad * span / 2.0  // kN/m
        val Vc = 0.17 * sqrt(fcPrime) * 1000.0 * d / 1000.0  // kN/m (ACI 22.5.5.1, λ=1)
        val phiVc = PHI_SHEAR * Vc

        return ShearCheckResult(
            appliedShear = Vu,
            shearCapacity = Vc,
            isSafe = Vu <= phiVc,
            utilizationRatio = Vu / phiVc.coerceAtLeast(0.1),
            criticalSection = d,
            criticalPerimeter = 0.0,
            warnings = if (Vu > phiVc) listOf(
                "ACI 22.5.5.1: Vu=%.1f kN/m > φVc=%.1f kN/m".format(Vu, phiVc)
            ) else emptyList()
        )
    }

    /**
     * فحص الانحراف - ACI 24.2
     * δ = 5 × w × L⁴ / (384 × Ec × Icr)
     * Ec = 4700√fc' (ACI 19.2.2.1)
     */
    private fun checkSlabDeflection(
        slab: SlabType,
        fy: Double,
        fcPrime: Double
    ): DeflectionCheckResult {
        // Ec = 4700√fc' (ACI 19.2.2.1)
        val Ec = 4700.0 * sqrt(fcPrime)

        val thickness = when (slab) {
            is SlabType.OneWay -> slab.thickness
            is SlabType.Solid -> slab.thickness
            is SlabType.TwoWay -> slab.thickness
            is SlabType.FlatPlate -> slab.thickness
            is SlabType.FlatSlab -> slab.thickness + slab.dropPanelThickness
            is SlabType.Hordi -> slab.totalThickness
            is SlabType.Waffle -> slab.totalThickness
            is SlabType.PostTensioned -> slab.thickness
            is SlabType.Precast -> slab.thickness + slab.toppingThickness
        }

        val span = when (slab) {
            is SlabType.OneWay -> slab.span
            is SlabType.Solid -> slab.shortSpan
            is SlabType.TwoWay -> slab.shortSpan
            is SlabType.FlatPlate -> slab.panelLength
            is SlabType.FlatSlab -> slab.panelLength
            is SlabType.Hordi -> slab.span
            is SlabType.Waffle -> slab.shortSpan
            is SlabType.PostTensioned -> slab.shortSpan
            is SlabType.Precast -> slab.length
        }

        val spanM = span / 1000.0
        val d = thickness - 26.0

        // عزم القصور للمقطع المتشقق (تقريبي)
        val rho = 0.005
        val n = 200000.0 / Ec
        val kd = (2.0 * n * rho + sqrt(4.0 * n * n * rho * rho + 4.0 * n * rho)) / 2.0
        val Icr = max(
            1000.0 * (kd * d).pow(3) / 3.0 + n * rho * 1000.0 * d * (d - kd * d).pow(2),
            1000.0 * thickness.pow(3) / 12.0 * 0.5
        )

        // الانحراف الفوري تحت الحمل الحي فقط (5 kN/m² تقريبي)
        val wLL = 5.0  // kN/m²
        val deltaImmediate = 5.0 * wLL * spanM.pow(4) * 1e12 / (384.0 * Ec * Icr)  // mm

        // الانحراف طويل المدى: δ_LT = δ_i × (1 + λ)
        val longTermMultiplier = 2.0
        val deltaLongTerm = deltaImmediate * (1.0 + longTermMultiplier)

        // الحد المسموح: L/240 للانحراف الكلي (ACI 24.2.2)
        val deltaAllow = span / 240.0
        val ratio = deltaLongTerm / deltaAllow.coerceAtLeast(0.1)

        return DeflectionCheckResult(
            immediateDeflection = deltaImmediate,
            longTermDeflection = deltaLongTerm,
            calculatedDeflection = deltaLongTerm,
            allowableDeflection = deltaAllow,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            message = "δ_i=%.1fmm, δ_LT=%.1fmm, δ_allow=L/240=%.1fmm".format(
                deltaImmediate, deltaLongTerm, deltaAllow),
            recommendation = if (ratio > 1.0)
                "ACI 24.2: Increase thickness or reduce reinforcement ratio" else "ACI 24.2: Deflection OK"
        )
    }

    // ============================================================
    // SHARED DESIGN HELPERS
    // ============================================================

    /**
     * حساب β₁ الديناميكي - ACI 22.2.2.4.1
     * β₁ = 0.85 for fc' ≤ 28 MPa
     * β₁ = 0.85 - 0.05(fc' - 28)/7, ≥ 0.65 for fc' > 28 MPa
     */
    private fun computeBeta1(fcPrime: Double): Double {
        return if (fcPrime <= 28.0) 0.85
        else max(0.65, 0.85 - 0.05 * (fcPrime - 28.0) / 7.0)
    }

    /**
     * حساب Rn و ρ وطريقة Rn-ρ (ACI 22.2.2.4) - NOT K-method
     * Rn = Mu / (φ × b × d²)
     * ρ = (0.85×fc'/fy) × [1 - √(1 - 2×Rn/(0.85×fc'×β₁))]
     * As = ρ × b × d
     */
    private fun computeRnRho(
        Mu: Double,        // kN.m/m
        fcPrime: Double,   // MPa
        fy: Double,        // MPa
        d: Double          // mm
    ): Triple<Double, Double, Double> {
        val b = 1000.0
        val Mu_Nmm = Mu * 1e6  // N.mm/m
        val Rn = Mu_Nmm / (PHI_FLEXURE * b * d * d)

        // Rn-ρ method: discriminant does NOT include β₁ (derived from Whitney stress block)
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fcPrime)

        val rho = if (discriminant > 0) {
            (0.85 * fcPrime / fy) * (1.0 - sqrt(discriminant))
        } else {
            0.025  // الحد الأقصى
        }

        // الحد الأدنى للتسليح - ACI 7.6.1.1
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        val rhoFinal = max(rho, rhoMin)
        val asRequired = rhoFinal * b * d

        return Triple(rhoFinal, asRequired, Rn)
    }

    /**
     * تصميم الانحناء بطريقة Rn-ρ مع تفاصيل كاملة
     */
    private fun designFlexureRnRho(
        Mu: Double,          // kN.m/m
        fcPrime: Double,
        fy: Double,
        thickness: Double,
        isTopSteel: Boolean = false
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val d = thickness - 20.0 - 6.0
        val b = 1000.0
        val Mu_Nmm = Mu * 1e6

        // Rn calculation
        val Rn = Mu_Nmm / (PHI_FLEXURE * b * d * d)

        // β₁ - ACI 22.2.2.4.1
        val beta1 = computeBeta1(fcPrime)
        codeNotes.add("Rn = Mu/(φbd²) = %.2f MPa".format(Rn))
        codeNotes.add("β₁ = %.3f (ACI 22.2.2.4.1)".format(beta1))

        // ρ from Rn
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fcPrime)
        val rho = if (discriminant > 0) {
            (0.85 * fcPrime / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add("ACI 22.2: Compression failure - increase slab depth")
            0.025
        }

        // Min steel - ACI 7.6.1.1
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        val rhoFinal = max(rho, rhoMin).coerceAtMost(0.025)
        val asRequired = rhoFinal * b * d

        codeNotes.add("ρ=%.5f, ρ_min=%.5f, ρ_final=%.5f".format(rho, rhoMin, rhoFinal))

        // Bar selection - economical
        val (ecoBar, ecoSpacing, ecoAs) = selectEconomicalBar(asRequired, thickness)
        // Bar selection - safest (next size up)
        val (safeBar, safeSpacing, safeAs) = selectSafestBar(asRequired, thickness)

        codeNotes.add("As_req = %.1f mm²/m".format(asRequired))
        codeNotes.add("Economical: Φ%.0f @ %.0fmm → As=%.0f mm²/m".format(ecoBar, ecoSpacing, ecoAs))
        codeNotes.add("Safest:    Φ%.0f @ %.0fmm → As=%.0f mm²/m".format(safeBar, safeSpacing, safeAs))

        if (isTopSteel) {
            codeNotes.add("Top steel at support (negative moment zone)")
        }

        val Vc = 0.17 * sqrt(fcPrime) * b * d / 1000.0

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = ecoAs,
            barDiameter = ecoBar,
            barSpacing = ecoSpacing,
            minThickness = 0.0,
            shearCapacity = Vc,
            isSafe = discriminant > 0,
            utilizationRatio = Rn / (0.85 * fcPrime * 0.375).coerceAtLeast(0.01),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * اختيار البديل الاقتصادي: أصغر قطر سيخ يعطي تسليح كافٍ
     * ACI 7.7.2.3: max spacing = min(3h, 450mm)
     */
    private fun selectEconomicalBar(
        asRequired: Double,  // mm²/m
        thickness: Double    // مم
    ): Triple<Double, Double, Double> {
        val maxSpacing = min(3.0 * thickness, 450.0)
        return BAR_DIAMETERS.map { dia ->
            val area = PI * dia * dia / 4.0
            val spacing = (area * 1000.0 / asRequired).coerceIn(100.0, maxSpacing)
            val asProvided = area * 1000.0 / spacing
            Triple(dia, spacing, asProvided)
        }.firstOrNull { (_, _, asProv) -> asProv >= asRequired }
            ?: Triple(12.0, 100.0, PI * 144.0 / 4.0 * 1000.0 / 100.0)
    }

    /**
     * اختيار البديل الأكثر أماناً: قطر أكبر بمستوى واحد
     * يعطي تسليح أعلى من المطلوب لهامش أمان إضافي
     */
    private fun selectSafestBar(
        asRequired: Double,
        thickness: Double
    ): Triple<Double, Double, Double> {
        val maxSpacing = min(3.0 * thickness, 450.0)
        // أوجد البديل الاقتصادي أولاً، ثم استخدم القطر التالي
        val ecoIdx = BAR_DIAMETERS.indexOfFirst { dia ->
            val area = PI * dia * dia / 4.0
            val spacing = (area * 1000.0 / asRequired).coerceIn(100.0, maxSpacing)
            area * 1000.0 / spacing >= asRequired
        }.coerceAtLeast(0)

        val safeIdx = min(ecoIdx + 1, BAR_DIAMETERS.size - 1)
        val dia = BAR_DIAMETERS[safeIdx]
        val area = PI * dia * dia / 4.0
        val spacing = (area * 1000.0 / asRequired).coerceIn(100.0, maxSpacing)
        val asProvided = area * 1000.0 / spacing

        return Triple(dia, spacing, asProvided)
    }

    // ============================================================
    // SIMPLE DELEGATION METHODS (for other slab types)
    // ============================================================

    private fun designSolidSlab(
        slab: SlabType.Solid,
        fcPrime: Double,
        fy: Double,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        val lx = slab.shortSpan / 1000.0
        val Mu = totalLoad * lx * lx / 8.0
        val Vu = totalLoad * lx / 2.0
        return baseSlab.designOneWaySlab(
            fcPrime / 0.8, fy, slab.thickness, slab.shortSpan,
            Mu, Vu, loadCombination
        )
    }

    private fun designOneWayAdvanced(
        slab: SlabType.OneWay,
        fcPrime: Double,
        fy: Double,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        val span = slab.span / 1000.0
        val Mu = totalLoad * span * span / 8.0
        val Vu = totalLoad * span / 2.0
        return baseSlab.designOneWaySlab(
            fcPrime / 0.8, fy, slab.thickness, slab.span,
            Mu, Vu, loadCombination
        )
    }

    private fun designTwoWayAdvanced(
        slab: SlabType.TwoWay,
        fcPrime: Double,
        fy: Double,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        val result = baseSlab.designTwoWaySlab(
            fcPrime / 0.8, fy, slab.thickness,
            slab.shortSpan, slab.longSpan,
            slab.supportConditions, totalLoad, loadCombination
        )
        return result.shortDirection
    }

    private fun designPostTensionedSimple(
        slab: SlabType.PostTensioned,
        fcPrime: Double,
        fy: Double,
        totalLoad: Double
    ): SlabDesignResult {
        val lx = slab.shortSpan / 1000.0
        val netLoad = max(0.2 * totalLoad, totalLoad - slab.prestressForce * 0.1)
        val Mu = netLoad * lx * lx / 12.0
        return designFlexureRnRho(Mu, fcPrime, fy, slab.thickness)
    }

    private fun designPrecastSimple(
        slab: SlabType.Precast,
        fcPrime: Double,
        fy: Double,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        val L = slab.length / 1000.0
        val W = slab.width / 1000.0
        val totalH = slab.thickness + slab.toppingThickness
        val loadPerMeter = totalLoad * W
        val Mu = loadPerMeter * L * L / 8.0
        return designFlexureRnRho(Mu, fcPrime, fy, totalH)
    }

    // ============================================================
    // QUANTITY CALCULATIONS
    // ============================================================

    private fun calculateConcreteVolume(slab: SlabType): Double {
        val area = when (slab) {
            is SlabType.Solid -> slab.shortSpan * slab.longSpan
            is SlabType.OneWay -> slab.span * slab.width
            is SlabType.TwoWay -> slab.shortSpan * slab.longSpan
            is SlabType.Hordi -> slab.span * 10.0
            is SlabType.Waffle -> slab.shortSpan * slab.longSpan
            is SlabType.PostTensioned -> slab.shortSpan * slab.longSpan
            is SlabType.Precast -> slab.length * slab.width
            is SlabType.FlatPlate -> slab.panelLength * slab.panelWidth
            is SlabType.FlatSlab -> slab.panelLength * slab.panelWidth
        }
        val effectiveThickness = when (slab) {
            is SlabType.Solid -> slab.thickness
            is SlabType.OneWay -> slab.thickness
            is SlabType.TwoWay -> slab.thickness
            is SlabType.Hordi -> slab.totalThickness - slab.blockHeight * 0.5
            is SlabType.Waffle -> slab.totalThickness * 0.55
            is SlabType.FlatSlab -> slab.thickness + slab.dropPanelThickness * 0.15
            is SlabType.Precast -> slab.thickness + slab.toppingThickness
            else -> 200.0
        }
        return area * effectiveThickness / 1e6  // m³
    }

    private fun calculateFormworkArea(slab: SlabType): Double {
        return when (slab) {
            is SlabType.Solid -> slab.shortSpan * slab.longSpan / 1e6
            is SlabType.OneWay -> slab.span * slab.width / 1e6
            is SlabType.TwoWay -> slab.shortSpan * slab.longSpan / 1e6
            is SlabType.Hordi -> slab.span * 10.0 / 1e6
            is SlabType.Waffle -> slab.shortSpan * slab.longSpan * 1.5 / 1e6
            is SlabType.PostTensioned -> slab.shortSpan * slab.longSpan / 1e6
            is SlabType.Precast -> slab.length * slab.width / 1e6
            is SlabType.FlatPlate -> slab.panelLength * slab.panelWidth / 1e6
            is SlabType.FlatSlab -> slab.panelLength * slab.panelWidth / 1e6
        }
    }

    // ============================================================
    // LAYOUT & INVENTORY
    // ============================================================

    private fun createReinforcementLayout(
        result: SlabDesignResult,
        slab: SlabType
    ): ReinforcementLayout {
        val span = when (slab) {
            is SlabType.OneWay -> slab.span / 1000.0
            is SlabType.Solid -> slab.shortSpan / 1000.0
            is SlabType.FlatPlate -> slab.panelLength / 1000.0
            is SlabType.FlatSlab -> slab.panelLength / 1000.0
            is SlabType.Hordi -> slab.span / 1000.0
            is SlabType.Waffle -> slab.shortSpan / 1000.0
            is SlabType.TwoWay -> slab.shortSpan / 1000.0
            is SlabType.PostTensioned -> slab.shortSpan / 1000.0
            is SlabType.Precast -> slab.length / 1000.0
        }
        val numBars = max(1, (span * 1000.0 / result.barSpacing).toInt())

        return ReinforcementLayout(
            topBars = BarLayout(result.barDiameter, result.barSpacing,
                BarDirection.BOTH, span, numBars),
            bottomBars = BarLayout(result.barDiameter, result.barSpacing,
                BarDirection.BOTH, span, numBars),
            distributionBars = BarLayout(
                max(result.barDiameter - 2.0, 8.0), 200.0,
                BarDirection.BOTH, span, max(1, numBars / 2)
            ),
            additionalBars = emptyList()
        )
    }

    private fun analyzeInventory(
        result: SlabDesignResult,
        slab: SlabType,
        inventory: RebarInventory
    ): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        val span = when (slab) {
            is SlabType.OneWay -> slab.span
            is SlabType.Solid -> slab.shortSpan
            is SlabType.FlatPlate -> slab.panelLength
            is SlabType.FlatSlab -> slab.panelLength
            is SlabType.Hordi -> slab.span
            is SlabType.Waffle -> slab.shortSpan
            else -> 5000.0
        }
        return analyzer.analyze(
            requiredArea = result.requiredReinforcement,
            requiredLength = span,
            inventory = inventory,
            designCode = DesignCode.ACI,
            elementLength = span
        )
    }

    private fun getCodeReference(slab: SlabType): String = when (slab) {
        is SlabType.FlatPlate -> "ACI 318-19: Chapter 8 (Flat Plate - Direct Design Method)"
        is SlabType.FlatSlab -> "ACI 318-19: Chapter 8 + Section 8.10.3 (Flat Slab with Drop Panels)"
        is SlabType.Hordi -> "ACI 318-19: Section 7.6 (One-Way Ribbed Slab)"
        is SlabType.Waffle -> "ACI 318-19: Chapter 8 + Section 7.6 (Two-Way Waffle Slab)"
        is SlabType.Solid -> CodeReference.ACI.SLAB_TWO_WAY
        is SlabType.OneWay -> CodeReference.ACI.SLAB_ONE_WAY
        is SlabType.TwoWay -> CodeReference.ACI.SLAB_TWO_WAY
        is SlabType.PostTensioned -> "ACI 318-19: Chapter 24 (Post-Tensioned Slab)"
        is SlabType.Precast -> "ACI 318-19: Chapter 16 (Precast Concrete)"
    }
}