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
import com.civileg.app.R
import com.civileg.app.databinding.FragmentWaterTankBinding
import com.civileg.app.db.*
import com.civileg.app.db.Project as DbProject
import com.civileg.app.domain.calculations.base.TankType as DomainTankType
import com.civileg.app.domain.calculations.base.TankResult as DomainTankResult
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
    private val projectId: Long
        get() = requireArguments().getLong("projectId", -1L)
    
    @Inject
    lateinit var settingsManager: SettingsManager

    private var lastResult: DomainTankResult? = null
    private var lastInputData: JSONObject? = null
    private var projectsList: List<DbProject> = emptyList()
    private var selectedType = DomainTankType.RECTANGULAR_GROUND
    
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
                R.id.rbCircular -> DomainTankType.CIRCULAR_GROUND
                R.id.rbElevated -> DomainTankType.RECTANGULAR_ELEVATED
                R.id.rbUnderground -> DomainTankType.RECTANGULAR_UNDERGROUND
                else -> DomainTankType.RECTANGULAR_GROUND
            }
            
            if (selectedType == DomainTankType.CIRCULAR_GROUND || 
                selectedType == DomainTankType.CIRCULAR_ELEVATED ||
                selectedType == DomainTankType.CIRCULAR_UNDERGROUND) {
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
        // Legacy engine removed — use TankScreen (Compose) with code-specific ECPTank/ACITank/SBCTank
        showError("Tank design has been migrated. Please use the updated Tank Design screen.")
    }

    private fun updateTankView(result: DomainTankResult) {
        binding.tankView.update(
            l = (lastInputData?.optDouble("length") ?: 5000.0).toFloat(),
            h = (lastInputData?.optDouble("height") ?: 3000.0).toFloat(),
            t = result.wallThickness.toFloat(),
            bt = result.baseThickness.toFloat(),
            wd = (lastInputData?.optDouble("waterHeight") ?: 2500.0).toFloat(),
            type = if (selectedType.name.contains("CIRCULAR")) CalculatorEngine.TankType.CIRCULAR_GROUND else if (selectedType.name.contains("UNDERGROUND")) CalculatorEngine.TankType.UNDERGROUND else CalculatorEngine.TankType.RECTANGULAR_GROUND,
            rebarText = result.wallReinforcement.barString,
            baseRebar = result.baseReinforcement.barString,
            safe = result.isSafe
        )
    }
    
    private fun showResults(result: DomainTankResult) {
        binding.cardResults.visibility = View.VISIBLE
        
        binding.tvCapacity.text = String.format(Locale.getDefault(), "%.1f m³", result.capacityM3)
        binding.tvSteelArea.text = result.wallReinforcement.barString
        binding.tvPressure.text = String.format(Locale.getDefault(), "%.1f kPa", result.pressure)
        binding.tvConcreteVol.text = String.format(Locale.getDefault(), "%.2f m³", result.concreteVolume)
        binding.tvTotalCost.text = String.format(Locale.getDefault(), "%.2f %s", result.cost, settingsManager.currency)
        
        val safetyInfo = result.safetyChecks.joinToString("\n") { 
            "${it.name}: ${String.format("%.2f", it.value)} / ${it.limit} ${it.unit} ${if(it.isSafe) "✓" else "✗"}"
        }
        binding.tvSafetyCheck.text = safetyInfo
        binding.tvSafetyCheck.setTextColor(if(result.isSafe) ContextCompat.getColor(requireContext(), R.color.success) else ContextCompat.getColor(requireContext(), R.color.danger))
        
        binding.tvAlternatives.text = result.structuralSystem
        
        binding.tvWaterTightStatus.text = if (result.isSafe) "DESIGN SAFE" else "UNSAFE DESIGN"
        val statusColor = if (result.isSafe) ContextCompat.getColor(requireContext(), R.color.success) else ContextCompat.getColor(requireContext(), R.color.danger)
        binding.tvWaterTightStatus.setTextColor(statusColor)
        binding.tvWaterTightStatus.setBackgroundColor(Color.argb(30, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor)))
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            val projectId = if (this@WaterTankFragment.projectId != -1L) this@WaterTankFragment.projectId else {
                if (projectsList.isEmpty()) {
                    showError("Please create a project first")
                    return@setOnClickListener
                }
                projectsList.first().id
            }
            saveToProject(projectId, result)
        }
    }

    private fun saveToProject(projectId: Long, result: DomainTankResult) {
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
            if (selectedType.name.contains("CIRCULAR")) {
                inputs["Diameter"] = "${lastInputData?.optDouble("diameter")} mm"
            } else {
                inputs["Length"] = "${lastInputData?.optDouble("length")} mm"
                inputs["Width"] = "${lastInputData?.optDouble("width")} mm"
            }
            inputs["Height"] = "${lastInputData?.optDouble("height")} mm"
            
            val results = mapOf(
                "Type" to selectedType.name,
                "Capacity" to String.format(Locale.getDefault(), "%.1f m3", res.capacityM3),
                "Concrete Vol" to String.format(Locale.getDefault(), "%.2f m3", res.concreteVolume),
                "Estimated Cost" to String.format(Locale.getDefault(), "%.2f %s", res.cost, settingsManager.currency),
                "Status" to if(res.isSafe) "SAFE" else "UNSAFE"
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
