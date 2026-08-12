package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult

/**
 * واجهة موحدة لتصميم القواعد حسب أي كود إنشائي
 * تغطي: القواعد المنفصلة، التحقق من قص الاختراق، وحساب التسليح
 */
interface FootingDesign {
    
    /**
     * تصميم قاعدة منفصلة تحت عمود مع مراعاة حدود الجار
     */
    fun designIsolatedFooting(
        fcu: Double,
        fy: Double,
        columnWidth: Double,      // mm
        columnDepth: Double,      // mm
        axialLoad: Double,        // kN
        momentX: Double,          // kN.m
        momentY: Double,          // kN.m
        soilBearingCapacity: Double, // kPa
        footingDepth: Double,     // mm
        loadCombination: LoadCombination,
        constraints: BoundaryConstraints = BoundaryConstraints()
    ): FootingDesignResult

    /**
     * التحقق من قص الاختراق (Punching Shear)
     */
    fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double,  // kN
        loadCombination: LoadCombination
    ): ShearCheckResult

    /**
     * حساب تسليح القاعدة
     */
    fun calculateFootingReinforcement(
        fcu: Double,
        fy: Double,
        footingWidth: Double,
        footingLength: Double,
        effectiveDepth: Double,
        designMoment: Double,  // kN.m/m
        direction: FootingDirection
    ): ReinforcementResult

    /**
     * تصميم قاعدة مشتركة لعمودين
     */
    fun designCombinedFooting(
        fcu: Double,
        fy: Double,
        axialLoad1: Double,
        axialLoad2: Double,
        distanceBetweenColumns: Double, // mm
        soilBearingCapacity: Double,
        footingDepth: Double,
        loadCombination: LoadCombination,
        columnWidth: Double = 400.0,   // mm
        columnDepth: Double = 400.0    // mm
    ): FootingDesignResult

    /**
     * تصميم لبشة (Raft Foundation)
     */
    fun designRaftFoundation(
        fcu: Double,
        fy: Double,
        totalLoads: Double,
        totalArea: Double,
        moments: Pair<Double, Double>,
        soilBearingCapacity: Double,
        raftThickness: Double
    ): FootingDesignResult

    /**
     * تصميم Pile Cap
     */
    fun designPileCap(
        fcu: Double,
        fy: Double,
        pileLoad: Double,
        numberOfPiles: Int,
        pileDiameter: Double,
        columnLoads: Double
    ): FootingDesignResult

    // حدود الكود
    fun getMinFootingThickness(): Double
    fun getMinCover(): Double
    fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double
}

data class BoundaryConstraints(
    val maxLeft: Double? = null,   // mm (Distance from column center to left edge)
    val maxRight: Double? = null,  // mm
    val maxTop: Double? = null,    // mm
    val maxBottom: Double? = null, // mm
    val isCornerColumn: Boolean = false,
    val isEdgeColumn: Boolean = false
)

data class FootingDesignResult(
    val requiredWidth: Double,          // mm
    val requiredLength: Double,         // mm
    val requiredThickness: Double,      // mm
    val soilPressure: Double,           // kPa
    val maxSoilPressure: Double,        // kPa (مع العزم)
    val reinforcement: ReinforcementResult,
    val punchingShearCheck: ShearCheckResult,
    val isSafe: Boolean,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
)

enum class FootingDirection { SHORT, LONG }
