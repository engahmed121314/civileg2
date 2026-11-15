package com.civileg.core.engine.boq

import kotlin.math.max

/**
 * محرك حساب كميات الخرسانة والشدة الخشبية للعناصر الإنشائية.
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * // المرجع: مواصفات حساب الكميات الإنشائية (Bill of Quantities).
 * // ECP 203 / أبعاد القطاعات الإنشائية المعتمدة.
 *
 * // الوحدات: الأبعاد بالملم، الحجم بالملم³ وم³، مساحة الشدة بالملم² وم².
 */
object ConcreteQuantityEngine {

    // =========================================================================
    // 1. حجم الخرسانة — الكمرة المستطيلة (Rectangular Beam)
    // =========================================================================

    /**
     * حساب حجم الكمرة المستطيلة بالملم³.
     *
     * // V = b × h × L
     * // حيث b = العرض، h = الارتفاع، L = الطول
     */
    fun rectangularBeamVolumeMm3(widthMm: Double, heightMm: Double, lengthMm: Double): Double {
        require(widthMm > 0.0) { "Beam width must be > 0" }
        require(heightMm > 0.0) { "Beam height must be > 0" }
        require(lengthMm > 0.0) { "Beam length must be > 0" }
        return widthMm * heightMm * lengthMm
    }

    /**
     * حساب حجم الكمرة المستطيلة بالأمتار المكعبة.
     */
    fun rectangularBeamVolumeM3(widthMm: Double, heightMm: Double, lengthMm: Double): Double {
        return rectangularBeamVolumeMm3(widthMm, heightMm, lengthMm) / 1e9
    }

    /**
     * الحجم الإجمالي لعدد من الكاميرات بأبعاد متطابقة.
     */
    fun totalBeamVolumeM3(widthMm: Double, heightMm: Double, lengthMm: Double, count: Int): Double {
        require(count > 0) { "Count must be > 0" }
        return rectangularBeamVolumeM3(widthMm, heightMm, lengthMm) * count
    }

    // =========================================================================
    // 2. حجم الخرسانة — العمود المستطيل (Rectangular Column)
    // =========================================================================

    /**
     * حساب حجم العمود المستطيل بالملم³.
     *
     * // V = b × h × H
     * // حيث b = العرض، h = العمق، H = الارتفاع الصافي
     */
    fun rectangularColumnVolumeMm3(widthMm: Double, depthMm: Double, heightMm: Double): Double {
        require(widthMm > 0.0) { "Column width must be > 0" }
        require(depthMm > 0.0) { "Column depth must be > 0" }
        require(heightMm > 0.0) { "Column height must be > 0" }
        return widthMm * depthMm * heightMm
    }

    /**
     * حساب حجم العمود المستطيل بالأمتار المكعبة.
     */
    fun rectangularColumnVolumeM3(widthMm: Double, depthMm: Double, heightMm: Double): Double {
        return rectangularColumnVolumeMm3(widthMm, depthMm, heightMm) / 1e9
    }

    // =========================================================================
    // 3. حجم الخرسانة — البلاطة المصمتة (Solid Slab)
    // =========================================================================

    /**
     * حساب حجم البلاطة المصمتة بالملم³.
     *
     * // V = L × W × t
     * // حيث L = الطول، W = العرض، t = السماكة
     */
    fun solidSlabVolumeMm3(lengthMm: Double, widthMm: Double, thicknessMm: Double): Double {
        require(lengthMm > 0.0) { "Slab length must be > 0" }
        require(widthMm > 0.0) { "Slab width must be > 0" }
        require(thicknessMm > 0.0) { "Slab thickness must be > 0" }
        return lengthMm * widthMm * thicknessMm
    }

    /**
     * حساب حجم البلاطة المصمتة بالأمتار المكعبة.
     */
    fun solidSlabVolumeM3(lengthMm: Double, widthMm: Double, thicknessMm: Double): Double {
        return solidSlabVolumeMm3(lengthMm, widthMm, thicknessMm) / 1e9
    }

    // =========================================================================
    // 4. مساحة الشدة الخشبية — جوانب الكمرة (Beam Formwork Sides)
    // =========================================================================

    /**
     * مساحة جوانب الكمرة الجانبية بالملم² (دون الأسفل والسطح).
     *
     * // Area = 2 × (b + h) × L
     */
    fun beamFormworkSidesAreaMm2(widthMm: Double, heightMm: Double, lengthMm: Double): Double {
        require(widthMm > 0.0) { "Beam width must be > 0" }
        require(heightMm > 0.0) { "Beam height must be > 0" }
        require(lengthMm > 0.0) { "Beam length must be > 0" }
        return 2.0 * (widthMm + heightMm) * lengthMm
    }

    /**
     * مساحة جوانب الكمرة بالأمتار المربعة.
     */
    fun beamFormworkSidesAreaM2(widthMm: Double, heightMm: Double, lengthMm: Double): Double {
        return beamFormworkSidesAreaMm2(widthMm, heightMm, lengthMm) / 1e6
    }

    /**
     * مساحة سطح الكمرة السفلي (Soffit) بالملم².
     *
     * // Area = b × L
     */
    fun beamSoffitAreaMm2(widthMm: Double, lengthMm: Double): Double {
        require(widthMm > 0.0) { "Beam width must be > 0" }
        require(lengthMm > 0.0) { "Beam length must be > 0" }
        return widthMm * lengthMm
    }

    /**
     * مساحة سطح الكمرة السفلي بالأمتار المربعة.
     */
    fun beamSoffitAreaM2(widthMm: Double, lengthMm: Double): Double {
        return beamSoffitAreaMm2(widthMm, lengthMm) / 1e6
    }

    // =========================================================================
    // 5. مساحة الشدة الخشبية — جوانب العمود (Column Formwork Sides)
    // =========================================================================

    /**
     * مساحة جوانب العمود بالملم² (دون الرأس والقاعدة).
     *
     * // Area = 2 × (b + h) × H
     */
    fun columnFormworkSidesAreaMm2(widthMm: Double, depthMm: Double, heightMm: Double): Double {
        require(widthMm > 0.0) { "Column width must be > 0" }
        require(depthMm > 0.0) { "Column depth must be > 0" }
        require(heightMm > 0.0) { "Column height must be > 0" }
        return 2.0 * (widthMm + depthMm) * heightMm
    }

    /**
     * مساحة جوانب العمود بالأمتار المربعة.
     */
    fun columnFormworkSidesAreaM2(widthMm: Double, depthMm: Double, heightMm: Double): Double {
        return columnFormworkSidesAreaMm2(widthMm, depthMm, heightMm) / 1e6
    }

    // =========================================================================
    // 6. مساحة الشدة الخشبية — سطح البلاطة (Slab Soffit)
    // =========================================================================

    /**
     * مساحة سطح البلاطة السفلي بالملم².
     *
     * // Area = L × W
     */
    fun slabSoffitAreaMm2(lengthMm: Double, widthMm: Double): Double {
        require(lengthMm > 0.0) { "Slab length must be > 0" }
        require(widthMm > 0.0) { "Slab width must be > 0" }
        return lengthMm * widthMm
    }

    /**
     * مساحة سطح البلاطة بالأمتار المربعة.
     */
    fun slabSoffitAreaM2(lengthMm: Double, widthMm: Double): Double {
        return slabSoffitAreaMm2(lengthMm, widthMm) / 1e6
    }

    // =========================================================================
    // 7. 통합 الكميات — BOQ Summary
    // =========================================================================

    /**
     * المجموع الإجمالي لأحجام خرسانية متعددة بالأمتار المكعبة.
     */
    fun totalConcreteVolumeM3(volumesM3: List<Double>): Double {
        return volumesM3.sumOf { max(0.0, it) }
    }

    /**
     * المجموع الإجمالي لمساحات الشدة الخشبية بالأمتار المربعة.
     */
    fun totalFormworkAreaM2(areasM2: List<Double>): Double {
        return areasM2.sumOf { max(0.0, it) }
    }
}
