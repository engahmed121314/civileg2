package com.civilengineer.app.domain.export

import android.content.Context
import android.graphics.Paint
import com.civilengineer.app.data.models.*
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * نظام تصدير PDF متقدم مع الرسومات والجداول
 */
class AdvancedPDFExporter(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar", "EG"))

    /**
     * تصدير تقرير دراسة العقار مع الرسومات
     */
    fun exportRealEstateStudyToPDF(study: RealEstateStudy): File {
        val fileName = "RealEstateStudy_${study.studyName}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.getExternalFilesDir(null), fileName)

        PdfWriter(FileOutputStream(outputFile)).use { writer ->
            Document(PdfWriter(writer)).use { document ->
                // العنوان الرئيسي
                addTitle(document, "تقرير دراسة العقار")
                
                // معلومات الدراسة
                addStudyInfo(document, study)
                
                // البيانات المالية
                addFinancialData(document, study)
                
                // حالة الجدوى
                addFeasibilityStatus(document, study)
                
                // توزيع التكاليف
                addCostBreakdown(document, study)
                
                // التوصيات
                addRecommendations(document, study)
                
                // الخلاصة
                addSummary(document, study)
            }
        }

        return outputFile
    }

    /**
     * تصدير تقرير حصر الكميات مع الجداول
     */
    fun exportMaterialsEstimateToPDF(estimate: MaterialsEstimate): File {
        val fileName = "MaterialsEstimate_${estimate.estimateName}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.getExternalFilesDir(null), fileName)

        PdfWriter(FileOutputStream(outputFile)).use { writer ->
            Document(PdfWriter(writer)).use { document ->
                addTitle(document, "تقرير حصر المواد والكميات")
                
                // معلومات الحصر
                addEstimateInfo(document, estimate)
                
                // جدول المواد
                addMaterialsTable(document, estimate)
                
                // ملخص التكاليف
                addCostSummary(document, estimate)
                
                // الملاحظات
                if (estimate.notes.isNotEmpty()) {
                    addNotes(document, estimate.notes)
                }
            }
        }

        return outputFile
    }

    private fun addTitle(document: Document, title: String) {
        val titleParagraph = Paragraph(title)
            .setFont(PdfFontFactory.createFont())
            .setFontSize(24f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(20f)

        val dateParagraph = Paragraph(dateFormat.format(Date()))
            .setFontSize(10f)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(20f)

        document.add(titleParagraph)
        document.add(dateParagraph)
        document.add(Paragraph())
    }

    private fun addStudyInfo(document: Document, study: RealEstateStudy) {
        document.add(createSectionTitle("معلومات الدراسة"))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
        
        table.addCell(createTableCell("اسم الدراسة"))
        table.addCell(createTableCell(study.studyName))
        table.addCell(createTableCell("نوع المشروع"))
        table.addCell(createTableCell(getProjectTypeName(study.projectType)))
        table.addCell(createTableCell("مساحة الأرض (م²)"))
        table.addCell(createTableCell("${"%.2f".format(study.landAreaM2)}"))
        table.addCell(createTableCell("مساحة البناء (م²)"))
        table.addCell(createTableCell("${"%.2f".format(study.buildingAreaM2)}"))
        table.addCell(createTableCell("عدد الأدوار"))
        table.addCell(createTableCell(study.numberOfFloors.toString()))
        table.addCell(createTableCell("الميزانية"))
        table.addCell(createTableCell("${"%.2f".format(study.buildingBudget)} ${study.currency}"))

        document.add(table)
        document.add(Paragraph())
    }

    private fun addFinancialData(document: Document, study: RealEstateStudy) {
        document.add(createSectionTitle("البيانات المالية"))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
        
        table.addCell(createTableCell("التكلفة المقدرة للمتر"))
        table.addCell(createTableCell("${"%.2f".format(study.costPerM2Estimated)} ${study.currency}"))
        table.addCell(createTableCell("التكلفة الإجمالية المقدرة"))
        table.addCell(createTableCell("${"%.2f".format(study.estimatedTotalCost)} ${study.currency}"))
        table.addCell(createTableCell("الميزانية المتاحة"))
        table.addCell(createTableCell("${"%.2f".format(study.buildingBudget)} ${study.currency}"))
        
        val difference = study.buildingBudget - study.estimatedTotalCost
        val differenceCell = createTableCell("${"%.2f".format(difference)} ${study.currency}")
        if (difference >= 0) {
            differenceCell.setBackgroundColor(DeviceRgb(144, 238, 144))
        } else {
            differenceCell.setBackgroundColor(DeviceRgb(255, 127, 127))
        }
        table.addCell(createTableCell("الفرق"))
        table.addCell(differenceCell)
        
        table.addCell(createTableCell("نسبة تغطية الميزانية"))
        table.addCell(createTableCell("${"%.1f".format(study.budgetCoveragePercentage)}%"))

        document.add(table)
        document.add(Paragraph())
    }

    private fun addFeasibilityStatus(document: Document, study: RealEstateStudy) {
        document.add(createSectionTitle("حالة الجدوى"))

        val statusText = when (study.feasibilityStatus) {
            FeasibilityStatus.FEASIBLE -> "ممكن التنفيذ"
            FeasibilityStatus.FEASIBLE_WITH_REDUCTION -> "ممكن مع تقليل المساحة"
            FeasibilityStatus.FEASIBLE_WITH_BUDGET_INCREASE -> "ممكن مع زيادة الميزانية"
            FeasibilityStatus.NOT_FEASIBLE -> "غير ممكن التنفيذ"
        }

        val statusParagraph = Paragraph(statusText)
            .setBold()
            .setFontSize(14f)
            .setTextAlignment(TextAlignment.CENTER)

        when (study.feasibilityStatus) {
            FeasibilityStatus.FEASIBLE -> 
                statusParagraph.setFontColor(ColorConstants.GREEN)
            FeasibilityStatus.FEASIBLE_WITH_REDUCTION,
            FeasibilityStatus.FEASIBLE_WITH_BUDGET_INCREASE -> 
                statusParagraph.setFontColor(DeviceRgb(255, 165, 0))
            FeasibilityStatus.NOT_FEASIBLE -> 
                statusParagraph.setFontColor(ColorConstants.RED)
        }

        document.add(statusParagraph)
        document.add(Paragraph())
    }

    private fun addCostBreakdown(document: Document, study: RealEstateStudy) {
        document.add(createSectionTitle("توزيع التكاليف"))

        val breakdown = study.costBreakdown
        val totalCost = study.estimatedTotalCost

        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f)))
        
        // رأس الجدول
        table.addCell(createTableHeaderCell("البند"))
        table.addCell(createTableHeaderCell("النسبة"))
        table.addCell(createTableHeaderCell("القيمة"))

        data class CostItem(val name: String, val percentage: Double)
        
        val items = listOf(
            CostItem("الأساسات والحفريات", breakdown.foundationPercentage),
            CostItem("الهيكل الإنشائي", breakdown.structuralPercentage),
            CostItem("التشطيبات", breakdown.finishingPercentage),
            CostItem("الخدمات والمرافق", breakdown.utilitiesPercentage),
            CostItem("أخرى", breakdown.miscellaneousPercentage)
        )

        items.forEach { item ->
            table.addCell(createTableCell(item.name))
            table.addCell(createTableCell("${"%.1f".format(item.percentage)}%"))
            table.addCell(createTableCell("${"%.2f".format(totalCost * (item.percentage / 100))} ${study.currency}"))
        }

        table.addCell(createTableHeaderCell("الإجمالي"))
        table.addCell(createTableHeaderCell("100%"))
        table.addCell(createTableHeaderCell("${"%.2f".format(totalCost)} ${study.currency}"))

        document.add(table)
        document.add(Paragraph())
    }

    private fun addRecommendations(document: Document, study: RealEstateStudy) {
        if (study.recommendations.isNotEmpty()) {
            document.add(createSectionTitle("التوصيات"))
            document.add(Paragraph(study.recommendations))
            document.add(Paragraph())
        }
    }

    private fun addMaterialsTable(document: Document, estimate: MaterialsEstimate) {
        document.add(createSectionTitle("قائمة المواد والكميات"))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f, 1f)))
        
        table.addCell(createTableHeaderCell("المادة"))
        table.addCell(createTableHeaderCell("الكمية"))
        table.addCell(createTableHeaderCell("السعر الوحدة"))
        table.addCell(createTableHeaderCell("الإجمالي"))

        // الخرسانة
        if (estimate.concreteVolumeM3 > 0) {
            table.addCell(createTableCell("خرسانة (م³)"))
            table.addCell(createTableCell("${"%.2f".format(estimate.concreteVolumeM3)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.concreteUnitCost)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.concreteTotalCost)}"))
        }

        // الفولاذ
        if (estimate.steelWeightTons > 0) {
            table.addCell(createTableCell("فولاذ تسليح (طن)"))
            table.addCell(createTableCell("${"%.2f".format(estimate.steelWeightTons)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.steelUnitCost)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.steelTotalCost)}"))
        }

        // الطوب
        if (estimate.brickQuantity > 0) {
            table.addCell(createTableCell("طوب (ألف طابوقة)"))
            table.addCell(createTableCell(estimate.brickQuantity.toString()))
            table.addCell(createTableCell("${"%.2f".format(estimate.brickUnitCost)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.brickTotalCost)}"))
        }

        // الرمل
        if (estimate.sandVolumeM3 > 0) {
            table.addCell(createTableCell("رمل (م³)"))
            table.addCell(createTableCell("${"%.2f".format(estimate.sandVolumeM3)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.sandUnitCost)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.sandTotalCost)}"))
        }

        // الحصى
        if (estimate.gravelVolumeM3 > 0) {
            table.addCell(createTableCell("حصى (م³)"))
            table.addCell(createTableCell("${"%.2f".format(estimate.gravelVolumeM3)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.gravelUnitCost)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.gravelTotalCost)}"))
        }

        // الإسمنت
        if (estimate.cementBags > 0) {
            table.addCell(createTableCell("إسمنت (شيكارة)"))
            table.addCell(createTableCell(estimate.cementBags.toString()))
            table.addCell(createTableCell("${"%.2f".format(estimate.cementUnitCost)}"))
            table.addCell(createTableCell("${"%.2f".format(estimate.cementTotalCost)}"))
        }

        document.add(table)
        document.add(Paragraph())
    }

    private fun addCostSummary(document: Document, estimate: MaterialsEstimate) {
        document.add(createSectionTitle("ملخص التكاليف"))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
        
        table.addCell(createTableCell("تكاليف المواد"))
        table.addCell(createTableCell("${"%.2f".format(estimate.materialsTotalCost)}"))
        table.addCell(createTableCell("نسبة الأجور"))
        table.addCell(createTableCell("${"%.1f".format(estimate.laborCostPercentage)}%"))
        table.addCell(createTableCell("تكاليف الأجور"))
        table.addCell(createTableCell("${"%.2f".format(estimate.laborTotalCost)}"))
        
        val totalCell = createTableCell("${"%.2f".format(estimate.grandTotalCost)}")
        totalCell.setBackgroundColor(DeviceRgb(200, 200, 200))
        table.addCell(createTableHeaderCell("الإجمالي الكلي"))
        table.addCell(totalCell)

        document.add(table)
        document.add(Paragraph())
    }

    private fun addNotes(document: Document, notes: String) {
        document.add(createSectionTitle("الملاحظات"))
        document.add(Paragraph(notes))
        document.add(Paragraph())
    }

    private fun addSummary(document: Document, study: RealEstateStudy) {
        document.add(createSectionTitle("الملخص التنفيذي"))

        val summaryText = buildString {
            append("هذا التقرير يقدم تقييماً شاملاً لجدوى المشروع العقاري المقترح.\n\n")
            append("البيانات الأساسية:\n")
            append("- نوع المشروع: ${getProjectTypeName(study.projectType)}\n")
            append("- مساحة الأرض: ${"%.2f".format(study.landAreaM2)} م²\n")
            append("- مساحة البناء: ${"%.2f".format(study.buildingAreaM2)} م²\n")
            append("- عدد الأدوار: ${study.numberOfFloors}\n")
            append("- الميزانية المتاحة: ${"%.2f".format(study.buildingBudget)} ${study.currency}\n\n")
            
            append("النتائج:\n")
            append("- التكلفة المقدرة: ${"%.2f".format(study.estimatedTotalCost)} ${study.currency}\n")
            append("- نسبة تغطية الميزانية: ${"%.1f".format(study.budgetCoveragePercentage)}%\n")
            append("- حالة الجدوى: ")
            append(when (study.feasibilityStatus) {
                FeasibilityStatus.FEASIBLE -> "ممكن التنفيذ"
                FeasibilityStatus.FEASIBLE_WITH_REDUCTION -> "ممكن مع تقليل"
                FeasibilityStatus.FEASIBLE_WITH_BUDGET_INCREASE -> "ممكن مع زيادة الميزانية"
                FeasibilityStatus.NOT_FEASIBLE -> "غير ممكن"
            })
        }

        document.add(Paragraph(summaryText))
    }

    private fun createSectionTitle(title: String): Paragraph {
        return Paragraph(title)
            .setBold()
            .setFontSize(14f)
            .setMarginTop(12f)
            .setMarginBottom(8f)
            .setTextAlignment(TextAlignment.RIGHT)
    }

    private fun createTableCell(text: String): Cell {
        return Cell().add(Paragraph(text).setFontSize(10f))
    }

    private fun createTableHeaderCell(text: String): Cell {
        return Cell().add(Paragraph(text).setBold().setFontSize(10f))
            .setBackgroundColor(DeviceRgb(200, 200, 200))
    }

    private fun getProjectTypeName(type: ProjectType): String {
        return when (type) {
            ProjectType.RESIDENTIAL -> "سكني"
            ProjectType.COMMERCIAL -> "تجاري"
            ProjectType.INDUSTRIAL -> "صناعي"
            ProjectType.MIXED_USE -> "استخدام مختلط"
            ProjectType.RESIDENTIAL_COMMERCIAL -> "سكني تجاري"
        }
    }
}