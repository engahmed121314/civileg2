package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.WindLoadCalculator
import com.civileg.app.utils.WindLoadInput
import com.civileg.app.utils.WindLoadResult
import com.civileg.app.utils.BuildingShape
import com.civileg.app.utils.RoofType
import com.civileg.app.utils.TerrainCategory

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WindLoadViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private val _result = MutableLiveData<WindLoadResult?>()
    val result: LiveData<WindLoadResult?> = _result

    private val _isCalculating = MutableLiveData(false)
    val isCalculating: LiveData<Boolean> = _isCalculating

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun saveDesign(projectId: Long, name: String) {
        val res = _result.value ?: return
        viewModelScope.launch {
            repository.saveWindLoadDesign(projectId, name, res)
        }
    }

    private val _k2Table = MutableLiveData<List<Pair<Double, Double>>>(emptyList())
    val k2Table: LiveData<List<Pair<Double, Double>>> = _k2Table

    // ------------------------------------------------------------------
    // Input fields
    // ------------------------------------------------------------------

    val basicWindSpeed = MutableLiveData("30.0")
    val terrainCategory = MutableLiveData(TerrainCategory.SUBURBAN)
    val buildingHeight = MutableLiveData("20.0")
    val buildingWidth = MutableLiveData("15.0")
    val buildingDepth = MutableLiveData("10.0")
    val buildingShape = MutableLiveData(BuildingShape.RECTANGULAR)
    val roofType = MutableLiveData(RoofType.FLAT)
    val roofSlope = MutableLiveData("0.0")
    val importanceFactor = MutableLiveData("1.0")
    val topographyFactor = MutableLiveData("1.0")
    val numberOfFloors = MutableLiveData("5")
    val openingsInWindward = MutableLiveData(false)
    val isFlexibleStructure = MutableLiveData(false)
    val naturalFrequency = MutableLiveData("1.0")
    val dampingRatio = MutableLiveData("0.02")

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun MutableLiveData<String>.doubleValue(default: Double = 0.0): Double {
        return try {
            value?.toDoubleOrNull() ?: default
        } catch (_: NumberFormatException) {
            default
        }
    }

    private fun MutableLiveData<String>.intValue(default: Int = 1): Int {
        return try {
            value?.toIntOrNull() ?: default
        } catch (_: NumberFormatException) {
            default
        }
    }

    // ------------------------------------------------------------------
    // Build input
    // ------------------------------------------------------------------

    private fun buildInput(): WindLoadInput {
        // Rule 1.4: loud failures — validate before any maths (ADR-010)
        InputGuard.positive("basicWindSpeed", basicWindSpeed.value?.toDoubleOrNull() ?: 30.0)
        InputGuard.positive("buildingHeight", buildingHeight.value?.toDoubleOrNull() ?: 20.0)
        InputGuard.positive("buildingWidth", buildingWidth.value?.toDoubleOrNull() ?: 15.0)
        InputGuard.positive("buildingDepth", buildingDepth.value?.toDoubleOrNull() ?: 10.0)
        InputGuard.positive("importanceFactor", importanceFactor.value?.toDoubleOrNull() ?: 1.0)
        InputGuard.positive("topographyFactor", topographyFactor.value?.toDoubleOrNull() ?: 1.0)
        InputGuard.positive("numberOfFloors", numberOfFloors.value?.toIntOrNull() ?: 5)
        InputGuard.positive("naturalFrequency", naturalFrequency.value?.toDoubleOrNull() ?: 1.0)
        InputGuard.positive("dampingRatio", dampingRatio.value?.toDoubleOrNull() ?: 0.02)
        InputGuard.inRange("dampingRatio", dampingRatio.value?.toDoubleOrNull() ?: 0.02, 0.001, 0.1)

        return WindLoadInput(
            basicWindSpeed      = basicWindSpeed.value?.toDoubleOrNull() ?: 30.0,
            terrainCategory    = terrainCategory.value ?: TerrainCategory.SUBURBAN,
            buildingHeight     = buildingHeight.value?.toDoubleOrNull() ?: 20.0,
            buildingWidth      = buildingWidth.value?.toDoubleOrNull() ?: 15.0,
            buildingDepth      = buildingDepth.value?.toDoubleOrNull() ?: 10.0,
            buildingShape      = buildingShape.value ?: BuildingShape.RECTANGULAR,
            roofType           = roofType.value ?: RoofType.FLAT,
            roofSlope          = roofSlope.value?.toDoubleOrNull() ?: 0.0,
            importanceFactor   = importanceFactor.value?.toDoubleOrNull() ?: 1.0,
            topographyFactor   = topographyFactor.value?.toDoubleOrNull() ?: 1.0,
            numberOfFloors     = numberOfFloors.value?.toIntOrNull() ?: 5,
            openingsInWindward = openingsInWindward.value ?: false,
            isFlexibleStructure = isFlexibleStructure.value ?: false,
            naturalFrequency   = naturalFrequency.value?.toDoubleOrNull() ?: 1.0,
            dampingRatio       = dampingRatio.value?.toDoubleOrNull() ?: 0.02
        )
    }

    // ------------------------------------------------------------------
    // Core calculation
    // ------------------------------------------------------------------

    fun calculate() {
        _error.value = null
        _isCalculating.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val calculator = WindLoadCalculator()
                val input = buildInput()
                val res = calculator.calculate(input)
                _result.postValue(res)
            } catch (e: IllegalArgumentException) {
                _result.postValue(null)
                _error.postValue("مدخلات غير صالحة: ${e.message}")
                _errorMessage.postValue("Invalid input / مدخلات غير صالحة: ${e.message}")
            } catch (e: ArithmeticException) {
                _result.postValue(null)
                _error.postValue("خطأ حسابي: ${e.message}")
                _errorMessage.postValue("Arithmetic error / خطأ حسابي: ${e.message}")
            } catch (e: Exception) {
                _result.postValue(null)
                _error.postValue("خطأ غير متوقع: ${e.message}")
                _errorMessage.postValue("Unexpected error / خطأ غير متوقع: ${e.message}")
                android.util.Log.e("WindLoadVM", "calculate crash", e)
            } finally {
                _isCalculating.postValue(false)
            }
        }
    }

    // ------------------------------------------------------------------
    // Generate k2 table for current terrain and height
    // ------------------------------------------------------------------

    fun updateK2Table() {
        _error.value = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val terrain = terrainCategory.value ?: TerrainCategory.SUBURBAN
                val height = buildingHeight.doubleValue(20.0)
                val calculator = WindLoadCalculator()
                _k2Table.postValue(calculator.getK2Table(terrain, height))
            } catch (e: IllegalArgumentException) {
                _k2Table.postValue(emptyList())
                _error.postValue("مدخلات غير صالحة: ${e.message}")
                _errorMessage.postValue("Invalid input / مدخلات غير صالحة: ${e.message}")
            } catch (e: ArithmeticException) {
                _k2Table.postValue(emptyList())
                _error.postValue("خطأ حسابي: ${e.message}")
                _errorMessage.postValue("Arithmetic error / خطأ حسابي: ${e.message}")
            } catch (e: Exception) {
                _k2Table.postValue(emptyList())
                _error.postValue("خطأ غير متوقع: ${e.message}")
                _errorMessage.postValue("Unexpected error / خطأ غير متوقع: ${e.message}")
                android.util.Log.e("WindLoadVM", "updateK2Table crash", e)
            }
        }
    }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    fun resetToDefaults() {
        basicWindSpeed.postValue("30.0")
        terrainCategory.postValue(TerrainCategory.SUBURBAN)
        buildingHeight.postValue("20.0")
        buildingWidth.postValue("15.0")
        buildingDepth.postValue("10.0")
        buildingShape.postValue(BuildingShape.RECTANGULAR)
        roofType.postValue(RoofType.FLAT)
        roofSlope.postValue("0.0")
        importanceFactor.postValue("1.0")
        topographyFactor.postValue("1.0")
        numberOfFloors.postValue("5")
        openingsInWindward.postValue(false)
        isFlexibleStructure.postValue(false)
        naturalFrequency.postValue("1.0")
        dampingRatio.postValue("0.02")
        _result.postValue(null)
        _k2Table.postValue(emptyList())
    }

    // ------------------------------------------------------------------
    // Selection handlers
    // ------------------------------------------------------------------

    fun onTerrainSelected(terrain: TerrainCategory) {
        terrainCategory.postValue(terrain)
        updateK2Table()
    }

    fun onShapeSelected(shape: BuildingShape) {
        buildingShape.postValue(shape)
    }

    fun onRoofTypeSelected(type: RoofType) {
        roofType.postValue(type)
        // Auto-set slope for flat/gabled
        when (type) {
            RoofType.FLAT   -> roofSlope.postValue("0.0")
            RoofType.GABLED -> roofSlope.postValue("15.0")
            RoofType.HIP    -> roofSlope.postValue("20.0")
        }
    }

    // ------------------------------------------------------------------
    // Presets
    // ------------------------------------------------------------------

    /** Apply a quick preset for common building types. */
    fun applyPreset(preset: WindPreset) {
        when (preset) {
            WindPreset.LOW_RISE_RESIDENTIAL -> {
                basicWindSpeed.postValue("33.0")
                terrainCategory.postValue(TerrainCategory.SUBURBAN)
                buildingHeight.postValue("10.0")
                buildingWidth.postValue("12.0")
                buildingDepth.postValue("10.0")
                buildingShape.postValue(BuildingShape.RECTANGULAR)
                roofType.postValue(RoofType.GABLED)
                roofSlope.postValue("15.0")
                importanceFactor.postValue("1.0")
                topographyFactor.postValue("1.0")
                numberOfFloors.postValue("3")
                openingsInWindward.postValue(false)
                isFlexibleStructure.postValue(false)
            }
            WindPreset.HIGH_RISE_OFFICE -> {
                basicWindSpeed.postValue("47.0")
                terrainCategory.postValue(TerrainCategory.URBAN)
                buildingHeight.postValue("60.0")
                buildingWidth.postValue("25.0")
                buildingDepth.postValue("20.0")
                buildingShape.postValue(BuildingShape.RECTANGULAR)
                roofType.postValue(RoofType.FLAT)
                roofSlope.postValue("0.0")
                importanceFactor.postValue("1.0")
                topographyFactor.postValue("1.0")
                numberOfFloors.postValue("15")
                openingsInWindward.postValue(false)
                isFlexibleStructure.postValue(true)
                naturalFrequency.postValue("0.5")
                dampingRatio.postValue("0.02")
            }
            WindPreset.WAREHOUSE -> {
                basicWindSpeed.postValue("39.0")
                terrainCategory.postValue(TerrainCategory.OPEN_TERRAIN)
                buildingHeight.postValue("8.0")
                buildingWidth.postValue("30.0")
                buildingDepth.postValue("50.0")
                buildingShape.postValue(BuildingShape.RECTANGULAR)
                roofType.postValue(RoofType.GABLED)
                roofSlope.postValue("10.0")
                importanceFactor.postValue("0.9")
                topographyFactor.postValue("1.0")
                numberOfFloors.postValue("1")
                openingsInWindward.postValue(true)
                isFlexibleStructure.postValue(false)
            }
            WindPreset.COASTAL_TOWER -> {
                basicWindSpeed.postValue("50.0")
                terrainCategory.postValue(TerrainCategory.SEA_COAST)
                buildingHeight.postValue("100.0")
                buildingWidth.postValue("20.0")
                buildingDepth.postValue("15.0")
                buildingShape.postValue(BuildingShape.CIRCULAR)
                roofType.postValue(RoofType.FLAT)
                roofSlope.postValue("0.0")
                importanceFactor.postValue("1.15")
                topographyFactor.postValue("1.1")
                numberOfFloors.postValue("25")
                openingsInWindward.postValue(false)
                isFlexibleStructure.postValue(true)
                naturalFrequency.postValue("0.3")
                dampingRatio.postValue("0.015")
            }
        }
        updateK2Table()
        _result.postValue(null)
    }

    /** Export Wind Load Analysis report to PDF with 3-view engineering drawings. */
    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit = {}) {
        val res = _result.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = buildInput()
                val drawingBitmap = com.civileg.app.utils.PdfDrawingGenerator.generateWindLoadDrawing(
                    basicSpeed = input.basicWindSpeed,
                    height = input.buildingHeight,
                    width = input.buildingWidth,
                    depth = input.buildingDepth,
                    qzMax = res.designWindPressure,
                    baseShear = res.totalBaseShear,
                    buildingShape = input.buildingShape.name,
                    isSafe = true
                )

                val inputsMap = mapOf(
                    "Basic Wind Speed (V)" to "${input.basicWindSpeed} m/s",
                    "Building Height (H)" to "${input.buildingHeight} m",
                    "Building Width (B)" to "${input.buildingWidth} m",
                    "Building Depth (L)" to "${input.buildingDepth} m",
                    "Terrain Category" to input.terrainCategory.name,
                    "Importance Factor (I)" to "${input.importanceFactor}"
                )

                val resultsMap = mapOf(
                    "Top Wind Pressure (p_z)" to "${"%.2f".format(res.designWindPressure)} kN/m²",
                    "Total Base Shear (F_base)" to "${"%.1f".format(res.totalBaseShear)} kN",
                    "Overturning Moment" to "${"%.1f".format(res.overturningMoment)} kN.m",
                    "Gust Factor (G)" to "${"%.3f".format(res.gustFactor)}"
                )

                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, "Wind_Load_Report_${System.currentTimeMillis()}.pdf")

                val bundle = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير حساب أحمال الرياح",
                    titleEn = "Wind Load Analysis Report",
                    subtitle = "Building Wind Pressure & Base Shear",
                    designType = "Wind Analysis",
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

/** Quick-load presets for typical building scenarios. */
enum class WindPreset(val label: String) {
    LOW_RISE_RESIDENTIAL("Low-Rise Residential"),
    HIGH_RISE_OFFICE("High-Rise Office"),
    WAREHOUSE("Warehouse / Industrial"),
    COASTAL_TOWER("Coastal Tower")
}
