package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.R
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.CalculationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Bitmap
import javax.inject.Inject

@HiltViewModel
class FootingViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.FootingResult?>()
    val result: LiveData<CalculatorEngine.FootingResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _validationReport = MutableLiveData<CalculationValidator.ValidationReport?>()
    val validationReport: LiveData<CalculationValidator.ValidationReport?> = _validationReport

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    // Store actual inputs for save
    private var lastFcu: Double = 25.0
    private var lastFy: Double = 360.0

    fun calculateFooting(
        type: CalculatorEngine.FootingType,
        p: Double,
        fcu: Double,
        fy: Double,
        soil: Double,
        colB: Double,
        colT: Double,
        code: CalculatorEngine.DesignCode,
        preferredDiameter: Int,
        preferredSpacing: Double,
        p2: Double = 0.0,
        distance: Double = 0.0,
        maxLeft: Double? = null,
        maxRight: Double? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                lastFcu = fcu
                lastFy = fy

                val res = calculatorEngine.calculateFooting(
                    type = type,
                    p = p,
                    fcu = fcu,
                    fy = fy,
                    soil = soil,
                    colB = colB,
                    colT = colT,
                    code = code,
                    preferredDiameter = preferredDiameter,
                    preferredSpacing = preferredSpacing,
                    p2 = p2,
                    distance = distance,
                    maxLeft = maxLeft,
                    maxRight = maxRight
                )
                
                // Validate Footing
                val report = CalculationValidator.validate(res)
                val dlReport = CalculationValidator.inspectDeadLoadConsistency("FOOTING", mapOf("width" to colB, "depth" to colT), p)
                
                val combinedWarnings = report.warnings + dlReport.warnings
                _validationReport.value = report.copy(warnings = combinedWarnings)
                
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveFooting(projectId: Long, name: String, result: CalculatorEngine.FootingResult) {
        viewModelScope.launch {
            repository.saveFootingDesign(projectId, name, result, lastFcu, lastFy)
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                // [EXPERT]: Modern specialized Footing exporter
                val exporter = com.civileg.app.utils.exporters.FootingProPdfExporter(context)
                
                val designer: com.civileg.app.domain.calculations.base.FootingDesign = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> com.civileg.app.domain.calculations.aci.ACIFooting()
                    CalculatorEngine.DesignCode.SAUDI -> com.civileg.app.domain.calculations.sbc.SBCFooting()
                    else -> com.civileg.app.domain.calculations.ecp.ECPFooting()
                }

                // Re-calculate to get full professional metadata (formulas, etc)
                val fullDomainResult = designer.designIsolatedFooting(
                    fcu = lastFcu, fy = lastFy,
                    columnWidth = res.column1Size.second, columnDepth = res.column1Size.first,
                    axialLoad = res.soilPressure * res.width * res.length / 1e6 * 1.5, // Approx ultimate
                    momentX = 0.0, momentY = 0.0,
                    soilBearingCapacity = res.allowablePressure,
                    footingDepth = res.thickness,
                    loadCombination = com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE
                )

                val drawings = mutableMapOf<String, Bitmap?>()
                drawings["section"] = pendingDrawingBitmap

                val generated = exporter.exportToPdf(
                    result = fullDomainResult,
                    clientName = "Master Engineering Client",
                    projectName = "Professional Foundation Analysis",
                    drawings = drawings
                )
                pendingDrawingBitmap = null 

                withContext(Dispatchers.Main) {
                    generated.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message ?: ""}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
