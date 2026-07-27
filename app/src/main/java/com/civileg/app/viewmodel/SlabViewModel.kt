package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SlabViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.SlabResult?>()
    val result: LiveData<CalculatorEngine.SlabResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Store actual inputs for PDF export
    private var lastInputs: SlabStoredInputs? = null

    private data class SlabStoredInputs(
        val lx: Double, val ly: Double, val deadLoad: Double, val liveLoad: Double,
        val fcu: Double, val fy: Double, val ts: Double, val preferredDiameter: Int,
        val code: CalculatorEngine.DesignCode, val type: CalculatorEngine.SlabType,
        val prestressForce: Double, val dropPanelThickness: Double, val columnSize: Double
    )

    fun calculateSlabPro(
        lx: Double,
        ly: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        ts: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode,
        type: CalculatorEngine.SlabType = CalculatorEngine.SlabType.SOLID,
        prestressForce: Double = 0.0,
        dropPanelThickness: Double = 0.0,
        columnSize: Double = 400.0
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Store inputs for PDF export
                lastInputs = SlabStoredInputs(
                    lx, ly, deadLoad, liveLoad, fcu, fy, ts, preferredDiameter,
                    code, type, prestressForce, dropPanelThickness, columnSize
                )

                val res = calculatorEngine.designSlab(
                    lx = lx, ly = ly, deadLoad = deadLoad, liveLoad = liveLoad,
                    fcu = fcu, fy = fy, ts = ts, preferredDiameter = preferredDiameter,
                    code = code, type = type, prestressForce = prestressForce,
                    dropPanelThickness = dropPanelThickness, columnSize = columnSize
                )
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun calculateSlab(
        spanX: Double, spanY: Double, deadLoad: Double, liveLoad: Double,
        fcu: Double, fy: Double, thickness: Double, preferredDiameter: Int,
        type: CalculatorEngine.SlabType = CalculatorEngine.SlabType.SOLID,
        code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
        prestressForce: Double = 0.0,
        dropPanelThickness: Double = 0.0,
        columnSize: Double = 400.0
    ) {
        calculateSlabPro(
            lx = spanX, ly = spanY, deadLoad = deadLoad, liveLoad = liveLoad,
            fcu = fcu, fy = fy, ts = thickness, preferredDiameter = preferredDiameter,
            code = code, type = type, prestressForce = prestressForce,
            dropPanelThickness = dropPanelThickness, columnSize = columnSize
        )
    }

    fun saveSlab(projectId: Long, name: String, result: CalculatorEngine.SlabResult) {
        viewModelScope.launch { repository.saveSlabDesign(projectId, name, result) }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val res = _result.value ?: return
        val inputs = lastInputs ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // CRITICAL FIX (2026-07-27 v2): Use NativePdfExporter with StaticLayout
                // for proper Arabic BIDI + HarfBuzz shaping. iText 8 AGPL lacks
                // pdfCalligraph so Arabic appears as disconnected squares.
                val fileName = "Slab_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, fileName)

                // Generate drawing bitmap (bilingual, type-aware)
                val drawingBitmap = try {
                    com.civileg.app.utils.PdfDrawingGenerator.generateSlabDrawingByType(
                        slabType = inputs.type,
                        spanX = inputs.lx, spanY = inputs.ly, thickness = res.thickness,
                        mainDia = res.reinforcementMain.diameter.toDouble(),
                        mainSpacing = res.reinforcementMain.spacing,
                        distDia = res.reinforcementSecondary.diameter.toDouble(),
                        distSpacing = res.reinforcementSecondary.spacing,
                        dropPanelSize = inputs.dropPanelThickness,
                        ribWidth = 100.0,
                        ribSpacing = 500.0,
                        columnSize = inputs.columnSize
                    )
                } catch (e: Exception) { e.printStackTrace(); null }

                // Bilingual labels: Arabic descriptions when locale=ar, English for symbols
                val isAr = com.civileg.app.utils.LocaleHelper.isArabic()
                fun t(ar: String, en: String) = if (isAr) ar else en
                val codeName = when(inputs.code) {
                    CalculatorEngine.DesignCode.ACI -> "ACI 318"
                    CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
                    else -> "ECP 203"
                }
                val inputsMap = mapOf(
                    t("نوع البلاطة", "Slab Type") to inputs.type.displayName,
                    t("كود التصميم", "Design Code") to codeName,
                    t("البحر القصير Lx", "Short Span Lx") to "${inputs.lx} m",
                    t("البحر الطويل Ly", "Long Span Ly") to "${inputs.ly} m",
                    t("الحمل الميت DL", "Dead Load") to "${inputs.deadLoad} kN/m²",
                    t("الحمل الحي LL", "Live Load") to "${inputs.liveLoad} kN/m²",
                    "f'cu" to "${inputs.fcu} MPa",
                    "fy" to "${inputs.fy} MPa",
                    t("السمك", "Thickness") to "${res.thickness} mm",
                    t("قطر السيخ", "Bar Diameter") to "${inputs.preferredDiameter} mm"
                )
                val resultsMap = mapOf(
                    t("عزم Mx", "Moment Mx") to "${String.format("%.2f", res.momentX)} kN.m",
                    t("عزم My", "Moment My") to "${String.format("%.2f", res.momentY)} kN.m",
                    t("التسليح الرئيسي", "Main Reinforcement") to res.reinforcementMain.barString,
                    t("التسليح الثانوي", "Secondary Reinforcement") to res.reinforcementSecondary.barString,
                    t("أدنى سمك", "Min Thickness") to "${String.format("%.0f", res.minThickness)} mm",
                    t("نسبة الاستغلال", "Utilization") to "${(res.utilizationRatio * 100).toInt()}%",
                    t("حجم الخرسانة", "Concrete Volume") to "${String.format("%.2f", res.concreteVolume)} m³",
                    t("وزن التسليح", "Steel Weight") to "${String.format("%.1f", res.steelWeight)} kg"
                )
                val safetyChecks = res.safetyChecks.map { chk ->
                    com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = chk.name,
                        calculated = chk.value,
                        limit = chk.limit,
                        unit = chk.unit,
                        passed = chk.isSafe
                    )
                }

                // CRITICAL FIX (2026-07-27 v4): Switched from NativePdfExporter (Android-native
                // PdfDocument + Canvas — caused native Skia crashes) to ComprehensivePdfExporter
                // (iText 8 — same safe path as FrameAnalysisPdfExporter which never crashes).
                val exporter = com.civileg.app.utils.exporters.ComprehensivePdfExporter(context)
                val generated = exporter.exportGenericReport(
                    titleAr = "تقرير تصميم بلاطة - ${inputs.type.displayName}",
                    titleEn = "Slab Design Report — ${inputs.type.displayName}",
                    subtitle = "${t("الكود", "Code")}: $codeName  •  Lx=${inputs.lx}m, Ly=${inputs.ly}m",
                    designType = inputs.type.displayName,
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message ?: "Unknown error"}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}

data class SlabInputData(
    val spanX: Double,
    val spanY: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double,
    val type: CalculatorEngine.SlabType
)