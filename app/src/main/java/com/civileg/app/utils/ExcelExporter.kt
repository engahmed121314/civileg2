package com.civileg.app.utils

import android.content.Context
import com.civileg.app.db.Design
import com.civileg.app.db.MaterialItem
import com.civileg.app.domain.entities.BoqItem
import java.io.File
import java.io.FileOutputStream
import java.lang.StringBuilder
import java.util.Locale

/**
 * EXCEL EXPORTER - Civil EG Professional
 * Generates CSV files (compatible with Excel) for Bill of Quantities and Design Results.
 */
object ExcelExporter {

    /**
     * Exports a list of BOQ items to a CSV file.
     */
    fun exportBoqItemsToCsv(context: Context, projectName: String, items: List<BoqItem>): File? {
        val timestamp = System.currentTimeMillis()
        val fileName = "${projectName.replace(" ", "_")}_BOQ_$timestamp.csv"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
        
        try {
            val writer = FileOutputStream(file).bufferedWriter()
            // UTF-8 BOM for Arabic support in Excel
            writer.write("\uFEFF") 
            writer.write("Code,Description,Category,Unit,Quantity,Unit Price,Total Price,Reference\n")
            
            items.forEach { item ->
                val line = "${item.itemId},\"${item.description}\",${item.category},${item.unit},${String.format(Locale.US, "%.3f", item.quantity)},${item.unitPrice},${String.format(Locale.US, "%.2f", item.total)},\"${item.codeReference}\"\n"
                writer.write(line)
            }
            
            val grandTotal = items.sumOf { it.total }
            writer.write("\n,,,,,,TOTAL BUDGET,${String.format(Locale.US, "%.2f", grandTotal)}\n")
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Exports an Estimation result to CSV.
     */
    fun exportEstimationToCsv(context: Context, result: EstimationEngine.EstimationResult): File? {
        val fileName = "Estimate_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
        
        try {
            val writer = FileOutputStream(file).bufferedWriter()
            writer.write("\uFEFF") 
            writer.write("Item,Category,Unit,Quantity,Unit Price,Total Price (${result.currencySymbol})\n")
            
            result.items.forEach { item ->
                writer.write("${item.name},${item.category},${item.unit},${item.quantity},${item.unitPrice},${item.totalPrice}\n")
            }
            
            writer.write("\n,,,,,GRAND TOTAL,${result.totalCost}\n")
            writer.close()
            return file
        } catch (e: Exception) {
            return null
        }
    }

    fun exportDesignToCsv(context: Context, design: Design): File? {
        val fileName = "Design_${design.name.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        try {
            val writer = FileOutputStream(file).bufferedWriter()
            writer.write("\uFEFF")
            writer.write("Structural Design Report: ${design.name}\n")
            writer.write("Element Type: ${design.type}\n")
            writer.write("Code: ${design.codeUsed}\n")
            writer.write("Safety Status: ${if (design.isSafe) "SAFE" else "UNSAFE"}\n")
            writer.write("Concrete Volume: ${design.concreteVolume} m3\n")
            writer.write("Steel Weight: ${design.steelWeight} kg\n\n")
            
            writer.write("Input Data Summary\n")
            writer.write("${design.inputData.replace(",", " | ")}\n\n")
            
            writer.write("Calculation Results\n")
            writer.write("${design.results.replace(",", " | ")}\n")
            
            writer.close()
            return file
        } catch (e: Exception) {
            return null
        }
    }
}
