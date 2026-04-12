package com.civileg.app.ui.stairs

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityStairInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.StairViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StairInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStairInputBinding
    private val viewModel: StairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStairInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupObservers()
        setupListeners()
    }

    private fun setupSpinners() {
        val codes = CalculatorEngine.DesignCode.values().map { it.displayName }
        binding.spinnerCode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)

        val diameters = listOf(10, 12, 14, 16, 18)
        binding.spinnerDiameter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "$it mm" })
        binding.spinnerDiameter.setSelection(1) // Default 12mm
        
        val types = CalculatorEngine.StairType.values().map { it.displayName }
        binding.spinnerStairType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            val span = binding.etSpan.text.toString().toDoubleOrNull()
            val riser = binding.etRiser.text.toString().toDoubleOrNull()
            val tread = binding.etTread.text.toString().toDoubleOrNull()
            val liveLoad = binding.etLiveLoad.text.toString().toDoubleOrNull()
            val fcu = binding.etFcu.text.toString().toDoubleOrNull()
            val fy = binding.etFy.text.toString().toDoubleOrNull()

            if (span == null || riser == null || tread == null || liveLoad == null || fcu == null || fy == null) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val code = CalculatorEngine.DesignCode.values()[binding.spinnerCode.selectedItemPosition]
            val diameter = listOf(10, 12, 14, 16, 18)[binding.spinnerDiameter.selectedItemPosition]
            val type = CalculatorEngine.StairType.values()[binding.spinnerStairType.selectedItemPosition]

            viewModel.calculateStairPro(
                type = type,
                span = span,
                riser = riser,
                tread = tread,
                deadLoad = liveLoad * 0.8, // Assumption
                liveLoad = liveLoad,
                fcu = fcu,
                fy = fy,
                preferredDiameter = diameter,
                code = code
            )
        }
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            if (result != null) {
                startActivity(StairResultActivity.newIntent(this, result))
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
