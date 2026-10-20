package com.civileg.app.utils

import android.os.Parcelable
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.aci.AISCSteelDesignEngine
import com.civileg.app.domain.calculations.aci.SteelWindEngine
import com.civileg.app.domain.calculations.base.RetainingWallInput
import com.civileg.app.domain.calculations.base.StaircaseInput
import com.civileg.app.domain.calculations.base.SeismicZone
import com.civileg.app.domain.calculations.base.SoilType
import com.civileg.app.domain.calculations.base.SeismicDesign
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.ShearWallInput
import com.civileg.app.domain.ShearWallResult
import com.civileg.app.domain.FlatSlabInput
import com.civileg.app.domain.FlatSlabResult
import com.civileg.app.domain.PileInput
import com.civileg.app.domain.PileDesignResult
import com.civileg.app.domain.calculations.ecp.SteelConnectionDesign
import com.civileg.app.domain.calculations.ecp.SteelDesignEngine
import com.civileg.app.domain.calculations.sbc.SBCSteelDesignEngine
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.calculations.base.FootingDesignResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
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

        fun toDomain(): com.civileg.app.domain.calculations.base.StairType = when(this) {
            SINGLE_FLIGHT -> com.civileg.app.domain.calculations.base.StairType.STRAIGHT
            DOUBLE_FLIGHT -> com.civileg.app.domain.calculations.base.StairType.DOG_LEG
            TRIPLE_FLIGHT -> com.civileg.app.domain.calculations.base.StairType.OPEN_WELL
            CANTILEVER -> com.civileg.app.domain.calculations.base.StairType.STRAIGHT
            SPIRAL -> com.civileg.app.domain.calculations.base.StairType.SPIRAL
        }
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
        val area: Double get() = numLegs * PI * (diameter.toDouble() / 2.0).pow(2.0)
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
        val cost: Double = 0.0, val code: DesignCode = DesignCode.ECP, 
        val appliedMoment: Double = 0.0, val appliedShear: Double = 0.0,
        val supportType: SupportType = SupportType.HINGED_HINGED,
        val span: Double = 0.0,
        val momentCapacity: Double = 0.0, val shearCapacity: Double = 0.0, val steelRatio: Double = 0.0,
        val deflection: Double = 0.0, val allowableDeflection: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val calculationSteps: @RawValue List<CalculationStep> = emptyList(),
        val steelWasteTons: Double = 0.0,
        val neutralAxisDepth: Double = 0.0,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList()
    ) : Parcelable

    @Parcelize
    data class ColumnResult(
        val width: Double = 0.0, val depth: Double = 0.0, val pu: Double = 0.0,
        val mx: Double = 0.0, val my: Double = 0.0,
        val reinforcement: ReinforcementBar = ReinforcementBar(), val stirrups: StirrupReinforcement = StirrupReinforcement(),
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean = false,
        val concreteVolume: Double = 0.0, val steelWeight: Double = 0.0, val cost: Double = 0.0, val code: DesignCode = DesignCode.ECP,
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
        val confinementLength: Double = 0.0,
        val calculationSteps: @RawValue List<CalculationStep> = emptyList(),
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList()
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
        val reinforcementTopY: Int = 0,
        val calculationSteps: @RawValue List<CalculationStep> = emptyList()
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
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val calculationSteps: @RawValue List<CalculationStep> = emptyList()
    ) : Parcelable

    @Parcelize
    data class SlabResult(
        val type: SlabType = SlabType.SOLID, val thickness: Double = 0.0,
        val reinforcementMain: ReinforcementBar = ReinforcementBar(), val reinforcementSecondary: ReinforcementBar = ReinforcementBar(),
        val isSafe: Boolean = false, val concreteVolume: Double = 0.0, val steelWeight: Double = 0.0,
        val cost: Double = 0.0, val code: DesignCode = DesignCode.ECP,
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
        val punchingStressAtDrop: Double = 0.0,
        val calculationSteps: @RawValue List<CalculationStep> = emptyList()
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
        val suggestions: List<String> = emptyList(),
        val calculationSteps: @RawValue List<CalculationStep> = emptyList()
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
        val suggestions: List<String> = emptyList(),
        val calculationSteps: @RawValue List<CalculationStep> = emptyList()
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
        val suggestions: List<String> = emptyList(),
        val calculationSteps: @RawValue List<CalculationStep> = emptyList()
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
        val code: DesignCode = DesignCode.ECP
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
        // ── InputGuard (ADR-010) ──
        InputGuard.notNull("section", section)
        InputGuard.notNull("memberType", memberType)
        InputGuard.notNull("inputs", inputs)
        InputGuard.notNull("code", code)

        // P0-2: delegate to the code-specific domain engine so ECP / ACI / SBC produce
        // distinct, code-correct results (previously identical inline math for all codes,
        // with a KL/r unit bug injecting a spurious ×1000 factor).
        val domain = code
        val grade = inputs.grade
        val Pu = inputs.axialLoad.coerceAtLeast(0.0)
        val Mu = inputs.moment.coerceAtLeast(0.0)
        val Vu = inputs.shear.coerceAtLeast(0.0)
        val Lmm = inputs.length.coerceAtLeast(1.0)
        val Lbmm = inputs.unbracedLength.coerceAtLeast(1.0)

        return when (domain) {
            com.civileg.app.domain.entities.DesignCode.ECP ->
                designSteelMemberEcp(section, memberType, grade, Pu, Mu, Vu, Lmm, Lbmm)
            com.civileg.app.domain.entities.DesignCode.ACI ->
                designSteelMemberAisc(section, memberType, grade, Pu, Mu, Vu, Lmm, Lbmm)
            com.civileg.app.domain.entities.DesignCode.SBC ->
                designSteelMemberSbc(section, memberType, grade, Pu, Mu, Vu, Lmm, Lbmm)
        }
    }

    /** Capacity = reference demand ÷ utilization ratio measured at that reference demand. */
    private fun steelCapacityFromRatio(refDemand: Double, refRatio: Double): Double =
        if (refRatio > 1e-12) refDemand / refRatio else 0.0

    /** Map the entity grade onto the ECP engine's own 3-grade enum (nearest Fy). */
    private fun ecpSteelGrade(grade: SteelGrade): SteelDesignEngine.SteelGrade = when (grade) {
        SteelGrade.ST37 -> SteelDesignEngine.SteelGrade.ST37
        SteelGrade.ST44 -> SteelDesignEngine.SteelGrade.ST44
        SteelGrade.ST52 -> SteelDesignEngine.SteelGrade.ST52
        SteelGrade.A36 -> SteelDesignEngine.SteelGrade.ST37
        SteelGrade.A572_G50 -> SteelDesignEngine.SteelGrade.ST52
        SteelGrade.A992 -> SteelDesignEngine.SteelGrade.ST52
        SteelGrade.S275 -> SteelDesignEngine.SteelGrade.ST44
        SteelGrade.S355 -> SteelDesignEngine.SteelGrade.ST52
    }

    /** Map the entity section onto the ECP engine's SectionProperties (single source of truth). */
    private fun ecpSectionProperties(section: SteelSectionType): SteelDesignEngine.SectionProperties =
        SteelDesignEngine.SectionProperties(
            name = section.sectionName,
            h = section.depth, b = section.width,
            tw = section.webThickness, tf = section.flangeThickness,
            Ix = section.ix, Iy = section.iy,
            Zx = section.zx, Zy = section.zy,
            Sx = section.sx, Sy = section.sy,
            rx = section.rx, ry = section.ry,
            A = section.area, J = section.j
        )

    // ---- P1-6 / AUDIT 9.20 (b): engine-derived member end connection ----

    /** All three code engines expose the same designConnection entry point. */
    private fun designMemberConnection(
        code: DesignCode,
        connectionType: ConnectionType,
        appliedForce: Double,
        plateThickness: Double,
        grade: SteelGrade
    ): ConnectionDesignResult = when (code) {
        DesignCode.ECP ->
            SteelConnectionDesign().designConnection(connectionType, appliedForce, plateThickness, grade)
        DesignCode.ACI ->
            AISCSteelDesignEngine().designConnection(connectionType, appliedForce, plateThickness, grade)
        DesignCode.SBC ->
            SBCSteelDesignEngine().designConnection(connectionType, appliedForce, plateThickness, grade)
    }

    /**
     * Auto-size the member end connection from the actual end-force demand (AUDIT 9.20):
     * M20/Gr8.8 double-row bearing-type candidates with an increasing bolt count — the
     * first safe candidate wins; if none passes, the largest one is returned so the
     * drawing/PDF reports an honestly-unsafe connection with the engine's warnings.
     */
    private fun autoDesignMemberConnection(
        code: DesignCode,
        appliedForce: Double,
        plateThickness: Double,
        grade: SteelGrade
    ): ConnectionDesignResult {
        var last: ConnectionDesignResult? = null
        for (count in intArrayOf(4, 6, 8, 10)) {
            val candidate = ConnectionType.Bolted(
                boltDiameter = 20.0,
                boltGrade = BoltGrade.GRADE_8_8,
                numberOfBolts = count,
                boltPattern = BoltPattern.DOUBLE_ROW,
                connectionType = BoltConnectionType.BEARING
            )
            val r = designMemberConnection(code, candidate, appliedForce, plateThickness, grade)
            if (r.isSafe) return r
            last = r
        }
        // The loop always iterates at least once, so `last` is non-null here.
        return last!!
    }

    /**
     * End force (kN) the connection must transfer: axial, shear, or the equivalent
     * flange pair from the end moment (M / depth) — the worst case among them.
     */
    private fun memberEndForce(pu: Double, mu: Double, vu: Double, depthMm: Double): Double =
        maxOf(abs(pu), abs(vu), if (depthMm > 0.0) abs(mu) * 1000.0 / depthMm else 0.0)

    /** ECP 205 steel member — via ecp.SteelDesignEngine. */
    private fun designSteelMemberEcp(
        section: SteelSectionType, memberType: SteelMemberType, grade: SteelGrade,
        Pu: Double, Mu: Double, Vu: Double, Lmm: Double, Lbmm: Double
    ): SteelMemberResult {
        val engine = SteelDesignEngine()
        val props = ecpSectionProperties(section)
        val ecpGrade = ecpSteelGrade(grade)
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val mRef = max(Mu, 1.0)
        val vRef = max(Vu, 1.0)
        val beam = engine.designBeam(mRef, vRef, 0.0, Lmm, props, ecpGrade, Lbmm)
        val flexuralCapacity = steelCapacityFromRatio(mRef, beam.momentCheck.utilizationRatio)
        val shearCapacity = steelCapacityFromRatio(vRef, beam.shearCheck.utilizationRatio)

        val pRef = max(Pu, 1.0)
        val compression = engine.checkColumnCompression(pRef, props, ecpGrade, 1.0, Lmm)
        val axialCapacity = steelCapacityFromRatio(pRef, compression.axialCheck.utilizationRatio)

        val axialRatio = if (axialCapacity > 0) Pu / axialCapacity else 0.0
        val flexRatio = if (flexuralCapacity > 0) Mu / flexuralCapacity else 0.0
        val shearRatio = if (shearCapacity > 0) Vu / shearCapacity else 0.0
        val utilization = max(axialRatio, max(flexRatio, shearRatio))

        warnings.addAll(beam.warnings)
        warnings.addAll(compression.warnings)
        codeNotes.addAll(beam.codeNotes)
        codeNotes.addAll(compression.codeNotes)
        codeNotes.add("ECP 205 — utilization = max(axial, flexure, shear) = ${"%.3f".format(utilization)}")

        val bucklingCheck = if (memberType == SteelMemberType.COLUMN || Pu > 0) BucklingCheckResult(
            slendernessRatio = compression.slendernessRatio,
            criticalStress = 0.0,
            bucklingMode = BucklingMode.FLEXURAL,
            isSafe = compression.axialCheck.isSafe,
            codeReference = CodeReference.ECP.STEEL_COLUMN_COMPRESSION
        ) else null

        return SteelMemberResult(
            sectionType = section,
            memberType = memberType,
            axialCapacity = axialCapacity,
            flexuralCapacity = flexuralCapacity,
            shearCapacity = shearCapacity,
            utilizationRatio = utilization,
            isSafe = utilization <= 1.0 && beam.isSafe && compression.isSafe,
            connectionDesign = autoDesignMemberConnection(
                code = DesignCode.ECP,
                appliedForce = memberEndForce(Pu, Mu, Vu, section.depth),
                plateThickness = maxOf(section.webThickness, section.flangeThickness, 8.0),
                grade = grade
            ),
            bucklingCheck = bucklingCheck,
            deflectionCheck = null,
            weight = section.weight,
            cost = 0.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /** AISC 360 steel member — via aci.AISCSteelDesignEngine. */
    private fun designSteelMemberAisc(
        section: SteelSectionType, memberType: SteelMemberType, grade: SteelGrade,
        Pu: Double, Mu: Double, Vu: Double, Lmm: Double, Lbmm: Double
    ): SteelMemberResult {
        val engine = AISCSteelDesignEngine()
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val mRef = max(Mu, 1.0)
        val vRef = max(Vu, 1.0)
        val flex = engine.designFlexuralMember(section, grade, mRef, 0.0, vRef, 0.0, Lbmm, 1.0, false)
        val flexuralCapacity = flex.momentCapacityX
        val shearCapacity = flex.shearCapacityX

        val pRef = max(Pu, 1.0)
        val comp = engine.designCompressionMember(section, grade, pRef, 1.0, 1.0, Lmm, Lmm)
        val axialCapacity = comp.phiPn

        val axialRatio = if (axialCapacity > 0) Pu / axialCapacity else 0.0
        val flexRatio = if (flexuralCapacity > 0) Mu / flexuralCapacity else 0.0
        val shearRatio = if (shearCapacity > 0) Vu / shearCapacity else 0.0
        val utilization = if (Pu > 0 && Mu > 0)
            engine.checkCombinedLoading(Pu, Mu, 0.0, section, grade, 1.0, 1.0, Lmm, Lmm).interactionRatio
        else max(axialRatio, max(flexRatio, shearRatio))

        warnings.addAll(flex.warnings)
        warnings.addAll(comp.warnings)
        codeNotes.addAll(flex.codeNotes)
        codeNotes.addAll(comp.codeNotes)
        codeNotes.add("AISC 360-16 — utilization = ${"%.3f".format(utilization)}")

        val bucklingCheck = if (memberType == SteelMemberType.COLUMN || Pu > 0) BucklingCheckResult(
            slendernessRatio = comp.slendernessRatio,
            criticalStress = comp.Fcr,
            bucklingMode = BucklingMode.FLEXURAL,
            isSafe = comp.isSafe,
            codeReference = CodeReference.ACI.STEEL_COLUMN_COMPRESSION
        ) else null

        return SteelMemberResult(
            sectionType = section,
            memberType = memberType,
            axialCapacity = axialCapacity,
            flexuralCapacity = flexuralCapacity,
            shearCapacity = shearCapacity,
            utilizationRatio = utilization,
            isSafe = utilization <= 1.0 && flex.isSafe && comp.isSafe,
            connectionDesign = autoDesignMemberConnection(
                code = DesignCode.ACI,
                appliedForce = memberEndForce(Pu, Mu, Vu, section.depth),
                plateThickness = maxOf(section.webThickness, section.flangeThickness, 8.0),
                grade = grade
            ),
            bucklingCheck = bucklingCheck,
            deflectionCheck = null,
            weight = section.weight,
            cost = 0.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /** SBC 306 steel member — via sbc.SBCSteelDesignEngine. */
    private fun designSteelMemberSbc(
        section: SteelSectionType, memberType: SteelMemberType, grade: SteelGrade,
        Pu: Double, Mu: Double, Vu: Double, Lmm: Double, Lbmm: Double
    ): SteelMemberResult {
        val engine = SBCSteelDesignEngine()
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val mRef = max(Mu, 1.0)
        val vRef = max(Vu, 1.0)
        val beam = engine.designBeam(section, grade, mRef, vRef, 0.0, Lmm, Lbmm, 1.0)
        val flexuralCapacity = beam.phiMn
        val shearCapacity = beam.phiVn

        val pRef = max(Pu, 1.0)
        val col = engine.designColumn(section, grade, pRef, 0.0, 0.0, 1.0, 1.0, Lmm, Lmm)
        val axialCapacity = col.phiPn

        val axialRatio = if (axialCapacity > 0) Pu / axialCapacity else 0.0
        val flexRatio = if (flexuralCapacity > 0) Mu / flexuralCapacity else 0.0
        val shearRatio = if (shearCapacity > 0) Vu / shearCapacity else 0.0
        val utilization = if (Pu > 0 && Mu > 0)
            engine.checkCombinedLoading(Pu, Mu, 0.0, section, grade, 1.0, 1.0, Lmm, Lmm).interactionRatio
        else max(axialRatio, max(flexRatio, shearRatio))

        warnings.addAll(beam.warnings)
        warnings.addAll(col.warnings)
        codeNotes.addAll(beam.codeNotes)
        codeNotes.addAll(col.codeNotes)
        codeNotes.add("SBC 306 — utilization = ${"%.3f".format(utilization)}")

        val deflectionCheck = DeflectionCheckResult(
            calculatedDeflection = beam.deflection,
            allowableDeflection = beam.deflectionAllowable,
            ratio = beam.deflectionRatio,
            isSafe = beam.deflection <= beam.deflectionAllowable + 1e-9,
            message = "SBC 306 deflection check",
            recommendation = if (beam.deflection > beam.deflectionAllowable) "افحص الانحراف أو زد القطاع" else ""
        )

        val bucklingCheck = if (memberType == SteelMemberType.COLUMN || Pu > 0) BucklingCheckResult(
            slendernessRatio = col.slendernessRatio,
            criticalStress = col.Fcr,
            bucklingMode = BucklingMode.FLEXURAL,
            isSafe = col.isSafe,
            codeReference = CodeReference.SBC.STEEL_COLUMN_COMPRESSION
        ) else null

        return SteelMemberResult(
            sectionType = section,
            memberType = memberType,
            axialCapacity = axialCapacity,
            flexuralCapacity = flexuralCapacity,
            shearCapacity = shearCapacity,
            utilizationRatio = utilization,
            isSafe = utilization <= 1.0 && beam.isSafe && col.isSafe,
            connectionDesign = autoDesignMemberConnection(
                code = DesignCode.ACI,
                appliedForce = memberEndForce(Pu, Mu, Vu, section.depth),
                plateThickness = maxOf(section.webThickness, section.flangeThickness, 8.0),
                grade = grade
            ),
            bucklingCheck = bucklingCheck,
            deflectionCheck = deflectionCheck,
            weight = section.weight,
            cost = 0.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /** (axialUtil, flexUtil, shearUtil) for a section under (Pu, Mu, Vu), by code. */
    private fun steelMemberUtilizations(
        section: SteelSectionType, grade: SteelGrade,
        Pu: Double, Mu: Double, Vu: Double, Lmm: Double, Lbmm: Double,
        domain: com.civileg.app.domain.entities.DesignCode
    ): Triple<Double, Double, Double> {
        val r = when (domain) {
            com.civileg.app.domain.entities.DesignCode.ECP ->
                designSteelMemberEcp(section, SteelMemberType.BEAM, grade, Pu, Mu, Vu, Lmm, Lbmm)
            com.civileg.app.domain.entities.DesignCode.ACI ->
                designSteelMemberAisc(section, SteelMemberType.BEAM, grade, Pu, Mu, Vu, Lmm, Lbmm)
            com.civileg.app.domain.entities.DesignCode.SBC ->
                designSteelMemberSbc(section, SteelMemberType.BEAM, grade, Pu, Mu, Vu, Lmm, Lbmm)
        }
        return Triple(
            if (r.axialCapacity > 0) Pu / r.axialCapacity else 0.0,
            if (r.flexuralCapacity > 0) Mu / r.flexuralCapacity else 0.0,
            if (r.shearCapacity > 0) Vu / r.shearCapacity else 0.0
        )
    }

    fun designSteelWarehouse(inputs: SteelWarehouseInputs): SteelWarehouseAnalysisResult {
        InputGuard.positive("span", inputs.span)
        InputGuard.positive("eaveHeight", inputs.eaveHeight)
        InputGuard.positive("baySpacing", inputs.baySpacing)
        InputGuard.positive("length", inputs.length)
        InputGuard.nonNegative("ridgeHeight", inputs.ridgeHeight)
        InputGuard.nonNegative("slope", inputs.slope)
        InputGuard.nonNegative("deadLoad", inputs.deadLoad)
        InputGuard.nonNegative("liveLoad", inputs.liveLoad)
        InputGuard.nonNegative("purlinSpacing", inputs.purlinSpacing)

        val span = inputs.span
        val eave = inputs.eaveHeight
        val ridge = inputs.ridgeHeight.takeIf { it > eave } ?: (eave + span * inputs.slope)
        val bay = inputs.baySpacing
        val length = inputs.length

        val colSection = inputs.overrideColumnSection ?: SteelSectionType.ISection(h = 300.0, bf = 300.0, tf = 14.0, tw = 8.5, grade = SteelGrade.ST37, customName = "HEA 300")
        val rafSection = inputs.overrideRafterSection ?: SteelSectionType.ISection(h = 330.0, bf = 160.0, tf = 11.5, tw = 7.5, grade = SteelGrade.ST37, customName = "IPE 330")
        val purlinSection = inputs.overridePurlinSection ?: SteelSectionType.CSection(h = 160.0, bf = 65.0, tf = 7.5, tw = 5.5, grade = SteelGrade.ST37, customName = "C 160")

        val wu = (1.2 * inputs.deadLoad + 1.6 * inputs.liveLoad) * bay
        val rafterLen = sqrt((span / 2.0).pow(2) + (ridge - eave).pow(2))
        val maxMoment = wu * span.pow(2) / 8.0
        val maxShear = wu * span / 2.0
        val maxAxial = wu * span / 2.0
        val maxDeflection = 5.0 * (inputs.liveLoad * bay) * span.pow(4) * 1e12 / (384.0 * 200000.0 * rafSection.ix)
        val allowableDef = span * 1000.0 / 250.0

        val numBays = max(1, (length / bay).roundToInt())
        val numFrames = numBays + 1
        val purlinSpacing = inputs.purlinSpacing.takeIf { it > 0 } ?: 1.5
        val purlinsPerRafter = ceil(rafterLen / purlinSpacing).toInt() * 2
        val totalPurlinLen = purlinsPerRafter * length

        val frameWeightTons = (2 * eave * colSection.area * 7850.0 + 2 * rafterLen * rafSection.area * 7850.0) / 1e9 * numFrames
        val purlinWeightTons = totalPurlinLen * (purlinSection.area * 7850.0 / 1e6) / 1000.0
        val totalWeightTons = frameWeightTons + purlinWeightTons

        val claddingArea = 2.0 * span * eave + 2.0 * length * eave + 2.0 * rafterLen * length
        val weightPerM2 = (totalWeightTons * 1000.0) / (span * length)
        val cost = totalWeightTons * 1000.0 * 2.5

        // Code-specific, real-grade member checks (previously hardcoded fy=240 for every code/section).
        val (colAxialUtil, _, colShearUtil) = steelMemberUtilizations(
            colSection, colSection.grade, maxAxial, 0.0, maxShear, eave * 1000.0, eave * 1000.0, inputs.code
        )
        val (_, rafFlexUtil, rafShearUtil) = steelMemberUtilizations(
            rafSection, rafSection.grade, 0.0, maxMoment, maxShear, span * 1000.0, bay * 1000.0, inputs.code
        )

        val mainFrame = MainFrameResult(
            columnSection = colSection, rafterSection = rafSection,
            maxMoment = maxMoment, maxShear = maxShear, maxAxial = maxAxial,
            maxDeflection = maxDeflection, allowableDeflection = allowableDef,
            isSafe = maxDeflection <= allowableDef,
            utilizationMoment = rafFlexUtil.coerceIn(0.1, 1.5),
            utilizationShear = max(rafShearUtil, colShearUtil).coerceIn(0.1, 1.5),
            utilizationAxial = colAxialUtil.coerceIn(0.1, 1.5)
        )

        val secondary = SecondaryMembersResult(
            purlinSection = purlinSection, girtSection = purlinSection, bracingSection = purlinSection,
            purlinCount = purlinsPerRafter * numBays, isSafe = true
        )

        val weldedConn = ConnectionType.Welded(weldType = WeldType.FILLET, weldSize = 8.0, weldLength = 200.0, electrodeType = ElectrodeType.E70XX)
        val boltedConn = ConnectionType.Bolted(boltDiameter = 20.0, boltGrade = BoltGrade.GRADE_8_8, numberOfBolts = 6, boltPattern = BoltPattern.DOUBLE_ROW, connectionType = BoltConnectionType.BEARING)

        val connections = listOf(
            SteelConnectionDetail("Base Plate Connection", boltedConn, 400.0, maxAxial, true),
            SteelConnectionDetail("Apex Haunch Connection", weldedConn, maxMoment * 1.2, maxMoment, true)
        )

        return SteelWarehouseAnalysisResult(
            mainFrame = mainFrame, secondaryMembers = secondary, connections = connections,
            totalWeight = totalWeightTons, totalCladdingArea = claddingArea, weightPerM2 = weightPerM2,
            resultsByCode = inputs.code.displayName, safetyStatus = maxDeflection <= allowableDef,
            recommendations = if (maxDeflection > allowableDef) listOf("Increase rafter section height to control deflection") else emptyList(),
            materialTakeoff = mapOf("Columns (${colSection.displayName})" to frameWeightTons * 0.5, "Rafters (${rafSection.displayName})" to frameWeightTons * 0.5, "Purlins (${purlinSection.displayName})" to purlinWeightTons),
            estimatedTotalCost = cost, costPerM2 = cost / (span * length)
        )
    }

    /**
     * Pro wrapper (BoM + costing + ROI) over [designSteelWarehouse].
     *
     * InputGuard (ADR-010): every field this function touches (`span`, `length`, `deadLoad`,
     * `liveLoad`, `code`) is validated by [designSteelWarehouse] on line 1 of this body, before
     * any of them are read below — so there is deliberately no duplicate guard block here.
     */
    fun calculateSteelWarehousePro(inputs: SteelWarehouseInputs): SteelWarehouseProResult {
        val basicRes = designSteelWarehouse(inputs)
        val bom = listOf(
            BOMItem("Main Columns (${basicRes.mainFrame.columnSection.displayName})", "Structural Columns", basicRes.totalWeight * 0.4 * 1000.0 / 100.0, "Tons", basicRes.totalWeight * 0.4 * 1000.0, basicRes.estimatedTotalCost * 0.4),
            BOMItem("Roof Rafters (${basicRes.mainFrame.rafterSection.displayName})", "Structural Beams", basicRes.totalWeight * 0.4 * 1000.0 / 100.0, "Tons", basicRes.totalWeight * 0.4 * 1000.0, basicRes.estimatedTotalCost * 0.4),
            BOMItem("Purlins & Girts (${basicRes.secondaryMembers.purlinSection.displayName})", "Secondary Members", basicRes.totalWeight * 0.2 * 1000.0 / 100.0, "Tons", basicRes.totalWeight * 0.2 * 1000.0, basicRes.estimatedTotalCost * 0.2)
        )
        return SteelWarehouseProResult(
            codeName = inputs.code.displayName,
            tributaryAreaM2 = inputs.span * inputs.length,
            serviceLoadKnM2 = inputs.deadLoad + inputs.liveLoad,
            frameReactionKn = basicRes.mainFrame.maxAxial,
            baseShearKn = basicRes.mainFrame.maxShear,
            maxMomentKnM = basicRes.mainFrame.maxMoment,
            maxAxialKn = basicRes.mainFrame.maxAxial,
            maxShearKn = basicRes.mainFrame.maxShear,
            driftMm = basicRes.mainFrame.maxDeflection,
            utilization = basicRes.mainFrame.utilizationMoment,
            compressionZone = "Top Flange in Midspan",
            tensionZone = "Bottom Flange in Midspan",
            notes = listOf("Design performed according to ${inputs.code.displayName}"),
            billOfMaterials = bom,
            totalCost = basicRes.estimatedTotalCost,
            durationWeeks = 8,
            safetyScore = if (basicRes.safetyStatus) 95.0 else 60.0
        )
    }

    fun designColumn(
        width: Double, depth: Double, pu: Double, mx: Double = 0.0, my: Double = 0.0,
        fcu: Double, fy: Double, code: DesignCode, isCircular: Boolean = false, connectedSlab: String = "SOLID",
        hasCap: Boolean = false, clearHeight: Double = 3000.0, preferredDiameter: Int = 16,
        autoOptimize: Boolean = true, manualNumBars: Int? = null, autoIncludeSelfWeight: Boolean = true, isSeismic: Boolean = false
    ): ColumnResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.nonNegative("pu", pu)
        InputGuard.nonNegative("mx", mx)
        InputGuard.nonNegative("my", my)
        InputGuard.positive("clearHeight", clearHeight)

        val domainCode = code
        val engine = CalculationFactory.getColumnDesign(domainCode)

        val res = engine.calculateReinforcement(
            fcu = fcu, fy = fy, width = width, depth = depth,
            axialLoad = pu, momentX = mx, momentY = my,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        val ag = if (isCircular) PI * width * width / 4.0 else width * depth
        val concreteVolume = ag * clearHeight / 1e9
        val steelWeight = res.astProvided * clearHeight * 7850.0 / 1e9
        val cost = concreteVolume * 120.0 + steelWeight * 1.5

        return ColumnResult(
            width = width, depth = depth, pu = pu, mx = mx, my = my,
            reinforcement = ReinforcementBar(
                numBars = res.numberOfBars, diameter = res.barDiameter.toInt(),
                spacing = (res.spacing ?: 200.0).toDouble(), weightKg = steelWeight
            ),
            stirrups = StirrupReinforcement(
                diameter = res.tiesDiameter.toInt(),
                spacing = (res.tiesSpacing ?: 200.0).toDouble()
            ),
            safetyChecks = listOf(
                DesignSafetyCheck("Axial Capacity", pu, (res.astProvided * fy / 1.15 + 0.67 * fcu / 1.5 * (width * depth - res.astProvided)) / 1000.0, "kN", res.isSafe),
                DesignSafetyCheck("Utilization", res.utilizationRatio * 100, 100.0, "%", res.isSafe)
            ),
            isSafe = res.isSafe,
            concreteVolume = width * depth * clearHeight / 1e9,
            steelWeight = res.astProvided * clearHeight * 7850.0 / 1e9,
            cost = (width * depth * clearHeight / 1e9) * 120.0 + res.astProvided * clearHeight * 7850.0 / 1e9 * 1.5,
            code = code,
            axialCapacity = res.astProvided * fy / 1.15 + 0.67 * fcu / 1.5 * (width * depth - res.astProvided) / 1000.0,
            appliedAxial = pu,
            // ── توسيع: حساب قدرة العزوم الفعلية وليس صفر ──
            // Mn_x ≈ 0.5 × As × fy × d (simplified uniaxial moment capacity)
            mxCapacity = if (mx > 0) res.astProvided * fy / 1.15 * (min(width, depth) - 60.0) / 1e6 else 0.0,
            myCapacity = if (my > 0) res.astProvided * fy / 1.15 * (min(width, depth) - 60.0) / 1e6 else 0.0,
            slenderness = clearHeight * 1000.0 / min(width, depth),
            isSlender = clearHeight * 1000.0 / min(width, depth) > 30.0,
            utilizationRatio = res.utilizationRatio,
            isDuctile = res.isSafe,
            // ── توسيع: طول منطقة الحصر حسب الكود ──
            // ACI 18.7.5.5 / SBC 304-21: max(6db, 450mm) للمناطق الزلزالية
            confinementLength = max(6.0 * res.barDiameter, 450.0),
            warnings = res.warnings,
            codeNotes = res.codeNotes,
            calculationSteps = columnSteps(
                code, pu, mx, my, fcu, fy, res, width, depth, clearHeight
            )
        )
    }

    private fun columnSteps(
        code: DesignCode, pu: Double, mx: Double, my: Double,
        fcu: Double, fy: Double,
        res: com.civileg.app.domain.entities.ReinforcementResult,
        width: Double, depth: Double, clearHeight: Double
    ): List<CalculationStep> {
        val ref = when (code) {
            DesignCode.ECP -> "ECP 203-2020 §4-2"
            DesignCode.ACI -> "ACI 318-19 §22.4"
            DesignCode.SBC -> "SBC 304-2018 §4"
        }
        val mResultant = sqrt(mx * mx + my * my)
        val eMm = if (pu > 0) mResultant / pu * 1000.0 else 0.0
        val lambda = clearHeight * 1000.0 / min(width, depth)
        val slender = lambda > 30.0
        val axialCapacity = (res.astProvided * fy / 1.15 +
            0.67 * fcu / 1.5 * (width * depth - res.astProvided)) / 1000.0
        return listOf(
            CalculationStep(
                stepNumber = 1, title = "Ultimate Loads", subtitle = "Factored actions",
                formula = "Pu, Mux, Muy",
                formulaWithValues = "Pu = ${"%.1f".format(pu)} kN, Mux = ${"%.1f".format(mx)} kN·m, Muy = ${"%.1f".format(my)} kN·m",
                result = "Mu = ${"%.1f".format(mResultant)} kN·m", unit = "kN·m",
                codeReference = ref, status = StepStatus.CALCULATION
            ),
            CalculationStep(
                stepNumber = 2, title = "Eccentricity", subtitle = "e = Mu / Pu",
                formula = "e = Mu / Pu",
                formulaWithValues = "e = ${"%.1f".format(mResultant)} / ${"%.1f".format(pu)} = ${"%.0f".format(eMm)} mm",
                result = "${"%.0f".format(eMm)} mm", unit = "mm",
                codeReference = ref, status = StepStatus.CALCULATION
            ),
            CalculationStep(
                stepNumber = 3, title = "Reinforcement", subtitle = "Required vs provided",
                formula = "As,req → As,prov",
                formulaWithValues = "As,req = ${"%.0f".format(res.astRequired)} mm², As,prov = ${"%.0f".format(res.astProvided)} mm²",
                result = "${res.numberOfBars}Ø${res.barDiameter.toInt()}", unit = "mm²",
                codeReference = ref, status = if (res.isSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL,
                isPass = res.isSafe
            ),
            CalculationStep(
                stepNumber = 4, title = "Axial Capacity", subtitle = "φPn",
                formula = "φPn = 0.35fcu·Ac + 0.87fy·As",
                formulaWithValues = "Ag = ${"%.0f".format(width * depth)} mm², As = ${"%.0f".format(res.astProvided)} mm²",
                result = "φPn = ${"%.0f".format(axialCapacity)} kN · Pu = ${"%.0f".format(pu)} kN", unit = "kN",
                codeReference = ref,
                status = if (axialCapacity >= pu) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL,
                isPass = axialCapacity >= pu
            ),
            CalculationStep(
                stepNumber = 5, title = "Slenderness", subtitle = "λ = l0 / i",
                formula = "λ = l0 / (h/√12)",
                formulaWithValues = "λ = ${"%.1f".format(lambda)} vs 30",
                result = if (slender) "Slender — second-order effects required" else "Short column",
                unit = "", codeReference = ref,
                status = if (slender) StepStatus.WARNING else StepStatus.CHECK_PASS,
                isPass = !slender
            )
        )
    }

    fun designBeam(
        width: Double, height: Double, span: Double, fcu: Double, fy: Double,
        deadLoad: Double, liveLoad: Double, preferredDiameter: Int, code: DesignCode,
        supportType: SupportType = SupportType.HINGED_HINGED, customMoment: Double? = null, customShear: Double? = null,
        autoIncludeSelfWeight: Boolean = true
    ): BeamResult {
        InputGuard.positive("width", width)
        InputGuard.positive("height", height)
        InputGuard.positive("span", span)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.nonNegative("deadLoad", deadLoad)
        InputGuard.nonNegative("liveLoad", liveLoad)

        val domainCode = code
        val beamEngine = CalculationFactory.getBeamDesign(domainCode)

        val selfWeight = if (autoIncludeSelfWeight) 25.0 * (width / 1000.0) * (height / 1000.0) else 0.0
        val wDl = deadLoad + selfWeight
        val wLl = liveLoad
        val wu = when (code) {
            DesignCode.ECP -> 1.4 * wDl + 1.6 * wLl
            DesignCode.ACI, DesignCode.SBC -> 1.2 * wDl + 1.6 * wLl
            else -> 1.4 * wDl + 1.6 * wLl
        }

        val lM = span
        val mu = customMoment ?: when (supportType) {
            SupportType.CANTILEVER -> wu * lM.pow(2) / 2.0
            SupportType.FIXED_FIXED -> wu * lM.pow(2) / 12.0
            SupportType.FIXED_HINGED -> wu * lM.pow(2) / 8.0
            else -> wu * lM.pow(2) / 8.0
        }

        val vu = customShear ?: when (supportType) {
            SupportType.CANTILEVER -> wu * lM
            else -> wu * lM / 2.0
        }

        val cover = when (code) {
            DesignCode.ECP -> 40.0
            DesignCode.ACI -> 40.0
            DesignCode.SBC -> 50.0
            else -> 40.0
        }
        val effectiveDepth = max(50.0, height - cover)

        val flexRes = beamEngine.calculateFlexureReinforcement(
            fcu = fcu, fy = fy, width = width, effectiveDepth = effectiveDepth,
            totalDepth = height, designMoment = mu, loadCombination = LoadCombination.DEAD_LIVE
        )

        val shearRes = beamEngine.calculateShearReinforcement(
            fcu = fcu, fy = fy, width = width, effectiveDepth = effectiveDepth,
            designShear = vu, axialLoad = 0.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        val rho = flexRes.astProvided / (width * effectiveDepth)
        val defCheck = beamEngine.checkDeflection(
            span = span, totalDepth = height, reinforcementRatio = rho,
            supportCondition = supportType.toDomain()
        )

        val concreteVolume = (width / 1000.0) * (height / 1000.0) * span
        val mainBarWeight = flexRes.astProvided * span * 7850.0 / 1e6
        val topBarArea = max(0.1 * flexRes.astProvided, 2.0 * PI * 12.0 * 12.0 / 4.0)
        val topBarWeight = topBarArea * span * 7850.0 / 1e6
        
        val stirrupPerimeter = 2.0 * ((width - 2 * cover) + (height - 2 * cover)) / 1000.0
        val stirrupSpacingM = (shearRes.stirrupSpacing.takeIf { it > 0 } ?: 200.0) / 1000.0
        val numStirrups = max(5, (span / stirrupSpacingM).roundToInt() + 1)
        val stirrupDia = shearRes.stirrupDiameter.takeIf { it > 0 } ?: 8.0
        val singleStirrupWeight = stirrupPerimeter * (PI / 4.0 * stirrupDia * stirrupDia) * 7850.0 / 1e9
        val stirrupWeight = numStirrups * singleStirrupWeight

        val totalSteelWeight = mainBarWeight + topBarWeight + stirrupWeight
        val cost = concreteVolume * 120.0 + totalSteelWeight * 1.5

        val topNumBars = max(2, ceil(topBarArea / (PI / 4.0 * 12.0 * 12.0)).toInt())

        val safetyChecks = listOf(
            DesignSafetyCheck("Flexure Capacity", flexRes.astProvided, flexRes.astRequired, "mm²", flexRes.isSafe),
            DesignSafetyCheck("Shear Capacity", shearRes.providedShearReinforcement, shearRes.requiredShearReinforcement, "kN", shearRes.isSafe),
            DesignSafetyCheck("Deflection Check", defCheck.calculatedDeflection, defCheck.allowableDeflection, "mm", defCheck.isSafe)
        )

        val warnings = (flexRes.warnings + shearRes.warnings + defCheck.warnings +
            listOfNotNull(defCheck.message.takeIf { it.isNotBlank() })).distinct()
        val codeNotes = (flexRes.codeNotes + shearRes.codeNotes).distinct()
        val ref = when (code) {
            DesignCode.ECP -> "ECP 203-2020 §4"
            DesignCode.ACI -> "ACI 318-19 §9"
            DesignCode.SBC -> "SBC 304-2018 §9"
        }
        val steps = listOf(
            CalculationStep(
                stepNumber = 1, title = "Ultimate Load",
                formula = "wu = γD·(DL+self) + γL·LL",
                formulaWithValues = "wu = ${"%.2f".format(wu)} kN/m",
                result = "wu = ${"%.2f".format(wu)} kN/m", unit = "kN/m",
                codeReference = ref
            ),
            CalculationStep(
                stepNumber = 2, title = "Design Moment",
                formula = "Mu = wu·L²/8 (support-dependent)",
                formulaWithValues = "Mu = ${"%.2f".format(wu)} × ${"%.2f".format(span)}² / factor = ${"%.1f".format(mu)} kN·m",
                result = "Mu = ${"%.1f".format(mu)} kN·m", unit = "kN·m",
                codeReference = ref
            ),
            CalculationStep(
                stepNumber = 3, title = "Design Shear",
                formula = "Vu = wu·L/2",
                formulaWithValues = "Vu = ${"%.2f".format(wu)} × ${"%.2f".format(span)} / 2 = ${"%.1f".format(vu)} kN",
                result = "Vu = ${"%.1f".format(vu)} kN", unit = "kN",
                codeReference = ref
            ),
            CalculationStep(
                stepNumber = 4, title = "Flexural Steel",
                formula = "As = Mu / (0.87fy·j·d)",
                formulaWithValues = "As,req = ${"%.0f".format(flexRes.astRequired)} mm² → As,prov = ${"%.0f".format(flexRes.astProvided)} mm²",
                result = "${flexRes.numberOfBars}Ø${flexRes.barDiameter.toInt()}", unit = "mm²",
                codeReference = ref,
                isPass = flexRes.isSafe,
                status = if (flexRes.isSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
            ),
            CalculationStep(
                stepNumber = 5, title = "Shear Reinforcement",
                formula = "s = Asv·fy·d / Vs",
                formulaWithValues = "Ø${shearRes.stirrupDiameter.toInt()} @ ${"%.0f".format(shearRes.stirrupSpacing)} mm",
                result = "Ø${shearRes.stirrupDiameter.toInt()}@${"%.0f".format(shearRes.stirrupSpacing)}", unit = "mm",
                codeReference = ref,
                isPass = shearRes.isSafe,
                status = if (shearRes.isSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
            ),
            CalculationStep(
                stepNumber = 6, title = "Deflection",
                formula = "Δ ≤ L/250",
                formulaWithValues = "Δ = ${"%.2f".format(defCheck.calculatedDeflection)} mm vs ${"%.2f".format(defCheck.allowableDeflection)} mm",
                result = if (defCheck.isSafe) "OK" else "Exceeds limit", unit = "mm",
                codeReference = ref,
                isPass = defCheck.isSafe,
                status = if (defCheck.isSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
            )
        )

        return BeamResult(
            width = width, depth = height, mu = mu, vu = vu,
            reinforcementBottom = ReinforcementBar(
                numBars = flexRes.numberOfBars, diameter = flexRes.barDiameter.toInt(),
                weightKg = mainBarWeight
            ),
            reinforcementTop = ReinforcementBar(
                numBars = topNumBars, diameter = 12, weightKg = topBarWeight
            ),
            stirrups = StirrupReinforcement(
                diameter = stirrupDia.toInt(), spacing = shearRes.stirrupSpacing,
                numLegs = shearRes.numLegs, weightKg = stirrupWeight
            ),
            safetyChecks = safetyChecks,
            isSafe = flexRes.isSafe && shearRes.isSafe && defCheck.isSafe,
            concreteVolume = concreteVolume,
            steelWeight = totalSteelWeight,
            cost = cost,
            code = code,
            appliedMoment = mu,
            appliedShear = vu,
            supportType = supportType,
            span = span,
            momentCapacity = flexRes.astProvided * fy * (effectiveDepth - 0.4 * 0.2 * effectiveDepth) / 1e6,
            shearCapacity = shearRes.concreteShearCapacity,
            steelRatio = rho,
            deflection = defCheck.calculatedDeflection,
            allowableDeflection = defCheck.allowableDeflection,
            utilizationRatio = maxOf(flexRes.utilizationRatio, shearRes.utilizationRatio, defCheck.ratio),
            neutralAxisDepth = flexRes.neutralAxisDepth,
            warnings = warnings,
            codeNotes = codeNotes,
            calculationSteps = steps
        )
    }

    fun designSlab(lx: Double, ly: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, ts: Double, preferredDiameter: Int, code: DesignCode, type: SlabType = SlabType.SOLID, prestressForce: Double = 0.0, dropPanelThickness: Double = 0.0, columnSize: Double = 400.0, openingWidth: Double = 0.0, openingLength: Double = 0.0, ribWidth: Double = 100.0, ribSpacing: Double = 500.0): SlabResult {
        InputGuard.positive("lx", lx)
        InputGuard.positive("ly", ly)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ts", ts)

        return try {
            val domainCode = code
            
            if (type == SlabType.HOLLOW_BLOCK) {
                val hordiDesign = CalculationFactory.getHordiSlabDesign(domainCode)
                val wu = (1.4 * deadLoad + 1.6 * liveLoad)
                val s = ribSpacing / 1000.0
                val wuRib = wu * s
                val span = max(lx, ly)
                val muRib = wuRib * span.pow(2) / 8.0
                val vuRib = wuRib * span / 2.0
                
                val res = hordiDesign.designHordiSlab(
                    fcu = fcu, fy = fy, ribWidth = ribWidth, ribSpacing = ribSpacing,
                    totalThickness = ts, toppingThickness = 50.0, span = span * 1000.0,
                    designMoment = muRib, designShear = vuRib, loadCombination = LoadCombination.DEAD_LIVE
                )
                
                val concreteVol = lx * ly * ts / 1000.0
                val steelW = res.providedReinforcement * lx * ly * 7850.0 / 1e6
                val costVal = concreteVol * 120.0 + steelW * 1.5

                return SlabResult(
                    type = type, thickness = ts, isSafe = res.isSafe, code = code,
                    reinforcementMain = ReinforcementBar(spacing = res.barSpacing, diameter = res.barDiameter.toInt()),
                    momentX = muRib, utilizationRatio = res.utilizationRatio,
                    concreteVolume = concreteVol, steelWeight = steelW, cost = costVal,
                    safetyChecks = listOf(DesignSafetyCheck("Flexure", res.requiredReinforcement, res.providedReinforcement, "mm²", res.isSafe))
                )
            }

            if (type == SlabType.WAFFLE) {
                val waffleDesign = CalculationFactory.getWaffleSlabDesign(domainCode)

                val waffleInput = WaffleSlabDesign.WaffleSlabInput(
                    lx = lx * 1000.0,
                    ly = ly * 1000.0,
                    ribSpacing = ribSpacing,
                    ribWidth = ribWidth,
                    ribHeight = ts - 50.0,
                    toppingThickness = 50.0,
                    solidHeadSize = columnSize.coerceAtLeast(400.0),
                    columnWidth = columnSize,
                    columnDepth = columnSize,
                    fcu = fcu,
                    fy = fy,
                    liveLoad = liveLoad,
                    deadLoad = deadLoad,
                    designCode = domainCode
                )

                val waffleResult = waffleDesign.design(waffleInput)

                val concreteVol = waffleResult.concreteVolume.let { if (it > 0.0) it else lx * ly * ts / 1000.0 }
                val steelW = waffleResult.steelWeight.let { if (it > 0.0) it else {
                    val ribArea = waffleResult.ribDesign?.flexureReinforcement?.providedArea ?: 0.0
                    val headArea = waffleResult.solidHeadDesign?.flexureReinforcement?.providedArea ?: 0.0
                    (ribArea + headArea) * lx * ly * 7850.0 / 1e6
                }}
                val costVal = waffleResult.cost.let { if (it > 0.0) it else concreteVol * 120.0 + steelW * 1.5 }

                val ribDesign = waffleResult.ribDesign
                val barSpacing = ribDesign?.flexureReinforcement?.spacing?.toDouble() ?: 200.0
                val barDiameter = ribDesign?.flexureReinforcement?.diameter ?: 16

                return SlabResult(
                    type = type,
                    thickness = ts,
                    isSafe = waffleResult.isSafe,
                    code = code,
                    reinforcementMain = ReinforcementBar(
                        spacing = barSpacing,
                        diameter = barDiameter,
                        weightKg = steelW
                    ),
                    momentX = 0.0,
                    utilizationRatio = waffleResult.utilizationRatio,
                    concreteVolume = concreteVol,
                    steelWeight = steelW,
                    cost = costVal,
                    safetyChecks = waffleResult.safetyChecks.map {
                        DesignSafetyCheck(it.name, it.calculated, it.limit, it.unit, it.passed)
                    }
                )
            }

            // ── Solid slab → delegate to the code-specific SlabDesign engine (P0-2) ──
            // The previous inline path applied ECP's 1.4D+1.6L load factors and an ECP-style
            // 0.87·fy lever arm to every code, so ACI and SBC silently returned ECP numbers.
            // The engine now owns the code-dependent design; this block only maps its result
            // onto SlabResult. lx/ly are metres here, the engines work in mm.
            val slabEngine = CalculationFactory.getSlabDesign(domainCode)
            val shortM = min(lx, ly)
            val longM = max(lx, ly)
            val shortSpan = shortM * 1000.0
            val longSpan = longM * 1000.0
            val wu = when (domainCode) {                       // kN/m² — D+L factored per code
                DesignCode.ECP -> 1.4 * deadLoad + 1.6 * liveLoad                        // ECP 203 §2-3-1
                DesignCode.ACI, DesignCode.SBC -> 1.2 * deadLoad + 1.6 * liveLoad        // ACI 318 §5.3 / SBC 304
            }
            val isTwoWay = longM <= 2.0 * shortM

            val mainRes: SlabDesignResult
            val secondaryRes: SlabDesignResult?
            val muX: Double
            val muY: Double
            if (isTwoWay) {
                val supports = SlabSupportConditions(
                    EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
                    EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED
                )
                val tw = slabEngine.designTwoWaySlab(
                    fcu, fy, ts, shortSpan, longSpan, supports, wu, LoadCombination.DEAD_LIVE
                )
                mainRes = tw.shortDirection
                secondaryRes = tw.longDirection
                // Displayed moments are the ones each engine actually designed for. ECP scales
                // the positive (mid-span) coefficient with the span of that direction; ACI/SBC
                // design for the larger of mid-span/support and pre-scale their long-direction
                // coefficient by 1/ratio², i.e. they use lx² for both directions.
                val c = tw.momentCoefficients
                muX = when (domainCode) {
                    DesignCode.ECP -> c.positiveShort * wu * shortM * shortM
                    DesignCode.ACI, DesignCode.SBC -> max(c.positiveShort, c.negativeShort) * wu * shortM * shortM
                }
                muY = when (domainCode) {
                    DesignCode.ECP -> c.positiveLong * wu * longM * longM
                    DesignCode.ACI, DesignCode.SBC -> max(c.positiveLong, c.negativeLong) * wu * shortM * shortM
                }
            } else {
                val mu = wu * shortM * shortM / 8.0
                mainRes = slabEngine.designOneWaySlab(
                    fcu, fy, ts, shortSpan, mu, wu * shortM / 2.0, LoadCombination.DEAD_LIVE
                )
                secondaryRes = null
                muX = mu
                muY = 0.0
            }

            // Bar placement keeps the user's preferred diameter (the solid-slab bar selector
            // must keep working) and derives spacing from the engine's code-required steel,
            // clamped to the engine's own per-code limits: min 50 mm (ECPSlab) / 100 mm
            // (ACISlab·SBCSlab), max min(3h, getMaxBarSpacing()).
            InputGuard.positive("preferredDiameter", preferredDiameter)
            val minSpacing = if (domainCode == DesignCode.ECP) 50.0 else 100.0
            val maxSpacing = minOf(3.0 * ts, slabEngine.getMaxBarSpacing())
            val barArea = PI / 4.0 * preferredDiameter * preferredDiameter
            fun placeBar(required: Double): Pair<Double, Double> {
                val spacing = (barArea * 1000.0 / max(required, 1.0)).coerceIn(minSpacing, maxSpacing)
                return spacing to barArea * 1000.0 / spacing
            }

            val mainRequired = mainRes.requiredReinforcement
            val (mainSpacing, mainProvided) = placeBar(mainRequired)

            val secondaryRequired = if (secondaryRes != null) secondaryRes.requiredReinforcement
                else slabEngine.getMinReinforcementRatio() * 1000.0 * ts  // one-way temp/shrinkage steel
            val (secondarySpacing, secondaryProvided) = placeBar(secondaryRequired)
            val secondarySafe = (secondaryRes == null || secondaryRes.isSafe) &&
                secondaryProvided >= secondaryRequired - 1e-6
            val secondaryBar = ReinforcementBar(spacing = secondarySpacing, diameter = preferredDiameter)

            val concreteVol = lx * ly * ts / 1000.0
            val steelW = (mainProvided + secondaryProvided) * lx * ly * 7850.0 / 1e6
            val costVal = concreteVol * 120.0 + steelW * 1.5
            val vu = wu * shortM / 2.0
            val flexureSafe = mainProvided >= mainRequired - 1e-6
            val shearSafe = vu <= mainRes.shearCapacity
            val thicknessSafe = ts >= mainRes.minThickness

            val ref = codeNoteRef(domainCode)
            val steps = listOf(
                CalculationStep(
                    stepNumber = 1, title = "Factored Load",
                    formula = if (domainCode == DesignCode.ECP) "wu = 1.4DL + 1.6LL" else "wu = 1.2DL + 1.6LL",
                    formulaWithValues = "wu = ${"%.2f".format(wu)} kN/m²",
                    result = "wu = ${"%.2f".format(wu)} kN/m²", unit = "kN/m²",
                    codeReference = ref
                ),
                CalculationStep(
                    stepNumber = 2, title = "Design Moment",
                    formula = if (isTwoWay) "M = α·wu·L² (coefficient method)" else "M = wu·L²/8",
                    formulaWithValues = "Mx = ${"%.2f".format(muX)} kN·m/m, My = ${"%.2f".format(muY)} kN·m/m",
                    result = "Mx = ${"%.2f".format(muX)} kN·m/m", unit = "kN·m/m",
                    codeReference = ref
                ),
                CalculationStep(
                    stepNumber = 3, title = "Minimum Thickness",
                    formula = "ts ≥ hmin (code deflection limit)",
                    formulaWithValues = "ts = ${"%.0f".format(ts)} mm vs hmin = ${"%.0f".format(mainRes.minThickness)} mm",
                    result = if (thicknessSafe) "OK" else "Thickness insufficient", unit = "mm",
                    codeReference = ref, isPass = thicknessSafe,
                    status = if (thicknessSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
                ),
                CalculationStep(
                    stepNumber = 4, title = "Main Flexural Steel",
                    formula = "As = M / (0.87fy·j·d)",
                    formulaWithValues = "As,req = ${"%.0f".format(mainRequired)} mm²/m → ${preferredDiameter}@${"%.0f".format(mainSpacing)} mm",
                    result = "As,prov = ${"%.0f".format(mainProvided)} mm²/m", unit = "mm²/m",
                    codeReference = ref, isPass = flexureSafe,
                    status = if (flexureSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
                ),
                CalculationStep(
                    stepNumber = 5, title = "Secondary Steel",
                    formula = "As,sec = ρmin·b·h (or long-direction design)",
                    formulaWithValues = "As,req = ${"%.0f".format(secondaryRequired)} mm²/m → ${preferredDiameter}@${"%.0f".format(secondarySpacing)} mm",
                    result = "As,prov = ${"%.0f".format(secondaryProvided)} mm²/m", unit = "mm²/m",
                    codeReference = ref, isPass = secondarySafe,
                    status = if (secondarySafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
                ),
                CalculationStep(
                    stepNumber = 6, title = "Shear Check",
                    formula = "vu = wu·L/2 ≤ vc",
                    formulaWithValues = "vu = ${"%.2f".format(vu)} kN/m vs vc = ${"%.2f".format(mainRes.shearCapacity)} kN/m",
                    result = if (shearSafe) "OK" else "Shear reinforcement required", unit = "kN/m",
                    codeReference = ref, isPass = shearSafe,
                    status = if (shearSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
                )
            )

            return SlabResult(
                type = type,
                thickness = ts,
                // Strength (engine) + the code's own minimum-thickness/deflection limit, so a
                // too-thin slab can never be reported as SAFE with all three codes.
                isSafe = mainRes.isSafe && secondarySafe && flexureSafe && shearSafe && thicknessSafe,
                code = code,
                reinforcementMain = ReinforcementBar(spacing = mainSpacing, diameter = preferredDiameter, weightKg = steelW),
                reinforcementSecondary = secondaryBar,
                momentX = muX,
                momentY = muY,
                totalLoad = wu,
                utilizationRatio = (mainRequired / max(mainProvided, 1.0)).coerceIn(0.1, 1.5),
                concreteVolume = concreteVol,
                steelWeight = steelW,
                cost = costVal,
                minThickness = mainRes.minThickness,
                safetyChecks = listOf(
                    DesignSafetyCheck("Flexure (main)", mainProvided, mainRequired, "mm²/m", flexureSafe),
                    DesignSafetyCheck("Flexure (secondary)", secondaryProvided, secondaryRequired, "mm²/m", secondarySafe),
                    DesignSafetyCheck("Shear", vu, mainRes.shearCapacity, "kN/m", shearSafe),
                    DesignSafetyCheck("Min Thickness", ts, mainRes.minThickness, "mm", thicknessSafe)
                ),
                suggestions = mainRes.warnings + mainRes.codeNotes,
                calculationSteps = steps
            )
        } catch (e: Exception) {
            // ADR-010: لا تُرجع isSafe=true عند الفشل — loud failure, no silent safe
            SlabResult(thickness = ts, isSafe = false, code = code, type = type,
                suggestions = listOf("Calculation failed: ${e.message?.take(200) ?: "Unknown error"} / فشل الحساب"))
        }
    }

    fun calculateFooting(
        type: FootingType, p: Double, fcu: Double, fy: Double, soil: Double, colB: Double, colT: Double,
        code: DesignCode, preferredDiameter: Int = 16, preferredSpacing: Double = 150.0, p2: Double = 0.0, distance: Double = 0.0, 
        maxLeft: Double? = null, maxRight: Double? = null, maxTop: Double? = null, maxBottom: Double? = null,
        numPiles: Int = 4, pileDia: Double = 500.0, pileCapacity: Double = 500.0
    ): FootingResult {
        InputGuard.positive("p", p)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("soil", soil)
        InputGuard.positive("colB", colB)
        InputGuard.positive("colT", colT)

        val domainCode = code
        val footingEngine = CalculationFactory.getFootingDesign(domainCode)

        val initialDepth = 600.0

        if (type == FootingType.COMBINED && p2 > 0.0 && distance > 0.0) {
            val combinedRes = footingEngine.designCombinedFooting(
                fcu = fcu, fy = fy,
                axialLoad1 = p, axialLoad2 = p2,
                distanceBetweenColumns = distance,
                soilBearingCapacity = soil,
                footingDepth = initialDepth,
                loadCombination = LoadCombination.DEAD_LIVE,
                columnWidth = colB, columnDepth = colT,
                col2Width = colB, col2Depth = colT
            )
            val widthM = combinedRes.requiredWidth / 1000.0
            val lengthM = combinedRes.requiredLength / 1000.0
            val thickM = combinedRes.requiredThickness / 1000.0
            val concreteVolume = widthM * lengthM * thickM
            val barDia = combinedRes.reinforcement.barDiameter.toInt().takeIf { it > 0 } ?: preferredDiameter
            val spacing = combinedRes.reinforcement.spacing.takeIf { it > 0 } ?: preferredSpacing
            val numBarsX = max(5, ceil((combinedRes.requiredWidth - 100.0) / spacing).toInt())
            val numBarsY = max(5, ceil((combinedRes.requiredLength - 100.0) / spacing).toInt())
            val barAreaSingle = PI / 4.0 * barDia * barDia
            val totalLengthX = numBarsX * lengthM
            val totalLengthY = numBarsY * widthM
            val steelWeight = (totalLengthX + totalLengthY) * barAreaSingle * 7850.0 / 1e6
            val cost = concreteVolume * 120.0 + steelWeight * 1.5
            val safetyChecks = listOf(
                DesignSafetyCheck("Soil Bearing", combinedRes.soilPressure, soil, "kPa", combinedRes.soilPressure <= soil),
                DesignSafetyCheck("Punching Shear", combinedRes.punchingShearCheck.appliedShear, combinedRes.punchingShearCheck.shearCapacity, "kN", combinedRes.punchingShearCheck.isSafe),
                DesignSafetyCheck("Flexure Steel", combinedRes.reinforcement.astProvided, combinedRes.reinforcement.astRequired, "mm²", combinedRes.reinforcement.isSafe)
            )
            return FootingResult(
                type = type, width = combinedRes.requiredWidth, length = combinedRes.requiredLength, thickness = combinedRes.requiredThickness,
                soilPressure = combinedRes.soilPressure, allowablePressure = soil,
                reinforcementBottom = ReinforcementBar(
                    numBars = numBarsX, diameter = barDia, spacing = spacing, weightKg = steelWeight
                ),
                isSafe = combinedRes.isSafe, code = code,
                concreteVolume = concreteVolume, steelWeight = steelWeight, cost = cost,
                barsX = numBarsX, barsY = numBarsY, barDiameter = barDia,
                utilizationRatio = (combinedRes.soilPressure / soil).coerceIn(0.1, 1.5),
                safetyChecks = safetyChecks,
                column1Size = Pair(colB, colT),
                column2Size = Pair(colB, colT),
                isCombined = true,
                distanceBetweenColumns = distance
            )
        }

        val res = footingEngine.designIsolatedFooting(
            fcu = fcu, fy = fy, columnWidth = colB, columnDepth = colT,
            axialLoad = p, momentX = 0.0, momentY = 0.0,
            soilBearingCapacity = soil, footingDepth = initialDepth,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        val widthM = res.requiredWidth / 1000.0
        val lengthM = res.requiredLength / 1000.0
        val thickM = res.requiredThickness / 1000.0

        val concreteVolume = widthM * lengthM * thickM

        val barDia = res.reinforcement.barDiameter.toInt().takeIf { it > 0 } ?: preferredDiameter
        val spacing = res.reinforcement.spacing.takeIf { it > 0 } ?: preferredSpacing

        val numBarsX = max(5, ceil((res.requiredWidth - 100.0) / spacing).toInt())
        val numBarsY = max(5, ceil((res.requiredLength - 100.0) / spacing).toInt())

        val barAreaSingle = PI / 4.0 * barDia * barDia
        val totalLengthX = numBarsX * lengthM
        val totalLengthY = numBarsY * widthM
        val steelWeight = (totalLengthX + totalLengthY) * barAreaSingle * 7850.0 / 1e6
        val cost = concreteVolume * 120.0 + steelWeight * 1.5

        val safetyChecks = listOf(
            DesignSafetyCheck("Soil Bearing", res.soilPressure, soil, "kPa", res.soilPressure <= soil),
            DesignSafetyCheck("Punching Shear", res.punchingShearCheck.appliedShear, res.punchingShearCheck.shearCapacity, "kN", res.punchingShearCheck.isSafe),
            DesignSafetyCheck("Flexure Steel", res.reinforcement.astProvided, res.reinforcement.astRequired, "mm²", res.reinforcement.isSafe)
        )

        val ref = codeNoteRef(domainCode)
        val steps = listOf(
            CalculationStep(
                stepNumber = 1, title = "Required Area",
                formula = "A = P / q_allow",
                formulaWithValues = "A = ${"%.0f".format(p)} / ${"%.0f".format(soil)} = ${"%.2f".format(p / soil)} m²",
                result = "A = ${"%.2f".format(p / soil)} m²", unit = "m²",
                codeReference = ref
            ),
            CalculationStep(
                stepNumber = 2, title = "Footing Dimensions",
                formula = "B × L ≥ A (proportioned to column)",
                formulaWithValues = "B × L = ${"%.2f".format(res.requiredWidth / 1000.0)} × ${"%.2f".format(res.requiredLength / 1000.0)} m",
                result = "${"%.0f".format(res.requiredWidth)} × ${"%.0f".format(res.requiredLength)} mm", unit = "mm",
                codeReference = ref
            ),
            CalculationStep(
                stepNumber = 3, title = "Soil Bearing Check",
                formula = "q = P/A ≤ q_allow",
                formulaWithValues = "q = ${"%.1f".format(res.soilPressure)} kPa vs ${"%.1f".format(soil)} kPa",
                result = if (res.soilPressure <= soil) "OK" else "Exceeds allowable", unit = "kPa",
                codeReference = ref, isPass = res.soilPressure <= soil,
                status = if (res.soilPressure <= soil) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
            ),
            CalculationStep(
                stepNumber = 4, title = "Punching Shear",
                formula = "Vp ≤ Vc (u1 at d/2)",
                formulaWithValues = "Vp = ${"%.1f".format(res.punchingShearCheck.appliedShear)} kN vs Vc = ${"%.1f".format(res.punchingShearCheck.shearCapacity)} kN",
                result = if (res.punchingShearCheck.isSafe) "OK" else "Increase thickness", unit = "kN",
                codeReference = ref, isPass = res.punchingShearCheck.isSafe,
                status = if (res.punchingShearCheck.isSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
            ),
            CalculationStep(
                stepNumber = 5, title = "Flexural Steel",
                formula = "As = M / (0.87fy·j·d)",
                formulaWithValues = "As,req = ${"%.0f".format(res.reinforcement.astRequired)} mm² → As,prov = ${"%.0f".format(res.reinforcement.astProvided)} mm²",
                result = "${numBarsX}Ø${barDia} @ ${"%.0f".format(spacing)} mm", unit = "mm²",
                codeReference = ref, isPass = res.reinforcement.isSafe,
                status = if (res.reinforcement.isSafe) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
            )
        )

        return FootingResult(
            type = type, width = res.requiredWidth, length = res.requiredLength, thickness = res.requiredThickness,
            soilPressure = res.soilPressure, allowablePressure = soil,
            reinforcementBottom = ReinforcementBar(
                numBars = numBarsX, diameter = barDia, spacing = spacing, weightKg = steelWeight
            ),
            isSafe = res.isSafe, code = code,
            concreteVolume = concreteVolume, steelWeight = steelWeight, cost = cost,
            barsX = numBarsX, barsY = numBarsY, barDiameter = barDia,
            utilizationRatio = (res.soilPressure / soil).coerceIn(0.1, 1.5),
            safetyChecks = safetyChecks,
            column1Size = Pair(colB, colT),
            calculationSteps = steps
        )
    }

    fun calculateStrapFooting(
        col1Load: Double, col2Load: Double, distance: Double,
        col1W: Double, col1D: Double, col2W: Double, col2D: Double,
        soil: Double, fcu: Double, fy: Double,
        code: DesignCode, preferredDiameter: Int, strapWidth: Double = 400.0
    ): StrapFootingResult {
        InputGuard.positive("col1Load", col1Load)
        InputGuard.positive("col2Load", col2Load)
        InputGuard.positive("distance", distance)
        InputGuard.positive("col1W", col1W)
        InputGuard.positive("col1D", col1D)
        InputGuard.positive("col2W", col2W)
        InputGuard.positive("col2D", col2D)
        InputGuard.positive("soil", soil)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("strapWidth", strapWidth)
        // `preferredDiameter` is deliberately not guarded: this function never reads it.

        val design = CalculationFactory.getStrapFootingDesign(code)
        val inputs = StrapFootingDesignEngine.Inputs(
            column1Load = col1Load, column2Load = col2Load,
            distanceBetweenColumns = distance,
            column1Width = col1W, column1Depth = col1D,
            column2Width = col2W, column2Depth = col2D,
            soilBearingCapacity = soil, fcu = fcu, fy = fy,
            strapBeamWidth = strapWidth
        )
        // Route through the selected code engine so code notes/load factors apply (§P1-1).
        val res = design.design(inputs)

        val vol1 = (res.footing1.width * res.footing1.length * res.footing1.thickness) / 1e9
        val vol2 = (res.footing2.width * res.footing2.length * res.footing2.thickness) / 1e9
        val strapLenM = distance / 1000.0 + 0.8 // main bars incl. anchorage into both footings
        val volStrap = (res.strapBeam.width * res.strapBeam.depth * (distance / 1000.0)) / 1e9
        val totalConcreteVol = vol1 + vol2 + volStrap

        val steel1 = res.footing1.reinforcement.numberOfBars *
            barAreaMm2(res.footing1.reinforcement.barDiameter) * (res.footing1.length / 1000.0) * 7850.0 / 1e6
        val steel2 = res.footing2.reinforcement.numberOfBars *
            barAreaMm2(res.footing2.reinforcement.barDiameter) * (res.footing2.length / 1000.0) * 7850.0 / 1e6
        val steelStrap = (res.strapBeam.topReinforcement.numberOfBars *
            barAreaMm2(res.strapBeam.topReinforcement.barDiameter) +
            res.strapBeam.bottomReinforcement.numberOfBars *
            barAreaMm2(res.strapBeam.bottomReinforcement.barDiameter)) * strapLenM * 7850.0 / 1e6
        val totalSteelW = steel1 + steel2 + steelStrap

        val q1 = res.soilPressure1
        val q2 = res.soilPressure2
        val safetyChecks = listOf(
            DesignSafetyCheck("Soil Bearing - Footing 1", q1, soil, "kPa", q1 <= soil * 1.001),
            DesignSafetyCheck("Soil Bearing - Footing 2", q2, soil, "kPa", q2 <= soil * 1.001),
            DesignSafetyCheck("Punching Shear - Footing 1", res.punchingShear1, res.punchingCapacity1, "kN", res.punchingShear1 <= res.punchingCapacity1),
            DesignSafetyCheck("Punching Shear - Footing 2", res.punchingShear2, res.punchingCapacity2, "kN", res.punchingShear2 <= res.punchingCapacity2),
            DesignSafetyCheck("Strap Beam Flexure (As)", res.strapAsRequired, res.strapAsProvided, "mm²", res.strapAsRequired <= res.strapAsProvided),
            DesignSafetyCheck("Strap Beam Shear", res.strapShear, res.strapShearCapacity, "kN", res.strapShear <= res.strapShearCapacity)
        )
        val isSafe = safetyChecks.all { it.isSafe }
        val utilization = maxOf(
            if (soil > 0) maxOf(q1, q2) / soil else 0.0,
            if (res.punchingCapacity1 > 0) res.punchingShear1 / res.punchingCapacity1 else 0.0,
            if (res.punchingCapacity2 > 0) res.punchingShear2 / res.punchingCapacity2 else 0.0,
            if (res.strapAsProvided > 0) res.strapAsRequired / res.strapAsProvided else 0.0,
            if (res.strapShearCapacity > 0) res.strapShear / res.strapShearCapacity else 0.0
        ).coerceIn(0.0, 1.5)

        val footing1Safe = q1 <= soil * 1.001 && res.punchingShear1 <= res.punchingCapacity1
        val footing2Safe = q2 <= soil * 1.001 && res.punchingShear2 <= res.punchingCapacity2

        return StrapFootingResult(
            footing1 = FootingResult(
                type = FootingType.ISOLATED, width = res.footing1.width, length = res.footing1.length, thickness = res.footing1.thickness,
                reinforcementBottom = ReinforcementBar(
                    numBars = res.footing1.reinforcement.numberOfBars,
                    diameter = res.footing1.reinforcement.barDiameter.toInt(),
                    spacing = res.footing1.reinforcement.spacing,
                    weightKg = steel1
                ),
                isSafe = footing1Safe, code = code, allowablePressure = soil,
                soilPressure = q1,
                concreteVolume = vol1, steelWeight = steel1, cost = vol1 * 120.0 + steel1 * 1.5,
                barsX = res.footing1.reinforcement.numberOfBars,
                barsY = res.footing1.reinforcement.numberOfBars,
                barDiameter = res.footing1.reinforcement.barDiameter.toInt(),
                utilizationRatio = if (soil > 0) (q1 / soil).coerceIn(0.0, 1.5) else 0.0,
                safetyChecks = safetyChecks
            ),
            footing2 = FootingResult(
                type = FootingType.ISOLATED, width = res.footing2.width, length = res.footing2.length, thickness = res.footing2.thickness,
                reinforcementBottom = ReinforcementBar(
                    numBars = res.footing2.reinforcement.numberOfBars,
                    diameter = res.footing2.reinforcement.barDiameter.toInt(),
                    spacing = res.footing2.reinforcement.spacing,
                    weightKg = steel2
                ),
                isSafe = footing2Safe, code = code, allowablePressure = soil,
                soilPressure = q2,
                concreteVolume = vol2, steelWeight = steel2, cost = vol2 * 120.0 + steel2 * 1.5,
                barsX = res.footing2.reinforcement.numberOfBars,
                barsY = res.footing2.reinforcement.numberOfBars,
                barDiameter = res.footing2.reinforcement.barDiameter.toInt(),
                utilizationRatio = if (soil > 0) (q2 / soil).coerceIn(0.0, 1.5) else 0.0,
                safetyChecks = safetyChecks
            ),
            strapBeamWidth = res.strapBeam.width,
            strapBeamDepth = res.strapBeam.depth,
            strapTopReinforcement = ReinforcementBar(
                numBars = res.strapBeam.topReinforcement.numberOfBars,
                diameter = res.strapBeam.topReinforcement.barDiameter.toInt(),
                spacing = res.strapBeam.topReinforcement.spacing
            ),
            strapBottomReinforcement = ReinforcementBar(
                numBars = res.strapBeam.bottomReinforcement.numberOfBars,
                diameter = res.strapBeam.bottomReinforcement.barDiameter.toInt(),
                spacing = res.strapBeam.bottomReinforcement.spacing
            ),
            reactions = res.reactions,
            isSafe = isSafe,
            concreteVolume = totalConcreteVol,
            steelWeight = totalSteelW,
            utilizationRatio = utilization,
            safetyChecks = safetyChecks,
            calculationSteps = res.calculationSteps.mapIndexed { i, line ->
                CalculationStep(
                    stepNumber = i + 1, title = "Strap Design ${i + 1}",
                    formula = "", formulaWithValues = line, result = line,
                    codeReference = codeNoteRef(code)
                )
            }
        )
    }

    private fun codeNoteRef(code: DesignCode): String = when (code) {
        DesignCode.ECP -> "ECP 203-2020"
        DesignCode.ACI -> "ACI 318-19"
        DesignCode.SBC -> "SBC 304-2018"
    }

    private fun barAreaMm2(diameter: Double): Double = Math.PI / 4.0 * diameter * diameter

    fun designStaircase(type: StairType, span: Double, riser: Double, tread: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, preferredDiameter: Int, code: DesignCode): StairResult {
        InputGuard.positive("span", span)
        InputGuard.positive("riser", riser)
        InputGuard.positive("tread", tread)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        return try {
            val nTreads = max(1, (span * 1000.0 / tread).roundToInt())
            val nRisers = nTreads + 1
            val totalRise = riser * nRisers / 1000.0
            val defaultThickness = max(span * 1000.0 / 25.0, 120.0).coerceIn(120.0, 300.0)

            val input = StaircaseInput(
                stairType = type.toDomain(),
                span = span,
                totalRise = totalRise,
                stairWidth = 1.2,
                waistThickness = defaultThickness,
                fcu = fcu,
                fy = fy,
                deadLoad = deadLoad,
                liveLoad = liveLoad,
                riserCount = nRisers,
                going = tread
            )

            val result = CalculationFactory.getStaircaseDesign(code).designStaircase(input)

            val mainBar = parseBarSpec(result.mainRebar, result.mainRebarArea)
            val distBar = parseBarSpec(result.distributionRebar, result.distributionRebarArea)

            val utilization = result.safetyChecks.firstOrNull {
                it.name.contains("Flexure", ignoreCase = true) || it.name.contains("K ", ignoreCase = true)
            }?.let { if (it.limit > 0) (it.value / it.limit).coerceIn(0.1, 1.5) else 0.7 } ?: 0.7

            val concreteVolume = result.inclinedLength * 1.2 * defaultThickness / 1000.0
            val steelWeight = (result.inclinedLength + 0.6) * result.mainRebarArea * 7850.0 / 1e6 +
                (result.distributionRebarArea / 1000.0) * 1.2 * 7850.0 / 1e6
            val cost = concreteVolume * 120.0 + steelWeight * 1.5

            StairResult(
                type = type,
                thickness = defaultThickness,
                reinforcement = mainBar,
                distributionReinforcement = distBar,
                isSafe = result.isSafe,
                concreteVolume = concreteVolume,
                steelWeight = steelWeight,
                cost = cost,
                code = code,
                safetyChecks = result.safetyChecks.map {
                    DesignSafetyCheck(name = it.name, value = it.value, limit = it.limit, unit = it.unit, isSafe = it.isSafe)
                },
                utilizationRatio = utilization,
                mu = result.maxMoment,
                wu = result.horizontalLoad,
                span = span,
                riser = result.riser,
                tread = result.going,
                fcu = fcu,
                fy = fy,
                suggestions = result.safetyChecks.filterNot { it.isSafe }.map { it.description }
            )
        } catch (e: Exception) {
            // ADR-010: لا تُرجع isSafe=true عند الفشل — loud failure, no silent safe
            StairResult(type = type, thickness = 150.0, reinforcement = ReinforcementBar(), distributionReinforcement = ReinforcementBar(), isSafe = false, concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0, code = code, riser = riser, tread = tread,
                suggestions = listOf("Calculation failed: ${e.message?.take(200) ?: "Unknown error"} / فشل الحساب"))
        }
    }

    private fun parseBarSpec(spec: String, area: Double): ReinforcementBar {
        if (spec.isBlank() || spec.contains("None")) {
            return ReinforcementBar(description = spec)
        }
        // Engine specs come in two shapes:
        //  - retaining wall: "3Φ28"          (count first)
        //  - staircase:      "Ø16 @ 150 mm c/c"  (spacing first)
        // The old code treated every number pair as [dia, spacing], so "3Φ28" collapsed
        // to Φ6@50 for every code - the retaining wall showed identical bars for ECP/ACI/SBC.
        val counted = Regex("(\\d+)\\s*[\\u03A6\\u00D8]\\s*(\\d+)").find(spec)
        if (counted != null) {
            val numBars = counted.groupValues[1].toInt()
            val dia = counted.groupValues[2].toInt().coerceIn(6, 32)
            val derivedArea = numBars * PI * (dia / 2.0).pow(2)
            val areaFinal = if (area > 0.0) area else derivedArea
            return ReinforcementBar(
                numBars = numBars,
                diameter = dia,
                spacing = 1000.0 / numBars,
                description = spec,
                weightKg = (areaFinal / 1e6) * 7850.0
            )
        }
        val spaced = Regex("[\\u03A6\\u00D8]\\s*(\\d+)\\s*@\\s*(\\d+)").find(spec)
        if (spaced != null) {
            val dia = spaced.groupValues[1].toInt().coerceIn(6, 32)
            val spacing = spaced.groupValues[2].toDouble().coerceIn(50.0, 500.0)
            return ReinforcementBar(
                diameter = dia,
                spacing = spacing,
                description = spec,
                weightKg = (area / 1e6) * 7850.0
            )
        }
        val numbers = Regex("\\d+(\\.\\d+)?").findAll(spec).map { it.value.toDouble() }.toList()
        if (numbers.isEmpty()) return ReinforcementBar(description = spec)
        val dia = numbers[0].toInt().coerceIn(6, 32)
        val spacing = if (numbers.size > 1) numbers[1].coerceIn(50.0, 500.0) else 150.0
        return ReinforcementBar(
            diameter = dia,
            spacing = spacing,
            description = spec,
            weightKg = (area / 1e6) * 7850.0
        )
    }

    fun designTank(type: TankType, capacity: Double, height: Double, fcu: Double, fy: Double, preferredDiameter: Int = 12, code: DesignCode = DesignCode.ECP): TankResult {
        InputGuard.positive("height", height)
        InputGuard.positive("capacity", capacity)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        val domainCode = code
        val tankEngine = CalculationFactory.getTankDesign(domainCode)

        val hM = height / 1000.0
        val sideM = sqrt(capacity / max(0.5, hM))
        val sideMm = sideM * 1000.0

        val baseType = when(type) {
            TankType.CIRCULAR_GROUND -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_GROUND
            TankType.CIRCULAR_ELEVATED -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_ELEVATED
            TankType.CIRCULAR_UNDERGROUND -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_UNDERGROUND
            TankType.RECTANGULAR_ELEVATED -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_ELEVATED
            TankType.UNDERGROUND -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_UNDERGROUND
            else -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_GROUND
        }

        val res = tankEngine.calculateTank(
            length = sideMm, width = sideMm, height = height, waterDepth = max(100.0, height - 300.0),
            fcu = fcu, fy = fy, type = baseType
        )

        val wallBar = ReinforcementBar(
            numBars = res.wallReinforcement.numberOfBars,
            diameter = res.wallReinforcement.barDiameter.toInt().takeIf { it > 0 } ?: preferredDiameter,
            spacing = res.wallReinforcement.spacing.takeIf { it > 0 } ?: 200.0,
            weightKg = res.steelWeight * 0.5
        )

        val baseBar = ReinforcementBar(
            numBars = res.baseReinforcement.numberOfBars,
            diameter = res.baseReinforcement.barDiameter.toInt().takeIf { it > 0 } ?: preferredDiameter,
            spacing = res.baseReinforcement.spacing.takeIf { it > 0 } ?: 200.0,
            weightKg = res.steelWeight * 0.5
        )

        val checks = res.safetyChecks.map {
            DesignSafetyCheck(it.name, it.value, it.limit, it.unit, it.isSafe)
        }

        return TankResult(
            type = type, length = sideMm, width = sideMm, height = height,
            wallThickness = res.wallThickness, baseThickness = res.baseThickness,
            wallReinforcement = wallBar, baseReinforcement = baseBar,
            isSafe = res.isSafe, concreteVolume = res.concreteVolume, steelWeight = res.steelWeight,
            cost = res.cost, code = code, waterPressure = res.pressure,
            capacity = res.capacityM3, safetyChecks = checks, fcu = fcu, fy = fy
        )
    }

    fun designRetainingWall(height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double, preferredDiameter: Int = 16, code: DesignCode = DesignCode.ECP): RetainingWallResult {
        InputGuard.positive("height", height)
        InputGuard.positive("soilDensity", soilDensity)
        InputGuard.positive("frictionAngle", frictionAngle)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        val domainCode = code
        val wallEngine = CalculationFactory.getRetainingWallDesign(domainCode)

        // The engines take every linear dimension in METRES (their stem depth is
        // `stemBaseThickness * 1000 - cover` and the self-weight terms are
        // 25 kN/m³ × metre-volumes). The old code passed the thicknesses in millimetres,
        // which inflated the weights/moments/required steel and produced nonsense
        // stability factors - and, because the mistake was identical for all codes, it also
        // hid the code differences.
        val baseWidth = max(0.5 * height, 1.5)          // m
        val stemBaseThick = max(0.1 * height, 0.30)     // m
        val stemTopThick = 0.25                          // m
        val baseThick = max(0.1 * height, 0.35)          // m

        val input = RetainingWallInput(
            wallHeight = height,
            stemBaseThickness = stemBaseThick,
            stemTopThickness = stemTopThick,
            baseWidth = baseWidth,
            baseThickness = baseThick,
            toeLength = 0.3 * baseWidth,
            heelLength = 0.6 * baseWidth,
            soilDensity = soilDensity,
            frictionAngle = frictionAngle,
            surchargeLoad = surcharge,
            waterTableDepth = height + 1.0,
            fcu = fcu,
            fy = fy
        )

        val res = wallEngine.designRetainingWall(input)

        // Honor the user's preferred bar diameter (the screen's bar selector) by re-selecting
        // the bar count at that diameter against the engine's code-required area; fall back to
        // the engine's own selection when the preferred bars cannot cover the requirement
        // (selectBars allows at most 12 bars).
        val requiredStem = res.safetyChecks.firstOrNull { it.name == "Stem Flexure" }?.limit
            ?: res.stemMainRebarArea
        val preferredArea = PI / 4.0 * preferredDiameter * preferredDiameter
        val preferredCount = ceil(requiredStem / preferredArea).toInt()
        val stemBar = if (preferredCount in 1..12 && preferredCount * preferredArea >= requiredStem) {
            ReinforcementBar(
                numBars = preferredCount,
                diameter = preferredDiameter,
                spacing = 1000.0 / preferredCount,
                description = res.stemMainRebar,
                weightKg = (preferredCount * preferredArea / 1e6) * 7850.0
            )
        } else {
            parseBarSpec(res.stemMainRebar, res.stemMainRebarArea)
        }
        val baseBar = parseBarSpec(res.toeRebar, 0.0)

        val concreteVolume = baseWidth * baseThick + (stemBaseThick + stemTopThick) / 2.0 * height
        val steelWeight = (res.stemMainRebarArea / 1e6) * height * 7850.0 * 2.0
        val cost = concreteVolume * 120.0 + steelWeight * 1.5

        val checks = res.safetyChecks.map {
            DesignSafetyCheck(it.name, it.value, it.limit, "", it.isSafe)
        }

        return RetainingWallResult(
            height = height, stemThickness = stemBaseThick * 1000.0, baseWidth = baseWidth * 1000.0,
            stemReinforcement = stemBar, baseReinforcement = baseBar,
            safetyChecks = checks, isSafe = res.isSafe,
            concreteVolume = concreteVolume, steelWeight = steelWeight, cost = cost, code = code,
            factorOfSafetyOverturning = res.overturningFS, factorOfSafetySliding = res.slidingFS,
            maxBearingPressure = res.maxBearingPressure, minBearingPressure = res.minBearingPressure,
            bearingFS = res.bearingFS, muStem = res.stemMoment, fcu = fcu, fy = fy
        )
    }

    /**
     * Calculate seismic loads using the code-specific seismic engine via CalculationFactory.
     * Previously used an inline simplified formula — now delegates to the proper
     * ECP 201 / ASCE 7 / SBC 301 seismic design implementations.
     */
    fun calculateSeismicLoads(input: SeismicInput, code: DesignCode = DesignCode.ECP): SeismicResult {
        InputGuard.positive("height", input.height)
        InputGuard.positive("totalWeight", input.totalWeight)
        // Zone feeds the bucket mapping below; NaN or a negative factor previously fell through
        // to ZONE_5 silently instead of failing.
        InputGuard.inRange("zone", input.zone, 0.0, 0.40)
        InputGuard.notBlank("soilType", input.soilType)
        InputGuard.positive("importance", input.importance)
        InputGuard.positive("reductionFactor", input.reductionFactor)

        val domainCode = code
        val seismicEngine = CalculationFactory.getSeismicDesign(domainCode)

        // Map zone factor to SeismicZone enum
        val seismicZone = when {
            input.zone <= 0.10 -> SeismicZone.ZONE_1
            input.zone <= 0.15 -> SeismicZone.ZONE_2
            input.zone <= 0.20 -> SeismicZone.ZONE_3
            input.zone <= 0.30 -> SeismicZone.ZONE_4
            else -> SeismicZone.ZONE_5
        }

        // Map soil type string to SoilType enum
        val soilType = when (input.soilType.uppercase()) {
            "A" -> SoilType.A
            "B" -> SoilType.B
            "D" -> SoilType.D
            "E" -> SoilType.E
            else -> SoilType.C
        }

        val baseShearResult = seismicEngine.calculateBaseShear(
            totalWeight = input.totalWeight,
            seismicZone = seismicZone,
            soilType = soilType,
            importanceFactor = input.importance.coerceAtLeast(1.0),
            responseModificationFactor = input.reductionFactor.coerceAtLeast(1.0),
            buildingHeight = input.height
        )

        val spectrum = seismicEngine.getResponseSpectrum(
            period = 0.075 * input.height.pow(0.75),
            dampingRatio = 0.05,
            soilType = soilType,
            importanceFactor = input.importance.coerceAtLeast(1.0)
        )

        val timePeriod = 0.075 * input.height.pow(0.75)
        val drift = (0.01 * input.height) / input.reductionFactor.coerceAtLeast(1.0)

        return SeismicResult(
            baseShear = baseShearResult.baseShear,
            storyDrift = drift,
            timePeriod = timePeriod,
            spectralAcceleration = spectrum.spectralAcceleration,
            isSafe = drift <= 0.02 * input.height,
            code = code,
            zone = input.zone,
            importance = input.importance.coerceAtLeast(1.0),
            reductionFactor = input.reductionFactor.coerceAtLeast(1.0),
            totalWeight = input.totalWeight,
            height = input.height
        )
    }

    fun calculateWeldCapacity(size: Double, length: Double, electrode: ElectrodeType, code: DesignCode): Double {
        InputGuard.positive("size", size)
        InputGuard.positive("length", length)
        InputGuard.notNull("electrode", electrode)
        InputGuard.notNull("code", code)
        return when (code) {
            DesignCode.ACI -> AISCSteelDesignEngine().calculateFilletWeldCapacity(size, length, electrode)
            DesignCode.SBC -> SBCSteelDesignEngine().calculateFilletWeldCapacity(size, length, electrode)
            DesignCode.ECP -> SteelConnectionDesign().calculateFilletWeldCapacity(size, length, electrode)
        }
    }

    fun calculateBoltCapacity(diameter: Double, grade: BoltGrade, count: Int, code: DesignCode): Double {
        InputGuard.positive("diameter", diameter)
        InputGuard.positive("count", count)
        InputGuard.notNull("grade", grade)
        InputGuard.notNull("code", code)
        return when (code) {
            DesignCode.ACI -> AISCSteelDesignEngine().calculateBoltShearCapacity(diameter, grade, count)
            DesignCode.SBC -> SBCSteelDesignEngine().calculateBoltShearCapacity(diameter, grade, count)
            DesignCode.ECP ->
                SteelConnectionDesign().calculateBoltShearCapacity(grade, diameter, isDoubleShear = false, includeThreadedArea = true) * count
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Missing Bridge Functions — Phase 1 Fix
    //  These functions connect the UI layer to the CalculationFactory
    //  routing that was already in place but never bridged.
    // ══════════════════════════════════════════════════════════════

    /**
     * Shear Wall Design — delegates to code-specific engine via CalculationFactory.
     * Covers: flexural design, shear design, boundary elements, coupling beams, slenderness.
     */
    fun designShearWall(input: ShearWallInput, code: DesignCode = DesignCode.ECP): ShearWallResult {
        InputGuard.positive("wallLength", input.wallLength)
        InputGuard.positive("wallThickness", input.wallThickness)
        InputGuard.positive("wallHeight", input.wallHeight)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code
        val engine = CalculationFactory.getShearWallDesign(domainCode)
        return engine.designWall(input)
    }

    /**
     * Pile Foundation Design — delegates to code-specific engine via CalculationFactory.
     * Covers: single pile capacity, pile cap, settlement, group efficiency, lateral load.
     */
    fun designPileFoundation(input: PileInput, code: DesignCode = DesignCode.ECP): PileDesignResult {
        InputGuard.positive("pileDiameter", input.pileDiameter)
        InputGuard.positive("pileLength", input.pileLength)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code
        val engine = CalculationFactory.getPileFoundationDesign(domainCode)
        return engine.designPile(input)
    }

    /**
     * Flat Slab Design — delegates to code-specific engine via CalculationFactory.
     * Covers: DDM/EFM, moment distribution, punching shear, deflection, drop panels.
     */
    fun designFlatSlab(input: FlatSlabInput, code: DesignCode = DesignCode.ECP): FlatSlabResult {
        InputGuard.positive("lx", input.lx)
        InputGuard.positive("ly", input.ly)
        InputGuard.positive("slabThickness", input.slabThickness)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code
        val engine = CalculationFactory.getFlatSlabDesign(domainCode)
        return engine.design(input)
    }

    /**
     * Combined Footing Design — delegates to code-specific engine via CalculationFactory.
     * Covers: two-column combined footing with soil pressure, flexure, and shear checks.
     */
    fun designCombinedFooting(
        fcu: Double, fy: Double,
        axialLoad1: Double, axialLoad2: Double,
        distanceBetweenColumns: Double,
        soilBearingCapacity: Double,
        footingDepth: Double,
        code: DesignCode = DesignCode.ECP,
        columnWidth: Double = 400.0,
        columnDepth: Double = 400.0,
        col2Width: Double = 400.0,
        col2Depth: Double = 400.0
    ): FootingDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("axialLoad1", axialLoad1)
        InputGuard.positive("axialLoad2", axialLoad2)
        InputGuard.positive("distanceBetweenColumns", distanceBetweenColumns)
        InputGuard.positive("soilBearingCapacity", soilBearingCapacity)

        val domainCode = code
        val footingEngine = CalculationFactory.getFootingDesign(domainCode)
        return footingEngine.designCombinedFooting(
            fcu = fcu, fy = fy,
            axialLoad1 = axialLoad1, axialLoad2 = axialLoad2,
            distanceBetweenColumns = distanceBetweenColumns,
            soilBearingCapacity = soilBearingCapacity,
            footingDepth = footingDepth,
            loadCombination = LoadCombination.DEAD_LIVE,
            columnWidth = columnWidth, columnDepth = columnDepth,
            col2Width = col2Width, col2Depth = col2Depth
        )
    }

    /**
     * Waffle Slab Design — delegates to code-specific engine via CalculationFactory.
     * Covers: rib flexure/shear, solid head design, punching shear, deflection.
     */
    fun designWaffleSlab(input: WaffleSlabDesign.WaffleSlabInput, code: DesignCode = DesignCode.ECP): WaffleSlabDesign.WaffleSlabResult {
        InputGuard.positive("lx", input.lx)
        InputGuard.positive("ly", input.ly)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code
        val engine = CalculationFactory.getWaffleSlabDesign(domainCode)
        return engine.design(input)
    }

    private fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en
}

/** Localized Arabic label for a design code (UI presentation). */
val DesignCode.displayNameAr: String
    get() = when (this) {
        DesignCode.ECP -> "الكود المصري - ECP 203"
        DesignCode.ACI -> "الكود الأمريكي - ACI 318"
        DesignCode.SBC -> "الكود السعودي - SBC 304"
    }

/** Localized English label for a design code (UI presentation). */
val DesignCode.displayNameEn: String
    get() = when (this) {
        DesignCode.ECP -> "Egyptian Code - ECP 203"
        DesignCode.ACI -> "American Code - ACI 318"
        DesignCode.SBC -> "Saudi Code - SBC 304"
    }

/** Locale-aware label for a design code, matching the previous nested-enum text. */
val DesignCode.localizedName: String
    get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
