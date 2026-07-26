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
 * PdfExportHelper — Robust PDF export helper using NativePdfExporter.
 *
 * ********************************************************************************
 * CRITICAL FIX (2026-07-27): Switched from iText to Android Native PDF
 * ********************************************************************************
 * The previous iText 8 AGPL-based pipeline produced garbled Arabic text
 * ("encrypted/squares") on every device. Root cause: iText 8 AGPL lacks
 * pdfCalligraph (commercial-only module needed for Arabic GSUB shaping).
 *
 * The new NativePdfExporter uses Android's built-in android.graphics.pdf.PdfDocument
 * API, which leverages Android's native HarfBuzz engine for proper Arabic
 * letter shaping and Bidi algorithm for RTL reordering.
 *
 * This produces correctly-rendered Arabic on EVERY Android device, with NO
 * dependency on commercial modules or fragile manual shaping logic.
 *
 * ********************************************************************************
 * Thread Safety
 * ********************************************************************************
 * All PDF generation runs on Dispatchers.IO to avoid blocking the UI thread.
 * Use [exportCalculationReportAsync] from coroutines / Composables.
 */
object PdfExportHelper {

    private const val TAG = "PdfExportHelper"

    /**
     * Generate a calculation report PDF with mixed Arabic/Latin text.
     *
     * Uses NativePdfExporter for proper Arabic shaping via Android's HarfBuzz.
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

            val exporter = NativePdfExporter(context)
            val generated = exporter.generateCalculationReport(
                title = title,
                details = details,
                outputPath = file.absolutePath
            )

            if (generated != null) {
                openPdf(context, generated)
                generated.absolutePath
            } else {
                Log.e(TAG, "NativePdfExporter returned null")
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
     */
    fun exportDesignReport(
        context: Context,
        title: String,
        subtitle: String = "",
        designType: String = "",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<NativePdfExporter.SafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: android.graphics.Bitmap? = null,
        fileName: String
    ): String? {
        return try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.cacheDir
            directory.mkdirs()
            val file = File(directory, "$fileName.pdf")

            val exporter = NativePdfExporter(context)
            val generated = exporter.generateReport(
                title = title,
                subtitle = subtitle,
                designType = designType,
                inputs = inputs,
                results = results,
                safetyChecks = safetyChecks,
                isSafe = isSafe,
                drawingBitmap = drawingBitmap,
                outputPath = file.absolutePath
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
        safetyChecks: List<NativePdfExporter.SafetyCheck> = emptyList(),
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
