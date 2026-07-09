package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطات المتقدم حسب الكود السعودي SBC 304-2018
 * Advanced Slab Design per Saudi Building Code 304-2018
 *
 * SBC 304 is based on ACI 318 with Saudi-specific modifications:
 * - f'c = 0.67 x fcu / gamma_c (gamma_c = 1.5) — NOT 0.8 x fcu like ACI
 * - Enhanced cover requirements for hot/arid climate
 * - Saudi market bar diameters: 14, 16, 20, 25, 32 mm
 * - Seismic requirements per SBC 304 Section 21
 * - Hot climate crack width and deflection factors
 *
 * المراجع:
 * - SBC 304-2018 البند 8 (البلاطات)
 * - SBC 304-2018 البند 8.10 (طريقة التصميم المباشر)
 * - SBC 304-2018 البند 8.4 (البلاطات المقلوبة/البردورية)
 * - SBC 304-2018 البند 21 (متطلبات الزلازل)
 * - SBC 304-2018 البند 8.6 (الثقب)
 * - SBC 304-2018 الجدول 8.10.2.2 (توزيع العزوم)
 */
class SBCAdvancedSlab {

    companion object {
        // SBC 304 Strength Reduction Factors (Bond 9.3)
        private const val PHI_FLEXURE = 0.90
        private const val PHI_SHEAR = 0.75

        // SBC 304-8.4: Minimum reinforcement ratio (equals ACI 7.6.1.1)
        private const val MIN_REIN_RATIO = 0.0018

        // SBC 304 Material Conversion: f'c = 0.67 x fcu / gamma_c
        private const val FCU_TO_FC_PRIME_FACTOR = 0.67 / 1.5  // = 0.4467
        private const val GAMMA_C = 1.5

        // SBC 304 Cover requirements (mm) — البند 8.2, Table 8.2
        private const val COVER_NORMAL = 50.0      // Normal exposure (interior)
        private const val COVER_COASTAL = 65.0     // Corrosive / coastal zones
        private const val COVER_SEVERE = 75.0      // Severe / chemical exposure

        // Saudi market bar diameters (mm) — standard in Saudi construction
        val SAUDI_BAR_DIAMETERS = listOf(14.0, 16.0, 20.0, 25.0, 32.0)

        // Hot climate factors — SBC modification for Saudi environment
        private const val HOT_CLIMATE_DEFLECTION_FACTOR = 1.15    // 15% increase for temp
        private const val HOT_CLIMATE_CRACK_WIDTH_FACTOR = 1.25  // 25% increase for thermal cycling
        private const val HOT_CLIMATE_SHRINKAGE_FACTOR = 1.20    // 20% increase for shrinkage

        // SBC 304 Seismic (Section 21) minimum reinforcement ratios
        private const val SEISMIC_MIN_REIN_RATIO = 0.0025         // SBC 304-21.5.2
        private const val SEISMIC_MAX_BAR_SPACING = 200.0         // mm, SBC 304-21.5.3

        // SBC 304 Flat plate minimum thickness (mm) — Table 8.3.1.1
        private const val FLAT_PLATE_MIN_THICKNESS = 200.0
        private const val RIB_MIN_WIDTH = 100.0                   // mm, SBC 304-8.12
        private const val RIB_MAX_SPACING = 900.0                 // mm center-to-center
        private const val TOPPING_MIN_THICKNESS = 50.0            // mm

        // SBC 304-8.6: Punching shear
        private const val BETA_1_DEFAULT = 0.85                   // for fc' <= 28 MPa

        // SBC Fire resistance — minimum thickness for 1hr/2hr fire rating
        private const val FIRE_1HR_MIN_THICKNESS = 150.0
        private const val FIRE_2HR_MIN_THICKNESS = 200.0

        // SBC 304 Section 21 seismic zone classifications (Saudi)
        private const val SEISMIC_ZONE_A_FACTOR = 0.10  // Low seismicity
        private const val SEISMIC_ZONE_B_FACTOR = 0.20  // Moderate
        private const val SEISMIC_ZONE_C_FACTOR = 0.30  // High
        private const val SEISMIC_ZONE_D_FACTOR = 0.40  // Very high (western Saudi)
    }

    /**
     * Exposure classification for SBC 304 cover determination
     * تصنيف التعرض لتحديد الغطاء الخرساني حسب SBC 304
     */
    enum class ExposureClass(val displayNameAr: String, val displayNameEn: String, val cover: Double) {
        NORMAL("عادي", "Normal (Interior)", COVER_NORMAL),
        COASTAL("ساحلي", "Coastal / Corrosive", COVER_COASTAL),
        SEVERE("شديد", "Severe / Chemical", COVER_SEVERE);

        companion object {
            fun fromString(value: String): ExposureClass =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. FLAT PLATE DESIGN — SBC 304 §8
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * تصميم البلاطة المسطحة (Flat Plate) حسب SBC 304 البند 8.10
     * Direct Design Method with column strip / middle strip moment distribution
     *
     * @param fcu Cube compressive strength (MPa)
     * @param fy Steel yield strength (MPa)
     * @param slabThickness Total slab thickness (mm)
     * @param panelLength Long panel dimension (mm)
     * @param panelWidth Short panel dimension (mm)
     * @param columnSize Column side dimension (mm) — assumed square
     * @param totalLoad Factored total load (kN/m²)
     * @param exposure Exposure class for cover determination
     * @param isSeismic Whether seismic provisions apply (SBC §21)
     * @param fireRatingHours Fire resistance in hours (0 = not required)
     */
    fun designFlatPlate(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        panelLength: Double,
        panelWidth: Double,
        columnSize: Double,
        totalLoad: Double,
        exposure: ExposureClass = ExposureClass.NORMAL,
        isSeismic: Boolean = false,
        fireRatingHours: Double = 0.0
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        codeNotes.add("SBC 304-2018: Flat Plate Design (Direct Design Method)")
        codeNotes.add("Reference: SBC 304 §8.10, Table 8.10.2.2")

        // SBC material conversion
        val fcPrime = FCU_TO_FC_PRIME_FACTOR * fcu
        codeNotes.add(String.format("fcu=%.0f MPa → fc'=%.1f MPa (SBC: 0.67×fcu/γc, γc=1.5)", fcu, fcPrime))

        val cover = exposure.cover
        val effectiveDepth = slabThickness - cover - 8.0  // cover + bar radius (estimated)
        codeNotes.add(String.format("Cover=%.0f mm (%s), d=%.0f mm", cover, exposure.displayNameEn, effectiveDepth))

        // Panel geometry in meters
        val lx = min(panelLength, panelWidth) / 1000.0
        val ly = max(panelLength, panelWidth) / 1000.0
        val c = columnSize / 1000.0  // column size in m
        val ratio = ly / lx

        codeNotes.add(String.format("Panel: %.1f × %.1f m, ly/lx=%.2f", ly, lx, ratio))

        // SBC 304 Table 8.10.2.2 — Total static moment: Mo = wu * lx² * ly / 8
        val totalStaticMoment = totalLoad * lx * lx * ly / 8.0  // kN.m
        codeNotes.add(String.format("Mo = wu×lx²×ly/8 = %.1f kN.m", totalStaticMoment))

        // Column strip width = lx/2 on each side, but not exceeding ly/4
        val columnStripWidth = min(lx / 2.0, ly / 4.0)
        val middleStripWidth = lx - columnStripWidth

        // Moment distribution coefficients — SBC 304 Table 8.10.2.2
        // Interior panel (fully continuous)
        val columnStripNegFactor = 0.65   // Negative moment at column face
        val columnStripPosFactor = 0.35   // Positive moment at midspan
        val middleStripNegFactor = 0.25   // Remainder of negative to middle strip
        val middleStripPosFactor = 0.25   // Remainder of positive to middle strip

        // Column strip moments (kN.m)
        val colNegMoment = totalStaticMoment * columnStripNegFactor
        val colPosMoment = totalStaticMoment * columnStripPosFactor
        // Middle strip moments
        val midNegMoment = totalStaticMoment * middleStripNegFactor
        val midPosMoment = totalStaticMoment * middleStripPosFactor

        codeNotes.add(String.format("Column strip: M-=%.1f kN.m, M+=%.1f kN.m", colNegMoment, colPosMoment))
        codeNotes.add(String.format("Middle strip: M-=%.1f kN.m, M+=%.1f kN.m", midNegMoment, midPosMoment))

        // Design column strip (controls) — use the larger moment (negative)
        val colDesignMoment = max(colNegMoment, colPosMoment)  // kN.m
        val colMomentPerMeter = colDesignMoment / (columnStripWidth * 1000.0)  // kN.m/m

        val columnStripResult = designFlexureSection(
            fcPrime = fcPrime,
            fy = fy,
            momentPerMeter = colMomentPerMeter,
            effectiveDepth = effectiveDepth,
            slabThickness = slabThickness,
            isSeismic = isSeismic,
            warnings = warnings,
            codeNotes = codeNotes,
            codeRef = "SBC 304 §8.10"
        )

        // Design middle strip (use the larger moment)
        val midDesignMoment = max(midNegMoment, midPosMoment)
        val midMomentPerMeter = midDesignMoment / (middleStripWidth * 1000.0)

        val middleStripResult = designFlexureSection(
            fcPrime = fcPrime,
            fy = fy,
            momentPerMeter = midMomentPerMeter,
            effectiveDepth = effectiveDepth,
            slabThickness = slabThickness,
            isSeismic = isSeismic,
            warnings = warnings,
            codeNotes = codeNotes,
            codeRef = "SBC 304 §8.10"
        )

        // Punching shear check — SBC 304 §8.6
        val punchingCheck = checkPunchingShear(
            fcPrime = fcPrime,
            columnSize = columnSize,
            effectiveDepth = effectiveDepth,
            totalLoad = totalLoad,
            panelLength = panelLength,
            panelWidth = panelWidth,
            dropPanelWidth = 0.0,  // flat plate has no drop panel
            codeRef = "SBC 304 §8.6"
        )

        if (!punchingCheck.isSafe) {
            warnings.add("SBC §8.6: Punching shear NOT satisfied — consider drop panels or shear reinforcement")
        }

        // One-way shear check (at face of column)
        val shearWidth = ly * 1000.0  // mm
        val Vu = totalLoad * lx * ly * 1000.0 - totalLoad * c * c * 1000.0  // N
        val Vc = PHI_SHEAR * 0.17 * sqrt(fcPrime) * shearWidth * effectiveDepth / 1000.0  // kN
        val shearCheck = ShearCheckResult(
            appliedShear = Vu / 1000.0,
            shearCapacity = Vc,
            isSafe = Vu / 1000.0 <= Vc,
            utilizationRatio = (Vu / 1000.0) / Vc.coerceAtLeast(1.0),
            criticalSection = effectiveDepth / 2.0,
            criticalPerimeter = 2.0 * (columnSize + effectiveDepth),
            warnings = if (Vu / 1000.0 > Vc) listOf("One-way shear exceeded") else emptyList()
        )

        // Deflection check — SBC 304 Table 8.3.1.1
        val minThicknessSBC = max(FLAT_PLATE_MIN_THICKNESS, getSBCMinFlatPlateThickness(lx * 1000.0, ly * 1000.0, fy))
        val deflectionCheck = DeflectionCheckResult(
            immediateDeflection = 0.0,
            longTermDeflection = 0.0,
            calculatedDeflection = (5.0 * totalLoad * (lx * 1000.0).pow(4)) /
                    (384.0 * 25000.0 * 1000.0 * (slabThickness / 12.0).pow(3) * 1000.0) * HOT_CLIMATE_DEFLECTION_FACTOR,
            allowableDeflection = lx * 1000.0 / 360.0,
            isSafe = slabThickness >= minThicknessSBC,
            message = String.format("SBC 304 Table 8.3.1.1: min h = %.0f mm", minThicknessSBC),
            recommendation = if (slabThickness < minThicknessSBC)
                String.format("Increase thickness to %.0f mm per SBC 304 Table 8.3.1.1", minThicknessSBC)
            else "Thickness OK per SBC 304"
        )

        // Fire resistance check — SBC 304 Section 8.2
        if (fireRatingHours > 0) {
            val fireMin = if (fireRatingHours >= 2.0) FIRE_2HR_MIN_THICKNESS else FIRE_1HR_MIN_THICKNESS
            if (slabThickness < fireMin) {
                warnings.add("SBC 304: Fire rating %.0fhr requires min thickness %.0f mm (provided: %.0f mm)"
                    .format(fireRatingHours, fireMin, slabThickness))
            }
        }

        // Reinforcement layout
        val topLayout = BarLayout(
            diameter = columnStripResult.barDiameter,
            spacing = columnStripResult.barSpacing,
            direction = BarDirection.BOTH,
            length = min(panelLength, panelWidth) * 1.2,
            numberOfBars = ceil(panelLength / columnStripResult.barSpacing).toInt()
        )
        val bottomLayout = BarLayout(
            diameter = middleStripResult.barDiameter,
            spacing = middleStripResult.barSpacing,
            direction = BarDirection.BOTH,
            length = min(panelLength, panelWidth) * 1.0,
            numberOfBars = ceil(panelWidth / middleStripResult.barSpacing).toInt()
        )

        val flexureResult = columnStripResult.copy(
            codeNotes = codeNotes,
            warnings = warnings
        )

        return AdvancedSlabResult(
            slabType = SlabType.FlatPlate(slabThickness, panelLength, panelWidth, columnSize),
            flexureResult = flexureResult,
            shearCheck = shearCheck,
            deflectionCheck = deflectionCheck,
            punchingShearCheck = punchingCheck,
            reinforcementLayout = ReinforcementLayout(
                topBars = topLayout,
                bottomBars = bottomLayout,
                distributionBars = bottomLayout.copy(direction = BarDirection.LONG),
                additionalBars = emptyList()
            ),
            concreteVolume = panelLength * panelWidth * slabThickness / 1e9,
            formworkArea = panelLength * panelWidth / 1e6,
            inventoryAnalysis = null,
            postTensionCalculations = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. FLAT SLAB WITH DROP PANELS — SBC 304 §8.10 + §8.6
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * تصميم البلاطة المسطحة مع أطباق الدعم (Flat Slab with Drop Panels)
     * SBC 304 §8.10 (Direct Design) + §8.6 (Punching with enlarged perimeter)
     *
     * @param dropPanelThickness Drop panel thickness below slab soffit (mm)
     * @param dropPanelSize Drop panel plan dimension (mm), assumed square
     */
    fun designFlatSlabWithDropPanels(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        dropPanelThickness: Double,
        dropPanelSize: Double,
        panelLength: Double,
        panelWidth: Double,
        columnSize: Double,
        totalLoad: Double,
        exposure: ExposureClass = ExposureClass.NORMAL,
        isSeismic: Boolean = false,
        fireRatingHours: Double = 0.0
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        codeNotes.add("SBC 304-2018: Flat Slab with Drop Panels")
        codeNotes.add("Reference: SBC 304 §8.10, §8.6")

        val fcPrime = FCU_TO_FC_PRIME_FACTOR * fcu
        codeNotes.add(String.format("fcu=%.0f MPa → fc'=%.1f MPa (SBC conversion)", fcu, fcPrime))

        // SBC 304: Drop panel must extend at least 1/6 span each direction
        val lx = min(panelLength, panelWidth) / 1000.0
        val ly = max(panelLength, panelWidth) / 1000.0
        val minDropExtension = lx / 6.0 * 1000.0

        if (dropPanelSize < minDropExtension) {
            warnings.add("SBC 304 §8.10.4: Drop panel should extend ≥ L/6 = %.0f mm (provided: %.0f mm)"
                .format(minDropExtension, dropPanelSize))
        }

        // SBC: Drop panel thickness below slab ≥ slab/4
        val minDropThickness = slabThickness / 4.0
        if (dropPanelThickness < minDropThickness) {
            warnings.add("SBC 304 §8.10.4: Drop panel thickness should be ≥ h/4 = %.0f mm (provided: %.0f mm)"
                .format(minDropThickness, dropPanelThickness))
        }

        val totalDepthAtColumn = slabThickness + dropPanelThickness
        val cover = exposure.cover
        val effectiveDepth = totalDepthAtColumn - cover - 8.0

        // Total static moment (SBC 304 Table 8.10.2.2)
        val totalStaticMoment = totalLoad * lx * lx * ly / 8.0

        // Column strip carries more moment with drop panel
        val columnStripWidth = min(lx / 2.0, ly / 4.0)
        val colNegMoment = totalStaticMoment * 0.67  // Increased for drop panel
        val colPosMoment = totalStaticMoment * 0.33
        val colMomentPerMeter = max(colNegMoment, colPosMoment) / (columnStripWidth * 1000.0)

        val columnStripResult = designFlexureSection(
            fcPrime = fcPrime, fy = fy,
            momentPerMeter = colMomentPerMeter,
            effectiveDepth = effectiveDepth,
            slabThickness = totalDepthAtColumn,
            isSeismic = isSeismic,
            warnings = warnings, codeNotes = codeNotes,
            codeRef = "SBC 304 §8.10"
        )

        // Punching shear — SBC 304 §8.6 with drop panel perimeter
        // Critical section at d/2 from face of drop panel (larger perimeter)
        val punchingCheck = checkPunchingShear(
            fcPrime = fcPrime,
            columnSize = dropPanelSize,  // Use drop panel as effective column
            effectiveDepth = effectiveDepth,
            totalLoad = totalLoad,
            panelLength = panelLength,
            panelWidth = panelWidth,
            dropPanelWidth = dropPanelSize,
            codeRef = "SBC 304 §8.6 (with drop panel)"
        )

        if (!punchingCheck.isSafe) {
            warnings.add("SBC §8.6: Punching shear critical — shear studs or enlarged drop panel required")
        }

        // Shear check
        val shearWidth = ly * 1000.0
        val Vc = PHI_SHEAR * 0.17 * sqrt(fcPrime) * shearWidth * effectiveDepth / 1000.0
        val Vu = totalLoad * lx * ly * 1000.0 / 1000.0
        val shearCheck = ShearCheckResult(
            appliedShear = Vu, shearCapacity = Vc,
            isSafe = Vu <= Vc, utilizationRatio = Vu / Vc.coerceAtLeast(1.0),
            criticalSection = effectiveDepth / 2.0,
            criticalPerimeter = 2.0 * (dropPanelSize + effectiveDepth)
        )

        // Deflection
        val minThicknessSBC = max(FLAT_PLATE_MIN_THICKNESS,
            getSBCMinFlatPlateThickness(lx * 1000.0, ly * 1000.0, fy))
        val deflectionCheck = DeflectionCheckResult(
            calculatedDeflection = 0.0,
            allowableDeflection = lx * 1000.0 / 360.0,
            isSafe = slabThickness >= minThicknessSBC,
            message = String.format("Min h = %.0f mm per SBC 304 Table 8.3.1.1", minThicknessSBC),
            recommendation = "Drop panel improves deflection significantly"
        )

        val topLayout = BarLayout(
            diameter = columnStripResult.barDiameter,
            spacing = columnStripResult.barSpacing,
            direction = BarDirection.BOTH,
            length = dropPanelSize * 1.3,
            numberOfBars = ceil(dropPanelSize / columnStripResult.barSpacing).toInt()
        )
        val bottomLayout = BarLayout(
            diameter = columnStripResult.barDiameter,
            spacing = columnStripResult.barSpacing * 1.2,
            direction = BarDirection.BOTH,
            length = min(panelLength, panelWidth),
            numberOfBars = ceil(min(panelLength, panelWidth) / (columnStripResult.barSpacing * 1.2)).toInt()
        )

        return AdvancedSlabResult(
            slabType = SlabType.FlatSlab(slabThickness, dropPanelThickness, dropPanelSize,
                panelLength, panelWidth, columnSize),
            flexureResult = columnStripResult.copy(codeNotes = codeNotes, warnings = warnings),
            shearCheck = shearCheck,
            deflectionCheck = deflectionCheck,
            punchingShearCheck = punchingCheck,
            reinforcementLayout = ReinforcementLayout(
                topBars = topLayout, bottomBars = bottomLayout,
                distributionBars = bottomLayout.copy(direction = BarDirection.LONG),
                additionalBars = emptyList()
            ),
            concreteVolume = (panelLength * panelWidth * slabThickness +
                    dropPanelSize * dropPanelSize * dropPanelThickness) / 1e9,
            formworkArea = panelLength * panelWidth / 1e6,
            inventoryAnalysis = null,
            postTensionCalculations = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. ONE-WAY HORDI / RIBBED SLAB — SBC 304 §8.12
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * تصميم البلاطة المقلوبة/البردورية ذات الاتجاه الواحد (One-Way Hordi/Ribbed Slab)
     * Very common in Saudi construction (Hordi blocks + ribs + topping)
     *
     * SBC 304 §8.12 requirements:
     * - Rib width ≥ 100 mm
     * - Rib spacing ≤ 900 mm center-to-center
     * - Clear depth of rib ≤ 3.5 × rib width
     * - Topping thickness ≥ 50 mm
     *
     * @param totalThickness Total depth including topping (mm)
     * @param ribWidth Rib width (mm)
     * @param ribSpacing Center-to-center rib spacing (mm)
     * @param blockWidth Hollow block width (mm)
     * @param blockHeight Hollow block height (mm)
     * @param span Clear span (mm)
     * @param supportConditions Edge support conditions
     */
    fun designOneWayHordiSlab(
        fcu: Double,
        fy: Double,
        totalThickness: Double,
        ribWidth: Double,
        ribSpacing: Double,
        blockWidth: Double,
        blockHeight: Double,
        span: Double,
        supportConditions: SlabSupportConditions,
        totalLoad: Double,
        exposure: ExposureClass = ExposureClass.NORMAL,
        isSeismic: Boolean = false
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        codeNotes.add("SBC 304-2018: One-Way Hordi/Ribbed Slab")
        codeNotes.add("Reference: SBC 304 §8.12")

        val fcPrime = FCU_TO_FC_PRIME_FACTOR * fcu
        codeNotes.add(String.format("fcu=%.0f MPa → fc'=%.1f MPa (SBC conversion)", fcu, fcPrime))

        // SBC 304 §8.12: Validate rib dimensions
        if (ribWidth < RIB_MIN_WIDTH) {
            warnings.add(String.format("SBC 304 §8.12.1: Rib width %.0f mm < minimum %.0f mm", ribWidth, RIB_MIN_WIDTH))
        }
        if (ribSpacing > RIB_MAX_SPACING) {
            warnings.add(String.format("SBC 304 §8.12.1: Rib spacing %.0f mm > maximum %.0f mm", ribSpacing, RIB_MAX_SPACING))
        }

        val ribDepth = totalThickness - blockHeight
        val clearRibDepth = totalThickness - TOPPING_MIN_THICKNESS
        if (ribDepth > 3.5 * ribWidth) {
            warnings.add(String.format("SBC 304 §8.12.2: Rib depth/h (%.1f) > 3.5 — design as beam", ribDepth / ribWidth))
        }

        val toppingThickness = totalThickness - blockHeight
        if (toppingThickness < TOPPING_MIN_THICKNESS) {
            warnings.add("SBC 304 §8.12.1: Topping thickness %.0f mm < minimum %.0f mm"
                .format(toppingThickness, TOPPING_MIN_THICKNESS))
        }

        codeNotes.add(String.format("Rib: %.0f×%.0f mm, spacing: %.0f mm c/c", ribWidth, ribDepth, ribSpacing))
        codeNotes.add(String.format("Topping: %.0f mm, Block: %.0f×%.0f mm", toppingThickness, blockWidth, blockHeight))

        // Effective depth for rib (main reinforcement)
        val cover = exposure.cover
        val effectiveDepthRib = ribDepth - cover - 10.0  // cover + half bar (estimated ~10mm for d~25)
        val effectiveDepthTopping = toppingThickness - 20.0 - 6.0  // top bars in topping

        // Self-weight of ribbed slab (per m²)
        val ribAreaPerMeter = ribWidth * ribDepth * 1000.0 / ribSpacing  // mm²/m of rib concrete
        val blockAreaPerMeter = blockWidth * blockHeight * 1000.0 / ribSpacing
        val toppingAreaPerMeter = 1000.0 * toppingThickness  // mm²/m
        // Self weight: (concrete - blocks) + topping + blocks
        val concreteSelfWeight = (ribAreaPerMeter + toppingAreaPerMeter) * 25.0 / 1e6  // kN/m²
        val blockSelfWeight = blockAreaPerMeter * 4.5 / 1e6  // lightweight blocks ~4.5 kN/m³

        codeNotes.add("Self-weight: concrete=%.2f kN/m², blocks=%.2f kN/m²"
            .format(concreteSelfWeight, blockSelfWeight))

        // Design moment per rib
        val spanM = span / 1000.0
        val loadPerMeter = totalLoad * ribSpacing / 1000.0  // kN/m per rib
        val supportType = determineSupportType(supportConditions)
        val momentCoeff = when (supportType) {
            SupportCondition.SIMPLY_SUPPORTED -> 1.0 / 8.0
            SupportCondition.CONTINUOUS -> 1.0 / 10.0
            SupportCondition.CANTILEVER -> 1.0 / 2.0
        }
        val Mu = momentCoeff * loadPerMeter * spanM * spanM  // kN.m per rib

        codeNotes.add(String.format("Mu = %.1f kN.m per rib (w=%.1f kN/m, L=%.1f m)", Mu, loadPerMeter, spanM))

        // Flexure design for rib (treated as T-beam)
        val bw = ribWidth  // mm
        val bf = ribSpacing  // mm (flange width = rib spacing)
        val hf = toppingThickness  // mm (flange depth = topping)
        val d = effectiveDepthRib

        // Check if neutral axis is in flange: Mu < 0.85*fc'*bf*hf*(d-hf/2)
        val flangeMomentCapacity = 0.85 * fcPrime * bf * hf * (d - hf / 2.0) / 1e6
        val isInFlange = Mu <= flangeMomentCapacity

        val asRequired: Double
        if (isInFlange) {
            // Design as rectangular section with b = bf
            asRequired = calculateRequiredAs(fcPrime, fy, Mu * 1e6, bf, d)
        } else {
            // T-beam design: flange contribution + web contribution
            val MuFlange = 0.85 * fcPrime * (bf - bw) * hf * (d - hf / 2.0) / 1e6  // kN.m
            val MuWeb = Mu - MuFlange  // remaining moment for web
            val asWeb = calculateRequiredAs(fcPrime, fy, MuWeb * 1e6, bw, d)
            val asFlange = 0.85 * fcPrime * (bf - bw) * hf / fy  // kN to mm²
            asRequired = asFlange + asWeb
        }

        codeNotes.add(String.format("NA in %s: As_req = %.0f mm² per rib", 
            if (isInFlange) "flange" else "web", asRequired))

        // Select bar per rib using Saudi diameters
        val barSelection = selectSaudiBar(asRequired, maxSpacing = 200.0, minSpacing = 75.0)
        codeNotes.add(String.format("Rib reinforcement: %d×Φ%.0f mm", barSelection.first, barSelection.second))

        // Minimum reinforcement per rib (SBC 304-8.12)
        val ribConcreteArea = ribWidth * ribDepth
        val asMinPerRib = max(MIN_REIN_RATIO * ribConcreteArea, 2.0 * PI * 10.0 * 10.0 / 4.0)
        val asFinal = max(barSelection.first * PI * barSelection.second * barSelection.second / 4.0, asMinPerRib)

        // Shear check per rib
        val shearCoeff = when (supportType) {
            SupportCondition.SIMPLY_SUPPORTED -> 0.5
            SupportCondition.CONTINUOUS -> 0.6
            SupportCondition.CANTILEVER -> 1.0
        }
        val Vu = shearCoeff * loadPerMeter * spanM  // kN per rib
        val Vc = PHI_SHEAR * 0.17 * sqrt(fcPrime) * bw * d / 1000.0
        val isShearSafe = Vu <= Vc

        if (!isShearSafe) {
            warnings.add("SBC 304 §8.12: Shear in rib exceeds Vc — shear reinforcement needed")
        }

        // Deflection with hot climate factor — SBC modification
        val minThicknessSBC = when (supportType) {
            SupportCondition.SIMPLY_SUPPORTED -> span / 20.0
            SupportCondition.CONTINUOUS -> span / 28.0
            SupportCondition.CANTILEVER -> span / 8.0
        } * HOT_CLIMATE_DEFLECTION_FACTOR  // Hot climate increase

        val deflectionCheck = DeflectionCheckResult(
            immediateDeflection = 0.0,
            longTermDeflection = 0.0,
            calculatedDeflection = 0.0,  // Simplified — span/thickness check used
            allowableDeflection = spanM * 1000.0 / (if (supportType == SupportCondition.CANTILEVER) 180.0 else 250.0),
            isSafe = totalThickness >= minThicknessSBC,
            message = "SBC min thickness (hot climate factor ×%.2f): %.0f mm"
                .format(HOT_CLIMATE_DEFLECTION_FACTOR, minThicknessSBC),
            recommendation = if (totalThickness < minThicknessSBC)
                String.format("Increase total thickness to ≥ %.0f mm", minThicknessSBC)
            else "Hordi slab thickness adequate"
        )

        val shearCheckResult = ShearCheckResult(
            appliedShear = Vu, shearCapacity = Vc,
            isSafe = isShearSafe, utilizationRatio = Vu / Vc.coerceAtLeast(0.01),
            criticalSection = d, criticalPerimeter = 2.0 * (ribWidth + d)
        )

        val ribFlexureResult = SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asFinal,
            barDiameter = barSelection.second,
            barSpacing = ribSpacing,
            minThickness = minThicknessSBC,
            shearCapacity = Vc,
            isSafe = isShearSafe,
            utilizationRatio = asRequired / asMinPerRib.coerceAtLeast(1.0),
            warnings = warnings,
            codeNotes = codeNotes
        )

        val ribSlabType = SlabType.Hordi(totalThickness, ribWidth, ribSpacing,
            blockWidth, blockHeight, span, supportConditions)

        return AdvancedSlabResult(
            slabType = ribSlabType,
            flexureResult = ribFlexureResult,
            shearCheck = shearCheckResult,
            deflectionCheck = deflectionCheck,
            punchingShearCheck = null,
            reinforcementLayout = ReinforcementLayout(
                topBars = BarLayout(barSelection.second, ribSpacing, BarDirection.SHORT,
                    span * 1.1, ceil(span / ribSpacing).toInt()),
                bottomBars = BarLayout(barSelection.second, ribSpacing, BarDirection.SHORT,
                    span * 1.1, ceil(span / ribSpacing).toInt()),
                distributionBars = BarLayout(10.0, 300.0, BarDirection.LONG,
                    span * 1.1, ceil(span / 300.0).toInt()),
                additionalBars = if (isSeismic) listOf(
                    BarLayout(barSelection.second, SEISMIC_MAX_BAR_SPACING, BarDirection.SHORT,
                        span * 1.1, ceil(span / SEISMIC_MAX_BAR_SPACING).toInt())
                ) else emptyList()
            ),
            concreteVolume = span * 1000.0 * (ribAreaPerMeter + toppingAreaPerMeter) / 1e9,
            formworkArea = span * 1000.0 / 1e6,
            inventoryAnalysis = null,
            postTensionCalculations = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. TWO-WAY WAFFLE SLAB — SBC 304 §8.12
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * تصميم البلاطة الوافل ذات الاتجاهين (Two-Way Waffle Slab)
     * Ribs in both directions with solid heads at columns
     *
     * SBC 304 §8.12 extended for two-way:
     * - Both direction ribs
     * - Solid head at columns (for punching resistance)
     * - SBC punching shear with enlarged perimeter
     *
     * @param ribDepth Depth of rib below topping (mm)
     */
    fun designTwoWayWaffleSlab(
        fcu: Double,
        fy: Double,
        totalThickness: Double,
        ribWidth: Double,
        ribDepth: Double,
        ribSpacing: Double,
        shortSpan: Double,
        longSpan: Double,
        solidHeadSize: Double,
        totalLoad: Double,
        supportConditions: SlabSupportConditions,
        exposure: ExposureClass = ExposureClass.NORMAL,
        isSeismic: Boolean = false
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        codeNotes.add("SBC 304-2018: Two-Way Waffle Slab")
        codeNotes.add("Reference: SBC 304 §8.12 (two-way extension)")

        val fcPrime = FCU_TO_FC_PRIME_FACTOR * fcu
        codeNotes.add(String.format("fcu=%.0f MPa → fc'=%.1f MPa (SBC conversion)", fcu, fcPrime))

        val lx = shortSpan / 1000.0
        val ly = longSpan / 1000.0
        val toppingThickness = totalThickness - ribDepth
        val ratio = ly / lx

        // Validate SBC requirements
        if (ribWidth < RIB_MIN_WIDTH) {
            warnings.add(String.format("SBC 304 §8.12.1: Rib width < %.0f mm minimum", RIB_MIN_WIDTH))
        }
        if (ribSpacing > RIB_MAX_SPACING) {
            warnings.add(String.format("SBC 304 §8.12.1: Rib spacing > %.0f mm maximum", RIB_MAX_SPACING))
        }
        if (toppingThickness < TOPPING_MIN_THICKNESS) {
            warnings.add(String.format("SBC 304 §8.12.1: Topping < %.0f mm minimum", TOPPING_MIN_THICKNESS))
        }

        // Solid head must extend at least 1/6 span from column face
        val minHeadSize = max(lx, ly) / 6.0 * 1000.0
        if (solidHeadSize < minHeadSize) {
            warnings.add(String.format("SBC 304 §8.12: Solid head ≥ L/6 = %.0f mm recommended", minHeadSize))
        }

        codeNotes.add(String.format("Ribs: %.0f×%.0f mm @ %.0f mm c/c (both ways)", ribWidth, ribDepth, ribSpacing))
        codeNotes.add(String.format("Topping: %.0f mm, Solid head: %.0f×%.0f mm", toppingThickness, solidHeadSize, solidHeadSize))

        // Effective depth
        val cover = exposure.cover
        val effectiveDepth = ribDepth - cover - 10.0

        // Moment distribution (similar to flat slab but with rib stiffness)
        // Total static moment per SBC 304 Table 8.10.2.2
        val totalStaticMoment = totalLoad * lx * lx * ly / 8.0

        // Short direction carries more load (stiffer)
        val shortMomentFactor = when {
            ratio <= 1.0 -> 0.50
            ratio <= 1.5 -> 0.55
            ratio <= 2.0 -> 0.65
            else -> 0.70
        }
        val longMomentFactor = 1.0 - shortMomentFactor

        val shortMoment = totalStaticMoment * shortMomentFactor * 0.65  // Column strip neg
        val longMoment = totalStaticMoment * longMomentFactor * 0.45

        // Design short direction ribs
        val loadPerRibShort = totalLoad * ribSpacing / 1000.0  // kN/m per rib
        val MuShort = shortMoment / (lx * 1000.0 / ribSpacing)  // kN.m per rib
        val asShort = calculateRequiredAs(fcPrime, fy, MuShort * 1e6, ribWidth, effectiveDepth)

        // Design long direction ribs
        val loadPerRibLong = totalLoad * ribSpacing / 1000.0
        val MuLong = longMoment / (ly * 1000.0 / ribSpacing)
        val asLong = calculateRequiredAs(fcPrime, fy, MuLong * 1e6, ribWidth, effectiveDepth)

        // Select bars — Saudi diameters
        val shortBar = selectSaudiBar(asShort, 200.0, 75.0)
        val longBar = selectSaudiBar(asLong, 200.0, 75.0)

        codeNotes.add(String.format("Short dir ribs: %d×Φ%.0f mm (As=%.0f mm²)", 
            shortBar.first, shortBar.second, shortBar.first * PI * shortBar.second * shortBar.second / 4.0))
        codeNotes.add(String.format("Long dir ribs: %d×Φ%.0f mm (As=%.0f mm²)", 
            longBar.first, longBar.second, longBar.first * PI * longBar.second * longBar.second / 4.0))

        // Minimum reinforcement per rib
        val ribArea = ribWidth * ribDepth
        val asMinRib = max(MIN_REIN_RATIO * ribArea, 2.0 * PI * 100.0 / 4.0)

        // Shear per rib (short direction controls)
        val VuRib = 0.5 * loadPerRibShort * lx  // kN
        val VcRib = PHI_SHEAR * 0.17 * sqrt(fcPrime) * ribWidth * effectiveDepth / 1000.0
        val isShearSafe = VuRib <= VcRib
        if (!isShearSafe) {
            warnings.add("SBC 304: Rib shear exceeded — increase rib width or depth")
        }

        // Punching shear at solid head — SBC 304 §8.6
        val punchingCheck = checkPunchingShear(
            fcPrime = fcPrime,
            columnSize = solidHeadSize,
            effectiveDepth = totalThickness - cover - 8.0,
            totalLoad = totalLoad,
            panelLength = shortSpan,
            panelWidth = longSpan,
            dropPanelWidth = solidHeadSize,
            codeRef = "SBC 304 §8.6 (waffle solid head)"
        )

        if (!punchingCheck.isSafe) {
            warnings.add("SBC §8.6: Punching shear at solid head — enlarge head or add shear studs")
        }

        // Deflection (hot climate factor for Saudi)
        val minThicknessSBC = max(FLAT_PLATE_MIN_THICKNESS, lx * 1000.0 / 30.0) * HOT_CLIMATE_DEFLECTION_FACTOR
        val deflectionCheck = DeflectionCheckResult(
            calculatedDeflection = 0.0,
            allowableDeflection = lx * 1000.0 / 250.0,
            isSafe = totalThickness >= minThicknessSBC,
            message = String.format("SBC min (with hot climate ×%.2f): %.0f mm", 
                HOT_CLIMATE_DEFLECTION_FACTOR, minThicknessSBC)
        )

        val maxAs = max(asShort, asLong)
        val flexureResult = SlabDesignResult(
            requiredReinforcement = maxAs,
            providedReinforcement = shortBar.first * PI * shortBar.second * shortBar.second / 4.0,
            barDiameter = shortBar.second,
            barSpacing = ribSpacing,
            minThickness = minThicknessSBC,
            shearCapacity = VcRib,
            isSafe = isShearSafe && punchingCheck.isSafe,
            utilizationRatio = maxAs / asMinRib.coerceAtLeast(1.0),
            warnings = warnings, codeNotes = codeNotes
        )

        return AdvancedSlabResult(
            slabType = SlabType.Waffle(totalThickness, ribWidth, ribDepth, ribSpacing,
                shortSpan, longSpan, supportConditions),
            flexureResult = flexureResult,
            shearCheck = ShearCheckResult(
                appliedShear = VuRib, shearCapacity = VcRib,
                isSafe = isShearSafe, utilizationRatio = VuRib / VcRib.coerceAtLeast(0.01),
                criticalSection = effectiveDepth / 2.0,
                criticalPerimeter = 2.0 * (solidHeadSize + totalThickness)
            ),
            deflectionCheck = deflectionCheck,
            punchingShearCheck = punchingCheck,
            reinforcementLayout = ReinforcementLayout(
                topBars = BarLayout(shortBar.second, ribSpacing, BarDirection.SHORT,
                    shortSpan * 1.1, ceil(shortSpan / ribSpacing).toInt()),
                bottomBars = BarLayout(longBar.second, ribSpacing, BarDirection.LONG,
                    longSpan * 1.1, ceil(longSpan / ribSpacing).toInt()),
                distributionBars = BarLayout(10.0, 300.0, BarDirection.BOTH,
                    min(shortSpan, longSpan), 4),
                additionalBars = emptyList()
            ),
            concreteVolume = shortSpan * longSpan * totalThickness / 1e9 * 0.5,  // ~50% solid
            formworkArea = shortSpan * longSpan / 1e6,
            inventoryAnalysis = null,
            postTensionCalculations = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CANTILEVER SLAB — SBC 304 §8
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * تصميم البلاطة الكابولية (Cantilever Slab) حسب SBC 304
     * Full design with hot climate crack width check
     *
     * SBC-specific considerations:
     * - Minimum span/thickness = L/8 per SBC 304 Table 8.3.1.1
     * - Hot climate crack width factor = 1.25 (SBC modification)
     * - Top reinforcement is primary (negative moment)
     *
     * @param cantileverSpan Cantilever projection length (mm)
     * @param backSpan Back span length (mm) — for continuity
     * @param totalLoad Factored load (kN/m²)
     */
    fun designCantileverSlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        cantileverSpan: Double,
        backSpan: Double,
        totalLoad: Double,
        exposure: ExposureClass = ExposureClass.NORMAL,
        isSeismic: Boolean = false,
        isBalcony: Boolean = false
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        codeNotes.add("SBC 304-2018: Cantilever Slab Design")
        codeNotes.add("Reference: SBC 304 §8, Table 8.3.1.1")

        val fcPrime = FCU_TO_FC_PRIME_FACTOR * fcu
        codeNotes.add(String.format("fcu=%.0f MPa → fc'=%.1f MPa (SBC conversion)", fcu, fcPrime))

        val cover = exposure.cover
        // For cantilever: top bars are main reinforcement
        val effectiveDepthTop = slabThickness - cover - 8.0
        val effectiveDepthBottom = slabThickness - cover - 14.0  // bottom bars smaller

        val spanM = cantileverSpan / 1000.0
        codeNotes.add(String.format("Cantilever span: %.2f m, h = %.0f mm", spanM, slabThickness))

        // SBC minimum thickness for cantilever: L/8
        val minThicknessSBC = cantileverSpan / 8.0
        if (slabThickness < minThicknessSBC) {
            warnings.add("SBC 304 Table 8.3.1.1: Min thickness L/8 = %.0f mm (provided: %.0f mm)"
                .format(minThicknessSBC, slabThickness))
        }

        // Balcony live load = 3.0 kN/m² (SBC 301) vs floor = 2.0
        val effectiveLoad = if (isBalcony) totalLoad else totalLoad

        // Moment at support (negative): M = w × L² / 2
        val MuSupport = effectiveLoad * spanM * spanM / 2.0  // kN.m/m (negative)

        // Moment at tip: 0 (free end)
        // Moment at mid-span for deflection: w × (L/2)² / 2
        val MuMid = effectiveLoad * (spanM / 2.0).pow(2) / 2.0

        codeNotes.add(String.format("M- (support) = %.1f kN.m/m, M+ (mid) = %.1f kN.m/m", MuSupport, MuMid))

        // Design top reinforcement (at support — negative moment)
        val topResult = designFlexureSection(
            fcPrime = fcPrime, fy = fy,
            momentPerMeter = MuSupport,
            effectiveDepth = effectiveDepthTop,
            slabThickness = slabThickness,
            isSeismic = isSeismic,
            warnings = warnings, codeNotes = codeNotes,
            codeRef = "SBC 304 §8 (cantilever top)"
        )

        // Design bottom reinforcement (minimum — distribution steel)
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        val asMinBottom = rhoMin * 1000.0 * effectiveDepthBottom
        val bottomBar = selectSaudiBar(asMinBottom, 300.0, 100.0)
        val asBottom = bottomBar.first * PI * bottomBar.second * bottomBar.second / 4.0

        // Shear at support face: V = w × L
        val Vu = effectiveLoad * spanM  // kN/m
        val Vc = PHI_SHEAR * 0.17 * sqrt(fcPrime) * 1000.0 * effectiveDepthTop / 1000.0
        val isShearSafe = Vu <= Vc
        if (!isShearSafe) {
            warnings.add("SBC 304: Cantilever shear at support = %.1f kN/m > φVc = %.1f kN/m"
                .format(Vu, Vc))
        }

        // Deflection check — hot climate factor
        // Tip deflection: δ = w×L⁴ / (8×E×I) with long-term multiplier
        val Ec = 4700.0 * sqrt(fcPrime)  // MPa
        val Ig = 1000.0 * slabThickness.pow(3) / 12.0  // mm⁴/m
        val immediateDeflection = effectiveLoad * 1000.0 * (cantileverSpan).pow(4) /
                (8.0 * Ec * Ig) * 1000.0  // mm (approximate)
        val longTermDeflection = immediateDeflection * 2.0 * HOT_CLIMATE_DEFLECTION_FACTOR
        val allowableDeflection = cantileverSpan / 180.0  // L/180 for cantilevers

        val deflectionCheck = DeflectionCheckResult(
            immediateDeflection = immediateDeflection,
            longTermDeflection = longTermDeflection,
            calculatedDeflection = longTermDeflection,
            allowableDeflection = allowableDeflection,
            isSafe = longTermDeflection <= allowableDeflection,
            message = String.format("Hot climate factor ×%.2f applied to long-term deflection", 
                HOT_CLIMATE_DEFLECTION_FACTOR),
            recommendation = if (longTermDeflection > allowableDeflection)
                "Increase thickness or add top reinforcement"
            else "Deflection OK"
        )

        // Crack width check — SBC 304 with hot climate factor
        val crackWidthCheck = checkCrackWidthSBC(
            fcPrime = fcPrime,
            fy = fy,
            asProvided = topResult.providedReinforcement,
            effectiveDepth = effectiveDepthTop,
            slabThickness = slabThickness,
            momentPerMeter = MuSupport,
            totalLoad = effectiveLoad,
            span = cantileverSpan,
            exposure = exposure,
            warnings = warnings
        )

        // Development length check — SBC 304 §25
        val db = topResult.barDiameter
        val ld = max(12.0 * db, (fy * db) / (1.1 * sqrt(fcPrime) * 1000.0) * 20.0 / 25.0)
        val availableLength = cantileverSpan + backSpan * 0.3  // extend into back span
        if (ld > availableLength) {
            warnings.add("SBC 304 §25: Development length ld=%.0f mm > available=%.0f mm — use hooks"
                .format(ld, availableLength))
        }

        val shearCheck = ShearCheckResult(
            appliedShear = Vu, shearCapacity = Vc,
            isSafe = isShearSafe, utilizationRatio = Vu / Vc.coerceAtLeast(0.01),
            criticalSection = effectiveDepthTop, criticalPerimeter = 1000.0 + 2.0 * effectiveDepthTop
        )

        val flexureResult = topResult.copy(
            minThickness = minThicknessSBC,
            shearCapacity = Vc,
            isSafe = isShearSafe && longTermDeflection <= allowableDeflection && crackWidthCheck.isSafe,
            warnings = warnings, codeNotes = codeNotes
        )

        return AdvancedSlabResult(
            slabType = SlabType.OneWay(slabThickness, cantileverSpan, 1000.0),
            flexureResult = flexureResult,
            shearCheck = shearCheck,
            deflectionCheck = deflectionCheck,
            punchingShearCheck = null,
            reinforcementLayout = ReinforcementLayout(
                topBars = BarLayout(topResult.barDiameter, topResult.barSpacing,
                    BarDirection.SHORT, cantileverSpan + backSpan * 0.3,
                    ceil((cantileverSpan + backSpan * 0.3) / topResult.barSpacing).toInt()),
                bottomBars = BarLayout(bottomBar.second, 300.0,
                    BarDirection.SHORT, cantileverSpan,
                    ceil(cantileverSpan / 300.0).toInt()),
                distributionBars = BarLayout(10.0, 300.0,
                    BarDirection.LONG, 1000.0, 4),
                additionalBars = if (isBalcony) listOf(
                    BarLayout(topResult.barDiameter, topResult.barSpacing,
                        BarDirection.SHORT, cantileverSpan * 1.1,
                        ceil(cantileverSpan * 1.1 / topResult.barSpacing).toInt())
                ) else emptyList()
            ),
            concreteVolume = cantileverSpan * 1000.0 * slabThickness / 1e9,
            formworkArea = cantileverSpan * 1000.0 / 1e6,
            inventoryAnalysis = null,
            postTensionCalculations = null,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. SEISMIC PROVISIONS — SBC 304 §21
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * تطبيق متطلبات الزلازل على البلاطة (Seismic Provisions for Slabs)
     * SBC 304 Section 21 (equivalent to ACI 18 with Saudi modifications)
     *
     * When seismic load is active:
     * - Minimum reinforcement ratio increases to 0.0025 (SBC 304-21.5.2)
     * - Maximum bar spacing reduces to 200 mm (SBC 304-21.5.3)
     * - Additional top reinforcement at supports for moment reversal
     * - Bottom reinforcement must extend into supports
     *
     * @param seismicZone SBC seismic zone (A, B, C, D)
     * @param designResult Existing non-seismic design result to modify
     */
    fun applySeismicProvisions(
        fcPrime: Double,
        fy: Double,
        seismicZone: String,
        designResult: SlabDesignResult,
        slabThickness: Double,
        span: Double
    ): SlabDesignResult {
        val warnings = designResult.warnings.toMutableList()
        val codeNotes = designResult.codeNotes.toMutableList()
        codeNotes.add("SBC 304 §21: Seismic Provisions Applied")
        codeNotes.add(String.format("Seismic Zone: %s", seismicZone))

        val zoneFactor = when (seismicZone.uppercase()) {
            "A" -> SEISMIC_ZONE_A_FACTOR
            "B" -> SEISMIC_ZONE_B_FACTOR
            "C" -> SEISMIC_ZONE_C_FACTOR
            "D" -> SEISMIC_ZONE_D_FACTOR
            else -> {
                warnings.add(String.format("SBC 304 §21: Unknown seismic zone '%s' — defaulting to Zone A", seismicZone))
                SEISMIC_ZONE_A_FACTOR
            }
        }

        // SBC 304-21.5.2: Minimum reinforcement ratio for seismic
        val seismicMinRatio = max(SEISMIC_MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        codeNotes.add("SBC 304-21.5.2: Seismic min ρ = %.4f (Zone factor = %.2f)"
            .format(seismicMinRatio, zoneFactor))

        // Check if existing reinforcement meets seismic minimum
        val b = 1000.0
        val effectiveDepth = slabThickness - COVER_NORMAL - 8.0
        val asSeismicMin = seismicMinRatio * b * effectiveDepth

        if (designResult.providedReinforcement < asSeismicMin) {
            // Upgrade to seismic minimum using Saudi bars
            val newBar = selectSaudiBar(asSeismicMin, SEISMIC_MAX_BAR_SPACING, 100.0)
            val newAs = newBar.first * PI * newBar.second * newBar.second / 4.0
            val newSpacing = min(designResult.barSpacing, SEISMIC_MAX_BAR_SPACING)

            warnings.add("SBC 304-21.5.2: Reinforcement increased for seismic: %.0f → %.0f mm²/m"
                .format(designResult.providedReinforcement, newAs))
            codeNotes.add("Seismic upgrade: %d×Φ%.0f mm @ %.0f mm"
                .format(newBar.first, newBar.second, newSpacing))

            return designResult.copy(
                providedReinforcement = newAs,
                barDiameter = newBar.second,
                barSpacing = newSpacing,
                warnings = warnings,
                codeNotes = codeNotes
            )
        }

        // SBC 304-21.5.3: Check maximum spacing
        if (designResult.barSpacing > SEISMIC_MAX_BAR_SPACING) {
            warnings.add("SBC 304-21.5.3: Spacing reduced from %.0f to %.0f mm for seismic"
                .format(designResult.barSpacing, SEISMIC_MAX_BAR_SPACING))
            return designResult.copy(
                barSpacing = SEISMIC_MAX_BAR_SPACING,
                providedReinforcement = PI * designResult.barDiameter * designResult.barDiameter / 4.0 *
                        1000.0 / SEISMIC_MAX_BAR_SPACING,
                warnings = warnings,
                codeNotes = codeNotes
            )
        }

        // SBC 304-21.5.4: Bottom bars must extend into support by ld
        val db = designResult.barDiameter
        val ldSeismic = max(12.0 * db, (fy * db) / (1.1 * sqrt(fcPrime)) * 20.0 / 25.0)
        val minExtension = max(ldSeismic, span / 4.0)
        codeNotes.add("SBC 304-21.5.4: Bottom bar min extension into support = %.0f mm"
            .format(minExtension))

        return designResult.copy(warnings = warnings, codeNotes = codeNotes)
    }

    /**
     * حساب التسليح الإضافي للزلازل (Additional seismic reinforcement)
     * Per SBC 304 §21.5 — top reinforcement at supports for moment reversal
     *
     * @return Additional reinforcement area (mm²/m) required for seismic
     */
    fun calculateSeismicTopReinforcement(
        fcPrime: Double,
        fy: Double,
        slabThickness: Double,
        span: Double,
        seismicZone: String,
        exposure: ExposureClass = ExposureClass.NORMAL
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        codeNotes.add("SBC 304 §21.5: Seismic Top Reinforcement Calculation")

        val zoneFactor = when (seismicZone.uppercase()) {
            "A" -> SEISMIC_ZONE_A_FACTOR
            "B" -> SEISMIC_ZONE_B_FACTOR
            "C" -> SEISMIC_ZONE_C_FACTOR
            "D" -> SEISMIC_ZONE_D_FACTOR
            else -> SEISMIC_ZONE_A_FACTOR
        }

        val effectiveDepth = slabThickness - exposure.cover - 8.0
        val b = 1000.0

        // SBC 304-21.5.2: Min reinforcement for seismic
        val rhoSeismic = max(SEISMIC_MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        val asRequired = rhoSeismic * b * effectiveDepth * zoneFactor / SEISMIC_ZONE_B_FACTOR

        // Ensure minimum
        val asMin = max(asRequired, MIN_REIN_RATIO * b * effectiveDepth)

        val bar = selectSaudiBar(asMin, SEISMIC_MAX_BAR_SPACING, 100.0)
        val asProvided = bar.first * PI * bar.second * bar.second / 4.0

        codeNotes.add("Zone %s (factor=%.2f): As_seismic = %.0f mm²/m"
            .format(seismicZone, zoneFactor, asMin))

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asProvided,
            barDiameter = bar.second,
            barSpacing = min(SEISMIC_MAX_BAR_SPACING, 1000.0 * PI * bar.second * bar.second / 4.0 / asMin),
            minThickness = span / 8.0,
            shearCapacity = 0.0,
            isSafe = asProvided >= asRequired,
            utilizationRatio = asRequired / asMin.coerceAtLeast(1.0),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * SBC cylinder conversion: fc' = 0.67 × fcu / γc
     * This is the KEY DIFFERENCE from ACI (which uses 0.8 × fcu)
     */
    private fun sbcFcPrime(fcu: Double): Double = FCU_TO_FC_PRIME_FACTOR * fcu

    /**
     * Flexure section design using Rn-ρ method (SBC 304 / ACI 318)
     * Returns SlabDesignResult with bar selection from Saudi market diameters
     */
    private fun designFlexureSection(
        fcPrime: Double,
        fy: Double,
        momentPerMeter: Double,
        effectiveDepth: Double,
        slabThickness: Double,
        isSeismic: Boolean,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>,
        codeRef: String
    ): SlabDesignResult {
        val Mu = momentPerMeter * 1e6  // N.mm/m
        val b = 1000.0
        val Rn = Mu / (PHI_FLEXURE * b * effectiveDepth * effectiveDepth)

        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fcPrime)
        val rho = if (discriminant > 0) {
            (0.85 * fcPrime / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add(String.format("%s: Compression failure — increase depth or strength", codeRef))
            0.025
        }

        // Minimum reinforcement (SBC 304-8.4)
        val rhoMin = if (isSeismic) {
            max(SEISMIC_MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        } else {
            max(MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        }

        val rhoFinal = rho.coerceIn(0.0, 0.025)
        val asRequired = max(rhoFinal, rhoMin) * b * effectiveDepth

        // Select bar from Saudi market diameters
        val maxSpacing = if (isSeismic) SEISMIC_MAX_BAR_SPACING
            else min(3.0 * slabThickness, 450.0)
        val bar = selectSaudiBar(asRequired, maxSpacing, 100.0)
        val barArea = PI * bar.second * bar.second / 4.0
        val nominalSpacing = barArea * 1000.0 / asRequired
        val finalSpacing = nominalSpacing.coerceIn(100.0, maxSpacing)
        val asProvided = barArea * 1000.0 / finalSpacing

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asProvided,
            barDiameter = bar.second,
            barSpacing = finalSpacing,
            minThickness = 0.0,
            shearCapacity = 0.0,
            isSafe = discriminant > 0,
            utilizationRatio = rhoFinal / rhoMin.coerceAtLeast(0.001),
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * Calculate required As using Rn-ρ method
     * @return Required steel area (mm²)
     */
    private fun calculateRequiredAs(
        fcPrime: Double, fy: Double,
        Mu: Double,  // N.mm
        b: Double,   // mm
        d: Double    // mm
    ): Double {
        val Rn = Mu / (PHI_FLEXURE * b * d * d)
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fcPrime)
        val rho = if (discriminant > 0) {
            (0.85 * fcPrime / fy) * (1.0 - sqrt(discriminant))
        } else {
            0.025  // compression failure
        }
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fcPrime) / fy)
        return max(rho, rhoMin) * b * d
    }

    /**
     * Select bars from Saudi market diameters [14, 16, 20, 25, 32]
     * @return Pair of (numberOfBars, barDiameter) for the given spacing constraint
     */
    private fun selectSaudiBar(
        asRequired: Double,
        maxSpacing: Double,
        minSpacing: Double
    ): Pair<Int, Double> {
        for (dia in SAUDI_BAR_DIAMETERS) {
            val area = PI * dia * dia / 4.0
            val spacing = area * 1000.0 / asRequired
            if (spacing in minSpacing..maxSpacing) {
                return Pair(1, dia)  // 1 bar per spacing unit
            }
        }
        // Fallback: smallest bar that can carry the load
        val smallestDia = SAUDI_BAR_DIAMETERS.first()
        val area = PI * smallestDia * smallestDia / 4.0
        val spacing = (area * 1000.0 / asRequired).coerceIn(minSpacing, maxSpacing)
        return Pair(ceil(1000.0 / spacing).toInt(), smallestDia)
    }

    /**
     * Punching shear check — SBC 304 §8.6
     * Critical section at d/2 from column face (or drop panel face)
     *
     * vc = 0.33 × √fc' × β (SBC modification — higher factor than ACI's 0.17)
     * For interior columns: bo = 4 × (c + d)
     */
    private fun checkPunchingShear(
        fcPrime: Double,
        columnSize: Double,     // mm
        effectiveDepth: Double, // mm
        totalLoad: Double,      // kN/m²
        panelLength: Double,    // mm
        panelWidth: Double,     // mm
        dropPanelWidth: Double, // mm (0 for flat plate)
        codeRef: String
    ): PunchingShearCheckResult {
        val d = effectiveDepth
        val c = if (dropPanelWidth > 0) dropPanelWidth else columnSize

        // Critical perimeter at d/2 from column/drop face
        val bo = 4.0 * (c + d)  // mm — interior column
        val criticalArea = (c + d) * (c + d)  // mm² (tributary area within perimeter)

        // Factored shear force
        val tributaryArea = panelLength * panelWidth / 1e6  // m²
        val Vu = totalLoad * tributaryArea  // kN (simplified — full panel load)
        val VuNet = max(Vu - 0.0, 0.0)  // No subtraction for simplicity (conservative)

        // SBC 304 §8.6: vc = 0.33 × √fc' (SBC uses 0.33 vs ACI's 0.17 for punching)
        // Actually SBC uses same as ACI for two-way shear: vc = 0.33√fc' for β=1.0
        val vc = 0.33 * sqrt(fcPrime)  // MPa (N/mm²)
        val phiVc = PHI_SHEAR * vc * bo * d / 1000.0  // kN

        // Also check β = 1.0 (no eccentricity) case
        val vcBeta1 = 0.33 * sqrt(fcPrime)
        val phiVcBeta1 = PHI_SHEAR * vcBeta1 * bo * d / 1000.0

        // Maximum shear capacity (SBC 304 §8.6.3): vn_max = 0.5√fc'
        val vnMax = 0.5 * sqrt(fcPrime)
        val phiVnMax = PHI_SHEAR * vnMax * bo * d / 1000.0

        val isSafe = VuNet <= phiVc
        val shearHeadsRequired = VuNet > phiVc && VuNet <= phiVnMax

        val warnings = mutableListOf<String>()
        if (shearHeadsRequired) {
            warnings.add("%s: Shear reinforcement (studs/stirrups) required — Vu=%.0f > φVc=%.0f kN"
                .format(codeRef, VuNet, phiVc))
        }
        if (VuNet > phiVnMax) {
            warnings.add("%s: Maximum shear exceeded — redesign slab (increase thickness or drop panel)"
                .format(codeRef))
        }

        return PunchingShearCheckResult(
            appliedShear = VuNet,
            shearCapacity = phiVc,
            utilizationRatio = VuNet / phiVc.coerceAtLeast(1.0),
            isSafe = isSafe,
            criticalPerimeter = bo,
            shearHeadsRequired = shearHeadsRequired,
            codeReference = codeRef,
            warnings = warnings
        )
    }

    /**
     * Crack width check per SBC 304 with hot climate factor
     * Uses Gergely-Lutz equation with SBC modification for Saudi climate
     *
     * SBC modification: wk × 1.25 for hot/arid climate (thermal cycling)
     * Allowable crack width: 0.30 mm (interior), 0.20 mm (exterior/coastal)
     */
    private fun checkCrackWidthSBC(
        fcPrime: Double,
        fy: Double,
        asProvided: Double,
        effectiveDepth: Double,
        slabThickness: Double,
        momentPerMeter: Double,
        totalLoad: Double,
        span: Double,
        exposure: ExposureClass,
        warnings: MutableList<String>
    ): CrackWidthCheckResult {
        val d = effectiveDepth
        val h = slabThickness
        val b = 1000.0
        val As = asProvided

        // Service moment (approximate: factored / 1.5)
        val Ms = momentPerMeter / 1.5
        val n = 200000.0 / (4700.0 * sqrt(fcPrime))  // Es / Ec
        val rho = As / (b * d)
        val fs = max(Ms * 1e6 / (As * d * 0.85), fy * 0.5)  // Service stress in steel (N/mm²)

        // Gergely-Lutz equation: w = 0.076 × fs × (dc × A)^(1/3) / (d × 1e5)
        // Simplified for slabs:
        val dc = h - d  // distance from extreme tension fiber to steel center
        val A = b * dc  // concrete area per bar
        val crackWidthBase = 0.076 * fs * pow(dc * A, 1.0 / 3.0) / (d * 1e5) * 1000.0  // mm

        // SBC hot climate modification
        val crackWidth = crackWidthBase * HOT_CLIMATE_CRACK_WIDTH_FACTOR

        // Allowable per SBC (more restrictive in coastal/severe)
        val allowable = when (exposure) {
            ExposureClass.NORMAL -> 0.30
            ExposureClass.COASTAL -> 0.20
            ExposureClass.SEVERE -> 0.15
        }

        val isSafe = crackWidth <= allowable
        if (!isSafe) {
            warnings.add("SBC 304: Crack width = %.3f mm > allowable %.2f mm (%s) — increase reinforcement or reduce bar spacing"
                .format(crackWidth, allowable, exposure.displayNameEn))
        }

        return CrackWidthCheckResult(
            calculatedWidth = crackWidth,
            allowableWidth = allowable,
            isSafe = isSafe,
            codeReference = String.format("SBC 304 §8.7 (with hot climate factor ×%.2f)", HOT_CLIMATE_CRACK_WIDTH_FACTOR)
        )
    }

    /**
     * SBC 304 minimum flat plate thickness — Table 8.3.1.1
     * Modified for SBC with hot climate consideration
     */
    private fun getSBCMinFlatPlateThickness(
        shortSpan: Double,  // mm
        longSpan: Double,   // mm
        fy: Double          // MPa
    ): Double {
        val lx = shortSpan / 1000.0
        val ly = longSpan / 1000.0
        val alpha = 0.0  // beam stiffness ratio (0 for flat plate)

        // SBC 304 Table 8.3.1.1 (similar to ACI but with SBC safety margin)
        // For flat plate (no beams): ln/33 (interior panel), ln/30 (exterior)
        val baseThickness = lx * 1000.0 / 33.0

        // Correction for fy (SBC 304)
        val fyFactor = min(1.0, 420.0 / fy.coerceAtLeast(200.0))

        return baseThickness * fyFactor
    }

    /**
     * Determine support type from SlabSupportConditions
     */
    private fun determineSupportType(conditions: SlabSupportConditions): SupportCondition {
        return when {
            conditions.edgeA == EdgeCondition.FREE || conditions.edgeC == EdgeCondition.FREE
                    || conditions.edgeB == EdgeCondition.FREE || conditions.edgeD == EdgeCondition.FREE
            -> SupportCondition.CANTILEVER

            conditions.edgeA == EdgeCondition.CONTINUOUS || conditions.edgeC == EdgeCondition.CONTINUOUS
                    || conditions.edgeB == EdgeCondition.CONTINUOUS || conditions.edgeD == EdgeCondition.CONTINUOUS
            -> SupportCondition.CONTINUOUS

            else -> SupportCondition.SIMPLY_SUPPORTED
        }
    }

}