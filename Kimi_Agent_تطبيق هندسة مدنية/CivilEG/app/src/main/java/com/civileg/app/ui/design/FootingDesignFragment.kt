package com.civileg.app.ui.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.civileg.app.R
import com.civileg.app.databinding.FragmentFootingDesignBinding
import com.civileg.app.ui.base.BaseFragment
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import kotlinx.coroutines.launch

/**
 * Footing Design Fragment with Interactive Drawing
 * Shows plan and section views of isolated footing
 */
class FootingDesignFragment : BaseFragment() {

    private var _binding: FragmentFootingDesignBinding? = null
    private val binding get() = _binding!!

    private lateinit var calculator: CalculatorEngine
    private var selectedCode: DesignCode = DesignCode.EGYPTIAN

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
        binding.footingPlanView.apply {
            footingLength = 2000f
            footingWidth = 2000f
            footingThickness = 400f
            columnLength = 400f
            columnWidth = 400f
            barDiameter = 16
            barSpacingX = 180f
            barSpacingY = 180f
            invalidate()
        }
    }

    private fun performCalculation() {
        try {
            // Read footing dimensions
            val footingLength = binding.etFootingLength.text.toString().toFloat()
            val footingWidth = binding.etFootingWidth.text.toString().toFloat()
            val footingThickness = binding.etFootingThickness.text.toString().toFloat()
            val cover = binding.etConcreteCover.text.toString().toFloat()

            // Read column dimensions
            val columnLength = binding.etColumnLength.text.toString().toFloat()
            val columnWidth = binding.etColumnWidth.text.toString().toFloat()

            // Read material properties
            val concreteStrength = binding.etConcreteStrength.text.toString().toDouble()
            val steelStrength = binding.etSteelStrength.text.toString().toDouble()

            // Read loading
            val axialLoad = binding.etAxialLoad.text.toString().toDouble()
            val soilPressure = binding.etSoilPressure.text.toString().toDouble()

            // Validate inputs
            if (footingLength <= 0 || footingWidth <= 0 || footingThickness <= 0) {
                showError(getString(R.string.check_inputs))
                return
            }

            // Perform calculation based on selected code
            val result = when (selectedCode) {
                DesignCode.EGYPTIAN -> calculator.calculateFootingEgyptian(
                    footingLength, footingWidth, footingThickness,
                    columnLength, columnWidth, cover,
                    concreteStrength, steelStrength,
                    axialLoad, soilPressure
                )
                DesignCode.ACI -> calculator.calculateFootingACI(
                    footingLength, footingWidth, footingThickness,
                    columnLength, columnWidth, cover,
                    concreteStrength, steelStrength,
                    axialLoad, soilPressure
                )
                DesignCode.SBC -> calculator.calculateFootingSBC(
                    footingLength, footingWidth, footingThickness,
                    columnLength, columnWidth, cover,
                    concreteStrength, steelStrength,
                    axialLoad, soilPressure
                )
            }

            // Update interactive drawing with results
            updateDrawing(result, footingLength, footingWidth, footingThickness,
                columnLength, columnWidth, cover)

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
        result: FootingCalculationResult,
        footingLength: Float,
        footingWidth: Float,
        footingThickness: Float,
        columnLength: Float,
        columnWidth: Float,
        cover: Float
    ) {
        binding.footingPlanView.updateFromCalculation(
            length = footingLength,
            width = footingWidth,
            thickness = footingThickness,
            colLength = columnLength,
            colWidth = columnWidth,
            colHeight = 3000f,
            barsX = result.barsX,
            barsY = result.barsY,
            barDia = result.barDiameter,
            spacingX = result.spacingX,
            spacingY = result.spacingY,
            soilPress = result.soilPressure,
            allowPress = result.allowablePressure,
            punchShear = result.punchingShearStress,
            allowShear = result.allowableShearStress,
            safe = result.isSafe,
            reqArea = result.requiredArea
        )
    }

    private fun displayResults(result: FootingCalculationResult) {
        binding.resultsCard.visibility = View.VISIBLE

        // Bottom reinforcement X
        binding.etBottomReinforcementX.setText(
            "${result.barsX}Ø${result.barDiameter} @ ${result.spacingX.toInt()} mm"
        )

        // Bottom reinforcement Y
        binding.etBottomReinforcementY.setText(
            "${result.barsY}Ø${result.barDiameter} @ ${result.spacingY.toInt()} mm"
        )

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

    private fun saveDesign(result: FootingCalculationResult) {
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
 * Footing Calculation Result Data Class
 */
data class FootingCalculationResult(
    val barsX: Int,
    val barsY: Int,
    val barDiameter: Int,
    val spacingX: Float,
    val spacingY: Float,
    val soilPressure: Double,
    val allowablePressure: Double,
    val punchingShearStress: Double,
    val allowableShearStress: Double,
    val requiredArea: Double,
    val isSafe: Boolean,
    val code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN
)
