package com.civileg.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.SettingsManager
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val pdfExporter: ComprehensivePdfExporter,
    private val settingsManager: SettingsManager
) : ViewModel() {
    
    private fun applyLanguage() {
        pdfExporter.setLanguage(settingsManager.language)
    }
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState
    
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
                
                val file = pdfExporter.exportColumnReport(
                    projectName = projectName,
                    designCode = designCode,
                    columnType = columnType,
                    inputs = inputs,
                    result = result,
                    inventoryAnalysis = inventoryAnalysis,
                    alternatives = alternatives,
                    outputPath = outputFile.absolutePath,
                    drawingBitmap = bitmap
                )
                
                _exportState.value = ExportState.Success(file!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
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
                        mainRebarDia = 20.0, // default, should come from result
                        mainRebarCount = 4,
                        stirrupDia = 8.0,
                        stirrupSpacing = 200.0,
                        momentPoints = diagrams.momentPoints,
                        shearPoints = diagrams.shearPoints,
                        maxMoment = diagrams.momentPoints.maxOfOrNull { it.second } ?: 0.0,
                        maxShear = diagrams.shearPoints.maxOfOrNull { kotlin.math.abs(it.second) } ?: 0.0,
                        isSafe = true
                    )
                } else null
                
                val file = pdfExporter.exportBeamReport(
                    projectName = projectName,
                    designCode = designCode,
                    beamType = beamType,
                    inputs = inputs,
                    result = result,
                    inventoryAnalysis = inventoryAnalysis,
                    momentShearDiagrams = diagrams,
                    outputPath = outputFile.absolutePath,
                    drawingBitmap = bitmap
                )
                
                _exportState.value = ExportState.Success(file!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
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
                
                val file = pdfExporter.exportSlabReport(
                    projectName = projectName,
                    designCode = designCode,
                    slabType = slabType,
                    inputs = inputs,
                    result = result,
                    outputPath = outputFile.absolutePath,
                    drawingBitmap = bitmap
                )
                
                _exportState.value = ExportState.Success(file!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
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
                    sectionName = sectionType.displayName,
                    sectionHeight = sectionType.depth,
                    flangeWidth = sectionType.width,
                    webThickness = sectionType.webThickness,
                    flangeThickness = sectionType.flangeThickness,
                    memberLength = inputs.length,
                    isSafe = result.isSafe,
                    utilizationRatio = result.utilizationRatio * 100
                )
                
                val file = pdfExporter.exportSteelReport(
                    projectName = projectName,
                    designCode = designCode,
                    sectionType = sectionType,
                    memberType = memberType,
                    inputs = inputs,
                    result = result,
                    connectionDesign = connectionDesign,
                    outputPath = outputFile.absolutePath,
                    drawingBitmap = bitmap
                )
                
                _exportState.value = ExportState.Success(file!!)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
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
                spanX = lx * 1000, spanY = ly * 1000, thickness = thickness,
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