package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.view.View
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image as ITextImage
import com.itextpdf.layout.properties.BaseDirection
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
 * يدير تصدير PDF متقدم مع صور وجداول مع دعم كامل للغة العربية
 */
object AdvancedPdfExporter {

    private val PRIMARY_COLOR = DeviceRgb(21, 101, 192)

    // ==================== Arabic Support ====================
    private fun getArabicFont(context: Context): PdfFont? {
        return ArabicFontProvider.getArabicPdfFont(context)
    }

    private fun containsArabic(text: String): Boolean {
        return ArabicFontProvider.containsArabic(text)
    }

    private fun createStyledParagraph(text: String, font: PdfFont?, fontSize: Float = 12f, isBold: Boolean = false): Paragraph {
        val p = Paragraph(text).setFontSize(fontSize)
        if (isBold) p.setBold()
        if (font != null && containsArabic(text)) {
            p.setFont(font)
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        return p
    }

    private fun addArabicCell(table: Table, text: String, font: PdfFont?, isBold: Boolean = false, fontSize: Float = 10f) {
        val p = createStyledParagraph(text, font, fontSize, isBold)
        table.addCell(Cell().add(p))
    }

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
        beamWidth: Float,
        beamHeight: Float,
        mu: Double,
        asReq: Double,
        beamLength: Float = 5.0f,
        isSafe: Boolean = true,
        header: ReportHeader = ReportHeader("مشروع جديد", "تصميم كمرة"),
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
            val arabicFont = getArabicFont(context)

            // Header
            addReportHeader(document, header, arabicFont)

            // Project Info
            document.add(createStyledParagraph("معلومات المشروع - Project Information", arabicFont, 14f, true))
            document.add(createInfoTable(mapOf(
                "اسم المشروع" to header.projectName,
                "نوع التصميم" to header.designType,
                "المهندس" to header.engineer,
                "الكود المستخدم" to header.code,
                "التاريخ" to header.date
            ), arabicFont))

            // Design Parameters
            document.add(createStyledParagraph("\nمعاملات التصميم - Design Parameters", arabicFont, 14f, true))
            document.add(createInfoTable(mapOf(
                "عرض الكمرة" to "${beamWidth.toInt()} mm",
                "ارتفاع الكمرة" to "${beamHeight.toInt()} mm",
                "طول الكمرة" to "${beamLength.toInt()} m",
                "مساحة الحديد المطلوبة (As)" to "${asReq.toInt()} mm²",
                "العزم التصميمي (Mu)" to "${String.format(Locale.getDefault(), "%.2f", mu)} kN.m",
                "الحالة" to if (isSafe) "آمن ✓" else "غير آمن ✗"
            ), arabicFont))

            // Drawing
            if (drawingView != null) {
                document.add(createStyledParagraph("\nالرسم التفاعلي - Interactive Drawing", arabicFont, 14f, true))
                val bitmap = viewToBitmap(drawingView)
                addBitmapToDocument(document, bitmap)
            }

            // Recommendations
            document.add(createStyledParagraph("\nالتوصيات - Recommendations", arabicFont, 14f, true))
            val recommendations = if (isSafe) {
                "✓ التصميم آمن ومطابق للكود\n✓ يمكن تنفيذ العمل"
            } else {
                "✗ التصميم غير آمن\n✗ يجب مراجعة المقطع وزيادة التسليح"
            }
            document.add(createStyledParagraph(recommendations, arabicFont))

            // Footer
            addReportFooter(document, arabicFont)

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
            val arabicFont = getArabicFont(context)

            addReportHeader(document, header, arabicFont)

            document.add(createStyledParagraph("معلومات المشروع - Project Information", arabicFont, 14f, true))
            document.add(createInfoTable(mapOf(
                "اسم المشروع" to header.projectName,
                "نوع التصميم" to if (isCircular) "عمود دائري" else "عمود مربع",
                "المهندس" to header.engineer,
                "الكود المستخدم" to header.code
            ), arabicFont))

            document.add(createStyledParagraph("\nمعاملات التصميم - Design Parameters", arabicFont, 14f, true))
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
            
            document.add(createInfoTable(params, arabicFont))

            if (drawingView != null) {
                document.add(createStyledParagraph("\nالرسم التفاعلي - Interactive Drawing", arabicFont, 14f, true))
                val bitmap = viewToBitmap(drawingView)
                addBitmapToDocument(document, bitmap)
            }

            document.add(createStyledParagraph("\nالتحقق من الأمان - Safety Check", arabicFont, 14f, true))
            val safetyTable = createSafetyCheckTable(
                actualCapacity = capacity,
                requiredCapacity = appliedLoad,
                ratio = capacity / appliedLoad,
                font = arabicFont
            )
            document.add(safetyTable)

            addReportFooter(document, arabicFont)
            document.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Helper Functions ====================
    private fun addReportHeader(document: Document, header: ReportHeader, font: PdfFont?) {
        val title = createStyledParagraph("تطبيق مساعد المهندس المدني", font, 24f, true)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(PRIMARY_COLOR)
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
        document.add(createInfoTable(headerInfo, font))
        document.add(Paragraph("\n"))
    }

    private fun createInfoTable(info: Map<String, String>, font: PdfFont?): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(3f, 5f))).useAllAvailableWidth()
        table.setMarginBottom(10f)

        info.forEach { (key, value) ->
            addArabicCell(table, key, font, isBold = true)
            addArabicCell(table, value, font)
        }

        return table
    }

    private fun createSafetyCheckTable(
        actualCapacity: Double,
        requiredCapacity: Double,
        ratio: Double,
        font: PdfFont?
    ): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 2f, 2f))).useAllAvailableWidth()

        addArabicCell(table, "القيمة الفعلية (Actual)", font, isBold = true)
        addArabicCell(table, "القيمة المطلوبة (Required)", font, isBold = true)
        addArabicCell(table, "نسبة الأمان (Ratio)", font, isBold = true)

        table.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", actualCapacity))))
        table.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", requiredCapacity))))
        val ratioText = if (ratio >= 1.0) {
            String.format(Locale.getDefault(), "%.2f ✓", ratio)
        } else {
            String.format(Locale.getDefault(), "%.2f ✗", ratio)
        }
        addArabicCell(table, ratioText, font)

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

    private fun addReportFooter(document: Document, font: PdfFont?) {
        document.add(Paragraph("\n"))
        val footer = createStyledParagraph("تم إنشاء هذا التقرير باستخدام تطبيق Civil EG\nGenerated by Civil EG Application", font, 10f)
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
            val arabicFont = getArabicFont(context)

            addReportHeader(document, header, arabicFont)

            document.add(createStyledParagraph("جدول الكميات - Bill of Quantities", arabicFont, 16f, true))
            document.add(createStyledParagraph("\nنوع العنصر: $itemType\nالوصف: $description\n", arabicFont))

            val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 3f))).useAllAvailableWidth()
            addArabicCell(summaryTable, "الخرسانة (m³)", arabicFont, isBold = true)
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", totalConcrete))))
            addArabicCell(summaryTable, "الحديد (kg)", arabicFont, isBold = true)
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", totalSteel))))
            addArabicCell(summaryTable, "الدرجة", arabicFont, isBold = true)
            addArabicCell(summaryTable, concreteGrade, arabicFont)
            addArabicCell(summaryTable, "الإجمالي (EGP)", arabicFont, isBold = true)
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", totalCost))))
            document.add(summaryTable)

            document.add(createStyledParagraph("\nتفاصيل البنود - Detailed Items", arabicFont, 12f, true))
            val itemsTable = Table(UnitValue.createPercentArray(floatArrayOf(2f, 1f, 1f, 1f))).useAllAvailableWidth()
            addArabicCell(itemsTable, "البند", arabicFont, isBold = true)
            addArabicCell(itemsTable, "الكمية", arabicFont, isBold = true)
            addArabicCell(itemsTable, "الوحدة", arabicFont, isBold = true)
            addArabicCell(itemsTable, "التكلفة", arabicFont, isBold = true)

            items.forEach { item ->
                addArabicCell(itemsTable, item.name, arabicFont)
                itemsTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", item.quantity))))
                addArabicCell(itemsTable, item.unit, arabicFont)
                itemsTable.addCell(Cell().add(Paragraph(String.format(Locale.getDefault(), "%.2f", item.totalCost))))
            }
            document.add(itemsTable)

            addReportFooter(document, arabicFont)
            document.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    data class BoqItem(val name: String, val quantity: Double, val unit: String, val totalCost: Double)
}
