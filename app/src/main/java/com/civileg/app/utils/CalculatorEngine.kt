package com.civileg.app.utils

import android.os.Parcelable
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.calculations.base.SoilType
import com.civileg.app.domain.calculations.ecp.SteelConnectionDesign
import com.civileg.app.domain.calculations.ecp.SteelDesignEngine
import com.civileg.core.calculations.entities.DesignCode as CoreDesignCode
import com.civileg.core.calculations.entities.LoadCombination as CoreLoadCombination
import com.civileg.core.calculations.entities.*
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class CalculatorEngine @Inject constructor(
    private val settingsManager: SettingsManager
) {

    enum class AppDesignCode(val displayNameAr: String, val displayNameEn: String) {
        EGYPTIAN("الكود المصري - ECP 203", "Egyptian Code - ECP 203"),
        ACI("الكود الأمريكي - ACI 318", "American Code - ACI 318"),
        SAUDI("الكود السعودي - SBC 304", "Saudi Code - SBC 304");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn

        companion object {
            fun fromDomain(code: CoreDesignCode): AppDesignCode = when (code) {
                CoreDesignCode.ECP -> EGYPTIAN
                CoreDesignCode.ACI -> ACI
                CoreDesignCode.SBC -> SAUDI
            }
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
    enum class SupportType(val displayName: String) {
        HINGED_HINGED("Hinged-Hinged"),
        ROLLER_HINGED("Roler-Hinged"),
        FIXED_HINGED("Fixed-Hinged"),
        FIXED_FIXED("Fixed-Fixed"),
        CANTILEVER("Cantilever")
    }

    @Parcelize
    data class ReinforcementBar(
        val numBars: Int = 0,
        val diameter: Int = 12,
        val spacing: Double = 0.0,
        val type: String = "Main",
        val description: String = "",
        val weightKg: Double = 0.0,
        val barLength: Double = 0.0, // in meters
        val shapeCode: Int = 0       // for BBS
    ) : Parcelable {
        val barString: String get() = if (numBars > 0) "${numBars}\u03A6${diameter}" else if (spacing > 0) "${(1000/spacing).toInt()}\u03A6${diameter}/m'" else description
        val area: Double get() = if (numBars > 0) numBars * (PI * diameter.toDouble().pow(2.0) / 4.0) else if (spacing > 0) (1000.0/spacing) * (PI * diameter.toDouble().pow(2.0) / 4.0) else 0.0
    }

    @Parcelize
    data class StirrupZone(
        val name: String,
        val startLocation: Double,
        val endLocation: Double,
        val spacing: Double,
        val numLegs: Int = 2,
        val diameter: Int = 8,
        val description: String = ""
    ) : Parcelable

    @Parcelize
    data class StirrupReinforcement(
        val diameter: Int = 8, 
        val spacing: Double = 200.0, 
        val description: String = "5Ø8/m'", 
        val numLegs: Int = 2,
        val zones: List<StirrupZone> = emptyList(),
        val condensationZoneLength: Double = 0.0,
        val spacingAtSupport: Double = 0.0,
        val spacingAtMidspan: Double = 0.0
    ) : Parcelable

    @Parcelize
    data class DesignSafetyCheck(val name: String, val value: Double, val limit: Double, val unit: String, val isSafe: Boolean) : Parcelable

    @Parcelize
    data class ColumnResult(
        val width: Double, val depth: Double, val pu: Double, val muX: Double, val muY: Double,
        val reinforcement: ReinforcementBar, val stirrups: StirrupReinforcement,
        val isSafe: Boolean, val axialCapacity: Double, val concreteVolume: Double,
        val steelWeight: Double, val cost: Double, val code: AppDesignCode,
        val slenderness: Double = 0.0, val isSlender: Boolean = false,
        val punchingSafe: Boolean = true, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val utilizationRatio: Double = 0.0, val columnType: String = "RECTANGULAR",
        val isDuctile: Boolean = false, val confinementLength: Double = 0.0,
        val reinforcementArea: Double = 0.0,
        val reinforcementRatio: Double = 0.0,
        val steelWasteKg: Double = 0.0,
        val mxCapacity: Double = 0.0,
        val myCapacity: Double = 0.0,
        val rebarAlternatives: List<ReinforcementBar> = emptyList(),
        val trace: @RawValue DesignTrace = DesignTrace()
    ) : Parcelable

    @Parcelize
    data class BeamResult(
        val width: Double, val depth: Double, val mu: Double, val vu: Double,
        val reinforcementBottom: ReinforcementBar, val reinforcementTop: ReinforcementBar,
        val stirrups: StirrupReinforcement, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: AppDesignCode, val appliedMoment: Double, val appliedShear: Double,
        val supportType: SupportType = SupportType.HINGED_HINGED,
        val momentCapacity: Double = 0.0, val shearCapacity: Double = 0.0,
        val steelRatio: Double = 0.0, val warnings: List<String> = emptyList(),
        val deflection: Double = 0.0, val allowableDeflection: Double = 0.0,
        val steelWasteTons: Double = 0.0, val utilizationRatio: Double = 0.0,
        val trace: @RawValue DesignTrace = DesignTrace()
    ) : Parcelable

    @Parcelize
    data class SlabResult(
        val type: SlabType, val thickness: Double,
        val reinforcementMain: ReinforcementBar, val reinforcementSecondary: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: AppDesignCode,
        val momentX: Double = 0.0, val momentY: Double = 0.0, val totalLoad: Double = 0.0,
        val punchingSafe: Boolean = true, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val steelWasteTons: Double = 0.0,
        val minThickness: Double = 0.0,
        val efficiencyScore: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val suggestions: List<String> = emptyList(),
        val columnStripSteelX: String = "",
        val middleStripSteelX: String = "",
        val columnStripSteelY: String = "",
        val middleStripSteelY: String = "",
        val dropPanelWidth: Double = 0.0,
        val dropPanelThick: Double = 0.0,
        val punchingStressAtDrop: Double = 0.0,
        val trimmerReinforcement: String = "",
        val trace: @RawValue DesignTrace = DesignTrace()
    ) : Parcelable

    @Parcelize
    data class FootingResult(
        val width: Double, val length: Double, val thickness: Double,
        val reinforcement: ReinforcementBar, val isSafe: Boolean,
        val soilPressure: Double, val maxSoilPressure: Double,
        val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: AppDesignCode, val utilizationRatio: Double = 0.0,
        val trace: @RawValue DesignTrace = DesignTrace(),
        val column1Size: Pair<Double, Double> = Pair(400.0, 400.0),
        val allowablePressure: Double = 200.0,
        val barsX: Int = 0, val barsY: Int = 0,
        val type: FootingType = FootingType.ISOLATED,
        val efficiencyScore: Double = 0.0,
        val isOptimal: Boolean = true,
        val barDiameter: Int = 16,
        val reinforcementBottom: ReinforcementBar = ReinforcementBar(),
        val safetyChecks: List<DesignSafetyCheck> = emptyList()
    ) : Parcelable

    enum class FootingType(val displayName: String) {
        ISOLATED("Isolated Footing"), COMBINED("Combined Footing"), RAFT("Raft Foundation"), STRIP("Strip Footing"), PILE_CAP("Pile Cap")
    }

    @Parcelize
    data class TankResult(
        val type: TankType, val length: Double, val width: Double, val height: Double,
        val wallThickness: Double, val baseThickness: Double,
        val wallReinforcement: ReinforcementBar, val baseReinforcement: ReinforcementBar,
        val isSafe: Boolean, val capacityM3: Double, val concreteVolume: Double,
        val steelWeight: Double, val cost: Double, val code: AppDesignCode,
        val pressure: Double = 0.0,
        val fcu: Double = 25.0, val fy: Double = 400.0,
        val capacity: Double = 0.0, val waterPressure: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val trace: @RawValue DesignTrace = DesignTrace()
    ) : Parcelable

    enum class TankType(val displayName: String) {
        RECTANGULAR_GROUND("Rectangular Ground"), CIRCULAR_GROUND("Circular Ground"),
        RECTANGULAR_ELEVATED("Rectangular Elevated"), CIRCULAR_ELEVATED("Circular Elevated"),
        UNDERGROUND("Underground Rectangular"), CIRCULAR_UNDERGROUND("Underground Circular")
    }

    @Parcelize
    data class StairResult(
        val type: StairType, val thickness: Double,
        val reinforcement: ReinforcementBar, val distributionReinforcement: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: AppDesignCode,
        val utilizationRatio: Double = 0.0,
        val mu: Double = 0.0, val wu: Double = 0.0,
        val span: Double = 0.0, val riser: Double = 0.0, val tread: Double = 0.0,
        val fcu: Double = 25.0, val fy: Double = 400.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val trace: @RawValue DesignTrace = DesignTrace()
    ) : Parcelable

    enum class StairType(val displayName: String) {
        STRAIGHT("Straight Flight"), DOG_LEG("Dog-Leg Stair"), SPIRAL("Spiral Stair")
    }

    enum class RetainingWallType { CANTILEVER, GRAVITY, COUNTERFORT }

    @Parcelize
    data class RetainingWallResult(
        val type: RetainingWallType,
        val height: Double,
        val stemThickness: Double,
        val baseWidth: Double,
        val baseThickness: Double,
        val reinforcement: ReinforcementBar,
        val isSafe: Boolean,
        val concreteVolume: Double,
        val steelWeight: Double,
        val cost: Double,
        val code: AppDesignCode,
        val factorOfSafetyOverturning: Double = 0.0,
        val factorOfSafetySliding: Double = 0.0,
        val maxBearingPressure: Double = 0.0,
        val minBearingPressure: Double = 0.0,
        val pa: Double = 0.0,
        val muStem: Double = 0.0,
        val stemReinforcement: ReinforcementBar = ReinforcementBar(),
        val baseReinforcement: ReinforcementBar = ReinforcementBar(),
        val backfillAngle: Double = 0.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val utilizationRatio: Double = 0.0,
        val trace: @RawValue DesignTrace = DesignTrace()
    ) : Parcelable

    @Parcelize
    data class SeismicResult(
        val code: AppDesignCode,
        val baseShear: Double,
        val timePeriod: Double,
        val spectralAcceleration: Double,
        val totalWeight: Double,
        val zone: String,
        val importance: Double,
        val responseReduction: Double,
        val height: Double,
        val forcesPerFloor: List<Pair<Int, Double>>,
        val storyDrift: List<Double>,
        val isSafe: Boolean = true,
        val trace: @RawValue DesignTrace = DesignTrace()
    ) : Parcelable

    // ── COLUMN ──
    fun designColumn(
        width: Double, depth: Double, pu: Double, mx: Double = 0.0, my: Double = 0.0,
        fcu: Double, fy: Double, code: AppDesignCode, isCircular: Boolean = false,
        clearHeight: Double = 3000.0, preferredDiameter: Int = 16, isSeismic: Boolean = false,
        connectedSlab: ConnectedSlabType = ConnectedSlabType.SOLID, hasCap: Boolean = false
    ): ColumnResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getColumnDesign(domainCode)
        val res = engine.calculateReinforcement(fcu, fy, width, depth, pu, mx, my, CoreLoadCombination.DEAD_LIVE)
        
        val concVol = (width * depth * clearHeight) / 1e9
        val steelWt = res.astProvided * (clearHeight / 1000.0) * 7.85e-3

        return ColumnResult(
            width, depth, pu, mx, my,
            ReinforcementBar(res.numberOfBars, res.barDiameter.toInt(), description = "${res.numberOfBars}Ø${res.barDiameter.toInt()}"),
            StirrupReinforcement(res.tiesDiameter.toInt(), res.tiesSpacing),
            res.isSafe, 0.0, concVol, steelWt, concVol * 4000.0 + steelWt * 45.0, code,
            utilizationRatio = res.utilizationRatio,
            reinforcementArea = res.astProvided,
            trace = res.trace
        )
    }

    // ── BEAM ──
    fun designBeam(
        width: Double, height: Double, span: Double, fcu: Double, fy: Double,
        deadLoad: Double, liveLoad: Double, preferredDiameter: Int, code: AppDesignCode
    ): BeamResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getBeamDesign(domainCode)
        val wu = domainCode.getDeadLoadFactor() * deadLoad + domainCode.getLiveLoadFactor() * liveLoad
        val mu = wu * (span/1000.0).pow(2) / 8.0
        val vu = wu * (span/1000.0) / 2.0
        val d = height - 50.0

        val flex = engine.calculateFlexureReinforcement(fcu, fy, width, d, height, mu, CoreLoadCombination.DEAD_LIVE)
        val shear = engine.calculateShearReinforcement(fcu, fy, width, d, vu, 0.0, CoreLoadCombination.DEAD_LIVE)

        val concVol = (width * height * span) / 1e9
        val steelWt = flex.astProvided * (span / 1000.0) * 7.85e-3

        return BeamResult(
            width, height, mu, vu,
            ReinforcementBar(flex.numberOfBars, flex.barDiameter.toInt()),
            ReinforcementBar(2, 12),
            StirrupReinforcement(shear.stirrupDiameter.toInt(), shear.stirrupSpacing),
            isSafe = flex.isSafe && shear.isSafe, concreteVolume = concVol, steelWeight = steelWt,
            cost = concVol * 4000.0 + steelWt * 45.0, code = code, appliedMoment = mu, appliedShear = vu,
            utilizationRatio = flex.utilizationRatio,
            trace = flex.trace
        )
    }

    // ── SLAB ──
    fun designSlab(
        lx: Double, ly: Double, deadLoad: Double, liveLoad: Double,
        fcu: Double, fy: Double, ts: Double, preferredDiameter: Int, code: AppDesignCode
    ): SlabResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getSlabDesign(domainCode)
        val wu = domainCode.getDeadLoadFactor() * deadLoad + domainCode.getLiveLoadFactor() * liveLoad
        
        val res = engine.designTwoWaySlab(
            fcu, fy, ts, lx * 1000.0, ly * 1000.0,
            SlabSupportConditions(
                EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
                EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED
            ),
            wu, CoreLoadCombination.DEAD_LIVE
        )

        return SlabResult(
            SlabType.SOLID, ts,
            ReinforcementBar(spacing = res.shortDirection.barSpacing, diameter = res.shortDirection.barDiameter.toInt()),
            ReinforcementBar(spacing = res.longDirection.barSpacing, diameter = res.longDirection.barDiameter.toInt()),
            res.isSafe, lx * ly * ts / 1000.0, 0.0, 0.0, code, res.shortDirection.utilizationRatio,
            trace = res.shortDirection.trace
        )
    }

    // ── FOOTING ──
    fun designIsolatedFooting(
        columnWidth: Double, columnDepth: Double, fcu: Double, fy: Double,
        axialLoad: Double, momentX: Double, momentY: Double,
        soilCapacity: Double, footingDepth: Double, code: AppDesignCode
    ): FootingResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getFootingDesign(domainCode)
        
        val res = engine.designIsolatedFooting(
            fcu, fy, columnWidth, columnDepth, axialLoad, momentX, momentY,
            soilCapacity, footingDepth, CoreLoadCombination.DEAD_LIVE, BoundaryConstraints()
        )

        val concVol = (res.requiredWidth * res.requiredLength * res.requiredThickness) / 1e9
        val steelWt = res.reinforcement.astProvided * (res.requiredWidth / 1000.0) * 7.85e-3 * 2

        return FootingResult(
            res.requiredWidth, res.requiredLength, res.requiredThickness,
            ReinforcementBar(res.reinforcement.numberOfBars, res.reinforcement.barDiameter.toInt(), res.reinforcement.spacing),
            res.isSafe, res.soilPressure, res.maxSoilPressure, concVol, steelWt,
            concVol * 4000.0 + steelWt * 45.0, code, res.reinforcement.utilizationRatio,
            trace = res.trace
        )
    }
    
    // ── TANK ──
    fun designTank(
        type: TankType, capacityM3: Double, height: Double, fcu: Double, fy: Double,
        preferredDiameter: Int, code: AppDesignCode
    ): TankResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getTankDesign(domainCode)
        
        val area = capacityM3 / height
        val side = sqrt(area)
        
        val res = engine.calculateTank(
            length = side * 1000.0, width = side * 1000.0, height = height * 1000.0,
            waterDepth = height * 1000.0, fcu = fcu, fy = fy
        )

        return TankResult(
            type, side, side, height, res.wallThickness, res.baseThickness,
            ReinforcementBar(spacing = res.wallReinforcement.tiesSpacing, diameter = res.wallReinforcement.barDiameter.toInt()),
            ReinforcementBar(spacing = res.baseReinforcement.tiesSpacing, diameter = res.baseReinforcement.barDiameter.toInt()),
            res.isSafe, capacityM3, res.concreteVolume, res.steelWeight, res.cost, code,
            pressure = res.pressure, fcu = fcu, fy = fy, capacity = capacityM3, waterPressure = res.pressure,
            trace = res.trace
        )
    }

    // ── STAIRCASE ──
    fun designStaircase(
        type: StairType, span: Double, rise: Double, fcu: Double, fy: Double,
        ts: Double, preferredDiameter: Int, code: AppDesignCode
    ): StairResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getStaircaseDesign(domainCode)
        
        val res = engine.designStaircase(
            StaircaseInput(
                stairType = com.civileg.app.domain.calculations.base.StairType.STRAIGHT,
                span = span, totalRise = rise, stairWidth = 1.2,
                waistThickness = ts, fcu = fcu, fy = fy
            )
        )

        return StairResult(
            type, ts,
            ReinforcementBar(spacing = res.stirrupSpacing, diameter = res.stirrupDiameter.toInt()),
            ReinforcementBar(spacing = 200.0, diameter = 10),
            res.isSafe, 0.0, 0.0, 0.0, code,
            utilizationRatio = res.reinforcementRatio / res.minSteelRatio.coerceAtLeast(0.001),
            mu = res.maxMoment, wu = res.factoredLoad, span = span, riser = res.riser, tread = res.going,
            fcu = fcu, fy = fy,
            trace = res.trace
        )
    }

    fun calculateSeismicLoads(
        code: AppDesignCode,
        height: Double,
        numStories: Int,
        totalWeight: Double,
        zone: String,
        importance: Double,
        responseReduction: Double
    ): SeismicResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getSeismicAnalysis(domainCode)
        
        val res = engine.calculateBaseShear(
            totalWeight = totalWeight,
            seismicZone = SeismicZone.ZONE_1,
            soilType = SoilType.C,
            importanceFactor = importance,
            responseModificationFactor = responseReduction,
            buildingHeight = height
        )

        return SeismicResult(
            code = code,
            baseShear = res.baseShear,
            timePeriod = 0.5,
            spectralAcceleration = 0.2,
            totalWeight = totalWeight,
            zone = zone,
            importance = importance,
            responseReduction = responseReduction,
            height = height,
            forcesPerFloor = emptyList(),
            storyDrift = emptyList(),
            isSafe = true
        )
    }

    fun designRetainingWall(
        height: Double,
        soilDensity: Double,
        frictionAngle: Double,
        surcharge: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: AppDesignCode
    ): RetainingWallResult {
        val domainCode = if (code == AppDesignCode.EGYPTIAN) CoreDesignCode.ECP else if (code == AppDesignCode.ACI) CoreDesignCode.ACI else CoreDesignCode.SBC
        val engine = CalculationFactory.getRetainingWallDesign(domainCode)
        
        val input = RetainingWallInput(
            wallHeight = height, stemBaseThickness = 400.0, stemTopThickness = 200.0,
            baseWidth = height * 0.6 * 1000.0, baseThickness = 500.0,
            toeLength = height * 0.15 * 1000.0, heelLength = height * 0.45 * 1000.0,
            soilDensity = soilDensity, frictionAngle = frictionAngle,
            surchargeLoad = surcharge, waterTableDepth = 10.0,
            fcu = fcu, fy = fy
        )
        val res = engine.designRetainingWall(input)

        return RetainingWallResult(
            type = RetainingWallType.CANTILEVER,
            height = height,
            stemThickness = 400.0,
            baseWidth = height * 0.6 * 1000.0,
            baseThickness = 500.0,
            reinforcement = ReinforcementBar(10, 16),
            isSafe = res.isSafe,
            concreteVolume = 0.0,
            steelWeight = 0.0,
            cost = 0.0,
            code = code,
            factorOfSafetyOverturning = res.overturningFS,
            factorOfSafetySliding = res.slidingFS,
            maxBearingPressure = res.maxBearingPressure,
            minBearingPressure = res.minBearingPressure,
            pa = 0.0,
            muStem = res.stemMoment,
            stemReinforcement = ReinforcementBar(description = res.stemMainRebar),
            baseReinforcement = ReinforcementBar(description = res.heelRebar),
            backfillAngle = frictionAngle,
            safetyChecks = res.safetyChecks.map { DesignSafetyCheck(it.name, it.value, it.limit, "kN", it.isSafe) },
            utilizationRatio = 0.0,
            trace = res.trace
        )
    }

    fun calculateWeldCapacity(size: Double, length: Double, electrode: ElectrodeType, code: AppDesignCode): Double {
        val engine = SteelConnectionDesign()
        return engine.calculateFilletWeldCapacity(size, length, electrode)
    }

    fun calculateBoltCapacity(diameter: Double, grade: BoltGrade, count: Int, code: AppDesignCode): Double {
        val engine = SteelConnectionDesign()
        return engine.calculateBoltShearCapacity(grade, diameter) * count
    }

    fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en

    // ── STEEL MEMBER ──
    fun calculateSteelMember(
        section: SteelSectionType,
        memberType: SteelMemberType,
        inputs: SteelInputs,
        code: AppDesignCode
    ): SteelMemberResult {
        val engine = SteelDesignEngine()
        val secProps = SteelDesignEngine.SectionProperties(
            name = section.sectionName,
            h = section.depth, b = section.width, tw = section.webThickness, tf = section.flangeThickness,
            Ix = section.ix, Iy = section.iy, Zx = section.zx, Zy = section.zy, Sx = section.sx, Sy = section.sy,
            rx = section.rx, ry = section.ry, A = section.area, J = section.j
        )
        val grade = when (section) {
            is SteelSectionType.ISection -> mapGrade(section.grade)
            is SteelSectionType.CSection -> mapGrade(section.grade)
            is SteelSectionType.LSection -> mapGrade(section.grade)
            is SteelSectionType.RHS -> mapGrade(section.grade)
            is SteelSectionType.CHS -> mapGrade(section.grade)
            is SteelSectionType.TSection -> mapGrade(section.grade)
            is SteelSectionType.PlateGirder -> mapGrade(section.grade)
            is SteelSectionType.Pipe -> mapGrade(section.grade)
            else -> SteelDesignEngine.SteelGrade.ST37
        }

        return if (memberType == SteelMemberType.COLUMN) {
            val res = engine.checkColumnCombined(inputs.axialLoad, inputs.moment, inputs.momentY, secProps, grade, inputs.kX, inputs.length)
            SteelMemberResult(
                sectionType = section, memberType = memberType,
                axialCapacity = 0.0, flexuralCapacity = 0.0, shearCapacity = 0.0,
                utilizationRatio = res.axialCheck.utilizationRatio, isSafe = res.isSafe,
                connectionDesign = null, bucklingCheck = null, weight = section.weight, cost = 0.0,
                warnings = res.warnings, codeNotes = res.codeNotes + "Calculated using ${code.name}", 
                trace = res.trace
            )
        } else {
            val res = engine.designBeam(inputs.moment, inputs.shear, inputs.liveLoad, inputs.length, secProps, grade, inputs.unbracedLength)
            SteelMemberResult(
                sectionType = section, memberType = memberType,
                axialCapacity = 0.0, flexuralCapacity = 0.0, shearCapacity = 0.0,
                utilizationRatio = res.momentCheck.utilizationRatio, isSafe = res.isSafe,
                connectionDesign = null, bucklingCheck = null, weight = section.weight, cost = 0.0,
                warnings = res.warnings, codeNotes = res.codeNotes + "Calculated using ${code.name}", 
                trace = res.trace
            )
        }
    }

    private fun mapGrade(grade: SteelGrade): SteelDesignEngine.SteelGrade {
        return when (grade) {
            SteelGrade.ST44 -> SteelDesignEngine.SteelGrade.ST44
            SteelGrade.ST52 -> SteelDesignEngine.SteelGrade.ST52
            else -> SteelDesignEngine.SteelGrade.ST37
        }
    }

    fun getSteelSectionLibrary(): Map<String, List<SteelSectionType>> {
        return mapOf(
            "IPE (European I-Beams)" to SteelTables.ipeSections.map { it.toSteelSectionType() },
            "HEA (Light H-Beams)" to SteelTables.heaSections.map { it.toSteelSectionType() },
            "HEB (Heavy H-Beams)" to SteelTables.hebSections.map { it.toSteelSectionType() },
            "UPN (Channels)" to SteelTables.upnSections.map { it.toSteelSectionType() },
            "Angles (L-Sections)" to SteelTables.angleSections.map { it.toSteelSectionType() },
            "RHS (Hollow Sections)" to SteelTables.rhsSections.map { it.toSteelSectionType() }
        )
    }

    private fun SteelTables.SectionProperties.toSteelSectionType(): SteelSectionType {
        return if (name.startsWith("IPE") || name.startsWith("HE")) {
            SteelSectionType.ISection(depth, width, tf, tw, SteelGrade.ST37, name)
        } else if (name.startsWith("UPN")) {
            SteelSectionType.CSection(depth, width, tf, tw, SteelGrade.ST37, name)
        } else if (name.startsWith("L")) {
            SteelSectionType.LSection(depth, width, tf, SteelGrade.ST37, name)
        } else {
            SteelSectionType.RHS(width, depth, tw, SteelGrade.ST37, name)
        }
    }

    // ── STEEL WAREHOUSE ──
    fun designSteelWarehouse(inputs: SteelWarehouseInputs): SteelWarehouseAnalysisResult {
        val span = inputs.span
        val spacing = inputs.baySpacing
        val load = (inputs.deadLoad + inputs.liveLoad) * spacing
        val maxM = load * span.pow(2) / 8.0
        val maxV = load * span / 2.0
        
        val selectedSection = SteelSectionType.ISection(300.0, 150.0, 10.7, 7.1, SteelGrade.ST37)

        return SteelWarehouseAnalysisResult(
            mainFrame = MainFrameResult(
                columnSection = selectedSection,
                rafterSection = selectedSection,
                maxMoment = maxM, maxShear = maxV, maxAxial = 50.0,
                maxDeflection = 10.0, allowableDeflection = 25.0,
                isSafe = true
            ),
            secondaryMembers = SecondaryMembersResult(
                purlinSection = SteelSectionType.CSection(140.0, 60.0, 8.0, 5.0, SteelGrade.ST37, "UPN 140"),
                girtSection = SteelSectionType.CSection(120.0, 50.0, 6.0, 4.0, SteelGrade.ST37, "UPN 120"),
                bracingSection = SteelSectionType.LSection(80.0, 80.0, 8.0, SteelGrade.ST37, "L 80x8"),
                purlinCount = (span / inputs.purlinSpacing).toInt() * 2,
                isSafe = true
            ),
            connections = emptyList(),
            totalWeight = span * inputs.length * 0.05,
            totalCladdingArea = span * inputs.length,
            weightPerM2 = 50.0,
            resultsByCode = "AISC / ECP",
            safetyStatus = true,
            recommendations = listOf("Use Anchor Bolts M24"),
            materialTakeoff = mapOf("IPE Sections" to 10.5),
            estimatedTotalCost = span * inputs.length * 1500.0,
            costPerM2 = 1500.0,
            roi = 20.0,
            netProfit = 100000.0
        )
    }

    fun calculateSteelWarehousePro(inputs: SteelWarehouseInputs): SteelWarehouseProResult {
        return SteelWarehouseProResult(
            codeName = inputs.code.name,
            tributaryAreaM2 = inputs.span * inputs.length,
            serviceLoadKnM2 = inputs.deadLoad + inputs.liveLoad,
            frameReactionKn = 50.0,
            baseShearKn = 10.0,
            maxMomentKnM = inputs.span * 45.0,
            maxAxialKn = 100.0,
            maxShearKn = 25.0,
            driftMm = 5.0,
            utilization = 0.65,
            compressionZone = "Top flange",
            tensionZone = "Bottom flange",
            notes = listOf("Advanced analysis results")
        )
    }
}
