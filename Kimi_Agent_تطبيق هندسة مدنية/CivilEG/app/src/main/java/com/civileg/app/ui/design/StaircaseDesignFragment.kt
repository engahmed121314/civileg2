package com.civileg.app.ui.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.civileg.app.R
import com.civileg.app.databinding.FragmentStaircaseDesignBinding
import com.civileg.app.ui.base.BaseFragment
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import kotlinx.coroutines.launch

/**
 * Staircase Design Fragment with Interactive Drawing
 * Shows elevation view with steps, landings, and reinforcement
 */
class StaircaseDesignFragment : BaseFragment() {

    private var _binding: FragmentStaircaseDesignBinding? = null
    private val binding get() = _binding!!

    private lateinit var calculator: CalculatorEngine
    private var selectedCode: DesignCode = DesignCode.EGYPTIAN

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

        calculator = CalculatorEngine()

        setupCodeSelection()
        setupCalculateButton()
        setupInitialDrawing()
    }

    private fun setupCodeSelection() {
        binding.codeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCode = when (checkedId) {
                    R.id.btnAmericanCode -> DesignCode.ACI
                    R.id.btnSaudiCode -> DesignCode.SBC
                    else -> DesignCode.EGYPTIAN
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
        binding.staircaseElevationView.apply {
            totalRise = 3000f
            totalRun = 4000f
            riserHeight = 150f
            treadDepth = 250f
            waistThickness = 150f
            landingThickness = 180f
            stairWidth = 1200f
            invalidate()
        }
    }

    private fun performCalculation() {
        try {
            // Read staircase dimensions
            val totalRise = binding.etTotalRise.text.toString().toFloat()
            val totalRun = binding.etTotalRun.text.toString().toFloat()
            val riserHeight = binding.etRiserHeight.text.toString().toFloat()
            val treadDepth = binding.etTreadDepth.text.toString().toFloat()
            val stairWidth = binding.etStairWidth.text.toString().toFloat()
            val waistThickness = binding.etWaistThickness.text.toString().toFloat()
            val landingThickness = binding.etLandingThickness.text.toString().toFloat()

            // Read material properties
            val concreteStrength = binding.etConcreteStrength.text.toString().toDouble()
            val steelStrength = binding.etSteelStrength.text.toString().toDouble()

            // Read loading
            val deadLoad = binding.etDeadLoad.text.toString().toDouble()
            val liveLoad = binding.etLiveLoad.text.toString().toDouble()

            // Validate inputs
            if (totalRise <= 0 || totalRun <= 0 || riserHeight <= 0 || treadDepth <= 0) {
                showError(getString(R.string.check_inputs))
                return
            }

            // Perform calculation based on selected code
            val result = when (selectedCode) {
                DesignCode.EGYPTIAN -> calculator.calculateStaircaseEgyptian(
                    totalRise, totalRun, riserHeight, treadDepth,
                    stairWidth, waistThickness, landingThickness,
                    concreteStrength, steelStrength, deadLoad, liveLoad
                )
                DesignCode.ACI -> calculator.calculateStaircaseACI(
                    totalRise, totalRun, riserHeight, treadDepth,
                    stairWidth, waistThickness, landingThickness,
                    concreteStrength, steelStrength, deadLoad, liveLoad
                )
                DesignCode.SBC -> calculator.calculateStaircaseSBC(
                    totalRise, totalRun, riserHeight, treadDepth,
                    stairWidth, waistThickness, landingThickness,
                    concreteStrength, steelStrength, deadLoad, liveLoad
                )
            }

            // Update interactive drawing with results
            updateDrawing(result, totalRise, totalRun, riserHeight, treadDepth,
                stairWidth, waistThickness, landingThickness)

            // Display results
            displayResults(result)

            Toast.makeText(
                requireContext(),
                getString(R.string.calculation_complete),
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: NumberFormatException) {
            showError(getString(R.string.check_inputs))
        } catch (e: Exception) {
            showError(e.message ?: getString(R.string.error))
        }
    }

    private fun updateDrawing(
        result: StaircaseCalculationResult,
        totalRise: Float,
        totalRun: Float,
        riserHeight: Float,
        treadDepth: Float,
        stairWidth: Float,
        waistThickness: Float,
        landingThickness: Float
    ) {
        binding.staircaseElevationView.updateFromCalculation(
            rise = totalRise,
            run = totalRun,
            riser = riserHeight,
            tread = treadDepth,
            waist = waistThickness,
            landingLen = totalRun * 0.3f,
            landingThick = landingThickness,
            width = stairWidth,
            momentCap = result.momentCapacity,
            shearCap = result.shearCapacity,
            appMoment = result.appliedMoment,
            appShear = result.appliedShear,
            safe = result.isSafe
        )
    }

    private fun displayResults(result: StaircaseCalculationResult) {
        binding.resultsCard.visibility = View.VISIBLE

        // Main reinforcement
        binding.etMainReinforcement.setText(result.mainReinforcement)

        // Distribution reinforcement
        binding.etDistributionReinforcement.setText(result.distributionReinforcement)

        // Code used
        val codeText = when (result.code) {
            CalculatorEngine.DesignCode.EGYPTIAN -> getString(R.string.egyptian_code)
            CalculatorEngine.DesignCode.ACI -> getString(R.string.american_code)
            CalculatorEngine.DesignCode.SAUDI -> getString(R.string.saudi_code)
        }
        binding.etCodeUsed.setText(codeText)

        // Save button
        binding.btnSaveDesign.setOnClickListener {
            saveDesign(result)
        }

        // Scroll to results
        binding.root.post {
            binding.root.scrollTo(0, binding.resultsCard.top)
        }
    }

    private fun saveDesign(result: StaircaseCalculationResult) {
        lifecycleScope.launch {
            Toast.makeText(
                requireContext(),
                getString(R.string.save_success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Staircase Calculation Result Data Class
 */
data class StaircaseCalculationResult(
    val momentCapacity: Double,
    val shearCapacity: Double,
    val appliedMoment: Double,
    val appliedShear: Double,
    val mainReinforcement: String,
    val distributionReinforcement: String,
    val numberOfSteps: Int,
    val isSafe: Boolean,
    val code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN
)
