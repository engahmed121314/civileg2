package com.civileg.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.SettingsManager
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import com.civileg.app.domain.calculations.InputGuard

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {
    
    private fun applyLanguage() {
        // ProfessionalEnglishPdfReporter always uses English — no language switch needed
    }
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    fun exportColumnReport(
        context: Context,
        projectName: String,
        designCode: DesignCode,
        columnType: ColumnType,
        inputs: ColumnInputs,
        result: AdvancedColumnResult,
        inventoryAnalysis: InventoryAnalysisResult?,
        alternatives: List<ColumnAlternative>,
        drawingBitmap: Bitmap? = null
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            applyLanguage()
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Column_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                // Generate drawing if not provided
                val bitmap = drawingBitmap ?: generateColumnBitmap(columnType, result)
                
                val inputsMap = mapOf(
                    "Project" to projectName,
                    "Design Code" to designCode.displayName,
                    "Column Type" to columnType.displayName
                )
                val resultsMap = mapOf(
                    "Axial Capacity" to String.format("%.1f", result.axialCapacity) + " kN",
                    "Slenderness Ratio" to String.format("%.1f", result.slendernessRatio),
                    "Is Slender" to if (result.isSlender) "Yes" else "No",
                    "Main Bars" to "${result.reinforcementResult.numberOfBars}Ø${result.reinforcementResult.barDiameter}",
                    "Ties" to "Ø${result.reinforcementResult.tiesDiameter.toInt()}@${result.reinforcementResult.tiesSpacing.toInt()} mm",
                    "Status" to if (result.reinforcementResult.isSafe) "SAFE" else "UNSAFE"
                )
                val safetyChecks = result.reinforcementResult.let { rr ->
                    listOf(GenericSafetyCheck(
                        "Column Capacity", 0.0, result.axialCapacity, "kN", rr.isSafe
                    ))
                }
                val generated = ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم عمود",
                    titleEn = "Column Design Report",
                    subtitle = "Code: ${designCode.displayName}  •  ${columnType.displayName}",
                    designType = "Column",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = result.reinforcementResult.isSafe,
                    drawingBitmap = bitmap,
                    outputPath = outputFile.absolutePath
                )

                _exportState.value = ExportState.Success(generated!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
                _errorMessage.value = "Column export failed / فشل تصدير العمود: ${e.localizedMessage ?: "Export failed"}"
            }
        }
    }
    
    fun exportBeamReport(
        context: Context,
        projectName: String,
        designCode: DesignCode,
        beamType: BeamType,
        inputs: BeamInputs,
        result: AdvancedBeamResult,
        inventoryAnalysis: InventoryAnalysisResult?,
        diagrams: MomentShearDiagrams,
        drawingBitmap: Bitmap? = null
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            applyLanguage()
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Beam_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                // Generate advanced drawing with BMD/SFD if diagrams available
                val bitmap = drawingBitmap ?: if (diagrams.momentPoints.isNotEmpty()) {
                    PdfDrawingGenerator.generateBeamDrawingWithDiagrams(
                        beamWidth = inputs.width,
                        beamDepth = inputs.totalDepth,
                        span = inputs.span,
                        mainRebarDia = result.flexureResult.barDiameter,
                        mainRebarCount = result.flexureResult.numberOfBars,
                        stirrupDia = 8.0,
                        stirrupSpacing = 200.0,
                        momentPoints = diagrams.momentPoints,
                        shearPoints = diagrams.shearPoints,
                        maxMoment = diagrams.momentPoints.maxOfOrNull { it.second } ?: 0.0,
                        maxShear = diagrams.shearPoints.maxOfOrNull { kotlin.math.abs(it.second) } ?: 0.0,
                        isSafe = true
                    )
                } else null
                
                val inputsMap = mapOf(
                    "Project" to projectName,
                    "Design Code" to designCode.displayName,
                    "Beam Type" to beamType.displayName
                )
                val resultsMap = mapOf(
                    "Flexural Capacity" to String.format("%.1f", result.flexureResult.astProvided) + " mm²",
                    "Main Bars" to "${result.flexureResult.numberOfBars}Ø${result.flexureResult.barDiameter}",
                    "Ties" to "Ø${result.flexureResult.tiesDiameter.toInt()}@${result.flexureResult.tiesSpacing.toInt()} mm",
                    "Status" to if (result.flexureResult.isSafe) "SAFE" else "UNSAFE"
                )
                val safetyChecks = listOf(GenericSafetyCheck(
                    "Beam Capacity", 0.0, result.flexureResult.astProvided, "mm²", result.flexureResult.isSafe
                ))
                val generated = ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم كمرة",
                    titleEn = "Beam Design Report",
                    subtitle = "Code: ${designCode.displayName}  •  ${beamType.displayName}",
                    designType = "Beam",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = result.flexureResult.isSafe,
                    drawingBitmap = bitmap,
                    outputPath = outputFile.absolutePath
                )

                _exportState.value = ExportState.Success(generated!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
                _errorMessage.value = "Beam export failed / فشل تصدير الكمرة: ${e.localizedMessage ?: "Export failed"}"
            }
        }
    }

    fun exportSlabReport(
        context: Context,
        projectName: String,
        designCode: DesignCode,
        slabType: SlabType,
        inputs: SlabInputs,
        result: AdvancedSlabResult,
        drawingBitmap: Bitmap? = null
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            applyLanguage()
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Slab_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                val bitmap = drawingBitmap ?: generateSlabBitmap(slabType, result)
                
                val inputsMap = mapOf(
                    "Project" to projectName,
                    "Design Code" to designCode.displayName,
                    "Slab Type" to slabType.displayName
                )
                val resultsMap = mapOf(
                    "Flexural Rebar" to "${(1000.0 / result.flexureResult.barSpacing).toInt()}Ø${result.flexureResult.barDiameter.toInt()}@${result.flexureResult.barSpacing.toInt()} mm",
                    "Concrete Volume" to String.format("%.3f", result.concreteVolume) + " m³",
                    "Status" to if (result.flexureResult.isSafe) "SAFE" else "UNSAFE"
                )
                val safetyChecks = listOf(GenericSafetyCheck(
                    "Slab Capacity", 0.0, result.flexureResult.providedReinforcement, "mm²", result.flexureResult.isSafe
                ))
                val generated = ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم بلاطة",
                    titleEn = "Slab Design Report",
                    subtitle = "Code: ${designCode.displayName}  •  ${slabType.displayName}",
                    designType = "Slab",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = result.flexureResult.isSafe,
                    drawingBitmap = bitmap,
                    outputPath = outputFile.absolutePath
                )

                _exportState.value = ExportState.Success(generated!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
                _errorMessage.value = "Slab export failed / فشل تصدير البلاطة: ${e.localizedMessage ?: "Export failed"}"
            }
        }
    }

    fun exportSteelReport(
        context: Context,
        projectName: String,
        designCode: DesignCode,
        sectionType: SteelSectionType,
        memberType: SteelMemberType,
        inputs: SteelInputs,
        result: SteelMemberResult,
        connectionDesign: ConnectionDesignResult?,
        drawingBitmap: Bitmap? = null
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            applyLanguage()
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Steel_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                val bitmap = drawingBitmap ?: PdfDrawingGenerator.generateSteelDrawing(
                    sectionName = sectionType.sectionName,
                    sectionHeight = sectionType.depth,
                    flangeWidth = sectionType.width,
                    webThickness = sectionType.webThickness,
                    flangeThickness = sectionType.flangeThickness,
                    memberLength = inputs.length,
                    isSafe = result.isSafe,
                    utilizationRatio = result.utilizationRatio * 100,
                    sectionType = sectionType.displayName,
                    radius = sectionType.rootRadius,
                    area = sectionType.area,
                    ix = sectionType.ix,
                    sx = sectionType.sx,
                    zx = sectionType.zx,
                    weightPerMeter = sectionType.weight,
                    boltDia = 20.0,
                    boltCount = 4,
                    boltGauge = 90.0,
                    boltPitch = 75.0,
                    endPlateThickness = 12.0,
                    hasStiffener = false,
                    weldSize = 6.0,
                    isColumn = memberType == SteelMemberType.COLUMN
                )
                
                val codeName = when (designCode) {
                    DesignCode.ACI -> "AISC 360-16"
                    DesignCode.SBC -> "SBC 306"
                    else -> "ECP 205-2007"
                }
                val memberTypeLabel = when (memberType) {
                    SteelMemberType.COLUMN -> "Column"
                    SteelMemberType.BEAM -> "Beam"
                    SteelMemberType.BRACING -> "Bracing"
                    SteelMemberType.TRUSS_MEMBER -> "Truss"
                    SteelMemberType.GIRDERS -> "Girder"
                }
                val inputsMap = mapOf(
                    "Project" to projectName,
                    "Design Code" to codeName,
                    "Section" to sectionType.displayName,
                    "Member Type" to memberTypeLabel,
                    "Length" to "${inputs.length / 1000.0} m",
                    "Axial Load" to "${inputs.axialLoad} kN",
                    "Moment" to "${inputs.moment} kN.m",
                    "Shear" to "${inputs.shear} kN"
                )
                val resultsMap = mapOf(
                    "Axial Capacity" to String.format("%.2f", result.axialCapacity) + " kN",
                    "Moment Capacity" to String.format("%.2f", result.flexuralCapacity) + " kN.m",
                    "Shear Capacity" to String.format("%.2f", result.shearCapacity) + " kN",
                    "Utilization" to "${(result.utilizationRatio * 100).toInt()}%",
                    "Status" to if (result.isSafe) "SAFE" else "UNSAFE"
                )
                val safetyChecks = mutableListOf<GenericSafetyCheck>()
                if (inputs.axialLoad > 0) safetyChecks.add(GenericSafetyCheck("Axial", inputs.axialLoad, result.axialCapacity, "kN", inputs.axialLoad <= result.axialCapacity))
                if (inputs.moment > 0) safetyChecks.add(GenericSafetyCheck("Flexural", inputs.moment, result.flexuralCapacity, "kN.m", inputs.moment <= result.flexuralCapacity))
                if (inputs.shear > 0) safetyChecks.add(GenericSafetyCheck("Shear", inputs.shear, result.shearCapacity, "kN", inputs.shear <= result.shearCapacity))
                val generated = ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم قطاع معدني - ${sectionType.displayName}",
                    titleEn = "Steel Member Design Report — ${sectionType.displayName}",
                    subtitle = "Code: $codeName  •  $memberTypeLabel",
                    designType = "Steel — ${sectionType.displayName}",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = result.isSafe,
                    drawingBitmap = bitmap,
                    outputPath = outputFile.absolutePath
                )

                _exportState.value = ExportState.Success(generated!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
                _errorMessage.value = "Steel export failed / فشل تصدير القطعة الحديدية: ${e.localizedMessage ?: "Export failed"}"
            }
        }
    }
    
    // ========== Bitmap Generation Helpers ==========
    
    private fun generateColumnBitmap(columnType: ColumnType, result: AdvancedColumnResult): Bitmap? {
        return try {
            when (columnType) {
                is ColumnType.Rectangular -> PdfDrawingGenerator.generateColumnDrawing(
                    columnWidth = columnType.width, columnDepth = columnType.depth,
                    columnHeight = 3000.0,
                    numBars = result.reinforcementResult.numberOfBars,
                    barDia = result.reinforcementResult.barDiameter,
                    tieDia = 8.0, tieSpacing = 200.0, cover = 40.0
                )
                is ColumnType.Circular -> PdfDrawingGenerator.generateColumnDrawing(
                    columnWidth = columnType.diameter, columnDepth = columnType.diameter,
                    columnHeight = 3000.0,
                    numBars = result.reinforcementResult.numberOfBars,
                    barDia = result.reinforcementResult.barDiameter,
                    tieDia = 8.0, tieSpacing = 200.0, cover = 40.0
                )
                else -> null
            }
        } catch (e: Exception) { null }
    }
    
    private fun generateSlabBitmap(slabType: SlabType, result: AdvancedSlabResult): Bitmap? {
        return try {
            val lx = when (slabType) {
                is SlabType.Solid -> slabType.shortSpan
                is SlabType.FlatPlate -> slabType.panelLength
                is SlabType.Hordi -> slabType.span
                else -> 5.0
            }
            val ly = when (slabType) {
                is SlabType.Solid -> slabType.longSpan
                is SlabType.FlatPlate -> slabType.panelWidth
                is SlabType.Hordi -> slabType.span
                else -> 5.0
            }
            val thickness = when (slabType) {
                is SlabType.Solid -> slabType.thickness
                is SlabType.FlatPlate -> slabType.thickness
                is SlabType.Hordi -> slabType.totalThickness
                else -> 150.0
            }
            val bottomBars = result.reinforcementLayout.bottomBars
            val distBars = result.reinforcementLayout.distributionBars
            
            PdfDrawingGenerator.generateSlabDrawing(
                spanX = lx, spanY = ly, thickness = thickness,
                mainDia = bottomBars.diameter, mainSpacing = bottomBars.spacing,
                distDia = distBars?.diameter ?: 12.0, distSpacing = distBars?.spacing ?: 200.0
            )
        } catch (e: Exception) { null }
    }
    
    fun reset() {
        _exportState.value = ExportState.Idle
    }
}

sealed class ExportState {
    object Idle : ExportState()
    object Exporting : ExportState()
    data class Success(val file: File) : ExportState()
    data class Error(val message: String) : ExportState()
}