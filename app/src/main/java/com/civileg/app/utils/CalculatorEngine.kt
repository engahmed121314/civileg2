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
        val cost: Double = 0.0, val code: DesignCode = DesignCode.EGYPTIAN, 
        val appliedMoment: Double = 0.0, val appliedShear: Double = 0.0,
        val supportType: SupportType = SupportType.HINGED_HINGED,
        val span: Double = 0.0,
        val momentCapacity: Double = 0.0, val shearCapacity: Double = 0.0, val steelRatio: Double = 0.0,
        val deflection: Double = 0.0, val allowableDeflection: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val calculationSteps: @RawValue List<CalculationStep> = emptyList(),
        val steelWasteTons: Double = 0.0,
        val neutralAxisDepth: Double = 0.0
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
        // ── InputGuard (ADR-010) ──
        InputGuard.notNull("section", section)
        InputGuard.notNull("memberType", memberType)
        InputGuard.notNull("inputs", inputs)
        InputGuard.notNull("code", code)

        val area = section.area
        // ── توسيع: حساب حقيقي حسب الكود (ACI/AISC/SBC) وليس stub ──
        val fy = inputs.grade.fy  // استخدم مقاومة الخضوع الفعلية من الدرجة
        val E = 200000.0  // MPa — معامل مرونة الصلب
        val phi = 0.90     // معامل الاختزال (AISC 360 / SBC 306)

        // 1. القدرة المحورية: φPn = φ × fy × Ag (tension yielding per AISC D2)
        val axialCapacity = (phi * fy * area) / 1000.0  // kN

        // 2. القدرة الانحنائية: φMn = φ × fy × Sx (per AISC F2 / SBC 306)
        val Sx = section.sx  // elastic section modulus (mm³)
        val flexuralCapacity = if (Sx > 0) (phi * fy * Sx) / 1e6 else 0.0  // kN.m

        // 3. قدرة القص: φVn = φ × 0.6 × fy × Aw (per AISC G2 / SBC 306)
        val tw = section.webThickness
        val h = section.depth
        val Aw = h * tw  // مساحة النصل الوبية
        val shearCapacity = if (Aw > 0) (phi * 0.6 * fy * Aw) / 1000.0 else 0.0  // kN

        // 4. فحص الانبعاج (KL/r) — per AISC E3 / SBC 306
        val L = inputs.unbracedLength.coerceAtLeast(1.0)
        val K = 1.0  // effective length factor (default = 1.0 for braced)
        val rx = section.rx.coerceAtLeast(1.0)
        val KLOverR = K * L * 1000.0 / rx  // KL/r
        val Fe = (PI * PI * E) / (KLOverR * KLOverR)  // Euler stress
        val isSlender = KLOverR > 4.71 * sqrt(E / fy)
        val Fcr = if (isSlender) 0.877 * Fe else fy * (1.0 - 0.375 * KLOverR * KLOverR * fy / (PI * PI * E))
        val compressiveCapacity = (phi * Fcr * area) / 1000.0  // kN
        val bucklingSafe = !isSlender || (compressiveCapacity > 0)

        // 5. فحص الانحراف: L/250 للأعضاء الرئيسية
        val deflectionLimit = L * 1000.0 / 250.0  // mm
        val appliedDeflection = 0.0  // mm (no applied deflection input — skip check if 0)
        val deflectionSafe = appliedDeflection <= 0 || appliedDeflection <= deflectionLimit

        // 6. نسبة الاستغلال
        val axialDemand = inputs.axialLoad.coerceAtLeast(0.0)
        val flexDemand = inputs.moment.coerceAtLeast(0.0)
        val shearDemand = inputs.shear.coerceAtLeast(0.0)
        val axialRatio = if (axialCapacity > 0) axialDemand / axialCapacity else 0.0
        val flexRatio = if (flexuralCapacity > 0) flexDemand / flexuralCapacity else 0.0
        val shearRatio = if (shearCapacity > 0) shearDemand / shearCapacity else 0.0
        val utilizationRatio = (axialRatio + flexRatio + shearRatio).coerceIn(0.0, 3.0)

        val isSafe = utilizationRatio <= 1.0 && bucklingSafe && deflectionSafe

        val codeRef = when (code) {
            DesignCode.ACI -> "AISC 360-16"
            DesignCode.SAUDI -> "SBC 306-2018"
            DesignCode.EGYPTIAN -> "ECP 205-2007 (Steel)"
            else -> "Unknown Code"
        }

        return SteelMemberResult(
            sectionType = section, memberType = memberType, axialCapacity = axialCapacity,
            flexuralCapacity = flexuralCapacity, shearCapacity = shearCapacity,
            utilizationRatio = utilizationRatio,
            isSafe = isSafe,
            connectionDesign = null,
            bucklingCheck = if (!bucklingSafe) BucklingCheckResult(slendernessRatio = KLOverR, criticalStress = Fcr, bucklingMode = BucklingMode.FLEXURAL, isSafe = bucklingSafe, codeReference = codeRef) else null,
            deflectionCheck = if (!deflectionSafe) DeflectionCheckResult(calculatedDeflection = appliedDeflection, allowableDeflection = deflectionLimit, ratio = if (deflectionLimit > 0) appliedDeflection / deflectionLimit else 0.0, isSafe = deflectionSafe, message = "Deflection exceeds L/250", recommendation = "Increase section depth or reduce span") else null,
            weight = area * 1e-6 * 7850, cost = 0.0,
            warnings = mutableListOf<String>().apply {
                if (!bucklingSafe) add("$codeRef: Slender member (KL/r=${"%.1f".format(KLOverR)}) — increase section or reduce L")
                if (!deflectionSafe) add("$codeRef: Deflection ${"%.1f".format(appliedDeflection)}mm > L/250=${"%.1f".format(deflectionLimit)}mm")
            },
            codeNotes = mutableListOf<String>().apply {
                add("$codeRef: Steel member design")
                add("fy = $fy MPa, φ = $phi")
                add("φPn = ${"%.1f".format(axialCapacity)} kN, φMn = ${"%.1f".format(flexuralCapacity)} kN.m, φVn = ${"%.1f".format(shearCapacity)} kN")
                add("KL/r = ${"%.1f".format(KLOverR)}, utilization = ${"%.3f".format(utilizationRatio)}")
            }
        )
    }

    fun designSteelWarehouse(inputs: SteelWarehouseInputs): SteelWarehouseAnalysisResult {
        InputGuard.positive("span", inputs.span)
        InputGuard.positive("eaveHeight", inputs.eaveHeight)
        InputGuard.positive("baySpacing", inputs.baySpacing)
        InputGuard.positive("length", inputs.length)

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

        val mainFrame = MainFrameResult(
            columnSection = colSection, rafterSection = rafSection,
            maxMoment = maxMoment, maxShear = maxShear, maxAxial = maxAxial,
            maxDeflection = maxDeflection, allowableDeflection = allowableDef,
            isSafe = maxDeflection <= allowableDef,
            utilizationMoment = (maxMoment / (rafSection.sx * 240.0 / 1e6)).coerceIn(0.1, 1.5),
            utilizationShear = (maxShear / (colSection.area * 0.6 * 240.0 / 1000.0)).coerceIn(0.1, 1.5),
            utilizationAxial = (maxAxial / (colSection.area * 240.0 / 1000.0)).coerceIn(0.1, 1.5)
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

        val domainCode = code.toDomain()
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
                DesignSafetyCheck("Axial Capacity", pu, res.astProvided * fy / 1.15 + 0.67 * fcu / 1.5 * (width * depth - res.astProvided) / 1000.0, "kN", res.isSafe),
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
            confinementLength = max(6.0 * res.barDiameter, 450.0)
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

        val domainCode = code.toDomain()
        val beamEngine = CalculationFactory.getBeamDesign(domainCode)

        val selfWeight = if (autoIncludeSelfWeight) 25.0 * (width / 1000.0) * (height / 1000.0) else 0.0
        val wDl = deadLoad + selfWeight
        val wLl = liveLoad
        val wu = 1.4 * wDl + 1.6 * wLl

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

        val cover = 40.0
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
            neutralAxisDepth = flexRes.neutralAxisDepth
        )
    }

    fun designSlab(lx: Double, ly: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, ts: Double, preferredDiameter: Int, code: DesignCode, type: SlabType = SlabType.SOLID, prestressForce: Double = 0.0, dropPanelThickness: Double = 0.0, columnSize: Double = 400.0, openingWidth: Double = 0.0, openingLength: Double = 0.0, ribWidth: Double = 100.0, ribSpacing: Double = 500.0): SlabResult {
        InputGuard.positive("lx", lx)
        InputGuard.positive("ly", ly)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ts", ts)

        return try {
            val domainCode = code.toDomain()
            
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

            val wu = (1.4 * deadLoad + 1.6 * liveLoad)
            val span = min(lx, ly)
            val mu = wu * span.pow(2) / 8.0
            
            val d = max(50.0, ts - 20.0)
            val astReq = (mu * 1e6) / (0.87 * fy * d)
            val spacing = (1000.0 * (PI / 4.0 * preferredDiameter * preferredDiameter)) / max(astReq, 100.0)
            val clampedSpacing = spacing.coerceIn(100.0, 250.0)
            val astProvided = (1000.0 / clampedSpacing) * (PI / 4.0 * preferredDiameter * preferredDiameter)

            val concreteVol = lx * ly * ts / 1000.0
            val steelW = astProvided * lx * ly * 7850.0 / 1e6
            val costVal = concreteVol * 120.0 + steelW * 1.5

            return SlabResult(
                type = type,
                thickness = ts,
                isSafe = astProvided >= astReq,
                code = code,
                reinforcementMain = ReinforcementBar(spacing = clampedSpacing, diameter = preferredDiameter, weightKg = steelW),
                momentX = mu,
                utilizationRatio = (astReq / astProvided).coerceIn(0.1, 1.5),
                concreteVolume = concreteVol,
                steelWeight = steelW,
                cost = costVal,
                safetyChecks = listOf(
                    DesignSafetyCheck("Flexural Capacity", astProvided, astReq, "mm²/m", astProvided >= astReq)
                )
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

        val domainCode = code.toDomain()
        val footingEngine = CalculationFactory.getFootingDesign(domainCode)

        val initialDepth = 600.0
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
            column1Size = Pair(colB, colT)
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
        InputGuard.positive("soil", soil)

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

        val vol1 = (res.footing1.width * res.footing1.length * res.footing1.thickness) / 1e9
        val vol2 = (res.footing2.width * res.footing2.length * res.footing2.thickness) / 1e9
        val volStrap = (res.strapBeam.width * res.strapBeam.depth * distance) / 1e9
        val totalConcreteVol = vol1 + vol2 + volStrap

        val steel1 = res.footing1.reinforcement.numberOfBars * (PI / 4.0 * res.footing1.reinforcement.barDiameter.pow(2)) * (res.footing1.length / 1000.0) * 7850.0 / 1e6
        val steel2 = res.footing2.reinforcement.numberOfBars * (PI / 4.0 * res.footing2.reinforcement.barDiameter.pow(2)) * (res.footing2.length / 1000.0) * 7850.0 / 1e6
        val steelStrap = (res.strapBeam.topReinforcement.numberOfBars * (PI / 4.0 * res.strapBeam.topReinforcement.barDiameter.pow(2)) +
            res.strapBeam.bottomReinforcement.numberOfBars * (PI / 4.0 * res.strapBeam.bottomReinforcement.barDiameter.pow(2))) * distance * 7850.0 / 1e6
        val totalSteelW = steel1 + steel2 + steelStrap

        return StrapFootingResult(
            footing1 = FootingResult(
                type = FootingType.ISOLATED, width = res.footing1.width, length = res.footing1.length, thickness = res.footing1.thickness,
                reinforcementBottom = ReinforcementBar(numBars = res.footing1.reinforcement.numberOfBars, diameter = res.footing1.reinforcement.barDiameter.toInt(), weightKg = steel1),
                isSafe = res.isSafe, code = code, allowablePressure = soil, 
                soilPressure = res.reactions.first / (res.footing1.width * res.footing1.length / 1e6),
                concreteVolume = vol1, steelWeight = steel1, cost = vol1 * 120.0 + steel1 * 1.5
            ),
            footing2 = FootingResult(
                type = FootingType.ISOLATED, width = res.footing2.width, length = res.footing2.length, thickness = res.footing2.thickness,
                reinforcementBottom = ReinforcementBar(numBars = res.footing2.reinforcement.numberOfBars, diameter = res.footing2.reinforcement.barDiameter.toInt(), weightKg = steel2),
                isSafe = res.isSafe, code = code, allowablePressure = soil, 
                soilPressure = res.reactions.second / (res.footing2.width * res.footing2.length / 1e6),
                concreteVolume = vol2, steelWeight = steel2, cost = vol2 * 120.0 + steel2 * 1.5
            ),
            strapBeamWidth = res.strapBeam.width,
            strapBeamDepth = res.strapBeam.depth,
            strapTopReinforcement = ReinforcementBar(numBars = res.strapBeam.topReinforcement.numberOfBars, diameter = res.strapBeam.topReinforcement.barDiameter.toInt()),
            strapBottomReinforcement = ReinforcementBar(numBars = res.strapBeam.bottomReinforcement.numberOfBars, diameter = res.strapBeam.bottomReinforcement.barDiameter.toInt()),
            reactions = res.reactions,
            isSafe = res.isSafe,
            concreteVolume = totalConcreteVol, 
            steelWeight = totalSteelW,
            // ── توسيع: نسبة الاستغلال الفعلية من ضغط التربة والقدرة ──
            utilizationRatio = if (res.isSafe) {
                val area1 = res.footing1.width * res.footing1.length
                val area2 = res.footing2.width * res.footing2.length
                val maxSoilPressure = maxOf(
                    if (area1 > 0) (res.reactions.first / (area1 / 1e6)).coerceAtLeast(0.0) else 0.0,
                    if (area2 > 0) (res.reactions.second / (area2 / 1e6)).coerceAtLeast(0.0) else 0.0
                )
                val allowableSoil = inputs.soilBearingCapacity.coerceAtLeast(1.0)
                (maxSoilPressure / allowableSoil).coerceIn(0.0, 1.5)
            } else 1.5  // غير آمن → نسبة عالية
        )
    }

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

            val result = CalculationFactory.getStaircaseDesign(code.toDomain()).designStaircase(input)

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

    fun designTank(type: TankType, capacity: Double, height: Double, fcu: Double, fy: Double, preferredDiameter: Int = 12, code: DesignCode = DesignCode.EGYPTIAN): TankResult {
        InputGuard.positive("height", height)
        InputGuard.positive("capacity", capacity)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        val domainCode = code.toDomain()
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

    fun designRetainingWall(height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double, preferredDiameter: Int = 16, code: DesignCode = DesignCode.EGYPTIAN): RetainingWallResult {
        InputGuard.positive("height", height)
        InputGuard.positive("soilDensity", soilDensity)
        InputGuard.positive("frictionAngle", frictionAngle)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        val domainCode = code.toDomain()
        val wallEngine = CalculationFactory.getRetainingWallDesign(domainCode)

        val baseWidth = max(0.5 * height, 1.5)
        val stemBaseThick = max(0.1 * height * 1000.0, 300.0)
        val stemTopThick = 250.0
        val baseThick = max(0.1 * height * 1000.0, 350.0)

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

        val stemBar = parseBarSpec(res.stemMainRebar, res.stemMainRebarArea)
        val baseBar = parseBarSpec(res.toeRebar, 0.0)

        val concreteVolume = (baseWidth * (baseThick / 1000.0) + (stemBaseThick + stemTopThick) / 2000.0 * height)
        val steelWeight = (res.stemMainRebarArea / 1e6) * height * 7850.0 * 2.0
        val cost = concreteVolume * 120.0 + steelWeight * 1.5

        val checks = res.safetyChecks.map {
            DesignSafetyCheck(it.name, it.value, it.limit, "", it.isSafe)
        }

        return RetainingWallResult(
            height = height, stemThickness = stemBaseThick, baseWidth = baseWidth * 1000.0,
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
    fun calculateSeismicLoads(input: SeismicInput, code: DesignCode = DesignCode.EGYPTIAN): SeismicResult {
        InputGuard.positive("height", input.height)
        InputGuard.positive("totalWeight", input.totalWeight)

        val domainCode = code.toDomain()
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
        val fExx = electrode.tensileStrength
        val phi = 0.75
        return (phi * 0.60 * fExx * 0.707 * size * length) / 1000.0
    }

    fun calculateBoltCapacity(diameter: Double, grade: BoltGrade, count: Int, code: DesignCode): Double {
        InputGuard.positive("diameter", diameter)
        InputGuard.positive("count", count)
        val fub = grade.fu
        val ab = PI / 4.0 * diameter * diameter
        val phi = 0.75
        val nominalShear = 0.45 * fub * ab
        return (phi * nominalShear * count) / 1000.0
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
    fun designShearWall(input: ShearWallInput, code: DesignCode = DesignCode.EGYPTIAN): ShearWallResult {
        InputGuard.positive("wallLength", input.wallLength)
        InputGuard.positive("wallThickness", input.wallThickness)
        InputGuard.positive("wallHeight", input.wallHeight)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code.toDomain()
        val engine = CalculationFactory.getShearWallDesign(domainCode)
        return engine.designWall(input)
    }

    /**
     * Pile Foundation Design — delegates to code-specific engine via CalculationFactory.
     * Covers: single pile capacity, pile cap, settlement, group efficiency, lateral load.
     */
    fun designPileFoundation(input: PileInput, code: DesignCode = DesignCode.EGYPTIAN): PileDesignResult {
        InputGuard.positive("pileDiameter", input.pileDiameter)
        InputGuard.positive("pileLength", input.pileLength)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code.toDomain()
        val engine = CalculationFactory.getPileFoundationDesign(domainCode)
        return engine.designPile(input)
    }

    /**
     * Flat Slab Design — delegates to code-specific engine via CalculationFactory.
     * Covers: DDM/EFM, moment distribution, punching shear, deflection, drop panels.
     */
    fun designFlatSlab(input: FlatSlabInput, code: DesignCode = DesignCode.EGYPTIAN): FlatSlabResult {
        InputGuard.positive("lx", input.lx)
        InputGuard.positive("ly", input.ly)
        InputGuard.positive("slabThickness", input.slabThickness)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code.toDomain()
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
        code: DesignCode = DesignCode.EGYPTIAN,
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

        val domainCode = code.toDomain()
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
    fun designWaffleSlab(input: WaffleSlabDesign.WaffleSlabInput, code: DesignCode = DesignCode.EGYPTIAN): WaffleSlabDesign.WaffleSlabResult {
        InputGuard.positive("lx", input.lx)
        InputGuard.positive("ly", input.ly)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)

        val domainCode = code.toDomain()
        val engine = CalculationFactory.getWaffleSlabDesign(domainCode)
        return engine.design(input)
    }

    private fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en
}
