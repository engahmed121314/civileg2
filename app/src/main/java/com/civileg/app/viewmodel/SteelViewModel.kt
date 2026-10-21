package com.civileg.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.CalculationValidator
import com.civileg.app.utils.ExportUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
        val code: DesignCode
    )

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    var pendingDrawingBitmap: Bitmap? = null

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _validationReport = MutableLiveData<CalculationValidator.ValidationReport?>()
    val validationReport: LiveData<CalculationValidator.ValidationReport?> = _validationReport

    val sectionLibrary: Map<String, List<SteelSectionType>> = calculatorEngine.getSteelSectionLibrary()

    // Configurable connection design defaults (mm). Override via constructor or
    // update from actual connection design results before PDF export.
    var defaultBoltDia: Double = 20.0
    var defaultBoltCount: Int = 4
    var defaultBoltGauge: Double = 90.0
    var defaultBoltPitch: Double = 75.0
    var defaultEndPlateThickness: Double = 12.0
    var defaultWeldSize: Double = 6.0
    var defaultHasStiffener: Boolean = false

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
        code: DesignCode
    ) {
        InputGuard.notNull("section", section)
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("inputs.unbracedLength", inputs.unbracedLength)
        InputGuard.positive("inputs.length", inputs.length)

        // Store actual inputs for PDF export
        lastMemberInputs = SteelMemberStoredInputs(section, memberType, inputs, code)

        viewModelScope.launch {
            _isLoading.value = true
            pendingDrawingBitmap = null  // Reset stale bitmap from previous calculation
            _errorMessage.value = null
            try {
                val res = calculatorEngine.calculateSteelMember(section, memberType, inputs, code)
                
                // Validate Steel Member
                val report = CalculationValidator.validate(res)
                _validationReport.value = report
                
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
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("inputs.span", inputs.span)
        InputGuard.positive("inputs.length", inputs.length)
        InputGuard.positive("inputs.eaveHeight", inputs.eaveHeight)
        InputGuard.positive("inputs.ridgeHeight", inputs.ridgeHeight)
        InputGuard.positive("inputs.baySpacing", inputs.baySpacing)

        lastWarehouseInputs = inputs
        viewModelScope.launch {
            _isLoading.value = true
            pendingDrawingBitmap = null  // Reset stale bitmap from previous calculation
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

    fun exportWarehouseProToPdf(
        context: android.content.Context,
        clientName: String, projectName: String,
        onComplete: (java.io.File?) -> Unit
    ) {
        val res = _warehouseResult.value ?: return
        val inputs = lastWarehouseInputs ?: return
        // Consume the on-screen drawing captured by SteelDesignScreen just before this call,
        // so a later export can never show a stale view.
        val capturedDrawing = pendingDrawingBitmap
        pendingDrawingBitmap = null

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                val exporter = com.civileg.app.utils.exporters.SteelWarehouseProPdfExporter(context)
                val file = exporter.exportToDownload(inputs, res, clientName, projectName, capturedDrawing)

                // DXF companion with a compact result summary on the drawing sheet.
                try {
                    com.civileg.app.utils.DxfExporter.writeSibling(
                        file,
                        com.civileg.app.utils.ElementDxfDrawings.generic(
                            title = "STEEL WAREHOUSE ${inputs.span.toInt()}x${inputs.length.toInt()} m",
                            widthMm = 600.0,
                            heightMm = 400.0,
                            summaryLines = listOf(
                                "Span = ${inputs.span} m  Length = ${inputs.length} m",
                                "Eave height = ${inputs.eaveHeight} m  Bay spacing = ${inputs.baySpacing} m",
                                "Column: ${res.mainFrame.columnSection}",
                                "Rafter: ${res.mainFrame.rafterSection}  Purlin: ${res.secondaryMembers.purlinSection}",
                                "Steel weight = ${"%.1f".format(res.totalWeight)} t  (${"%.1f".format(res.weightPerM2)} kg/m2)"
                            )
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    com.civileg.app.utils.ExportUtils.openPdf(context, file)
                    onComplete(file)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _errorMessage.value = "Warehouse PDF export failed: ${e.message ?: "Unknown error"}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
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
        // Consume the on-screen drawing captured by SteelSectionTab just before this call,
        // so a later export can never show a stale view.
        val capturedDrawing = pendingDrawingBitmap
        pendingDrawingBitmap = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // PDF + DXF via ExportManager.exportReportAndDxf (iText, EN-only per ADR-005/009).
                // The old NativePdfExporter (android.graphics.pdf + Canvas) was removed:
                // it can SIGSEGV inside Skia with no catchable exception.
                val fileName = "Steel_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, fileName)

                // Connection parameters must match the on-screen drawing: read the actual
                // bolt diameter/count and weld size from the connection design, deriving
                // gauge/pitch from the bolt diameter (AISC 360 J3.3), falling back to defaults.
                val connParams = com.civileg.app.utils.SteelConnectionDrawing.from(
                    connectionDesign = res.connectionDesign,
                    defaultBoltDia = defaultBoltDia,
                    defaultBoltCount = defaultBoltCount,
                    defaultWeldSize = defaultWeldSize
                )

                // Prefer the captured on-screen drawing (ProfessionalSteelDrawing) so the PDF
                // shows exactly what the user is looking at; fall back to the independent
                // renderer, which reads the actual section/connection properties.
                val drawingBitmap = capturedDrawing ?: try {
                    PdfDrawingGenerator.generateSteelDrawing(
                        sectionName = stored.section.sectionName,
                        sectionHeight = stored.section.depth,
                        flangeWidth = stored.section.width,
                        webThickness = stored.section.webThickness,
                        flangeThickness = stored.section.flangeThickness,
                        memberLength = stored.inputs.length,
                        isSafe = res.isSafe,
                        utilizationRatio = res.utilizationRatio * 100,
                        // New parameters matching on-screen ProfessionalSteelDrawing
                        sectionType = stored.section.displayName,
                        radius = stored.section.rootRadius,
                        area = stored.section.area,
                        ix = stored.section.ix,
                        sx = stored.section.sx,
                        zx = stored.section.zx,
                        weightPerMeter = stored.section.weight,
                        // Connection parameters — from actual connection design (matches on-screen drawing)
                        boltDia = connParams.boltDia,
                        boltCount = connParams.boltCount,
                        boltGauge = connParams.boltGauge,
                        boltPitch = connParams.boltPitch,
                        endPlateThickness = defaultEndPlateThickness,
                        hasStiffener = defaultHasStiffener,
                        weldSize = connParams.weldSize,
                        isColumn = stored.memberType == com.civileg.app.domain.entities.SteelMemberType.COLUMN
                    )
                } catch (e: Exception) { e.printStackTrace(); null }

                // Language fix (2026-08-04): ProfessionalEnglishPdfReporter uses Helvetica (English-only).
                // Always pass English keys — Arabic keys would appear garbled.
                val codeName = when (stored.code) {
                    DesignCode.ACI -> "AISC 360-16"
                    DesignCode.SBC -> "SBC 306"
                    else -> "ECP 205-2007"
                }

                val inputsMap = mapOf(
                    "Section Type" to stored.section.displayName,
                    "Member Type" to when (stored.memberType) {
                        com.civileg.app.domain.entities.SteelMemberType.COLUMN -> "Column"
                        com.civileg.app.domain.entities.SteelMemberType.BEAM -> "Beam"
                        com.civileg.app.domain.entities.SteelMemberType.BRACING -> "Bracing"
                        com.civileg.app.domain.entities.SteelMemberType.TRUSS_MEMBER -> "Truss"
                        com.civileg.app.domain.entities.SteelMemberType.GIRDERS -> "Girder"
                    },
                    "Design Code" to codeName,
                    "Length" to "${stored.inputs.length / 1000.0} m",
                    "Axial Load" to "${stored.inputs.axialLoad} kN",
                    "Moment" to "${stored.inputs.moment} kN.m",
                    "Shear Force" to "${stored.inputs.shear} kN"
                )
                val resultsMap = mapOf(
                    "Axial Capacity" to "${String.format("%.2f", res.axialCapacity)} kN",
                    "Moment Capacity" to "${String.format("%.2f", res.flexuralCapacity)} kN.m",
                    "Shear Capacity" to "${String.format("%.2f", res.shearCapacity)} kN",
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%",
                    "Status" to if (res.isSafe) "SAFE" else "UNSAFE"
                )
                val memberTypeLabel = when (stored.memberType) {
                    com.civileg.app.domain.entities.SteelMemberType.COLUMN -> "Column"
                    com.civileg.app.domain.entities.SteelMemberType.BEAM -> "Beam"
                    com.civileg.app.domain.entities.SteelMemberType.BRACING -> "Bracing"
                    com.civileg.app.domain.entities.SteelMemberType.TRUSS_MEMBER -> "Truss"
                    com.civileg.app.domain.entities.SteelMemberType.GIRDERS -> "Girder"
                }
                val safetyChecks = mutableListOf<GenericSafetyCheck>()
                if (stored.inputs.axialLoad > 0) {
                    safetyChecks.add(
                        GenericSafetyCheck(
                            name = "Axial Capacity",
                            calculated = stored.inputs.axialLoad,
                            limit = res.axialCapacity,
                            unit = "kN",
                            passed = stored.inputs.axialLoad <= res.axialCapacity
                        )
                    )
                }
                if (stored.inputs.moment > 0) {
                    safetyChecks.add(
                        GenericSafetyCheck(
                            name = "Flexural Capacity",
                            calculated = stored.inputs.moment,
                            limit = res.flexuralCapacity,
                            unit = "kN.m",
                            passed = stored.inputs.moment <= res.flexuralCapacity
                        )
                    )
                }
                if (stored.inputs.shear > 0) {
                    safetyChecks.add(com.civileg.app.domain.entities.GenericSafetyCheck(
                        name = "Shear Capacity",
                        calculated = stored.inputs.shear,
                        limit = res.shearCapacity,
                        unit = "kN",
                        passed = stored.inputs.shear <= res.shearCapacity
                    ))
                }
                res.bucklingCheck?.let { buckling ->
                    safetyChecks.add(com.civileg.app.domain.entities.GenericSafetyCheck(
                        name = "Buckling Check",
                        calculated = buckling.slendernessRatio,
                        limit = 200.0,
                        unit = "-",
                        passed = buckling.isSafe
                    ))
                }
                res.deflectionCheck?.let { defl ->
                    safetyChecks.add(com.civileg.app.domain.entities.GenericSafetyCheck(
                        name = "Deflection Check",
                        calculated = defl.calculatedDeflection,
                        limit = defl.allowableDeflection,
                        unit = "mm",
                        passed = defl.isSafe
                    ))
                }

                val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم قطاع معدني - ${stored.section.displayName}",
                    titleEn = "Steel Member Design Report — ${stored.section.displayName}",
                    subtitle = "Code: $codeName  •  $memberTypeLabel",
                    designType = "Steel — ${stored.section.displayName}",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    drawing = com.civileg.app.utils.ElementDxfDrawings.steelISection(
                        title = "${stored.section.sectionName} - $memberTypeLabel",
                        heightMm = stored.section.depth,
                        widthMm = stored.section.width,
                        webThicknessMm = stored.section.webThickness,
                        flangeThicknessMm = stored.section.flangeThickness,
                        summaryLines = listOf(
                            "Pu = ${String.format("%.1f", stored.inputs.axialLoad)} kN / ${String.format("%.1f", res.axialCapacity)} kN",
                            "Mu = ${String.format("%.1f", stored.inputs.moment)} kN.m / ${String.format("%.1f", res.flexuralCapacity)} kN.m",
                            "Vu = ${String.format("%.1f", stored.inputs.shear)} kN / ${String.format("%.1f", res.shearCapacity)} kN",
                            "Utilization = ${(res.utilizationRatio * 100).toInt()}% - ${if (res.isSafe) "SAFE" else "UNSAFE"}"
                        )
                    ),
                    outputPath = file.absolutePath
                ).pdfFile

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

    fun calculateWeldCapacity(size: Double, length: Double, electrode: ElectrodeType, code: DesignCode): Double {
        return calculatorEngine.calculateWeldCapacity(size, length, electrode, code)
    }

    fun calculateBoltCapacity(diameter: Double, grade: BoltGrade, count: Int, code: DesignCode): Double {
        return calculatorEngine.calculateBoltCapacity(diameter, grade, count, code)
    }

    fun exportWeldToPdf(context: Context, size: Double, length: Double, electrode: ElectrodeType, code: DesignCode, capacity: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "Weld_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val inputsMap = mapOf(
                    "Weld Size" to "${size} mm",
                    "Weld Length" to "${length} mm",
                    "Electrode" to electrode.displayName,
                    "Design Code" to code.name
                )
                val resultsMap = mapOf(
                    "Total Capacity" to "${String.format("%.1f", capacity)} kN",
                    "Status" to if (capacity > 0) "SAFE" else "UNSAFE"
                )
                val safetyChecks = listOf(
                    GenericSafetyCheck("Weld Capacity", 0.0, capacity, "kN", capacity > 0)
                )

                val exported = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم وصلة ملحومة",
                    titleEn = "Welded Connection Design Report",
                    subtitle = "Code: ${code.name}  •  Electrode: ${electrode.displayName}",
                    designType = "Welded Connection",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = capacity > 0,
                    drawingBitmap = null,
                    drawing = com.civileg.app.utils.ElementDxfDrawings.generic(
                        title = "WELDED CONNECTION",
                        widthMm = 500.0,
                        heightMm = 300.0,
                        summaryLines = listOf(
                            "Weld size = $size mm  Length = $length mm",
                            "Electrode: ${electrode.displayName}",
                            "Capacity = ${String.format("%.1f", capacity)} kN",
                            "Code: ${code.name}"
                        )
                    ),
                    outputPath = file.absolutePath
                ).pdfFile

                withContext(Dispatchers.Main) {
                    exported?.let { ExportUtils.openPdf(context, it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportBoltToPdf(context: Context, dia: Double, grade: BoltGrade, count: Int, code: DesignCode, capacity: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "Bolt_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val inputsMap = mapOf(
                    "Bolt Diameter" to "${dia} mm",
                    "Bolt Grade" to grade.displayName,
                    "Number of Bolts" to "$count",
                    "Design Code" to code.name
                )
                val resultsMap = mapOf(
                    "Total Capacity" to "${String.format("%.1f", capacity)} kN",
                    "Status" to if (capacity > 0) "SAFE" else "UNSAFE"
                )
                val safetyChecks = listOf(
                    GenericSafetyCheck("Bolt Capacity", 0.0, capacity, "kN", capacity > 0)
                )

                val exported = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم وصلة مسمارية",
                    titleEn = "Bolted Connection Design Report",
                    subtitle = "Code: ${code.name}  •  Grade: ${grade.displayName}",
                    designType = "Bolted Connection",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = capacity > 0,
                    drawingBitmap = null,
                    drawing = com.civileg.app.utils.ElementDxfDrawings.generic(
                        title = "BOLTED CONNECTION",
                        widthMm = 500.0,
                        heightMm = 300.0,
                        summaryLines = listOf(
                            "Bolt M$dia x $count  Grade: ${grade.displayName}",
                            "Capacity = ${String.format("%.1f", capacity)} kN",
                            "Code: ${code.name}"
                        )
                    ),
                    outputPath = file.absolutePath
                ).pdfFile

                withContext(Dispatchers.Main) {
                    exported?.let { ExportUtils.openPdf(context, it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportBasePlateToPdf(
        context: Context,
        result: SteelBasePlateDesign.BasePlateResult,
        colSection: String,
        bf: Double,
        dc: Double,
        onComplete: (File?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "BasePlate_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val drawing = PdfDrawingGenerator.generateBasePlateDrawing(
                    plateWidth = result.plateWidth,
                    plateLength = result.plateLength,
                    plateThickness = result.plateThickness,
                    colBf = bf,
                    colDepth = dc,
                    boltDiameter = result.anchorBolts.boltDiameter,
                    boltCount = result.anchorBolts.numberOfBolts,
                    isSafe = result.isSafe
                )
                
                val inputsMap = mapOf(
                    "Column Section" to colSection,
                    "Plate Width" to "${result.plateWidth} mm",
                    "Plate Length" to "${result.plateLength} mm",
                    "Plate Thickness" to "${result.plateThickness} mm"
                )
                val resultsMap = mapOf(
                    "Plate Width" to "${String.format("%.1f", result.plateWidth)} mm",
                    "Plate Length" to "${String.format("%.1f", result.plateLength)} mm",
                    "Plate Thickness" to "${String.format("%.1f", result.plateThickness)} mm",
                    "Anchor Bolt Dia" to "${result.anchorBolts.boltDiameter} mm",
                    "Number of Bolts" to "${result.anchorBolts.numberOfBolts}",
                    "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
                )
                val safetyChecks = listOf(
                    GenericSafetyCheck("Base Plate", 0.0, 1.0, "-", result.isSafe)
                )
                val exported = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم قاعدة معدنية",
                    titleEn = "Steel Base Plate Design Report",
                    subtitle = "Column: $colSection",
                    designType = "Base Plate",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = result.isSafe,
                    drawingBitmap = drawing,
                    drawing = com.civileg.app.utils.ElementDxfDrawings.basePlate(
                        title = "BASE PLATE - $colSection",
                        plateWidthMm = result.plateWidth,
                        plateLengthMm = result.plateLength,
                        plateThicknessMm = result.plateThickness,
                        boltDiaMm = result.anchorBolts.boltDiameter,
                        boltCount = result.anchorBolts.numberOfBolts,
                        columnWidthMm = bf,
                        columnDepthMm = dc
                    ),
                    outputPath = file.absolutePath
                ).pdfFile

                withContext(Dispatchers.Main) {
                    exported?.let { ExportUtils.openPdf(context, it) }
                    onComplete(exported)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(null) }
            }
        }
    }
}