package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.civileg.app.domain.entities.ReinforcementResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PdfExporter(private val context: Context) {
    
    fun exportColumnReport(
        projectName: String,
        designCode: String,
        inputs: Map<String, Double>,
        result: ReinforcementResult,
        notes: String = ""
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Portrait
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        
        // Font Settings
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        var y = 50f
        
        // Title
        canvas.drawText("Civil EG - Column Design Report", 50f, y, titlePaint)
        y += 30f
        canvas.drawText("Project: $projectName", 50f, y, paint)
        y += 20f
        canvas.drawText("Design Code: $designCode", 50f, y, paint)
        y += 20f
        canvas.drawText("Date: ${getCurrentDate()}", 50f, y, paint)
        y += 40f
        
        // Input Data
        canvas.drawText("Input Data", 50f, y, headerPaint)
        y += 25f
        inputs.forEach { (key, value) ->
            canvas.drawText("$key: $value", 70f, y, paint)
            y += 20f
        }
        y += 20f
        
        // Design Results
        canvas.drawText("Design Results", 50f, y, headerPaint)
        y += 25f
        canvas.drawText("Required Steel Area: %.2f mm²".format(result.astRequired), 70f, y, paint)
        y += 20f
        canvas.drawText("Provided Steel Area: %.2f mm²".format(result.astProvided), 70f, y, paint)
        y += 20f
        canvas.drawText("Bar Diameter: Ø%.1f mm".format(result.barDiameter), 70f, y, paint)
        y += 20f
        canvas.drawText("Number of Bars: %d".format(result.numberOfBars), 70f, y, paint)
        y += 20f
        canvas.drawText("Ties: Ø%.1f @ %.0f mm".format(result.tiesDiameter, result.tiesSpacing), 70f, y, paint)
        y += 20f
        canvas.drawText("Safety Status: ${result.safetyStatus}", 70f, y, paint)
        y += 20f
        canvas.drawText("Utilization Ratio: %.1f%%".format(result.utilizationRatio * 100), 70f, y, paint)
        y += 30f
        
        // Code Notes
        if (result.codeNotes.isNotEmpty()) {
            canvas.drawText("Code Notes", 50f, y, headerPaint)
            y += 25f
            result.codeNotes.forEach { note ->
                canvas.drawText("• $note", 70f, y, paint)
                y += 18f
            }
            y += 10f
        }
        
        // Warnings
        if (result.warnings.isNotEmpty()) {
            canvas.drawText("⚠️ Warnings", 50f, y, headerPaint.apply { color = android.graphics.Color.parseColor("#FF9800") })
            y += 25f
            result.warnings.forEach { warning ->
                canvas.drawText("• $warning", 70f, y, paint.apply { color = android.graphics.Color.parseColor("#FF9800") })
                y += 18f
            }
        }
        
        // Disclaimer
        y = 750f
        canvas.drawText("Disclaimer:", 50f, y, paint.apply { isFakeBoldText = true })
        y += 18f
        canvas.drawText("This report is for educational and preliminary design purposes only.", 50f, y, paint)
        y += 18f
        canvas.drawText("Final design must be reviewed and approved by a licensed engineer.", 50f, y, paint)
        
        document.finishPage(page)
        
        // Save File
        val fileName = "${projectName}_Column_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)
        document.writeTo(FileOutputStream(file))
        document.close()
        
        return file
    }
    
    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
}
