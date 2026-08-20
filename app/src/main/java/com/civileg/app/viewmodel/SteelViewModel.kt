package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.CalculationValidator
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
        code: CalculatorEngine.DesignCode
    ) {
        // Store actual inputs for PDF export
        lastMemberInputs = SteelMemberStoredInputs(section, memberType, inputs, code)

        viewModelScope.launch {
            _isLoading.value = true
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

    fun exportWarehouseProToPdf(
        context: android.content.Context,
        clientAr: String, clientEn: String, projAr: String, projEn: String,
        onComplete: (java.io.File?) -> Unit
    ) {
        val res = _warehouseResult.value ?: return
        val inputs = lastWarehouseInputs ?: return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                val exporter = com.civileg.app.utils.exporters.SteelWarehouseProPdfExporter(context)
                val file = exporter.exportToDownload(inputs, res, clientAr, clientEn, projAr, projEn)

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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // CRITICAL FIX (2026-07-27 v2): Use NativePdfExporter with StaticLayout
                // for proper Arabic BIDI + HarfBuzz shaping.
                val fileName = "Steel_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, fileName)

                // Generate steel drawing bitmap using actual section properties
                // 2026-08-04 v3: Pass all section properties to match ProfessionalSteelDrawing
                val drawingBitmap = try {
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
                        // Connection parameters — use configurable defaults (set from connection design if available)
                        boltDia = defaultBoltDia,
                        boltCount = defaultBoltCount,
                        boltGauge = defaultBoltGauge,
                        boltPitch = defaultBoltPitch,
                        endPlateThickness = defaultEndPlateThickness,
                        hasStiffener = defaultHasStiffener,
                        weldSize = defaultWeldSize,
                        isColumn = stored.memberType == com.civileg.app.domain.entities.SteelMemberType.COLUMN
                    )
                } catch (e: Exception) { e.printStackTrace(); null }

                // Language fix (2026-08-04): ProfessionalEnglishPdfReporter uses Helvetica (English-only).
                // Always pass English keys — Arabic keys would appear garbled.
                val codeName = when (stored.code) {
                    CalculatorEngine.DesignCode.ACI -> "AISC 360-16"
                    CalculatorEngine.DesignCode.SAUDI -> "SBC 306"
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
                val safetyChecks = mutableListOf<com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck>()
                if (stored.inputs.axialLoad > 0) {
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = "Axial Capacity",
                        calculated = stored.inputs.axialLoad,
                        limit = res.axialCapacity,
                        unit = "kN",
                        passed = stored.inputs.axialLoad <= res.axialCapacity
                    ))
                }
                if (stored.inputs.moment > 0) {
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = "Flexural Capacity",
                        calculated = stored.inputs.moment,
                        limit = res.flexuralCapacity,
                        unit = "kN.m",
                        passed = stored.inputs.moment <= res.flexuralCapacity
                    ))
                }
                if (stored.inputs.shear > 0) {
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = "Shear Capacity",
                        calculated = stored.inputs.shear,
                        limit = res.shearCapacity,
                        unit = "kN",
                        passed = stored.inputs.shear <= res.shearCapacity
                    ))
                }
                res.bucklingCheck?.let { buckling ->
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = "Buckling Check",
                        calculated = buckling.slendernessRatio,
                        limit = 200.0,
                        unit = "-",
                        passed = buckling.isSafe
                    ))
                }
                res.deflectionCheck?.let { defl ->
                    safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = "Deflection Check",
                        calculated = defl.calculatedDeflection,
                        limit = defl.allowableDeflection,
                        unit = "mm",
                        passed = defl.isSafe
                    ))
                }

                val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم قطاع معدني - ${stored.section.displayName}",
                    titleEn = "Steel Member Design Report — ${stored.section.displayName}",
                    subtitle = "Code: $codeName  •  $memberTypeLabel",
                    designType = "Steel — ${stored.section.displayName}",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath
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
                    "Design Type" to "Weld Design",
                    "Code" to code.name,
                    "Weld Size" to "${size} mm",
                    "Weld Length" to "${length} mm",
                    "Electrode Type" to electrode.name,
                    "Weld Capacity" to "${"%.1f".format(capacity)} kN"
                )
                val results = mapOf(
                    "Capacity" to "${"%.1f".format(capacity)} kN",
                    "Status" to if (capacity > 0) "PASS" else "CHECK REQUIRED"
                )
                val exportedFile = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "Weld Design Report",
                    titleEn = "Weld Design Report",
                    subtitle = code.name,
                    designType = "STEEL",
                    inputs = inputs,
                    results = results,
                    safetyChecks = emptyList(),
                    isSafe = capacity > 0,
                    drawingBitmap = null,
                    outputPath = file.absolutePath
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
                    "Design Type" to "Bolt Design",
                    "Code" to code.name,
                    "Bolt Diameter" to "${dia} mm",
                    "Bolt Grade" to grade.name,
                    "Bolt Count" to "$count",
                    "Total Capacity" to "${"%.1f".format(capacity)} kN"
                )
                val results = mapOf(
                    "Total Capacity" to "${"%.1f".format(capacity)} kN",
                    "Per Bolt Capacity" to "${"%.1f".format(capacity / maxOf(1, count))} kN",
                    "Status" to if (capacity > 0) "PASS" else "CHECK REQUIRED"
                )
                val exportedFile = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "Bolt Design Report",
                    titleEn = "Bolt Design Report",
                    subtitle = code.name,
                    designType = "STEEL",
                    inputs = inputs,
                    results = results,
                    safetyChecks = emptyList(),
                    isSafe = capacity > 0,
                    drawingBitmap = null,
                    outputPath = file.absolutePath
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    exportedFile?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}