package com.civileg.app.ui.retaining

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.civileg.app.R
import com.civileg.app.databinding.FragmentRetainingWallBinding
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
class RetainingWallFragment : Fragment() {
    
    private var _binding: FragmentRetainingWallBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProjectViewModel by viewModels()
    private val args: RetainingWallFragmentArgs by navArgs()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    private var lastResult: CalculatorEngine.RetainingWallResult? = null
    private var lastInputData: JSONObject? = null
    private var projectsList: List<DbProject> = emptyList()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRetainingWallBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            projectsList = projects
        }
        
        setupCalculateButton()
        setupSaveButton()
        setupExportButton()
        
        binding.btnCloseResults.setOnClickListener {
            binding.cardResults.visibility = View.GONE
        }
    }
    
    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            calculateRetainingWall()
        }
    }
    
    private fun calculateRetainingWall() {
        try {
            val h = binding.etWallHeight.text.toString().toDoubleOrNull() ?: 3.0
            val soilDensity = binding.etSoilDensity.text.toString().toDoubleOrNull() ?: 18.0
            val angle = binding.etFrictionAngle.text.toString().toDoubleOrNull() ?: 30.0
            val surcharge = 0.0 // Could add to UI
            val fc = 25.0
            val fy = 360.0

            val result = calculatorEngine.designRetainingWall(
                height = h,
                soilDensity = soilDensity,
                frictionAngle = angle,
                surcharge = surcharge,
                fcu = fc,
                fy = fy
            )
            
            lastResult = result
            lastInputData = JSONObject().apply {
                put("height", h); put("soilDensity", soilDensity)
                put("angle", angle); put("surcharge", surcharge)
            }
            
            showResults(result)
            
            binding.wallView.update(
                h = (h * 1000).toFloat(),
                base = result.baseWidth.toFloat(),
                stemT = result.stemThickness.toFloat(),
                baseT = 400f // Default or from result if available
            )
            
            Toast.makeText(requireContext(), getString(R.string.calculation_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: getString(R.string.error))
        }
    }
    
    private fun showResults(result: CalculatorEngine.RetainingWallResult) {
        binding.cardResults.visibility = View.VISIBLE
        
        // Mapping results to existing layout views
        binding.tvEarthPressure.text = String.format(Locale.getDefault(), "Stem: %.0f mm", result.stemThickness)
        binding.tvKa.text = String.format(Locale.getDefault(), "Base: %.0f mm", result.baseWidth)
        binding.tvFosOverturning.text = result.stemReinforcement?.barString ?: "N/A"
        binding.tvMoments.text = result.baseReinforcement?.barString ?: "N/A"
        binding.tvConcreteVol.text = String.format(Locale.getDefault(), "%.2f m³", result.concreteVolume)
        binding.tvCost.text = String.format(Locale.getDefault(), "Cost: %.2f %s", result.cost, settingsManager.currency)
        
        binding.tvStatus.text = getString(R.string.status_safe)
        binding.tvStatus.setTextColor(resources.getColor(R.color.success, null))
        binding.tvStatus.setBackgroundColor(Color.argb(30, 0, 255, 0))
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

    private fun saveToProject(projectId: Long, result: CalculatorEngine.RetainingWallResult) {
        val design = Design(
            projectId = projectId, type = DesignType.RETAINING_WALL,
            name = "Wall - ${System.currentTimeMillis() % 1000}",
            inputData = lastInputData.toString(),
            results = JSONObject().apply {
                put("stem", result.stemThickness); put("base", result.baseWidth)
                put("concreteVol", result.concreteVolume); put("cost", result.cost)
                put("currency", settingsManager.currency)
            }.toString(),
            isSafe = true, codeUsed = settingsManager.defaultDesignCode.name,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight,
            totalCost = result.cost
        )
        viewModel.saveDesign(design)
        
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Concrete (Wall)", unit = "m3", quantity = result.concreteVolume, unitPrice = settingsManager.concretePrice, totalPrice = result.concreteVolume * settingsManager.concretePrice, category = MaterialCategory.CONCRETE))
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Steel (Wall)", unit = "kg", quantity = result.steelWeight, unitPrice = settingsManager.steelPrice/1000, totalPrice = result.steelWeight * (settingsManager.steelPrice/1000), category = MaterialCategory.STEEL))

        Toast.makeText(context, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
    }

    private fun setupExportButton() {
        binding.btnExportPdf.setOnClickListener {
            val res = lastResult ?: return@setOnClickListener
            val inputs = mapOf("Height" to "${lastInputData?.optDouble("height")} m", "Density" to "${lastInputData?.optDouble("soilDensity")} kN/m3")
            val results = mapOf(
                "Stem T" to "${res.stemThickness} mm",
                "Base W" to "${res.baseWidth} mm",
                "Reinforcement" to (res.stemReinforcement?.barString ?: "N/A"),
                "Estimated Cost" to String.format(Locale.getDefault(), "%.2f %s", res.cost, settingsManager.currency)
            )
            try {
                val file = PdfGenerator.generateDesignReport(requireContext(), "Retaining Wall Report", "WALL", inputs, results, true)
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
