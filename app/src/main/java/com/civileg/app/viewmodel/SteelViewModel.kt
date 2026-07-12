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
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
                val fileName = "Steel_Report_${System.currentTimeMillis()}.pdf"
                val file = java.io.File(context.cacheDir, fileName)
                val exporter = com.civileg.app.utils.exporters.ComprehensivePdfExporter(context)

                // Generate steel drawing bitmap using actual section properties
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

                // Map to domain DesignCode
                val domainCode = when (stored.code) {
                    CalculatorEngine.DesignCode.ACI -> DesignCode.ACI
                    CalculatorEngine.DesignCode.SAUDI -> DesignCode.SBC
                    else -> DesignCode.ECP
                }

                val exportedFile = exporter.run {
                    setLanguage(settingsManager.language)
                    exportSteelReport(
                        projectName = "تقرير تصميم قطاع معدني - ${stored.section.displayName}",
                        designCode = domainCode,
                        sectionType = stored.section,
                        memberType = stored.memberType,
                        inputs = stored.inputs,
                        result = res,
                        connectionDesign = res.connectionDesign,
                        outputPath = file.absolutePath,
                        drawingBitmap = drawingBitmap
                    )
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (exportedFile != null) {
                        com.civileg.app.utils.ExportUtils.openPdf(context, exportedFile)
                    }
                    onComplete(exportedFile)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
}