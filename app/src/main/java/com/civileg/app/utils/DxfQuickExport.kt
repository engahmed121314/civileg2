package com.civileg.app.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.civileg.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-tap DXF export for design screens.
 *
 * Writes a standalone AC1027 DXF to the app documents folder and offers the
 * platform share/save sheet, so the file can be opened in AutoCAD, BricsCAD,
 * LibreCAD, DWG TrueView or any online CAD viewer.
 */
object DxfQuickExport {

    /** Sanitized, user-facing base name (ASCII only — ADR-009). */
    private fun safeName(baseName: String): String =
        baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "Drawing" }

    private fun write(context: Context, drawing: DxfDrawable, baseName: String): File? {
        val directory = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir,
            "DXF"
        ).apply { mkdirs() }
        val file = File(directory, "${safeName(baseName)}_${System.currentTimeMillis()}.dxf")
        return DxfExporter().exportDxf(drawing, file.absolutePath)
    }

    private fun share(context: Context, file: File, baseName: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, safeName(baseName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, safeName(baseName)))
        } catch (_: Exception) {
            // No share target — the file is still written and the toast below reports it.
        }
    }

    /**
     * Export [drawing] off the main thread, then open the share sheet.
     *
     * @param onComplete receives the written file, or null when there was no
     *   drawing / writing failed. Always invoked on the main thread.
     */
    fun export(
        context: Context,
        drawing: DxfDrawable?,
        baseName: String,
        scope: CoroutineScope,
        onComplete: (File?) -> Unit = {}
    ) {
        if (drawing == null) {
            onComplete(null)
            return
        }
        scope.launch {
            val file = withContext(Dispatchers.IO) { write(context, drawing, baseName) }
            withContext(Dispatchers.Main) {
                if (file != null) {
                    share(context, file, baseName)
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_dxf_created, file.name),
                        Toast.LENGTH_LONG
                    ).show()
                }
                onComplete(file)
            }
        }
    }
}
