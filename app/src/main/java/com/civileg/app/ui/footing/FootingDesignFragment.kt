package com.civileg.app.ui.footing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.civileg.app.R
import com.civileg.app.databinding.FragmentFootingDesignBinding
import com.civileg.app.db.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import com.civileg.app.utils.SettingsManager
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class FootingDesignFragment : Fragment() {

    private var _binding: FragmentFootingDesignBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProjectViewModel by viewModels()
    private val args: FootingDesignFragmentArgs by navArgs()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    private var lastResult: CalculatorEngine.FootingResult? = null
    private var lastInputData: JSONObject? = null
    private var projectsList: List<Project> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFootingDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            projectsList = projects
        }
        
        setupCalculateButton()
        setupInitialDrawing()
        setupSaveButton()
        setupExportButton()
        
        binding.btnCloseResults.setOnClickListener {
            binding.cardResults.visibility = View.GONE
        }
    }

    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            calculateFooting()
        }
    }

    private fun setupInitialDrawing() {
        binding.footingView.apply {
            footingLength = 2000f
            footingWidth = 2000f
            footingThickness = 400f
            columnLength = 600f
            columnWidth = 300f
            invalidate()
        }
    }

    private fun calculateFooting() {
        try {
            val cl = binding.etColLength.text.toString().toDoubleOrNull() ?: 600.0
            val cw = binding.etColWidth.text.toString().toDoubleOrNull() ?: 300.0
            val p = binding.etAxialLoad.text.toString().toDoubleOrNull() ?: throw Exception(getString(R.string.check_inputs))
            val sp = binding.etSoilCapacity.text.toString().toDoubleOrNull() ?: throw Exception(getString(R.string.check_inputs))
            val fc = binding.etFc.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etFy.text.toString().toDoubleOrNull() ?: 360.0

            val result = calculatorEngine.calculateFooting(
                p = p, fcu = fc, fy = fy, soil = sp, colB = cw, colT = cl,
                code = CalculatorEngine.DesignCode.EGYPTIAN
            )

            lastResult = result
            lastInputData = JSONObject().apply {
                put("axialLoad", p); put("soilCapacity", sp); put("fc", fc); put("fy", fy); put("colL", cl); put("colW", cw)
            }
            
            showResults(result)
            updateDrawing(result, cl.toFloat(), cw.toFloat())
            Toast.makeText(requireContext(), getString(R.string.calculation_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: getString(R.string.error))
        }
    }

    private fun updateDrawing(result: CalculatorEngine.FootingResult, cl: Float, cw: Float) {
        binding.footingView.updateFromCalculation(
            fL = result.length.toFloat(), fW = result.width.toFloat(), fT = result.thickness.toFloat(),
            cL = cl, cW = cw, bX = result.barsX, bY = result.barsY, bDia = result.barDiameter,
            sPressure = result.soilPressure, aPressure = result.allowablePressure,
            pStress = 0.0, aShear = 0.0, safe = result.isSafe
        )
    }

    private fun showResults(result: CalculatorEngine.FootingResult) {
        binding.cardResults.visibility = View.VISIBLE
        binding.tvFootingDim.text = String.format(Locale.getDefault(), "%.0f x %.0f mm", result.width, result.length)
        binding.tvThickness.text = String.format(Locale.getDefault(), "%.0f mm", result.thickness)
        binding.tvReinforcement.text = String.format(Locale.getDefault(), "%dØ%d", result.barsX, result.barDiameter)
        binding.tvConcreteVol.text = String.format(Locale.getDefault(), "%.2f m³", result.concreteVolume)
        binding.tvSteelWeight.text = String.format(Locale.getDefault(), "%.1f kg", result.steelWeight)
        
        binding.tvStatus.text = if (result.isSafe) getString(R.string.status_safe) else getString(R.string.status_unsafe)
        val statusColor = if (result.isSafe) ContextCompat.getColor(requireContext(), R.color.success) else ContextCompat.getColor(requireContext(), R.color.danger)
        binding.tvStatus.setTextColor(statusColor)
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            val projectId = args.projectId
            if (projectId != -1L) {
                saveToProject(projectId, result)
            } else {
                Toast.makeText(requireContext(), "Please select a project first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupExportButton() {
        binding.btnExportPdf.setOnClickListener {
            val res = lastResult ?: return@setOnClickListener
            val inputs = mapOf("Load" to "${res.soilPressure} kN/m2", "Soil" to "${res.allowablePressure} kPa")
            val results = mapOf("Size" to "${res.width}x${res.length}", "Concrete" to "${res.concreteVolume} m3")
            try {
                val file = PdfGenerator.generateDesignReport(requireContext(), "Footing Report", "FOOTING", inputs, results, res.isSafe)
                ExportUtils.openPdf(requireContext(), file)
            } catch (e: Exception) { showError("PDF Error: ${e.message}") }
        }
    }

    private fun saveToProject(projectId: Long, result: CalculatorEngine.FootingResult) {
        val design = Design(
            projectId = projectId, type = DesignType.FOOTING,
            name = "Footing Design - ${System.currentTimeMillis() % 1000}",
            inputData = lastInputData.toString(),
            results = result.toString(),
            isSafe = result.isSafe, codeUsed = result.code.displayName,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, totalCost = result.cost
        )
        viewModel.saveDesign(design)
        Toast.makeText(context, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.error))
            .setMessage(message)
            .setPositiveButton(R.string.ok, null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
