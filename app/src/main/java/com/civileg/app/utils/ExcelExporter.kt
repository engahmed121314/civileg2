package com.civileg.app.utils

import android.content.Context
import com.civileg.app.db.Design
import com.civileg.app.db.MaterialItem
import com.civileg.app.domain.entities.BillOfQuantities
import com.civileg.app.domain.entities.BoqCategory
import java.io.File
import java.io.FileOutputStream
import java.lang.StringBuilder

/**
 * EXCEL EXPORTER - Civil EG
 * Generates CSV files (compatible with Excel) for Bill of Quantities and Design Results.
 */
object ExcelExporter {

    /**
     * R5: write a pre-built CSV payload (UTF-8 BOM) and return the file.
     * Caller shares/opens via ExportUtils with "text/csv".
     */
    fun exportTextCsv(context: android.content.Context, baseName: String, csv: String): java.io.File? {
        return try {
            val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, "${baseName}_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).bufferedWriter().use { w ->
                w.write("\uFEFF")
                w.write(csv)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    fun exportBOQToCsv(context: Context, projectName: String, materials: List<MaterialItem>): File? {        val fileName = "${projectName}_BOQ_${System.currentTimeMillis()}.csv"
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

    /**
     * R5 — structured CSV content of a full [BillOfQuantities] (pure function,
     * unit-testable without Android): per-item rows with MATERIAL/LABOR/
     * EQUIPMENT detail, category subtotals, grand total. RFC-4180-safe.
     */
    fun buildBillCsv(bill: BillOfQuantities): String {
        val sb = StringBuilder()
        sb.append("Item ID,Description,Category,Unit,Quantity,Unit Price (${bill.currency}),Total (${bill.currency}),Materials,Labor,Equipment,Code Ref\n")

        fun csv(v: Any?): String {
            val s = v.toString()
            return if (s.contains(',') || s.contains('"') || s.contains('\n'))
                "\"${s.replace("\"", "\"\"")}\"" else s
        }

        bill.items.forEach { item ->
            sb.append(
                listOf(
                    csv(item.itemId), csv(item.description), csv(item.category.name),
                    csv(item.unit),
                    "%.3f".format(java.util.Locale.US, item.quantity),
                    "%.2f".format(java.util.Locale.US, item.unitPrice),
                    "%.2f".format(java.util.Locale.US, item.total),
                    "%.2f".format(java.util.Locale.US, item.materialCost),
                    "%.2f".format(java.util.Locale.US, item.laborCost),
                    "%.2f".format(java.util.Locale.US, item.equipmentCost),
                    csv(item.codeReference ?: "")
                ).joinToString(",") + "\n"
            )
        }

        // Category subtotals (aligned to the 11-column header)
        sb.append("\nCATEGORY SUBTOTALS (${bill.currency})\n")
        BoqCategory.entries.forEach { cat ->
            val subtotal = bill.getTotalByCategory(cat)
            if (subtotal > 0.0) {
                val catItems = bill.items.filter { it.category == cat }
                val mat = catItems.sumOf { it.materialCost }
                val lab = catItems.sumOf { it.laborCost }
                val eqp = catItems.sumOf { it.equipmentCost }
                val row = listOf(
                    csv(cat.name), "", "", "", "", "", "",
                    "%.2f".format(java.util.Locale.US, subtotal),
                    "%.2f".format(java.util.Locale.US, mat),
                    "%.2f".format(java.util.Locale.US, lab),
                    "%.2f".format(java.util.Locale.US, eqp)
                ).joinToString(",")
                sb.append(row).append("\n")
            }
        }

        sb.append("\n")
        sb.append(
            listOf(
                "GRAND TOTAL", "", "", "", "", "", "",
                "%.2f".format(java.util.Locale.US, bill.getGrandTotal()), "", "", ""
            ).joinToString(",")
        ).append("\n")
        return sb.toString()
    }

    /** Writes [buildBillCsv] to external files dir. */
    fun exportBillToCsv(context: Context, bill: BillOfQuantities): File? {
        val fileName = "${bill.projectName.ifEmpty { "Project" }}_BOQ_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
        return try {
            val writer = FileOutputStream(file).bufferedWriter()
            writer.write("\uFEFF")   // BOM so Excel detects UTF-8
            writer.write(buildBillCsv(bill))
            writer.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
