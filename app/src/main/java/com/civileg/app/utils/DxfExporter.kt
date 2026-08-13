package com.civileg.app.utils

import com.civileg.app.domain.entities.*
import java.util.Locale

/**
 * Legacy DXF exporter — kept for backward-compat only.
 * All active DXF generation has been migrated to [DxfExportEngine].
 */
@Deprecated("Use DxfExportEngine instead — this object is retained only for API compat.")
object DxfExporter {
    private fun isAr() = LocaleHelper.isArabic()
    private fun tr(en: String, ar: String): String = if (isAr()) ar else en

    // ─── FOOTING (legacy API) ─────────────────────────────────────────
    fun exportFootingDetailed(
        result: CalculatorEngine.FootingResult,
        colWidth: Double,
        colDepth: Double,
        outputPath: String
    ): java.io.File {
        val dxf = DxfExportEngine.generateFootingDxf(result, colWidth, colDepth)
        val file = java.io.File(outputPath)
        file.writeText(dxf)
        return file
    }

    // ─── BEAM (legacy API) ─────────────────────────────────────────────
    fun exportBeamDetailed(
        result: CalculatorEngine.BeamResult,
        width: Double,
        height: Double,
        span: Double,
        outputPath: String
    ): java.io.File {
        val dxf = DxfExportEngine.generateBeamDxf(result, span)
        val file = java.io.File(outputPath)
        file.writeText(dxf)
        return file
    }

    // ─── COLUMN (legacy API) ───────────────────────────────────────────
    fun exportColumnDetailed(
        result: CalculatorEngine.ColumnResult,
        width: Double,
        depth: Double,
        height: Double,
        outputPath: String
    ): java.io.File {
        val dxf = DxfExportEngine.generateColumnDxf(result, height / 1000.0)
        val file = java.io.File(outputPath)
        file.writeText(dxf)
        return file
    }

    // ─── SLAB (legacy API) ─────────────────────────────────────────────
    fun exportSlabDetailed(
        result: CalculatorEngine.SlabResult,
        lx: Double,
        ly: Double,
        outputPath: String
    ): java.io.File {
        val dxf = DxfExportEngine.generateSlabDxf(result, lx, ly)
        val file = java.io.File(outputPath)
        file.writeText(dxf)
        return file
    }

    // ─── STAIR (legacy API) ────────────────────────────────────────────
    fun exportStairDetailed(
        result: CalculatorEngine.StairResult,
        outputPath: String
    ): java.io.File {
        val dxf = DxfExportEngine.generateStairDxf(result)
        val file = java.io.File(outputPath)
        file.writeText(dxf)
        return file
    }

    // ─── TANK (legacy API) ─────────────────────────────────────────────
    fun exportTankDetailed(
        result: CalculatorEngine.TankResult,
        outputPath: String
    ): java.io.File {
        val dxf = DxfExportEngine.generateTankDxf(result)
        val file = java.io.File(outputPath)
        file.writeText(dxf)
        return file
    }

    // ─── RETAINING WALL (legacy API) ───────────────────────────────────
    fun exportRetainingWallDetailed(
        result: CalculatorEngine.RetainingWallResult,
        outputPath: String
    ): java.io.File {
        val dxf = DxfExportEngine.generateRetainingWallDxf(result)
        val file = java.io.File(outputPath)
        file.writeText(dxf)
        return file
    }
}
