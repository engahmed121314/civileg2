package com.civileg.core.output.dxf

import kotlin.math.max

/**
 * محرك توليد ملفات DXF (Drawing Exchange Format) لبرنامج AutoCAD.
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * // المرجع: Autodesk DXF Reference — Group Codes (إصدار R12/R14/2000+).
 * // تنسيق DXF: أزواج من (كود المجموعة على سطر، القيمة على السور التالي).
 * // مثال: 0\nSECTION\n2\nHEADER\n...
 *
 * // دعم أكواد التصميم الثلاثة: ECP 203-2020 (المصري)، ACI 318M-19 (الأمريكي)، SBC 304-2018 (السعودي).
 * // يتم استخدام طبقات DXF منفصلة لكل كود لتمييز الرسومات الناتجة عن كل محرك حسابي.
 *
 * // الوحدات: الأبعاد بالملم (mm) وفقًا لاتفاقية المشروع.
 * // 출력: سلسلة نصية ASCII يمكن كتابتها إلى ملف بامتداد .dxf
 */
object DxfEngine {

    /** رمز نهاية ملف DXF */
    private const val DXF_EOF = "EOF"

    /** معرّف الإصدار AutoCAD 2000 (AC1015) */
    private const val ACAD_VERSION = "AC1015"

    /** معامل بدء العد للتعرف على الكائنات (handles) — يبدأ من 1 */
    private var handleCounter = 0

    // =========================================================================
    // أكواد الطبقات لكل تصميم كود (ECP 203 / ACI 318 / SBC 304)
    // =========================================================================

    /** طبقة الخرسانة للكود المصري ECP 203 */
    const val LAYER_ECP_CONCRETE = "ECP_CONCRETE"
    /** طبقة 철근 للكود المصري ECP 203 */
    const val LAYER_ECP_REBAR = "ECP_REBAR"
    /** طبقة الأبعاد للكود المصري ECP 203 */
    const val LAYER_ECP_DIMENSION = "ECP_DIMENSION"

    /** طبقة الخرسانة للكود الأمريكي ACI 318 */
    const val LAYER_ACI_CONCRETE = "ACI_CONCRETE"
    /** طبقة 철근 للكود الأمريكي ACI 318 */
    const val LAYER_ACI_REBAR = "ACI_REBAR"
    /** طبقة الأبعاد للكود الأمريكي ACI 318 */
    const val LAYER_ACI_DIMENSION = "ACI_DIMENSION"

    /** طبقة الخرسانة للكود السعودي SBC 304 */
    const val LAYER_SBC_CONCRETE = "SBC_CONCRETE"
    /** طبقة 철근 للكود السعودي SBC 304 */
    const val LAYER_SBC_REBAR = "SBC_REBAR"
    /** طبقة الأبعاد للكود السعودي SBC 304 */
    const val LAYER_SBC_DIMENSION = "SBC_DIMENSION"

    /** الطبقة الافتراضية */
    const val LAYER_DEFAULT = "0"

    /**
     * يعيد اسم الطبقة المناسبة للكود المحدد ونوع العنصر.
     *
     * // ECP 203 Cl. 4-2-1 / ACI 318 Cl. 9.3 / SBC 304 Cl. 22.2 — تقسيم الرسومات حسب الكود التصميمي.
     */
    fun layerForCode(designCode: String, elementType: String): String {
        require(designCode.isNotEmpty()) { "Design code must not be empty" }
        require(elementType.isNotEmpty()) { "Element type must not be empty" }

        val code = designCode.uppercase()
        val type = elementType.uppercase()

        return when {
            code.startsWith("ECP") -> when {
                type.contains("CONCRETE") -> LAYER_ECP_CONCRETE
                type.contains("REBAR") || type.contains("STEEL") -> LAYER_ECP_REBAR
                type.contains("DIM") -> LAYER_ECP_DIMENSION
                else -> LAYER_ECP_CONCRETE
            }
            code.startsWith("ACI") -> when {
                type.contains("CONCRETE") -> LAYER_ACI_CONCRETE
                type.contains("REBAR") || type.contains("STEEL") -> LAYER_ACI_REBAR
                type.contains("DIM") -> LAYER_ACI_DIMENSION
                else -> LAYER_ACI_CONCRETE
            }
            code.startsWith("SBC") -> when {
                type.contains("CONCRETE") -> LAYER_SBC_CONCRETE
                type.contains("REBAR") || type.contains("STEEL") -> LAYER_SBC_REBAR
                type.contains("DIM") -> LAYER_SBC_DIMENSION
                else -> LAYER_SBC_CONCRETE
            }
            else -> LAYER_DEFAULT
        }
    }

    private fun nextHandle(): String {
        handleCounter++
        return handleCounter.toString()
    }

    // =========================================================================
    // 1. أزواج أكواد المجموعة (Group Code Pairs)
    // =========================================================================

    /**
     * يكوّن زوج كودجموعة DXF: الكود على السطر الأول، القيمة على السطر الثاني.
     *
     * // DXF Group Code format: <code>\n<value>\n
     */
    fun groupCode(code: Int, value: String): String {
        require(code >= 0) { "Group code must be >= 0" }
        return "$code\n$value\n"
    }

    fun groupCode(code: Int, value: Double): String {
        return groupCode(code, value.toString())
    }

    fun groupCode(code: Int, value: Int): String {
        return groupCode(code, value.toString())
    }

    // =========================================================================
    // 2. قسم HEADER
    // =========================================================================

    /**
     * يولد قسم رأس الملف HEADER بمتغيرات AutoCAD الأساسية.
     *
     * // يحتوي على متغير $ACADVER لإصدار الرسم.
     */
    fun headerSection(): String {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n")
        sb.append("2\nHEADER\n")
        sb.append("9\n\$ACADVER\n")
        sb.append("1\n$ACAD_VERSION\n")
        sb.append("0\nENDSEC\n")
        return sb.toString()
    }

    // ==================================////////////////////////////////=======
    // 3. جدول الطبقات TABLES — LAYER
    // =========================================================================

    /**
     * يولد جدول الطبقات (LAYER TABLE) لقائمة أسماء الطبقات.
     *
     * // يحتوي على entrées لكل طبقة مع علم اللون والنوع.
     */
    fun layerTable(layerNames: List<String>): String {
        require(layerNames.isNotEmpty()) { "Layer list must not be empty" }
        require(layerNames.all { it.isNotEmpty() }) { "Layer names must not be empty" }

        val sb = StringBuilder()
        sb.append("0\nTABLE\n")
        sb.append("2\nLAYER\n")
        sb.append("5\n${nextHandle()}\n")
        sb.append("100\nAcDbSymbolTable\n")
        sb.append("100\nAcDbLayerTable\n")
        sb.append("70\n${layerNames.size}\n") // عدد الطبقات

        for (layerName in layerNames) {
            sb.append("0\nLAYER\n")
            sb.append("5\n${nextHandle()}\n")
            sb.append("100\nAcDbSymbolTableRecord\n")
            sb.append("100\nAcDbLayerTableRecord\n")
            sb.append("2\n$layerName\n") // اسم الطبقة
            sb.append("70\n0\n") // علم الطبقة (0 = عادية)
            sb.append("62\n7\n") // لون الطبقة (أبيض = 7 افتراضيًا)
            sb.append("6\nCONTINUOUS\n") // نوع الخط
        }

        sb.append("0\nENDTAB\n")
        return sb.toString()
    }

    // =========================================================================
    // 4. الكيانات — LINE (خط مستقيم)
    // =========================================================================

    /**
     * يولد كيان خط مستقيم (LINE) من نقطتين.
     *
     * // group codes: 10/20 = نقطة البدء، 11/21 = نقطة النهاية، 8 = الطبقة
     */
    fun lineEntity(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        layer: String = "0"
    ): String {
        require(layer.isNotEmpty()) { "Layer name must not be empty" }

        val sb = StringBuilder()
        sb.append("0\nLINE\n")
        sb.append("5\n${nextHandle()}\n")
        sb.append("8\n$layer\n")
        sb.append("10\n${startX.toString()}\n") // X البدء
        sb.append("20\n${startY.toString()}\n") // Y البدء
        sb.append("30\n0.0\n") // Z البدء
        sb.append("11\n${endX.toString()}\n") // X النهاية
        sb.append("21\n${endY.toString()}\n") // Y النهاية
        sb.append("31\n0.0\n") // Z النهاية
        return sb.toString()
    }

    // =========================================================================
    // 5. الكيانات — CIRCLE (دائرة)
    // =========================================================================

    /**
     * يولد كيان دائرة (CIRCLE) بمركز ونصف قطر.
     *
     * // group codes: 10/20 = المركز، 40 = نصف القطر، 8 = الطبقة
     */
    fun circleEntity(
        centerX: Double,
        centerY: Double,
        radius: Double,
        layer: String = "0"
    ): String {
        require(radius > 0.0) { "Radius must be > 0" }
        require(layer.isNotEmpty()) { "Layer name must not be empty" }

        val sb = StringBuilder()
        sb.append("0\nCIRCLE\n")
        sb.append("5\n${nextHandle()}\n")
        sb.append("8\n$layer\n")
        sb.append("10\n${centerX.toString()}\n") // X المركز
        sb.append("20\n${centerY.toString()}\n") // Y المركز
        sb.append("30\n0.0\n") // Z المركز
        sb.append("40\n${radius.toString()}\n") // نصف القطر
        return sb.toString()
    }

    // =========================================================================
    // 6. الكيانات — TEXT (نص توضيحي)
    // =========================================================================

    /**
     * يولد كيان نص (TEXT) عند موقع معين بارتفاع محدد.
     *
     * // group codes: 10/20 = الموضع، 40 = ارتفاع النص، 1 = قيمة النص، 8 = الطبقة
     */
    fun textEntity(
        text: String,
        x: Double,
        y: Double,
        height: Double,
        layer: String = "0",
        rotation: Double = 0.0
    ): String {
        require(height > 0.0) { "Text height must be > 0" }
        require(layer.isNotEmpty()) { "Layer name must not be empty" }

        val sb = StringBuilder()
        sb.append("0\nTEXT\n")
        sb.append("5\n${nextHandle()}\n")
        sb.append("8\n$layer\n")
        sb.append("10\n${x.toString()}\n") // X الموضع
        sb.append("20\n${y.toString()}\n") // Y الموضع
        sb.append("30\n0.0\n") // Z الموضع
        sb.append("40\n${height.toString()}\n") // ارتفاع النص
        sb.append("1\n${escapeDxfText(text)}\n") // قيمة النص
        sb.append("50\n${rotation.toString()}\n") // زاوية الدوران
        return sb.toString()
    }

    /**
     * يُهدر الأحرف الخاصة في نصوص DXF لمنع كسر التنسيق.
     */
    private fun escapeDxfText(text: String): String {
        return text.replace("\n", "\\P").replace("\r", "")
    }

    // =========================================================================
    // 7. الكيانات — 철근 (Rebar) كخط مع تسمية القطر
    // =========================================================================

    /**
     * يولد كيان خط يمثل سيخ حديد تسليح (Rebar) مع تسمية قطره كنص.
     *
     * // يتكون من LINE للنسيج + TEXT لقطر السيخ.
     */
    fun rebarEntity(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        diameterMm: Double,
        layer: String = "REBAR"
    ): String {
        require(diameterMm > 0.0) { "Rebar diameter must be > 0" }

        val sb = StringBuilder()
        sb.append(lineEntity(startX, startY, endX, endY, layer))
        // تسمية القطر عند منتصف الخط
        val midX = (startX + endX) / 2.0
        val midY = (startY + endY) / 2.0
        sb.append(textEntity(
            text = "Ø${diameterMm.toInt()} mm",
            x = midX,
            y = midY,
            height = 25.0,
            layer = "DIMENSION"
        ))
        return sb.toString()
    }

    // =========================================================================
    // 8. قسم الكيانات ENTITIES
    // =========================================================================

    /**
     * يولد قسم الكيانات ENTITIES من قائمة نصوص الكيانات.
     */
    fun entitiesSection(entities: List<String>): String {
        require(entities.isNotEmpty()) { "Entities list must not be empty" }

        val sb = StringBuilder()
        sb.append("0\nSECTION\n")
        sb.append("2\nENTITIES\n")
        for (entity in entities) {
            sb.append(entity)
        }
        sb.append("0\nENDSEC\n")
        return sb.toString()
    }

    // =========================================================================
    // 9. ملف DXF كامل
    // =========================================================================

    /**
     * يولد جدول أقطار الحديد التسليح القياسية حسب الكود التصميمي.
     *
     * // ECP 203 Cl. 4-2-5 / ACI 318 Cl. 7.5 / SBC 304 Cl. 7.5 — أقطار φ8 إلى φ32.
     * // يدرج القطر، المساحة مقطعية، والوزن بالمتر الطولي لكل سيخ.
     */
    fun rebarDiameterTableDxf(designCode: String, startY: Double = 0.0): String {
        require(designCode.isNotEmpty()) { "Design code must not be empty" }

        val sb = StringBuilder()
        val layer = layerForCode(designCode, "REBAR")

        // أقطار الحديد القياسية (مم) حسب جميع الأكواد الثلاثة
        val diameters = listOf(8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)

        // رأس الجدول
        sb.append(textEntity(
            text = "أقطار الحديد التسليح ($designCode) / Rebar Diameters ($designCode)",
            x = 0.0, y = startY,
            height = 30.0,
            layer = layerForCode(designCode, "DIMENSION"),
            rotation = 0.0
        ))

        var yPos = startY - 40.0
        for (d in diameters) {
            val area = kotlin.math.PI * d * d / 4.0 // مم²
            val weight = d * d * 0.006165 // كغ/م

            // خط القطر
            sb.append(lineEntity(0.0, yPos, 50.0, yPos, layer))
            // قيمة القطر كنص
            sb.append(textEntity(
                text = "Ø${d.toInt()}",
                x = 10.0, y = yPos - 5.0,
                height = 20.0,
                layer = layerForCode(designCode, "DIMENSION")
            ))
            // المساحة المقطعية
            sb.append(textEntity(
                text = "A=${area.toString().take(5)} مم²",
                x = 60.0, y = yPos - 5.0,
                height = 20.0,
                layer = layerForCode(designCode, "DIMENSION")
            ))
            // الوزن
            sb.append(textEntity(
                text = "W=${weight.toString().take(5)} كغ/م",
                x = 150.0, y = yPos - 5.0,
                height = 20.0,
                layer = layerForCode(designCode, "DIMENSION")
            ))

            yPos -= 35.0
        }

        return sb.toString()
    }

    /**
     * يولد كتلة عنوان (Title Block) تحتوي على معلومات الكود التصميمي والمشروع.
     *
     * // يحتوي على اسم الكود، إصدار الكود، نوع العنصر، وتاريخ التصميم.
     */
    fun titleBlockDxf(
        designCode: String,
        projectName: String,
        elementType: String,
        drawnBy: String = "CivilEG2"
    ): String {
        require(designCode.isNotEmpty()) { "Design code must not be empty" }
        require(projectName.isNotEmpty()) { "Project name must not be empty" }

        val sb = StringBuilder()
        val layer = LAYER_DEFAULT

        // إطار الكتلة
        sb.append(lineEntity(0.0, 0.0, 2000.0, 0.0, layer))
        sb.append(lineEntity(2000.0, 0.0, 2000.0, -500.0, layer))
        sb.append(lineEntity(2000.0, -500.0, 0.0, -500.0, layer))
        sb.append(lineEntity(0.0, -500.0, 0.0, 0.0, layer))

        // معلومات الكود
        sb.append(textEntity(
            text = "كود التصميم: $designCode / Design Code: $designCode",
            x = 50.0, y = -100.0,
            height = 25.0,
            layer = layerForCode(designCode, "DIMENSION")
        ))
        sb.append(textEntity(
            text = "المشروع: $projectName / Project: $projectName",
            x = 50.0, y = -150.0,
            height = 25.0,
            layer = layerForCode(designCode, "DIMENSION")
        ))
        sb.append(textEntity(
            text = "العنصر: $elementType / Element: $elementType",
            x = 50.0, y = -200.0,
            height = 25.0,
            layer = layerForCode(designCode, "DIMENSION")
        ))
        sb.append(textEntity(
            text = "إعداد: $drawnBy / Drawn by: $drawnBy",
            x = 50.0, y = -250.0,
            height = 25.0,
            layer = layerForCode(designCode, "DIMENSION")
        ))

        return sb.toString()
    }

    /**
     * يولد ملف DXF كامل مع دعم الكود التصميمي المحدد.
     *
     * // يتضمن رأس الملف، جداول الطبقات المخصصة لكل كود، كتلة العنوان، وجدول أقطار الحديد.
     * // ECP 203 / ACI 318 / SBC 304 — 모든 الأكواد مدعومة.
     */
    fun generateCompleteDxfWithCode(
        drawingName: String,
        designCode: String,
        projectName: String,
        elementType: String,
        layers: List<String>,
        entities: List<String>
    ): String {
        require(drawingName.isNotEmpty()) { "Drawing name must not be empty" }
        require(designCode.isNotEmpty()) { "Design code must not be empty" }
        require(projectName.isNotEmpty()) { "Project name must not be empty" }
        require(layers.isNotEmpty()) { "Layers list must not be empty" }
        require(entities.isNotEmpty()) { "Entities list must not be empty" }

        val sb = StringBuilder()
        resetHandles()

        sb.append(headerSection())

        // جدول الطبقات مع طبقات الكود المحددة
        sb.append("0\nSECTION\n")
        sb.append("2\nTABLES\n")
        sb.append(layerTable(layers))
        sb.append("0\nENDSEC\n")

        // كتلة العنوان مع معلومات الكود
        sb.append(titleBlockDxf(designCode, projectName, elementType))

        // جدول أقطار الحديد حسب الكود
        sb.append(rebarDiameterTableDxf(designCode))

        // قسم الكيانات
        sb.append(entitiesSection(entities))
        sb.append("0\n$DXF_EOF\n")

        return sb.toString()
    }

    /**
     * يولد ملف DXF كامل بقسمات HEADER و TABLES و ENTITIES.
     *
     * // هيكل الملف:
     * //   HEADER -> TABLES (LAYER) -> ENTITIES -> EOF
     */
    fun generateCompleteDxf(
        drawingName: String,
        layers: List<String>,
        entities: List<String>
    ): String {
        require(drawingName.isNotEmpty()) { "Drawing name must not be empty" }
        require(layers.isNotEmpty()) { "Layers list must not be empty" }
        require(entities.isNotEmpty()) { "Entities list must not be empty" }

        val sb = StringBuilder()
        // إعادةعداد عداد المقادير لكل ملف جديد
        resetHandles()

        sb.append(headerSection())
        sb.append("0\nSECTION\n")
        sb.append("2\nTABLES\n")
        sb.append(layerTable(layers))
        sb.append("0\nENDSEC\n")
        sb.append(entitiesSection(entities))
        sb.append("0\n$DXF_EOF\n")

        return sb.toString()
    }

    /**
     * إعادةعداد عداد المقادير (handles) لبدء جديد.
     */
    fun resetHandles() {
        handleCounter = 0
    }
}
