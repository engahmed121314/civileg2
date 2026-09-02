package com.civileg.app.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ExportUtils {
    
    fun sharePdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Civil EG Design Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Share Report"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    fun openPdf(context: Context, file: File) {
        openFile(context, file, "application/pdf")
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share File"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openFile(context: Context, file: File, mimeType: String = "*/*") {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_LONG).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // No viewer for this type (e.g. DXF on a phone without CAD) — fall back
            // to share so the user can send it to AutoCAD/web/mail.
            Toast.makeText(
                context,
                "No ${mimeType.substringAfter('/').uppercase()} viewer installed — sharing instead",
                Toast.LENGTH_LONG
            ).show()
            shareFile(context, file, mimeType)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Centralized DXF export-result handling (governance rule 1.4 — no silent
     * failures). A sheet is opened ONLY when the exporter's self-QA passed;
     * export errors and QA issues are surfaced to the user instead of ignored.
     * Spec §47: a drawing file is not considered successful before validation.
     */
    fun handleDxfOutcome(context: Context, outcome: CadDxfExporter.DxfExportOutcome?) {
        when {
            outcome == null ->
                Toast.makeText(
                    context,
                    "DXF export failed — details in logcat",
                    Toast.LENGTH_LONG
                ).show()
            !outcome.qaPassed -> {
                val issues = outcome.issues.joinToString("; ").take(140)
                Toast.makeText(
                    context,
                    "DXF saved but QA FAILED${if (issues.isNotBlank()) ": $issues" else ""}",
                    Toast.LENGTH_LONG
                ).show()
                // File remains on disk under CivilEG_DXF/ for inspection — do not auto-open.
            }
            else -> CadDxfExporter.firstSheetFile(outcome)?.let { openFile(context, it, "application/dxf") }
        }
    }
}