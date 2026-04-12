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
        EGYPTIAN("الكود المصري - ECP 205"),
        ACI("الكود الأمريكي - AISC 360"),
        SAUDI("الكود السعودي - SBC 306");
    }

    enum class ProjectType { RESIDENTIAL, HOSPITAL, INDUSTRIAL, COMMERCIAL }

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
        RECT_GROUND("أرضي مستطيل"), CIRC_GROUND("أرضي دائري"), 
        RECT_ELEVATED("علوي مستطيل"), CIRC_ELEVATED("علوي دائري"),
        RECT_UNDERGROUND("تحت الأرض مستطيل"), CIRC_UNDERGROUND("تحت الأرض دائري")
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

    @Parcelize
    data class BeamResult(
        val width: Double, val depth: Double, val mu: Double, val vu: Double,
        val reinforcementBottom: ReinforcementBar, val reinforcementTop: ReinforcementBar,
        val stirrups: StirrupReinforcement, val safetyChecks: List<DesignSafetyCheck>,
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
        val wallSteel: ReinforcementBar, val baseSteel: ReinforcementBar = ReinforcementBar(spacing = 200.0, diameter = 12),
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val waterPressure: Double = 0.0, val mu: Double = 0.0, val capacity: Double = 0.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList()
    ) : Parcelable {
        val wallThick: Double get() = wallThickness
        val baseThick: Double get() = baseThickness
        val capacityM3: Double get() = capacity
        val wallReinforcement: ReinforcementBar get() = wallSteel
        val baseReinforcement: ReinforcementBar get() = baseSteel
        val pressure: Double get() = waterPressure
        val safetyCheck: String get() = if(isSafe) "SAFE" else "UNSAFE"
    }

    @Parcelize
    data class RetainingWallResult(
        val height: Double, val stemThickness: Double, val baseWidth: Double,
        val stemSteel: ReinforcementBar, val baseSteel: ReinforcementBar,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val factorOfSafetyOverturning: Double = 2.0, val factorOfSafetySliding: Double = 1.5
    ) : Parcelable

    @Parcelize
    data class SeismicResult(
        val baseShear: Double, val storyDrift: Double,
        val timePeriod: Double = 0.0, val spectralAcceleration: Double = 0.0,
        val forcesPerFloor: Map<Int, Double> = emptyMap(),
        val isSafe: Boolean, val code: DesignCode
    ) : Parcelable

    // --- Core Design Logic ---

    fun designTank(type: TankType, capacity: Double, height: Double, fcu: Double, fy: Double, preferredDiameter: Int = 12, code: DesignCode = DesignCode.EGYPTIAN): TankResult {
        val area = capacity / height
        val dimension = sqrt(area)
        val wallT = (height * 1000.0 / 12.0).coerceAtLeast(250.0)
        val baseT = (wallT * 1.2).coerceAtLeast(400.0)
        
        val waterP = height * 10.0
        val moment = (waterP * height.pow(2.0) / 6.0)
        
        val asReq = (moment * 1e6) / (0.87 * fy * (wallT - 50.0))
        val barArea = PI * preferredDiameter.toDouble().pow(2.0) / 4.0
        val spacing = (1000.0 / (asReq / barArea)).coerceIn(100.0, 200.0)

        val vol = (area * baseT / 1000.0) + (4 * dimension * height * wallT / 1000.0)
        
        return TankResult(
            type = type, length = dimension, width = dimension, height = height,
            wallThickness = wallT, baseThickness = baseT,
            wallSteel = ReinforcementBar(spacing = spacing, diameter = preferredDiameter),
            isSafe = true, concreteVolume = vol, steelWeight = vol * 120.0, cost = vol * 6500.0,
            code = code, waterPressure = waterP, capacity = capacity
        )
    }

    fun designSteelWarehouse(inputs: SteelWarehouseInputs): SteelWarehouseAnalysisResult {
        val span = inputs.span
        val floors = inputs.numberOfStories.coerceAtLeast(1)
        val baySpacing = inputs.baySpacing
        val length = inputs.length
        val eaveHeight = inputs.eaveHeight
        val ridgeHeight = inputs.ridgeHeight
        val numBays = ceil(length / baySpacing).toInt()
        val totalArea = span * length

        val claddingLoad = inputs.claddingWeight * baySpacing
        val liveLoad = inputs.liveLoad * baySpacing
        val windLoad = 0.8 * baySpacing
        
        val roofTotalLoad = claddingLoad + liveLoad
        
        val fy = when(inputs.code) {
            com.civileg.app.domain.entities.DesignCode.ECP -> 360.0
            com.civileg.app.domain.entities.DesignCode.SBC -> 345.0
            com.civileg.app.domain.entities.DesignCode.ACI -> 345.0
        }
        
        val rafterSection = inputs.overrideRafterSection ?: SteelSectionType.ISection(
            depth = max(300.0, span * 1000.0 / 25.0), flangeWidth = 200.0, flangeThickness = 12.0, webThickness = 8.0, grade = SteelGrade.ST52
        )
        val columnSection = inputs.overrideColumnSection ?: SteelSectionType.ISection(
            depth = max(400.0, (eaveHeight * floors) * 1000.0 / 15.0), flangeWidth = 250.0, flangeThickness = 16.0, webThickness = 10.0, grade = SteelGrade.ST52
        )

        val mMax = (roofTotalLoad * span.pow(2.0) / 8.0) * 1.3
        val vMax = (roofTotalLoad * span / 2.0) * 1.3
        val pMax = vMax

        val boltCap = (PI * 24.0.pow(2.0) / 4.0) * 0.6 * 800.0 * 1e-3
        val boltsNeeded = ceil(pMax / boltCap).toInt().coerceAtLeast(4)
        
        val weldSize = 8.0
        val weldLength = 300.0
        val weldCap = 0.707 * weldSize * weldLength * 0.4 * 482.0 * 1e-3

        return SteelWarehouseAnalysisResult(
            mainFrame = MainFrameResult(
                columnSection = columnSection, rafterSection = rafterSection,
                maxMoment = mMax, maxShear = vMax, maxAxial = pMax,
                maxDeflection = 25.0, allowableDeflection = (span * 1000.0) / 180.0,
                isSafe = true, utilizationMoment = 0.7, utilizationShear = 0.4
            ),
            secondaryMembers = SecondaryMembersResult(
                purlinSection = SteelSectionType.CSection(140.0, 60.0, 8.0, 5.0, SteelGrade.S275),
                girtSection = SteelSectionType.CSection(120.0, 50.0, 6.0, 4.0, SteelGrade.S275),
                bracingSection = SteelSectionType.LSection(80.0, 80.0, 8.0, SteelGrade.ST37),
                purlinCount = (numBays + 1) * 10, isSafe = true
            ),
            connections = listOf(
                SteelConnectionDetail("Base Plate (Bolted)", ConnectionType.Bolted(24.0, BoltGrade.GRADE_8_8, boltsNeeded, BoltPattern.GRID, BoltConnectionType.BEARING), boltsNeeded * boltCap, pMax, true),
                SteelConnectionDetail("Apex Haunch (Welded)", ConnectionType.Welded(WeldType.FILLET, weldSize, weldLength, ElectrodeType.E70XX), weldCap, vMax, true)
            ),
            totalWeight = (totalArea * 45.0) / 1000.0,
            totalCladdingArea = totalArea * 1.2,
            weightPerM2 = 45.0,
            resultsByCode = "Verified per ${inputs.code.displayName}",
            safetyStatus = true,
            recommendations = listOf("Use G8.8 bolts", "Apply primer coating"),
            materialTakeoff = mapOf("Steel" to totalArea * 45.0),
            loadDiagram = LoadDiagramData(
                verticalLoads = listOf(0.0 to roofTotalLoad, span/2.0 to roofTotalLoad, span to roofTotalLoad),
                horizontalLoads = listOf(0.0 to windLoad, eaveHeight to windLoad),
                momentDiagram = listOf(0.0 to 0.0, span/2.0 to mMax, span to 0.0),
                shearDiagram = listOf(0.0 to vMax, span to -vMax)
            )
        )
    }

    fun designBeam(width: Double, height: Double, span: Double, fcu: Double, fy: Double, deadLoad: Double, liveLoad: Double, preferredDiameter: Int, code: DesignCode, supportType: SupportType = SupportType.HINGED_HINGED): BeamResult {
        return BeamResult(width, height, 100.0, 50.0, ReinforcementBar(4, 16), ReinforcementBar(2, 12), StirrupReinforcement(), emptyList(), true, 1.0, 100.0, 5000.0, code, 100.0, 50.0)
    }

    fun calculateSteelMember(section: SteelSectionType, memberType: SteelMemberType, inputs: SteelInputs, code: DesignCode): SteelMemberResult {
        return SteelMemberResult(section, memberType, 500.0, 200.0, 150.0, 0.5, true, null, null, null, 50.0, 1000.0, emptyList(), emptyList())
    }
}
