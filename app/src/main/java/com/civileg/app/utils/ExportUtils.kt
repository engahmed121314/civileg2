package com.civileg.app.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ExportUtils {

    // ── DXF helpers ──────────────────────────────────────────────

    /**
     * Opens a DXF file using an external viewer.
     * Falls back to sharing if no viewer is installed.
     * Uses a generic MIME type as a last resort so the file
     * can always be sent to another app (e.g. email, cloud).
     */
    fun openDxf(context: Context, file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "DXF file not found", Toast.LENGTH_LONG).show()
                return
            }
            // Also copy to public Downloads so the user always has access
            copyToDownloads(context, file, file.name)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            // Try standard DXF MIME first
            var handled = false
            for (mimeType in listOf("application/dxf", "application/acad", "application/octet-stream")) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        handled = true
                        break
                    }
                } catch (_: Exception) { continue }
            }
            if (!handled) {
                // No viewer at all – share the file
                shareDxf(context, file)
                Toast.makeText(context, "No DXF viewer found. File saved to Downloads.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening DXF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareDxf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                // Use octet-stream as fallback MIME so any app can receive it
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Civil EG DXF Drawing")
                putExtra(Intent.EXTRA_TEXT, "Engineering DXF drawing exported from CivilEG")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share DXF Drawing"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share DXF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Copy file to the public Downloads directory so it's accessible
     * via file managers and can be transferred to PC.
     */
    private fun copyToDownloads(context: Context, source: File, fileName: String) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, "CivilEG_$fileName")
            source.copyTo(destFile, overwrite = true)
        } catch (_: Exception) { /* Silently fail – the FileProvider path still works */ }
    }

    // ── PDF helpers (existing) ───────────────────────────────────

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
        try {
            if (!file.exists()) {
                Toast.makeText(context, "PDF file not found", Toast.LENGTH_LONG).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer installed. Please install one.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}