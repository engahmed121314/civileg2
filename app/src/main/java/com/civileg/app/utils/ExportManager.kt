package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.utils.DxfExporter
import com.civileg.app.utils.NativePdfExporter
import com.civileg.app.utils.NativePdfExporter.SafetyCheck as NativePdfSafetyCheck
import com.civileg.app.utils.ExportUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified Export Manager — Single entry point for all exports (PDF, DXF, IFC, CSV).
 *
 * ADR-004: DXF canonical line = detailing/V7 (DxfWriter AC1027 + DrawingValidator + CalculatorCadExporterV7)
 * ADR-005: Reports English-only unified path (PdfDrawingGenerator bitmaps attached)
 * ADR-009: Arabic FORBIDDEN in PDF/DXF exports; translation at export time only
 *
 * Supported formats:
 * - PDF: NativePdfExporter (Android PdfDocument + HarfBuzz) — EN-only
 * - DXF: DxfWriter AC1027 R2007+ — layers, blocks, BBS
 * - IFC: IfcWriter (IFC4) — BIM export for Revit/Navisworks/Tekla
 * - CSV: ExcelExporter (RFC-4180)
 *
 * Usage:
 * ```kotlin
 * val manager = ExportManager(context)
 * manager.exportPdf(context, result, drawingBitmap, "beam_report.pdf") { file ->
 *     // handle file
 * }
 * ```
 */
class ExportManager(private val context: Context) {

    companion object {
        private const val TAG = "ExportManager"
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API: PDF Export
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Export a structural design result to PDF using NativePdfExporter (Android PdfDocument).
     * EN-only per ADR-009. Drawing bitmap is embedded if provided.
     *
     * @param title Report title (e.g., "Beam Design Report")
     * @param subtitle Optional subtitle
     * @param designType Element type (Beam, Column, Slab, etc.)
     * @param inputs Map of input parameter name -> value (with units)
     * @param results Map of result name -> value (with units)
     * @param safetyChecks List of safety checks (PASS/FAIL with values)
     * @param isSafe Overall safety status
     * @param drawingBitmap Optional bitmap of the engineering drawing
     * @param outputPath Output file path (e.g., "/storage/.../beam_report.pdf")
     * @param onComplete Callback with File? (null if failed)
     */
    fun exportPdf(
        title: String,
        subtitle: String = "",
        designType: String = "",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<NativePdfSafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: Bitmap? = null,
        outputPath: String,
        onComplete: (File?) -> Unit
    ) {
        val exporter = NativePdfExporter(context)
        exporter.generateReport(
            title = title,
            subtitle = subtitle,
            designType = designType,
            inputs = inputs,
            results = results,
            safetyChecks = safetyChecks,
            isSafe = isSafe,
            drawingBitmap = drawingBitmap,
            outputPath = outputPath
        )?.let { file ->
            ExportUtils.openFile(context, file, "application/pdf")
            onComplete(file)
        } ?: onComplete(null)
    }

    /**
     * Export a design-specific PDF using the professional reporter.
     * Used by Beam, Column, Slab, etc. screens.
     */
    fun exportProfessionalPdf(
        reportType: ProfessionalEnglishPdfReporter.ReportType,
        title: String,
        subtitle: String = "",
        designCode: String = "ECP 203-2020",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<ProfessionalEnglishPdfReporter.SafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: Bitmap? = null,
        calculationSteps: List<ProfessionalEnglishPdfReporter.CalculationStep> = emptyList(),
        config: ProfessionalEnglishPdfReporter.ReportConfig = ProfessionalEnglishPdfReporter.ReportConfig(),
        outputPath: String,
        onComplete: (File?) -> Unit
    ) {
        ProfessionalEnglishPdfReporter.generateReport(
            reportType = reportType,
            title = title,
            subtitle = subtitle,
            designCode = designCode,
            inputs = inputs,
            results = results,
            safetyChecks = safetyChecks,
            isSafe = isSafe,
            drawingBitmap = drawingBitmap,
            calculationSteps = calculationSteps,
            config = config,
            outputPath = outputPath
        )?.let { file ->
            ExportUtils.openFile(context, file, "application/pdf")
            onComplete(file)
        } ?: onComplete(null)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API: DXF Export
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Export a structural drawing to DXF using DxfWriter (AC1027/R2007+).
     * The drawing parameter must implement DxfDrawable.
     *
     * @param drawing The drawing data (must implement DxfDrawable)
     * @param outputPath Output file path
     * @param onComplete Callback with File? (null if failed)
     */
    fun exportDxf(
        drawing: DxfDrawable,
        outputPath: String,
        onComplete: (File?) -> Unit
    ) {
        val exporter = DxfExporter(context)
        exporter.exportDxf(drawing, outputPath)?.let { file ->
            ExportUtils.openFile(context, file, "application/dxf")
            onComplete(file)
        } ?: onComplete(null)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API: IFC Export (BIM)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Export a structural model to IFC 4×3 format for BIM workflows.
     * Creates IfcProject, IfcSite, IfcBuilding, IfcBuildingStorey,
     * IfcBeam, IfcColumn, IfcSlab, IfcFooting, IfcReinforcingBar entities.
     *
     * @param projectName Project name
     * @param elements List of structural elements (beams, columns, slabs, etc.)
     * @param outputPath Output file path (e.g., "/storage/.../model.ifc")
     * @param onComplete Callback with File? (null if failed)
     */
    fun exportIfc(
        projectName: String,
        elements: List<IfcElement>,
        outputPath: String,
        onComplete: (File?) -> Unit
    ) {
        val writer = IfcWriter(context)
        writer.writeIfc(
            projectName = projectName,
            elements = elements,
            outputPath = outputPath
        )?.let { file ->
            ExportUtils.openFile(context, file, "application/octet-stream")
            onComplete(file)
        } ?: onComplete(null)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API: CSV Export
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Export data to RFC-4180 compliant CSV.
     */
    fun exportCsv(
        headers: List<String>,
        rows: List<List<String>>,
        outputPath: String,
        onComplete: (File?) -> Unit
    ) {
        ExcelExporter.exportTextCsv(context, "export", buildCsv(headers, rows))
            ?.let { file ->
                ExportUtils.openFile(context, file, "text/csv")
                onComplete(file)
            } ?: onComplete(null)
    }

    private fun buildCsv(headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append(headers.joinToString(",")).append("\n")
        rows.forEach { row ->
            sb.append(row.joinToString(",")).append("\n")
        }
        return sb.toString()
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// INTERFACES & DATA CLASSES
// ───────────────────────────────────────────────────────────────────────────────

/**
 * Interface for objects that can be exported to DXF.
 * Implement this in your drawing data classes.
 */
interface DxfDrawable {
    /**
     * Export this drawing to the given DxfExporter.
     */
    fun exportToDxf(exporter: DxfExporter)
}

/**
 * Interface for objects that can be exported to IFC.
 */
interface IfcElement {
    /**
     * Get the IFC entity type name (e.g., "IfcBeam", "IfcColumn").
     */
    val entityType: String

    /**
     * Get the GlobalId for this element (compressed GUID).
     */
    val globalId: String

    /**
     * Get the name/description of the element.
     */
    val name: String

    /**
     * Get the placement matrix (local to global).
     */
    val placementMatrix: DoubleArray

    /**
     * Get the geometric representation (mesh, extrusion, etc.).
     */
    val geometry: IfcGeometry
}

/**
 * Geometric representation for IFC export.
 */
data class IfcGeometry(
    val extrusionProfile: IfcProfile? = null,
    val extrusionDepth: Double = 0.0,
    val mesh: IfcMesh? = null,
    val representations: List<IfcRepresentation> = emptyList()
)

/**
 * Profile definition for extruded elements (beams, columns).
 */
data class IfcProfile(
    val profileType: IfcProfileType,
    val dimensions: DoubleArray, // profile-specific dimensions
    val materialName: String = "Concrete"
)

enum class IfcProfileType {
    RECTANGLE,      // width, height
    CIRCLE,         // radius
    I_SECTION,      // h, b, tw, tf
    T_SECTION,      // h, b, tf, tw
    L_SECTION,      // h, b, t
    PIPE            // outer_radius, wall_thickness
}

/**
 * Mesh representation for complex geometries.
 */
data class IfcMesh(
    val vertices: DoubleArray, // flat array: x, y, z, x, y, z, ...
    val faces: IntArray,       // face indices (triangles)
    val normals: DoubleArray? = null
)

/**
 * Representation context for IFC.
 */
data class IfcRepresentation(
    val context: String, // "Body", "Axis", "FootPrint"
    val items: List<IfcRepresentationItem>
)

sealed interface IfcRepresentationItem {
    data class Extrusion(
        val profile: IfcProfile,
        val depth: Double,
        val direction: DoubleArray = doubleArrayOf(0.0, 0.0, 1.0)
    ) : IfcRepresentationItem

    data class Mesh(val mesh: IfcMesh) : IfcRepresentationItem
}