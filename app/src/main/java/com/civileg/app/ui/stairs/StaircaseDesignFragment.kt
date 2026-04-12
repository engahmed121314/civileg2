package com.civileg.app.ui.stairs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.civileg.app.R
import com.civileg.app.databinding.FragmentStaircaseDesignBinding
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

@AndroidEntryPoint
class StaircaseDesignFragment : Fragment() {
    
    private var _binding: FragmentStaircaseDesignBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProjectViewModel by viewModels()
    private val args: StaircaseDesignFragmentArgs by navArgs()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    private var lastResult: CalculatorEngine.StairResult? = null
    private var lastInputData: JSONObject? = null
    private var projectsList: List<DbProject> = emptyList()
    private var selectedType = CalculatorEngine.StairType.SINGLE_FLIGHT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStaircaseDesignBinding.inflate(inflater, container, false)
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
        
        updateStairView()
    }

    private fun setupTypeSelector() {
        binding.rgStairType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbSingleFlight -> {
                    selectedType = CalculatorEngine.StairType.SINGLE_FLIGHT
                    binding.layoutLandingWidth.visibility = View.GONE
                    binding.layoutSteps.visibility = View.VISIBLE
                }
                R.id.rbDoubleFlight -> {
                    selectedType = CalculatorEngine.StairType.DOUBLE_FLIGHT
                    binding.layoutLandingWidth.visibility = View.VISIBLE
                    binding.layoutSteps.visibility = View.VISIBLE
                }
                R.id.rbCantilever -> {
                    selectedType = CalculatorEngine.StairType.SPIRAL // Use spiral for cantilever as fallback
                    binding.layoutLandingWidth.visibility = View.GONE
                    binding.layoutSteps.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            calculateStaircase()
        }
    }

    private fun updateStairView() {
        try {
            val r = binding.etRiser.text.toString().toFloatOrNull() ?: 150f
            val t = binding.etTread.text.toString().toFloatOrNull() ?: 300f
            val s = binding.etSteps.text.toString().toIntOrNull() ?: 12
            
            if (r > 0 && t > 0 && s > 0) {
                binding.stairView.riserHeight = r
                binding.stairView.treadWidth = t
                binding.stairView.numSteps = s
                binding.stairView.stairType = selectedType
                binding.stairView.invalidate()
            }
        } catch (e: Exception) {}
    }
    
    private fun calculateStaircase() {
        try {
            val riser = binding.etRiser.text.toString().toDoubleOrNull() ?: 150.0
            val tread = binding.etTread.text.toString().toDoubleOrNull() ?: 300.0
            val width = binding.etWidth.text.toString().toDoubleOrNull() ?: 1200.0
            val steps = binding.etSteps.text.toString().toIntOrNull() ?: 12
            val landingWidth = binding.etLandingWidth.text.toString().toDoubleOrNull() ?: 1.2
            val fc = binding.etConcreteStrength.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etSteelStrength.text.toString().toDoubleOrNull() ?: 360.0
            val load = binding.etLiveLoad.text.toString().toDoubleOrNull() ?: 3.0

            val result = calculatorEngine.designStaircase(
                type = selectedType,
                span = steps * tread / 1000.0 + (if(selectedType == CalculatorEngine.StairType.DOUBLE_FLIGHT) landingWidth else 0.0),
                riser = riser,
                tread = tread,
                deadLoad = load * 0.8,
                liveLoad = load,
                fcu = fc,
                fy = fy,
                preferredDiameter = 12,
                code = CalculatorEngine.DesignCode.EGYPTIAN
            )
            
            lastResult = result
            lastInputData = JSONObject().apply {
                put("type", selectedType.name)
                put("riser", riser); put("tread", tread); put("width", width)
                put("steps", steps); put("landingWidth", landingWidth)
                put("fc", fc); put("fy", fy); put("load", load)
            }
            
            showResults(result)
            
            // Update view with reinforcement details
            binding.stairView.apply {
                riserHeight = riser.toFloat()
                treadWidth = tread.toFloat()
                numSteps = steps
                stairType = selectedType
                mainReinforcementText = result.reinforcement.barString
                concreteVolume = result.concreteVolume.toFloat()
                isSafe = result.isSafe
                invalidate()
            }

            Toast.makeText(requireContext(), getString(R.string.calculation_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: getString(R.string.error))
        }
    }
    
    private fun showResults(result: CalculatorEngine.StairResult) {
        binding.cardResults.visibility = View.VISIBLE
        
        binding.tvMainReinforcement.text = result.reinforcement.barString
        binding.tvStairLength.text = String.format(Locale.getDefault(), "Cost: %.2f %s", result.cost, settingsManager.currency)
        binding.tvMoment.text = String.format(Locale.getDefault(), "Vol: %.2f m³", result.concreteVolume)
        
        binding.tvOptimizationTip.text = "Optimized Design"
        binding.tvStairAlternatives.text = result.distributionReinforcement.barString

        binding.root.post {
            binding.root.smoothScrollTo(0, binding.cardResults.top)
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            val projectId = if (args.projectId != -1L) args.projectId else projectsList.firstOrNull()?.id ?: -1L
            if (projectId != -1L) {
                saveToProject(projectId, result)
            } else {
                showError("Please create a project first")
            }
        }
    }

    private fun saveToProject(projectId: Long, result: CalculatorEngine.StairResult) {
        val design = Design(
            projectId = projectId, type = DesignType.STAIRCASE,
            name = "Staircase (${selectedType.name}) - ${System.currentTimeMillis() % 1000}",
            inputData = lastInputData.toString(),
            results = JSONObject().apply {
                put("reinforcement", result.reinforcement.barString)
                put("concreteVol", result.concreteVolume)
                put("cost", result.cost)
                put("currency", settingsManager.currency)
            }.toString(),
            isSafe = result.isSafe, codeUsed = result.code.displayName,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight,
            totalCost = result.cost
        )
        viewModel.saveDesign(design)
        
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Concrete (Stairs)", unit = "m3", quantity = result.concreteVolume, unitPrice = settingsManager.concretePrice, totalPrice = result.concreteVolume * settingsManager.concretePrice, category = MaterialCategory.CONCRETE))
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Steel (Stairs)", unit = "kg", quantity = result.steelWeight, unitPrice = settingsManager.steelPrice/1000, totalPrice = result.steelWeight * (settingsManager.steelPrice/1000), category = MaterialCategory.STEEL))

        Toast.makeText(context, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
    }

    private fun setupExportButton() {
        binding.btnExportPdf.setOnClickListener {
            val res = lastResult ?: return@setOnClickListener
            val inputs = mutableMapOf(
                "Type" to selectedType.name,
                "Riser" to "${lastInputData?.optDouble("riser")} mm",
                "Tread" to "${lastInputData?.optDouble("tread")} mm",
                "Steps" to "${lastInputData?.optInt("steps")}"
            )
            if (selectedType == CalculatorEngine.StairType.DOUBLE_FLIGHT) {
                inputs["Landing Width"] = "${lastInputData?.optDouble("landingWidth")} m"
            }

            val results = mapOf(
                "Reinforcement" to res.reinforcement.barString,
                "Concrete Vol" to String.format(Locale.getDefault(), "%.2f m3", res.concreteVolume),
                "Estimated Cost" to String.format(Locale.getDefault(), "%.2f %s", res.cost, settingsManager.currency)
            )
            try {
                val file = PdfGenerator.generateDesignReport(requireContext(), "Staircase Design Report", "STAIRS", inputs, results, res.isSafe)
                ExportUtils.openPdf(requireContext(), file)
            } catch (e: Exception) { showError("Export failed: ${e.message}") }
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
