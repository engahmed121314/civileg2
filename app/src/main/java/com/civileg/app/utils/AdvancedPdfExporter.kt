package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.view.View
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image as ITextImage
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.io.image.ImageDataFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Advanced PDF Export System
 * يدير تصدير PDF متقدم مع صور وجداول
 */
object AdvancedPdfExporter {

    // ==================== Report Header ====================
    data class ReportHeader(
        val projectName: String,
        val designType: String,
        val engineer: String = "Civil EG Engineer",
        val company: String = "Civil EG",
        val date: String = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date()),
        val code: String = "ECP 203"
    )

    data class ReportSection(
        val title: String,
        val details: Map<String, String> = emptyMap(),
        val image: Bitmap? = null,
        val table: ReportTable? = null
    )

    data class ReportTable(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val widths: List<Float>? = null
    )

    // ==================== Export Beam Report ====================
    fun exportBeamDesignReport(
        context: Context,
        header: ReportHeader,
        beamWidth: Float,
        beamHeight: Float,
        beamLength: Float,
        steelInfo: String,
        momentCapacity: Double,
        appliedMoment: Double,
        isSafe: Boolean,
        drawingView: View? = null,
        fileName: String = "Beam_Design_Report"
    ): String? {
        return try {
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "$fileName.pdf"
            )

            val writer = PdfWriter(FileOutputStream(file))
            val pdf = PdfDocument(writer)
            val document = Document(pdf)

            // Header
            addReportHeader(document, header)

            // Project Info
            document.add(Paragraph("معلومات المشروع - Project Information").setBold().setFontSize(14f))
            document.add(createInfoTable(mapOf(
                "اسم المشروع" to header.projectName,
                "نوع التصميم" to header.designType,
                "المهندس" to header.engineer,
                "الكود المستخدم" to header.code,
                "التاريخ" to header.date
            )))

            // Design Parameters
            document.add(Paragraph("\nمعاملات التصميم - Design Parameters").setBold().setFontSize(14f))
            document.add(createInfoTable(mapOf(
                "عرض الكمرة" to "${beamWidth.toInt()} mm",
                "ارتفاع الكمرة" to "${beamHeight.toInt()} mm",
                "طول الكمرة" to "${beamLength.toInt()} mm",
                "التسليح" to steelInfo,
                "القدرة (Moment)" to "${String.format(Locale.getDefault(), "%.2f", momentCapacity)} kN.m",
                "العزم المطبق" to "${String.format(Locale.getDefault(), "%.2f", appliedMoment)} kN.m",
                "الحالة" to if (isSafe) "آمن ✓" else "غير آمن ✗"
            )))

            // Drawing
            if (drawingView != null) {
                document.add(Paragraph("\nالرسم التفاعلي - Interactive Drawing").setBold().setFontSize(14f))
                val bitmap = viewToBitmap(drawingView)
                addBitmapToDocument(document, bitmap)
            }

            // Safety Check
            document.add(Paragraph("\nالتحقق من الأمان - Safety Check").setBold().setFontSize(14f))
            val safetyTable = createSafetyCheckTable(
                actualCapacity = momentCapacity,
                requiredCapacity = appliedMoment,
                ratio = momentCapacity / appliedMoment
            )
            document.add(safetyTable)

            // Recommendations
            document.add(Paragraph("\nالتوصيات - Recommendations").setBold().setFontSize(14f))
            val recommendations = if (isSafe) {
                "✓ التصميم آمن ومطابق للكود\n✓ يمكن تنفيذ العمل"
            } else {
                "✗ التصميم غير آمن\n✗ يجب مراجعة المقطع وزيادة التسليح"
            }
            document.add(Paragraph(recommendations))

            // Footer
            addReportFooter(document)

            document.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Export Column Report ====================
    fun exportColumnDesignReport(
        context: Context,
        header: ReportHeader,
        columnWidth: Float,
        columnHeight: Float,
        columnLength: Float,
        isCircular: Boolean,
        steelInfo: String,
        capacity: Double,
        appliedLoad: Double,
        isSafe: Boolean,
        drawingView: View? = null,
        fileName: String = "Column_Design_Report"
    ): String? {
        return try {
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "$fileName.pdf"
            )

            val writer = PdfWriter(FileOutputStream(file))
            val pdf = PdfDocument(writer)
            val document = Document(pdf)

            addReportHeader(document, header)

            document.add(Paragraph("معلومات المشروع - Project Information").setBold().setFontSize(14f))
            document.add(createInfoTable(mapOf(
                "اسم المشروع" to header.projectName,
                "نوع التصميم" to if (isCircular) "عمود دائري" else "عمود مربع",
                "المهندس" to header.engineer,
                "الكود المستخدم" to header.code
            )))

            document.add(Paragraph("\nمعاملات التصميم - Design Parameters").setBold().setFontSize(14f))
            val params = mutableMapOf(
                "الارتفاع" to "${columnLength.toInt()} mm",
                "التسليح" to steelInfo,
                "القدرة" to "${String.format(Locale.getDefault(), "%.2f", capacity)} kN",
                "الحمل المطبق" to "${String.format(Locale.getDefault(), "%.2f", appliedLoad)} kN",
                "الحالة" to if (isSafe) "آمن ✓" else "غير آمن ✗"
            )
            
            if (isCircular) {
                params["القطر"] = "${columnWidth.toInt()} mm"
            } else {
                params["العرض"] = "${columnWidth.toInt()} mm"
                params["الارتفاع"] = "${columnHeight.toInt()} mm"
            }
            
            document.add(createInfoTable(params))

            if (drawingView != null) {
                document.add(Paragraph("\nالرسم التفاعلي - Interactive Drawing").setBold().setFontSize(14f))
                val bitmap = viewToBitmap(drawingView)
                addBitmapToDocument(document, bitmap)
            }

            document.add(Paragraph("\nالتحقق من الأمان - Safety Check").setBold().setFontSize(14f))
            val safetyTable = createSafetyCheckTable(
                actualCapacity = capacity,
                requiredCapacity = appliedLoad,
                ratio = capacity / appliedLoad
            )
            document.add(safetyTable)

            addReportFooter(document)
            document.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Helper Functions ====================
    private fun addReportHeader(document: Document, header: ReportHeader) {
        val title = Paragraph("تطبيق مساعد المهندس المدني")
            .setBold()
            .setFontSize(24f)
            .setTextAlignment(TextAlignment.CENTER)
        document.add(title)

        val subtitle = Paragraph("Civil EG - Advanced Design Calculator")
            .setFontSize(12f)
            .setTextAlignment(TextAlignment.CENTER)
        document.add(subtitle)

        document.add(Paragraph("\n"))

        val headerInfo = mapOf(
            "المشروع" to header.projectName,
            "نوع التصميم" to header.designType,
            "الكود" to header.code,
            "التاريخ" to header.date
        )
        document.add(createInfoTable(headerInfo))
        document.add(Paragraph("\n"))
    }

    private fun createInfoTable(info: Map<String, String>): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(3f, 5f))).useAllAvailableWidth()
        table.setMarginBottom(10f)

        info.forEach { (key, value) ->
            table.addCell(Cell().add(Paragraph(key).setBold()))
            table.addCell(Cell().add(Paragraph(value)))
        }

        return table
    }

    private fun createSafetyCheckTable(
        actualCapacity: Double,
        requiredCapacity: Double,
        ratio: Double
    ): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 2f, 2f))).useAllAvailableWidth()

        table.addCell(Cell().add(Paragraph("القيمة الفعلية\n(Actual)").setBold()))
        table.addCell(Cell().add(Paragraph("القيمة المطلوبة\n(Required)").setBold()))
        table.addCell(Cell().add(Paragraph("نسبة الأمان\n(Ratio)").setBold()))

        table.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", actualCapacity))))
        table.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", requiredCapacity))))
        val ratioText = if (ratio >= 1.0) {
            String.format(Locale.getDefault(), "%.2f ✓", ratio)
        } else {
            String.format(Locale.getDefault(), "%.2f ✗", ratio)
        }
        table.addCell(Cell().add(Paragraph(ratioText)))

        return table
    }

    private fun viewToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun addBitmapToDocument(document: Document, bitmap: Bitmap) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val image = ITextImage(ImageDataFactory.create(stream.toByteArray()))
            image.setMaxWidth(500f)
            image.setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(image)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addReportFooter(document: Document) {
        document.add(Paragraph("\n"))
        val footer = Paragraph("تم إنشاء هذا التقرير باستخدام تطبيق Civil EG\nGenerated by Civil EG Application")
            .setFontSize(10f)
            .setTextAlignment(TextAlignment.CENTER)
            .setItalic()
        document.add(footer)
    }

    fun exportBillOfQuantities(
        context: Context,
        header: ReportHeader,
        itemType: String,
        description: String,
        concreteGrade: String,
        totalConcrete: Double,
        totalSteel: Double,
        totalCost: Double,
        items: List<BoqItem>,
        fileName: String = "Bill_of_Quantities"
    ): String? {
        return try {
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "$fileName.pdf"
            )

            val writer = PdfWriter(FileOutputStream(file))
            val pdf = PdfDocument(writer)
            val document = Document(pdf)

            addReportHeader(document, header)

            document.add(Paragraph("جدول الكميات - Bill of Quantities").setBold().setFontSize(16f))
            document.add(Paragraph("\nنوع العنصر: $itemType\nالوصف: $description\n"))

            val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 3f))).useAllAvailableWidth()
            summaryTable.addCell(Cell().add(Paragraph("الخرسانة (m³)").setBold()))
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", totalConcrete))))
            summaryTable.addCell(Cell().add(Paragraph("الحديد (kg)").setBold()))
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", totalSteel))))
            summaryTable.addCell(Cell().add(Paragraph("الدرجة").setBold()))
            summaryTable.addCell(Cell().add(Paragraph(concreteGrade)))
            summaryTable.addCell(Cell().add(Paragraph("الإجمالي (EGP)").setBold()))
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", totalCost))))
            document.add(summaryTable)

            document.add(Paragraph("\nتفاصيل البنود - Detailed Items").setBold().setFontSize(12f))
            val itemsTable = Table(UnitValue.createPercentArray(floatArrayOf(2f, 1f, 1f, 1f))).useAllAvailableWidth()
            itemsTable.addCell(Cell().add(Paragraph("البند").setBold()))
            itemsTable.addCell(Cell().add(Paragraph("الكمية").setBold()))
            itemsTable.addCell(Cell().add(Paragraph("الوحدة").setBold()))
            itemsTable.addCell(Cell().add(Paragraph("التكلفة").setBold()))

            items.forEach { item ->
                itemsTable.addCell(Cell().add(Paragraph(item.name)))
                itemsTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", item.quantity))))
                itemsTable.addCell(Cell().add(Paragraph(item.unit)))
                itemsTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", item.totalCost))))
            }
            document.add(itemsTable)

            addReportFooter(document)
            document.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    data class BoqItem(val name: String, val quantity: Double, val unit: String, val totalCost: Double)
}
