package com.civileg.app.ui.seismic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.civileg.app.R
import com.civileg.app.databinding.FragmentSeismicAnalysisBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import com.civileg.app.utils.ExcelExporter
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class SeismicAnalysisFragment : Fragment() {
    
    private var _binding: FragmentSeismicAnalysisBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var calculatorEngine: CalculatorEngine

    private var lastResult: CalculatorEngine.SeismicResult? = null
    private var lastInput: CalculatorEngine.SeismicInput? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeismicAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSpinners()
        setupCalculateButton()
        setupExportButton()
    }
    
    private fun setupSpinners() {
        val zones = arrayOf("Zone 1 (0.075)", "Zone 2A (0.15)", "Zone 2B (0.20)", "Zone 3 (0.30)", "Zone 4 (0.40)")
        val zoneAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, zones)
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerZone.adapter = zoneAdapter

        val soilTypes = arrayOf("Type A (Rock)", "Type B (Dense)", "Type C (Medium)", "Type D (Soft)")
        val soilAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, soilTypes)
        soilAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSoilType.adapter = soilAdapter
    }
    
    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            calculateSeismic()
        }
    }
    
    private fun calculateSeismic() {
        try {
            val height = binding.etBuildingHeight.text.toString().toDoubleOrNull() ?: throw Exception(getString(R.string.check_inputs))
            val numFloors = binding.etNumFloors.text.toString().toIntOrNull() ?: throw Exception(getString(R.string.check_inputs))
            val weightPerFloor = binding.etBaseArea.text.toString().toDoubleOrNull() ?: 500.0
            
            val zoneVal = when (binding.spinnerZone.selectedItemPosition) {
                0 -> 0.075; 1 -> 0.15; 2 -> 0.20; 3 -> 0.30; else -> 0.40
            }
            
            val importance = 1.0
            val soil = binding.spinnerSoilType.selectedItem.toString()

            val input = CalculatorEngine.SeismicInput(
                zone = zoneVal,
                importance = importance,
                soilType = soil,
                height = height,
                totalWeight = weightPerFloor * numFloors,
                systemType = "Bearing Wall System"
            )

            val result = calculatorEngine.calculateSeismicLoads(input)
            lastInput = input
            lastResult = result

            showResults(result)
        } catch (e: Exception) {
            showError("${getString(R.string.error)}: ${e.message}")
        }
    }
    
    private fun showResults(result: CalculatorEngine.SeismicResult) {
        binding.cardResults.visibility = View.VISIBLE
        
        binding.tvPeriod.text = String.format(Locale.getDefault(), "Fundamental Period (T): %.3f sec", result.timePeriod)
        binding.tvSpectralAccel.text = String.format(Locale.getDefault(), "Spectral Accel (Sa/g): %.3f", result.spectralAcceleration)
        binding.tvBaseShear.text = String.format(Locale.getDefault(), "Base Shear (V): %.2f kN", result.baseShear)
        
        val forcesText = result.forcesPerFloor.entries.joinToString("\n") { (floor, force) ->
            "Floor $floor: ${String.format(Locale.getDefault(), "%.2f", force)} kN"
        }
        
        binding.tvFloorForces.text = "Vertical Distribution:\n$forcesText"
    }

    private fun setupExportButton() {
        binding.btnExportPdf.setOnClickListener {
            val res = lastResult ?: return@setOnClickListener
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Export Seismic Report")
                .setItems(arrayOf("PDF Official Report", "Excel (CSV) Data")) { _, which ->
                    when (which) {
                        0 -> exportPdf(res)
                        1 -> exportExcel(res)
                    }
                }.show()
        }
    }

    private fun exportPdf(res: CalculatorEngine.SeismicResult) {
        val inputs = mapOf(
            "Zone Factor" to "${lastInput?.zone}",
            "Importance (I)" to "${lastInput?.importance}",
            "Height" to "${lastInput?.height} m",
            "Total Weight" to "${lastInput?.totalWeight} kN"
        )
        val results = mapOf(
            "Base Shear (V)" to "${res.baseShear} kN",
            "Time Period (T)" to "${res.timePeriod} sec",
            "Spectral Accel" to "${res.spectralAcceleration}"
        )
        try {
            val file = PdfGenerator.generateDesignReport(requireContext(), "Seismic Analysis (Static Method)", "SEISMIC", inputs, results, true)
            ExportUtils.openPdf(requireContext(), file)
        } catch (e: Exception) { showError("PDF failed: ${e.message}") }
    }

    private fun exportExcel(res: CalculatorEngine.SeismicResult) {
        val design = Design(
            projectId = 0, type = DesignType.SEISMIC,
            name = "Seismic_Analysis",
            inputData = JSONObject().apply { put("zone", lastInput?.zone); put("height", lastInput?.height) }.toString(),
            results = JSONObject().apply { put("baseShear", res.baseShear); put("period", res.timePeriod) }.toString(),
            isSafe = true, codeUsed = "ECP-201",
            concreteVolume = 0.0, steelWeight = 0.0, totalCost = 0.0
        )
        val file = ExcelExporter.exportDesignToCsv(requireContext(), design)
        if (file != null) ExportUtils.openPdf(requireContext(), file)
    }
    
    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.error))
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
