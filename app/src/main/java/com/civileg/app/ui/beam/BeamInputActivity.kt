package com.civileg.app.ui.beam

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityBeamInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.BeamViewModel
import com.civileg.app.utils.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BeamInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeamInputBinding
    private val viewModel: BeamViewModel by viewModels()

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeamInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupObservers()
        setupListeners()
        
        setDefaultSelections()
    }

    private fun setupSpinners() {
        val codes = CalculatorEngine.DesignCode.values().map { it.displayName }
        binding.spinnerCode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)

        val diameters = listOf(10, 12, 14, 16, 18, 20, 22, 25)
        binding.spinnerDiameter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "$it mm" })
    }

    private fun setDefaultSelections() {
        val defaultCode = settingsManager.defaultDesignCode
        val codeIndex = when(defaultCode) {
            com.civileg.app.domain.entities.DesignCode.ECP -> 0
            com.civileg.app.domain.entities.DesignCode.ACI -> 1
            com.civileg.app.domain.entities.DesignCode.SBC -> 2
        }
        binding.spinnerCode.setSelection(codeIndex)
        binding.spinnerDiameter.setSelection(3) // 16mm default
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            val width = binding.etWidth.text.toString().toDoubleOrNull() ?: 250.0
            val depth = binding.etDepth.text.toString().toDoubleOrNull() ?: 600.0
            val span = binding.etSpan.text.toString().toDoubleOrNull() ?: 5.0
            val load = binding.etLoad.text.toString().toDoubleOrNull() ?: 20.0
            
            val code = CalculatorEngine.DesignCode.values()[binding.spinnerCode.selectedItemPosition]
            val diameter = listOf(10, 12, 14, 16, 18, 20, 22, 25)[binding.spinnerDiameter.selectedItemPosition]

            viewModel.calculateBeamPro(
                width = width,
                height = depth,
                span = span,
                deadLoad = load * 0.6,
                liveLoad = load * 0.4,
                fcu = 25.0,
                fy = 360.0,
                preferredDiameter = diameter,
                code = code,
                supportType = CalculatorEngine.SupportType.HINGED_HINGED
            )
        }
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            if (result != null) {
                Toast.makeText(this, "تم التصميم بنجاح: ${result.reinforcementBottom.barString}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            errorMsg?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
}
