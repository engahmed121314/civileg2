package com.civilengineer.assistant.utils

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.civilengineer.assistant.models.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * نظام تصدير النتائج إلى PDF
 * PDF Export System for Design Results
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595  // A4
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 18f

    private val titlePaint = Paint().apply {
        textSize = 20f
        isFakeBoldText = true
        color = android.graphics.Color.parseColor("#1565C0")
    }

    private val subtitlePaint = Paint().apply {
        textSize = 14f
        isFakeBoldText = true
        color = android.graphics.Color.parseColor("#0D47A1")
    }

    private val normalPaint = Paint().apply {
        textSize = 11f
        color = android.graphics.Color.BLACK
    }

    private val smallPaint = Paint().apply {
        textSize = 9f
        color = android.graphics.Color.GRAY
    }

    private val boldPaint = Paint().apply {
        textSize = 11f
        isFakeBoldText = true
        color = android.graphics.Color.BLACK
    }

    private val safePaint = Paint().apply {
        textSize = 14f
        isFakeBoldText = true
        color = android.graphics.Color.parseColor("#2E7D32")
    }

    private val unsafePaint = Paint().apply {
        textSize = 14f
        isFakeBoldText = true
        color = android.graphics.Color.parseColor("#C62828")
    }

    private val linePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#BBDEFB")
        strokeWidth = 1f
    }

    /**
     * تصدير نتائج تصميم العمود
     */
    fun exportColumnDesign(
        context: Context,
        result: EngineeringCalculations.ColumnDesignResult,
        code: DesignCode,
        fcu: Double,
        fy: Double,
        Pu: Double,
        Mu: Double,
        b: Double,
        h: Double,
        length: Double,
        quantitySurvey: QuantitySurveyResult? = null,
        costResult: CostResult? = null
    ): File? {
        return try {
            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var y = MARGIN + 30f

            // === الرأس ===
            canvas.drawText("مساعد المهندس المدني - Civil Engineer Assistant", MARGIN, y, titlePaint)
            y += 30f
            canvas.drawText("تقرير تصميم العمود - Column Design Report", MARGIN, y, subtitlePaint)
            y += 25f

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            canvas.drawText("التاريخ: ${dateFormat.format(Date())}", MARGIN, y, smallPaint)
            canvas.drawText("الكود: ${code.displayName}", MARGIN + 200, y, smallPaint)
            y += 20f

            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f

            // === بيانات المدخلات ===
            canvas.drawText("بيانات المدخلات:", MARGIN, y, subtitlePaint)
            y += LINE_HEIGHT + 5

            val inputs = listOf(
                "الحمل المحوري Pu = ${String.format("%.1f", Pu)} kN",
                "العزم Mu = ${String.format("%.1f", Mu)} kN.m",
                "أبعاد العمود: ${b.toInt()} × ${h.toInt()} mm",
                "طول العمود Lu = ${length.toInt()} mm",
                "رتبة الخرسانة fcu = ${fcu.toInt()} N/mm²",
                "رتبة الحديد fy = ${fy.toInt()} N/mm²"
            )
            inputs.forEach { line ->
                canvas.drawText("  • $line", MARGIN + 10, y, normalPaint)
                y += LINE_HEIGHT
            }
            y += 10f

            // === النتائج ===
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 15f
            canvas.drawText("نتائج التصميم:", MARGIN, y, subtitlePaint)
            y += LINE_HEIGHT + 5

            // حالة الأمان
            canvas.drawText(
                if (result.isSafe) "الحالة: آمن (SAFE)" else "الحالة: غير آمن (UNSAFE)",
                MARGIN + 10, y,
                if (result.isSafe) safePaint else unsafePaint
            )
            y += LINE_HEIGHT + 5

            val results = listOf(
                "نوع العمود: ${if (result.isShortColumn) "قصير" else "طويل"} (λ = ${String.format("%.1f", result.slendernessRatio)})",
                "مساحة التسليح المطلوبة As = ${String.format("%.0f", result.requiredAs)} mm²",
                "مساحة التسليح المقدمة = ${String.format("%.0f", result.providedAs)} mm²",
                "التسليح: ${result.numberOfBars} φ ${result.barDiameter.toInt()} mm",
                "نسبة التسليح ρ = ${String.format("%.2f", result.ratio * 100)}%",
                "كانات: φ${result.stirrupDiameter.toInt()} @ ${result.stirrupSpacing.toInt()} mm",
                "سعة العمود = ${String.format("%.0f", result.capacity)} kN",
                "معامل الأمان = ${String.format("%.2f", result.safetyFactor)}"
            )
            results.forEach { line ->
                canvas.drawText("  • $line", MARGIN + 10, y, boldPaint)
                y += LINE_HEIGHT
            }
            y += 10f

            // === فحوصات الكود ===
            if (result.codeChecks.isNotEmpty()) {
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 15f
                canvas.drawText("فحوصات الكود:", MARGIN, y, subtitlePaint)
                y += LINE_HEIGHT + 5

                result.codeChecks.forEach { check ->
                    val status = if (check.isPassed) "✓" else "✗"
                    canvas.drawText(
                        "  $status ${check.checkName}: ${check.actualValue} (${check.codeClause})",
                        MARGIN + 10, y,
                        if (check.isPassed) normalPaint else unsafePaint
                    )
                    y += LINE_HEIGHT
                }
                y += 10f
            }

            // === خطوات الحل ===
            if (result.equations.isNotEmpty()) {
                // فحص هل نحتاج صفحة جديدة
                if (y > PAGE_HEIGHT - 200) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                }

                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 15f
                canvas.drawText("خطوات الحل والمعادلات:", MARGIN, y, subtitlePaint)
                y += LINE_HEIGHT + 5

                result.equations.forEach { step ->
                    if (y > PAGE_HEIGHT - 80) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        y = MARGIN + 20f
                    }

                    canvas.drawText("  ${step.stepNumber}. ${step.title}", MARGIN + 10, y, boldPaint)
                    y += LINE_HEIGHT
                    canvas.drawText("     ${step.equation}", MARGIN + 20, y, normalPaint)
                    y += LINE_HEIGHT
                    canvas.drawText("     ${step.result}", MARGIN + 20, y, normalPaint)
                    y += LINE_HEIGHT
                    if (step.codeReference.isNotEmpty()) {
                        canvas.drawText("     [${step.codeReference}]", MARGIN + 20, y, smallPaint)
                        y += LINE_HEIGHT
                    }
                    y += 5f
                }
            }

            // === الحصر ===
            if (quantitySurvey != null) {
                if (y > PAGE_HEIGHT - 150) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                }

                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 15f
                canvas.drawText("الحصر:", MARGIN, y, subtitlePaint)
                y += LINE_HEIGHT + 5

                val qItems = listOf(
                    "حجم الخرسانة = ${String.format("%.3f", quantitySurvey.concreteVolume)} م³",
                    "حجم الخرسانة + هالك = ${String.format("%.3f", quantitySurvey.concreteWithWaste)} م³",
                    "وزن الحديد = ${String.format("%.2f", quantitySurvey.steelWeight)} كجم",
                    "وزن الحديد + هالك = ${String.format("%.2f", quantitySurvey.steelWithWaste)} كجم",
                    "مساحة الشدات = ${String.format("%.2f", quantitySurvey.formworkArea)} م²"
                )
                qItems.forEach { line ->
                    canvas.drawText("  • $line", MARGIN + 10, y, normalPaint)
                    y += LINE_HEIGHT
                }
            }

            // === التكلفة ===
            if (costResult != null) {
                y += 10f
                canvas.drawText("التكلفة التقديرية:", MARGIN, y, subtitlePaint)
                y += LINE_HEIGHT + 5

                costResult.breakdown.forEach { item ->
                    canvas.drawText(
                        "  • ${item.description}: ${String.format("%,.0f", item.totalPrice)} ${costResult.currency.symbol}",
                        MARGIN + 10, y, normalPaint
                    )
                    y += LINE_HEIGHT
                }
                canvas.drawText(
                    "  الإجمالي: ${String.format("%,.0f", costResult.totalCost)} ${costResult.currency.symbol}",
                    MARGIN + 10, y, boldPaint
                )
            }

            // إنهاء الصفحة
            document.finishPage(page)

            // حفظ الملف
            val fileName = "ColumnDesign_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            document.writeTo(FileOutputStream(file))
            document.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * تصدير عام لأي نتائج تصميم
     */
    fun exportGenericReport(
        context: Context,
        title: String,
        subtitle: String,
        codeName: String,
        inputs: List<Pair<String, String>>,
        results: List<Pair<String, String>>,
        isSafe: Boolean,
        equations: List<EquationStep> = emptyList(),
        codeChecks: List<CodeCheck> = emptyList(),
        quantitySurvey: QuantitySurveyResult? = null,
        costResult: CostResult? = null
    ): File? {
        return try {
            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var y = MARGIN + 30f

            // رأس
            canvas.drawText("مساعد المهندس المدني", MARGIN, y, titlePaint)
            y += 30f
            canvas.drawText(title, MARGIN, y, subtitlePaint)
            y += 20f
            canvas.drawText(subtitle, MARGIN, y, smallPaint)
            y += 15f

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            canvas.drawText("التاريخ: ${dateFormat.format(Date())}  |  الكود: $codeName", MARGIN, y, smallPaint)
            y += 20f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f

            // حالة الأمان
            canvas.drawText(
                if (isSafe) "الحالة: آمن (SAFE)" else "الحالة: غير آمن (UNSAFE)",
                MARGIN, y, if (isSafe) safePaint else unsafePaint
            )
            y += 25f

            // مدخلات
            canvas.drawText("المدخلات:", MARGIN, y, subtitlePaint)
            y += LINE_HEIGHT + 3
            inputs.forEach { (label, value) ->
                canvas.drawText("  • $label: $value", MARGIN + 10, y, normalPaint)
                y += LINE_HEIGHT
            }
            y += 10f

            // نتائج
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 15f
            canvas.drawText("النتائج:", MARGIN, y, subtitlePaint)
            y += LINE_HEIGHT + 3
            results.forEach { (label, value) ->
                canvas.drawText("  • $label: $value", MARGIN + 10, y, boldPaint)
                y += LINE_HEIGHT
            }
            y += 10f

            // فحوصات الكود
            if (codeChecks.isNotEmpty()) {
                if (y > PAGE_HEIGHT - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                }
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 15f
                canvas.drawText("فحوصات الكود:", MARGIN, y, subtitlePaint)
                y += LINE_HEIGHT + 3
                codeChecks.forEach { check ->
                    val status = if (check.isPassed) "✓ PASS" else "✗ FAIL"
                    canvas.drawText("  $status - ${check.checkName}: ${check.actualValue}", MARGIN + 10, y,
                        if (check.isPassed) normalPaint else unsafePaint)
                    y += LINE_HEIGHT
                }
                y += 10f
            }

            // معادلات
            if (equations.isNotEmpty()) {
                if (y > PAGE_HEIGHT - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                }
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 15f
                canvas.drawText("خطوات الحل:", MARGIN, y, subtitlePaint)
                y += LINE_HEIGHT + 3

                equations.forEach { step ->
                    if (y > PAGE_HEIGHT - 60) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        y = MARGIN + 20f
                    }
                    canvas.drawText("  ${step.stepNumber}. ${step.title}: ${step.equation}", MARGIN + 10, y, normalPaint)
                    y += LINE_HEIGHT
                    canvas.drawText("     => ${step.result}", MARGIN + 20, y, boldPaint)
                    y += LINE_HEIGHT
                    if (step.codeReference.isNotEmpty()) {
                        canvas.drawText("     [${step.codeReference}]", MARGIN + 20, y, smallPaint)
                        y += LINE_HEIGHT
                    }
                }
            }

            // حصر
            if (quantitySurvey != null) {
                if (y > PAGE_HEIGHT - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                }
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 15f
                canvas.drawText("الحصر:", MARGIN, y, subtitlePaint)
                y += LINE_HEIGHT + 3
                canvas.drawText("  خرسانة: ${String.format("%.3f", quantitySurvey.concreteWithWaste)} م³", MARGIN + 10, y, normalPaint)
                y += LINE_HEIGHT
                canvas.drawText("  حديد: ${String.format("%.2f", quantitySurvey.steelWithWaste)} كجم", MARGIN + 10, y, normalPaint)
                y += LINE_HEIGHT
                canvas.drawText("  شدات: ${String.format("%.2f", quantitySurvey.formworkArea)} م²", MARGIN + 10, y, normalPaint)
                y += LINE_HEIGHT + 10
            }

            // تكلفة
            if (costResult != null) {
                canvas.drawText("التكلفة: ${String.format("%,.0f", costResult.totalCost)} ${costResult.currency.symbol}", MARGIN + 10, y, boldPaint)
            }

            document.finishPage(page)

            val fileName = "Report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            document.writeTo(FileOutputStream(file))
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * مشاركة ملف PDF
     */
    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة التقرير"))
    }
}
