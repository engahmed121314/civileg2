package com.civileg.app.ui.watertank

import android.graphics.Color
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
import com.civileg.app.databinding.FragmentWaterTankBinding
import com.civileg.app.db.*
import com.civileg.app.db.Project as DbProject
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
import kotlin.math.PI
import kotlin.math.pow

@AndroidEntryPoint
class WaterTankFragment : Fragment() {
    
    private var _binding: FragmentWaterTankBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProjectViewModel by viewModels()
    private val args: WaterTankFragmentArgs by navArgs()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    private var lastResult: CalculatorEngine.TankResult? = null
    private var lastInputData: JSONObject? = null
    private var projectsList: List<DbProject> = emptyList()
    private var selectedType = CalculatorEngine.TankType.RECTANGULAR_GROUND
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterTankBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            projectsList = projects
        }
        
        setupTypeSelector()
        setupCalculateButton()
        setupSaveButton()
        setupExportButton()
        
        binding.btnCloseResults.setOnClickListener {
            binding.cardResults.visibility = View.GONE
        }
    }

    private fun setupTypeSelector() {
        binding.rgTankType.setOnCheckedChangeListener { _, checkedId ->
            selectedType = when (checkedId) {
                R.id.rbCircular -> CalculatorEngine.TankType.CIRCULAR_GROUND
                R.id.rbElevated -> CalculatorEngine.TankType.RECTANGULAR_ELEVATED
                R.id.rbUnderground -> CalculatorEngine.TankType.RECTANGULAR_GROUND // Assuming underground is rectangular ground for now
                else -> CalculatorEngine.TankType.RECTANGULAR_GROUND
            }
            
            if (selectedType == CalculatorEngine.TankType.CIRCULAR_GROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_ELEVATED) {
                binding.layoutRectDimensions.visibility = View.GONE
                binding.layoutDiameter.visibility = View.VISIBLE
            } else {
                binding.layoutRectDimensions.visibility = View.VISIBLE
                binding.layoutDiameter.visibility = View.GONE
            }
        }
    }
    
    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            calculateWaterTank()
        }
    }
    
    private fun calculateWaterTank() {
        try {
            val length = binding.etLength.text.toString().toDoubleOrNull() ?: 5.0
            val width = binding.etWidth.text.toString().toDoubleOrNull() ?: 4.0
            val diameter = binding.etDiameter.text.toString().toDoubleOrNull() ?: 5.0
            val height = binding.etHeight.text.toString().toDoubleOrNull() ?: 3.0
            val waterHeight = binding.etWaterHeight.text.toString().toDoubleOrNull() ?: 2.5
            
            val capacity = if (selectedType == CalculatorEngine.TankType.CIRCULAR_GROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_ELEVATED) {
                PI * (diameter/2.0).pow(2) * waterHeight
            } else {
                length * width * waterHeight
            }

            val result = calculatorEngine.designTank(
                type = selectedType,
                capacity = capacity,
                height = height,
                fcu = 25.0,
                fy = 360.0
            )
            
            lastResult = result
            lastInputData = JSONObject().apply {
                put("type", selectedType.name)
                put("length", length); put("width", width)
                put("diameter", diameter)
                put("height", height); put("waterHeight", waterHeight)
            }
            
            showResults(result)
            
            Toast.makeText(requireContext(), getString(R.string.calculation_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: getString(R.string.error))
        }
    }
    
    private fun showResults(result: CalculatorEngine.TankResult) {
        binding.cardResults.visibility = View.VISIBLE
        
        binding.tvCapacity.text = String.format(Locale.getDefault(), "%.1f m³", result.capacityM3)
        binding.tvSteelArea.text = result.wallReinforcement?.barString ?: "N/A"
        binding.tvPressure.text = String.format(Locale.getDefault(), "%.1f kPa", result.pressure)
        binding.tvConcreteVol.text = String.format(Locale.getDefault(), "%.2f m³", result.concreteVolume)
        binding.tvTotalCost.text = String.format(Locale.getDefault(), "%.2f %s", result.cost, settingsManager.currency)
        
        binding.tvSafetyCheck.text = result.safetyCheck
        binding.tvSafetyCheck.setTextColor(if(result.isSafe) ContextCompat.getColor(requireContext(), R.color.success) else ContextCompat.getColor(requireContext(), R.color.danger))
        
        // Alternatives
        val alternativesText = result.alternatives
            .joinToString(" or ") { it.wallReinforcement?.barString ?: "N/A" }
        binding.tvAlternatives.text = if (alternativesText.isNotEmpty()) alternativesText else "No practical alternatives"
        
        binding.tvWaterTightStatus.text = if (result.isSafe) getString(R.string.status_safe) else getString(R.string.status_unsafe)
        val statusColor = if (result.isSafe) ContextCompat.getColor(requireContext(), R.color.success) else ContextCompat.getColor(requireContext(), R.color.danger)
        binding.tvWaterTightStatus.setTextColor(statusColor)
        binding.tvWaterTightStatus.setBackgroundColor(Color.argb(30, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor)))
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            val projectId = if (args.projectId != -1L) args.projectId else {
                if (projectsList.isEmpty()) {
                    showError("Please create a project first")
                    return@setOnClickListener
                }
                projectsList.first().id
            }
            saveToProject(projectId, result)
        }
    }

    private fun saveToProject(projectId: Long, result: CalculatorEngine.TankResult) {
        val design = Design(
            projectId = projectId, type = DesignType.WATER_TANK,
            name = "Tank (${selectedType.name}) - ${System.currentTimeMillis() % 1000}",
            inputData = lastInputData.toString(),
            results = JSONObject().apply {
                put("capacity", result.capacityM3)
                put("cost", result.cost)
                put("concreteVol", result.concreteVolume)
                put("currency", settingsManager.currency)
            }.toString(),
            isSafe = result.isSafe, codeUsed = settingsManager.defaultDesignCode.name,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight,
            totalCost = result.cost
        )
        viewModel.saveDesign(design)
        
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Concrete (Tank)", unit = "m3", quantity = result.concreteVolume, unitPrice = settingsManager.concretePrice, totalPrice = result.concreteVolume * settingsManager.concretePrice, category = MaterialCategory.CONCRETE))
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Steel (Tank)", unit = "kg", quantity = result.steelWeight, unitPrice = settingsManager.steelPrice/1000, totalPrice = result.steelWeight * (settingsManager.steelPrice/1000), category = MaterialCategory.STEEL))

        Toast.makeText(context, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
    }

    private fun setupExportButton() {
        binding.btnExportPdf.setOnClickListener {
            val res = lastResult ?: return@setOnClickListener
            val inputs = mutableMapOf<String, String>()
            if (selectedType == CalculatorEngine.TankType.CIRCULAR_GROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_ELEVATED) {
                inputs["Diameter"] = "${lastInputData?.optDouble("diameter")} m"
            } else {
                inputs["Length"] = "${lastInputData?.optDouble("length")} m"
                inputs["Width"] = "${lastInputData?.optDouble("width")} m"
            }
            inputs["Height"] = "${lastInputData?.optDouble("height")} m"
            
            val results = mapOf(
                "Type" to selectedType.name,
                "Capacity" to String.format(Locale.getDefault(), "%.1f m3", res.capacityM3),
                "Concrete Vol" to String.format(Locale.getDefault(), "%.2f m3", res.concreteVolume),
                "Estimated Cost" to String.format(Locale.getDefault(), "%.2f %s", res.cost, settingsManager.currency),
                "Check" to res.safetyCheck
            )
            try {
                val file = PdfGenerator.generateDesignReport(requireContext(), "Water Tank Report", "TANK", inputs, results, res.isSafe)
                ExportUtils.openPdf(requireContext(), file)
            } catch (e: Exception) { showError("Export failed") }
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext()).setTitle("Error").setMessage(message).setPositiveButton("OK", null).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
