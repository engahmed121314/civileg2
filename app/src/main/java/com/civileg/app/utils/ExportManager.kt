package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.civileg.app.R
import com.civileg.app.domain.entities.CalculationStep as DomainCalculationStep
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.utils.DxfExporter
import com.civileg.app.utils.ExportUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unified Export Manager — Single entry point for all exports (PDF, DXF, IFC, CSV).
 *
 * ADR-004: DXF canonical line = detailing/V7 (DxfWriter AC1027 + DrawingValidator + CalculatorCadExporterV7)
 * ADR-005: Reports English-only unified path (PdfDrawingGenerator bitmaps attached)
 * ADR-009: Arabic FORBIDDEN in PDF/DXF exports; translation at export time only
 *
 * Supported formats:
 * - PDF: ProfessionalEnglishPdfReporter (iText) — EN-only
 * - DXF: DxfWriter AC1027 R2007+ — layers, blocks, BBS
 * - IFC: IfcWriter (IFC4) — BIM export for Revit/Navisworks/Tekla
 * - CSV: ExcelExporter (RFC-4180)
 *
 * Every PDF path goes through iText. The Android-native
 * android.graphics.pdf.PdfDocument + Canvas pipeline was removed because it can
 * fail with an uncatchable Skia SIGSEGV; nothing in the app may reintroduce it.
 *
 * Usage:
 * ```kotlin
 * val manager = ExportManager(context)
 * manager.exportPdf(title = "Beam Design Report", outputPath = file.path) { file ->
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
     * Export a structural design result to PDF using the professional reporter (iText).
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
        safetyChecks: List<ProfessionalEnglishPdfReporter.SafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: Bitmap? = null,
        outputPath: String,
        onComplete: (File?) -> Unit
    ) {
        ProfessionalEnglishPdfReporter.generateReport(
            reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
            title = title,
            subtitle = subtitle,
            designCode = "",
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
        val exporter = DxfExporter()
        exporter.exportDxf(drawing, outputPath)?.let { file ->
            ExportUtils.openFile(context, file, "application/dxf")
            onComplete(file)
        } ?: onComplete(null)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // PUBLIC API: Combined PDF + DXF export & packaging
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Export a design result to BOTH PDF and DXF in one call.
     *
     * PDF is generated with ProfessionalEnglishPdfReporter (iText, EN-only, ADR-005/009).
     * DXF is generated with DxfExporter (AC1027/R2007+, ADR-004) when a
     * [DxfDrawable] is supplied; otherwise only the PDF is produced.
     *
     * Files are written into [outputDir] as `<baseName>.pdf` and `<baseName>.dxf`.
     *
     * @return [ExportBundle] holding whichever artifacts were produced (never throws).
     */
    fun exportBoth(
        title: String,
        subtitle: String = "",
        designType: String = "",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<ProfessionalEnglishPdfReporter.SafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: Bitmap? = null,
        drawing: DxfDrawable? = null,
        outputDir: File,
        baseName: String,
        openPdfOnComplete: Boolean = false
    ): ExportBundle {
        outputDir.mkdirs()

        val pdfFile = ProfessionalEnglishPdfReporter.generateReport(
            reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
            title = title,
            subtitle = subtitle,
            designCode = "",
            inputs = inputs,
            results = results,
            safetyChecks = safetyChecks,
            isSafe = isSafe,
            drawingBitmap = drawingBitmap,
            outputPath = File(outputDir, "$baseName.pdf").absolutePath
        )

        val dxfFile = drawing?.let {
            DxfExporter().exportDxf(it, File(outputDir, "$baseName.dxf").absolutePath)
        }

        if (openPdfOnComplete && pdfFile != null) {
            ExportUtils.openFile(context, pdfFile, "application/pdf")
        }
        return ExportBundle(pdfFile = pdfFile, dxfFile = dxfFile)
    }

    /**
     * Export a design report to BOTH PDF and DXF in one call, using the
     * professional English reporter for the PDF (the path all element screens use).
     *
     * This is the single migration target for ViewModels that previously called
     * `ProfessionalEnglishPdfReporter.generateReportLegacy(...)` and then wrote a
     * DXF companion by hand (P2-11).
     *
     * The PDF is EN-only (ADR-005/009); the DXF is AC1027/EN-only (ADR-004/009).
     * A DXF failure never fails the PDF — [ExportBundle.dxfFile] is simply null.
     * When a DXF is produced the user is notified with a toast, so the companion
     * drawing is never written silently (P2-11 surfacing).
     *
     * @param drawing optional [DxfDrawable] companion; null ⇒ PDF only.
     * @param openPdfOnComplete opens the PDF when it was produced successfully.
     * @return [ExportBundle]; never throws.
     */
    fun exportReportAndDxf(
        titleAr: String,
        titleEn: String,
        subtitle: String = "",
        designType: String = "",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<GenericSafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: Bitmap? = null,
        drawing: DxfDrawable? = null,
        calculationSteps: List<DomainCalculationStep> = emptyList(),
        bbsEntries: List<BbsEntry> = emptyList(),
        boqRows: List<ProfessionalEnglishPdfReporter.BoqRow> = emptyList(),
        outputPath: String,
        openPdfOnComplete: Boolean = false
    ): ExportBundle {
        val pdfFile = try {
            ProfessionalEnglishPdfReporter.generateReportLegacy(
                titleAr = titleAr,
                titleEn = titleEn,
                subtitle = subtitle,
                designType = designType,
                inputs = inputs,
                results = results,
                safetyChecks = safetyChecks,
                isSafe = isSafe,
                drawingBitmap = drawingBitmap,
                outputPath = outputPath,
                boqRows = boqRows,
                bbsRows = bbsEntries.map { entry ->
                    ProfessionalEnglishPdfReporter.BbsRow(
                        memberMark = entry.memberMark,
                        barMark = entry.barMark,
                        diameter = entry.diameter,
                        shapeCode = entry.shapeCode,
                        count = entry.count,
                        totalLengthMm = entry.totalLengthPerBar,
                        weightKg = entry.totalWeightKg
                    )
                },
                calculationSteps = calculationSteps.map { step ->
                    ProfessionalEnglishPdfReporter.CalculationStep(
                        stepNumber = step.stepNumber,
                        description = step.title,
                        equation = step.formulaWithValues.ifBlank { step.formula },
                        reference = step.codeReference.ifBlank { null },
                        result = listOf(step.result, step.unit)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "PDF export failed: ${e.message}", e)
            null
        }

        val dxfFile = if (pdfFile != null && drawing != null) {
            DxfExporter.writeSibling(pdfFile, drawing, bbsEntries)
        } else null

        if (openPdfOnComplete && pdfFile != null) {
            ExportUtils.openPdf(context, pdfFile)
        }
        if (dxfFile != null) notifyDxfSaved(dxfFile)

        return ExportBundle(pdfFile = pdfFile, dxfFile = dxfFile)
    }

    private fun notifyDxfSaved(dxfFile: File) {
        val message = try {
            context.getString(R.string.export_dxf_created, dxfFile.name)
        } catch (e: Exception) {
            "DXF drawing saved: ${dxfFile.name}"
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Package the artifacts of an [ExportBundle] into a single ZIP archive.
     *
     * @return the ZIP [File] if at least one artifact existed, else null.
     */
    fun exportPackage(bundle: ExportBundle, outputZip: File): File? =
        exportPackage(listOfNotNull(bundle.pdfFile, bundle.dxfFile), outputZip)

    /**
     * Package an arbitrary list of files into a single ZIP archive.
     * Non-existent files are skipped.
     *
     * @return the ZIP [File] if at least one file existed, else null.
     */
    fun exportPackage(files: List<File>, outputZip: File): File? {
        val present = files.filter { it.isFile }
        if (present.isEmpty()) return null
        outputZip.parentFile?.mkdirs()
        return try {
            ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                present.forEach { f ->
                    zos.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            Log.i(TAG, "Export package created: ${outputZip.absolutePath} (${outputZip.length()} bytes)")
            outputZip
        } catch (e: Exception) {
            Log.e(TAG, "Export packaging failed: ${e.message}", e)
            null
        }
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
 * Result of a combined export — holds whichever artifacts were produced.
 * Any field may be null if that artifact failed or was not requested.
 */
data class ExportBundle(
    val pdfFile: File? = null,
    val dxfFile: File? = null,
    val zipFile: File? = null
) {
    val isEmpty: Boolean get() = pdfFile == null && dxfFile == null && zipFile == null
}

/**
 * Interface for objects that can be exported to DXF.
 * Implement this in your drawing data classes.
 */
fun interface DxfDrawable {
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