package com.civileg.core.calculations.entities

/**
 * مجموعات التحميل حسب الكود المصري ECP 203-2020 البند 2-3-1
 * والكود الأمريكي ACI 318-19 البند 5.3
 */
enum class LoadCombination(val factor: Double, val description: String) {
    DEAD_ONLY(1.4, "Dead Load Only (1.4D / 1.2D)"),
    DEAD_LIVE(1.5, "Dead + Live Load (1.4D+1.6L / 1.2D+1.6L)"),
    DEAD_LIVE_WIND(1.5, "Dead + Live + Wind"),
    DEAD_LIVE_EARTHQUAKE(1.4, "Dead + Live + Earthquake"),
    DEAD_EARTHQUAKE(1.2, "Dead + Earthquake");

    fun getFactorForCode(code: DesignCode): Double = when(code) {
        DesignCode.ECP -> when(this) {
            DEAD_ONLY -> 1.4
            DEAD_LIVE -> 1.5
            DEAD_LIVE_WIND -> 1.4
            DEAD_LIVE_EARTHQUAKE -> 1.4
            DEAD_EARTHQUAKE -> 0.9
        }
        DesignCode.ACI, DesignCode.SBC -> when(this) {
            DEAD_ONLY -> 1.2
            DEAD_LIVE -> 1.4
            DEAD_LIVE_WIND -> 1.2
            DEAD_LIVE_EARTHQUAKE -> 1.2
            DEAD_EARTHQUAKE -> 0.9
        }
    }

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
