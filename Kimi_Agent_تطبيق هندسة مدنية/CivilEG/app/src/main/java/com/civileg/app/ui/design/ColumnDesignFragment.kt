package com.civileg.app.ui.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.civileg.app.R
import com.civileg.app.databinding.FragmentColumnDesignBinding
import com.civileg.app.ui.base.BaseFragment
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import com.civileg.app.views.ColumnSectionView
import kotlinx.coroutines.launch

/**
 * Column Design Fragment with Interactive Drawing
 * Supports both rectangular and circular columns
 */
class ColumnDesignFragment : BaseFragment() {

    private var _binding: FragmentColumnDesignBinding? = null
    private val binding get() = _binding!!

    private lateinit var calculator: CalculatorEngine
    private var selectedCode: DesignCode = DesignCode.EGYPTIAN
    private var columnType: ColumnSectionView.ColumnType = ColumnSectionView.ColumnType.RECTANGULAR

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColumnDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calculator = CalculatorEngine()

        setupColumnTypeSelection()
        setupCodeSelection()
        setupCalculateButton()
        setupInitialDrawing()
    }

    private fun setupColumnTypeSelection() {
        binding.rgColumnType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbCircular -> {
                    columnType = ColumnSectionView.ColumnType.CIRCULAR
                    binding.layoutColumnWidth.visibility = View.GONE
                    binding.layoutColumnHeight.visibility = View.GONE
                    binding.layoutColumnDiameter.visibility = View.VISIBLE
                }
                else -> {
                    columnType = ColumnSectionView.ColumnType.RECTANGULAR
                    binding.layoutColumnWidth.visibility = View.VISIBLE
                    binding.layoutColumnHeight.visibility = View.VISIBLE
                    binding.layoutColumnDiameter.visibility = View.GONE
                }
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
        binding.columnSectionView.apply {
            this.columnType = this@ColumnDesignFragment.columnType
            when (columnType) {
                ColumnSectionView.ColumnType.RECTANGULAR -> {
                    columnWidth = 400f
                    columnHeight = 400f
                }
                ColumnSectionView.ColumnType.CIRCULAR -> {
                    columnDiameter = 400f
                }
            }
            cover = 40f
            cornerBars = 4
            cornerBarDiameter = 20
            invalidate()
        }
    }

    private fun performCalculation() {
        try {
            // Read input values based on column type
            val width: Float
            val height: Float
            val diameter: Float

            if (columnType == ColumnSectionView.ColumnType.RECTANGULAR) {
                width = binding.etColumnWidth.text.toString().toFloat()
                height = binding.etColumnHeight.text.toString().toFloat()
                diameter = 0f
            } else {
                width = 0f
                height = 0f
                diameter = binding.etColumnDiameter.text.toString().toFloat()
            }

            val length = binding.etColumnLength.text.toString().toFloat()
            val cover = binding.etConcreteCover.text.toString().toFloat()
            val concreteStrength = binding.etConcreteStrength.text.toString().toDouble()
            val steelStrength = binding.etSteelStrength.text.toString().toDouble()
            val axialLoad = binding.etAxialLoad.text.toString().toDouble()
            val momentX = binding.etMomentX.text.toString().toDouble()
            val momentY = binding.etMomentY.text.toString().toDouble()

            // Validate inputs
            if (cover <= 0 || length <= 0) {
                showError(getString(R.string.check_inputs))
                return
            }

            // Perform calculation based on selected code
            val result = when (selectedCode) {
                DesignCode.EGYPTIAN -> calculator.calculateColumnEgyptian(
                    columnType, width, height, diameter, length, cover,
                    concreteStrength, steelStrength, axialLoad, momentX, momentY
                )
                DesignCode.ACI -> calculator.calculateColumnACI(
                    columnType, width, height, diameter, length, cover,
                    concreteStrength, steelStrength, axialLoad, momentX, momentY
                )
                DesignCode.SBC -> calculator.calculateColumnSBC(
                    columnType, width, height, diameter, length, cover,
                    concreteStrength, steelStrength, axialLoad, momentX, momentY
                )
            }

            // Update interactive drawing with results
            updateDrawing(result, width, height, diameter, cover)

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
        result: ColumnCalculationResult,
        width: Float,
        height: Float,
        diameter: Float,
        cover: Float
    ) {
        binding.columnSectionView.updateFromCalculation(
            type = columnType,
            width = width,
            height = height,
            diameter = diameter,
            coverValue = cover,
            corners = result.cornerBars,
            cornerDia = result.cornerBarDiameter,
            sideX = result.sideBarsX,
            sideY = result.sideBarsY,
            sideDia = result.sideBarDiameter,
            tieDia = result.tieDiameter,
            spiral = result.isSpiral,
            axialCap = result.axialCapacity,
            mxCap = result.momentCapacityX,
            myCap = result.momentCapacityY,
            appAxial = result.appliedAxial,
            appMx = result.appliedMomentX,
            appMy = result.appliedMomentY,
            safe = result.isSafe
        )
    }

    private fun displayResults(result: ColumnCalculationResult) {
        binding.resultsCard.visibility = View.VISIBLE

        // Longitudinal bars
        val totalBars = result.cornerBars + (result.sideBarsX + result.sideBarsY) * 2
        binding.etLongitudinalBars.setText("$totalBars Ø${result.cornerBarDiameter} mm")

        // Ties/Spiral
        val tieText = if (result.isSpiral) {
            "Ø${result.tieDiameter} mm Spiral"
        } else {
            "Ø${result.tieDiameter} mm @ 200 mm"
        }
        binding.etTies.setText(tieText)

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

    private fun saveDesign(result: ColumnCalculationResult) {
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
 * Column Calculation Result Data Class
 */
data class ColumnCalculationResult(
    val axialCapacity: Double,
    val momentCapacityX: Double,
    val momentCapacityY: Double,
    val appliedAxial: Double,
    val appliedMomentX: Double,
    val appliedMomentY: Double,
    val cornerBars: Int,
    val cornerBarDiameter: Int,
    val sideBarsX: Int,
    val sideBarsY: Int,
    val sideBarDiameter: Int,
    val tieDiameter: Int,
    val isSpiral: Boolean,
    val isSafe: Boolean,
    val code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN
)
