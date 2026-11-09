package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.SoilBearingCalculator
import com.civileg.app.utils.SoilBearingInput
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.SoilType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoilBearingViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private val _result = MutableLiveData<SoilBearingResult?>()
    val result: LiveData<SoilBearingResult?> = _result

    private val _isCalculating = MutableLiveData(false)
    val isCalculating: LiveData<Boolean> = _isCalculating

    private val _comparisonResults = MutableLiveData<Map<BearingMethod, SoilBearingResult>>(emptyMap())
    val comparisonResults: LiveData<Map<BearingMethod, SoilBearingResult>> = _comparisonResults

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ------------------------------------------------------------------
    // Input fields (two-way via MutableLiveData)
    // ------------------------------------------------------------------

    val method = MutableLiveData(BearingMethod.TERZAGHI)
    val soilType = MutableLiveData(SoilType.CLAY)

    val foundationWidth = MutableLiveData("1.5")
    val foundationLength = MutableLiveData("1.5")
    val foundationDepth = MutableLiveData("1.0")

    val cohesion = MutableLiveData("25.0")
    val frictionAngle = MutableLiveData("30.0")
    val unitWeight = MutableLiveData("18.0")

    val waterTableDepth = MutableLiveData("5.0")

    val eccentricityX = MutableLiveData("0.0")
    val eccentricityY = MutableLiveData("0.0")

    val loadInclinationX = MutableLiveData("0.0")
    val loadInclinationY = MutableLiveData("0.0")

    val safetyFactor = MutableLiveData("3.0")

    // ------------------------------------------------------------------
    // Build input object from current LiveData values
    // ------------------------------------------------------------------

    private fun buildInput(): SoilBearingInput {
        // Rule 1.4: loud failures — validate before any maths (ADR-010)
        val width = InputGuard.positive("foundationWidth", foundationWidth.value?.toDoubleOrNull() ?: 1.5)
        val length = InputGuard.positive("foundationLength", foundationLength.value?.toDoubleOrNull() ?: 1.5)
        val depth = InputGuard.positive("foundationDepth", foundationDepth.value?.toDoubleOrNull() ?: 1.0)
        val load = InputGuard.nonNegative("cohesion", cohesion.value?.toDoubleOrNull() ?: 25.0) // cohesion can be 0 for sand
        InputGuard.positive("frictionAngle", frictionAngle.value?.toDoubleOrNull() ?: 30.0)
        InputGuard.inRange("frictionAngle", frictionAngle.value?.toDoubleOrNull() ?: 30.0, 0.0, 45.0)
        InputGuard.positive("unitWeight", unitWeight.value?.toDoubleOrNull() ?: 18.0)
        InputGuard.positive("safetyFactor", safetyFactor.value?.toDoubleOrNull() ?: 3.0)
        InputGuard.nonNegative("waterTableDepth", waterTableDepth.value?.toDoubleOrNull() ?: 5.0)
        InputGuard.nonNegative("eccentricityX", eccentricityX.value?.toDoubleOrNull() ?: 0.0)
        InputGuard.nonNegative("eccentricityY", eccentricityY.value?.toDoubleOrNull() ?: 0.0)
        InputGuard.nonNegative("loadInclinationX", loadInclinationX.value?.toDoubleOrNull() ?: 0.0)
        InputGuard.nonNegative("loadInclinationY", loadInclinationY.value?.toDoubleOrNull() ?: 0.0)
        InputGuard.positive("safetyFactor", safetyFactor.value?.toDoubleOrNull() ?: 3.0)

        return SoilBearingInput(
            method      = method.value ?: BearingMethod.TERZAGHI,
            soilType    = soilType.value ?: SoilType.CLAY,
            foundationWidth    = foundationWidth.value?.toDoubleOrNull() ?: 1.5,
            foundationLength   = foundationLength.value?.toDoubleOrNull() ?: 1.5,
            foundationDepth    = foundationDepth.value?.toDoubleOrNull() ?: 1.0,
            cohesion           = cohesion.value?.toDoubleOrNull() ?: 25.0,
            frictionAngle      = frictionAngle.value?.toDoubleOrNull() ?: 30.0,
            unitWeight         = unitWeight.value?.toDoubleOrNull() ?: 18.0,
            waterTableDepth    = waterTableDepth.value?.toDoubleOrNull() ?: 5.0,
            eccentricityX      = eccentricityX.value?.toDoubleOrNull() ?: 0.0,
            eccentricityY      = eccentricityY.value?.toDoubleOrNull() ?: 0.0,
            loadInclinationX   = loadInclinationX.value?.toDoubleOrNull() ?: 0.0,
            loadInclinationY   = loadInclinationY.value?.toDoubleOrNull() ?: 0.0,
            safetyFactor       = safetyFactor.value?.toDoubleOrNull() ?: 3.0
        )
    }

    // ------------------------------------------------------------------
    // Core calculation
    // ------------------------------------------------------------------

    /** Run the selected method and post the result. */
    fun calculate() {
        _error.value = null
        _isCalculating.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val calculator = SoilBearingCalculator()
                val input = buildInput()
                val res = when (input.method) {
                    BearingMethod.TERZAGHI -> calculator.calculateTerzaghi(input)
                    BearingMethod.MEYERHOF -> calculator.calculateMeyerhof(input)
                    BearingMethod.HANSEN   -> calculator.calculateHansen(input)
                    BearingMethod.VESIC    -> calculator.calculateVesic(input)
                }
                _result.postValue(res)
            } catch (e: IllegalArgumentException) {
                _result.postValue(null)
                _error.postValue("مدخلات غير صالحة: ${e.message}")
                _errorMessage.postValue("Invalid input / مدخلات غير صالحة: ${e.message}")
            } catch (e: ArithmeticException) {
                _result.postValue(null)
                _error.postValue("خطأ حسابي: ${e.message}")
                _errorMessage.postValue("Arithmetic error / خطأ حسابي: ${e.message}")
            } catch (e: Throwable) {
                _result.postValue(null)
                _error.postValue("خطأ: ${e.message?.take(100) ?: "Unknown error"}")
                _errorMessage.postValue("Error / خطأ: ${e.message?.take(100) ?: "Unknown error"}")
                android.util.Log.e("SoilBearingVM", "calculate crash", e)
            } finally {
                _isCalculating.postValue(false)
            }
        }
    }

    /** Compare all four methods and post results. */
    fun compareAllMethods() {
        _error.value = null
        _isCalculating.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val calculator = SoilBearingCalculator()
                val input = buildInput()
                val map = calculator.compareAllMethods(input)
                _comparisonResults.postValue(map)
                // Also set the single result to the selected method
                _result.postValue(map[input.method])
            } catch (e: IllegalArgumentException) {
                _result.postValue(null)
                _comparisonResults.postValue(emptyMap())
                _error.postValue("مدخلات غير صالحة: ${e.message}")
                _errorMessage.postValue("Invalid input / مدخلات غير صالحة: ${e.message}")
            } catch (e: ArithmeticException) {
                _result.postValue(null)
                _comparisonResults.postValue(emptyMap())
                _error.postValue("خطأ حسابي: ${e.message}")
                _errorMessage.postValue("Arithmetic error / خطأ حسابي: ${e.message}")
            } catch (e: Throwable) {
                _result.postValue(null)
                _comparisonResults.postValue(emptyMap())
                _error.postValue("خطأ: ${e.message?.take(100) ?: "Unknown error"}")
                _errorMessage.postValue("Error / خطأ: ${e.message?.take(100) ?: "Unknown error"}")
                android.util.Log.e("SoilBearingVM", "compareAll crash", e)
            } finally {
                _isCalculating.postValue(false)
            }
        }
    }

    // ------------------------------------------------------------------
    // Convenience: reset to defaults
    // ------------------------------------------------------------------

    fun resetToDefaults() {
        method.postValue(BearingMethod.TERZAGHI)
        soilType.postValue(SoilType.CLAY)
        foundationWidth.postValue("1.5")
        foundationLength.postValue("1.5")
        foundationDepth.postValue("1.0")
        cohesion.postValue("25.0")
        frictionAngle.postValue("30.0")
        unitWeight.postValue("18.0")
        waterTableDepth.postValue("5.0")
        eccentricityX.postValue("0.0")
        eccentricityY.postValue("0.0")
        loadInclinationX.postValue("0.0")
        loadInclinationY.postValue("0.0")
        safetyFactor.postValue("3.0")
        _result.postValue(null)
        _comparisonResults.postValue(emptyMap())
    }

    // ------------------------------------------------------------------
    // Quick presets based on soil type
    // ------------------------------------------------------------------

    fun applySoilPreset(soilType: SoilType) {
        this.soilType.postValue(soilType)
        when (soilType) {
            SoilType.CLAY -> {
                cohesion.postValue("25.0")
                frictionAngle.postValue("5.0")
                unitWeight.postValue("17.0")
            }
            SoilType.SAND -> {
                cohesion.postValue("0.0")
                frictionAngle.postValue("35.0")
                unitWeight.postValue("19.0")
            }
            SoilType.ROCK -> {
                cohesion.postValue("100.0")
                frictionAngle.postValue("45.0")
                unitWeight.postValue("24.0")
            }
            SoilType.MIXED -> {
                cohesion.postValue("15.0")
                frictionAngle.postValue("20.0")
                unitWeight.postValue("18.5")
            }
        }
    }

    fun onMethodSelected(method: BearingMethod) {
        this.method.postValue(method)
    }

    fun onSoilTypeSelected(soilType: SoilType) {
        this.soilType.postValue(soilType)
    }

    /** Export Soil Bearing Capacity report to PDF with 3-view drawings. */
    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit = {}) {
        val res = _result.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = buildInput()
                val drawingBitmap = com.civileg.app.utils.PdfDrawingGenerator.generateSoilBearingDrawing(
                    width = input.foundationWidth,
                    length = input.foundationLength,
                    depth = input.foundationDepth,
                    cohesion = input.cohesion,
                    phi = input.frictionAngle,
                    qUlt = res.grossBearingCapacity,
                    qAllow = res.allowableBearingCapacity,
                    gwt = input.waterTableDepth,
                    isSafe = true
                )

                val inputsMap = mapOf(
                    "Method" to input.method.name,
                    "Soil Type" to input.soilType.name,
                    "Foundation Width (B)" to "${input.foundationWidth} m",
                    "Foundation Length (L)" to "${input.foundationLength} m",
                    "Foundation Depth (D_f)" to "${input.foundationDepth} m",
                    "Cohesion (c)" to "${input.cohesion} kPa",
                    "Friction Angle (φ)" to "${input.frictionAngle}°",
                    "Unit Weight (γ)" to "${input.unitWeight} kN/m³",
                    "Safety Factor" to "${input.safetyFactor}"
                )

                val resultsMap = mapOf(
                    "Gross Ultimate Capacity (q_ult)" to "${"%.1f".format(res.grossBearingCapacity)} kPa",
                    "Net Ultimate Capacity (q_net)" to "${"%.1f".format(res.netBearingCapacity)} kPa",
                    "Allowable Capacity (q_allow)" to "${"%.1f".format(res.allowableBearingCapacity)} kPa",
                    "Effective Width (B')" to "${"%.2f".format(res.effectiveWidth)} m"
                )

                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, "Soil_Bearing_Report_${System.currentTimeMillis()}.pdf")

                val bundle = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير قدرة تحمّل التربة",
                    titleEn = "Soil Bearing Capacity Report",
                    subtitle = "Geotechnical Foundation Analysis",
                    designType = "Soil Bearing",
                    inputs = inputsMap,
                    results = resultsMap,
                    isSafe = true,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath,
                    openPdfOnComplete = true
                )
                onComplete(bundle.pdfFile)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(null)
            }
        }
    }
}
