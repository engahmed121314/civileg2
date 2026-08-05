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
            Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}