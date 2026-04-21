package com.civileg.app.domain.calculations.steel

/**
 * محرك تصميم المنشآت المعدنية (Steel Design) 
 * يدعم التحقق من القطاعات المدرفلة (Hot Rolled) وفقاً لـ LRFD
 */
class SteelDesignEngine {

    data class SteelCheckResult(
        val utilizationRatio: Double,
        val isSafe: Boolean,
        val classification: String
    )

    fun checkBeamCapacity(
        mu: Double, // kNm (Ultimate Moment)
        phi: Double = 0.9,
        fy: Double, // MPa (Steel Grade 37, 44, 52)
        zx: Double  // Plastic Section Modulus (cm3)
    ): SteelCheckResult {
        // Mn = Fy * Zx
        val nominalCapacity = (fy * zx * 1000) / 1e6 // التحويل لـ kNm
        val designCapacity = phi * nominalCapacity
        
        val ratio = mu / designCapacity
        
        return SteelCheckResult(
            utilizationRatio = ratio,
            isSafe = ratio <= 1.0,
            classification = if (ratio < 0.8) "Economic" else "Optimized"
        )
    }

    // حساب طول الانبعاج (Buckling Length)
    fun calculateSlenderness(k: Double, L: Double, r: Double): Double {
        return (k * L) / r
    }
}