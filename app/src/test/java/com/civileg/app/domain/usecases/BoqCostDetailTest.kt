package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.BillOfQuantities
import com.civileg.app.domain.entities.BoqCategory
import com.civileg.app.domain.entities.BoqCostSplit
import com.civileg.app.domain.entities.BoqItem
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.app.utils.ExcelExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5 BOQ cost-detail contracts:
 *  - BoqCostSplit: every category's MATERIAL+LABOR+EQUIPMENT sums to 1.0
 *  - BoqItem decomposition is consistent and totals reconcile
 *  - buildBillCsv: RFC-4180 escaping, category subtotals, grand total row
 */
class BoqCostDetailTest {

    // ── Cost split invariants ────────────────────────────────────────

    @Test fun costSplit_everyCategory_sumsToOne() {
        BoqCategory.entries.forEach { cat ->
            val sum = BoqCostSplit.material(cat) + BoqCostSplit.labor(cat) + BoqCostSplit.equipment(cat)
            assertEquals("split for $cat must total 100%", 1.0, sum, 1e-9)
        }
    }

    @Test fun boqItem_decomposition_reconciles() {
        val item = BoqItem(
            itemId = "COL_CONC_001", description = "Column concrete",
            category = BoqCategory.CONCRETE, unit = "m3",
            quantity = 10.0, unitPrice = 1500.0
        )
        assertEquals(15000.0, item.total, 1e-6)
        val decomposed = item.materialCost + item.laborCost + item.equipmentCost
        assertEquals("decomposition must reconstruct the total", item.total, decomposed, 1e-6)
        // Concrete split: material-dominant
        assertTrue(item.materialCost > item.laborCost)
        assertTrue(item.laborCost > item.equipmentCost)
    }

    @Test fun excavation_isEquipmentDominated() {
        val exc = BoqItem("FTG_EXCAV_001", "Excavation", BoqCategory.EXCAVATION, "m3", 50.0, 120.0)
        assertTrue(
            "excavation equipment share must exceed material share",
            exc.equipmentCost > exc.materialCost
        )
    }

    // ── CSV generation ───────────────────────────────────────────────

    private fun sampleBill() = BillOfQuantities(
        projectName = "Test Tower",
        designCode = DesignCode.ECP,
        currency = "EGP",
        items = listOf(
            BoqItem("C1", "Concrete C30", BoqCategory.CONCRETE, "m3", 12.0, 1600.0),
            BoqItem("S1", "Rebar Ø16", BoqCategory.REINFORCEMENT, "ton", 2.4, 48000.0),
            BoqItem("F1", "Column formwork", BoqCategory.FORMWORK, "m2", 45.0, 280.0)
        )
    )

    @Test fun csv_headerAndRows_columnCountConsistent() {
        val csv = ExcelExporter.buildBillCsv(sampleBill())
        val lines = csv.lines().filter { it.isNotBlank() }
        // Header has 11 columns
        assertEquals(11, lines.first().split(',').size)
        // Every item row also has 11 columns (no commas inside these test descriptions)
        val dataRows = lines.filter { it.startsWith("C1,") || it.startsWith("S1,") || it.startsWith("F1,") }
        assertEquals(3, dataRows.size)
        dataRows.forEach { assertEquals(11, it.split(',').size) }
    }

    @Test fun csv_escapesCommasInDescription() {
        val bill = sampleBill().copy(
            items = listOf(BoqItem("X1", "Footing, 2.5 x 2.5 \"with\" haunch", BoqCategory.CONCRETE, "m3", 1.0, 100.0))
        )
        val line = ExcelExporter.buildBillCsv(bill).lines().first { it.startsWith("X1,") }
        // Quoted field protects inner commas/quotes; row still parses as 11 fields
        assertTrue(line.contains("\"Footing, 2.5 x 2.5 \"\"with\"\" haunch\""))
    }

    @Test fun csv_subtotals_andGrandTotal_reconcile() {
        val bill = sampleBill()
        val csv = ExcelExporter.buildBillCsv(bill)
        val grandRow = csv.lines().first { it.startsWith("GRAND TOTAL") }
        val grandValue = grandRow.split(',')[7].toDouble()
        assertEquals(bill.getGrandTotal(), grandValue, 0.01)

        // Each non-empty subtotal row value matches its category total
        val concRow = csv.lines().first { it.startsWith("CONCRETE,") }
        assertEquals(bill.getTotalByCategory(BoqCategory.CONCRETE), concRow.split(',')[7].toDouble(), 0.01)
    }

    @Test fun csv_itemTotals_matchGrandTotal() {
        val bill = sampleBill()
        val csv = ExcelExporter.buildBillCsv(bill)
        val itemSum = csv.lines()
            .filter { it.startsWith("C1,") || it.startsWith("S1,") || it.startsWith("F1,") }
            .sumOf { it.split(',')[6].toDouble() }
        assertEquals(bill.getGrandTotal(), itemSum, 0.01)
    }
}
