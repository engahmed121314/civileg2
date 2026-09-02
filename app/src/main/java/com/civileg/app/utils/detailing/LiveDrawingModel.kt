package com.civileg.app.utils.detailing

import com.civileg.app.domain.BoundaryElementType
import com.civileg.app.domain.FlatSlabInput
import com.civileg.app.domain.FlatSlabResult
import com.civileg.app.domain.PileDesignResult
import com.civileg.app.domain.ShearWallInput
import com.civileg.app.domain.ShearWallResult
import com.civileg.app.domain.entities.SteelMemberResult
import com.civileg.app.domain.entities.depth
import com.civileg.app.domain.entities.flangeThickness
import com.civileg.app.domain.entities.webThickness
import com.civileg.app.domain.entities.width
import com.civileg.app.domain.entities.FrameAnalysisResult
import com.civileg.app.domain.entities.FrameAnalysisSettings
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import com.civileg.app.domain.calculations.base.SeismicForceDistribution
import com.civileg.app.domain.calculations.base.SpectrumValue
import com.civileg.app.utils.CalculatorEngine
import com.civileg.core.calculations.entities.DesignCode as CoreDesignCode
import com.civileg.core.calculations.entities.DimensionLine
import com.civileg.core.calculations.entities.DimensionSet
import com.civileg.core.calculations.entities.DrawingModel
import com.civileg.core.calculations.entities.DrawingModelBuilder
import com.civileg.core.calculations.entities.DrawingStatus
import com.civileg.core.calculations.entities.FrameMemberMark
import com.civileg.core.calculations.entities.Point2D
import com.civileg.core.calculations.entities.ReinforcementResult
import com.civileg.core.calculations.entities.SteelMemberMark
import com.civileg.core.calculations.entities.TitleBlock
import com.civileg.core.engineering.CodeVersionRegistry
import com.civileg.core.engineering.FlatSlabReinforcementResult
import com.civileg.core.engineering.FlatSlabStripReinforcement
import com.civileg.core.engineering.FootingDistributionSteel
import com.civileg.core.engineering.FootingReinforcementResult
import com.civileg.core.engineering.FrameAnalysisDetailResult
import com.civileg.core.engineering.FrameMemberDetail
import com.civileg.core.engineering.FrameNodeDetail
import com.civileg.core.engineering.RetainingWallReinforcementResult
import com.civileg.core.engineering.SeismicDetailResult
import com.civileg.core.engineering.SeismicFloorForcePoint
import com.civileg.core.engineering.SeismicSpectrumPoint
import com.civileg.core.engineering.ShearWallBoundaryReinforcement
import com.civileg.core.engineering.ShearWallCouplingReinforcement
import com.civileg.core.engineering.ShearWallHorizontalReinforcement
import com.civileg.core.engineering.ShearWallReinforcementResult
import com.civileg.core.engineering.ShearWallVerticalReinforcement
import com.civileg.core.engineering.SlabReinforcementResult
import com.civileg.core.engineering.StairReinforcementResult
import com.civileg.core.engineering.SteelMemberReinforcementResult
import com.civileg.core.engineering.TankReinforcementResult
import java.time.LocalDate
import kotlin.math.atan
import kotlin.math.cos

// ─────────────────────────────────────────────────────────────────────────────
// P043 — LIVE RESULT → DRAWING MODEL ADAPTER (Pillar-2 endgame seam).
//
// Mapping of the app's live CalculatorEngine results onto the core
// DrawingModel. Everything is a pure passthrough of engine-computed values
// (counts, diameters, spacings, +/-safety) — the layer NEVER recomputes a
// strength quantity. Only layout geometry mirrors engine/detailing
// conventions, each annotated below:
//   • Effective depth: the engine's own d (beam h−50, stair h−cover,
//     tank wall − 50); slab d = ts − cover − Ø/2 (engine does not expose d).
//   • Concrete cover: legacy ADR-010 conventions (40 beam/column, ECP 50 /
//     other 75 footing, 50 tank, 20/25 stair, 25 slab, 50/65/75
//     retaining-wall by code — all mirroring the screen renderers).
//   • Stair width: 1200 mm assumed (not carried by the engine result).
//   • Stair inclined length: engine's lengthOnSlant = span / cos(atan(riser/tread)).
//   • Slab mesh: engine X ("Main") → short direction, engine Y ("Secondary") →
//     long direction, exactly as the screen's ProfessionalSlabDrawing maps them.
//   • Retaining wall: ENGINE's true proportions (toe B/3, heel = B−toe−t,
//     baseThickness = stemT) and stem-main spacing derived from the engine's
//     per-metre count; distribution Ø10 @ 200 and code-dependent cover mirror
//     the screen renderer's ProfessionalRetainingWallDrawing.
//   • Pile cap: cap B×L×t + flexural mesh both ways via the footing builder;
//     code taken from the screen's selected code string (result carries none),
//     cover 75 mm assumed (cap against soil). Piles not in the core section.
//   • Flat slab: the ENGINE's four strip groups (column/middle × top/bottom)
//     passed through as-is; panel spans the input's mm values (lx = short,
//     ly = long — the screen feeds metres × 1000); cover from the screen's own
//     clearCover input; column-strip band width = engine columnStripWidthX;
//     drop outline from the input drop panel (dropThickness × dropSizeX) — the
//     design result's dropRequired drives nothing in layout (only isSafe).
//   • Shear wall: the plan section (length × thickness) draws the engine's
//     longitudinal steel as one centre row + the horizontal (shear) steel as
//     face rows near both thickness faces; boundary-element and coupling-beam
//     families are scheduled (not re-drawn) in the core builder. L/T flange
//     legs mirror the renderer's schematic proportions. Code + wall shape are
//     passed from the screen (the result carries neither); cover = the screen's
//     clearCover input; story height drives bar supply lengths.
// ─────────────────────────────────────────────────────────────────────────────

object LiveDrawingModel {

    private const val BEAM_COLUMN_COVER_MM = 40.0
    private const val TANK_COVER_MM = 50.0
    private const val STAIR_WIDTH_MM = 1200.0

    // ── Beam ────────────────────────────────────────────────────────────────

    fun beam(
        res: CalculatorEngine.BeamResult,
        spanMm: Double,
        projectName: String,
        drawingNumber: String = "B-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = res.code.toCore()
        // R2 (P044): engine stirrup zones pass straight through (Pillar-2 —
        // no recompute), so the BBS and the DXF elevation carry the real
        // per-zone @spacing from the design engine.
        val result = ReinforcementResult(
            astRequired = 0.0,
            astProvided = 0.0,
            barDiameter = res.reinforcementBottom.diameter.toDouble(),
            numberOfBars = res.reinforcementBottom.numBars.coerceAtLeast(2),
            tiesDiameter = res.stirrups.diameter.toDouble(),
            tiesSpacing = res.stirrups.spacing,
            numLegs = res.stirrups.numLegs,
            isSafe = res.isSafe,
            utilizationRatio = res.utilizationRatio,
            spacing = res.stirrups.spacing,
            description = res.reinforcementBottom.barString,
            zones = res.stirrups.zones.map { z ->
                com.civileg.core.calculations.entities.StirrupZone(
                    name = z.name,
                    startLocation = z.startLocation,
                    endLocation = z.endLocation,
                    spacing = z.spacing,
                    numLegs = z.numLegs,
                    diameter = z.diameter,
                    description = z.description
                )
            }
        )
        val model = DrawingModelBuilder.buildBeam(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "BEAM SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            overallHeight = res.depth,
            overallWidth = res.width,
            effectiveDepth = res.depth - 50.0,
            concreteCover = BEAM_COLUMN_COVER_MM,
            beamLength = spanMm,
            beamResult = result,
            supportTypeName = res.supportType.name,
            appliedMomentKnM = res.appliedMoment,
            appliedShearKn = res.appliedShear
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                beamDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Column ──────────────────────────────────────────────────────────────

    fun column(
        res: CalculatorEngine.ColumnResult,
        heightMm: Double,
        projectName: String,
        drawingNumber: String = "C-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = res.code.toCore()
        val result = ReinforcementResult(
            astRequired = 0.0,
            astProvided = 0.0,
            barDiameter = res.reinforcement.diameter.toDouble(),
            numberOfBars = res.reinforcement.numBars.coerceAtLeast(4),
            tiesDiameter = res.stirrups.diameter.toDouble(),
            tiesSpacing = res.stirrups.spacing,
            numLegs = res.stirrups.numLegs,
            isSafe = res.isSafe,
            utilizationRatio = res.utilizationRatio,
            spacing = res.stirrups.spacing,
            description = res.reinforcement.barString
        )
        val model = DrawingModelBuilder.buildColumn(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "COLUMN SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            overallHeight = res.depth,
            overallWidth = res.width,
            concreteCover = BEAM_COLUMN_COVER_MM,
            columnLength = heightMm,
            columnResult = result
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                columnDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Footing ─────────────────────────────────────────────────────────────

    fun footing(
        res: CalculatorEngine.FootingResult,
        projectName: String,
        drawingNumber: String = "F-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = res.code.toCore()
        val cover = if (res.code.name.contains("ECP", true) || res.code.name.contains("EGYPTIAN", true)) 50.0 else 75.0
        val diaB = if (res.reinforcementBottom.diameter > 0) res.reinforcementBottom.diameter.toDouble()
                   else res.barDiameter.toDouble()
        val nX = maxOf(2, res.barsX)
        val nY = maxOf(2, res.barsY)
        val distribution = if (res.reinforcementTopX > 0 && res.topBarDiameter > 0)
            FootingDistributionSteel(
                barsPerMeter = res.reinforcementTopY,
                diameterMm = res.topBarDiameter.toDouble(),
                spacingMm = ((res.width - 2 * cover) / (maxOf(2, res.reinforcementTopY) - 1)).coerceAtLeast(50.0),
                astRequired = 0.0,
                astProvided = 0.0
            )
        else null

        val model = DrawingModelBuilder.buildFooting(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "FOOTING SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            lengthMm = res.length,
            widthMm = res.width,
            thicknessMm = res.thickness,
            concreteCover = cover,
            footing = FootingReinforcementResult(
                shortBarSelection = nX to diaB,
                longBarSelection = nY to diaB,
                shortSpacingMm = ((res.length - 2 * cover) / (nX - 1)).coerceAtLeast(50.0),
                longSpacingMm = ((res.width - 2 * cover) / (nY - 1)).coerceAtLeast(50.0),
                bottomAsProvided = 0.0,
                distribution = distribution,
                isSafe = res.isSafe,
                warnings = emptyList()
            )
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                footingDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Tank ────────────────────────────────────────────────────────────────

    fun tank(
        res: CalculatorEngine.TankResult,
        projectName: String,
        drawingNumber: String = "T-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = res.code.toCore()
        val model = DrawingModelBuilder.buildTank(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "TANK WALL & BASE SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            lengthMm = res.length * 1000.0,
            widthMm = res.width * 1000.0,
            heightMm = res.height * 1000.0,
            wallThicknessMm = res.wallThickness,
            baseThicknessMm = res.baseThickness,
            effectiveDepthMm = res.wallThickness - TANK_COVER_MM,
            concreteCoverMm = TANK_COVER_MM,
            tank = TankReinforcementResult(
                wallDiameter = res.wallReinforcement.diameter.toDouble(),
                wallSpacingMm = res.wallReinforcement.spacing.takeIf { it > 0 } ?: 200.0,
                wallHorizontalDiameter = 0.0,
                wallHorizontalSpacingMm = 0.0,
                baseDiameter = res.baseReinforcement.diameter.toDouble(),
                baseSpacingMm = res.baseReinforcement.spacing.takeIf { it > 0 } ?: 200.0,
                isSafe = res.isSafe,
                warnings = emptyList()
            )
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                tankDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Stair ───────────────────────────────────────────────────────────────

    fun stair(
        res: CalculatorEngine.StairResult,
        projectName: String,
        drawingNumber: String = "S-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = res.code.toCore()
        val cover = if (coreCode == CoreDesignCode.ECP) 20.0 else 25.0
        val inclinedLengthMm = if (res.tread > 0)
            res.span * 1000.0 / cos(atan(res.riser / res.tread))
        else res.span * 1000.0

        val model = DrawingModelBuilder.buildStair(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "STAIR WAIST SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            waistThicknessMm = res.thickness,
            effectiveDepthMm = res.thickness - cover,
            concreteCoverMm = cover,
            stairWidthMm = STAIR_WIDTH_MM,
            inclinedLengthMm = inclinedLengthMm,
            stair = StairReinforcementResult(
                mainDiameter = res.reinforcement.diameter.toDouble(),
                mainSpacingMm = res.reinforcement.spacing.takeIf { it > 0 } ?: 150.0,
                distributionDiameter = res.distributionReinforcement.diameter.toDouble(),
                distributionSpacingMm = res.distributionReinforcement.spacing.takeIf { it > 0 } ?: 250.0,
                isSafe = res.isSafe,
                warnings = emptyList()
            )
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                stairDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Slab ────────────────────────────────────────────────────────────────

    /**
     * Two-way solid slab model. Layout mirrors the screen renderer
     * [ProfessionalSlabDrawing] exactly: clear spans are the screen's
     * shortSpan/longSpan (metres → mm), cover = 25 (SlabScreen.kt:502), and the
     * mesh directions map reinforcementMain (engine X / "Main (X)") to the
     * short direction and reinforcementSecondary (engine Y) to the long
     * direction — the same main/sec mapping the screen renderer applies.
     * Effective depth d = ts − cover − Ø/2 is derived from engine geometry.
     */
    fun slab(
        res: CalculatorEngine.SlabResult,
        shortSpanMm: Double,
        longSpanMm: Double,
        projectName: String,
        drawingNumber: String = "SL-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = res.code.toCore()
        val cover = 25.0
        val sSpacing = res.reinforcementMain.spacing.takeIf { it > 0 } ?: 200.0
        val lSpacing = res.reinforcementSecondary.spacing.takeIf { it > 0 } ?: 200.0
        val sDia = res.reinforcementMain.diameter.toDouble()
        val lDia = res.reinforcementSecondary.diameter.toDouble()

        val model = DrawingModelBuilder.buildSlab(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "SLAB SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            thickness = res.thickness,
            effectiveDepth = res.thickness - cover - sDia / 2.0,
            concreteCover = cover,
            shortSpanMm = shortSpanMm,
            longSpanMm = longSpanMm,
            slab = SlabReinforcementResult(
                shortBarSelection = (1000.0 / sSpacing).toInt() to sDia,
                longBarSelection = (1000.0 / lSpacing).toInt() to lDia,
                shortSpacingMm = sSpacing,
                longSpacingMm = lSpacing,
                isSafe = res.isSafe,
                warnings = emptyList()
            )
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                slabDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Retaining Wall ──────────────────────────────────────────────────────

    /**
     * Cantilever retaining-wall model. Geometry mirrors the screen renderer
     * [ProfessionalRetainingWallDrawing] (RetainingWallScreen.kt:313-335),
     * which uses the ENGINE's true proportions — not the crude PDF-fallback
     * factors: baseThickness = stemT (engine baseT), toe = B/3, heel =
     * B − toe − stemT (CalculatorEngine.designRetainingWall). Cover is the
     * screen renderer's code-dependent value (75 ACI / 65 SBC / 50 ECP).
     * Steel passthrough:
     *   • Stem main is engine count-based (numStemBars per metre, spacing = 0 —
     *     designRetainingWall line 2015), so per-metre spacing derives from the
     *     count the renderer displays.  • Distribution follows the screen
     *     renderer's Ø10 @ 200.  • Base (toe + heel) takes the engine's single
     *     min(toe,heel) spacing, applied to both faces as the renderer does.
     */
    fun retainingWall(
        res: CalculatorEngine.RetainingWallResult,
        projectName: String,
        drawingNumber: String = "RW-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = res.code.toCore()
        val toeLengthMm = res.baseWidth / 3.0
        val heelLengthMm = (res.baseWidth - toeLengthMm - res.stemThickness).coerceAtLeast(0.0)
        val stemFreeHeightMm = (res.height * 1000.0 - res.stemThickness).coerceAtLeast(100.0)
        val cover = when (res.code) {
            CalculatorEngine.DesignCode.ACI -> 75.0
            CalculatorEngine.DesignCode.SAUDI -> 65.0
            else -> 50.0
        }
        val stemPerMetre = res.stemReinforcement.numBars.coerceAtLeast(5)
        val stemSpacingMm = 1000.0 / stemPerMetre
        val baseSpacingMm = res.baseReinforcement.spacing.takeIf { it > 0 } ?: 200.0
        val baseDia = res.baseReinforcement.diameter.toDouble()
        val baseCount = (1000.0 / baseSpacingMm).toInt().coerceAtLeast(2)

        val model = DrawingModelBuilder.buildRetainingWall(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "RETAINING WALL SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            wallHeightMm = res.height * 1000.0,
            stemHeightMm = stemFreeHeightMm,
            stemBaseThicknessMm = res.stemThickness,
            baseWidthMm = res.baseWidth,
            baseThicknessMm = res.stemThickness,
            toeLengthMm = toeLengthMm,
            heelLengthMm = heelLengthMm,
            concreteCoverMm = cover,
            wall = RetainingWallReinforcementResult(
                stemMainCount = stemPerMetre,
                stemMainDiameter = res.stemReinforcement.diameter.toDouble(),
                stemMainSpacingMm = stemSpacingMm,
                distributionBarsCount = (stemFreeHeightMm / 200.0).toInt(),
                distributionDiameter = 10.0,
                distributionSpacingMm = 200.0,
                toeBarsCount = baseCount,
                toeDiameter = baseDia,
                toeSpacingMm = baseSpacingMm,
                heelBarsCount = baseCount,
                heelDiameter = baseDia,
                heelSpacingMm = baseSpacingMm,
                isSafe = res.utilizationRatio <= 1.0,
                warnings = emptyList()
            )
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                retainingWallDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Pile Foundation (cap section) ───────────────────────────────────────

    /**
     * Pile-cap model, drawn as the cap section via the shared footing builder.
     * Layout mirrors the cap box of the screen renderer [ProfessionalPileDrawing]:
     * cap B×L×t and the engine's flexural mesh (bar count + diameter + spacing)
     * run in BOTH directions, exactly as the cap reinforcement is designed.
     * Pure passthrough — only the code + cover are assumed: PileDesignResult does
     * not carry the resolved design code nor capConcreteCover, so the screen's
     * selected code string is passed through (default ECP) and cover is 75 mm
     * (conservative for caps cast against soil). Piles themselves are not part
     * of the core DrawingModel section — the sheet is the cap (footing) section.
     */
    fun pileFoundation(
        res: PileDesignResult,
        projectName: String,
        designCode: String = "ECP",
        drawingNumber: String = "PF-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = when (designCode.uppercase().take(3)) {
            "ACI" -> CoreDesignCode.ACI
            "SBC" -> CoreDesignCode.SBC
            else -> CoreDesignCode.ECP
        }
        val cap = res.capResult
        val flex = cap.flexuralReinforcement
        val bars = flex.bars.coerceAtLeast(2)
        val spacing = flex.spacing.toDouble().coerceAtLeast(50.0)
        val dia = flex.diameter.toDouble()

        val model = DrawingModelBuilder.buildFooting(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "PILE CAP SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            lengthMm = cap.capLength,
            widthMm = cap.capWidth,
            thicknessMm = cap.capThickness,
            concreteCover = 75.0,
            footing = FootingReinforcementResult(
                shortBarSelection = bars to dia,
                longBarSelection = bars to dia,
                shortSpacingMm = spacing,
                longSpacingMm = spacing,
                bottomAsProvided = 0.0,
                distribution = null,
                isSafe = res.isSafe,
                warnings = emptyList()
            )
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                footingDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Flat Slab (strip section) ─────────────────────────────────────────

    /**
     * Flat-slab panel section via the strip-aware core builder. Pure
     * passthrough of the engine's [FlatSlabResult]: the four strip groups
     * (columnStripTop/Bottom, middleStripTop/Bottom) map 1:1 onto the core
     * strip reinforcement; panel spans are the input's mm values (lx = short,
     * ly = long); cover is the screen's own clearCover input; the drop-panel
     * outline mirrors the screen renderer (input dropThickness × dropSizeX,
     * only when the user entered a drop); column-strip band width =
     * engine columnStripWidthX (clamped in the emitter). isSafe maps to the
     * model's overall status. Only the code string is assumed — the result
     * does not carry it, so it is passed through from the screen (default ECP).
     */
    fun flatSlab(
        res: FlatSlabResult,
        input: FlatSlabInput,
        projectName: String,
        designCode: String = "ECP",
        drawingNumber: String = "FS-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = when (designCode.uppercase().take(3)) {
            "ACI" -> CoreDesignCode.ACI
            "SBC" -> CoreDesignCode.SBC
            else -> CoreDesignCode.ECP
        }
        val shortSpanMm = input.lx.coerceAtLeast(1.0)
        val longSpanMm = input.ly.coerceAtLeast(1.0)
        val cover = input.clearCover.takeIf { it > 0 } ?: 25.0
        val topDia = res.columnStripTopRebar.diameter.toDouble().coerceAtLeast(1.0)

        fun strip(r: com.civileg.app.domain.RebarResult): FlatSlabStripReinforcement =
            FlatSlabStripReinforcement(
                barSelection = r.bars.coerceAtLeast(1) to r.diameter.toDouble().coerceAtLeast(1.0),
                spacingMm = r.spacing.toDouble().coerceAtLeast(50.0)
            )

        val model = DrawingModelBuilder.buildFlatSlab(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "FLAT SLAB STRIP SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            thickness = input.slabThickness.coerceAtLeast(50.0),
            effectiveDepth = input.slabThickness.coerceAtLeast(50.0) - cover - topDia / 2.0,
            concreteCover = cover,
            shortSpanMm = shortSpanMm,
            longSpanMm = longSpanMm,
            columnStripWidthMm = res.columnStripWidthX,
            dropDepthMm = if (input.dropSizeX > 0 && input.dropThickness > 0) input.dropThickness else 0.0,
            dropSizeMm = if (input.dropSizeX > 0 && input.dropThickness > 0) input.dropSizeX else 0.0,
            slab = FlatSlabReinforcementResult(
                columnStripTop = strip(res.columnStripTopRebar),
                columnStripBottom = strip(res.columnStripBotRebar),
                middleStripTop = strip(res.middleStripTopRebar),
                middleStripBottom = strip(res.middleStripBotRebar),
                isSafe = res.isSafe,
                warnings = res.warnings
            )
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                slabDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Shear Wall (horizontal section) ─────────────────────────────────

    /**
     * Shear-wall horizontal-section model via the dedicated core builder. Pure
     * passthrough of the engine's [ShearWallResult]: the longitudinal count
     * (webVertical), the horizontal (shear) layer spacing, the boundary-element
     * steel/ties and the coupled-beam diagonal/transverse steel each map 1:1
     * onto the core families; the wall length × thickness plan cut is the
     * input's mm geometry and cover is the screen's clearCover input. Only
     * layout is assumed, annotated in the core builder: the boundary end zone
     * extent uses the input's endZoneLength when entered, else the renderer's
     * 12%-of-length schematic; the story height drives bar supply lengths
     * (story + one splice). The wall shape (Rectangular/L/T) and the resolved
     * code string are passed through from the screen (result carries neither).
     */
    fun shearWall(
        res: ShearWallResult,
        input: ShearWallInput,
        wallShape: String = "Rectangular",
        projectName: String,
        designCode: String = "ECP",
        drawingNumber: String = "SW-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = when (designCode.uppercase().take(3)) {
            "ACI" -> CoreDesignCode.ACI
            "SBC" -> CoreDesignCode.SBC
            else -> CoreDesignCode.ECP
        }
        val cover = input.clearCover.takeIf { it > 0 } ?: 25.0
        val vertical = res.verticalReinforcement
        val horizontal = res.horizontalReinforcement
        val boundary = res.boundaryElementReinforcement
        val needBoundaryZone = boundary != null &&
            res.boundaryElementType != BoundaryElementType.NONE
        val boundaryLen = if (needBoundaryZone)
            (if (input.endZoneLength > 0) input.endZoneLength
             else input.wallLength.coerceAtLeast(1.0) * 0.12)
        else 0.0
        val cbClearSpan = if (input.couplingBeamClearSpan > 0) input.couplingBeamClearSpan
            else input.couplingBeamLength
        val cbHeight = input.couplingBeamHeight.coerceAtLeast(1.0)

        val wall = ShearWallReinforcementResult(
            vertical = ShearWallVerticalReinforcement(
                count = vertical.bars.coerceAtLeast(2),
                diameterMm = vertical.diameter.toDouble().coerceAtLeast(1.0),
                spacingMm = vertical.spacing.toDouble().coerceAtLeast(50.0)
            ),
            horizontal = ShearWallHorizontalReinforcement(
                diameterMm = horizontal.diameter.toDouble().coerceAtLeast(1.0),
                spacingMm = horizontal.spacing.toDouble().coerceAtLeast(50.0)
            ),
            boundary = boundary?.let {
                ShearWallBoundaryReinforcement(
                    bars = it.bars.coerceAtLeast(2),
                    diameterMm = it.diameter.toDouble().coerceAtLeast(1.0),
                    spacingMm = it.spacing.toDouble().coerceAtLeast(50.0)
                )
            },
            couplingBeam = res.couplingBeamResult?.let { cb ->
                ShearWallCouplingReinforcement(
                    diagonalBars = cb.diagonalBars.coerceAtLeast(2),
                    diagonalDiameterMm = cb.diagonalBarDiameter.toDouble().coerceAtLeast(1.0),
                    transverseDiameterMm = cb.transverseBarsDiameter.toDouble().coerceAtLeast(1.0),
                    transverseSpacingMm = cb.transverseBarsSpacing.toDouble().coerceAtLeast(50.0)
                )
            },
            isSafe = res.isSafe,
            warnings = emptyList()
        )

        val model = DrawingModelBuilder.buildShearWall(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "SHEAR WALL SECTION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            wallLengthMm = input.wallLength.coerceAtLeast(1.0),
            wallThicknessMm = input.wallThickness.coerceAtLeast(1.0),
            storyHeightMm = input.wallHeight.coerceAtLeast(1.0),
            couplingBeamClearSpanMm = cbClearSpan,
            couplingBeamHeightMm = cbHeight,
            boundaryElementLengthMm = boundaryLen,
            wallShape = wallShape,
            concreteCoverMm = cover,
            wall = wall
        )
        val b = model.bounds
        return model.copy(
            dimensions = DimensionSet(
                shearWallDimensions = sectionDims(b, fmt(b.maxX - b.minX), fmt(b.maxY - b.minY))
            )
        )
    }

    // ── Steel member ────────────────────────────────────────────────────

    /**
     * Steel-member model via the dedicated core builder. Pure passthrough of the
     * engine's [SteelMemberResult]: section name, member length (mm), and the
     * profile quantities depth/width/web/flange are the engine's own; the
     * section's code citation (AISC 360-B4 / ECP 205-3 …) passes through as-is.
     * The design-code label for the title block mirrors the member PDF's
     * convention (ACI→"AISC 360-16", SAUDI→"SBC 306", else→"ECP 205-2007") and
     * the core code family is the same mapping used by the concrete adapters.
     */
    // ── Steel member ─────────────────────────────────────────────────────────

    fun steelMember(
        res: SteelMemberResult,
        lengthMm: Double,
        steelCode: CalculatorEngine.DesignCode,
        projectName: String,
        drawingNumber: String = "ST-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = steelCode.toCore()
        val codeLabel = when (steelCode) {
            CalculatorEngine.DesignCode.ACI -> "AISC 360-16"
            CalculatorEngine.DesignCode.SAUDI -> "SBC 306"
            else -> "ECP 205-2007"
        }
        val member = SteelMemberReinforcementResult(
            sectionName = res.sectionType.sectionName,
            memberType = res.memberType.name,
            memberLengthMm = lengthMm.coerceAtLeast(1.0),
            depthMm = res.sectionType.depth.coerceAtLeast(1.0),
            widthMm = res.sectionType.width.coerceAtLeast(1.0),
            webThicknessMm = res.sectionType.webThickness.coerceAtLeast(1.0),
            flangeThicknessMm = res.sectionType.flangeThickness.coerceAtLeast(1.0),
            utilizationRatio = res.utilizationRatio,
            isSafe = res.isSafe,
            codeReference = res.sectionType.codeReference,
            warnings = res.warnings
        )
        return DrawingModelBuilder.buildSteelMember(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(
                projectName,
                "STEEL ${res.memberType.name}",
                drawingNumber,
                date,
                coreCode,
                codeLabel
            ),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            member = member,
            steelMembers = listOf(
                SteelMemberMark(
                    mark = "${res.memberType.name.take(3)}-1",
                    sectionName = member.sectionName,
                    lengthMm = member.memberLengthMm,
                    quantity = 1
                )
            )
        )
    }

    // ── Frame ─────────────────────────────────────────────────────────────────

    /**
     * Frame elevation (P043G). Mirrors the screen's longitudinal-section view
     * (FrameDrawingCanvas mode 1): the member centreline topology is carried
     * as rectangles — columns thick via their section WIDTH, beams thin via
     * their section DEPTH; steel members get a schematic 250 mm band because the
     * steel design result carries no profile dimensions (same as the canvas's
     * uniform-thickness steel lines). Base support symbols, bay-width +
     * story-height dimensions and the member schedule row are all derived from
     * the model. Pure passthrough of the analysis topology + the engine's
     * per-member verdicts ([FrameAnalysisResult]); the code citation resolves
     * from the frame settings' design code (concrete-edition label).
     */
    fun frame(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        result: FrameAnalysisResult,
        settings: FrameAnalysisSettings,
        projectName: String,
        drawingNumber: String = "FR-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val coreCode = settings.designCode
        val codeRef = CodeVersionRegistry.defaultFor(coreCode).label
        val nodeById = nodes.associateBy { it.id }

        fun bandOf(m: FrameMember): Double = when {
            m.materialType == FrameMaterialType.Steel -> 250.0
            m.memberType == FrameMemberType.Column -> (m.concreteSection?.width ?: 300.0).coerceAtLeast(1.0)
            m.memberType == FrameMemberType.Beam -> (m.concreteSection?.depth ?: 500.0).coerceAtLeast(1.0)
            else -> (m.concreteSection?.width ?: 300.0).coerceAtLeast(1.0)
        }

        fun sectionNameOf(m: FrameMember): String = when {
            m.materialType == FrameMaterialType.Steel ->
                m.steelSectionName?.takeIf { it.isNotBlank() } ?: "STEEL"
            else -> {
                val w = m.concreteSection?.width ?: 300.0
                val d = m.concreteSection?.depth ?: 500.0
                "${fmt(w)}x${fmt(d)}"
            }
        }

        val concreteRes = result.concreteDesignResults.associateBy { it.memberId }
        val steelRes = result.steelDesignResults.associateBy { it.memberId }

        fun verdict(m: FrameMember): Pair<Boolean, Double> {
            if (m.materialType == FrameMaterialType.Steel) {
                val r = steelRes[m.id]
                return (r?.isSafe ?: true) to (r?.combinedUtilization ?: 0.0)
            }
            val r = concreteRes[m.id]
            return (r?.isSafe ?: true) to maxOf(r?.momentUtilization ?: 0.0, r?.shearUtilization ?: 0.0)
        }

        val marks = members.mapIndexed { i, m ->
            FrameMemberMark(
                mark = "FM-${i + 1}",
                memberType = m.memberType.name.uppercase(),
                sectionName = sectionNameOf(m),
                lengthMm = (m.getLength(nodes) * 1000.0).coerceAtLeast(1.0),
                quantity = 1
            )
        }

        val detail = FrameAnalysisDetailResult(
            nodes = nodes.map { FrameNodeDetail(it.x * 1000.0, it.y * 1000.0, it.support.name.uppercase()) },
            members = members.mapNotNull { m ->
                val ni = nodeById[m.nodeI] ?: return@mapNotNull null
                val nj = nodeById[m.nodeJ] ?: return@mapNotNull null
                val (safe, util) = verdict(m)
                FrameMemberDetail(
                    memberId = m.id,
                    x1Mm = ni.x * 1000.0, y1Mm = ni.y * 1000.0,
                    x2Mm = nj.x * 1000.0, y2Mm = nj.y * 1000.0,
                    materialType = m.materialType.name.uppercase(),
                    memberType = m.memberType.name.uppercase(),
                    bandMm = bandOf(m),
                    sectionName = sectionNameOf(m),
                    isSafe = safe,
                    utilization = util
                )
            },
            isSafe = members.all { verdict(it).first },
            codeReference = codeRef
        )

        return DrawingModelBuilder.buildFrame(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "FRAME ELEVATION", drawingNumber, date, coreCode),
            code = coreCode,
            edition = CodeVersionRegistry.defaultFor(coreCode),
            detail = detail,
            frameMembers = marks
        )
    }

    /**
     * P043H — Seismic chart sheet. Pure passthrough of the app-side seismic
     * engine results: the response spectrum curve (T, Sa), the per-floor lateral
     * forces, the base shear with its zone/soil/importance/reduction factors and
     * the engine's own code citation. No strength math is recomputed here — only
     * the builder's paper placement of the curve and bars. The code (one of the
     * seismic screen's design codes) drives the title block citation.
     */
    fun seismic(
        baseShearResult: SeismicBaseShearResult,
        spectrumValues: List<SpectrumValue>,
        floorForces: List<SeismicForceDistribution>,
        fundamentalPeriod: Double,
        spectralAccel: Double,
        code: CoreDesignCode,
        projectName: String,
        drawingNumber: String = "SE-01",
        date: String = LocalDate.now().toString()
    ): DrawingModel {
        val detail = SeismicDetailResult(
            spectrumPoints = spectrumValues.map { SeismicSpectrumPoint(it.period, it.spectralAcceleration) },
            floorForces = floorForces.map {
                SeismicFloorForcePoint(
                    floorIndex = it.floorIndex,
                    floorHeight = it.floorHeight,
                    forceKn = it.lateralForce,
                    storyShearKn = it.storyShear
                )
            },
            baseShearKn = baseShearResult.baseShear,
            zoneFactor = baseShearResult.zoneFactor,
            soilFactor = baseShearResult.soilFactor,
            importanceFactor = baseShearResult.importanceFactor,
            responseModification = baseShearResult.responseModification,
            fundamentalPeriod = fundamentalPeriod,
            spectralAccel = spectralAccel,
            calculationFormula = baseShearResult.calculationFormula,
            codeReference = baseShearResult.codeReference,
            isSafe = baseShearResult.baseShear > 0,
            warnings = baseShearResult.warnings
        )
        return DrawingModelBuilder.buildSeismic(
            project = projectName,
            drawingNumber = drawingNumber,
            sheetNumber = "1/1",
            titleBlock = titleBlock(projectName, "SEISMIC ANALYSIS", drawingNumber, date, code),
            code = code,
            edition = CodeVersionRegistry.defaultFor(code),
            detail = detail
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun CalculatorEngine.DesignCode.toCore(): CoreDesignCode = when (this) {
        CalculatorEngine.DesignCode.EGYPTIAN -> CoreDesignCode.ECP
        CalculatorEngine.DesignCode.ACI -> CoreDesignCode.ACI
        CalculatorEngine.DesignCode.SAUDI -> CoreDesignCode.SBC
    }

    private fun titleBlock(
        project: String,
        drawingTitle: String,
        drawingNumber: String,
        date: String,
        coreCode: CoreDesignCode,
        codeLabel: String? = null
    ): TitleBlock = TitleBlock(
        project = project,
        drawingTitle = drawingTitle,
        drawingNumber = drawingNumber,
        date = date,
        scale = "",
        designCode = codeLabel ?: CodeVersionRegistry.defaultFor(coreCode).label,
        revision = "00",
        status = DrawingStatus.FOR_CONSTRUCTION,
        sheet = "1/1"
    )

    /** Width (below) + height (right) dimension lines for a section. */
    private fun sectionDims(b: com.civileg.core.calculations.entities.BoundingBox, w: String, h: String): List<DimensionLine> = listOf(
        DimensionLine(
            start = Point2D(b.minX, b.minY - 20.0),
            end = Point2D(b.maxX, b.minY - 20.0),
            value = w
        ),
        DimensionLine(
            start = Point2D(b.maxX + 20.0, b.minY),
            end = Point2D(b.maxX + 20.0, b.maxY),
            value = h
        )
    )

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else "%.1f".format(v).trimEnd('0').trimEnd('.')
}