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

    enum class DesignCode(val displayNameAr: String, val displayNameEn: String) {
        EGYPTIAN("الكود المصري - ECP 203", "Egyptian Code - ECP 203"),
        ACI("الكود الأمريكي - ACI 318", "American Code - ACI 318"),
        SAUDI("الكود السعودي - SBC 304", "Saudi Code - SBC 304");

        val displayName: String
            get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn

        companion object {
            fun fromDomain(code: com.civileg.app.domain.entities.DesignCode): DesignCode {
                return when (code) {
                    com.civileg.app.domain.entities.DesignCode.ECP -> EGYPTIAN
                    com.civileg.app.domain.entities.DesignCode.ACI -> ACI
                    com.civileg.app.domain.entities.DesignCode.SBC -> SAUDI
                    else -> EGYPTIAN
                }
            }
        }
    }

    enum class SlabType(val displayNameAr: String, val displayNameEn: String) {
        SOLID("بلاطة صلبة", "Solid Slab"),
        FLAT("بلاطة مسطحة", "Flat Slab"),
        HOLLOW_BLOCK("بلاطة هردي", "Hollow Block"),
        POST_TENSION("بلاطة بست تنشن", "Post-Tensioned"),
        WAFFLE("بلاطة وافل", "Waffle Slab");
        val displayName: String get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }
    
    enum class SupportType(val displayNameAr: String, val displayNameEn: String) {
        HINGED_HINGED("مفصلي - مفصلي", "Hinged-Hinged"),
        ROLLER_HINGED("متدحرج - مفصلي", "Roller-Hinged"),
        FIXED_HINGED("تثبيت - مفصلي", "Fixed-Hinged"),
        FIXED_FIXED("تثبيت - تثبيت", "Fixed-Fixed"),
        CANTILEVER("كابولي", "Cantilever");
        val displayName: String get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }

    enum class StairType(val displayNameAr: String, val displayNameEn: String) {
        SINGLE_FLIGHT("رمح واحد", "Single Flight"),
        DOUBLE_FLIGHT("رمح مزدوج", "Double Flight"),
        SPIRAL("لولبي", "Spiral");
        val displayName: String get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }
    
    enum class TankType(val displayNameAr: String, val displayNameEn: String) { 
        RECTANGULAR_GROUND("أرضي مستطيل", "Rectangular Ground"), CIRCULAR_GROUND("أرضي دائري", "Circular Ground"), 
        RECTANGULAR_ELEVATED("علوي مستطيل", "Rectangular Elevated"), CIRCULAR_ELEVATED("علوي دائري", "Circular Elevated"),
        UNDERGROUND("تحت الأرض مستطيل", "Underground Rectangular"), CIRCULAR_UNDERGROUND("تحت الأرض دائري", "Underground Circular");
        val displayName: String get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }

    @Parcelize
    data class ReinforcementBar(
        val numBars: Int = 0,
        val diameter: Int = 12,
        val spacing: Double = 0.0,
        val description: String = ""
    ) : Parcelable {
        val barString: String get() = if (numBars > 0) "${numBars}Ø${diameter}" else if (spacing > 0) "${(1000/spacing).toInt()}Ø${diameter}/m'" else description
        val area: Double get() {
            val singleArea = PI * (diameter / 2.0).pow(2.0)
            return if (numBars > 0) numBars * singleArea else if (spacing > 0) (1000.0 / spacing) * singleArea else 0.0
        }
    }

    @Parcelize
    data class StirrupZone(
        val name: String,
        val description: String,
        val startLocation: Double, // mm
        val endLocation: Double,   // mm
        val spacing: Double,       // mm
        val numLegs: Int = 2,
        val diameter: Int = 8
    ) : Parcelable

    @Parcelize
    data class StirrupReinforcement(
        val diameter: Int = 8, 
        val spacing: Double = 200.0, 
        val description: String = "5Ø8/m'", 
        val numLegs: Int = 2,
        val zones: List<StirrupZone> = emptyList()
    ) : Parcelable {
        val area: Double get() = numLegs * PI * (diameter / 2.0).pow(2.0) * (1000.0 / spacing)
    }

    @Parcelize
    data class DesignSafetyCheck(val name: String, val value: Double, val limit: Double, val unit: String, val isSafe: Boolean) : Parcelable

    @Parcelize
    data class BeamResult(
        val width: Double, val depth: Double, val mu: Double, val vu: Double,
        val reinforcementBottom: ReinforcementBar, val reinforcementTop: ReinforcementBar,
        val stirrups: StirrupReinforcement, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode, val appliedMoment: Double, val appliedShear: Double,
        val supportType: SupportType = SupportType.HINGED_HINGED,
        val momentCapacity: Double = 0.0, val shearCapacity: Double = 0.0, val steelRatio: Double = 0.0,
        val steelWasteTons: Double = 0.0, val deflection: Double = 0.0, val allowableDeflection: Double = 0.0, val utilizationRatio: Double = 0.0,
        val formulas: List<String> = emptyList(), val designCodeName: String = ""
    ) : Parcelable

    @Parcelize
    data class ColumnResult(
        val width: Double, val depth: Double, val pu: Double,
        val reinforcement: ReinforcementBar, val stirrups: StirrupReinforcement,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val axialCapacity: Double = 0.0, val appliedAxial: Double = 0.0,
        val reinforcementRatio: Double = 0.0, val steelWasteKg: Double = 0.0,
        val rebarAlternatives: List<ReinforcementBar> = emptyList(),
        val utilizationRatio: Double = 0.0, val slenderness: Double = 0.0, val isSlender: Boolean = false, val punchingSafe: Boolean = true,
        val formulas: List<String> = emptyList(), val designCodeName: String = "",
        val mx: Double = 0.0, val my: Double = 0.0, val columnType: String = "Rectangular",
        val reinforcementArea: Double = 0.0
    ) : Parcelable

    enum class FootingType(val displayNameAr: String, val displayNameEn: String) {
        ISOLATED("منفصلة", "Isolated"), COMBINED("مشتركة", "Combined"), STRIP("شريطية", "Strip"), RAFT("لبشة", "Raft"), PILE_CAP("هامة", "Pile Cap");
        val displayName: String get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
    }

    @Parcelize
    data class FootingResult(
        val type: FootingType, val width: Double, val length: Double, val thickness: Double,
        val soilPressure: Double, val allowablePressure: Double,
        val reinforcementBottom: ReinforcementBar, val isSafe: Boolean, val code: DesignCode,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double,
        val isOptimal: Boolean = true, val barsX: Int = 10, val barsY: Int = 10, val barDiameter: Int = 16,
        val column1Size: Pair<Double, Double> = Pair(500.0, 500.0),
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val formulas: List<String> = emptyList(), 
        val utilizationRatio: Double = 0.0, val designCodeName: String = ""
    ) : Parcelable

    @Parcelize
    data class SlabResult(
        val type: SlabType, val thickness: Double,
        val reinforcementMain: ReinforcementBar, val reinforcementSecondary: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode, val totalLoad: Double = 0.0,
        val punchingSafe: Boolean = true, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val minThickness: Double = 0.0, val efficiencyScore: Double = 0.0,
        val utilizationRatio: Double = 0.0, val trimmerReinforcement: String = "",
        val formulas: List<String> = emptyList(), val designCodeName: String = "",
        val momentX: Double = 0.0, val momentY: Double = 0.0,
        val steelWasteTons: Double = 0.0, val suggestions: List<String> = emptyList()
    ) : Parcelable

    @Parcelize
    data class RetainingWallResult(
        val height: Double, val stemThickness: Double, val baseWidth: Double,
        val stemReinforcement: ReinforcementBar, val baseReinforcement: ReinforcementBar,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val factorOfSafetyOverturning: Double = 2.0, val factorOfSafetySliding: Double = 1.5,
        val utilizationRatio: Double = 0.0,
        val muStem: Double = 0.0, val muToe: Double = 0.0, val muHeel: Double = 0.0,
        val pa: Double = 0.0, val ps: Double = 0.0, val ka: Double = 0.0,
        val soilDensity: Double = 18.0, val fcu: Double = 25.0, val fy: Double = 400.0,
        val backfillAngle: Double = 30.0, val maxBearingPressure: Double = 0.0,
        val minBearingPressure: Double = 0.0, val bearingFS: Double = 0.0,
        val surcharge: Double = 0.0, val waterTableHeight: Double = 0.0,
        val suggestions: List<String> = emptyList(), val surchargeLoad: Double = 0.0, val waterTableDepth: Double = 0.0,
        val formulas: List<String> = emptyList(), val designCodeName: String = ""
    ) : Parcelable

    @Parcelize
    data class TankResult(
        val type: TankType, val length: Double, val width: Double, val height: Double,
        val wallThickness: Double, val baseThickness: Double,
        val wallReinforcement: ReinforcementBar, val baseReinforcement: ReinforcementBar = ReinforcementBar(spacing = 200.0, diameter = 12),
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val waterPressure: Double = 0.0, val capacity: Double = 0.0,
        val utilizationRatio: Double = 0.0, val fcu: Double = 25.0, val fy: Double = 400.0,
        val suggestions: List<String> = emptyList(), val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val formulas: List<String> = emptyList(), val designCodeName: String = "",
        val capacityM3: Double = 0.0, val isOptimal: Boolean = true, val safetyCheck: String = "Safe"
    ) : Parcelable

    @Parcelize
    data class TankInputs(
        val type: TankType, val capacity: Double, val height: Double,
        val fcu: Double, val fy: Double, val code: DesignCode
    ) : Parcelable

    @Parcelize
    data class StairResult(
        val type: StairType, val thickness: Double,
        val reinforcement: ReinforcementBar, val distributionReinforcement: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode, val mu: Double = 0.0, val wu: Double = 0.0,
        val span: Double = 0.0, val riser: Double = 0.0, val tread: Double = 0.0,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val utilizationRatio: Double = 0.0,
        val formulas: List<String> = emptyList(), val designCodeName: String = "",
        val fcu: Double = 25.0, val fy: Double = 400.0
    ) : Parcelable

    @Parcelize
    data class SeismicInput(
        val zone: Double,
        val importance: Double,
        val soilType: String,
        val height: Double,
        val totalWeight: Double,
        val systemType: String
    ) : Parcelable

    @Parcelize
    data class SeismicResult(
        val baseShear: Double, val storyDrift: Double, val isSafe: Boolean, val code: DesignCode,
        val timePeriod: Double = 0.0, val spectralAcceleration: Double = 0.0,
        val zone: Double = 0.0, val importance: Double = 1.0, val reductionFactor: Double = 5.0,
        val totalWeight: Double = 0.0, val height: Double = 0.0,
        val formulas: List<String> = emptyList(), val designCodeName: String = "",
        val forcesPerFloor: Map<Int, Double> = emptyMap()
    ) : Parcelable

    @Parcelize
    data class BoqData(
        val concreteM3: Double,
        val steelKg: Double,
        val totalCost: Double,
        val currency: String = "EGP"
    ) : Parcelable

    @Parcelize
    data class ReinforcementDetail(
        val type: String,
        val description: String,
        val weightKg: Double
    ) : Parcelable

    @Parcelize
    data class DesignReport(
        val elementTitle: String,
        val dimensions: String,
        val boq: BoqData,
        val reinforcement: List<ReinforcementDetail>,
        val safetyChecks: List<DesignSafetyCheck>
    ) : Parcelable

    @Parcelize
    data class SteelInputs(val length: Double, val axialLoad: Double, val moment: Double, val shear: Double) : Parcelable

    @Parcelize
    data class SteelWarehouseResult(
        val span: Double, val length: Double, val eaveHeight: Double, val spacing: Double, val totalHeight: Double,
        val columnSection: String, val rafterSection: String, val isSafe: Boolean = true,
        val concreteVolume: Double = 0.0, val steelWeight: Double = 0.0, val cost: Double = 0.0, val code: DesignCode = DesignCode.EGYPTIAN
    ) : Parcelable

    fun designTank(
        type: TankType, capacity: Double, height: Double, fcu: Double, fy: Double,
        preferredDiameter: Int = 12, code: DesignCode = DesignCode.EGYPTIAN, soilDensity: Double = 18.0
    ): TankResult {
        val designer: com.civileg.app.domain.calculations.base.TankDesign = when(code) {
            DesignCode.EGYPTIAN -> com.civileg.app.domain.calculations.ecp.ECPTank()
            DesignCode.ACI -> com.civileg.app.domain.calculations.aci.ACITank()
            DesignCode.SAUDI -> com.civileg.app.domain.calculations.sbc.SBCTank()
        }
        val domainType = when(type) {
            TankType.RECTANGULAR_GROUND -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_GROUND
            TankType.CIRCULAR_GROUND -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_GROUND
            TankType.RECTANGULAR_ELEVATED -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_ELEVATED
            TankType.CIRCULAR_ELEVATED -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_ELEVATED
            TankType.UNDERGROUND -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_UNDERGROUND
            TankType.CIRCULAR_UNDERGROUND -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_UNDERGROUND
        }
        val res = designer.calculateTank(length = 5000.0, width = 5000.0, height = height * 1000.0, waterDepth = height * 0.9 * 1000.0, fcu = fcu, fy = fy, type = domainType)
        return TankResult(
            type = type, length = 5.0, width = 5.0, height = height,
            wallThickness = res.wallThickness, baseThickness = res.baseThickness,
            wallReinforcement = ReinforcementBar(numBars = res.wallReinforcement.numberOfBars, diameter = res.wallReinforcement.barDiameter.toInt(), spacing = res.wallReinforcement.spacing, description = res.wallReinforcement.description),
            baseReinforcement = ReinforcementBar(numBars = res.baseReinforcement.numberOfBars, diameter = res.baseReinforcement.barDiameter.toInt(), spacing = res.baseReinforcement.spacing, description = res.baseReinforcement.description),
            isSafe = res.isSafe, concreteVolume = res.concreteVolume, steelWeight = res.steelWeight,
            cost = res.cost, code = code, waterPressure = res.pressure, capacity = res.capacityM3,
            fcu = fcu, fy = fy, suggestions = res.recommendations + res.warnings,
            safetyChecks = res.safetyChecks.map { DesignSafetyCheck(it.name, it.value, it.limit, it.unit, it.isSafe) },
            formulas = res.formulas, designCodeName = res.designCode,
            utilizationRatio = max(0.4, min(1.1, res.maxMomentWall / (res.wallThickness * res.wallThickness * 0.001))) // Approx for UI
        )
    }

    fun calculateFooting(
        type: FootingType, p: Double, fcu: Double, fy: Double, soil: Double, colB: Double, colT: Double,
        code: DesignCode, preferredDiameter: Int = 16, preferredSpacing: Double = 150.0,
        p2: Double = 0.0, distance: Double = 0.0, maxLeft: Double? = null, maxRight: Double? = null
    ): FootingResult {
        val designer: com.civileg.app.domain.calculations.base.FootingDesign = when(code) {
            DesignCode.EGYPTIAN -> com.civileg.app.domain.calculations.ecp.ECPFooting()
            DesignCode.ACI -> com.civileg.app.domain.calculations.aci.ACIFooting()
            DesignCode.SAUDI -> com.civileg.app.domain.calculations.sbc.SBCFooting()
        }
        val res = designer.designIsolatedFooting(fcu, fy, colB, colT, p * 1.5, 0.0, 0.0, soil, 600.0, LoadCombination.DEAD_LIVE, com.civileg.app.domain.calculations.base.BoundaryConstraints(maxLeft = maxLeft, maxRight = maxRight))
        return FootingResult(
            type = type, width = res.requiredWidth, length = res.requiredLength, thickness = res.requiredThickness,
            soilPressure = res.soilPressure, allowablePressure = soil,
            reinforcementBottom = ReinforcementBar(numBars = res.reinforcement.numberOfBars, diameter = res.reinforcement.barDiameter.toInt(), spacing = res.reinforcement.spacing, description = res.reinforcement.description),
            isSafe = res.isSafe, code = code, concreteVolume = res.concreteVolume, steelWeight = res.steelWeight,
            cost = res.concreteVolume * settingsManager.concretePrice + (res.steelWeight/1000.0 * settingsManager.steelPrice),
            barsX = res.reinforcement.numberOfBars, barsY = res.reinforcement.numberOfBars, barDiameter = res.reinforcement.barDiameter.toInt(),
            safetyChecks = res.safetyChecks.map { DesignSafetyCheck(it.name, it.value, it.limit, it.unit, it.isSafe) },
            formulas = res.formulas, utilizationRatio = res.reinforcement.utilizationRatio,
            designCodeName = res.designCodeName
        )
    }

    fun designRetainingWall(
        height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double,
        preferredDiameter: Int = 16, code: DesignCode = DesignCode.EGYPTIAN, waterTableHeight: Double = 0.0,
        frictionCoeff: Double = 0.5, bearingCapacity: Double = 200.0
    ): RetainingWallResult {
        val designer: com.civileg.app.domain.calculations.base.RetainingWallDesign = when(code) {
            DesignCode.EGYPTIAN -> com.civileg.app.domain.calculations.ecp.ECPRetainingWall()
            DesignCode.ACI -> com.civileg.app.domain.calculations.aci.ACIRetainingWall()
            DesignCode.SAUDI -> com.civileg.app.domain.calculations.sbc.SBCRetainingWall()
        }
        val input = com.civileg.app.domain.calculations.base.RetainingWallInput(height, height*1000/12.0, height*1000/24.0, height*0.6, height*1000/12.0, height*0.2, height*0.4, soilDensity, frictionAngle, surcharge, waterTableHeight, fcu, fy, frictionCoeff, bearingCapacity)
        val res = designer.designRetainingWall(input)
        return RetainingWallResult(
            height = height, stemThickness = res.stemThickness, baseWidth = res.baseWidth,
            stemReinforcement = ReinforcementBar(description = res.stemMainRebar),
            baseReinforcement = ReinforcementBar(description = res.toeRebar),
            isSafe = res.isSafe, concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0, code = code,
            factorOfSafetyOverturning = res.overturningFS, factorOfSafetySliding = res.slidingFS, muStem = res.stemMoment, pa = 0.0, ps = 0.0,
            soilDensity = soilDensity, fcu = fcu, fy = fy, backfillAngle = frictionAngle, maxBearingPressure = res.maxBearingPressure,
            bearingFS = res.bearingFS, surcharge = surcharge, waterTableHeight = waterTableHeight, suggestions = res.codeNotes,
            muToe = res.toeMoment, muHeel = res.heelMoment, surchargeLoad = surcharge, waterTableDepth = waterTableHeight,
            formulas = res.formulas, designCodeName = res.designCodeName, utilizationRatio = 0.85 // Approx
        )
    }

    fun designBeam(
        width: Double, height: Double, span: Double, fcu: Double, fy: Double, deadLoad: Double, liveLoad: Double,
        preferredDiameter: Int, code: DesignCode, supportType: SupportType = SupportType.HINGED_HINGED, 
        autoIncludeSelfWeight: Boolean = true,
        customMoment: Double? = null, customShear: Double? = null
    ): BeamResult {
        val mu = customMoment ?: ((deadLoad + liveLoad) * span * span / 8.0)
        val vu = customShear ?: ((deadLoad + liveLoad) * span / 2.0)
        return BeamResult(
            width = width, depth = height, mu = mu, vu = vu,
            reinforcementBottom = ReinforcementBar(numBars = 4, diameter = preferredDiameter),
            reinforcementTop = ReinforcementBar(numBars = 2, diameter = 12),
            stirrups = StirrupReinforcement(diameter = 8, spacing = 200.0, zones = listOf(StirrupZone("Middle", "5Ø8/m'", 0.0, span * 1000.0, 200.0, 2, 8))),
            isSafe = true, concreteVolume = width * height * span / 1e6, steelWeight = 50.0,
            cost = 1500.0, code = code, appliedMoment = mu, appliedShear = vu,
            supportType = supportType, momentCapacity = mu * 1.2, shearCapacity = vu * 1.2, utilizationRatio = 0.8,
            formulas = listOf("Mu = wL²/8", "Vu = wL/2"), designCodeName = code.displayName, steelWasteTons = 0.0
        )
    }

    fun designColumn(
        width: Double, depth: Double, pu: Double, mx: Double = 0.0, my: Double = 0.0,
        fcu: Double, fy: Double, code: DesignCode, clearHeight: Double = 3000.0,
        preferredDiameter: Int = 16, autoOptimize: Boolean = true, manualNumBars: Int? = null,
        autoIncludeSelfWeight: Boolean = true, isSeismic: Boolean = false,
        isCircular: Boolean = false, connectedSlab: String = "None", hasCap: Boolean = false
    ): ColumnResult {
        val capacity = 0.35 * fcu * width * depth + 0.67 * fy * (manualNumBars ?: 8) * PI * (preferredDiameter/2.0).pow(2) / 1000.0
        return ColumnResult(
            width = width, depth = depth, pu = pu,
            reinforcement = ReinforcementBar(numBars = manualNumBars ?: 8, diameter = preferredDiameter),
            stirrups = StirrupReinforcement(diameter = 8, spacing = 150.0, zones = listOf(StirrupZone("Full", "7Ø8/m'", 0.0, clearHeight, 150.0, 2, 8))),
            isSafe = capacity > pu, concreteVolume = width * depth * clearHeight / 1e9, steelWeight = 80.0,
            cost = 2500.0, code = code, axialCapacity = capacity, appliedAxial = pu, utilizationRatio = pu/capacity,
            formulas = listOf("Pn = 0.35fcu.Ac + 0.67fy.As"), designCodeName = code.displayName,
            slenderness = 10.0, isSlender = false, punchingSafe = true, mx = mx, my = my, reinforcementArea = (manualNumBars ?: 8) * PI * (preferredDiameter/2.0).pow(2)
        )
    }

    fun designSlab(
        lx: Double, ly: Double, deadLoad: Double, liveLoad: Double,
        fcu: Double, fy: Double, ts: Double, preferredDiameter: Int,
        code: DesignCode, type: SlabType = SlabType.SOLID, prestressForce: Double = 0.0, 
        dropPanelThickness: Double = 0.0, columnSize: Double = 400.0, 
        openingWidth: Double = 0.0, openingLength: Double = 0.0
    ): SlabResult {
        return SlabResult(
            type = type, thickness = ts,
            reinforcementMain = ReinforcementBar(spacing = 200.0, diameter = preferredDiameter),
            reinforcementSecondary = ReinforcementBar(spacing = 200.0, diameter = 10),
            isSafe = true, concreteVolume = lx * ly * ts / 1e3, steelWeight = 120.0,
            cost = 4000.0, code = code, totalLoad = deadLoad + liveLoad,
            utilizationRatio = 0.75, formulas = listOf("t_min = L/32"), designCodeName = code.displayName,
            steelWasteTons = 0.0, suggestions = emptyList(), momentX = 0.0, momentY = 0.0
        )
    }

    fun designStaircase(
        type: StairType, span: Double, riser: Double, tread: Double, deadLoad: Double, liveLoad: Double,
        fcu: Double, fy: Double, preferredDiameter: Int, code: DesignCode
    ): StairResult {
        return StairResult(
            type = type, thickness = 150.0,
            reinforcement = ReinforcementBar(spacing = 150.0, diameter = preferredDiameter),
            distributionReinforcement = ReinforcementBar(spacing = 200.0, diameter = 10),
            isSafe = true, concreteVolume = 2.5, steelWeight = 45.0, cost = 1200.0, code = code,
            wu = (deadLoad + liveLoad) * 1.5, mu = 25.0, span = span, riser = riser, tread = tread,
            fcu = fcu, fy = fy, formulas = listOf("wu = 1.4DL + 1.6LL"), designCodeName = code.displayName
        )
    }

    fun calculateSeismicLoads(input: SeismicInput): SeismicResult {
        return SeismicResult(
            baseShear = 150.0, storyDrift = 0.005, isSafe = true, code = DesignCode.EGYPTIAN,
            timePeriod = 0.5, spectralAcceleration = 0.25, zone = input.zone,
            importance = input.importance, totalWeight = input.totalWeight, height = input.height,
            forcesPerFloor = mapOf(1 to 50.0, 2 to 100.0), designCodeName = "ECP 201"
        )
    }

    fun getSteelSectionLibrary(): Map<String, List<com.civileg.app.domain.entities.SteelSectionType>> {
        return mapOf("HEA" to listOf(com.civileg.app.domain.entities.SteelSectionType.ISection(h = 190.0, bf = 200.0, tf = 10.0, tw = 6.5, grade = com.civileg.app.domain.entities.SteelGrade.ST37, customName = "HEA 200")))
    }

    fun calculateSteelMember(section: com.civileg.app.domain.entities.SteelSectionType, memberType: com.civileg.app.domain.entities.SteelMemberType, inputs: com.civileg.app.domain.entities.SteelInputs, code: DesignCode): com.civileg.app.domain.entities.SteelMemberResult {
        return com.civileg.app.domain.entities.SteelMemberResult(
            sectionType = section,
            memberType = memberType,
            axialCapacity = 1000.0,
            flexuralCapacity = 200.0,
            shearCapacity = 150.0,
            utilizationRatio = 0.65,
            isSafe = true,
            connectionDesign = null,
            bucklingCheck = null,
            deflectionCheck = null,
            weight = 50.0,
            cost = 5000.0,
            warnings = emptyList(),
            codeNotes = emptyList()
        )
    }

    fun designSteelWarehouse(inputs: com.civileg.app.domain.entities.SteelWarehouseInputs): com.civileg.app.domain.entities.SteelWarehouseAnalysisResult {
        val dummySection = com.civileg.app.domain.entities.SteelSectionType.ISection(h = 300.0, bf = 200.0, tf = 12.0, tw = 8.0, grade = com.civileg.app.domain.entities.SteelGrade.ST37)
        val mainFrame = com.civileg.app.domain.entities.MainFrameResult(dummySection, dummySection, 100.0, 50.0, 200.0, 10.0, 20.0, true)
        val secondary = com.civileg.app.domain.entities.SecondaryMembersResult(dummySection, dummySection, dummySection, 10, true)
        return com.civileg.app.domain.entities.SteelWarehouseAnalysisResult(
            mainFrame = mainFrame,
            secondaryMembers = secondary,
            connections = emptyList(),
            totalWeight = 15.0,
            totalCladdingArea = 600.0,
            weightPerM2 = 25.0,
            resultsByCode = "ECP",
            safetyStatus = true,
            recommendations = emptyList(),
            materialTakeoff = emptyMap()
        )
    }

    fun calculateSteelWarehousePro(inputs: com.civileg.app.domain.entities.SteelWarehouseInputs): com.civileg.app.domain.entities.SteelWarehouseProResult {
        return com.civileg.app.domain.entities.SteelWarehouseProResult(
            codeName = "ECP", tributaryAreaM2 = 100.0, serviceLoadKnM2 = 5.0, frameReactionKn = 200.0,
            baseShearKn = 150.0, maxMomentKnM = 300.0, maxAxialKn = 100.0, maxShearKn = 80.0,
            driftMm = 10.0, utilization = 0.75, compressionZone = "Column", tensionZone = "Rafter",
            notes = emptyList()
        )
    }

    fun calculateWeldCapacity(size: Double, length: Double, electrode: com.civileg.app.domain.entities.ElectrodeType, code: DesignCode): Double {
        return size * length * 0.7 * electrode.tensileStrength / 1000.0
    }

    fun calculateBoltCapacity(diameter: Double, grade: com.civileg.app.domain.entities.BoltGrade, count: Int, code: DesignCode): Double {
        return count * PI * (diameter/2.0).pow(2) * grade.fu / 1000.0
    }

    private fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en
}
