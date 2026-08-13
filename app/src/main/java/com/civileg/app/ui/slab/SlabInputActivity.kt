package com.civileg.app.ui.slab

import android.os.Bundle
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.R
import com.civileg.app.databinding.ActivitySlabInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.SlabViewModel
import com.civileg.app.utils.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SlabInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlabInputBinding
    private val viewModel: SlabViewModel by viewModels()
    private var projectId: Long = -1

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySlabInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra("project_id", -1L)
        if (projectId == -1L) {
            Toast.makeText(this, R.string.error_no_project, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupObservers()
        setupSpinners()
        setupListeners()
        
        setDefaultSelections()

        // Initial calculation
        triggerCalculation()
    }

    private fun setDefaultSelections() {
        val defaultCode = settingsManager.defaultDesignCode
        val codeIndex = when(defaultCode) {
            com.civileg.app.domain.entities.DesignCode.ECP -> 0
            com.civileg.app.domain.entities.DesignCode.ACI -> 1
            com.civileg.app.domain.entities.DesignCode.SBC -> 2
        }
        binding.spinnerCode.setSelection(codeIndex)
    }

    private fun triggerCalculation() {
        val spanX = binding.etSpanX.text.toString().toDoubleOrNull() ?: return
        val spanY = binding.etSpanY.text.toString().toDoubleOrNull() ?: return
        val liveLoad = binding.etLiveLoad.text.toString().toDoubleOrNull() ?: return
        val deadLoad = binding.etDeadLoad.text.toString().toDoubleOrNull() ?: return
        val thickness = binding.etThickness.text.toString().toDoubleOrNull() ?: 150.0
        val fcu = binding.etFcu.text.toString().toDoubleOrNull() ?: 25.0
        val fy = binding.etFy.text.toString().toDoubleOrNull() ?: 400.0

        val selectedType = CalculatorEngine.SlabType.values().getOrNull(binding.spinnerSlabType.selectedItemPosition) ?: CalculatorEngine.SlabType.SOLID
        val selectedCode = CalculatorEngine.DesignCode.values().getOrNull(binding.spinnerCode.selectedItemPosition) ?: CalculatorEngine.DesignCode.EGYPTIAN

        val diamStr = binding.spinnerDiameter.selectedItem?.toString()?.replace(" mm", "")
        val preferredDiameter = diamStr?.toIntOrNull() ?: 12

        viewModel.calculateSlab(
            spanX = spanX,
            spanY = spanY,
            deadLoad = deadLoad,
            liveLoad = liveLoad,
            fcu = fcu,
            fy = fy,
            thickness = thickness,
            preferredDiameter = preferredDiameter,
            type = selectedType,
            code = selectedCode
        )
    }

    private fun setupSpinners() {
        // Slab Types
        val slabTypes = CalculatorEngine.SlabType.values()
        val slabAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, slabTypes.map { it.displayName })
        binding.spinnerSlabType.adapter = slabAdapter

        // Design Codes
        val codes = CalculatorEngine.DesignCode.values()
        val codeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes.map { it.displayName })
        binding.spinnerCode.adapter = codeAdapter
        
        // Diameters
        val diameters = listOf(8, 10, 12, 14, 16, 18, 20, 22, 25)
        val diameterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "${it} mm" })
        binding.spinnerDiameter.adapter = diameterAdapter
        binding.spinnerDiameter.setSelection(2) // Default 12mm
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                triggerCalculation()
            }
        }

        binding.etSpanX.addTextChangedListener(watcher)
        binding.etSpanY.addTextChangedListener(watcher)
        binding.etThickness.addTextChangedListener(watcher)
        binding.etLiveLoad.addTextChangedListener(watcher)
        binding.etDeadLoad.addTextChangedListener(watcher)
        binding.etFcu.addTextChangedListener(watcher)
        binding.etFy.addTextChangedListener(watcher)

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                triggerCalculation()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerSlabType.onItemSelectedListener = spinnerListener
        binding.spinnerCode.onItemSelectedListener = spinnerListener
        binding.spinnerDiameter.onItemSelectedListener = spinnerListener

        binding.btnCalculate.setOnClickListener {
            val result = viewModel.result.value
            if (result != null) {
                val designName = "Slab Design ${System.currentTimeMillis() % 1000}"
                viewModel.saveSlab(projectId, designName, result)
                // startActivity(SlabResultActivity.newIntent(this, result)) // Assuming this exists or will be created
                Toast.makeText(this, "Design Saved Successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter valid data first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            if (result != null) {
                binding.resultCard.visibility = View.VISIBLE
                
                // Status & Safety
                if (result.isSafe) {
                    binding.tvStatus.text = "Status: SAFE"
                    binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    binding.tvStatus.text = "Status: UNSAFE"
                    binding.tvStatus.setTextColor(Color.RED)
                }

                // Efficiency & Utilization
                binding.tvEfficiency.text = "Efficiency Score: ${result.efficiencyScore.toInt()}%"
                binding.tvUtilization.text = "Utilization Ratio: ${"%.2f".format(result.utilizationRatio)}"
                
                // Colorize Utilization for Engineer
                when {
                    result.utilizationRatio > 1.0 -> binding.tvUtilization.setTextColor(Color.RED)
                    result.utilizationRatio > 0.9 -> binding.tvUtilization.setTextColor(Color.parseColor("#FF9800")) // Warning orange
                    result.utilizationRatio < 0.5 -> binding.tvUtilization.setTextColor(Color.BLUE) // Over-designed
                    else -> binding.tvUtilization.setTextColor(Color.parseColor("#4CAF50"))
                }

                // Warnings
                val warnings = result.safetyChecks.filter { !it.isSafe }.map { it.name }
                if (warnings.isNotEmpty()) {
                    binding.tvWarnings.visibility = View.VISIBLE
                    binding.tvWarnings.text = "Warnings: ${warnings.joinToString(", ")}"
                } else {
                    binding.tvWarnings.visibility = View.GONE
                }

                // Suggestions
                if (result.suggestions.isNotEmpty()) {
                    binding.tvSuggestions.visibility = View.VISIBLE
                    binding.tvSuggestions.text = "💡 Suggestion: ${result.suggestions.first()}"
                } else {
                    binding.tvSuggestions.visibility = View.GONE
                }
            } else {
                binding.resultCard.visibility = View.GONE
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

}
