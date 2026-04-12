package com.civileg.app.ui.beam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.civileg.app.R
import com.civileg.app.databinding.FragmentBeamDesignBinding
import com.civileg.app.db.*
import com.civileg.app.domain.entities.*
import com.civileg.app.db.Project as DbProject
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.SettingsManager
import com.civileg.app.views.BeamSectionView
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class BeamDesignFragment : Fragment() {

    private var _binding: FragmentBeamDesignBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private val args: BeamDesignFragmentArgs by navArgs()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    private var lastResult: CalculatorEngine.BeamResult? = null
    private var lastInputData: JSONObject? = null
    private var projectsList: List<DbProject> = emptyList()
    private var selectedCode: DesignCode = DesignCode.ECP

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBeamDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            projectsList = projects
        }

        setupCodeSelection()
        setupCalculateButton()
        setupInitialDrawing()
        setupSaveButton()
    }

    private fun setupCodeSelection() {
        binding.codeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCode = when (checkedId) {
                    R.id.btnAmericanCode -> DesignCode.ACI
                    R.id.btnSaudiCode -> DesignCode.SBC
                    else -> DesignCode.ECP
                }
            }
        }
    }

    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            performCalculation()
        }
    }

    private fun setupInitialDrawing() {
        binding.beamSectionView.apply {
            beamWidth = 300f
            beamHeight = 600f
            cover = 40f
            topBars = listOf(BeamSectionView.BarInfo(2, 12))
            bottomBars = listOf(BeamSectionView.BarInfo(3, 16))
            invalidate()
        }
    }

    private fun performCalculation() {
        try {
            val width = binding.etBeamWidth.text.toString().toDoubleOrNull() ?: 250.0
            val height = binding.etBeamHeight.text.toString().toDoubleOrNull() ?: 600.0
            val length = binding.etBeamLength.text.toString().toDoubleOrNull() ?: 5000.0
            val fc = binding.etConcreteStrength.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etSteelStrength.text.toString().toDoubleOrNull() ?: 360.0
            val dl = binding.etDeadLoad.text.toString().toDoubleOrNull() ?: 15.0
            val ll = binding.etLiveLoad.text.toString().toDoubleOrNull() ?: 10.0

            val code = when(selectedCode) {
                DesignCode.ECP -> CalculatorEngine.DesignCode.EGYPTIAN
                DesignCode.ACI -> CalculatorEngine.DesignCode.ACI
                DesignCode.SBC -> CalculatorEngine.DesignCode.SAUDI
            }

            val result = calculatorEngine.designBeam(
                width = width,
                height = height,
                span = length / 1000.0,
                fcu = fc,
                fy = fy,
                deadLoad = dl,
                liveLoad = ll,
                preferredDiameter = 16,
                code = code
            )

            lastResult = result
            lastInputData = JSONObject().apply {
                put("width", width); put("height", height); put("length", length)
                put("fc", fc); put("fy", fy); put("dl", dl); put("ll", ll)
            }
            
            updateDrawing(result)
            displayResults(result)

            Toast.makeText(requireContext(), getString(R.string.calculation_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: getString(R.string.error))
        }
    }

    private fun updateDrawing(result: CalculatorEngine.BeamResult) {
        binding.beamSectionView.updateFromCalculation(
            width = result.width.toFloat(),
            height = result.depth.toFloat(),
            coverValue = 40f,
            topReinforcement = result.topBars.map { BeamSectionView.BarInfo(it.numBars, it.diameter) },
            bottomReinforcement = result.bottomBars.map { BeamSectionView.BarInfo(it.numBars, it.diameter) },
            momentCap = result.momentCapacity,
            shearCap = result.shearCapacity,
            appMoment = result.appliedMoment,
            appShear = result.appliedShear,
            naDepth = 0.0,
            ratio = result.steelRatio,
            safe = result.isSafe
        )
    }

    private fun displayResults(result: CalculatorEngine.BeamResult) {
        binding.resultsCard.visibility = View.VISIBLE
        val bottomBar = result.bottomBars.firstOrNull()
        binding.etBottomReinforcement.setText(if (bottomBar != null) "${bottomBar.numBars}Ø${bottomBar.diameter}" else "-")
        binding.etTopReinforcement.setText(String.format(Locale.getDefault(), "Cost: %.2f %s", result.cost, settingsManager.currency))
        
        val stirrupsText = result.stirrups.description
        binding.etStirrups.setText(stirrupsText)

        binding.etCodeUsed.setText(result.code.displayName)
        
        binding.root.post {
            binding.root.smoothScrollTo(0, binding.resultsCard.top)
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveDesign.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            val projectId = if (args.projectId != -1L) args.projectId else projectsList.firstOrNull()?.id ?: -1L
            if (projectId != -1L) {
                saveToProject(projectId, result)
            } else {
                showError("Please create a project first")
            }
        }
    }

    private fun saveToProject(projectId: Long, result: CalculatorEngine.BeamResult) {
        val design = Design(
            projectId = projectId, type = DesignType.BEAM,
            name = "Beam - ${System.currentTimeMillis() % 1000}",
            inputData = lastInputData.toString(),
            results = JSONObject().apply {
                val bottomBar = result.bottomBars.firstOrNull()
                put("mainSteel", if (bottomBar != null) "${bottomBar.numBars}Ø${bottomBar.diameter}" else "-")
                put("concreteVol", result.concreteVolume)
                put("cost", result.cost)
                put("currency", settingsManager.currency)
            }.toString(),
            isSafe = result.isSafe, codeUsed = result.code.displayName,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight,
            totalCost = result.cost
        )
        viewModel.saveDesign(design)
        
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Concrete (Beam)", unit = "m3", quantity = result.concreteVolume, unitPrice = settingsManager.concretePrice, totalPrice = result.concreteVolume * settingsManager.concretePrice, category = MaterialCategory.CONCRETE))
        viewModel.saveMaterial(MaterialItem(projectId = projectId, name = "Steel (Beam)", unit = "kg", quantity = result.steelWeight, unitPrice = settingsManager.steelPrice/1000, totalPrice = result.steelWeight * (settingsManager.steelPrice/1000), category = MaterialCategory.STEEL))

        Toast.makeText(context, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.error))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
