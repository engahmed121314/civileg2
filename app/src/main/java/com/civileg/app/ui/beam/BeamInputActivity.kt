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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BeamInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeamInputBinding
    private val viewModel: BeamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeamInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupObservers()
        setupListeners()
    }

    private fun setupSpinners() {
        val codes = CalculatorEngine.DesignCode.values().map { it.displayName }
        binding.spinnerCode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)

        val diameters = listOf(10, 12, 14, 16, 18, 20, 22, 25)
        binding.spinnerDiameter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "$it mm" })
        binding.spinnerDiameter.setSelection(3)
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
