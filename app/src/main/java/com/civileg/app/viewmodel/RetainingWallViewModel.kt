package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculationValidator
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import javax.inject.Inject

@HiltViewModel
class RetainingWallViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.RetainingWallResult?>()
    val result: LiveData<CalculatorEngine.RetainingWallResult?> = _result

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

    fun calculateRetainingWallPro(
        height: Double,
        soilDensity: Double,
        frictionAngle: Double,
        surcharge: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designRetainingWall(
                    height = height,
                    soilDensity = soilDensity,
                    frictionAngle = frictionAngle,
                    surcharge = surcharge,
                    fcu = fcu,
                    fy = fy,
                    preferredDiameter = preferredDiameter,
                    code = code
                )
                
                // Validate Retaining Wall
                val report = CalculationValidator.validateRetainingWall(res)
                _validationReport.value = report
                
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Legacy method for backward compatibility
    fun calculateRetainingWall(height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double) {
        calculateRetainingWallPro(height, soilDensity, frictionAngle, surcharge, fcu, fy, 16, CalculatorEngine.DesignCode.EGYPTIAN)
    }

    fun saveRetainingWall(projectId: Long, name: String, result: CalculatorEngine.RetainingWallResult) {
        viewModelScope.launch {
            repository.saveRetainingWallDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val currentResult = _result.value ?: return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val exportedFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // [EXPERT]: Modern specialized Retaining Wall exporter
                    val exporter = com.civileg.app.utils.exporters.RetainingWallProPdfExporter(context)
                    
                    // Domain mapping
                    val domainType = com.civileg.app.domain.calculations.base.TankType.RECTANGULAR // Dummy for matching if needed
                    val designer: com.civileg.app.domain.calculations.base.RetainingWallDesign = when(currentResult.code) {
                        CalculatorEngine.DesignCode.ACI -> com.civileg.app.domain.calculations.aci.ACIRetainingWall()
                        CalculatorEngine.DesignCode.SAUDI -> com.civileg.app.domain.calculations.sbc.SBCRetainingWall()
                        else -> com.civileg.app.domain.calculations.ecp.ECPRetainingWall()
                    }

                    val input = com.civileg.app.domain.calculations.base.RetainingWallInput(
                        wallHeight = currentResult.height,
                        stemBaseThickness = currentResult.stemThickness / 1000.0,
                        stemTopThickness = currentResult.stemThickness / 1000.0 * 0.6,
                        baseWidth = currentResult.baseWidth / 1000.0,
                        baseThickness = currentResult.stemThickness / 1000.0 * 1.2,
                        toeLength = currentResult.baseWidth / 1000.0 * 0.3,
                        heelLength = currentResult.baseWidth / 1000.0 * 0.6,
                        soilDensity = currentResult.soilDensity,
                        frictionAngle = currentResult.backfillAngle,
                        surchargeLoad = currentResult.surcharge,
                        waterTableDepth = currentResult.waterTableHeight,
                        fcu = currentResult.fcu,
                        fy = currentResult.fy
                    )

                    val fullDomainResult = designer.designRetainingWall(input)

                    val drawings = mutableMapOf<String, Bitmap?>()
                    drawings["elevation"] = pendingDrawingBitmap

                    exporter.exportToPdf(
                        result = fullDomainResult,
                        clientName = "Master Engineering Client",
                        projectName = "Professional Cantilever Wall Analysis",
                        drawings = drawings
                    )
                }
                exportedFile?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                onComplete(exportedFile)
            } catch (e: Throwable) {
                e.printStackTrace()
                _error.value = "PDF export failed: ${e.message ?: "Unknown error"}"
                onComplete(null)
            } finally {
                _isExporting.value = false
            }
        }
    }
}
