package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculationValidator
import com.civileg.app.utils.CalculatorEngine
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Bitmap
import javax.inject.Inject

@HiltViewModel
class TankViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.TankResult?>()
    val result: LiveData<CalculatorEngine.TankResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _validationReport = MutableLiveData<CalculationValidator.ValidationReport?>()
    val validationReport: LiveData<CalculationValidator.ValidationReport?> = _validationReport

    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    fun calculateTankPro(
        type: CalculatorEngine.TankType,
        capacity: Double,
        height: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designTank(type, capacity, height, fcu, fy, preferredDiameter, code)
                val report = CalculationValidator.validateTank(res)
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

    fun calculateTank(type: CalculatorEngine.TankType, capacity: Double, height: Double, fcu: Double, fy: Double) {
        calculateTankPro(type, capacity, height, fcu, fy, 12, CalculatorEngine.DesignCode.EGYPTIAN)
    }

    fun saveTank(projectId: Long, name: String, result: CalculatorEngine.TankResult) {
        viewModelScope.launch {
            repository.saveTankDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // [EXPERT]: Modern specialized tank exporter integration
                val exporter = com.civileg.app.utils.exporters.TankProPdfExporter(context)
                
                // Map local TankResult back to domain TankResult for the exporter if necessary
                // Actually, the exporter takes a domain TankResult.
                // Let's create a dummy or fix the exporter to take the engine result.
                // Re-calculating to get the full domain object is one way, or just mapping.
                
                val domainType = when(res.type) {
                    CalculatorEngine.TankType.RECTANGULAR_GROUND -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_GROUND
                    CalculatorEngine.TankType.CIRCULAR_GROUND -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_GROUND
                    CalculatorEngine.TankType.RECTANGULAR_ELEVATED -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_ELEVATED
                    CalculatorEngine.TankType.CIRCULAR_ELEVATED -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_ELEVATED
                    CalculatorEngine.TankType.UNDERGROUND -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_UNDERGROUND
                    CalculatorEngine.TankType.CIRCULAR_UNDERGROUND -> com.civileg.app.domain.calculations.base.TankType.CIRCULAR_UNDERGROUND
                    else -> com.civileg.app.domain.calculations.base.TankType.RECTANGULAR
                }

                val designer: com.civileg.app.domain.calculations.base.TankDesign = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> com.civileg.app.domain.calculations.aci.ACITank()
                    CalculatorEngine.DesignCode.SAUDI -> com.civileg.app.domain.calculations.sbc.SBCTank()
                    else -> com.civileg.app.domain.calculations.ecp.ECPTank()
                }

                val domainRes = designer.calculateTank(
                    length = res.length * 1000.0, width = res.width * 1000.0, height = res.height * 1000.0,
                    waterDepth = res.height * 0.9 * 1000.0, fcu = res.fcu, fy = res.fy, type = domainType
                )

                val drawings = mutableMapOf<String, Bitmap?>()
                drawings["elevation"] = pendingDrawingBitmap
                
                val inputs = CalculatorEngine.TankInputs(res.type, res.capacity, res.height, res.fcu, res.fy, res.code)

                val generated = exporter.exportToPdf(
                    result = domainRes,
                    tankInputs = inputs,
                    clientName = "Master Engineering Client",
                    projectName = "Professional Tank Analysis Project",
                    drawings = drawings
                )
                pendingDrawingBitmap = null 

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    generated.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _error.value = "PDF Export Error: ${e.message}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
