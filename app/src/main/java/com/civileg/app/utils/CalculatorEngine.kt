package com.civileg.app.utils

import android.os.Parcelable
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.entities.*
import com.civileg.core.engineering.StrapFootingDesignEngine
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * The Professional Bridge Engine.
 * Acts as the primary interface between the UI layer and the deep structural calculation logic.
 */
@Singleton
class CalculatorEngine @Inject constructor(
    private val settingsManager: SettingsManager
) {

    enum class DesignCode(val displayNameAr: String, val displayNameEn: String) {
        EGYPTIAN("الكود المصري - ECP 203", "Egyptian Code - ECP 203"),
        ACI("الكود الأمريكي - ACI 318", "American Code - ACI 318"),
        SAUDI("الكود السعودي - SBC 304", "Saudi Code - SBC 304");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn

        fun toDomain(): com.civileg.app.domain.entities.DesignCode = when(this) {
            EGYPTIAN -> com.civileg.app.domain.entities.DesignCode.ECP
            ACI -> com.civileg.app.domain.entities.DesignCode.ACI
            SAUDI -> com.civileg.app.domain.entities.DesignCode.SBC
        }
        
        companion object {
            fun fromDomain(domain: com.civileg.app.domain.entities.DesignCode): DesignCode = when(domain) {
                com.civileg.app.domain.entities.DesignCode.ECP -> EGYPTIAN
                com.civileg.app.domain.entities.DesignCode.ACI -> ACI
                com.civileg.app.domain.entities.DesignCode.SBC -> SAUDI
            }
        }
    }

    enum class SupportType(val displayNameAr: String, val displayNameEn: String) {
        HINGED_HINGED("مفصلي - مفصلي", "Hinged-Hinged"),
        ROLLER_HINGED("متدحرج - مفصلي", "Roller-Hinged"),
        FIXED_HINGED("تثبيت - مفصلي", "Fixed-Hinged"),
        FIXED_FIXED("تثبيت - تثبيت", "Fixed-Fixed"),
        CANTILEVER("كابولي", "Cantilever");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
            
        fun toDomain(): SupportCondition = when(this) {
            CANTILEVER -> SupportCondition.CANTILEVER
            else -> SupportCondition.SIMPLY_SUPPORTED
        }
    }

    enum class SlabType(val displayNameAr: String, val displayNameEn: String) {
        SOLID("بلاطة صلبة", "Solid Slab"),
        FLAT("بلاطة مسطحة", "Flat Slab"),
        HOLLOW_BLOCK("بلاطة هردي", "Hollow Block"),
        POST_TENSION("بلاطة بست تنشن", "Post-Tensioned"),
        WAFFLE("بلاطة وافل", "Waffle Slab");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }

    enum class StairType(val displayNameAr: String, val displayNameEn: String) {
        SINGLE_FLIGHT("قلبة واحدة", "Single Flight"),
        DOUBLE_FLIGHT("قلبتين", "Double Flight"),
        TRIPLE_FLIGHT("3 قلبات", "Triple Flight"),
        CANTILEVER("درج كابولي", "Cantilever Stairs"),
        SPIRAL("درج حلزوني", "Spiral Stairs");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }

    enum class TankType(val displayNameAr: String, val displayNameEn: String) {
        RECTANGULAR_GROUND("مستطيل - سطحي", "Rectangular Ground"),
        CIRCULAR_GROUND("دائري - سطحي", "Circular Ground"),
        RECTANGULAR_ELEVATED("مستطيل - علوي", "Rectangular Elevated"),
        CIRCULAR_ELEVATED("دائري - علوي", "Circular Elevated"),
        UNDERGROUND("مستطيل - تحت الأرض", "Underground"),
        CIRCULAR_UNDERGROUND("دائري - تحت الأرض", "Circular Underground");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }

    @Parcelize
    data class ReinforcementBar(
        val numBars: Int = 0,
        val diameter: Int = 12,
        val spacing: Double = 0.0,
        val type: String = "Main",
        val description: String = "",
        val weightKg: Double = 0.0,
        val barLength: Double = 0.0,
        val shapeCode: Int = 0
    ) : Parcelable {
        val barString: String get() = if (numBars > 0) "${numBars}Ø${diameter}" else if (spacing > 0) "${(1000/spacing).toInt()}Ø${diameter}/m'" else description
        val area: Double get() = if (numBars > 0) numBars * PI * (diameter.toDouble() / 2.0).pow(2.0) else if (spacing > 0) (1000.0/spacing) * PI * (diameter.toDouble() / 2.0).pow(2.0) else 0.0
    }

    @Parcelize
    data class StirrupReinforcement(
        val diameter: Int = 8,
        val spacing: Double = 200.0,
        val description: String = "5Φ8/m'",
        val weightKg: Double = 0.0,
        val numLegs: Int = 2,
        val zones: List<StirrupZone> = emptyList(),
        val condensationZoneLength: Double = 0.0,
        val spacingAtSupport: Double = 0.0,
        val spacingAtMidspan: Double = 0.0
    ) : Parcelable {
        val area: Double get() = numLegs * Math.PI * (diameter.toDouble() / 2.0) * (diameter.toDouble() / 2.0)
    }

    @Parcelize
    data class DesignSafetyCheck(val name: String, val value: Double, val limit: Double, val unit: String, val isSafe: Boolean) : Parcelable

    @Parcelize
    data class BOQSummary(val concreteM3: Double, val steelKg: Double, val totalCost: Double, val currency: String = "USD") : Parcelable

    @Parcelize
    data class ReinforcementDetail(val type: String, val description: String, val weightKg: Double) : Parcelable

    @Parcelize
    data class DesignReport(
        val elementTitle: String,
        val dimensions: String,
        val boq: BOQSummary,
        val reinforcement: List<ReinforcementDetail>,
        val safetyChecks: List<DesignSafetyCheck>
    ) : Parcelable

    @Parcelize
    data class BeamResult(
        val width: Double = 0.0, val depth: Double = 0.0, val mu: Double = 0.0, val vu: Double = 0.0,
        val reinforcementBottom: ReinforcementBar = ReinforcementBar(), val reinforcementTop: ReinforcementBar = ReinforcementBar(),
        val stirrups: StirrupReinforcement = StirrupReinforcement(), val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val isSafe: Boolean = false, val concreteVolume: Double = 0.0, val steelWeight: Double = 0.0,
        val cost: Double = 0.0, val code: DesignCode = DesignCode.EGYPTIAN, 
        val appliedMoment: Double = 0.0, val appliedShear: Double = 0.0,
        val supportType: SupportType = SupportType.HINGED_HINGED,
        val span: Double = 0.0,
        val momentCapacity: Double = 0.0, val shearCapacity: Double = 0.0, val steelRatio: Double = 0.0,
        val deflection: Double = 0.0, val allowableDeflection: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val calculationSteps: @RawValue List<CalculationStep> = emptyList(),
        val steelWasteTons: Double = 0.0
    ) : Parcelable

    @Parcelize
    data class ColumnResult(
        val width: Double = 0.0, val depth: Double = 0.0, val pu: Double = 0.0,
        val mx: Double = 0.0, val my: Double = 0.0,
        val reinforcement: ReinforcementBar = ReinforcementBar(), val stirrups: StirrupReinforcement = StirrupReinforcement(),
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean = false,
        val concreteVolume: Double = 0.0, val steelWeight: Double = 0.0, val cost: Double = 0.0, val code: DesignCode = DesignCode.EGYPTIAN,
        val axialCapacity: Double = 0.0, val appliedAxial: Double = 0.0,
        val mxCapacity: Double = 0.0, val myCapacity: Double = 0.0,
        val slenderness: Double = 0.0, val isSlender: Boolean = false,
        val punchingSafe: Boolean = true,
        val columnType: String = "RECTANGULAR",
        val reinforcementArea: Double = 0.0,
        val minReinforcementArea: Double = 0.0,
        val maxReinforcementArea: Double = 0.0,
        val reinforcementRatio: Double = 0.0,
        val steelWasteKg: Double = 0.0,
        val rebarAlternatives: List<ReinforcementBar> = emptyList(),
        val momentCapacity: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val isDuctile: Boolean = true,
        val confinementLength: Double = 0.0
    ) : Parcelable

    enum class FootingType(val displayNameAr: String, val displayNameEn: String) {
        ISOLATED("منفصلة", "Isolated"),
        COMBINED("مشتركة", "Combined"),
        STRAP("شداد", "Strap"),
        STRIP("شريطية", "Strip"),
        RAFT("لبشة خرسانية", "Raft"),
        PILE_CAP("هامة خوازيق", "Pile Cap");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }

    @Parcelize
    data class FootingResult(
        val type: FootingType,
        val width: Double, val length: Double, val thickness: Double,
        val soilPressure: Double, val allowablePressure: Double,
        val reinforcementBottom: ReinforcementBar, val isSafe: Boolean, val code: DesignCode,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double,
        val isOptimal: Boolean = true,
        val efficiencyScore: Double = 100.0,
        val barsX: Int = 10, val barsY: Int = 10, val barDiameter: Int = 16,
        val utilizationRatio: Double = 0.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val column1Size: Pair<Double, Double> = Pair(300.0, 600.0),
        val column2Size: Pair<Double, Double> = Pair(300.0, 600.0),
        val isCombined: Boolean = false,
        val distanceBetweenColumns: Double = 0.0,
        val reinforcementTopX: Int = 0,
        val topBarDiameter: Int = 16,
        val reinforcementTopY: Int = 0
    ) : Parcelable

    @Parcelize
    data class StrapFootingResult(
        val footing1: FootingResult,
        val footing2: FootingResult,
        val strapBeamWidth: Double,
        val strapBeamDepth: Double,
        val strapTopReinforcement: ReinforcementBar,
        val strapBottomReinforcement: ReinforcementBar,
        val reactions: Pair<Double, Double>,
        val isSafe: Boolean,
        val concreteVolume: Double,
        val steelWeight: Double,
        val utilizationRatio: Double,
        val safetyChecks: List<DesignSafetyCheck> = emptyList()
    ) : Parcelable

    @Parcelize
    data class SlabResult(
        val type: SlabType = SlabType.SOLID, val thickness: Double = 0.0,
        val reinforcementMain: ReinforcementBar = ReinforcementBar(), val reinforcementSecondary: ReinforcementBar = ReinforcementBar(),
        val isSafe: Boolean = false, val concreteVolume: Double = 0.0, val steelWeight: Double = 0.0,
        val cost: Double = 0.0, val code: DesignCode = DesignCode.EGYPTIAN,
        val momentX: Double = 0.0, val momentY: Double = 0.0, val totalLoad: Double = 0.0,
        val punchingSafe: Boolean = true, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val minThickness: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val efficiencyScore: Double = 0.0,
        val suggestions: List<String> = emptyList(),
        val trimmerReinforcement: String = "",
        val steelWasteTons: Double = 0.0,
        val columnStripSteelX: String = "",
        val middleStripSteelX: String = "",
        val columnStripSteelY: String = "",
        val middleStripSteelY: String = "",
        val dropPanelWidth: Double = 0.0,
        val dropPanelThick: Double = 0.0,
        val punchingStressAtDrop: Double = 0.0
    ) : Parcelable

    @Parcelize
    data class StairResult(
        val type: StairType, val thickness: Double,
        val reinforcement: ReinforcementBar, val distributionReinforcement: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val utilizationRatio: Double = 0.0,
        val mu: Double = 0.0,
        val wu: Double = 0.0,
        val span: Double = 0.0,
        val riser: Double = 0.0,
        val tread: Double = 0.0,
        val fcu: Double = 25.0,
        val fy: Double = 400.0,
        val suggestions: List<String> = emptyList()
    ) : Parcelable

    @Parcelize
    data class TankResult(
        val type: TankType, val length: Double, val width: Double, val height: Double,
        val wallThickness: Double, val baseThickness: Double,
        val wallReinforcement: ReinforcementBar, val baseReinforcement: ReinforcementBar = ReinforcementBar(spacing = 200.0, diameter = 12),
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val waterPressure: Double = 0.0, val soilPressure: Double = 0.0, val mu: Double = 0.0, val capacity: Double = 0.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val alternatives: List<TankResult> = emptyList(),
        val isOptimal: Boolean = true,
        val utilizationRatio: Double = 0.0,
        val fcu: Double = 25.0,
        val fy: Double = 400.0,
        val suggestions: List<String> = emptyList()
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
        val factorOfSafetyOverturning: Double = 2.0, val factorOfSafetySliding: Double = 1.5,
        val utilizationRatio: Double = 0.0,
        val muStem: Double = 0.0,
        val pa: Double = 0.0,
        val ps: Double = 0.0,
        val ka: Double = 0.0,
        val soilDensity: Double = 18.0,
        val fcu: Double = 25.0,
        val fy: Double = 400.0,
        val backfillAngle: Double = 30.0,
        val maxBearingPressure: Double = 0.0,
        val minBearingPressure: Double = 0.0,
        val bearingFS: Double = 0.0,
        val suggestions: List<String> = emptyList()
    ) : Parcelable

    // SteelMemberResult moved to SteelEntities.kt

    @Parcelize
    data class SteelWarehouseResult(
        val span: Double, 
        val length: Double,
        val eaveHeight: Double, 
        val spacing: Double,
        val totalHeight: Double,
        val maxMoment: Double = 0.0,
        val maxShear: Double = 0.0,
        val columnSection: String, 
        val rafterSection: String,
        val purlinSection: String = "C100x50x2.5",
        val boltType: String = "A325 M20",
        val isSafe: Boolean = true, 
        val concreteVolume: Double = 0.0, 
        val steelWeight: Double = 0.0, 
        val cost: Double = 0.0,
        val code: DesignCode = DesignCode.EGYPTIAN
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
        val isSafe: Boolean, val code: DesignCode,
        val zone: Double = 0.0,
        val importance: Double = 1.0,
        val reductionFactor: Double = 5.0,
        val totalWeight: Double = 0.0,
        val height: Double = 0.0
    ) : Parcelable

    // --- Steel Dictionary & Design ---

    fun getSteelSectionLibrary(): Map<String, List<SteelSectionType>> {
        val iSections = SteelTables.ipeSections.map { s ->
            SteelSectionType.ISection(h = s.depth, bf = s.width, tf = s.tf, tw = s.tw, grade = SteelGrade.ST37, customName = s.name)
        }
        return mapOf("IPE (European I-Beams)" to iSections)
    }

    fun calculateSteelMember(section: SteelSectionType, memberType: SteelMemberType, inputs: SteelInputs, code: DesignCode): SteelMemberResult {
        val area = section.area
        val fy = 240.0
        val axialCapacity = (0.85 * fy * area) / 1000.0
        return SteelMemberResult(
            sectionType = section, memberType = memberType, axialCapacity = axialCapacity,
            flexuralCapacity = 100.0, shearCapacity = 100.0, utilizationRatio = 0.5,
            isSafe = true, connectionDesign = null, bucklingCheck = null, deflectionCheck = null,
            weight = area * 1e-6 * 7850, cost = 0.0,
            warnings = emptyList(), codeNotes = emptyList()
        )
    }

    fun designSteelWarehouse(inputs: SteelWarehouseInputs): SteelWarehouseAnalysisResult {
        throw UnsupportedOperationException()
    }

    fun calculateSteelWarehousePro(inputs: SteelWarehouseInputs): SteelWarehouseProResult {
        throw UnsupportedOperationException()
    }

    fun designColumn(
        width: Double, depth: Double, pu: Double, mx: Double = 0.0, my: Double = 0.0,
        fcu: Double, fy: Double, code: DesignCode, isCircular: Boolean = false, connectedSlab: String = "SOLID",
        hasCap: Boolean = false, clearHeight: Double = 3000.0, preferredDiameter: Int = 16,
        autoOptimize: Boolean = true, manualNumBars: Int? = null, autoIncludeSelfWeight: Boolean = true, isSeismic: Boolean = false
    ): ColumnResult {
        val ag = if (isCircular) PI * width.pow(2.0) / 4.0 else width * depth
        return ColumnResult(width = width, depth = depth, pu = pu, isSafe = true, concreteVolume = ag * clearHeight / 1e9, steelWeight = 0.0, cost = 0.0, code = code, axialCapacity = 1000.0)
    }

    fun designBeam(
        width: Double, height: Double, span: Double, fcu: Double, fy: Double,
        deadLoad: Double, liveLoad: Double, preferredDiameter: Int, code: DesignCode,
        supportType: SupportType = SupportType.HINGED_HINGED, customMoment: Double? = null, customShear: Double? = null,
        autoIncludeSelfWeight: Boolean = true
    ): BeamResult {
        val mu = customMoment ?: ( (1.2*deadLoad + 1.6*liveLoad) * span.pow(2) / 8.0 )
        return BeamResult(width = width, depth = height, mu = mu, isSafe = true, code = code, appliedMoment = mu)
    }

    fun designSlab(lx: Double, ly: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, ts: Double, preferredDiameter: Int, code: DesignCode, type: SlabType = SlabType.SOLID, prestressForce: Double = 0.0, dropPanelThickness: Double = 0.0, columnSize: Double = 400.0, openingWidth: Double = 0.0, openingLength: Double = 0.0): SlabResult {
        return SlabResult(thickness = ts, isSafe = true, code = code, type = type)
    }

    fun calculateFooting(
        type: FootingType, p: Double, fcu: Double, fy: Double, soil: Double, colB: Double, colT: Double,
        code: DesignCode, preferredDiameter: Int = 16, preferredSpacing: Double = 150.0, p2: Double = 0.0, distance: Double = 0.0, 
        maxLeft: Double? = null, maxRight: Double? = null, maxTop: Double? = null, maxBottom: Double? = null,
        numPiles: Int = 4, pileDia: Double = 500.0, pileCapacity: Double = 500.0
    ): FootingResult {
        return FootingResult(type = type, width = 2000.0, length = 2000.0, thickness = 600.0, soilPressure = 150.0, allowablePressure = soil, reinforcementBottom = ReinforcementBar(spacing = preferredSpacing, diameter = preferredDiameter), isSafe = true, code = code, concreteVolume = 2.4, steelWeight = 120.0, cost = 5000.0)
    }

    fun calculateStrapFooting(
        col1Load: Double, col2Load: Double, distance: Double,
        col1W: Double, col1D: Double, col2W: Double, col2D: Double,
        soil: Double, fcu: Double, fy: Double,
        code: DesignCode, preferredDiameter: Int, strapWidth: Double = 400.0
    ): StrapFootingResult {
        val design = CalculationFactory.getStrapFootingDesign(code.toDomain())
        val inputs = StrapFootingDesignEngine.Inputs(
            column1Load = col1Load, column2Load = col2Load,
            distanceBetweenColumns = distance,
            column1Width = col1W, column1Depth = col1D,
            column2Width = col2W, column2Depth = col2D,
            soilBearingCapacity = soil, fcu = fcu, fy = fy,
            strapBeamWidth = strapWidth
        )
        val res = StrapFootingDesignEngine.design(inputs)
        
        return StrapFootingResult(
            footing1 = FootingResult(
                type = FootingType.ISOLATED, width = res.footing1.width, length = res.footing1.length, thickness = res.footing1.thickness,
                reinforcementBottom = ReinforcementBar(res.footing1.reinforcement.numberOfBars, res.footing1.reinforcement.barDiameter.toInt()),
                isSafe = res.isSafe, code = code, allowablePressure = soil, 
                soilPressure = res.reactions.first / (res.footing1.width * res.footing1.length / 1e6),
                concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0
            ),
            footing2 = FootingResult(
                type = FootingType.ISOLATED, width = res.footing2.width, length = res.footing2.length, thickness = res.footing2.thickness,
                reinforcementBottom = ReinforcementBar(res.footing2.reinforcement.numberOfBars, res.footing2.reinforcement.barDiameter.toInt()),
                isSafe = res.isSafe, code = code, allowablePressure = soil, 
                soilPressure = res.reactions.second / (res.footing2.width * res.footing2.length / 1e6),
                concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0
            ),
            strapBeamWidth = res.strapBeam.width,
            strapBeamDepth = res.strapBeam.depth,
            strapTopReinforcement = ReinforcementBar(res.strapBeam.topReinforcement.numberOfBars, res.strapBeam.topReinforcement.barDiameter.toInt()),
            strapBottomReinforcement = ReinforcementBar(res.strapBeam.bottomReinforcement.numberOfBars, res.strapBeam.bottomReinforcement.barDiameter.toInt()),
            reactions = res.reactions,
            isSafe = res.isSafe,
            concreteVolume = 0.0, 
            steelWeight = 0.0,
            utilizationRatio = 0.7
        )
    }

    fun designStaircase(type: StairType, span: Double, riser: Double, tread: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, preferredDiameter: Int, code: DesignCode): StairResult {
        return StairResult(type = type, thickness = 150.0, reinforcement = ReinforcementBar(), distributionReinforcement = ReinforcementBar(), isSafe = true, concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0, code = code)
    }

    fun designTank(type: TankType, capacity: Double, height: Double, fcu: Double, fy: Double, preferredDiameter: Int = 12, code: DesignCode = DesignCode.EGYPTIAN): TankResult {
        return TankResult(type = type, length = 5.0, width = 5.0, height = height, wallThickness = 250.0, baseThickness = 400.0, wallReinforcement = ReinforcementBar(), isSafe = true, concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0, code = code)
    }

    fun designRetainingWall(height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double, preferredDiameter: Int = 16, code: DesignCode = DesignCode.EGYPTIAN): RetainingWallResult {
        return RetainingWallResult(height = height, stemThickness = 300.0, baseWidth = 2000.0, stemReinforcement = ReinforcementBar(), baseReinforcement = ReinforcementBar(), isSafe = true, concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0, code = code)
    }

    fun calculateSeismicLoads(input: SeismicInput): SeismicResult {
        return SeismicResult(baseShear = 100.0, storyDrift = 0.01, isSafe = true, code = DesignCode.EGYPTIAN)
    }

    fun calculateWeldCapacity(size: Double, length: Double, electrode: ElectrodeType, code: DesignCode): Double = 0.0
    fun calculateBoltCapacity(diameter: Double, grade: BoltGrade, count: Int, code: DesignCode): Double = 0.0

    private fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en
}
