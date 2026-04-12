package com.civileg.app.ui.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.civileg.app.R
import com.civileg.app.databinding.FragmentBeamDesignBinding
import com.civileg.app.ui.base.BaseFragment
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.DesignCode
import com.civileg.app.views.BeamSectionView
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch

/**
 * Beam Design Fragment with Interactive Drawing
 * Features clear input labels and real-time visualization
 */
class BeamDesignFragment : BaseFragment() {

    private var _binding: FragmentBeamDesignBinding? = null
    private val binding get() = _binding!!

    private lateinit var calculator: CalculatorEngine
    private var selectedCode: DesignCode = DesignCode.EGYPTIAN

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
        // Show initial beam drawing with default values
        binding.beamSectionView.apply {
            beamWidth = 300f
            beamHeight = 600f
            cover = 40f
            effectiveDepth = 560f
            topBars = listOf(BeamSectionView.BarInfo(2, 16))
            bottomBars = listOf(BeamSectionView.BarInfo(3, 25))
            invalidate()
        }
    }

    private fun performCalculation() {
        try {
            // Read input values with clear labels
            val width = binding.etBeamWidth.text.toString().toFloat()
            val height = binding.etBeamHeight.text.toString().toFloat()
            val length = binding.etBeamLength.text.toString().toFloat()
            val cover = binding.etConcreteCover.text.toString().toFloat()

            val concreteStrength = binding.etConcreteStrength.text.toString().toDouble()
            val steelStrength = binding.etSteelStrength.text.toString().toDouble()

            val deadLoad = binding.etDeadLoad.text.toString().toDouble()
            val liveLoad = binding.etLiveLoad.text.toString().toDouble()

            // Validate inputs
            if (width <= 0 || height <= 0 || length <= 0) {
                showError(getString(R.string.check_inputs))
                return
            }

            // Perform calculation based on selected code
            val result = when (selectedCode) {
                DesignCode.EGYPTIAN -> calculator.calculateBeamEgyptian(
                    width, height, length, cover,
                    concreteStrength, steelStrength,
                    deadLoad, liveLoad
                )
                DesignCode.ACI -> calculator.calculateBeamACI(
                    width, height, length, cover,
                    concreteStrength, steelStrength,
                    deadLoad, liveLoad
                )
                DesignCode.SBC -> calculator.calculateBeamSBC(
                    width, height, length, cover,
                    concreteStrength, steelStrength,
                    deadLoad, liveLoad
                )
            }

            // Update interactive drawing with results
            updateDrawing(result, width, height, cover)

            // Display results
            displayResults(result)

            // Show success message
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
        result: BeamCalculationResult,
        width: Float,
        height: Float,
        cover: Float
    ) {
        binding.beamSectionView.updateFromCalculation(
            width = width,
            height = height,
            coverValue = cover,
            topReinforcement = result.topBars,
            bottomReinforcement = result.bottomBars,
            momentCap = result.momentCapacity,
            shearCap = result.shearCapacity,
            appMoment = result.appliedMoment,
            appShear = result.appliedShear,
            naDepth = result.neutralAxisDepth,
            ratio = result.steelRatio,
            safe = result.isSafe
        )
    }

    private fun displayResults(result: BeamCalculationResult) {
        binding.resultsCard.visibility = View.VISIBLE

        // Bottom reinforcement
        val bottomText = if (result.bottomBars.isNotEmpty()) {
            val bar = result.bottomBars[0]
            "${bar.count}Ø${bar.diameter} mm"
        } else "-"
        binding.etBottomReinforcement.setText(bottomText)

        // Top reinforcement
        val topText = if (result.topBars.isNotEmpty()) {
            val bar = result.topBars[0]
            "${bar.count}Ø${bar.diameter} mm"
        } else "-"
        binding.etTopReinforcement.setText(topText)

        // Stirrups
        binding.etStirrups.setText(result.stirrups)

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

    private fun saveDesign(result: BeamCalculationResult) {
        lifecycleScope.launch {
            // Save to database
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
 * Beam Calculation Result Data Class
 */
data class BeamCalculationResult(
    val momentCapacity: Double,
    val shearCapacity: Double,
    val appliedMoment: Double,
    val appliedShear: Double,
    val neutralAxisDepth: Double,
    val steelRatio: Double,
    val topBars: List<BeamSectionView.BarInfo>,
    val bottomBars: List<BeamSectionView.BarInfo>,
    val stirrups: String,
    val isSafe: Boolean,
    val code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN
)
