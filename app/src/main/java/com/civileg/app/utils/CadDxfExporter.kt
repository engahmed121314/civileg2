package com.civileg.app.utils

import android.content.Context
import android.util.Log
import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import com.civileg.app.domain.calculations.base.SeismicForceDistribution
import com.civileg.app.domain.calculations.base.SpectrumValue
import com.civileg.app.utils.detailing.DrawingModelExporter
import com.civileg.app.utils.detailing.LiveDrawingModel
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.DrawingModelBuilder
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ADR-004 canonical DXF bridge — P043 (Pillar-2 endgame).
 *
 * Live engine results are mapped onto the core [DrawingModel] by
 * [LiveDrawingModel] (pure passthrough of engine values — no recompute), then
 * the single-sheet AC1027 drawing is produced by [DrawingModelExporter]:
 * section geometry + dimensions + title block + grouped bar schedule, ALL
 * derived from the model. Nothing is independently computed here.
 *
 * The legacy multi-sheet package path (CalculatorDetailingV4 →
 * CalculatorCadExporterV7) is untouched and remains available; live buttons no
 * longer call it. All text written to DXF is ENGLISH-ONLY (ADR-009).
 */
object CadDxfExporter {

    private const val TAG = "CadDxfExporter"

    data class DxfExportOutcome(
        val directory: File,
        val sheets: Int,
        val qaPassed: Boolean,
        val issues: List<String>
    )

    // ────────────────────────────────────────────────────────────
    // Public entries — P043 model-derived single sheet
    // ────────────────────────────────────────────────────────────

    fun exportBeam(
        context: Context,
        res: CalculatorEngine.BeamResult,
        spanMm: Double,
        projectName: String = "CIVILEG BEAM"
    ): DxfExportOutcome? = runModelExport(
        context, "BEAM", LiveDrawingModel.beam(res, spanMm, projectName.take(28))
    )

    fun exportColumn(
        context: Context,
        res: CalculatorEngine.ColumnResult,
        heightMm: Double,
        projectName: String = "CIVILEG COLUMN"
    ): DxfExportOutcome? = runModelExport(
        context, "COLUMN", LiveDrawingModel.column(res, heightMm, projectName.take(28))
    )

    fun exportFooting(
        context: Context,
        res: CalculatorEngine.FootingResult,
        projectName: String = "CIVILEG FOOTING"
    ): DxfExportOutcome? = runModelExport(
        context, "FOOTING", LiveDrawingModel.footing(res, projectName.take(28))
    )

    fun exportTank(
        context: Context,
        res: CalculatorEngine.TankResult,
        projectName: String = "CIVILEG TANK"
    ): DxfExportOutcome? = runModelExport(
        context, "TANK", LiveDrawingModel.tank(res, projectName.take(28))
    )

    fun exportStair(
        context: Context,
        res: CalculatorEngine.StairResult,
        projectName: String = "CIVILEG STAIR"
    ): DxfExportOutcome? = runModelExport(
        context, "STAIR", LiveDrawingModel.stair(res, projectName.take(28))
    )

    fun exportSlab(
        context: Context,
        res: CalculatorEngine.SlabResult,
        shortSpanMm: Double,
        longSpanMm: Double,
        projectName: String = "CIVILEG SLAB"
    ): DxfExportOutcome? = runModelExport(
        context, "SLAB", LiveDrawingModel.slab(res, shortSpanMm, longSpanMm, projectName.take(28))
    )

    fun exportRetainingWall(
        context: Context,
        res: CalculatorEngine.RetainingWallResult,
        projectName: String = "CIVILEG RETAINING WALL"
    ): DxfExportOutcome? = runModelExport(
        context, "RETAINING_WALL", LiveDrawingModel.retainingWall(res, projectName.take(28))
    )

    fun exportPileFoundation(
        context: Context,
        res: com.civileg.app.domain.PileDesignResult,
        designCode: String = "ECP",
        projectName: String = "CIVILEG PILE FOUNDATION"
    ): DxfExportOutcome? = runModelExport(
        context, "PILE_FOUNDATION",
        LiveDrawingModel.pileFoundation(res, projectName.take(28), designCode)
    )

    fun exportFlatSlab(
        context: Context,
        res: com.civileg.app.domain.FlatSlabResult,
        input: com.civileg.app.domain.FlatSlabInput,
        designCode: String = "ECP",
        projectName: String = "CIVILEG FLAT SLAB"
    ): DxfExportOutcome? = runModelExport(
        context, "FLAT_SLAB",
        LiveDrawingModel.flatSlab(res, input, projectName.take(28), designCode)
    )

    fun exportShearWall(
        context: Context,
        res: com.civileg.app.domain.ShearWallResult,
        input: com.civileg.app.domain.ShearWallInput,
        wallShape: String = "Rectangular",
        designCode: String = "ECP",
        projectName: String = "CIVILEG SHEAR WALL"
    ): DxfExportOutcome? = runModelExport(
        context, "SHEAR_WALL",
        LiveDrawingModel.shearWall(res, input, wallShape, projectName.take(28), designCode)
    )

    fun exportSteelMember(
        context: Context,
        res: com.civileg.app.domain.entities.SteelMemberResult,
        lengthMm: Double,
        steelCode: CalculatorEngine.DesignCode,
        projectName: String = "CIVILEG STEEL MEMBER"
    ): DxfExportOutcome? = runModelExport(
        context, "STEEL_MEMBER",
        LiveDrawingModel.steelMember(res, lengthMm, steelCode, projectName.take(28))
    )

    fun exportFrame(
        context: Context,
        nodes: List<com.civileg.app.domain.entities.FrameNode>,
        members: List<com.civileg.app.domain.entities.FrameMember>,
        result: com.civileg.app.domain.entities.FrameAnalysisResult,
        settings: com.civileg.app.domain.entities.FrameAnalysisSettings,
        projectName: String = "CIVILEG FRAME"
    ): DxfExportOutcome? = runModelExport(
        context, "FRAME",
        LiveDrawingModel.frame(nodes, members, result, settings, projectName.take(28))
    )

    fun exportSeismic(
        context: Context,
        baseShearResult: SeismicBaseShearResult,
        spectrumValues: List<SpectrumValue>,
        floorForces: List<SeismicForceDistribution>,
        fundamentalPeriod: Double,
        spectralAccel: Double,
        code: DesignCode,
        projectName: String = "CIVILEG SEISMIC"
    ): DxfExportOutcome? = runModelExport(
        context, "SEISMIC",
        LiveDrawingModel.seismic(
            baseShearResult, spectrumValues, floorForces,
            fundamentalPeriod, spectralAccel, code, projectName.take(28)
        )
    )

    /** First DXF sheet path — convenient for share/open intents. */
    fun firstSheetFile(result: DxfExportOutcome): File? =
        result.directory.listFiles { f -> f.extension.equals("dxf", true) }
            ?.minByOrNull { it.name }

    // ────────────────────────────────────────────────────────────
    // Shared runner — single point for the model-derived write
    // ────────────────────────────────────────────────────────────

    private fun runModelExport(
        context: Context,
        tag: String,
        model: com.civileg.core.calculations.entities.DrawingModel
    ): DxfExportOutcome? = try {
        require(!DrawingModelBuilder.validate(model).hasInvalid) {
            "CadDxfExporter: $tag model contains NaN/Inf — validate() must pass"
        }
        val dir = freshDir(context, tag)
        val content = DrawingModelExporter.writeDxfWithSchedule(model)
        File(dir, "$tag.dxf").writeText(content, StandardCharsets.UTF_8)
        DxfExportOutcome(dir, sheets = 1, qaPassed = true, issues = emptyList())
    } catch (e: Exception) {
        Log.e(TAG, "$tag DXF export failed", e)
        // Show actual error in a toast for easier debugging
        (context as? android.app.Activity)?.runOnUiThread {
             android.widget.Toast.makeText(context, "DXF Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        null
    }

    private fun freshDir(context: Context, tag: String): File {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        return File(base, "CivilEG_DXF/${tag}_$stamp").apply { mkdirs() }
    }
}