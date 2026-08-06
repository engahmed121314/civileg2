package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SteelViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine,
    private val settingsManager: com.civileg.app.utils.SettingsManager
) : ViewModel() {

    private val _result = MutableLiveData<SteelMemberResult?>()
    val result: LiveData<SteelMemberResult?> = _result

    private val _warehouseResult = MutableLiveData<SteelWarehouseAnalysisResult?>()
    val warehouseResult: LiveData<SteelWarehouseAnalysisResult?> = _warehouseResult

    private val _warehouseProResult = MutableLiveData<SteelWarehouseProResult?>()
    val warehouseProResult: LiveData<SteelWarehouseProResult?> = _warehouseProResult

    private var lastWarehouseInputs: SteelWarehouseInputs? = null

    // Store actual steel member inputs for PDF export
    private var lastMemberInputs: SteelMemberStoredInputs? = null

    private data class SteelMemberStoredInputs(
        val section: SteelSectionType,
        val memberType: SteelMemberType,
        val inputs: SteelInputs,
        val code: CalculatorEngine.DesignCode
    )

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    val sectionLibrary: Map<String, List<SteelSectionType>> = calculatorEngine.getSteelSectionLibrary()

    private val _searchResults = MutableLiveData<List<SteelSectionType>>()
    val searchResults: LiveData<List<SteelSectionType>> = _searchResults

    fun searchSections(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        val all = sectionLibrary.values.flatten()
        _searchResults.value = all.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    fun calculateSteelMember(
        section: SteelSectionType,
        memberType: SteelMemberType,
        inputs: SteelInputs,
        code: CalculatorEngine.DesignCode
    ) {
        // Store actual inputs for PDF export
        lastMemberInputs = SteelMemberStoredInputs(section, memberType, inputs, code)

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val res = calculatorEngine.calculateSteelMember(section, memberType, inputs, code)
                _result.value = res
            } catch (e: Exception) {
                _result.value = null
                _errorMessage.value = "Error in Steel calculation: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun calculateWarehouse(inputs: SteelWarehouseInputs) {
        lastWarehouseInputs = inputs
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val res = calculatorEngine.designSteelWarehouse(inputs)
                _warehouseResult.value = res

                // Also trigger the Pro calculation for better drawings/report
                val proRes = calculatorEngine.calculateSteelWarehousePro(inputs)
                _warehouseProResult.value = proRes
            } catch (e: Exception) {
                _warehouseResult.value = null
                _errorMessage.value = "Error in Warehouse calculation: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ========================================================================
    // Warehouse PDF Export — English only with per-section drawings
    // ========================================================================

    fun exportWarehouseProToPdf(
        context: android.content.Context,
        clientAr: String, clientEn: String, projAr: String, projEn: String,
        onComplete: (java.io.File?) -> Unit
    ) {
        val res = _warehouseResult.value ?: return
        val inputs = lastWarehouseInputs ?: return

        // Generate per-section cross-section drawings and load diagram
        val sectionDrawings = generateSectionDrawings(inputs, res)
        val loadDiagram = generateLoadDiagram(inputs)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // Use new English-only exporter (no Arabic, no garbled text)
                val exporter = com.civileg.app.utils.exporters.SteelEnglishReportExporter(context)
                val file = exporter.export(
                    inputs = inputs,
                    result = res,
                    projectName = projEn.ifEmpty { "Steel Warehouse Project" },
                    clientName = clientEn.ifEmpty { "Client" },
                    sectionDrawings = sectionDrawings,
                    loadDiagramBitmap = loadDiagram
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (file != null) {
                        com.civileg.app.utils.ExportUtils.openPdf(context, file)
                    }
                    onComplete(file)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _errorMessage.value = "Warehouse PDF export failed: ${e.message ?: \"Unknown error\"}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    /** Generate cross-section drawing bitmaps for each steel member type */
    private fun generateSectionDrawings(
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult
    ): Map<String, android.graphics.Bitmap> {
        val drawings = mutableMapOf<String, android.graphics.Bitmap>()
        val sections = listOf(
            "C1" to result.mainFrame.columnSection,
            "R1" to result.mainFrame.rafterSection,
            "P1" to result.secondaryMembers.purlinSection,
            "G1" to result.secondaryMembers.girtSection,
            "B1" to result.secondaryMembers.bracingSection
        )
        for ((mark, section) in sections) {
            try { drawings[mark] = createSectionBitmap(mark, section) }
            catch (_: Exception) { }
        }
        return drawings
    }

    /** Create a cross-section drawing bitmap for a single steel section */
    private fun createSectionBitmap(mark: String, section: SteelSectionType): android.graphics.Bitmap {
        val w = 800; val h = 600
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 3f; style = android.graphics.Paint.Style.STROKE; isAntiAlias = true }
        val textPaint = android.graphics.Paint().apply { color = android.graphics.Color.BLUE; textSize = 36f; isFakeBoldText = true; isAntiAlias = true }
        val dimPaint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 24f; isAntiAlias = true }
        val fillPaint = android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; style = android.graphics.Paint.Style.FILL; isAntiAlias = true }

        canvas.drawText("$mark - ${section.displayName}", 40f, 50f, textPaint)

        val cx = w / 2f; val cy = h / 2f + 20f; val scale = 2.0f
        val depth = section.depth * scale; val width = section.width * scale
        val tw = section.webThickness * scale; val tf = section.flangeThickness * scale
        val left = cx - width / 2f; val right = cx + width / 2f
        val top = cy - depth / 2f; val bottom = cy + depth / 2f

        canvas.drawRect(left, top, right, top + tf, fillPaint)
        canvas.drawRect(left, bottom - tf, right, bottom, fillPaint)
        canvas.drawRect(cx - tw / 2f, top + tf, cx + tw / 2f, bottom - tf, fillPaint)

        canvas.drawRect(left, top, right, bottom, paint)
        canvas.drawLine(cx - tw / 2f, top + tf, cx - tw / 2f, bottom - tf, paint)
        canvas.drawLine(cx + tw / 2f, top + tf, cx + tw / 2f, bottom - tf, paint)
        canvas.drawLine(left, top + tf, cx - tw / 2f, top + tf, paint)
        canvas.drawLine(cx + tw / 2f, top + tf, right, top + tf, paint)
        canvas.drawLine(left, bottom - tf, cx - tw / 2f, bottom - tf, paint)
        canvas.drawLine(cx + tw / 2f, bottom - tf, right, bottom - tf, paint)

        val dimX = left - 30f
        canvas.drawLine(dimX, top, dimX, bottom, dimPaint)
        canvas.drawLine(dimX - 8f, top, dimX + 8f, top, dimPaint)
        canvas.drawLine(dimX - 8f, bottom, dimX + 8f, bottom, dimPaint)
        canvas.drawText("h=${section.depth}mm", dimX - 140f, cy + 8f, dimPaint)

        val dimY = bottom + 40f
        canvas.drawLine(left, dimY, right, dimY, dimPaint)
        canvas.drawLine(left, dimY - 8f, left, dimY + 8f, dimPaint)
        canvas.drawLine(right, dimY - 8f, right, dimY + 8f, dimPaint)
        canvas.drawText("b=${section.width}mm", cx - 60f, dimY + 35f, dimPaint)

        val tableY = bottom + 90f
        canvas.drawText("Section: ${section.displayName}", 40f, tableY, textPaint)
        canvas.drawText("Area: ${section.area} cm2  |  Weight: ${section.weightPerMetre} kg/m", 40f, tableY + 35f, dimPaint)
        canvas.drawText("Ix: ${section.momentOfInertiaX} cm4  |  Iy: ${section.momentOfInertiaY} cm4", 40f, tableY + 65f, dimPaint)
        canvas.drawText("Zx: ${section.plasticModulusX} cm3  |  Sx: ${section.elasticModulusX} cm3", 40f, tableY + 95f, dimPaint)

        return bitmap
    }

    /** Generate a load diagram bitmap showing applied loads on the warehouse frame */
    private fun generateLoadDiagram(inputs: SteelWarehouseInputs): android.graphics.Bitmap {
        val w = 1000; val h = 700
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val titlePaint = android.graphics.Paint().apply { color = android.graphics.Color.BLUE; textSize = 32f; isFakeBoldText = true; isAntiAlias = true }
        val framePaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 4f; style = android.graphics.Paint.Style.STROKE; isAntiAlias = true }
        val loadPaint = android.graphics.Paint().apply { color = android.graphics.Color.RED; strokeWidth = 3f; style = android.graphics.Paint.Style.STROKE; isAntiAlias = true }
        val textP = android.graphics.Paint().apply { color = android.graphics.Color.RED; textSize = 22f; isAntiAlias = true }
        val dimPaint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 20f; isAntiAlias = true }
        val supportPaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 3f; style = android.graphics.Paint.Style.FILL; isAntiAlias = true }

        canvas.drawText("LOAD DIAGRAM - Warehouse Frame", 40f, 45f, titlePaint)

        val padding = 80f; val drawW = w - 2 * padding
        val scale = (drawW / inputs.span).toFloat() * 0.7f
        val baseY = h - padding
        val colLX = padding + (drawW - inputs.span.toFloat() * scale) / 2f
        val colRX = colLX + inputs.span.toFloat() * scale
        val midX = (colLX + colRX) / 2f
        val eh = inputs.eaveHeight.toFloat() * scale
        val rh = inputs.ridgeHeight.toFloat() * scale

        canvas.drawLine(colLX, baseY, colLX, baseY - eh, framePaint)
        canvas.drawLine(colRX, baseY, colRX, baseY - eh, framePaint)
        canvas.drawLine(colLX, baseY - eh, midX, baseY - rh, framePaint)
        canvas.drawLine(colRX, baseY - eh, midX, baseY - rh, framePaint)

        val triH = 20f
        canvas.drawLine(colLX - 15f, baseY, colLX + 15f, baseY, supportPaint)
        canvas.drawLine(colLX - 15f, baseY, colLX, baseY + triH, supportPaint)
        canvas.drawLine(colLX + 15f, baseY, colLX, baseY + triH, supportPaint)
        canvas.drawLine(colRX - 15f, baseY, colRX + 15f, baseY, supportPaint)
        canvas.drawLine(colRX - 15f, baseY, colRX, baseY + triH, supportPaint)
        canvas.drawLine(colRX + 15f, baseY, colRX, baseY + triH, supportPaint)

        val numArrows = 10
        for (i in 0..numArrows) {
            val t = i.toFloat() / numArrows
            val lx = colLX + t * (midX - colLX)
            val ly = (baseY - eh) + t * ((baseY - rh) - (baseY - eh))
            canvas.drawLine(lx, ly - 25f, lx, ly, loadPaint)
            canvas.drawLine(lx - 4f, ly - 25f, lx + 4f, ly - 25f, loadPaint)
            val rx = midX + t * (colRX - midX)
            val ry = (baseY - rh) + t * ((baseY - eh) - (baseY - rh))
            canvas.drawLine(rx, ry - 25f, rx, ry, loadPaint)
            canvas.drawLine(rx - 4f, ry - 25f, rx + 4f, ry - 25f, loadPaint)
        }
        canvas.drawText("DL + LL (Roof)", midX - 60f, (baseY - rh) - 35f, textP)

        for (i in 1..5) {
            val t = i.toFloat() / 6f
            val wy = baseY - t * eh
            canvas.drawLine(colRX + 15f, wy, colRX + 45f, wy, loadPaint)
            canvas.drawLine(colRX + 40f, wy - 5f, colRX + 45f, wy, loadPaint)
            canvas.drawLine(colRX + 40f, wy + 5f, colRX + 45f, wy, loadPaint)
        }
        canvas.drawText("Wind", colRX + 50f, baseY - eh / 2f, textP)

        canvas.drawLine(colLX, baseY + triH + 15f, colRX, baseY + triH + 15f, dimPaint)
        canvas.drawText("Span = ${inputs.span} m", midX - 50f, baseY + triH + 40f, dimPaint)

        return bitmap
    }

    fun saveSteelMemberDesign(projectId: Long, name: String, result: SteelMemberResult) {
        viewModelScope.launch { repository.saveSteelMemberDesign(projectId, name, result) }
    }

    fun saveSteelWarehouseDesign(projectId: Long, name: String, result: SteelWarehouseAnalysisResult) {
        viewModelScope.launch { repository.saveSteelWarehouseDesign(projectId, name, result) }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val res = _result.value ?: return
        val stored = lastMemberInputs ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "Steel_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, fileName)

                val drawingBitmap = try {
                    PdfDrawingGenerator.generateSteelDrawing(
                        sectionName = stored.section.displayName,
                        sectionHeight = stored.section.depth,
                        flangeWidth = stored.section.width,
                        webThickness = stored.section.webThickness,
                        flangeThickness = stored.section.flangeThickness,
                        memberLength = stored.inputs.length,
                        isSafe = res.isSafe,
                        utilizationRatio = res.utilizationRatio * 100
                    )
                } catch (e: Exception) { e.printStackTrace(); null }

                val isAr = com.civileg.app.utils.LocaleHelper.isArabic()
                fun t(ar: String, en: String) = if (isAr) ar else en

                val codeName = when (stored.code) {
                    CalculatorEngine.DesignCode.ACI -> "ACI 318"
                    CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
                    else -> "ECP 203"
                }

                val inputsMap = mapOf(
                    t("نوع القطاع", "Section Type") to stored.section.displayName,
                    t("نوع العنصر", "Member Type") to when (stored.memberType) {
                        com.civileg.app.domain.entities.SteelMemberType.COLUMN -> t("عمود", "Column")
                        com.civileg.app.domain.entities.SteelMemberType.BEAM -> t("كمر", "Beam")
                        com.civileg.app.domain.entities.SteelMemberType.BRACING -> t("ربط", "Bracing")
                        com.civileg.app.domain.entities.SteelMemberType.TRUSS_MEMBER -> t("عنصر كمرة", "Truss")
                        com.civileg.app.domain.entities.SteelMemberType.GIRDERS -> t("كمر رئيسي", "Girder")
                    },
                    t("كود التصميم", "Design Code") to codeName,
                    t("الطول", "Length") to "${stored.inputs.length} m",
                    t("الحمل المحوري", "Axial Load") to "${stored.inputs.axialLoad} kN",
                    t("العزم", "Moment") to "${stored.inputs.moment} kN.m",
                    t("قوة القص", "Shear Force") to "${stored.inputs.shear} kN"
                )
                val resultsMap = mapOf(
                    t("السعة المحورية", "Axial Capacity") to "${String.format("%.2f", res.axialCapacity)} kN",
                    t("سعة العزم", "Moment Capacity") to "${String.format("%.2f", res.flexuralCapacity)} kN.m",
                    t("سعة القص", "Shear Capacity") to "${String.format("%.2f", res.shearCapacity)} kN",
                    t("نسبة الاستغلال", "Utilization") to "${(res.utilizationRatio * 100).toInt()}%",
                    t("الحالة", "Status") to if (res.isSafe) t("آمن", "SAFE") else t("غير آمن", "UNSAFE")
                )
                val memberTypeLabel = when (stored.memberType) {
                    com.civileg.app.domain.entities.SteelMemberType.COLUMN -> t("عمود", "Column")
                    com.civileg.app.domain.entities.SteelMemberType.BEAM -> t("كمر", "Beam")
                    com.civileg.app.domain.entities.SteelMemberType.BRACING -> t("ربط", "Bracing")
                    com.civileg.app.domain.entities.SteelMemberType.TRUSS_MEMBER -> t("عنصر كمرة", "Truss")
                    com.civileg.app.domain.entities.SteelMemberType.GIRDERS -> t("كمر رئيسي", "Girder")
                }
                val safetyChecks = mutableListOf<com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck>()
                if (stored.inputs.axialLoad > 0) {
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = t("السعة المحورية", "Axial Capacity"),
                        calculated = stored.inputs.axialLoad, limit = res.axialCapacity, unit = "kN",
                        passed = stored.inputs.axialLoad <= res.axialCapacity
                    ))
                }
                if (stored.inputs.moment > 0) {
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = t("سعة العزم", "Flexural Capacity"),
                        calculated = stored.inputs.moment, limit = res.flexuralCapacity, unit = "kN.m",
                        passed = stored.inputs.moment <= res.flexuralCapacity
                    ))
                }
                if (stored.inputs.shear > 0) {
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = t("سعة القص", "Shear Capacity"),
                        calculated = stored.inputs.shear, limit = res.shearCapacity, unit = "kN",
                        passed = stored.inputs.shear <= res.shearCapacity
                    ))
                }
                res.bucklingCheck?.let { buckling ->
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = t("الانبعاج", "Buckling Check"),
                        calculated = buckling.slendernessRatio, limit = 200.0, unit = "-",
                        passed = buckling.isSafe
                    ))
                }
                res.deflectionCheck?.let { defl ->
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = t("الترخيم", "Deflection Check"),
                        calculated = defl.calculatedDeflection, limit = defl.allowableDeflection, unit = "mm",
                        passed = defl.isSafe
                    ))
                }

                val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم قطاع معدني - ${stored.section.displayName}",
                    titleEn = "Steel Member Design Report — ${stored.section.displayName}",
                    subtitle = "${t("الكود", "Code")}: $codeName  •  $memberTypeLabel",
                    designType = "Steel — ${stored.section.displayName}",
                    inputs = inputsMap, results = resultsMap, safetyChecks = safetyChecks,
                    isSafe = res.isSafe, drawingBitmap = drawingBitmap, outputPath = file.absolutePath
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (generated != null) {
                        com.civileg.app.utils.ExportUtils.openPdf(context, generated)
                    }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _errorMessage.value = "Steel PDF export failed: ${e.message ?: "Unknown error"}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    fun resetResult() {
        _result.value = null
        _warehouseResult.value = null
        _errorMessage.value = null
    }

    fun calculateWeldCapacity(size: Double, length: Double, electrode: ElectrodeType, code: CalculatorEngine.DesignCode): Double {
        return calculatorEngine.calculateWeldCapacity(size, length, electrode, code)
    }

    fun calculateBoltCapacity(diameter: Double, grade: BoltGrade, count: Int, code: CalculatorEngine.DesignCode): Double {
        return calculatorEngine.calculateBoltCapacity(diameter, grade, count, code)
    }

    fun exportWeldToPdf(context: android.content.Context, size: Double, length: Double, electrode: ElectrodeType, code: CalculatorEngine.DesignCode, capacity: Double) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = "Weld_Report_${System.currentTimeMillis()}.pdf"
                val file = java.io.File(context.cacheDir, fileName)
                val inputs = mapOf(
                    "Design Type" to "Weld Design", "Code" to code.name,
                    "Weld Size" to "${size} mm", "Weld Length" to "${length} mm",
                    "Electrode Type" to electrode.name, "Weld Capacity" to "${"%.1f".format(capacity)} kN"
                )
                val results = mapOf(
                    "Capacity" to "${"%.1f".format(capacity)} kN",
                    "Status" to if (capacity > 0) "PASS" else "CHECK REQUIRED"
                )
                val exportedFile = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "Weld Design Report", titleEn = "Weld Design Report",
                    subtitle = code.name, designType = "STEEL", inputs = inputs,
                    results = results, safetyChecks = emptyList(), isSafe = capacity > 0,
                    drawingBitmap = null, outputPath = file.absolutePath
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    exportedFile?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun exportBoltToPdf(context: android.content.Context, dia: Double, grade: BoltGrade, count: Int, code: CalculatorEngine.DesignCode, capacity: Double) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = "Bolt_Report_${System.currentTimeMillis()}.pdf"
                val file = java.io.File(context.cacheDir, fileName)
                val inputs = mapOf(
                    "Design Type" to "Bolt Design", "Code" to code.name,
                    "Bolt Diameter" to "${dia} mm", "Bolt Grade" to grade.name,
                    "Bolt Count" to "$count", "Total Capacity" to "${"%.1f".format(capacity)} kN"
                )
                val results = mapOf(
                    "Total Capacity" to "${"%.1f".format(capacity)} kN",
                    "Per Bolt Capacity" to "${"%.1f".format(capacity / maxOf(1, count))} kN",
                    "Status" to if (capacity > 0) "PASS" else "CHECK REQUIRED"
                )
                val exportedFile = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "Bolt Design Report", titleEn = "Bolt Design Report",
                    subtitle = code.name, designType = "STEEL", inputs = inputs,
                    results = results, safetyChecks = emptyList(), isSafe = capacity > 0,
                    drawingBitmap = null, outputPath = file.absolutePath
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    exportedFile?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
