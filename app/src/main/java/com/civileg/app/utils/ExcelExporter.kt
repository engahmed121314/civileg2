package com.civileg.app.utils

import android.content.Context
import com.civileg.app.db.Design
import com.civileg.app.db.MaterialItem
import java.io.File
import java.io.FileOutputStream

/**
 * EXCEL EXPORTER - Civil EG
 * Generates CSV files (compatible with Excel) for Bill of Quantities and Design Results.
 * Follows RFC-4180: fields containing comma, quote, or newline are wrapped in quotes,
 * and internal quotes are doubled.
 */
object ExcelExporter {

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

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
                val line = "${escapeCsv(item.name)},${escapeCsv(item.category.name)},${escapeCsv(item.unit)},${escapeCsv(item.quantity.toString())},${escapeCsv(item.unitPrice.toString())},${escapeCsv(item.totalPrice.toString())}\n"
                writer.write(line)
                grandTotal += item.totalPrice
            }
            
            writer.write(",,,TOTAL PROJECT COST,,$grandTotal\n")
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
writer.write("Design Report: ${escapeCsv(design.name)}\n")
            writer.write("Type: ${escapeCsv(design.type.name)}\n")
            writer.write("Code: ${escapeCsv(design.codeUsed)}\n")
            writer.write("Safety Status: ${if (design.isSafe) "SAFE" else "UNSAFE"}\n\n")
            
            writer.write("Input Data\n")
            writer.write("${escapeCsv(design.inputData.replace(",", ";"))}\n\n") // Simple JSON dump
            
            writer.write("Calculation Results\n")
            writer.write("${escapeCsv(design.results.replace(",", ";"))}\n")
            
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generic CSV export for arbitrary data.
     */
    fun exportTextCsv(context: Context, fileName: String, csvContent: String): File? {
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
        
        try {
            val writer = FileOutputStream(file).bufferedWriter()
            writer.write("\uFEFF") // UTF-8 BOM for Excel
            writer.write(csvContent)
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
