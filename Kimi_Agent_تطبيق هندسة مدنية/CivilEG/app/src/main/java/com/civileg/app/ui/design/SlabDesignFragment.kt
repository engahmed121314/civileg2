package com.civileg.app.ui.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.civileg.app.R
import com.civileg.app.databinding.FragmentSlabDesignBinding
import com.civileg.app.ui.base.BaseFragment
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import com.civileg.app.views.SlabDetailView
import kotlinx.coroutines.launch

/**
 * Slab Design Fragment with Interactive Drawing
 * Supports multiple slab types: Solid, Flat, Waffle, Ribbed, Hollow Block
 */
class SlabDesignFragment : BaseFragment() {

    private var _binding: FragmentSlabDesignBinding? = null
    private val binding get() = _binding!!

    private lateinit var calculator: CalculatorEngine
    private var selectedCode: DesignCode = DesignCode.EGYPTIAN
    private var selectedSlabType: SlabDetailView.SlabType = SlabDetailView.SlabType.SOLID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlabDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calculator = CalculatorEngine()

        setupSlabTypeSelection()
        setupCodeSelection()
        setupCalculateButton()
        setupInitialDrawing()
    }

    private fun setupSlabTypeSelection() {
        val slabTypes = arrayOf(
            getString(R.string.solid_slab),
            getString(R.string.flat_slab),
            getString(R.string.waffle_slab),
            getString(R.string.ribbed_slab),
            getString(R.string.hollow_block)
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            slabTypes
        )
        binding.spinnerSlabType.setAdapter(adapter)
        binding.spinnerSlabType.setOnItemClickListener { _, _, position, _ ->
            selectedSlabType = when (position) {
                1 -> SlabDetailView.SlabType.FLAT
                2 -> SlabDetailView.SlabType.WAFFLE
                3 -> SlabDetailView.SlabType.RIBBED
                4 -> SlabDetailView.SlabType.HOLLOW_BLOCK
                else -> SlabDetailView.SlabType.SOLID
            }
            updateInitialDrawing()
        }
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
        updateInitialDrawing()
    }

    private fun updateInitialDrawing() {
        binding.slabDetailView.apply {
            slabType = selectedSlabType
            slabLength = 5000f
            slabWidth = 5000f
            slabThickness = 200f
            barDiameter = 12
            barSpacing = 200f
            invalidate()
        }
    }

    private fun performCalculation() {
        try {
            // Read slab dimensions
            val lengthX = binding.etSlabLengthX.text.toString().toFloat()
            val lengthY = binding.etSlabLengthY.text.toString().toFloat()
            val thickness = binding.etSlabThickness.text.toString().toFloat()
            val cover = binding.etConcreteCover.text.toString().toFloat()

            // Read material properties
            val concreteStrength = binding.etConcreteStrength.text.toString().toDouble()
            val steelStrength = binding.etSteelStrength.text.toString().toDouble()

            // Read loading
            val deadLoad = binding.etDeadLoad.text.toString().toDouble()
            val liveLoad = binding.etLiveLoad.text.toString().toDouble()

            // Validate inputs
            if (lengthX <= 0 || lengthY <= 0 || thickness <= 0) {
                showError(getString(R.string.check_inputs))
                return
            }

            // Perform calculation based on selected code
            val result = when (selectedCode) {
                DesignCode.EGYPTIAN -> calculator.calculateSlabEgyptian(
                    selectedSlabType, lengthX, lengthY, thickness, cover,
                    concreteStrength, steelStrength, deadLoad, liveLoad
                )
                DesignCode.ACI -> calculator.calculateSlabACI(
                    selectedSlabType, lengthX, lengthY, thickness, cover,
                    concreteStrength, steelStrength, deadLoad, liveLoad
                )
                DesignCode.SBC -> calculator.calculateSlabSBC(
                    selectedSlabType, lengthX, lengthY, thickness, cover,
                    concreteStrength, steelStrength, deadLoad, liveLoad
                )
            }

            // Update interactive drawing with results
            updateDrawing(result, lengthX, lengthY, thickness)

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
        result: SlabCalculationResult,
        lengthX: Float,
        lengthY: Float,
        thickness: Float
    ) {
        binding.slabDetailView.updateFromCalculation(
            type = selectedSlabType,
            length = lengthX,
            width = lengthY,
            thickness = thickness,
            dropThick = thickness * 1.5f,
            dropWidth = lengthX * 0.4f,
            ribW = 150f,
            ribSpace = 500f,
            blockH = 200f,
            barDia = result.barDiameter,
            barSpace = result.barSpacing,
            mxCap = result.momentCapacityX,
            myCap = result.momentCapacityY,
            appMx = result.appliedMomentX,
            appMy = result.appliedMomentY,
            defl = result.deflection,
            allowDefl = result.allowableDeflection,
            safe = result.isSafe
        )
    }

    private fun displayResults(result: SlabCalculationResult) {
        binding.resultsCard.visibility = View.VISIBLE

        // Top reinforcement X
        binding.etTopReinforcementX.setText(
            "${result.topBarsX}Ø${result.barDiameter} @ ${result.topSpacingX.toInt()} mm"
        )

        // Top reinforcement Y
        binding.etTopReinforcementY.setText(
            "${result.topBarsY}Ø${result.barDiameter} @ ${result.topSpacingY.toInt()} mm"
        )

        // Bottom reinforcement X
        binding.etBottomReinforcementX.setText(
            "${result.bottomBarsX}Ø${result.barDiameter} @ ${result.bottomSpacingX.toInt()} mm"
        )

        // Bottom reinforcement Y
        binding.etBottomReinforcementY.setText(
            "${result.bottomBarsY}Ø${result.barDiameter} @ ${result.bottomSpacingY.toInt()} mm"
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

    private fun saveDesign(result: SlabCalculationResult) {
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
 * Slab Calculation Result Data Class
 */
data class SlabCalculationResult(
    val momentCapacityX: Double,
    val momentCapacityY: Double,
    val appliedMomentX: Double,
    val appliedMomentY: Double,
    val deflection: Double,
    val allowableDeflection: Double,
    val topBarsX: Int,
    val topBarsY: Int,
    val bottomBarsX: Int,
    val bottomBarsY: Int,
    val topSpacingX: Float,
    val topSpacingY: Float,
    val bottomSpacingX: Float,
    val bottomSpacingY: Float,
    val barDiameter: Int,
    val barSpacing: Float,
    val isSafe: Boolean,
    val code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN
)
