package com.civileg.app.domain.entities

/**
 * مجموعات التحميل حسب الكود المصري ECP 203-2020 البند 2-3-1
 * والكود الأمريكي ACI 318-19 البند 5.3
 *
 * معاملات التحويل (factor) تُستخدم لتحويل الحمل التصميمي (Factored) إلى حمل الخدمة (Service):
 *   P_service = P_factored / factor
 *
 * ECP 203: 1.4D+1.6L → factor ≈ 1.5, 1.4D → factor = 1.4
 * ACI 318: 1.2D+1.6L → factor ≈ 1.4, 1.2D → factor = 1.2
 */
enum class LoadCombination(val factor: Double, val description: String) {
    DEAD_ONLY(1.4, "Dead Load Only (1.4D / 1.2D)"),
    DEAD_LIVE(1.5, "Dead + Live Load (1.4D+1.6L / 1.2D+1.6L)"),
    DEAD_LIVE_WIND(1.5, "Dead + Live + Wind"),
    DEAD_LIVE_EARTHQUAKE(1.4, "Dead + Live + Earthquake"),
    DEAD_EARTHQUAKE(1.2, "Dead + Earthquake");

    /**
     * معامل التحويل حسب الكود - يُستخدم لتحويل الحمل التصميمي إلى حمل الخدمة
     * P_service = P_factored / getFactorForCode(code)
     */
    fun getFactorForCode(code: DesignCode): Double = when(code) {
        DesignCode.ECP -> when(this) {
            // ECP 203 البند 2-3-1: 1.4D, 1.4D+1.6L, 1.4D+1.6L+0.4W, 1.4D+1.0L+1.4W, 0.9D+1.4W, 1.4D+1.0L+1.0E
            DEAD_ONLY -> 1.4          // 1.4D
            DEAD_LIVE -> 1.5          // متوسط: (1.4D+1.6L) / (D+L) ≈ 1.5 عندما D≈L
            DEAD_LIVE_WIND -> 1.4     // 1.4D + 1.6L + 0.4W (الرياح ثانوية)
            DEAD_LIVE_EARTHQUAKE -> 1.4 // 1.4D + 1.0L + 1.0E
            DEAD_EARTHQUAKE -> 0.9    // 0.9D + 1.4E (أو 0.9D - 1.4E)
        }
        DesignCode.ACI -> when(this) {
            // ACI 318-19 البند 5.3: 1.2D, 1.2D+1.6L, 1.2D+1.6L+0.5W, 1.2D+1.0L+1.0W, 0.9D+1.6W, 1.2D+1.0E
            DEAD_ONLY -> 1.2
            DEAD_LIVE -> 1.4          // متوسط: (1.2D+1.6L) / (D+L) ≈ 1.4
            DEAD_LIVE_WIND -> 1.2     // 1.2D + 1.6L + 0.5W
            DEAD_LIVE_EARTHQUAKE -> 1.2 // 1.2D + 1.0L + 1.0E
            DEAD_EARTHQUAKE -> 0.9    // 0.9D + 1.0E
        }
        DesignCode.SBC -> when(this) {
            // SBC 304-2018 يتبع ACI 318
            DEAD_ONLY -> 1.2
            DEAD_LIVE -> 1.4
            DEAD_LIVE_WIND -> 1.2
            DEAD_LIVE_EARTHQUAKE -> 1.2
            DEAD_EARTHQUAKE -> 0.9
        }
    }

    /**
     * معاملات التحميل الكاملة (للتحقق من التصميم)
     * يُرجع زوج (γ_D, γ_L) للتحميل الأولي
     */
    fun getLoadFactors(code: DesignCode): Pair<Double, Double> = when(code) {
        DesignCode.ECP -> when(this) {
            DEAD_ONLY -> 1.4 to 0.0
            DEAD_LIVE -> 1.4 to 1.6
            DEAD_LIVE_WIND -> 1.4 to 1.6
            DEAD_LIVE_EARTHQUAKE -> 1.4 to 1.0
            DEAD_EARTHQUAKE -> 0.9 to 0.0
        }
        DesignCode.ACI, DesignCode.SBC -> when(this) {
            DEAD_ONLY -> 1.2 to 0.0
            DEAD_LIVE -> 1.2 to 1.6
            DEAD_LIVE_WIND -> 1.2 to 1.6
            DEAD_LIVE_EARTHQUAKE -> 1.2 to 1.0
            DEAD_EARTHQUAKE -> 0.9 to 0.0
        }
    }

    companion object {
        fun getByCode(code: DesignCode): List<LoadCombination> = when(code) {
            DesignCode.ECP -> listOf(DEAD_ONLY, DEAD_LIVE, DEAD_LIVE_EARTHQUAKE)
            DesignCode.ACI -> listOf(DEAD_ONLY, DEAD_LIVE, DEAD_LIVE_WIND, DEAD_EARTHQUAKE)
            DesignCode.SBC -> listOf(DEAD_ONLY, DEAD_LIVE, DEAD_LIVE_EARTHQUAKE)
        }
    }
}
