package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.DesignCode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.domain.calculations.utils.CalculationValidator
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.shared.reporting.PdfDrawingGenerator
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Bitmap
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.calculations.InputGuard
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

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _validationReport = MutableLiveData<CalculationValidator.ValidationReport?>()
    val validationReport: LiveData<CalculationValidator.ValidationReport?> = _validationReport

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    var excavationDepthM: Double = 0.0
    var blindingThicknessM: Double = 0.0

    fun calculateTankPro(
        type: CalculatorEngine.TankType,
        capacity: Double,
        height: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: DesignCode
    ) {
        InputGuard.positive("capacity", capacity)
        InputGuard.positive("height", height)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("preferredDiameter", preferredDiameter)

        viewModelScope.launch {
            _isLoading.value = true
            pendingDrawingBitmap = null  // Reset stale bitmap from previous calculation
            try {
                val res = calculatorEngine.designTank(type, capacity, height, fcu, fy, preferredDiameter, code)
                
                // Validate Tank Design
                val report = CalculationValidator.validateTank(res)
                _validationReport.value = report
                
                _result.value = res
                _error.value = null
                _errorMessage.value = null
            } catch (e: Exception) {
                _result.value = null; _validationReport.value = null  // a failed attempt must not leave the previous design on screen
                _error.value = "Error: ${e.message}"
                _errorMessage.value = "Tank calculation error / خطأ في حساب الخزان: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Drop the shown result (an input changed or the last attempt failed). */
    fun clearResult() {
        _result.value = null
        _validationReport.value = null
        _error.value = null
        pendingDrawingBitmap = null
    }

    // Legacy method
    fun calculateTank(type: CalculatorEngine.TankType, capacity: Double, height: Double, fcu: Double, fy: Double, code: com.civileg.app.domain.entities.DesignCode = com.civileg.app.ui.defaultDesignCode()) {
        calculateTankPro(type, capacity, height, fcu, fy, 12, code)
    }

    fun saveTank(projectId: Long, name: String, result: CalculatorEngine.TankResult) {
        viewModelScope.launch {
            try {
                repository.saveTankDesign(projectId, name, result)
            } catch (e: Exception) {
                _errorMessage.value = "Save failed / فشل الحفظ: ${e.message}"
            }
        }
    }

    /** Standalone DXF sheet for the current tank design (null when no result). */
    fun buildDxfDrawing(): com.civileg.app.utils.DxfDrawable? {
        val res = _result.value ?: return null
        return com.civileg.app.utils.ElementDxfDrawings.tankSheet(
            title = "TANK ${res.type.displayNameEn}",
            isCircular = res.type.name.contains("CIRCULAR"),
            innerLengthMm = res.length * 1000.0,
            innerWidthMm = res.width * 1000.0,
            heightMm = res.height * 1000.0,
            wallThicknessMm = res.wallThickness,
            baseThicknessMm = res.baseThickness,
            coverMm = 40.0,
            vertBarDiaMm = res.wallReinforcement.diameter.toDouble(),
            vertBarSpacingMm = res.wallReinforcement.spacing.toDouble(),
            horizBarDiaMm = res.baseReinforcement.diameter.toDouble(),
            horizBarSpacingMm = res.baseReinforcement.spacing.toDouble(),
            underground = res.type.name.contains("UNDERGROUND"),
            upliftFs = res.safetyChecks.firstOrNull { it.name.contains("Uplift", ignoreCase = true) && !it.notAssessed }?.value ?: 0.0,
            upliftLimit = res.safetyChecks.firstOrNull { it.name.contains("Uplift", ignoreCase = true) && !it.notAssessed }?.limit ?: 0.0
        )
    }

    /** Export just the CAD drawing (no PDF) for the current design. */
    fun exportToDxf(context: Context, onComplete: (File?) -> Unit = {}) {
        com.civileg.app.utils.DxfQuickExport.export(
            context = context,
            drawing = buildDxfDrawing(),
            baseName = "Tank",
            scope = viewModelScope,
            onComplete = onComplete
        )
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // PDF + DXF via ExportManager.exportReportAndDxf (iText, EN-only per ADR-005/009).
                val fileName = "Tank_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                // Page-proportioned drawing from PdfDrawingGenerator (the screen capture is stretched on the page); the captured screen bitmap is only the fallback
                val generated2 = try {
                    PdfDrawingGenerator.generateTankDrawing(
                        tankType = res.type.displayName,
                        length = res.length,
                        width = res.width,
                        height = res.height,
                        wallThickness = res.wallThickness,
                        baseThickness = res.baseThickness,
                        verticalRebarDia = res.wallReinforcement.diameter.toDouble(),
                        verticalRebarSpacing = res.wallReinforcement.spacing.toDouble(),
                        horizontalRebarDia = res.baseReinforcement.diameter.toDouble(),
                        horizontalRebarSpacing = res.baseReinforcement.spacing.toDouble(),
                        waterLevel = res.height * 0.85,
                        foundationDepth = if (res.type == CalculatorEngine.TankType.UNDERGROUND || res.type == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND) res.height * 0.3 else 0.0
                    )
                } catch (e: Exception) { null }
                val drawingBitmap = generated2 ?: pendingDrawingBitmap
                pendingDrawingBitmap = null  // consume after use

                val codeName = when(res.code) {
                    DesignCode.ACI -> "ACI 318"
                    DesignCode.SBC -> "SBC 304"
                    else -> "ECP 203"
                }
                val inputsMap = mapOf(
                    "Tank Type" to res.type.displayNameEn,
                    (if (res.type.name.contains("CIRCULAR")) "Inside Diameter" else "Length") to "${String.format("%.2f", res.length)} m",
                    "Width" to (if (res.type.name.contains("CIRCULAR")) "same as diameter" else "${String.format("%.2f", res.width)} m"),
                    "Height" to "${String.format("%.2f", res.height)} m",
                    "Wall Thickness" to "${res.wallThickness} mm",
                    "Base Thickness" to "${res.baseThickness} mm",
                    "Design Code" to codeName,
                    "fcu" to "${res.fcu} MPa",
                    "fy" to "${res.fy} MPa",
                    "Excavation Depth" to "$excavationDepthM m",
                    "Blinding Thickness" to "$blindingThicknessM m"
                )
                val resultsMap = mutableMapOf(
                    "Capacity" to "${String.format("%.2f", res.capacity)} m³",
                    "Water Pressure" to "${String.format("%.2f", res.waterPressure)} kN/m²",
                    "Soil Pressure" to "${String.format("%.1f", res.soilPressure)} kPa",
                    "Design Moment" to (if (res.mu > 0.0) "${String.format("%.1f", res.mu)} kN.m/m" else "NOT ASSESSED"),
                    "Wall Reinforcement" to res.wallReinforcement.barString,
                    "Base Reinforcement" to res.baseReinforcement.barString,
                    "Utilization" to (if (res.utilizationRatio > 0.0) String.format("%.2f", res.utilizationRatio) else "NOT ASSESSED"),
                    "Concrete Volume" to "${String.format("%.3f", res.concreteVolume)} m³",
                    "Steel (indicative ratio, not designed bars)" to "${String.format("%.1f", res.steelWeight)} kg"
                )
                val safetyChecks = res.safetyChecks.map { chk ->
                    GenericSafetyCheck(
                        name = chk.name, calculated = chk.value,
                        limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                    )
                }

                // Professional English PDF Report — English only, no Arabic encoding issues
                val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم خزان - ${res.type.displayName}",
                    titleEn = "Tank Design Report — ${res.type.displayNameEn}",
                    subtitle = "Code: $codeName  •  " + (if (res.type.name.contains("CIRCULAR")) "Ø${String.format("%.2f", res.length)} × H ${String.format("%.2f", res.height)} m" else "${String.format("%.2f", res.length)}×${String.format("%.2f", res.width)}×${String.format("%.2f", res.height)} m"),
                    designType = "Tank (${res.type.displayNameEn})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    calculationSteps = res.calculationSteps,
                    // Tank bar schedule not issued: the generator schedules only vertical wall bars and one base mat
                    // (no horizontal wall bars, no top base layer), so it is not a designed-bar schedule.
                    bbsEntries = emptyList(),
                    boqRows = BoqEstimator.tankRows(
                        res.concreteVolume, res.steelWeight, res.length, res.width, res.height,
                        "T1", excavationDepthM, blindingThicknessM,
                        isCircular = res.type.name.contains("CIRCULAR"),
                        waterproofingAreaM2 = if (res.type.name.contains("UNDERGROUND")) {
                            BoqEstimator.tankWaterproofingArea(
                                isCircular = res.type.name.contains("CIRCULAR"),
                                lengthM = res.length, widthM = res.width, heightM = res.height
                            )
                        } else 0.0
                    ),
                    drawingBitmap = drawingBitmap,
                    drawing = buildDxfDrawing(),
                    outputPath = file.absolutePath
                ).pdfFile

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _error.value = "PDF Export Error: ${e.message}"
                    _errorMessage.value = "PDF export failed / فشل تصدير PDF: ${e.message}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
