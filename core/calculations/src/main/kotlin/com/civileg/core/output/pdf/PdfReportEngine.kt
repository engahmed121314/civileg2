package com.civileg.core.output.pdf

import kotlin.math.max

/**
 * محرك توليد تقارير PDF الثنائية اللغة (عربي/إنجليزي) لملاحظات الحساب الهندسية.
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * // المرجع: مواصفات ملف PDF (ISO 32000-1 / PDF 1.4).
 * // الهيكل الأساسي: رأس الملف -> كائنات -> جدول المراجع التبادلي (xref) -> معاينة -> %%EOF
 * // الترميز: UTF-8 لدعم النص العربي (RTL).
 *
 * // 출력: سلسلة نصية تمثل هيكل PDF يمكن كتابتها إلى ملف بامتداد .pdf
 * // ملاحظة: لإنشاء ملف PDF متكامل مع الخطوط العربية المنسقة، يُستحسن استخدام
 * // مكتبة PDF على طبقة الأندرويد (مثل PdfDocument) واستهلاك هذا المحرك كمصدر للبيانات.
 */
object PdfReportEngine {

    // =========================================================================
    // 1. رأس ملف PDF
    // =========================================================================

    /**
     * يولد رأس ملف PDF بالإصدار 1.4.
     *
     * // %PDF-1.4 يحدد إصدار spécification.
     */
    fun pdfHeader(): String {
        // بايتات عشوائية بعد % لإظهار أن الملف يحتوي على محتوى ثنائي (PDF convention)
        return "%PDF-1.4\n%\u0082\u0083\u0087\u008d\n"
    }

    // =========================================================================
    // 2. كائنات PDF (PDF Objects)
    // =========================================================================

    /**
     * يغلف محتوى في كائن PDF رقمي مع رقم الكائن وإصدار المراجعة.
     *
     * // <objectNumber> <generation> obj\n<content>\nendobj
     */
    fun pdfObject(objectNumber: Int, generation: Int, content: String): String {
        require(objectNumber > 0) { "Object number must be > 0" }
        require(generation >= 0) { "Generation must be >= 0" }
        return "$objectNumber $generation obj\n$content\nendobj\n"
    }

    /**
     * يولد كائن PDF يحتوي على تيار (stream) مع تحديد طوله.
     */
    fun pdfStreamObject(objectNumber: Int, generation: Int, streamContent: String): String {
        require(objectNumber > 0) { "Object number must be > 0" }
        require(generation >= 0) { "Generation must be >= 0" }
        val length = streamContent.length
        return "$objectNumber $generation obj\n<< /Length $length >>\nstream\n$streamContent\nendstream\nendobj\n"
    }

    // =========================================================================
    // 3. كتالوج المستند (Document Catalog)
    // =========================================================================

    /**
     * يولد كائن الكتالوج الذي يشير إلى قاموس الصفحات.
     *
     * // /Type /Catalog /Pages <reference>
     */
    fun documentCatalog(pagesRef: String): String {
        return "<< /Type\n/Catalog\n/Pages\n$pagesRef >>"
    }

    // =========================================================================
    // 4. قاموس الصفحات (Pages Dictionary)
    // =========================================================================

    /**
     * يولد قاموس الصفحات مع قائمة الأبناء وأبعاد الصفحة (MediaBox).
     *
     * // A4 portrait: [0 0 595 842] بالنقاط (1 بوصة = 72 نقطة)
     */
    fun pagesDictionary(kidsRef: String, count: Int, mediaBox: String): String {
        require(count > 0) { "Page count must be > 0" }
        return "<< /Type\n/Pages\n/Kids\n[$kidsRef]\n/Count\n$count\n/MediaBox\n$mediaBox >>"
    }

    // =========================================================================
    // 5. قاموس الصفحة الواحدة (Page Dictionary)
    // =========================================================================

    /**
     * يولد قاموس صفحة واحدة يشير إلى الوالد والمحتوى والموارد.
     */
    fun pageDictionary(
        parentRef: String,
        contentsRef: String,
        resourcesRef: String
    ): String {
        return "<< /Type\n/Page\n/Parent\n$parentRef\n/Contents\n$contentsRef\n/Resources\n$resourcesRef >>"
    }

    // =========================================================================
    // 6. تيار المحتوى (Content Stream) — مشغلات النص
    // =========================================================================

    /**
     * يولد تيار محتوى لرسم نص عند موقع وحجم خط معينين.
     *
     * // BT = Begin Text، ET = End Text
     * // /F<name> <size> Tf = تحديد الخط والحجم
     * // <x> <y> Td = تحديد الموضع
     * // (<text>) Tj = رسم النص
     */
    fun contentStreamText(
        text: String,
        x: Double,
        y: Double,
        fontSize: Double,
        fontRef: String
    ): String {
        require(fontSize > 0.0) { "Font size must be > 0" }
        require(fontRef.isNotEmpty()) { "Font reference must not be empty" }

        val escapedText = escapePdfString(text)
        return "BT\n$fontRef ${fontSize.toString()} Tf\n${x.toString()} ${y.toString()} Td\n($escapedText) Tj\nET\n"
    }

    // =========================================================================
    // 7. قاموس الموارد (Resources Dictionary) — الخطوط
    // =========================================================================

    /**
     * يولد قاموس الموارد الذي يحتوي على قاموس الخطوط.
     */
    fun resourcesDictionary(fontF1Ref: String, fontF2Ref: String): String {
        return "<< /Type\n/Resources\n/Font\n<< /F1\n$fontF1Ref /F2\n$fontF2Ref >> >>"
    }

    // =========================================================================
    // 8. قاموس الخط (Font Dictionary)
    // =========================================================================

    /**
     * يولد قاموس خط Type1 بسيط.
     */
    fun fontDictionary(fontName: String, baseFont: String, encoding: String): String {
        return "<< /Type\n/Font\n/Subtype\n/Type1\n/BaseFont\n/$baseFont\n/Encoding\n/$encoding >>"
    }

    // =========================================================================
    // 9. جدول المراجع التبادلي (Cross-Reference Table)
    // =========================================================================

    /**
     * يولد جدول المراجع التبادلي (xref) مع إزاحات الكائنات في الملف.
     *
     * // كل إدخال: <offset 10 digits> <generation 5 digits> n \n
     * // الإدراج الأول (object 0) يكون مجانيًا (f).
     */
    fun xrefTable(entries: Map<Int, Long>): String {
        require(entries.isNotEmpty()) { "Xref entries must not be empty" }

        val sortedKeys = entries.keys.sorted()
        val totalObjects = sortedKeys.size + 1 // بما في ذلك الكائن 0

        val sb = StringBuilder()
        sb.append("xref\n")
        sb.append("0 $totalObjects\n")
        // الكائن 0: مجاني
        sb.append("0000000000 65535 f \n")
        for (key in sortedKeys) {
            val offset = entries[key]!!
            sb.append("${offset.toString().padStart(10, '0')} 00000 n \n")
        }
        return sb.toString()
    }

    // =========================================================================
    // 10. معاينة الملف (Trailer)
    // =========================================================================

    /**
     * يولد معاينة ملف PDF مع عدد الكائنات ومرجع الكتالوج.
     */
    fun trailer(size: Int, rootRef: String): String {
        require(size > 0) { "Trailer size must be > 0" }
        return "trailer\n<< /Size $size /Root $rootRef >>\n"
    }

    // =========================================================================
    // 11. إنشاء تقرير PDF كامل
    // =========================================================================

    /**
     * ي_section في التقرير.
     */
    data class PdfSection(
        val titleAr: String,
        val titleEn: String,
        val contentAr: String,
        val contentEn: String
    )

    /**
     * يولد تقرير PDF كامل الثنائية اللغة مع أقسام متعددة.
     *
     * // هيكل التقرير:
     * //   %PDF-1.4 -> كائنات (Catalog, Pages, Page, Content, Fonts) -> xref -> trailer -> startxref -> %%EOF
     */
    fun generateReport(
        titleAr: String,
        titleEn: String,
        sections: List<PdfSection>
    ): String {
        require(titleAr.isNotEmpty()) { "Arabic title must not be empty" }
        require(titleEn.isNotEmpty()) { "English title must not be empty" }
        require(sections.isNotEmpty()) { "Sections list must not be empty" }

        val sb = StringBuilder()

        // رأس الملف
        sb.append(pdfHeader())

        // رقم الكائن 시작
        var currentObj = 1

        // الكائن 1: الكتالوج
        val catalogContent = documentCatalog("2 0 R")
        sb.append(pdfObject(currentObj, 0, catalogContent))
        val catalogObj = currentObj
        currentObj++

        // الكائن 2: قاموس الصفحات
        val pagesContent = pagesDictionary("3 0 R", 1, "[0 0 595 842]")
        sb.append(pdfObject(currentObj, 0, pagesContent))
        val pagesObj = currentObj
        currentObj++

        // الكائن 3: قاموس الصفحة
        // الكائن 4: تيار المحتوى
        // الكائن 5: الموارد
        // الكائن 6: الخط F1
        // الكائن 7: الخط F2

        // بناء تيار المحتوى مع جميع الأقسام
        val contentStream = buildContentStream(titleAr, titleEn, sections)
        sb.append(pdfStreamObject(currentObj, 0, contentStream))
        val contentsObj = currentObj
        currentObj++

        // الموارد
        val resourcesContent = resourcesDictionary("6 0 R", "7 0 R")
        sb.append(pdfObject(currentObj, 0, resourcesContent))
        val resourcesObj = currentObj
        currentObj++

        // الخط F1 (Helvetica)
        val font1Content = fontDictionary("/F1", "/Helvetica", "WinAnsiEncoding")
        sb.append(pdfObject(currentObj, 0, font1Content))
        currentObj++

        // الخط F2 (Helvetica للعربية — نفس الخط للتبسيط، يمكن استبداله بخط عربي حقيقي)
        val font2Content = fontDictionary("/F2", "/Helvetica", "WinAnsiEncoding")
        sb.append(pdfObject(currentObj, 0, font2Content))
        currentObj++

        // قاموس الصفحة
        val pageContent = pageDictionary("2 0 R", "$contentsObj 0 R", "$resourcesObj 0 R")
        sb.append(pdfObject(3, 0, pageContent))

        // جدول المراجع التبادلي: جمع إزاحات جميع الكائنات
        // في هذه التنفيذ المبسط، نستخدم إزاحات تقديرية (يجب حسابها بدقة في الإنتاج)
        val xrefEntries = mutableMapOf<Int, Long>()
        // ملاحظة: في التنفيذ الكامل، يجب تسجيل موقع كل كائن في流媒体 أثناء الكتابة.
        // هنا نستخدم إزاحات مكانية للإثبات على الهيكل.
        xrefEntries[1] = 0L
        xrefEntries[2] = 100L
        xrefEntries[3] = 200L

        sb.append(xrefTable(xrefEntries))
        sb.append(trailer(currentObj, "$catalogObj 0 R"))
        sb.append("startxref\n")
        sb.append("0\n") // إزاحة البدء التقديرية
        sb.append("%%EOF\n")

        return sb.toString()
    }

    /**
     * يبني تيار المحتوى مع العنوان والأقسام الثنائية اللغة.
     */
    private fun buildContentStream(
        titleAr: String,
        titleEn: String,
        sections: List<PdfSection>
    ): String {
        val sb = StringBuilder()
        sb.append("BT\n")
        // العنوان العربي
        sb.append("/F2 16 Tf\n")
        sb.append("50 800 Td\n")
        sb.append("(${escapePdfString(titleAr)}) Tj\n")
        // العنوان الإنجليزي
        sb.append("/F1 16 Tf\n")
        sb.append("50 770 Td\n")
        sb.append("(${escapePdfString(titleEn)}) Tj\n")
        sb.append("ET\n")

        var yPos = 730.0
        for (section in sections) {
            // عنوان القسم بالعربية
            sb.append("BT\n")
            sb.append("/F2 12 Tf\n")
            sb.append("50 ${yPos.toString()} Td\n")
            sb.append("(${escapePdfString(section.titleAr)}) Tj\n")
            sb.append("ET\n")
            yPos -= 20.0

            // محتوى القسم بالعربية
            sb.append("BT\n")
            sb.append("/F2 10 Tf\n")
            sb.append("50 ${yPos.toString()} Td\n")
            sb.append("(${escapePdfString(section.contentAr)}) Tj\n")
            sb.append("ET\n")
            yPos -= 20.0

            // عنوان القسم بالإنجليزية
            sb.append("BT\n")
            sb.append("/F1 12 Tf\n")
            sb.append("50 ${yPos.toString()} Td\n")
            sb.append("(${escapePdfString(section.titleEn)}) Tj\n")
            sb.append("ET\n")
            yPos -= 20.0

            // محتوى القسم بالإنجليزية
            sb.append("BT\n")
            sb.append("/F1 10 Tf\n")
            sb.append("50 ${yPos.toString()} Td\n")
            sb.append("(${escapePdfString(section.contentEn)}) Tj\n")
            sb.append("ET\n")
            yPos -= 30.0
        }

        return sb.toString()
    }

    /**
     * يُهدر الأحرف الخاصة في سلاسل PDF وفقًا للمواصفات.
     *
     * // الأحرف التي تحتاج إلى هروب: ( ) \ و سطور جديدة وعلامات تبويب
     */
    fun escapePdfString(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
