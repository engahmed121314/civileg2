package com.civileg.app.utils

import android.content.Context
import com.civileg.app.db.Design
import com.civileg.app.db.MaterialItem
import java.io.File
import java.io.FileOutputStream
import java.lang.StringBuilder

/**
 * EXCEL EXPORTER - Civil EG
 * Generates CSV files (compatible with Excel) for Bill of Quantities and Design Results.
 */
object ExcelExporter {

    fun exportBOQToCsv(context: Context, projectName: String, materials: List<MaterialItem>): File? {
        val fileName = "${projectName}_BOQ_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
        
        try {
            val writer = FileOutputStream(file).bufferedWriter()
            // CSV Header (UTF-8 BOM for Arabic support in Excel)
            writer.write("\uFEFF") 
            writer.write("Item Name,Category,Unit,Quantity,Unit Price (EGP),Total Price (EGP)\n")
            
            var grandTotal = 0.0
            materials.forEach { item ->
                val line = "${item.name},${item.category},${item.unit},${item.quantity},${item.unitPrice},${item.totalPrice}\n"
                writer.write(line)
                grandTotal += item.totalPrice
            }
            
            writer.write("\n,,,TOTAL PROJECT COST,,$grandTotal\n")
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportDesignToCsv(context: Context, design: Design): File? {
        val fileName = "Design_${design.name}_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        try {
            val writer = FileOutputStream(file).bufferedWriter()
            writer.write("\uFEFF")
            writer.write("Design Report: ${design.name}\n")
            writer.write("Type: ${design.type}\n")
            writer.write("Code: ${design.codeUsed}\n")
            writer.write("Safety Status: ${if (design.isSafe) "SAFE" else "UNSAFE"}\n\n")
            
            writer.write("Input Data\n")
            writer.write("${design.inputData.replace(",", ";")}\n\n") // Simple JSON dump
            
            writer.write("Calculation Results\n")
            writer.write("${design.results.replace(",", ";")}\n")
            
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
