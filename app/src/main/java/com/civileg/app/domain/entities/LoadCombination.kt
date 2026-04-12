package com.civileg.app.domain.entities

enum class LoadCombination(val factor: Double, val description: String) {
    DEAD_ONLY(1.4, "Dead Load Only"),
    DEAD_LIVE(1.2, "Dead + Live Load"),
    DEAD_LIVE_WIND(1.2, "Dead + Live + Wind"),
    DEAD_LIVE_EARTHQUAKE(1.0, "Dead + Live + Earthquake"),
    DEAD_EARTHQUAKE(0.9, "Dead + Earthquake");
    
    companion object {
        fun getByCode(code: DesignCode): List<LoadCombination> = when(code) {
            DesignCode.ECP -> listOf(DEAD_ONLY, DEAD_LIVE, DEAD_LIVE_EARTHQUAKE)
            DesignCode.ACI -> listOf(DEAD_ONLY, DEAD_LIVE, DEAD_LIVE_WIND, DEAD_EARTHQUAKE)
            DesignCode.SBC -> listOf(DEAD_ONLY, DEAD_LIVE, DEAD_LIVE_EARTHQUAKE)
        }
    }
}
