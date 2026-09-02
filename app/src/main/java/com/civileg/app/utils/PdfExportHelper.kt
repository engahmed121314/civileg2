package com.civileg.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PdfExportHelper — Robust PDF export helper using ComprehensivePdfExporter (iText 8).
 *
 * ********************************************************************************
 * CRITICAL FIX (2026-07-27 v4): Switched back from NativePdfExporter to iText 8
 * ********************************************************************************
 * NativePdfExporter (Android-native android.graphics.pdf.PdfDocument + Canvas)
 * was triggering NATIVE crashes (SIGSEGV in Skia) that could NOT be caught by
 * Java try/catch — killing the app process on every PDF export.
 *
 * ComprehensivePdfExporter uses iText 8 (PdfWriter/PdfDocument/Document) — the
 * same safe path used by FrameAnalysisPdfExporter which never crashes.
 *
 * Arabic shaping is handled by PdfTextSegmenter inside ComprehensivePdfExporter:
 * Arabic base letters (0600-06FF) are converted to Presentation Forms (FE70-FEFF)
 * before being passed to iText, producing correctly connected Arabic letters
 * WITHOUT needing the commercial pdfCalligraph module.
 *
 * ********************************************************************************
 * Thread Safety
 * ********************************************************************************
 * All PDF generation runs on Dispatchers.IO to avoid blocking the UI thread.
 * Use [exportCalculationReportAsync] / [exportDesignReportAsync] from coroutines.
 */
object PdfExportHelper {

    private const val TAG = "PdfExportHelper"

    /**
     * Generate a calculation report PDF (title + details only, no drawings).
     *
     * @return Absolute path to generated PDF, or null on failure
     */
    fun exportCalculationReport(
        context: Context,
        title: String,
        details: Map<String, String>,
        fileName: String
    ): String? {
        return try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.cacheDir
            directory.mkdirs()
            val file = File(directory, "$fileName.pdf")

            val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                titleAr = title,
                titleEn = title,
                subtitle = "",
                designType = "",
                inputs = details,
                results = emptyMap(),
                safetyChecks = emptyList(),
                isSafe = true,
                drawingBitmap = null,
                outputPath = file.absolutePath,
                context = context
            )

            if (generated != null) {
                openPdf(context, generated)
                generated.absolutePath
            } else {
                Log.e(TAG, "ProfessionalEnglishPdfReporter returned null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate PDF: ${e.message}", e)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    /**
     * SUSPEND version of [exportCalculationReport] — runs PDF generation on IO dispatcher.
     */
    suspend fun exportCalculationReportAsync(
        context: Context,
        title: String,
        details: Map<String, String>,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        exportCalculationReport(context, title, details, fileName)
    }

    /**
     * Generate a complete structural design report PDF with drawing and safety checks.
     *
     * Uses ComprehensivePdfExporter (iText 8) for safe, crash-free PDF generation
     * with proper Arabic shaping via PdfTextSegmenter.
     */
    fun exportDesignReport(
        context: Context,
        title: String,
        subtitle: String = "",
        designType: String = "",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: android.graphics.Bitmap? = null,
        fileName: String
    ): String? {
        return try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.cacheDir
            directory.mkdirs()
            val file = File(directory, "$fileName.pdf")

            val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                titleAr = title,
                titleEn = title,
                subtitle = subtitle,
                designType = designType,
                inputs = inputs,
                results = results,
                safetyChecks = safetyChecks,
                isSafe = isSafe,
                drawingBitmap = drawingBitmap,
                outputPath = file.absolutePath,
                context = context
            )

            if (generated != null) {
                openPdf(context, generated)
                generated.absolutePath
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Design report generation failed: ${e.message}", e)
            null
        }
    }

    /**
     * SUSPEND version of [exportDesignReport].
     */
    suspend fun exportDesignReportAsync(
        context: Context,
        title: String,
        subtitle: String = "",
        designType: String = "",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: android.graphics.Bitmap? = null,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        exportDesignReport(
            context, title, subtitle, designType, inputs, results,
            safetyChecks, isSafe, drawingBitmap, fileName
        )
    }

    private fun openPdf(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "No PDF viewer found: ${e.message}")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "PDF saved. Open from Files app.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
