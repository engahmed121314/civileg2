package com.civileg.core.engine.boq

import kotlin.math.PI

/**
 * محرك تفريد حديد التسليح وجداول BBS.
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * // المرجع: جدول أوزان أسياخ الصلب القياسية (kg/m).
 * // وزن المتر الطولي = d² × 0.006165 حيث d بالملم (المعادلة المشتقة من π/4 × d² × 7850 / 1e6).
 * // الأقطار القياسية: φ8, φ10, φ12, φ14, φ16, φ18, φ20, φ22, φ25, φ28, φ32.
 *
 * // ECP 203 / مواصفات تفريد الحديد (Bar Bending Schedule) المعتمدة.
 */
object RebarTakeoffEngine {

    /** كثافة الفولاذ بالكغ/م³ (AISC / مواصفات الصلب الإنشائي) */
    private const val STEEL_DENSITY_KG_PER_M3 = 7850.0

    /** ثابت التحويل: وزن المتر الطولي بالكيلوغرام لكل مم² من قطر السيخ.
     *  π/4 × 7850 / 1e6 = 0.006165 كغ/م لكل مم² */
    private const val UNIT_WEIGHT_FACTOR = 0.006165

    /** الم tillegg لكل ثني 90° بالملم = 6 × قطر السيخ (حسب المواصفات الهندسية الشائعة) */
    private const val BEND_90_ALLOWANCE_FACTOR = 6.0

    // =========================================================================
    // 1. وزن المتر الطولي للسيخ (Unit Weight per Meter)
    // =========================================================================

    /**
     * حساب وزن المتر الطولي للسيخ بالكغ/م.
     *
     * // W = d² × 0.006165
     * // مشتق من: W = (π/4 × d²) × 7850 / 1,000,000  (حيث d بالملم، النتيجة كغ/م)
     */
    fun unitWeightKgPerMeter(diameterMm: Double): Double {
        require(diameterMm > 0.0) { "Bar diameter must be > 0" }
        return diameterMm * diameterMm * UNIT_WEIGHT_FACTOR
    }

    // =========================================================================
    // 2. وزن السيخ الإجمالي (Total Bar Weight)
    // =========================================================================

    /**
     * حساب وزن سيخ واحد بطول معين.
     */
    fun totalBarWeightKg(diameterMm: Double, lengthM: Double): Double {
        require(diameterMm > 0.0) { "Bar diameter must be > 0" }
        require(lengthM > 0.0) { "Bar length must be > 0" }
        return unitWeightKgPerMeter(diameterMm) * lengthM
    }

    /**
     * حساب وزن مجموعة من الأسياخ بنفس القطر والطول.
     */
    fun totalRebarWeightKg(diameterMm: Double, lengthM: Double, count: Int): Double {
        require(diameterMm > 0.0) { "Bar diameter must be > 0" }
        require(lengthM > 0.0) { "Bar length must be > 0" }
        require(count > 0) { "Count must be > 0" }
        return totalBarWeightKg(diameterMm, lengthM) * count
    }

    // =========================================================================
    // 3. تفريد الحديد مجمّع حسب القطر (Group by Diameter)
    // =========================================================================

    /**
     * تجميع إدخالات الحديد حسب القطر وحساب الإجماليات لكل قطر.
     */
    fun groupByDiameter(entries: List<RebarEntry>): List<RebarTakeoffResult> {
        require(entries.isNotEmpty()) { "Entries list must not be empty" }
        require(entries.all { it.diameterMm > 0.0 }) { "All diameters must be > 0" }
        require(entries.all { it.lengthM > 0.0 }) { "All lengths must be > 0" }
        require(entries.all { it.count > 0 }) { "All counts must be > 0" }

        return entries.groupBy { it.diameterMm }.map { entry ->
            val diameter = entry.key
            val groupEntries = entry.value
            val totalCount = groupEntries.sumOf { it.count }
            val totalLengthM = groupEntries.sumOf { it.count * it.lengthM }
            val totalWeightKg = groupEntries.sumOf {
                it.count * it.lengthM * unitWeightKgPerMeter(it.diameterMm)
            }
            RebarTakeoffResult(
                diameterMm = diameter,
                unitWeightKgPerMeter = unitWeightKgPerMeter(diameter),
                totalLengthM = totalLengthM,
                totalCount = totalCount,
                totalWeightKg = totalWeightKg
            )
        }
    }

    // =========================================================================
    // 4. طول السيخ معDeutschات التقوس (Bar Length with Bends)
    // =========================================================================

    /**
     * حساب طول السيخ الكلي inclusiefDeutschات التقوس (bends).
     *
     * // L_total = L_clear + (num_bends × bend_allowance)
     * // حيث bend_allowance لكل ثني 90° = 6 × d (الم specs الهندسية الشائعة)
     */
    fun barLengthWithBendsMm(clearLengthMm: Double, diameterMm: Double, numBends90: Int): Double {
        require(clearLengthMm > 0.0) { "Clear length must be > 0" }
        require(diameterMm > 0.0) { "Bar diameter must be > 0" }
        require(numBends90 >= 0) { "Number of bends must be >= 0" }

        val bendAllowanceMm = BEND_90_ALLOWANCE_FACTOR * diameterMm * numBends90
        return clearLengthMm + bendAllowanceMm
    }

    // =========================================================================
    // 5. جدول التفريد BBS — Bar Bending Schedule Summary
    // =========================================================================

    /**
     * توليد ملخص جدول التفريد BBS من قائمة إدخالات الحديد.
     * يعيد النتائج مجمّعة حسب القطر.
     */
    fun bbsSummary(entries: List<RebarEntry>): List<RebarTakeoffResult> {
        return groupByDiameter(entries)
    }
}
