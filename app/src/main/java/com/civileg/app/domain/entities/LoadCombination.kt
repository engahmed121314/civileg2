package com.civileg.app.domain.entities

enum class LoadCombination(val factor: Double, val description: String) {
    DEAD_ONLY(1.4, "Dead Load Only"),
    DEAD_LIVE(1.2, "Dead + Live Load"),
    DEAD_LIVE_WIND(1.2, "Dead + Live + Wind"),
    DEAD_LIVE_EARTHQUAKE(1.0, "Dead + Live + Earthquake"),
    DEAD_EARTHQUAKE(0.9, "Dead + Earthquake");
    
    fun getFactorForCode(code: DesignCode): Double = when(code) {
        DesignCode.ECP -> when(this) {
            DEAD_ONLY -> 1.4
            DEAD_LIVE -> 1.5 // (1.4D + 1.6L)/2 approx or based on ratio
            else -> 1.2
        }
        DesignCode.ACI, DesignCode.SBC -> when(this) {
            DEAD_ONLY -> 1.2
            DEAD_LIVE -> 1.4 // (1.2D + 1.6L)/2 approx
            else -> 1.0
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
