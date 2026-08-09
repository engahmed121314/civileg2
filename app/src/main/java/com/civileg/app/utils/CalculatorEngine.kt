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
        val weightKg: Double = 0.0,
        val numLegs: Int = 2,
        val spacingAtSupport: Double = 0.0,
        val spacingAtMidspan: Double = 0.0,
        val condensationZoneLength: Double = 0.0,
        val codeNotes: String = "",
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
        val reinforcementArea: Double = 0.0,
        val minReinforcementArea: Double = 0.0,
        val maxReinforcementArea: Double = 0.0
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
        val utilizationRatio: Double = 0.0, val designCodeName: String = "",
        val reinforcementTopX: Int = 0,
        val topBarDiameter: Int = 12,
        val isCombined: Boolean = false,
        val distanceBetweenColumns: Double = 0.0,
        val column2Size: Pair<Double, Double> = Pair(500.0, 500.0)
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
        val capacityM3: Double = 0.0, val isOptimal: Boolean = true, val safetyCheck: String = "Safe",
        val maxMomentWall: Double = 0.0,
        val waterDepth: Double = 0.0
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
        val fcu: Double = 25.0, val fy: Double = 400.0,
        val suggestions: List<String> = emptyList()
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

    @Parcelize
    data class ColumnECPResult(val capacity: Double, val ast: Double) : Parcelable

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

    fun designColumn(
        width: Double,
        depth: Double,
        pu: Double,
        fcu: Double,
        fy: Double,
        code: DesignCode,
        isCircular: Boolean = false,
        connectedSlab: String = "SOLID",
        hasCap: Boolean = false,
        clearHeight: Double = 3000.0, 
        preferredDiameter: Int = 16,
        autoOptimize: Boolean = true,
        manualNumBars: Int? = null,
        mx: Double = 0.0,
        my: Double = 0.0
    ): ColumnResult {
        val ag = if (isCircular) PI * width.pow(2.0) / 4.0 else width * depth
        val fcPrime = if (code == DesignCode.EGYPTIAN) fcu else fcu * 0.8
        var capacity = 0.0
        var asMin = 0.008 * ag
        var asMax = 0.04 * ag
        val safetyChecks = mutableListOf<DesignSafetyCheck>()

        when (code) {
            DesignCode.EGYPTIAN -> {
                val i = if (isCircular) 0.25 * width else min(width, depth) / sqrt(12.0)
                val lambda = (1.0 * clearHeight) / i
                val lambdaLimit = if (isCircular) 8.0 else 10.0
                val isSlender = lambda > lambdaLimit
                asMin = 0.008 * ag 
                val asReq = max(asMin, (pu * 1000.0 - 0.35 * fcu * ag) / (0.67 * fy))
                capacity = (0.35 * fcu * (ag - asReq) + 0.67 * fy * asReq) / 1000.0
                safetyChecks.add(DesignSafetyCheck("Slenderness λ (ECP)", lambda, lambdaLimit, "", lambda <= lambdaLimit))
                if (isSlender) capacity *= 0.8 
            }
            DesignCode.SAUDI -> {
                val phi = if (isCircular) 0.75 else 0.65
                val alpha = if (isCircular) 0.85 else 0.80
                asMin = 0.01 * ag 
                asMax = 0.08 * ag
                val asReq = max(asMin, (pu * 1000.0 / (alpha * phi) - 0.85 * fcPrime * ag) / (fy - 0.85 * fcPrime))
                capacity = (alpha * phi * (0.85 * fcPrime * (ag - asReq) + fy * asReq)) / 1000.0
                val r = if (isCircular) 0.25 * width else min(width, depth) * 0.3
                val slenderness = (1.0 * clearHeight) / r
                safetyChecks.add(DesignSafetyCheck("SBC Slenderness (kl/r)", slenderness, 22.0, "", slenderness <= 22.0))
            }
            DesignCode.ACI -> {
                val phi = 0.65
                val alpha = 0.80
                asMin = 0.01 * ag
                asMax = 0.08 * ag
                val asReq = max(asMin, (pu * 1000.0 / (alpha * phi) - 0.85 * fcPrime * ag) / (fy - 0.85 * fcPrime))
                capacity = (alpha * phi * (0.85 * fcPrime * (ag - asReq) + fy * asReq)) / 1000.0
                val r = if (isCircular) 0.25 * width else min(width, depth) * 0.3
                val slenderness = (1.0 * clearHeight) / r
                safetyChecks.add(DesignSafetyCheck("ACI Slenderness Ratio", slenderness, 22.0, "", slenderness <= 22.0))
            }
            }

        val barDia = preferredDiameter.toDouble()
        val areaOneBar = PI * barDia.pow(2.0) / 4.0
        val asReqTotal = max(asMin, (pu * 1000.0 * 1.1) / fy)
        val finalNumBars = if (autoOptimize) ceil(asReqTotal / areaOneBar).toInt().coerceAtLeast(if (isCircular) 6 else 4) else (manualNumBars ?: 8)
        val finalAsProvided = finalNumBars * areaOneBar
        val rho = (finalAsProvided / ag) * 100.0
        val vol = (ag * clearHeight) / 1e9
        val db = preferredDiameter.toDouble() 
        val dtie = 8.0 
        val tieArea = PI * dtie.pow(2.0) / 4.0
        val sMaxECP = minOf(200.0, 16.0 * db, 48.0 * dtie, min(width, depth) / 2.0)
        val sMaxACI = minOf(16.0 * db, 48.0 * dtie, min(width, depth))
        val sMax = when (code) {
            DesignCode.ACI -> sMaxACI
            DesignCode.SAUDI -> minOf(200.0, sMaxACI)
            else -> sMaxECP
        }
        val condensationLen = max(width, depth)
        val sDense = (sMax * 0.5).coerceIn(75.0, sMax)
        val stirrupDesc = "Ø${dtie.toInt()} @ ${sMax.toInt()}mm c/c"
        val stirrupLength = if (isCircular) PI * width / 1000.0 else (2 * (width + depth) / 1000.0)
        val numStirrups = ceil(clearHeight / sMax).toInt()
        val stirrupWeight = numStirrups * stirrupLength * (dtie.pow(2.0) / 162.0)
        val totalSteelWeight = (finalNumBars * (clearHeight / 1000.0) * (barDia.pow(2.0) / 162.0) + stirrupWeight) * 1.05
        val utilizationRatio = (pu / capacity).coerceIn(0.0, 1.2)
        safetyChecks.add(DesignSafetyCheck("Axial Capacity", pu, capacity, "kN", capacity >= pu))
        safetyChecks.add(DesignSafetyCheck("Min Reinforcement", rho, (asMin/ag)*100.0, "%", finalAsProvided >= asMin))

        val zones = listOf(
            StirrupZone("Bottom Zone", "", 0.0, condensationLen, sDense, 2, dtie.toInt()),
            StirrupZone("Middle Zone", "", condensationLen, clearHeight - condensationLen, sMax, 2, dtie.toInt()),
            StirrupZone("Top Zone", "", clearHeight - condensationLen, clearHeight, sDense, 2, dtie.toInt())
        )

        return ColumnResult(
            width = width, depth = depth, pu = pu, 
            reinforcement = ReinforcementBar(finalNumBars, preferredDiameter), 
            stirrups = StirrupReinforcement(diameter = dtie.toInt(), spacing = sMax, description = stirrupDesc, weightKg = stirrupWeight, numLegs = 2, spacingAtSupport = sDense, spacingAtMidspan = sMax, condensationZoneLength = condensationLen, zones = zones), 
            safetyChecks = safetyChecks, isSafe = capacity >= pu && rho <= (asMax/ag)*100.0,
            concreteVolume = vol, steelWeight = totalSteelWeight, 
            cost = (vol * settingsManager.concretePrice) + (totalSteelWeight / 1000.0 * settingsManager.steelPrice), 
            code = code, axialCapacity = capacity, appliedAxial = pu, utilizationRatio = utilizationRatio, columnType = if (isCircular) "CIRCULAR" else "RECTANGULAR", mx = mx, my = my, reinforcementArea = finalAsProvided, minReinforcementArea = asMin, maxReinforcementArea = asMax
        )
        }

    fun designBeam(
        width: Double, height: Double, span: Double, fcu: Double, fy: Double, deadLoad: Double, liveLoad: Double, preferredDiameter: Int, code: DesignCode, supportType: SupportType = SupportType.HINGED_HINGED, customMoment: Double? = null, customShear: Double? = null
    ): BeamResult {
        val domainCode = when(code) {
            DesignCode.EGYPTIAN -> com.civileg.app.domain.entities.DesignCode.ECP
            DesignCode.ACI -> com.civileg.app.domain.entities.DesignCode.ACI
            else -> com.civileg.app.domain.entities.DesignCode.SBC
        }
        val totalLoad = domainCode.getDeadLoadFactor() * deadLoad + domainCode.getLiveLoadFactor() * liveLoad
        val momentFactor = when (supportType) { SupportType.CANTILEVER -> 2.0; SupportType.FIXED_FIXED -> 12.0; else -> 8.0 }
        val shearFactor = when (supportType) { SupportType.CANTILEVER -> 1.0; else -> 2.0 }
        val mu = customMoment ?: (totalLoad * span.pow(2.0) / momentFactor)
        val vu = customShear ?: (totalLoad * span / shearFactor)
        val d = height - 50.0 
        var asReq = 0.0
        when(code) {
            DesignCode.EGYPTIAN -> {
                val R = (mu * 1e6) / ((fcu / 1.5) * width * d.pow(2))
                val omega = 1.0 * (1 - sqrt(max(0.0, 1.0 - 2.0 * R)))
                asReq = (omega * (fcu / 1.5) / (fy / 1.15)) * width * d
            }
            else -> {
                val Rn = (mu * 1e6) / (0.9 * width * d.pow(2))
                val rho = (0.85 * fcu * 0.8 / fy) * (1 - sqrt(max(0.0, 1 - (2 * Rn) / (0.85 * fcu * 0.8))))
                asReq = rho * width * d
            }
        }
        val asMin = 0.0015 * width * d
        asReq = max(asReq, asMin)
        val numBars = ceil(asReq / (PI * preferredDiameter.toDouble().pow(2) / 4.0)).toInt().coerceAtLeast(2)
        val stirrupSpacing = 200.0
        val stirrupDia = 8
        val stirrupWeight = (span * (2 * (width + height) / 1000.0) / stirrupSpacing) * (stirrupDia.toDouble().pow(2) / 162.0)
        val totalSteelWeight = (numBars * span * (preferredDiameter.toDouble().pow(2) / 162.0) + stirrupWeight) * 1.1
        val zones = listOf(StirrupZone("Full", "", 0.0, span * 1000.0, stirrupSpacing, 2, stirrupDia))
        return BeamResult(
            width = width, depth = height, mu = mu, vu = vu, reinforcementBottom = ReinforcementBar(numBars, preferredDiameter), reinforcementTop = ReinforcementBar(2, 12),
            stirrups = StirrupReinforcement(diameter = stirrupDia, spacing = stirrupSpacing, weightKg = stirrupWeight, zones = zones),
            isSafe = true, concreteVolume = width * height * span / 1e6, steelWeight = totalSteelWeight, cost = 1500.0, code = code, appliedMoment = mu, appliedShear = vu, utilizationRatio = 0.8
        )
    }

    fun designSlab(
        lx: Double, ly: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, ts: Double, preferredDiameter: Int, code: DesignCode, type: SlabType = SlabType.SOLID, prestressForce: Double = 0.0, dropPanelThickness: Double = 0.0, columnSize: Double = 400.0, openingWidth: Double = 0.0, openingLength: Double = 0.0
    ): SlabResult {
        return SlabResult(
            type = type, thickness = ts, reinforcementMain = ReinforcementBar(spacing = 200.0, diameter = preferredDiameter), reinforcementSecondary = ReinforcementBar(spacing = 200.0, diameter = 10),
            isSafe = true, concreteVolume = lx * ly * ts / 1e3, steelWeight = 100.0, cost = 3000.0, code = code, totalLoad = deadLoad + liveLoad, utilizationRatio = 0.7
        )
    }

    fun designStaircase(
        type: StairType, span: Double, riser: Double, tread: Double, deadLoad: Double, liveLoad: Double, fcu: Double, fy: Double, preferredDiameter: Int, code: DesignCode
    ): StairResult {
        return StairResult(
            type = type, thickness = 150.0, reinforcement = ReinforcementBar(spacing = 150.0, diameter = preferredDiameter), distributionReinforcement = ReinforcementBar(spacing = 200.0, diameter = 10),
            isSafe = true, concreteVolume = 2.0, steelWeight = 40.0, cost = 1000.0, code = code, mu = 20.0, wu = 15.0, span = span, riser = riser, tread = tread, fcu = fcu, fy = fy
        )
    }

    fun designTank(
        type: TankType, capacity: Double, height: Double, fcu: Double, fy: Double, preferredDiameter: Int = 12, code: DesignCode = DesignCode.EGYPTIAN, soilDensity: Double = 18.0
    ): TankResult {
        return TankResult(
            type = type, length = 5.0, width = 5.0, height = height, wallThickness = 250.0, baseThickness = 300.0, wallReinforcement = ReinforcementBar(spacing = 200.0, diameter = 12),
            isSafe = true, concreteVolume = 15.0, steelWeight = 200.0, cost = 10000.0, code = code, capacity = capacity, fcu = fcu, fy = fy
        )
    }

    fun designRetainingWall(
        height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double, preferredDiameter: Int = 16, code: DesignCode = DesignCode.EGYPTIAN, waterTableHeight: Double = 0.0, frictionCoeff: Double = 0.5, bearingCapacity: Double = 200.0
    ): RetainingWallResult {
        return RetainingWallResult(
            height = height, stemThickness = 300.0, baseWidth = 2.5, stemReinforcement = ReinforcementBar(spacing = 150.0, diameter = 16), baseReinforcement = ReinforcementBar(spacing = 200.0, diameter = 12),
            isSafe = true, concreteVolume = 10.0, steelWeight = 150.0, cost = 8000.0, code = code
        )
    }

    fun calculateSeismicLoads(input: SeismicInput): SeismicResult {
        return SeismicResult(baseShear = 100.0, storyDrift = 0.005, isSafe = true, code = DesignCode.EGYPTIAN)
    }

    fun getSteelSectionLibrary(): Map<String, List<com.civileg.app.domain.entities.SteelSectionType>> {
        return mapOf("HEA" to listOf(com.civileg.app.domain.entities.SteelSectionType.ISection(h = 190.0, bf = 200.0, tf = 10.0, tw = 6.5, grade = com.civileg.app.domain.entities.SteelGrade.ST37, customName = "HEA 200")))
    }

    fun calculateFooting(
        type: FootingType,
        p: Double,
        fcu: Double,
        fy: Double,
        soil: Double,
        colB: Double,
        colT: Double,
        code: DesignCode,
        preferredDiameter: Int = 16,
        preferredSpacing: Double = 150.0,
        p2: Double = 0.0,
        distance: Double = 0.0,
        maxLeft: Double? = null,
        maxRight: Double? = null,
        maxTop: Double? = null,
        maxBottom: Double? = null,
        numPiles: Int = 4,
        pileDia: Double = 500.0,
        pileCapacity: Double = 500.0
    ): FootingResult {
        return when (type) {
            FootingType.COMBINED -> calculateCombinedFootingInternal(p, p2, distance, fcu, fy, soil, colB, colT, code, preferredDiameter)
            FootingType.STRIP -> calculateStripFootingInternal(p, fcu, fy, soil, colB, code, preferredDiameter)
            FootingType.RAFT -> calculateRaftInternal(p, fcu, fy, soil, code, preferredDiameter)
            FootingType.PILE_CAP -> calculatePileCapInternal(p, numPiles, pileDia, pileCapacity, fcu, fy, colB, colT, code, preferredDiameter)
            else -> calculateIsolatedFootingInternal(p, fcu, fy, soil, colB, colT, code, preferredDiameter, maxLeft, maxRight, maxTop, maxBottom)
            }
        }

    private fun calculateIsolatedFootingInternal(
        p: Double, fcu: Double, fy: Double, soil: Double, colB: Double, colT: Double,
        code: DesignCode, preferredDiameter: Int,
        maxLeft: Double?, maxRight: Double?, maxTop: Double?, maxBottom: Double?
    ): FootingResult {
        val areaReq = (p * 1.15) / soil
        var fL = sqrt(areaReq) 
        var fW = fL
        val diff = (colT - colB) / 1000.0
        fL = sqrt(areaReq) + diff / 2.0
        fW = areaReq / fL

        maxLeft?.let { if (fW / 2.0 > it / 1000.0) { fW = it * 2.0 / 1000.0; fL = areaReq / fW } }
        maxRight?.let { if (fW / 2.0 > it / 1000.0) { fW = it * 2.0 / 1000.0; fL = areaReq / fW } }

        fL = ceil(fL * 20.0) / 20.0 
        fW = ceil(fW * 20.0) / 20.0
        val fLmm = fL * 1000.0
        val fWmm = fW * 1000.0
        val pu = if (code == DesignCode.EGYPTIAN) p * 1.5 else p * 1.4 
        val qu = pu / (fL * fW)
        val projectionL = (fLmm - colT) / 2.0 
        val projectionW = (fWmm - colB) / 2.0
        val muL = (qu / 1000.0) * (projectionL.pow(2) / 2.0)
        
        var thickness = 500.0
        var d = thickness - 70.0
        val punchingLimit = if (code == DesignCode.EGYPTIAN) 0.316 * sqrt(fcu / 1.5) else 0.33 * 0.75 * sqrt(fcu * 0.8)
        
        do {
            d = thickness - 70.0
            val punchingForce = pu * 1000.0 * (1 - (colB + d)*(colT + d)/(fLmm*fWmm))
            val b0 = 2 * (colB + d + colT + d)
            if (punchingForce / (b0 * d) > punchingLimit) thickness += 50.0
        } while (thickness < 2000.0 && (pu * 1000.0 * (1 - (colB + d)*(colT + d)/(fLmm*fWmm)) / (2 * (colB + d + colT + d) * d)) > punchingLimit)
        
        val vol = (fL * fW * thickness / 1000.0)
        val steelWeight = vol * 110.0 // kg estimation
        
        return FootingResult(
            type = FootingType.ISOLATED, width = fWmm, length = fLmm, thickness = thickness,
            soilPressure = (p * 1.1) / (fL * fW), allowablePressure = soil,
            reinforcementBottom = ReinforcementBar(spacing = 150.0, diameter = preferredDiameter, description = "Bottom Mesh"),
            isSafe = true, code = code, concreteVolume = vol, steelWeight = steelWeight, 
            cost = vol * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice),
            utilizationRatio = ((p * 1.1) / (fL * fW) / soil).coerceIn(0.0, 1.2)
        )
    }

    private fun calculateStripFootingInternal(p: Double, fcu: Double, fy: Double, soil: Double, colB: Double, code: DesignCode, preferredDiameter: Int): FootingResult {
        val w = (p * 1.1) / soil
        val vol = w * 1.0 * 0.5 // per m
        return FootingResult(FootingType.STRIP, w*1000, 1000.0, 500.0, soil, soil, ReinforcementBar(spacing=150.0, diameter=preferredDiameter), true, code, vol, vol*100, vol*5000)
    }

    private fun calculateRaftInternal(p: Double, fcu: Double, fy: Double, soil: Double, code: DesignCode, preferredDiameter: Int): FootingResult {
        val area = (p * 1.1) / soil
        val side = sqrt(area)
        val vol = area * 1.0
        return FootingResult(FootingType.RAFT, side*1000, side*1000, 1000.0, soil, soil, ReinforcementBar(spacing=150.0, diameter=preferredDiameter), true, code, vol, vol*120, vol*5000)
    }

    private fun calculatePileCapInternal(p: Double, numPiles: Int, pileDia: Double, pileCap: Double, fcu: Double, fy: Double, colB: Double, colT: Double, code: DesignCode, preferredDiameter: Int): FootingResult {
        val vol = numPiles * 0.6 * 0.6 * 1.2
        return FootingResult(FootingType.PILE_CAP, 2000.0, 2000.0, 1200.0, pileCap, pileCap, ReinforcementBar(numBars=8, diameter=20), true, code, vol, vol*150, vol*6000)
    }

    private fun calculateCombinedFootingInternal(p1: Double, p2: Double, dist: Double, fcu: Double, fy: Double, soil: Double, colB: Double, colT: Double, code: DesignCode, preferredDiameter: Int): FootingResult {
        val area = (p1 + p2) * 1.15 / soil
        val length = dist * 1.5 * 1000.0
        val width = area * 1e6 / length
        return FootingResult(FootingType.COMBINED, width, length, 800.0, soil, soil, ReinforcementBar(spacing=150.0, diameter=preferredDiameter), true, code, area*0.8, area*140, area*5500)
    }

    fun calculateSteelMember(section: com.civileg.app.domain.entities.SteelSectionType, memberType: com.civileg.app.domain.entities.SteelMemberType, inputs: com.civileg.app.domain.entities.SteelInputs, code: DesignCode): com.civileg.app.domain.entities.SteelMemberResult {
        return com.civileg.app.domain.entities.SteelMemberResult(
            sectionType = section, memberType = memberType, axialCapacity = 1000.0, flexuralCapacity = 200.0, shearCapacity = 150.0,
            utilizationRatio = 0.6, isSafe = true, weight = 50.0, cost = 5000.0,
            connectionDesign = null, bucklingCheck = null, deflectionCheck = null, warnings = emptyList(), codeNotes = emptyList()
        )
    }

    private fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en
}
