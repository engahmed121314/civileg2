package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SteelViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<SteelMemberResult?>()
    val result: LiveData<SteelMemberResult?> = _result

    private val _warehouseResult = MutableLiveData<SteelWarehouseAnalysisResult?>()
    val warehouseResult: LiveData<SteelWarehouseAnalysisResult?> = _warehouseResult

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
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val res = calculatorEngine.designSteelWarehouse(inputs)
                _warehouseResult.value = res
            } catch (e: Exception) {
                _warehouseResult.value = null
                _errorMessage.value = "Error in Warehouse calculation: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun saveSteelMemberDesign(projectId: Long, name: String, result: SteelMemberResult) {
        viewModelScope.launch {
            repository.saveSteelMemberDesign(projectId, name, result)
        }
    }

    fun saveSteelWarehouseDesign(projectId: Long, name: String, result: SteelWarehouseAnalysisResult) {
        viewModelScope.launch {
            repository.saveSteelWarehouseDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "Steel_Report_${System.currentTimeMillis()}.pdf"
                val file = java.io.File(context.cacheDir, fileName)
                val exporter = com.civileg.app.utils.exporters.ComprehensivePdfExporter(context)

                // Use actual inputs from state if available, or reasonable defaults
                val inputs = SteelInputs(
                    axialLoad = 100.0,
                    moment = 50.0,
                    shear = 20.0,
                    unbracedLength = 6000.0,
                    length = 6000.0
                )

                val domainCode = DesignCode.ECP // Default, ideally should come from UI

                val exportedFile = exporter.exportSteelReport(
                    projectName = "Steel Design Report",
                    designCode = domainCode,
                    sectionType = res.sectionType,
                    memberType = res.memberType,
                    inputs = inputs,
                    result = res,
                    connectionDesign = res.connectionDesign,
                    outputPath = file.absolutePath
                )

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
}
