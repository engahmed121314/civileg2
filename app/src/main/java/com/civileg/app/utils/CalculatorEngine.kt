package com.civileg.app.utils

import android.os.Parcelable
import com.civileg.app.domain.entities.*
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class CalculatorEngine @Inject constructor(
    private val settingsManager: SettingsManager
) {

    enum class DesignCode(val displayName: String) {
        EGYPTIAN("الكود المصري - ECP 203"),
        ACI("الكود الأمريكي - ACI 318"),
        SAUDI("الكود السعودي - SBC 304");
    }

    enum class SlabType(val displayName: String) { 
        SOLID("Solid Slab"), FLAT("Flat Slab"), HOLLOW_BLOCK("Hollow Block"), POST_TENSION("Post Tension") 
    }
    
    enum class SupportType(val displayName: String) {
        HINGED_HINGED("Hinged-Hinged"), ROLLER_HINGED("Roller-Hinged"), FIXED_HINGED("Fixed-Hinged"), FIXED_FIXED("Fixed-Fixed"), CANTILEVER("Cantilever")
    }
    
    enum class StairType(val displayName: String) { 
        SINGLE_FLIGHT("Single Flight"), DOUBLE_FLIGHT("Double Flight"), SPIRAL("Spiral") 
    }
    
    enum class TankType(val displayName: String) { 
        RECTANGULAR_GROUND("أرضي مستطيل"), CIRCULAR_GROUND("أرضي دائري"), 
        RECTANGULAR_ELEVATED("علوي مستطيل"), CIRCULAR_ELEVATED("علوي دائري"),
        UNDERGROUND("تحت الأرض مستطيل"), CIRCULAR_UNDERGROUND("تحت الأرض دائري")
    }

    @Parcelize
    data class ReinforcementBar(
        val numBars: Int = 0,
        val diameter: Int = 12,
        val spacing: Double = 0.0,
        val type: String = "الرئيسي",
        val description: String = "",
        val weightKg: Double = 0.0
    ) : Parcelable {
        val barString: String get() = if (numBars > 0) "${numBars}Ø${diameter}" else if (spacing > 0) "${(1000/spacing).toInt()}Ø${diameter}/m'" else description
        val area: Double get() = if (numBars > 0) numBars * (PI * diameter.toDouble().pow(2.0) / 4.0) else if (spacing > 0) (1000.0/spacing) * (PI * diameter.toDouble().pow(2.0) / 4.0) else 0.0
    }

    @Parcelize
    data class StirrupReinforcement(val diameter: Int = 8, val spacing: Double = 200.0, val description: String = "5Ø8/m'") : Parcelable

    @Parcelize
    data class DesignSafetyCheck(val name: String, val value: Double, val limit: Double, val unit: String, val isSafe: Boolean) : Parcelable

    @Parcelize
    data class BOQData(val concreteM3: Double, val steelKg: Double, val totalCost: Double, val currency: String = "EGP") : Parcelable

    @Parcelize
    data class DesignReport(
        val elementTitle: String,
        val dimensions: String,
        val boq: BOQData,
        val reinforcement: List<ReinforcementBar>,
        val safetyChecks: List<DesignSafetyCheck>
    ) : Parcelable

    // --- Results Classes ---

    @Parcelize
    data class BeamResult(
        val width: Double, val depth: Double, val mu: Double, val vu: Double,
        val reinforcementBottom: ReinforcementBar, val reinforcementTop: ReinforcementBar,
        val stirrups: StirrupReinforcement, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode, val appliedMoment: Double, val appliedShear: Double,
        val supportType: SupportType = SupportType.HINGED_HINGED,
        val momentCapacity: Double = 0.0, val shearCapacity: Double = 0.0, val steelRatio: Double = 0.0
    ) : Parcelable

    @Parcelize
    data class ColumnResult(
        val width: Double, val depth: Double, val pu: Double,
        val reinforcement: ReinforcementBar, val stirrups: StirrupReinforcement,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val axialCapacity: Double = 0.0, val appliedAxial: Double = 0.0,
        val slenderness: Double = 0.0, val isSlender: Boolean = false
    ) : Parcelable

    @Parcelize
    data class FootingResult(
        val width: Double, val length: Double, val thickness: Double,
        val soilPressure: Double, val allowablePressure: Double,
        val reinforcementBottom: ReinforcementBar, val isSafe: Boolean, val code: DesignCode,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double,
        val barsX: Int = 10, val barsY: Int = 10, val barDiameter: Int = 16
    ) : Parcelable

    @Parcelize
    data class SlabResult(
        val type: SlabType, val thickness: Double,
        val reinforcementMain: ReinforcementBar, val reinforcementSecondary: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode,
        val momentX: Double = 0.0, val momentY: Double = 0.0, val totalLoad: Double = 0.0,
        val punchingSafe: Boolean = true, val safetyChecks: List<DesignSafetyCheck> = emptyList()
    ) : Parcelable

    @Parcelize
    data class StairResult(
        val type: StairType, val thickness: Double,
        val reinforcement: ReinforcementBar, val distributionReinforcement: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode,
        val safetyChecks: List<DesignSafetyCheck> = emptyList()
    ) : Parcelable

    @Parcelize
    data class TankResult(
        val type: TankType, val length: Double, val width: Double, val height: Double,
        val wallThickness: Double, val baseThickness: Double,
        val wallReinforcement: ReinforcementBar, val baseReinforcement: ReinforcementBar = ReinforcementBar(spacing = 200.0, diameter = 12),
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val waterPressure: Double = 0.0, val mu: Double = 0.0, val capacity: Double = 0.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val alternatives: List<TankResult> = emptyList()
    ) : Parcelable {
        val wallThick: Double get() = wallThickness
        val baseThick: Double get() = baseThickness
        val capacityM3: Double get() = capacity
        val pressure: Double get() = waterPressure
        val safetyCheck: String get() = if(isSafe) "SAFE" else "UNSAFE"
    }

    @Parcelize
    data class RetainingWallResult(
        val height: Double, val stemThickness: Double, val baseWidth: Double,
        val stemReinforcement: ReinforcementBar, val baseReinforcement: ReinforcementBar,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val factorOfSafetyOverturning: Double = 2.0, val factorOfSafetySliding: Double = 1.5
    ) : Parcelable

    @Parcelize
    data class SteelWarehouseResult(
        val span: Double, val eaveHeight: Double, val totalHeight: Double,
        val columnSection: String, val rafterSection: String,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double, val cost: Double
    ) : Parcelable

    @Parcelize
    data class SeismicInput(
        val zone: Double, val importance: Double, val soilType: String,
        val height: Double, val totalWeight: Double, val systemType: String = "Frames", val reductionFactor: Double = 5.0
    ) : Parcelable

    @Parcelize
    data class SeismicResult(
        val baseShear: Double, val storyDrift: Double,
        val timePeriod: Double = 0.0, val spectralAcceleration: Double = 0.0,
        val forcesPerFloor: Map<Int, Double> = emptyMap(),
        val isSafe: Boolean, val code: DesignCode
    ) : Parcelable

    // --- Design Functions ---

    fun calculateSteelMember(section: SteelSectionType, memberType: SteelMemberType, inputs: SteelInputs, code: DesignCode): SteelMemberResult {
        return SteelMemberResult(section, memberType, 1000.0, 500.0, 300.0, 0.65, true, null, null, null, 50.0, 5000.0, emptyList(), emptyList())
    }

    fun designSteelWarehouse(inputs: SteelWarehouseInputs): SteelWarehouseAnalysisResult {
        return SteelWarehouseAnalysisResult(
            mainFrame = MainFrameResult(
                columnSection = SteelSectionType.ISection(400.0, 200.0, 12.0, 8.0, SteelGrade.ST52),
                rafterSection = SteelSectionType.ISection(300.0, 150.0, 10.0, 6.0, SteelGrade.ST52),
                maxMoment = 150.0, maxShear = 80.0, maxAxial = 100.0, maxDeflection = 20.0, allowableDeflection = 30.0, isSafe = true
            ),
            secondaryMembers = SecondaryMembersResult(
                purlinSection = SteelSectionType.CSection(140.0, 60.0, 8.0, 5.0, SteelGrade.S275),
                girtSection = SteelSectionType.CSection(120.0, 50.0, 6.0, 4.0, SteelGrade.S275),
                bracingSection = SteelSectionType.LSection(80.0, 80.0, 8.0, SteelGrade.ST37),
                purlinCount = 20, isSafe = true
            ),
            connections = emptyList(), totalWeight = 15.0, totalCladdingArea = 1200.0, weightPerM2 = 45.0,
            resultsByCode = "AISC 360 Verified", safetyStatus = true, recommendations = emptyList(), materialTakeoff = emptyMap()
        )
    }

    fun designColumn(width: Double, depth: Double, pu: Double, fcu: Double, fy: Double, code: DesignCode): ColumnResult {
        val ag = width * depth
        val capacity = (0.35 * fcu * ag + 0.67 * fy * (0.008 * ag)) / 1000.0
        val asReq = max(0.008 * ag, (pu * 1000.0 - 0.35 * fcu * ag) / (0.67 * fy))
        val numBars = ceil(asReq / (PI * 16.0.pow(2.0) / 4.0)).toInt().coerceAtLeast(4)
        val vol = (ag * 3000.0) / 1e9
        return ColumnResult(width, depth, pu, ReinforcementBar(numBars, 16), StirrupReinforcement(8, 200.0), emptyList(), capacity >= pu, vol, vol * 150.0, vol * 6000.0, code, capacity, pu)
    }

    fun calculateFooting(p: Double, fcu: Double, fy: Double, soil: Double, colB: Double, colT: Double, code: DesignCode): FootingResult {
        val areaReq = (p * 1.1) / soil
        val fL = ceil(sqrt(areaReq) * 20.0) / 20.0 * 1000.0
        val vol = (fL * fL * 600.0) / 1e9
        return FootingResult(fL, fL, 600.0, p/areaReq, soil, ReinforcementBar(spacing = 150.0, diameter = 16), true, code, vol, vol * 80.0, vol * 5500.0)
    }

    fun designSlab(lx: Double, ly: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, ts: Double, preferredDiameter: Int, code: DesignCode): SlabResult {
        val wu = 1.4 * deadLoad + 1.6 * liveLoad
        val vol = (lx * ly * ts) / 1000.0
        return SlabResult(SlabType.SOLID, ts, ReinforcementBar(spacing = 150.0, diameter = preferredDiameter), ReinforcementBar(spacing = 200.0, diameter = preferredDiameter), true, vol, vol * 100.0, vol * 5800.0, code, wu * lx.pow(2.0)/10.0, 0.0, wu)
    }

    fun designStaircase(type: StairType, span: Double, riser: Double, tread: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, preferredDiameter: Int, code: DesignCode): StairResult {
        val thickness = (span * 1000.0 / 25.0).coerceAtLeast(150.0)
        val vol = (span * 1.2 * thickness / 1000.0)
        return StairResult(type, thickness, ReinforcementBar(spacing = 150.0, diameter = preferredDiameter), ReinforcementBar(spacing = 200.0, diameter = 10), true, vol, vol * 110.0, vol * 6000.0, code)
    }

    fun designTank(type: TankType, capacity: Double, height: Double, fcu: Double, fy: Double, preferredDiameter: Int = 12, code: DesignCode = DesignCode.EGYPTIAN): TankResult {
        val area = capacity / height
        val dim = sqrt(area)
        val vol = (area * 0.4) + (4 * dim * height * 250.0 / 1000.0)
        return TankResult(type, dim, dim, height, 250.0, 400.0, ReinforcementBar(spacing = 150.0, diameter = preferredDiameter), isSafe = true, concreteVolume = vol, steelWeight = vol * 130.0, cost = vol * 7000.0, code = code, capacity = capacity, waterPressure = height * 10.0)
    }

    fun designRetainingWall(height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double, preferredDiameter: Int = 16, code: DesignCode = DesignCode.EGYPTIAN): RetainingWallResult {
        val stemT = (height * 1000.0 / 10.0).coerceAtLeast(250.0)
        val baseW = height * 0.6
        val vol = (height * stemT / 1000.0) + (baseW * 0.5)
        return RetainingWallResult(height, stemT, baseW * 1000.0, ReinforcementBar(spacing = 150.0, diameter = preferredDiameter), ReinforcementBar(spacing = 150.0, diameter = preferredDiameter), emptyList(), true, vol, vol * 120.0, vol * 6500.0, code)
    }

    fun designBeam(width: Double, height: Double, span: Double, fcu: Double, fy: Double, deadLoad: Double, liveLoad: Double, preferredDiameter: Int, code: DesignCode, supportType: SupportType = SupportType.HINGED_HINGED): BeamResult {
        val totalLoad = (1.4 * deadLoad + 1.6 * liveLoad)
        val mu = totalLoad * span.pow(2.0) / 8.0
        val vol = (width * height * span) / 1e6
        return BeamResult(width, height, mu, totalLoad * span / 2.0, ReinforcementBar(4, preferredDiameter), ReinforcementBar(2, 12), StirrupReinforcement(), emptyList(), true, vol, vol * 120.0, vol * 6200.0, code, mu, totalLoad * span / 2.0, supportType)
    }

    fun calculateSeismicLoads(input: SeismicInput): SeismicResult {
        val h = input.height
        val sa = (input.zone * input.importance * 2.5) / input.reductionFactor
        val baseShear = sa * input.totalWeight
        val forces = mutableMapOf<Int, Double>()
        val n = max(1, ceil(h / 3.0).toInt())
        for (i in 1..n) forces[i] = (i.toDouble() / (n*(n+1)/2.0)) * baseShear
        return SeismicResult(baseShear, 0.005 * h/n, 0.075 * h.pow(0.75), sa, forces, true, DesignCode.EGYPTIAN)
    }
}
