package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.*
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
    private val pdfExporter: ComprehensivePdfExporter
) : ViewModel() {
    
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
        alternatives: List<ColumnAlternative>
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Column_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                val file = pdfExporter.exportColumnReport(
                    projectName = projectName,
                    designCode = designCode,
                    columnType = columnType,
                    inputs = inputs,
                    result = result,
                    inventoryAnalysis = inventoryAnalysis,
                    alternatives = alternatives,
                    outputPath = outputFile.absolutePath
                )
                
                _exportState.value = ExportState.Success(file)
                
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
        diagrams: MomentShearDiagrams
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Beam_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                val file = pdfExporter.exportBeamReport(
                    projectName = projectName,
                    designCode = designCode,
                    beamType = beamType,
                    inputs = inputs,
                    result = result,
                    inventoryAnalysis = inventoryAnalysis,
                    momentShearDiagrams = diagrams,
                    outputPath = outputFile.absolutePath
                )
                
                _exportState.value = ExportState.Success(file)
                
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
        result: AdvancedSlabResult
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Slab_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                val file = pdfExporter.exportSlabReport(
                    projectName = projectName,
                    designCode = designCode,
                    slabType = slabType,
                    inputs = inputs,
                    result = result,
                    outputPath = outputFile.absolutePath
                )
                
                _exportState.value = ExportState.Success(file)
                
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
        connectionDesign: ConnectionDesignResult?
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            
            try {
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fileName = "${projectName.replace(" ", "_")}_Steel_${System.currentTimeMillis()}.pdf"
                val outputFile = File(outputDir, fileName)
                
                val file = pdfExporter.exportSteelReport(
                    projectName = projectName,
                    designCode = designCode,
                    sectionType = sectionType,
                    memberType = memberType,
                    inputs = inputs,
                    result = result,
                    connectionDesign = connectionDesign,
                    outputPath = outputFile.absolutePath
                )
                
                _exportState.value = ExportState.Success(file)
                
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Export failed")
            }
        }
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
