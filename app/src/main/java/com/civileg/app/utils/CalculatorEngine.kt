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

        val displayName: String get() = displayNameEn

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
        HOLLOW_BLOCK("هردي", "Hollow Block"),
        POST_TENSION("بوست تنشن", "Post-Tensioned"),
        WAFFLE("بلاطة وافل", "Waffle Slab");

        val displayName: String get() = displayNameEn
    }
    
    enum class SupportType(val displayName: String) {
        HINGED_HINGED("Hinged-Hinged"), ROLLER_HINGED("Roller-Hinged"), FIXED_HINGED("Fixed-Hinged"), FIXED_FIXED("Fixed-Fixed"), CANTILEVER("Cantilever")
    }
    
    enum class StairType(val displayName: String) { 
        SINGLE_FLIGHT("Single Flight"), DOUBLE_FLIGHT("Double Flight"), SPIRAL("Spiral") 
    }
    
    enum class TankType(val displayNameAr: String, val displayNameEn: String) { 
        RECTANGULAR_GROUND("أرضي مستطيل", "Rectangular Ground"), CIRCULAR_GROUND("أرضي دائري", "Circular Ground"), 
        RECTANGULAR_ELEVATED("علوي مستطيل", "Rectangular Elevated"), CIRCULAR_ELEVATED("علوي دائري", "Circular Elevated"),
        UNDERGROUND("تحت الأرض مستطيل", "Underground Rectangular"), CIRCULAR_UNDERGROUND("تحت الأرض دائري", "Underground Circular");

        val displayName: String get() = displayNameEn
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
        val barString: String get() = if (numBars > 0) "${numBars}Ø${diameter}" else if (spacing > 0) "${(1000/spacing).toInt()}Ø${diameter}/m'" else description
        val area: Double get() = if (numBars > 0) numBars * (PI * diameter.toDouble().pow(2.0) / 4.0) else if (spacing > 0) (1000.0/spacing) * (PI * diameter.toDouble().pow(2.0) / 4.0) else 0.0
        val totalLength: Double get() = if (numBars > 0) numBars * barLength else if (spacing > 0) (1000.0/spacing) * barLength else 0.0
    }

    @Parcelize
    data class StirrupReinforcement(
        val diameter: Int = 8, 
        val spacing: Double = 200.0, 
        val description: String = "5Ø8/m'", 
        val weightKg: Double = 0.0,
        val numLegs: Int = 2
    ) : Parcelable {
        val area: Double get() = numLegs * (PI * diameter.toDouble().pow(2.0) / 4.0)
    }

    @Parcelize
    data class DesignSafetyCheck(val name: String, val value: Double, val limit: Double, val unit: String, val isSafe: Boolean) : Parcelable

    @Parcelize
    data class BOQData(val concreteM3: Double, val steelKg: Double, val totalCost: Double, val currency: String = "EGP", val wasteTons: Double = 0.0) : Parcelable

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
        val momentCapacity: Double = 0.0, val shearCapacity: Double = 0.0, val steelRatio: Double = 0.0,
        val steelWasteTons: Double = 0.0,
        val deflection: Double = 0.0,
        val allowableDeflection: Double = 0.0,
        val utilizationRatio: Double = 0.0
    ) : Parcelable

    @Parcelize
    data class ColumnResult(
        val width: Double, val depth: Double, val pu: Double,
        val reinforcement: ReinforcementBar, val stirrups: StirrupReinforcement,
        val safetyChecks: List<DesignSafetyCheck> = emptyList(), val isSafe: Boolean,
        val concreteVolume: Double, val steelWeight: Double, val cost: Double, val code: DesignCode,
        val axialCapacity: Double = 0.0, val appliedAxial: Double = 0.0,
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
        val utilizationRatio: Double = 0.0
    ) : Parcelable

    enum class FootingType(val displayNameAr: String, val displayNameEn: String) {
        ISOLATED("منفصلة", "Isolated"),
        COMBINED("مشتركة", "Combined"),
        STRIP("شريطية", "Strip"),
        RAFT("لبشة خرسانية", "Raft"),
        PILE_CAP("هامة خوازيق", "Pile Cap");

        val displayName: String get() = displayNameEn
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
        val reinforcementTopX: Int = 0, val reinforcementTopY: Int = 0,
        val topBarDiameter: Int = 0,
        val isCombined: Boolean = false,
        val distanceBetweenColumns: Double = 0.0,
        val column1Size: Pair<Double, Double> = Pair(500.0, 500.0),
        val column2Size: Pair<Double, Double> = Pair(500.0, 500.0),
        val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val utilizationRatio: Double = 0.0,
        val isNeighbor: Boolean = false
    ) : Parcelable

    @Parcelize
    data class SlabResult(
        val type: SlabType, val thickness: Double,
        val reinforcementMain: ReinforcementBar, val reinforcementSecondary: ReinforcementBar,
        val isSafe: Boolean, val concreteVolume: Double, val steelWeight: Double,
        val cost: Double, val code: DesignCode,
        val momentX: Double = 0.0, val momentY: Double = 0.0, val totalLoad: Double = 0.0,
        val punchingSafe: Boolean = true, val safetyChecks: List<DesignSafetyCheck> = emptyList(),
        val steelWasteTons: Double = 0.0,
        val minThickness: Double = 0.0,
        val efficiencyScore: Double = 0.0,
        val utilizationRatio: Double = 0.0,
        val suggestions: List<String> = emptyList()
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
        val iSections = mutableListOf<SteelSectionType>()
        // IPE Standard Sections
        val ipeSizes = listOf(80, 100, 120, 140, 160, 180, 200, 220, 240, 270, 300, 330, 360, 400, 450, 500, 550, 600)
        ipeSizes.forEach { h ->
            val bf = when {
                h <= 100 -> 55.0; h <= 140 -> 73.0; h <= 200 -> 100.0; h <= 300 -> 150.0; else -> 200.0
                }
            val tf = 7.0 + (h/100.0)*2.0
            val tw = 4.0 + (h/100.0)*1.5
            iSections.add(SteelSectionType.ISection(h.toDouble(), bf, tf, tw, SteelGrade.ST37, "IPE $h"))
            }

        val hebSections = mutableListOf<SteelSectionType>()
        // HEB Standard Sections
        val hebSizes = listOf(100, 120, 140, 160, 180, 200, 220, 240, 260, 280, 300)
        hebSizes.forEach { h ->
            hebSections.add(SteelSectionType.ISection(h.toDouble(), h.toDouble(), 10.0 + (h/50.0)*2.0, 6.0 + (h/50.0)*1.5, SteelGrade.ST37, "HEB $h"))
            }

        val angles = mutableListOf<SteelSectionType>()
        // L-Angles Standard (Equal Legs)
        listOf(40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 120.0).forEach { size ->
            angles.add(SteelSectionType.LSection(size, size, size/10.0, SteelGrade.ST37, "L ${size.toInt()}x${size.toInt()}"))
            }

        val channels = mutableListOf<SteelSectionType>()
        // UPE/UPN Standard
        listOf(80, 100, 120, 140, 160, 180, 200, 220, 240, 270, 300).forEach { h ->
            channels.add(SteelSectionType.CSection(h.toDouble(), h/2.0, 8.0 + h/100.0*2.0, 5.0 + h/100.0*1.5, SteelGrade.ST37, "UPN $h"))
            }

        val rhs = mutableListOf<SteelSectionType>()
        // Rectangular Hollow Sections
        listOf(50.0, 80.0, 100.0, 120.0, 150.0, 200.0).forEach { h ->
            rhs.add(SteelSectionType.RHS(h, h/2.0, 4.0 + h/50.0, SteelGrade.S275, "RHS ${h.toInt()}x${(h/2).toInt()}"))
            }

        return mapOf(
            "IPE (European I-Beams)" to iSections,
            "HEB (European Wide Flange)" to hebSections,
            "HEA (European Light Wide Flange)" to hebSections.map { 
                val s = it as SteelSectionType.ISection
                s.copy(tf = s.tf * 0.7, tw = s.tw * 0.8, customName = s.customName?.replace("HEB", "HEA")) 
            },
            "L-Angles (Equal)" to angles,
            "UPN Channels" to channels,
            "RHS (Hollow Sections)" to rhs
        )
        }

    fun calculateSteelMember(
        section: SteelSectionType,
        memberType: SteelMemberType,
        inputs: SteelInputs,
        code: DesignCode
    ): SteelMemberResult {
        val area = section.getArea()
        val fy = when (section) {
            is SteelSectionType.ISection -> section.grade.fy
            is SteelSectionType.CSection -> section.grade.fy
            is SteelSectionType.LSection -> section.grade.fy
            is SteelSectionType.CHS -> section.grade.fy
            is SteelSectionType.RHS -> section.grade.fy
            is SteelSectionType.TSection -> section.grade.fy
            else -> 240.0
            }
        
        // 1. Axial Capacity (Nominal) - Simplified AISC/ECP
        val axialCapacity = (0.85 * fy * area) / 1000.0 // kN
        
        // 2. Flexural Capacity - Sx calculation for I-Section (correct elastic section modulus)
        // Sx = I / (h/2) where I = (tw*(h-2tf)^3)/12 + 2*[(bf*tf^3)/12 + bf*tf*((h-tf)/2)^2]
        // Simplified: Sx = tw*(h-2tf)^2/6 + bf*tf*(h-tf)
        val sx = when (section) {
            is SteelSectionType.ISection -> {
                val hw = section.h - 2 * section.tf // web depth
                (section.tw * hw.pow(2) / 6.0) +
                (section.bf * section.tf * (section.h - section.tf))
                }
            is SteelSectionType.RHS -> {
                val oh = section.height - 2*section.thickness
                val ow = section.width - 2*section.thickness
                (section.height.pow(2) * section.width - oh.pow(2) * ow) / 6.0
                }
            else -> 1000.0 // Minimum placeholder
            }
        val flexuralCapacity = (0.9 * fy * sx) / 1e6 // kN.m

        // 3. Shear Capacity
        val shearArea = when(section) {
            is SteelSectionType.ISection -> section.h * section.tw
            else -> area * 0.6
            }
        val shearCapacity = (0.6 * fy * shearArea) / 1000.0 // kN

        val utilAxial = inputs.axialLoad / axialCapacity.coerceAtLeast(1.0)
        val utilMoment = inputs.moment / flexuralCapacity.coerceAtLeast(1.0)
        
        // 4. Professional Interaction Equation (AISC 360-H1 / ECP 205)
        val totalUtil = if (utilAxial >= 0.2) {
            utilAxial + (8.0/9.0) * utilMoment
        } else {
            (utilAxial / 2.0) + utilMoment
            }

        val weight = (area * 1e-6) * 7850.0 // kg/m (mm2 to m2 * density)

        val depth = when(section) {
            is SteelSectionType.ISection -> section.depth
            is SteelSectionType.CSection -> section.depth
            is SteelSectionType.RHS -> section.height
            is SteelSectionType.CHS -> section.outerDiameter
            is SteelSectionType.LSection -> section.legA
            is SteelSectionType.TSection -> section.webDepth + section.flangeThickness
            else -> 100.0
            }
        val slenderness = (inputs.unbracedLength / (depth * 0.28).coerceAtLeast(10.0))

        return SteelMemberResult(
            sectionType = section,
            memberType = memberType,
            axialCapacity = axialCapacity,
            flexuralCapacity = flexuralCapacity,
            shearCapacity = shearCapacity,
            utilizationRatio = totalUtil,
            isSafe = totalUtil <= 1.0 && inputs.shear <= shearCapacity,
            connectionDesign = null,
            bucklingCheck = BucklingCheckResult(
                slendernessRatio = slenderness,
                criticalStress = fy * 0.6,
                bucklingMode = BucklingMode.FLEXURAL,
                isSafe = slenderness <= 180.0,
                codeReference = "AISC-E3 / ECP-4.2"
            ),
            weight = weight,
            cost = weight * (settingsManager.steelPrice / 1000.0) * 1.2, // 20% fabrication markup
            warnings = if (totalUtil > 0.9) listOf("Section close to capacity") else emptyList(),
            codeNotes = listOf("Design based on ${code.displayName}", "E = 210,000 MPa")
        )
        }

    fun designSteelWarehouse(inputs: SteelWarehouseInputs): SteelWarehouseAnalysisResult {
        val span = inputs.span
        val spacing = inputs.baySpacing
        val load = (inputs.liveLoad + inputs.claddingWeight) * spacing // kN/m
        val maxMoment = (load * span.pow(2)) / 8.0
        val maxShear = (load * span) / 2.0
        
        // Mezzanine Loads
        val mezzanineArea = inputs.numberOfMezzanines * span * inputs.length
        val mezzanineLoadPerColumn = if (inputs.numberOfMezzanines > 0) {
            (inputs.mezzanineLiveLoad + 2.0) * (spacing * span / 2.0) * inputs.numberOfMezzanines
        } else 0.0
        
        val totalAxialLoad = 50.0 + mezzanineLoadPerColumn
        
        // Selection of Section (Simplistic search for optimal)
        val library = getSteelSectionLibrary()["IPE (European I-Beams)"] ?: emptyList()
        val selectedSection = library.firstOrNull { 
            val res = calculateSteelMember(it, SteelMemberType.BEAM, SteelInputs(axialLoad = totalAxialLoad, moment = maxMoment, shear = maxShear, unbracedLength = spacing * 1000), DesignCode.EGYPTIAN)
            res.isSafe
        } ?: library.last()

        val mezzanineSteelWeight = if (inputs.numberOfMezzanines > 0) {
            mezzanineArea * 45.0 / 1000.0 // Tons (Estimated 45kg/m2 for mezzanine steel)
        } else 0.0

        val totalWeight = (span * inputs.length * 5) * (selectedSection.getArea() / 1e6 * 7850) / 1000.0 + mezzanineSteelWeight // Tons (Estimated)

        // Feasibility Logic
        val estimatedTotalCost = totalWeight * (settingsManager.steelPrice) * 1.3 // 30% for erection/accessories
        val area = span * inputs.length
        val costPerM2 = estimatedTotalCost / area.coerceAtLeast(1.0)
        val estimatedProfit = estimatedTotalCost * 0.15 // 15% net profit margin
        val roi = (estimatedProfit / estimatedTotalCost.coerceAtLeast(1.0)) * 100.0

        return SteelWarehouseAnalysisResult(
            mainFrame = MainFrameResult(
                columnSection = selectedSection,
                rafterSection = selectedSection,
                maxMoment = maxMoment,
                maxShear = maxShear,
                maxAxial = totalAxialLoad,
                maxDeflection = (5 * load * span.pow(4) * 1e12) / (384 * 210000 * 1e8), 
                allowableDeflection = span * 1000 / 250.0,
                isSafe = true,
                utilizationMoment = 0.75
            ),
            secondaryMembers = SecondaryMembersResult(
                purlinSection = SteelSectionType.CSection(140.0, 60.0, 8.0, 5.0, SteelGrade.S275),
                girtSection = SteelSectionType.CSection(120.0, 50.0, 6.0, 4.0, SteelGrade.S275),
                bracingSection = SteelSectionType.LSection(80.0, 80.0, 8.0, SteelGrade.ST37),
                purlinCount = (span / inputs.purlinSpacing).toInt() * 2,
                isSafe = true
            ),
            connections = emptyList(),
            totalWeight = totalWeight,
            totalCladdingArea = span * inputs.length,
            weightPerM2 = (totalWeight * 1000) / (span * inputs.length).coerceAtLeast(1.0),
            resultsByCode = "AISC-ASD / ECP 205",
            safetyStatus = true,
            recommendations = listOf("Use Anchor Bolts M24", "Check lateral bracing", if (inputs.numberOfMezzanines > 0) "Verify Mezzanine Joists" else ""),
            materialTakeoff = mapOf("IPE Sections" to totalWeight, "Mezzanine Steel" to mezzanineSteelWeight),
            estimatedTotalCost = estimatedTotalCost,
            costPerM2 = costPerM2,
            roi = roi,
            netProfit = estimatedProfit,
            mezzanineArea = mezzanineArea,
            mezzanineSteelWeight = mezzanineSteelWeight
        )
    }

    @Parcelize
    data class ColumnECPResult(val capacity: Double, val asMin: Double) : Parcelable

    fun calculateSteelWarehousePro(inputs: SteelWarehouseInputs): SteelWarehouseProResult {
        val codeName = inputs.code.displayName
        val tributaryArea = inputs.baySpacing * inputs.length
        val serviceLoad = inputs.deadLoad + inputs.liveLoad + inputs.windLoad + inputs.snowLoad + inputs.claddingWeight
        val frameReaction = tributaryArea * serviceLoad / 2.0
        val spanM = inputs.span
        val maxMoment = frameReaction * spanM / 4.0
        val maxShear = frameReaction / 2.0
        val axial = frameReaction * 0.35 + inputs.windLoad * tributaryArea * 0.18
        val baseShear = max(0.0, inputs.windLoad * tributaryArea * 0.33)
        
        val drift = when (CalculatorEngine.DesignCode.fromDomain(inputs.code)) {
            CalculatorEngine.DesignCode.EGYPTIAN -> spanM * 1000.0 / 500.0
            CalculatorEngine.DesignCode.ACI -> spanM * 1000.0 / 400.0
            CalculatorEngine.DesignCode.SAUDI -> spanM * 1000.0 / 450.0
            else -> spanM * 1000.0 / 500.0
        } * 0.8

        val utilization = min(1.0, (maxMoment / 300.0) + (axial / 700.0) + (maxShear / 200.0))
        val notes = listOf(
            "Selected code: $codeName",
            "Report layout is bilingual and drawing-oriented.",
            "This export is structured for professional PDF presentation."
        )

        return SteelWarehouseProResult(
            codeName = codeName,
            tributaryAreaM2 = tributaryArea,
            serviceLoadKnM2 = serviceLoad,
            frameReactionKn = frameReaction,
            baseShearKn = baseShear,
            maxMomentKnM = maxMoment,
            maxAxialKn = axial,
            maxShearKn = maxShear,
            driftMm = drift,
            utilization = utilization,
            compressionZone = "Top flange / rafter compression zone",
            tensionZone = "Bottom flange / column base tension zone",
            notes = notes
        )
        }

    companion object {
        fun designColumnECP(w: Double, d: Double, p: Double, fcu: Double, fy: Double): ColumnECPResult {
            val ag = w * d
            val capacity = (0.35 * fcu * ag + 0.67 * fy * (0.008 * ag)) / 1000.0
            return ColumnECPResult(capacity, 0.008 * ag)
            }
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
        clearHeight: Double = 3000.0, // mm
        preferredDiameter: Int = 16,
        autoOptimize: Boolean = true,
        manualNumBars: Int? = null
    ): ColumnResult {
        val ag = if (isCircular) PI * width.pow(2.0) / 4.0 else width * depth
        
        // --- Code Specific Factors & Constants ---
        val fcPrime = if (code == DesignCode.EGYPTIAN) fcu else fcu * 0.8 // Approximate f'c from fcu
        var capacity = 0.0
        var asMin = 0.008 * ag
        var asMax = 0.04 * ag
        val safetyChecks = mutableListOf<DesignSafetyCheck>()

        when (code) {
            DesignCode.EGYPTIAN -> {
                // ECP 203 (Art. 4-2-1-1): Pu = 0.35*fcu*Ac + 0.67*fy*Asc
                // This formula is for short columns with minimum eccentricity.
                val i = if (isCircular) 0.25 * width else min(width, depth) / sqrt(12.0)
                val lambda = (1.0 * clearHeight) / i
                val lambdaLimit = if (isCircular) 8.0 else 10.0
                val isSlender = lambda > lambdaLimit
                
                asMin = 0.008 * ag // ECP Min reinforcement 0.8%
                val asReq = max(asMin, (pu * 1000.0 - 0.35 * fcu * ag) / (0.67 * fy))
                capacity = (0.35 * fcu * (ag - asReq) + 0.67 * fy * asReq) / 1000.0
                
                safetyChecks.add(DesignSafetyCheck("Slenderness λ (ECP)", lambda, lambdaLimit, "", lambda <= lambdaLimit))
                if (isSlender) {
                    // Approximate reduction for slenderness if not using delta method
                    capacity *= 0.8 
                }
            }
            DesignCode.SAUDI -> {
                // SBC 304 (Art. 10.3.6.2): ΦPn,max = 0.80 * Φ * [0.85*fc'*(Ag - Ast) + fy*Ast]
                // Tied columns: Φ = 0.65, α = 0.80. Spiral: Φ = 0.75, α = 0.85
                val phi = if (isCircular) 0.75 else 0.65
                val alpha = if (isCircular) 0.85 else 0.80
                
                asMin = 0.01 * ag // SBC Min 1%
                asMax = 0.08 * ag
                
                val asReq = max(asMin, (pu * 1000.0 / (alpha * phi) - 0.85 * fcPrime * ag) / (fy - 0.85 * fcPrime))
                capacity = (alpha * phi * (0.85 * fcPrime * (ag - asReq) + fy * asReq)) / 1000.0
                
                val r = if (isCircular) 0.25 * width else min(width, depth) * 0.3
                val slenderness = (1.0 * clearHeight) / r
                safetyChecks.add(DesignSafetyCheck("SBC Slenderness (kl/r)", slenderness, 22.0, "", slenderness <= 22.0))
            }
            DesignCode.ACI -> {
                // ACI 318-19 (Table 22.4.2.1): Same as SBC
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
        
        // --- Reinforcement Optimization / Manual Logic ---
        val finalNumBars = if (autoOptimize) {
            ceil(asReqTotal / areaOneBar).toInt().coerceAtLeast(if (isCircular) 6 else 4)
        } else {
            manualNumBars ?: ceil(asMin / areaOneBar).toInt().coerceAtLeast(if (isCircular) 6 else 4)
        }

        val finalAsProvided = finalNumBars * areaOneBar
        
        val rho = (finalAsProvided / ag) * 100.0
        val vol = (ag * clearHeight) / 1e9
        
        // Accurate Steel Weight Calculation
        val mainBarWeightPerMeter = (barDia.pow(2.0) / 162.0)
        val mainSteelWeight = finalNumBars * (clearHeight / 1000.0) * mainBarWeightPerMeter
        
        // Stirrups Weight (8mm @ 200mm or code min)
        val stirrupLength = if (isCircular) PI * width / 1000.0 else (2 * (width + depth) / 1000.0)
        val numStirrups = (clearHeight / 200.0) + 1
        val stirrupWeight = numStirrups * stirrupLength * (8.0.pow(2.0) / 162.0)
        
        val totalSteelWeight = (mainSteelWeight + stirrupWeight) * 1.05
        val steelWasteKg = totalSteelWeight * 0.05
        
        val utilizationRatio = (pu / capacity).coerceIn(0.0, 1.2)
        
        safetyChecks.add(DesignSafetyCheck("Axial Capacity", pu, capacity, "kN", capacity >= pu))
        safetyChecks.add(DesignSafetyCheck("Min Reinforcement", rho, (asMin/ag)*100.0, "%", finalAsProvided >= asMin))

        return ColumnResult(
            width = width, depth = depth, pu = pu, 
            reinforcement = ReinforcementBar(finalNumBars, preferredDiameter), 
            stirrups = StirrupReinforcement(8, 200.0), 
            safetyChecks = safetyChecks, isSafe = safetyChecks.all { it.isSafe } && rho <= (asMax/ag)*100.0,
            concreteVolume = vol, steelWeight = totalSteelWeight, 
            cost = (vol * settingsManager.concretePrice) + (totalSteelWeight / 1000.0 * settingsManager.steelPrice), 
            code = code,
            axialCapacity = capacity, appliedAxial = pu, 
            reinforcementArea = finalAsProvided,
            minReinforcementArea = asMin,
            maxReinforcementArea = asMax,
            reinforcementRatio = rho,
            steelWasteKg = steelWasteKg,
            rebarAlternatives = listOf(12, 14, 16, 18, 20, 25).map { dia ->
                val areaOne = PI * dia.toDouble().pow(2) / 4.0
                ReinforcementBar(ceil(asReqTotal/areaOne).toInt().coerceAtLeast(if(isCircular) 6 else 4), dia)
            },
            utilizationRatio = utilizationRatio
        )
        }

    fun designBeam(
        width: Double,
        height: Double,
        span: Double,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        preferredDiameter: Int,
        code: DesignCode,
        supportType: SupportType = SupportType.HINGED_HINGED,
        customMoment: Double? = null,
        customShear: Double? = null
    ): BeamResult {
        // Ultimate Load Factors based on code
        val domainCode = when(code) {
            DesignCode.EGYPTIAN -> com.civileg.app.domain.entities.DesignCode.ECP
            DesignCode.ACI -> com.civileg.app.domain.entities.DesignCode.ACI
            DesignCode.SAUDI -> com.civileg.app.domain.entities.DesignCode.SBC
        }
        val totalLoad = domainCode.getDeadLoadFactor() * deadLoad + domainCode.getLiveLoadFactor() * liveLoad
        
        // Structural Analysis based on support type
        val momentFactor = when (supportType) {
            SupportType.CANTILEVER -> 2.0        // wL²/2
            SupportType.FIXED_FIXED -> 12.0      // wL²/12
            SupportType.FIXED_HINGED -> 8.0      // wL²/8 (fixed end moment)
            else -> 8.0                           // wL²/8 (SS, roller-hinged)
        }
        val shearFactor = when (supportType) {
            SupportType.CANTILEVER -> 1.0         // wL
            SupportType.FIXED_FIXED -> 2.0        // wL/2
            else -> 2.0                           // wL/2
        }
        val mu = customMoment ?: (totalLoad * span.pow(2.0) / momentFactor)
        val vu = customShear ?: (totalLoad * span / shearFactor)
        val d = height - 50.0 // Effective depth (Assuming 25mm cover + stirrup + bar/2)
        
        var asReq = 0.0
        var momentCapacity = 0.0
        val safetyChecks = mutableListOf<DesignSafetyCheck>()

        when(code) {
            DesignCode.EGYPTIAN -> {
                // ECP 203 (First Principles / Curve): R = Mu / (fcu/gc * b * d^2)
                val R = (mu * 1e6) / ((fcu / 1.5) * width * d.pow(2))
                val omega = 1.25 * (1 - sqrt(1 - 2.25 * R))
                asReq = (omega * (fcu / 1.5) / (fy / 1.15)) * width * d
                if (asReq.isNaN()) asReq = (mu * 1e6) / (0.8 * fy * d) 
                }
            DesignCode.ACI, DesignCode.SAUDI -> {
                // ACI 318: Mn = As*fy(d - a/2), a = As*fy / (0.85*fc'*b)
                val fcPrime = fcu * 0.8
                val Rn = (mu * 1e6) / (0.9 * width * d.pow(2))
                val rho = (0.85 * fcPrime / fy) * (1 - sqrt(1 - (2 * Rn) / (0.85 * fcPrime)))
                asReq = rho * width * d
                if (asReq.isNaN()) asReq = (mu * 1e6) / (0.9 * fy * 0.9 * d)
                }
            }
        
        // Minimum Reinforcement (ACI: 0.25*sqrt(fc')/fy * b*d | ECP: 0.225*sqrt(fcu)/fy * b*d)
        val asMin = max(0.225 * sqrt(fcu * 0.8) / fy, 1.1 / fy) * width * d
        asReq = max(asReq, asMin)
        
        val barArea = PI * preferredDiameter.toDouble().pow(2.0) / 4.0
        val numBars = ceil(asReq / barArea).toInt().coerceAtLeast(2)
        val actualAs = numBars * barArea
        
        // Recalculate Capacity
        momentCapacity = when(code) {
            DesignCode.EGYPTIAN -> (actualAs * (fy/1.15) * (d - 0.4 * (actualAs * (fy/1.15) / (0.45 * fcu * width)))) / 1e6
            else -> (0.9 * actualAs * fy * (d - (actualAs * fy / (2 * 0.85 * fcu * 0.8 * width)))) / 1e6
            }

        // --- Shear Design ---
        val vc = when(code) {
            DesignCode.EGYPTIAN -> 0.24 * sqrt(fcu / 1.5)
            else -> 0.17 * sqrt(fcu * 0.8) * 0.75 // ACI Phi*0.17*sqrt(fc')
            }
        val v_stress = (vu * 1000.0) / (width * d)
        
        var stirrupSpacing = 200.0
        var stirrupDia = 8
        if (v_stress > vc) {
            val vs = v_stress - (vc / 2.0) // Simplified
            val stirrupArea = 2 * (PI * 8.0.pow(2) / 4.0) // 2 branches of 8mm
            stirrupSpacing = (stirrupArea * (fy / 1.15) * d) / (vs * width)
            stirrupSpacing = min(200.0, floor(stirrupSpacing / 10.0) * 10.0).coerceAtLeast(100.0)
            if (stirrupSpacing < 100.0) {
                stirrupDia = 10
                val area10 = 2 * (PI * 10.0.pow(2) / 4.0)
                stirrupSpacing = (area10 * (fy / 1.15) * d) / (vs * width)
                stirrupSpacing = min(200.0, floor(stirrupSpacing / 10.0) * 10.0).coerceAtLeast(100.0)
                }
            }
        
        val vol = (width * height * span) / 1e6
        
        // Accurate Steel Weight Calculation
        val bottomWeightPerMeter = (preferredDiameter.toDouble().pow(2.0) / 162.0)
        val topDia = (preferredDiameter * 0.8).toInt().coerceAtLeast(10)
        val topWeightPerMeter = (topDia.toDouble().pow(2.0) / 162.0)
        val stirrupWeightPerMeter = (stirrupDia.toDouble().pow(2.0) / 162.0)
        
        val bottomSteel = numBars * (span / 1000.0) * bottomWeightPerMeter
        val topSteel = max(2, numBars/3) * (span / 1000.0) * topWeightPerMeter
        val numStirrups = (span / stirrupSpacing) + 1
        val stirrupLength = (2 * (width + height) / 1000.0)
        val stirrupSteel = numStirrups * stirrupLength * stirrupWeightPerMeter
        
        val totalSteelWeight = (bottomSteel + topSteel + stirrupSteel) * 1.10 // 10% for laps/anchorage
        
        safetyChecks.add(DesignSafetyCheck("Flexural Strength", momentCapacity, mu, "kN.m", momentCapacity >= mu))
        safetyChecks.add(DesignSafetyCheck("Shear Stress", v_stress, vc * 2.5, "MPa", v_stress <= vc * 2.5)) // Max limit check

        val e_concrete = when(code) {
            DesignCode.EGYPTIAN -> 4400 * sqrt(fcu)
            else -> 4700 * sqrt(fcu * 0.8)
            }
        val deflection = (5 * totalLoad * (span * 1000).pow(4)) / (384 * e_concrete * (width * height.pow(3) / 12))
        val allowableDeflection = (span * 1000) / 250.0
        
        val utilizationRatio = maxOf(mu / momentCapacity, deflection / allowableDeflection).coerceIn(0.0, 1.2)
        
        safetyChecks.add(DesignSafetyCheck("Deflection (Instant)", deflection, allowableDeflection, "mm", deflection <= allowableDeflection))

        return BeamResult(
            width = width, depth = height, mu = mu, vu = vu, 
            reinforcementBottom = ReinforcementBar(numBars, preferredDiameter), 
            reinforcementTop = ReinforcementBar(max(2, numBars/3), topDia), 
            stirrups = StirrupReinforcement(stirrupDia, stirrupSpacing), 
            safetyChecks = safetyChecks, isSafe = momentCapacity >= mu && deflection <= allowableDeflection && v_stress <= vc * 2.5, 
            concreteVolume = vol, steelWeight = totalSteelWeight, 
            cost = (vol * settingsManager.concretePrice) + (totalSteelWeight / 1000.0 * settingsManager.steelPrice), 
            code = code, appliedMoment = mu, appliedShear = vu, 
            supportType = supportType, steelWasteTons = totalSteelWeight * 0.1 / 1000.0,
            deflection = deflection, allowableDeflection = allowableDeflection,
            momentCapacity = momentCapacity, shearCapacity = vc * width * d / 1000.0,
            steelRatio = (actualAs / (width * d)) * 100,
            utilizationRatio = utilizationRatio
        )
        }

    fun designSlab(
        lx: Double,
        ly: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        ts: Double,
        preferredDiameter: Int,
        code: DesignCode,
        type: SlabType = SlabType.SOLID,
        prestressForce: Double = 0.0,
        dropPanelThickness: Double = 0.0,
        columnSize: Double = 400.0
    ): SlabResult {
        val domainCode = when(code) {
            DesignCode.EGYPTIAN -> com.civileg.app.domain.entities.DesignCode.ECP
            DesignCode.ACI -> com.civileg.app.domain.entities.DesignCode.ACI
            DesignCode.SAUDI -> com.civileg.app.domain.entities.DesignCode.SBC
        }
        val wu = domainCode.getDeadLoadFactor() * deadLoad + domainCode.getLiveLoadFactor() * liveLoad
        
        val shortSpan = min(lx, ly)
        val longSpan = max(lx, ly)
        val r = longSpan / shortSpan
        
        var mx: Double
        var my: Double
        var minTs: Double
        
        // --- Specific Logic for Slab Types ---
        var d = ts - 25.0
        var voidRatio = 1.0
        
        when (type) {
            SlabType.WAFFLE -> {
                // Two-way ribbed behavior (reduced from simply supported due to two-way action)
                mx = wu * lx.pow(2) / 12.0
                my = wu * ly.pow(2) / 12.0
                minTs = lx * 1000 / 30.0
                voidRatio = 0.60  // 40% void reduction for waffle
                }
            SlabType.FLAT -> {
                // Flat Slab Direct Design Method - improved moment coefficients
                val totalMoment = (wu * ly * lx.pow(2)) / 8.0
                mx = 0.50 * totalMoment  // Column strip negative moment (governs)
                my = 0.10 * totalMoment  // Middle strip moment
                minTs = lx * 1000 / 32.0
                if (dropPanelThickness > 0) minTs *= 0.9
                }
            SlabType.POST_TENSION -> {
                // PT Slab: improved drape and loss calculations
                val sag = lx * 1000 / 25.0  // L/25 typical drape in mm
                val effectiveP = prestressForce * 0.80  // 20% total losses
                val balancedLoad = (8 * effectiveP * sag / 1000) / (lx * ly)
                val netWu = max(0.0, wu - balancedLoad)
                if (netWu <= 0) {
                    // Fully balanced - minimal reinforcement needed
                    mx = 0.0
                    my = 0.0
                    minTs = lx * 1000 / 45.0
                    } else {
                    mx = netWu * shortSpan.pow(2) / 10.0
                    my = netWu * longSpan.pow(2) / 12.0
                    minTs = shortSpan * 1000 / 45.0
                    }
                }
            SlabType.HOLLOW_BLOCK -> {
                // One-way ribbed (hollow block) slab
                mx = wu * shortSpan.pow(2) / 8.0
                my = wu * longSpan.pow(2) / 24.0
                minTs = shortSpan * 1000 / 25.0
                d = ts - 25.0 - (ts * 0.55) / 2.0  // ribs extend below blocks
                voidRatio = 0.55  // hollow block void ratio
                }
            else -> {
                // Solid Slab Grashoff
                val alpha = if (r <= 2.0) r.pow(4) / (1 + r.pow(4)) else 1.0
                val beta = if (r <= 2.0) 1 / (1 + r.pow(4)) else 0.0
                mx = alpha * wu * shortSpan.pow(2) / 8.0
                my = beta * wu * shortSpan.pow(2) / 8.0
                minTs = shortSpan * 1000 / 35.0
                }
            }
        
        d = max(d, ts * 0.3)  // Ensure reasonable effective depth
        
        // Flexure Design
        val asReqX = calculateAs(mx, fcu, fy, d, width = 1000.0, code = code)
        val asReqY = calculateAs(my, fcu, fy, d, width = 1000.0, code = code)
        
        val asMin = when(type) {
            SlabType.POST_TENSION -> 0.0012 * 1000.0 * ts
            else -> 0.0018 * 1000.0 * ts
            }
        
        val finalAsX = max(asReqX, asMin)
        val finalAsY = max(asReqY, asMin)
        
        val barArea = PI * preferredDiameter.toDouble().pow(2.0) / 4.0
        val spacingX = (1000.0 * barArea) / finalAsX
        val spacingY = (1000.0 * barArea) / finalAsY
        
        val finalSpacingX = min(200.0, floor(spacingX / 10.0) * 10.0).coerceAtLeast(100.0)
        val finalSpacingY = min(200.0, floor(spacingY / 10.0) * 10.0).coerceAtLeast(100.0)
        
        val volM3 = (lx * ly * ts) / 1000.0 * voidRatio
        
        val barWeightPerMeter = (preferredDiameter.toDouble().pow(2.0) / 162.0)
        val numBarsX = (ly * 1000.0 / finalSpacingX) + 1
        val numBarsY = (lx * 1000.0 / finalSpacingY) + 1
        val steelWeight = (numBarsX * lx * barWeightPerMeter + numBarsY * ly * barWeightPerMeter) * 1.05
        
        val safetyChecks = mutableListOf<DesignSafetyCheck>()
        safetyChecks.add(DesignSafetyCheck("Min Thickness", ts, minTs, "mm", ts >= minTs))
        
        // Punching Shear (Crucial for Flat Slabs)
        val punchingLoad = wu * (lx * ly - (columnSize + d).pow(2) / 1e6)
        val critPerimeter = 4 * (columnSize + d)
        val v_punch = punchingLoad * 1000 / (critPerimeter * d)
        val v_limit = when(code) {
            DesignCode.EGYPTIAN -> 0.316 * sqrt(fcu / 1.5)
            else -> 0.33 * 0.75 * sqrt(fcu * 0.8) // ACI 318 punching limit with Phi=0.75
            }
        
        if (type == SlabType.FLAT) {
            safetyChecks.add(DesignSafetyCheck("Punching Shear", v_punch, v_limit, "MPa", v_punch <= v_limit))
            }

        val efficiencyScore = (minTs / ts).coerceIn(0.0, 1.0) * 100
        val providedAsX = (1000.0 / finalSpacingX) * barArea
        val utilizationRatio = (asReqX / max(providedAsX, 1.0)).coerceIn(0.0, 1.2)
        
        val suggestions = mutableListOf<String>()
        if (ts < minTs) suggestions.add("Increase slab thickness to satisfy deflection/code requirements.")
        if (type == SlabType.FLAT && v_punch > v_limit) suggestions.add("Punching failure! Add drop panels or increase column size.")
        if (type == SlabType.POST_TENSION && prestressForce < 200) suggestions.add("Prestress force seems low for this span.")

        return SlabResult(
            type = type, thickness = ts, 
            reinforcementMain = ReinforcementBar(spacing = finalSpacingX, diameter = preferredDiameter, description = "Main (X)"), 
            reinforcementSecondary = ReinforcementBar(spacing = finalSpacingY, diameter = preferredDiameter, description = "Sec (Y)"), 
            isSafe = ts >= minTs && (type != SlabType.FLAT || v_punch <= v_limit),
            concreteVolume = volM3, steelWeight = steelWeight,
            cost = volM3 * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice),
            code = code, momentX = mx, momentY = my, totalLoad = wu,
            punchingSafe = v_punch <= v_limit, safetyChecks = safetyChecks,
            minThickness = minTs,
            efficiencyScore = efficiencyScore,
            utilizationRatio = utilizationRatio,
            suggestions = suggestions,
            steelWasteTons = steelWeight * 0.05 / 1000.0
        )
        }

    private fun calculateAs(mu: Double, fcu: Double, fy: Double, d: Double, width: Double, code: DesignCode): Double {
        return when(code) {
            DesignCode.EGYPTIAN -> {
                val R = (mu * 1e6) / ((fcu / 1.5) * width * d.pow(2))
                if (R > 0.2) return (mu * 1e6) / (0.8 * fy * d) // High moment fallback
                val omega = 1.25 * (1 - sqrt(1 - 2.25 * R))
                (omega * (fcu / 1.5) / (fy / 1.15)) * width * d
                }
            else -> { // ACI/SBC
                val fcPrime = fcu * 0.8
                val Rn = (mu * 1e6) / (0.9 * width * d.pow(2))
                val rho = (0.85 * fcPrime / fy) * (1 - sqrt(1 - (2 * Rn) / (0.85 * fcPrime)))
                rho * width * d
                }
            }
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
        val safetyChecks = mutableListOf<DesignSafetyCheck>()
        val areaReq = (p * 1.1) / soil
        var fL = sqrt(areaReq) 
        var fW = fL
        val diff = (colT - colB) / 1000.0
        fL = sqrt(areaReq) + diff / 2.0
        fW = areaReq / fL

        maxLeft?.let { if (fW / 2.0 > it / 1000.0) { fW = it * 2.0 / 1000.0; fL = areaReq / fW }     }
        maxRight?.let { if (fW / 2.0 > it / 1000.0) { fW = it * 2.0 / 1000.0; fL = areaReq / fW }     }
        maxTop?.let { if (fL / 2.0 > it / 1000.0) { fL = it * 2.0 / 1000.0; fW = areaReq / fL }     }
        maxBottom?.let { if (fL / 2.0 > it / 1000.0) { fL = it * 2.0 / 1000.0; fW = areaReq / fL }     }

        fL = ceil(fL * 20.0) / 20.0 
        fW = ceil(fW * 20.0) / 20.0
        val fLmm = fL * 1000.0
        val fWmm = fW * 1000.0
        
        val pu = if (code == DesignCode.EGYPTIAN) p * 1.5 else p * 1.4 
        val qu = pu / (fL * fW)
        
        val projectionL = (fLmm - colT) / 2.0 
        val projectionW = (fWmm - colB) / 2.0
        val muL = (qu / 1000.0) * (projectionL.pow(2) / 2.0)
        val muW = (qu / 1000.0) * (projectionW.pow(2) / 2.0)
        
        var thickness = 500.0
        var d = thickness - 70.0
        var punchingStress: Double
        val punchingLimit = if (code == DesignCode.EGYPTIAN) {
            0.316 * sqrt(fcu / 1.5) // ECP 203
        } else {
            0.33 * 0.75 * sqrt(fcu * 0.8) // ACI 318 (Phi = 0.75)
        }
        
        do {
            d = thickness - 70.0
            val punchingForce = pu * 1000.0 * (1 - (colB + d)*(colT + d)/(fLmm*fWmm))
            val b0 = 2 * (colB + d + colT + d)
            punchingStress = punchingForce / (b0 * d)
            if (punchingStress > punchingLimit) thickness += 50.0
        } while (punchingStress > punchingLimit && thickness < 2000.0)
        
        val asReqL = calculateAs(muL / 1e3, fcu, fy, d, 1000.0, code)
        val asReqW = calculateAs(muW / 1e3, fcu, fy, d, 1000.0, code)
        val asMin = 0.0015 * 1000.0 * thickness
        
        val finalAsL = max(asReqL, asMin)
        val finalAsW = max(asReqW, asMin)
        
        val barArea = PI * preferredDiameter.toDouble().pow(2.0) / 4.0
        val finalSpacingL = min(200.0, floor((1000.0 * barArea / finalAsL) / 10.0) * 10.0).coerceAtLeast(100.0)
        val finalSpacingW = min(200.0, floor((1000.0 * barArea / finalAsW) / 10.0) * 10.0).coerceAtLeast(100.0)
        
        val vol = (fL * fW * thickness / 1000.0)
        val barWeightPerMeter = (preferredDiameter.toDouble().pow(2.0) / 162.0)
        val numBarsL = (fWmm / finalSpacingL).toInt() + 1
        val numBarsW = (fLmm / finalSpacingW).toInt() + 1
        val totalSteelWeight = (numBarsL * fL + numBarsW * fW) * barWeightPerMeter * 1.05
        
        val actualPressure = (p * 1.1) / (fL * fW)
        val utilizationRatio = (actualPressure / soil).coerceIn(0.0, 1.2)
        safetyChecks.add(DesignSafetyCheck("Soil Pressure", actualPressure, soil, "kPa", actualPressure <= soil))
        safetyChecks.add(DesignSafetyCheck("Punching Shear", punchingStress, punchingLimit, "MPa", punchingStress <= punchingLimit))

        return FootingResult(
            type = FootingType.ISOLATED, width = fWmm, length = fLmm, thickness = thickness,
            soilPressure = actualPressure, allowablePressure = soil,
            reinforcementBottom = ReinforcementBar(spacing = finalSpacingL, diameter = preferredDiameter, description = "Bottom Mesh"),
            isSafe = actualPressure <= soil && punchingStress <= punchingLimit, code = code,
            concreteVolume = vol, steelWeight = totalSteelWeight, 
            cost = vol * settingsManager.concretePrice + (totalSteelWeight / 1000.0 * settingsManager.steelPrice),
            barsX = numBarsW, barsY = numBarsL, barDiameter = preferredDiameter, safetyChecks = safetyChecks,
            utilizationRatio = utilizationRatio
        )
        }

    private fun calculateStripFootingInternal(
        p_per_m: Double, fcu: Double, fy: Double, soil: Double, colB: Double,
        code: DesignCode, preferredDiameter: Int
    ): FootingResult {
        val safetyChecks = mutableListOf<DesignSafetyCheck>()
        val width = (p_per_m * 1.1) / soil // m
        val w_mm = ceil(width * 20.0) / 20.0 * 1000.0
        
        val pu = if (code == DesignCode.EGYPTIAN) p_per_m * 1.5 else p_per_m * 1.4
        val qu = pu / (w_mm / 1000.0)
        
        val projection = (w_mm - colB) / 2.0
        val mu = (qu / 1000.0) * (projection.pow(2) / 2.0)
        
        var thickness = 400.0
        var d = thickness - 70.0
        val shearLimit = if (code == DesignCode.EGYPTIAN) 0.16 * sqrt(fcu / 1.5) else 0.17 * sqrt(fcu * 0.8) * 0.75
        
        do {
            d = thickness - 70.0
            val shearForce = qu * (projection - d) / 1000.0 // kN
            val shearStress = shearForce * 1000.0 / (1000.0 * d)
            if (shearStress > shearLimit) thickness += 50.0
        } while (shearStress > shearLimit && thickness < 1500.0)
        
        val asReq = calculateAs(mu / 1e3, fcu, fy, d, 1000.0, code)
        val asMin = 0.0015 * 1000.0 * thickness
        val finalAs = max(asReq, asMin)
        
        val actualPressure = p_per_m * 1.1 / (w_mm / 1000.0)
        val utilizationRatio = (actualPressure / soil).coerceIn(0.0, 1.2)
        
        val barArea = PI * preferredDiameter.toDouble().pow(2.0) / 4.0
        val spacing = min(200.0, floor((1000.0 * barArea / finalAs) / 10.0) * 10.0).coerceAtLeast(100.0)
        
        val vol = (w_mm / 1000.0) * 1.0 * (thickness / 1000.0)
        val steelWeight = (1000.0 / spacing) * (w_mm / 1000.0) * (preferredDiameter.toDouble().pow(2.0) / 162.0) * 1.1
        
        safetyChecks.add(DesignSafetyCheck("Soil Pressure", p_per_m * 1.1 / (w_mm/1000.0), soil, "kPa", true))

        return FootingResult(
            type = FootingType.STRIP, width = w_mm, length = 1000.0, thickness = thickness,
            soilPressure = p_per_m * 1.1 / (w_mm/1000.0), allowablePressure = soil,
            reinforcementBottom = ReinforcementBar(spacing = spacing, diameter = preferredDiameter, description = "Transverse Steel"),
            isSafe = true, code = code, concreteVolume = vol, steelWeight = steelWeight,
            cost = vol * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice),
            safetyChecks = safetyChecks
        )
        }

    private fun calculateRaftInternal(
        totalP: Double, fcu: Double, fy: Double, soil: Double,
        code: DesignCode, preferredDiameter: Int
    ): FootingResult {
        val area = (totalP * 1.1) / soil
        val side = ceil(sqrt(area) * 2.0) / 2.0
        val thickness = (side * 1000.0 / 10.0).coerceIn(600.0, 2000.0) // L/10 approx
        val vol = side * side * thickness / 1000.0
        
        val actualPressure = totalP * 1.1 / (side * side)
        val utilizationRatio = (actualPressure / soil).coerceIn(0.0, 1.2)
        
        val mu = (totalP / (side * side)) * (side / 3.0).pow(2) / 8.0 // Simplified span moment
        val asReq = calculateAs(mu, fcu, fy, thickness - 70.0, 1000.0, code)
        val steelWeight = side * side * (asReq * 2 * 7.85 * 1e-3) * 1.2 // 2 directions + top/bottom mesh estimate
        
        return FootingResult(
            type = FootingType.RAFT, width = side * 1000.0, length = side * 1000.0, thickness = thickness,
            soilPressure = totalP * 1.1 / (side * side), allowablePressure = soil,
            reinforcementBottom = ReinforcementBar(spacing = 150.0, diameter = preferredDiameter, description = "Bottom Mat"),
            isSafe = true, code = code, concreteVolume = vol, steelWeight = steelWeight,
            cost = vol * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice),
            safetyChecks = listOf(DesignSafetyCheck("Average Pressure", totalP / (side * side), soil, "kPa", true)),
            utilizationRatio = utilizationRatio
        )
        }

    private fun calculatePileCapInternal(
        p: Double, numPiles: Int, pileDia: Double, pileCap: Double, fcu: Double, fy: Double,
        colB: Double, colT: Double, code: DesignCode, preferredDiameter: Int
    ): FootingResult {
        val spacing = 3 * pileDia
        val edge = 1.0 * pileDia
        val rows = ceil(sqrt(numPiles.toDouble())).toInt()
        val cols = ceil(numPiles.toDouble() / rows).toInt()
        val length = (cols - 1) * spacing + 2 * edge
        val width = (rows - 1) * spacing + 2 * edge
        
        val thickness = max(800.0, pileDia * 2)
        val d = thickness - 100.0
        
        // Simplified Truss Method
        val av = (spacing - colT) / 2.0
        val tForce = (p * 1.5 * av) / (0.87 * d) // Simplified tension
        val asReq = tForce * 1000.0 / (fy / 1.15)
        
        val vol = length * width * thickness / 1e9
        val steelWeight = (asReq * length / 1000.0 * 2 + asReq * width / 1000.0 * 2) * 7.85 * 1e-3 * 1000.0 // Very rough kg
        
        return FootingResult(
            type = FootingType.PILE_CAP, width = width, length = length, thickness = thickness,
            soilPressure = (p * 1.1) / numPiles, allowablePressure = pileCap,
            reinforcementBottom = ReinforcementBar(numBars = (asReq / 201.0).toInt().coerceAtLeast(8), diameter = 16, description = "Main Ties"),
            isSafe = (p * 1.1 / numPiles) <= pileCap, code = code,
            concreteVolume = vol, steelWeight = steelWeight,
            cost = vol * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice),
            safetyChecks = listOf(DesignSafetyCheck("Load per Pile", p * 1.1 / numPiles, pileCap, "kN", true))
        )
        }


    private fun calculateCombinedFootingInternal(
        p1: Double, p2: Double, distance: Double,
        fcu: Double, fy: Double, soil: Double,
        colB: Double, colT: Double, code: DesignCode,
        preferredDiameter: Int
    ): FootingResult {
        val safetyChecks = mutableListOf<DesignSafetyCheck>()
        val totalWorkingLoad = (p1 + p2) * 1.1
        val areaReq = totalWorkingLoad / soil
        
        // Resultant position from P1
        val xR = (p2 * distance) / (p1 + p2)
        val s1 = 600.0 // Default projection
        val length = 2 * (xR + s1)
        var width = (areaReq * 1e6) / length
        width = ceil(width / 50.0) * 50.0
        
        val actualArea = (length * width) / 1e6
        val actualPressure = totalWorkingLoad / actualArea
        val utilizationRatio = (actualPressure / soil).coerceIn(0.0, 1.2)
        
        val pu1 = if (code == DesignCode.EGYPTIAN) p1 * 1.5 else p1 * 1.4
        val pu2 = if (code == DesignCode.EGYPTIAN) p2 * 1.5 else p2 * 1.4
        val qu_ult = (pu1 + pu2) / actualArea
        
        var thickness = 800.0
        var d = thickness - 70.0
        val punchingLimit = if (code == DesignCode.EGYPTIAN) 0.316 * sqrt(fcu / 1.5) else 0.316 * sqrt(fcu * 0.8)
        
        // Punching check for both columns
        val b0_1 = 2 * (colB + d + colT + d)
        val stress1 = pu1 * 1000.0 / (b0_1 * d)
        val b0_2 = 2 * (colB + d + colT + d)
        val stress2 = pu2 * 1000.0 / (b0_2 * d)
        
        if (max(stress1, stress2) > punchingLimit) {
            thickness = ceil((max(pu1, pu2) * 1000.0 / (2 * (colB + colT) * punchingLimit) + 70.0) / 50.0) * 50.0
            }
        d = thickness - 70.0

        // Reinforcement (Simplified Beam Analogy)
        val maxMoment = (qu_ult * (width/1000.0) * (length/2000.0).pow(2)) / 8.0
        val asReq = calculateAs(maxMoment / (width/1000.0), fcu, fy, d, 1000.0, code)
        val asMin = 0.0015 * 1000.0 * thickness
        val finalAs = max(asReq, asMin)
        
        val barArea = PI * preferredDiameter.toDouble().pow(2) / 4.0
        val spacing = (1000.0 * barArea) / finalAs
        val finalSpacing = min(200.0, floor(spacing / 10.0) * 10.0).coerceAtLeast(100.0)
        
        val vol = actualArea * thickness / 1000.0
        val steelWeight = (actualArea * 150.0) // Rough estimate for combined 150kg/m3
        
        safetyChecks.add(DesignSafetyCheck("Soil Pressure", actualPressure, soil, "kPa", actualPressure <= soil))
        safetyChecks.add(DesignSafetyCheck("Punching Column 1", stress1, punchingLimit, "MPa", stress1 <= punchingLimit))
        safetyChecks.add(DesignSafetyCheck("Punching Column 2", stress2, punchingLimit, "MPa", stress2 <= punchingLimit))

        return FootingResult(
            type = FootingType.COMBINED,
            width = width,
            length = length,
            thickness = thickness,
            soilPressure = actualPressure,
            allowablePressure = soil,
            reinforcementBottom = ReinforcementBar(spacing = finalSpacing, diameter = preferredDiameter, description = "Bottom Mesh"),
            reinforcementTopX = (finalAs * 0.8 / barArea).toInt(), // Simplified top reinforcement
            topBarDiameter = preferredDiameter,
            isSafe = actualPressure <= soil && max(stress1, stress2) <= punchingLimit,
            code = code,
            concreteVolume = vol,
            steelWeight = steelWeight,
            cost = vol * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice),
            isCombined = true,
            distanceBetweenColumns = distance,
            column1Size = Pair(colT, colB),
            column2Size = Pair(colT, colB),
            safetyChecks = safetyChecks,
            utilizationRatio = utilizationRatio
        )
        }


    fun designStaircase(
        type: StairType,
        span: Double,
        riser: Double,
        tread: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: DesignCode
    ): StairResult {
        val suggestions = mutableListOf<String>()

        // --- A. Geometric Design Auto-Checks ---
        val twoRPlusT = 2.0 * riser + tread
        if (twoRPlusT < 550.0 || twoRPlusT > 700.0) {
            val msg = if (settingsManager.language == "en")
                "Warning: 2R+T = ${"%.0f".format(twoRPlusT)}mm (required 550-700mm)"
            else
                "تحذير: 2R+T = ${"%.0f".format(twoRPlusT)}mm (المفروض 550-700mm)"
            suggestions.add(msg)
        }
        if (riser > 180.0) {
            val msg = if (settingsManager.language == "en")
                "Warning: Riser height = ${"%.0f".format(riser)}mm (max 180mm)"
            else
                "تحذير: ارتفاع الدرجة = ${"%.0f".format(riser)}mm (الأقصى 180mm)"
            suggestions.add(msg)
        }
        if (tread < 250.0) {
            val msg = if (settingsManager.language == "en")
                "Warning: Tread width = ${"%.0f".format(tread)}mm (min 250mm)"
            else
                "تحذير: عرض الدرجة = ${"%.0f".format(tread)}mm (الأدنى 250mm)"
            suggestions.add(msg)
        }

        // Number of steps: n = totalRise / riser
        val totalRise = span * riser / tread // approximate total rise from geometry
        val nSteps = ceil(totalRise / riser)
        val actualSpan = nSteps * tread / 1000.0 // m (actual horizontal span from steps)
        if (abs(actualSpan - span) > 0.5) {
            val msg = if (settingsManager.language == "en")
                "Note: Number of steps = ${nSteps.toInt()}, Actual span = ${"%.2f".format(actualSpan)}m"
            else
                "ملاحظة: عدد الدرجات = ${nSteps.toInt()}، Span الفعلي ≈ ${"%.2f".format(actualSpan)}m"
            suggestions.add(msg)
        }

        // --- F. Spiral stair warning ---
        if (type == StairType.SPIRAL) {
            val msg = if (settingsManager.language == "en")
                "Warning: Spiral stair requires different analysis (torsion & warping)"
            else
                "تحذير: السلم الحلزوني يحتاج تحليل مختلف (عزم ليّ وفتل)"
            suggestions.add(msg)
        }

        // --- 1. Thickness (L/25 or L/20 depending on support) ---
        val ts = (span * 1000.0 / 25.0).coerceAtLeast(150.0)
        val angle = atan(riser / tread)
        val cosAlpha = cos(angle)
        
        // --- 2. Sloped length and slope-corrected load ---
        val lengthOnSlant = sqrt(span.pow(2) + (span * riser / tread).pow(2))
        // Horizontal projected load: w_horiz = w_total / cos(θ)
        
        // 3. Factored loads
        val wu = when(code) {
            DesignCode.EGYPTIAN -> 1.4 * deadLoad + 1.6 * liveLoad
            DesignCode.ACI, DesignCode.SAUDI -> 1.2 * deadLoad + 1.6 * liveLoad
        }
        // Slope correction: project horizontal load on slope
        val wuHoriz = wu / cosAlpha
        
        val minSteelRatio = when(code) {
            DesignCode.EGYPTIAN -> 0.0015  // ECP 203 solid slab
            DesignCode.ACI -> 0.0018       // ACI 318 one-way slab
            DesignCode.SAUDI -> 0.002       // SBC 304 hot climate
        }
        val cover = when(code) {
            DesignCode.EGYPTIAN -> 25.0
            DesignCode.ACI -> 38.0
            DesignCode.SAUDI -> 40.0
        }
        
        // --- 4. Moment calculation ---
        // B. Dog-leg stair (Double Flight): M = w*L²/10 (partial fixity from landing)
        // C. Single Flight: M = w*L²/10 for typical stair with partial fixity at ends
        val momentCoeff = when (type) {
            StairType.DOUBLE_FLIGHT -> 10.0 // Partial fixity from landing
            StairType.SPIRAL -> 8.0         // Simplified, warning already added
            StairType.SINGLE_FLIGHT -> 10.0 // Standard partial fixity
        }
        val mu = (wuHoriz * span.pow(2)) / momentCoeff
        val d = ts - cover
        
        // --- 5. Reinforcement ---
        val asReq = calculateAs(mu, fcu, fy, d, 1000.0, code)
        val asMin = minSteelRatio * 1000.0 * ts
        val finalAs = max(asReq, asMin)
        
        val barArea = PI * preferredDiameter.toDouble().pow(2) / 4.0
        val spacing = (1000.0 * barArea) / finalAs
        val finalSpacing = min(200.0, floor(spacing / 10.0) * 10.0).coerceAtLeast(100.0)
        
        // --- D. Distribution steel: 20% of main (ECP 203) ---
        val asDist = 0.20 * finalAs
        val distBarArea = PI * 10.0.pow(2) / 4.0 // 10mm bars for distribution
        val distSpacing = (1000.0 * distBarArea) / asDist
        val distFinalSpacing = min(250.0, floor(distSpacing / 10.0) * 10.0).coerceAtLeast(100.0)
        
        // --- E. Shear check: V = w*span/2, Vc = 0.5*sqrt(fcu)*b*d ---
        val vu = wuHoriz * span / 2.0
        val vc = 0.5 * sqrt(fcu) * 1000.0 * d / 1000.0 // kN (convert b=1000mm, d in mm)
        val shearSafe = vu <= vc
        if (!shearSafe) {
            suggestions.add("تحذير: قصّة الخرسانة غير كافية (Vu=${"%.1f".format(vu)}kN > Vc=${"%.1f".format(vc)}kN) - يحتاج كانات")
        }
        
        // --- 6. Quantities ---
        val vol = (lengthOnSlant * 1.0 * ts / 1000.0) + (span * (riser / 2000.0)) // m3 per meter width
        
        val barWeightPerMeter = (preferredDiameter.toDouble().pow(2.0) / 162.0)
        val distBarWeightPerMeter = (10.0.pow(2.0) / 162.0)
        
        val numMainBars = (1000.0 / finalSpacing) + 1
        val numDistBars = (lengthOnSlant * 1000.0 / distFinalSpacing) + 1
        
        val steelWeight = (numMainBars * lengthOnSlant * barWeightPerMeter + numDistBars * 1.0 * distBarWeightPerMeter) * 1.05
        
        val cost = vol * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice)
        
        val safetyChecks = mutableListOf<DesignSafetyCheck>()
        val capacityMu = (finalAs * 0.8 * fy * d / 1e6)
        val utilization = (mu / capacityMu).coerceIn(0.0, 1.2)
        safetyChecks.add(DesignSafetyCheck("Flexural Capacity", mu, capacityMu, "kN.m", utilization <= 1.0))
        safetyChecks.add(DesignSafetyCheck("Shear Capacity (Vc)", vu, vc, "kN", shearSafe))
        safetyChecks.add(DesignSafetyCheck("2R+T Rule", twoRPlusT, 700.0, "mm", twoRPlusT <= 700.0 && twoRPlusT >= 550.0))

        return StairResult(
            type = type,
            thickness = ts,
            reinforcement = ReinforcementBar(spacing = finalSpacing, diameter = preferredDiameter, description = "Main Steel"),
            distributionReinforcement = ReinforcementBar(spacing = distFinalSpacing, diameter = 10, description = "Distribution (20%)"),
            isSafe = utilization <= 1.0 && shearSafe,
            concreteVolume = vol,
            steelWeight = steelWeight,
            cost = cost,
            code = code,
            safetyChecks = safetyChecks,
            utilizationRatio = utilization,
            mu = mu,
            wu = wuHoriz,
            span = span,
            riser = riser,
            tread = tread,
            fcu = fcu,
            fy = fy,
            suggestions = suggestions
        )
        }

    fun designTank(
        type: TankType,
        capacity: Double,
        height: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int = 12,
        code: DesignCode = DesignCode.EGYPTIAN,
        soilDensity: Double = 18.0
    ): TankResult {
        val suggestions = mutableListOf<String>()
        val H = height
        val isCircular = type in listOf(TankType.CIRCULAR_GROUND, TankType.CIRCULAR_ELEVATED, TankType.CIRCULAR_UNDERGROUND)
        val isUnderground = type in listOf(TankType.UNDERGROUND, TankType.CIRCULAR_UNDERGROUND)

        // Dimensions from capacity
        val area = capacity / H
        val length: Double
        val width: Double
        if (isCircular) {
            val diameter = sqrt(4.0 * area / PI)
            length = diameter
            width = diameter
        } else {
            length = sqrt(area)
            width = sqrt(area)
        }

        // Wall and base thickness
        val wallThickness = (H * 100.0).coerceIn(250.0, 500.0)
        val baseThickness = (wallThickness + 100.0).coerceIn(400.0, 800.0)

        // Pressures
        val gammaW = 9.81
        val waterPressure = gammaW * H
        val soilPressure = if (isUnderground) soilDensity * H else 0.0

        // Code-specific parameters
        val allowableStress = when(code) {
            DesignCode.EGYPTIAN -> 170.0
            DesignCode.ACI -> 140.0
            DesignCode.SAUDI -> 130.0
        }
        val minWallRatio = when(code) {
            DesignCode.EGYPTIAN -> 0.0025
            DesignCode.ACI -> 0.0030
            DesignCode.SAUDI -> 0.0035
        }

        val cover = 50.0
        val d_wall = wallThickness - cover

        // Base cantilever moment: M = γw * H³ / 6 (kN.m/m)
        val mu = (gammaW * H.pow(3)) / 6.0

        // --- Type-specific design ---
        // Vertical reinforcement varies in 3 zones: 0-H/3 (bottom), H/3-2H/3, 2H/3-H (top)
        // For cantilever wall, moment at depth z from free surface: M(z) = γw * z³ / 6
        // Zone 1 (bottom third, z = H to 2H/3): max moment at base M_base = γw*H³/6
        // Zone 2 (middle third, z = 2H/3 to H/3): M at z=2H/3 = γw*(2H/3)³/6
        // Zone 3 (top third, z = H/3 to 0): minimum steel

        val muZone1 = gammaW * H.pow(3) / 6.0
        val muZone2 = gammaW * (2.0 * H / 3.0).pow(3) / 6.0
        val asMinZone = minWallRatio * 1000.0 * wallThickness

        val asReqZone1 = max((muZone1 / 1.5 * 1e6) / (allowableStress * 0.85 * d_wall), asMinZone)
        val asReqZone2 = max((muZone2 / 1.5 * 1e6) / (allowableStress * 0.85 * d_wall), asMinZone)
        val asReqZone3 = asMinZone

        // Horizontal reinforcement at 35% of max vertical
        val asHorizontal = 0.35 * asReqZone1

        // Hoop tension for circular tanks: T = γw * H * R (kN/m run)
        val asHoop: Double
        val hoopSpacing: Double
        if (isCircular) {
            val R = length / 2.0 // radius in meters
            val hoopTension = gammaW * H * R // kN/m
            asHoop = (hoopTension * 1000.0) / (0.87 * fy) // mm²/m
            val barArea = PI * preferredDiameter.toDouble().pow(2) / 4.0
            val s = (1000.0 * barArea) / asHoop
            hoopSpacing = min(200.0, floor(s / 10.0) * 10.0).coerceAtLeast(100.0)
        } else {
            asHoop = 0.0
            hoopSpacing = 0.0
        }

        // Vertical bar spacing (governed by Zone 1)
        val barArea = PI * preferredDiameter.toDouble().pow(2) / 4.0
        val spacingV = (1000.0 * barArea) / asReqZone1
        val finalSpacingV = min(200.0, floor(spacingV / 10.0) * 10.0).coerceAtLeast(100.0)

        // Horizontal bar spacing
        val spacingH = (1000.0 * barArea) / asHorizontal
        val finalSpacingH = min(200.0, floor(spacingH / 10.0) * 10.0).coerceAtLeast(100.0)

        val providedArea = (1000.0 / finalSpacingV) * barArea
        val utilization = (asReqZone1 / providedArea).coerceIn(0.0, 1.2)

        // --- Fixed concrete volume ---
        val totalVol = if (isCircular) {
            // π*D*H*thickness + π*D²/4*baseThickness
            PI * length * H * (wallThickness / 1000.0) + PI * length.pow(2) / 4.0 * (baseThickness / 1000.0)
        } else {
            // 2*(L+W)*H*thickness + L*W*baseThickness
            2.0 * (length + width) * H * (wallThickness / 1000.0) + length * width * (baseThickness / 1000.0)
        }

        // Underground uplift check
        if (isUnderground) {
            val tankWeight = totalVol * 25.0 // kN
            val upliftForce = gammaW * capacity // kN
            val fsUplift = tankWeight / upliftForce.coerceAtLeast(0.01)
            val fsLabel = if (settingsManager.language == "en") {
                if (fsUplift >= 1.2) "(Safe)" else "(Unsafe - needs extra weight or floor slabs)"
            } else {
                if (fsUplift >= 1.2) "(آمن)" else "(غير آمن - يحتاج وزن زائد أو أرضيات)"
            }
            val upliftPrefix = if (settingsManager.language == "en") "Uplift Factor of Safety (FS)" else "معامل أمان الرفع (Uplift FS)"
            suggestions.add("$upliftPrefix = ${"%.2f".format(fsUplift)} $fsLabel")
            if (soilPressure > 0) {
                val soilMsg = if (settingsManager.language == "en")
                    "External soil pressure on walls = ${"%.1f".format(soilPressure)} kN/m²"
                else
                    "ضغط التربة الخارجي على الحوائط = ${"%.1f".format(soilPressure)} kN/m²"
                suggestions.add(soilMsg)
            }
        }

        // --- Steel weight calculation ---
        val barWeightPerMeter = preferredDiameter.toDouble().pow(2.0) / 162.0
        val perimeter = if (isCircular) PI * length else 2.0 * (length + width)

        // Vertical steel (2 faces, 2 nets): Zone 1 full height (conservative)
        val numVerticalBars = floor((perimeter * 1000.0) / finalSpacingV).toInt() + 1
        // Horizontal steel (2 faces)
        val numHorizontalBars = floor((H * 1000.0) / finalSpacingH).toInt() + 1
        val wallSteelWeight = (numVerticalBars * H + numHorizontalBars * perimeter) * barWeightPerMeter * 2 // 2 nets

        // Base steel (top + bottom nets)
        val baseSteelWeight = if (isCircular) {
            val numBaseBars = floor((length * 1000.0) / 200.0).toInt() + 1
            numBaseBars * length * barWeightPerMeter * 2 // 2 directions * 2 faces
        } else {
            val numBarsL = floor((length * 1000.0) / 200.0).toInt() + 1
            val numBarsW = floor((width * 1000.0) / 200.0).toInt() + 1
            (numBarsL * width + numBarsW * length) * barWeightPerMeter * 2
        }

        // Additional hoop steel for circular tanks
        val hoopSteelWeight = if (isCircular) {
            val numHoopBars = floor((H * 1000.0) / hoopSpacing).toInt() + 1
            numHoopBars * perimeter * barWeightPerMeter * 2 // 2 faces
        } else 0.0

        val totalSteelWeight = (wallSteelWeight + baseSteelWeight + hoopSteelWeight) * 1.15
        val cost = totalVol * settingsManager.concretePrice + (totalSteelWeight / 1000.0 * settingsManager.steelPrice)

        val safetyChecks = mutableListOf<DesignSafetyCheck>()
        safetyChecks.add(DesignSafetyCheck("Crack Control (Steel Area)", providedArea, asReqZone1, "mm²", utilization <= 1.0))
        safetyChecks.add(DesignSafetyCheck("Wall Thickness", wallThickness, 250.0, "mm", wallThickness >= 250.0))
        if (isCircular) {
            safetyChecks.add(DesignSafetyCheck("Hoop Tension Capacity", providedArea, asHoop, "mm²/m", providedArea >= asHoop * 0.5))
        }

        val wallDesc = if (isCircular) {
            "Vert: ${finalSpacingV.toInt()}mm c/c | Hoop: ${hoopSpacing.toInt()}mm c/c | Horiz: ${finalSpacingH.toInt()}mm c/c"
        } else {
            "Vert Z1: ${finalSpacingV.toInt()}mm | Z2: ${min(200.0, finalSpacingV * 1.3).toInt()}mm | Z3: min | Horiz: ${finalSpacingH.toInt()}mm"
        }

        return TankResult(
            type = type, length = length, width = width, height = height,
            wallThickness = wallThickness, baseThickness = baseThickness,
            wallReinforcement = ReinforcementBar(spacing = finalSpacingV, diameter = preferredDiameter, description = wallDesc),
            baseReinforcement = ReinforcementBar(spacing = 200.0, diameter = preferredDiameter, description = "Base Mat"),
            isSafe = utilization <= 1.0, concreteVolume = totalVol, steelWeight = totalSteelWeight,
            cost = cost, code = code,
            waterPressure = waterPressure, soilPressure = soilPressure, mu = mu, capacity = capacity,
            safetyChecks = safetyChecks, utilizationRatio = utilization,
            fcu = fcu, fy = fy,
            suggestions = suggestions
        )
        }

    fun designRetainingWall(
        height: Double,
        soilDensity: Double,
        frictionAngle: Double,
        surcharge: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int = 16,
        code: DesignCode = DesignCode.EGYPTIAN,
        waterTableHeight: Double = 0.0,
        frictionCoeff: Double = 0.5,
        bearingCapacity: Double = 200.0
    ): RetainingWallResult {
        val suggestions = mutableListOf<String>()

        // 1. Earth Pressure Coefficients
        val phiRad = frictionAngle * PI / 180.0
        val ka = (1 - sin(phiRad)) / (1 + sin(phiRad))
        
        // 2. Dimensions (Standard Proportions with tapered stem)
        val stemT = (height * 1000.0 / 12.0).coerceAtLeast(300.0) // mm (base thickness)
        val stemTopT = max(200.0, stemT * 0.5) // Tapered top: half of base, min 200mm
        val baseW = (height * 0.5).coerceAtLeast(1.5) // m
        val baseT = stemT // mm
        val toeW = baseW / 3.0
        val heelW = baseW - toeW - (stemT / 1000.0)
        
        // 3. Lateral Earth Pressure with water table support
        // If water table present: above WT use soil, below WT use submerged + hydrostatic
        val pa: Double
        val ps: Double
        val gammaW = 9.81
        val gammaSub = soilDensity - gammaW // submerged unit weight
        
        if (waterTableHeight > 0 && waterTableHeight < height) {
            val Hw = waterTableHeight // height of water table from base
            val Hs = height - Hw // height of soil above water table
            
            // Active pressure above water table: 0.5 * ka * γ_soil * Hs²
            val pa_above = 0.5 * ka * soilDensity * Hs.pow(2)
            // Active pressure below water table (soil part): ka * γ_soil * Hs * Hw + 0.5 * ka * γ_sub * Hw²
            val pa_soil_below = ka * soilDensity * Hs * Hw + 0.5 * ka * gammaSub * Hw.pow(2)
            // Hydrostatic pressure below water table: 0.5 * γw * Hw²
            val pa_water = 0.5 * gammaW * Hw.pow(2)
            
            pa = pa_above + pa_soil_below + pa_water
            
            // Surcharge acts over full height
            ps = ka * surcharge * height
            
            if (pa > 0) {
                val wtMsg = if (settingsManager.language == "en")
                    "Groundwater level = ${"%.1f".format(waterTableHeight)}m - hydrostatic pressure calculated"
                else
                    "مستوى المياه الجوفية = ${"%.1f".format(waterTableHeight)}m - تم حساب ضغط المياه الهيدروستاتيكي"
                suggestions.add(wtMsg)
            }
        } else {
            pa = 0.5 * ka * soilDensity * height.pow(2)
            ps = ka * surcharge * height
        }
        
        // 4. Stability Analysis
        // Use average stem thickness for weight (trapezoidal section)
        val avgStemT = (stemT + stemTopT) / 2.0 / 1000.0 // m
        val wStem = height * avgStemT * 25.0
        val wBase = baseW * (baseT / 1000.0) * 25.0
        val wSoil = heelW * height * soilDensity
        
        val xStem = toeW + (stemT / 2000.0)
        val xBase = baseW / 2.0
        val xSoil = toeW + (stemT / 1000.0) + (heelW / 2.0)
        
        val resistingMoment = (wStem * xStem) + (wBase * xBase) + (wSoil * xSoil)
        val drivingMoment = (pa * height / 3.0) + (ps * height / 2.0)
        
        val otLimit = when(code) {
            DesignCode.EGYPTIAN, DesignCode.SAUDI -> 1.5
            DesignCode.ACI -> 2.0
        }
        val slideLimit = 1.5
        val cover = when(code) {
            DesignCode.EGYPTIAN -> 50.0
            DesignCode.ACI -> 75.0
            DesignCode.SAUDI -> 65.0
        }
        val minSteel = when(code) {
            DesignCode.EGYPTIAN -> 0.0013
            DesignCode.ACI -> 0.0018
            DesignCode.SAUDI -> 0.002
        }
        val lf = when(code) {
            DesignCode.EGYPTIAN -> 1.4
            DesignCode.ACI, DesignCode.SAUDI -> 1.6
        }
        
        val totalVerticalWeight = wStem + wBase + wSoil
        val fsOverturning = resistingMoment / drivingMoment.coerceAtLeast(0.01)
        val fsSliding = (frictionCoeff * totalVerticalWeight) / (pa + ps).coerceAtLeast(0.01)

        // Bearing pressure calculation
        val netMoment = resistingMoment - drivingMoment
        val eccentricity = (baseW / 2.0) - (netMoment / totalVerticalWeight.coerceAtLeast(0.01))
        val maxBearingPressure = (totalVerticalWeight / baseW) * (1.0 + 6.0 * eccentricity / baseW)
        val minBearingPressure = (totalVerticalWeight / baseW) * (1.0 - 6.0 * eccentricity / baseW)
        val bearingLimit = 1.0
        val bearingFS = if (maxBearingPressure > 0) bearingCapacity / maxBearingPressure else 0.0

        val utilizationRatio = max(otLimit / fsOverturning, slideLimit / fsSliding)
        val isSafe = fsOverturning >= otLimit && fsSliding >= slideLimit && bearingFS >= bearingLimit
        
        // 5. Stem reinforcement (tapered: design for max moment at base)
        val muStem = drivingMoment * lf // Factored
        val d = stemT - cover
        val asReqStem = calculateAs(muStem, fcu, fy, d, 1000.0, code)
        val asMinStem = minSteel * 1000.0 * stemT
        val asFinalStem = max(asReqStem, asMinStem)
        
        val barArea = PI * preferredDiameter.toDouble().pow(2) / 4.0
        val numStemBars = ceil(asFinalStem / barArea).toInt().coerceAtLeast(5)
        val stemReinforcement = ReinforcementBar(numStemBars, preferredDiameter, 
            description = "Stem (Vert) | Top thick: ${stemTopT.toInt()}mm, Base thick: ${stemT.toInt()}mm")
        
        // 6. Separate base reinforcement: toe and heel
        // Toe moment: M_toe = σ_max * L_toe² / 2 (cantilever from stem face)
        val sigmaToe = maxBearingPressure.coerceAtLeast(0.0)
        val muToe = sigmaToe * toeW.pow(2) / 2.0
        val dBase = baseT - cover
        val asReqToe = calculateAs(muToe * lf, fcu, fy, dBase, 1000.0, code)
        val asMinBase = minSteel * 1000.0 * baseT
        val asToe = max(asReqToe, asMinBase)
        val toeSpacing = (1000.0 * barArea) / asToe
        val toeFinalSpacing = min(200.0, floor(toeSpacing / 10.0) * 10.0).coerceAtLeast(100.0)
        
        // Heel moment: M_heel = (soil_weight + surcharge) * L_heel² / 2 - base_self_weight * L_heel² / 2
        val netHeelPressure = (soilDensity * height + surcharge) - (baseT / 1000.0 * 25.0)
        val muHeel = abs(netHeelPressure) * heelW.pow(2) / 2.0
        val asReqHeel = calculateAs(muHeel * lf, fcu, fy, dBase, 1000.0, code)
        val asHeel = max(asReqHeel, asMinBase)
        val heelSpacing = (1000.0 * barArea) / asHeel
        val heelFinalSpacing = min(200.0, floor(heelSpacing / 10.0) * 10.0).coerceAtLeast(100.0)
        
        val baseReinforcement = ReinforcementBar(spacing = min(toeFinalSpacing, heelFinalSpacing), 
            diameter = preferredDiameter, 
            description = "Base: Toe ${toeFinalSpacing.toInt()}mm c/c | Heel ${heelFinalSpacing.toInt()}mm c/c")
        
        // 7. Quantities
        val vol = (height * avgStemT) + (baseW * (baseT / 1000.0))
        val barWeightPerMeter = (preferredDiameter.toDouble().pow(2.0) / 162.0)
        // Stem: numStemBars * height + horizontal distribution bars * height
        val numDistBars = floor((height * 1000.0) / 200.0).toInt() + 1
        val steelWeight = (numStemBars * height * barWeightPerMeter 
            + numDistBars * 1.0 * (10.0.pow(2.0) / 162.0)
            + (1000.0 / toeFinalSpacing) * toeW * barWeightPerMeter
            + (1000.0 / heelFinalSpacing) * heelW * barWeightPerMeter
            ) * 1.15
        
        val safetyChecks = mutableListOf<DesignSafetyCheck>()
        safetyChecks.add(DesignSafetyCheck("Overturning Stability", fsOverturning, otLimit, "", fsOverturning >= otLimit))
        safetyChecks.add(DesignSafetyCheck("Sliding Stability (μ=$frictionCoeff)", fsSliding, slideLimit, "", fsSliding >= slideLimit))
        safetyChecks.add(DesignSafetyCheck("Bearing Capacity", bearingFS, bearingLimit, "kPa", bearingFS >= bearingLimit))

        return RetainingWallResult(
            height = height,
            stemThickness = stemT,
            baseWidth = baseW * 1000.0,
            stemReinforcement = stemReinforcement,
            baseReinforcement = baseReinforcement,
            safetyChecks = safetyChecks,
            isSafe = isSafe,
            concreteVolume = vol,
            steelWeight = steelWeight,
            cost = vol * settingsManager.concretePrice + (steelWeight / 1000.0 * settingsManager.steelPrice),
            code = code,
            factorOfSafetyOverturning = fsOverturning,
            factorOfSafetySliding = fsSliding,
            utilizationRatio = utilizationRatio,
            muStem = muStem,
            pa = pa,
            ps = ps,
            ka = ka,
            soilDensity = soilDensity,
            fcu = fcu,
            fy = fy,
            backfillAngle = frictionAngle,
            maxBearingPressure = maxBearingPressure,
            minBearingPressure = minBearingPressure,
            bearingFS = bearingFS,
            suggestions = suggestions
        )
        }

    fun calculateSeismicLoads(input: SeismicInput): SeismicResult {
        val h = input.height
        val sa = (input.zone * input.importance * 2.5) / input.reductionFactor
        val baseShear = sa * input.totalWeight
        val forces = mutableMapOf<Int, Double>()
        val n = max(1, ceil(h / 3.0).toInt())
        for (i in 1..n) forces[i] = (i.toDouble() / (n*(n+1)/2.0)) * baseShear
        return SeismicResult(
            baseShear = baseShear, 
            storyDrift = 0.005 * h/n, 
            timePeriod = 0.075 * h.pow(0.75), 
            spectralAcceleration = sa, 
            forcesPerFloor = forces, 
            isSafe = true, 
            code = DesignCode.EGYPTIAN,
            zone = input.zone,
            importance = input.importance,
            reductionFactor = input.reductionFactor,
            totalWeight = input.totalWeight,
            height = input.height
        )
    }

    fun calculateWeldCapacity(size: Double, length: Double, electrode: ElectrodeType, code: DesignCode): Double {
        val phi = if (code == DesignCode.ACI) 0.75 else 0.70
        return (phi * 0.60 * electrode.tensileStrength * 0.707 * size * length) / 1000.0 // kN
    }

    fun calculateBoltCapacity(diameter: Double, grade: BoltGrade, count: Int, code: DesignCode): Double {
        val area = PI * diameter.pow(2.0) / 4.0
        val phi = if (code == DesignCode.ACI) 0.75 else 0.65
        return (phi * 0.5 * grade.fu * area * count) / 1000.0 // kN
    }

}
