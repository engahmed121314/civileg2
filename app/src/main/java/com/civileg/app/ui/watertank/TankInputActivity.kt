package com.civileg.app.ui.watertank

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityTankInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.TankViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TankInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTankInputBinding
    private val viewModel: TankViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTankInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupObservers()
        setupListeners()
    }

    private fun setupSpinners() {
        val codes = CalculatorEngine.DesignCode.values().map { it.displayName }
        binding.spinnerCode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)

        val tankTypes = CalculatorEngine.TankType.values().map { it.name.replace("_", " ") }
        binding.spinnerTankType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tankTypes)

        val diameters = listOf(10, 12, 14, 16, 18, 20)
        binding.spinnerDiameter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "$it mm" })
        binding.spinnerDiameter.setSelection(1) // Default 12mm
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            val capacity = binding.etCapacity.text.toString().toDoubleOrNull()
            val height = binding.etHeight.text.toString().toDoubleOrNull()
            val fcu = binding.etFcu.text.toString().toDoubleOrNull()
            val fy = binding.etFy.text.toString().toDoubleOrNull()

            if (capacity == null || height == null || fcu == null || fy == null) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val code = CalculatorEngine.DesignCode.values()[binding.spinnerCode.selectedItemPosition]
            val type = CalculatorEngine.TankType.values()[binding.spinnerTankType.selectedItemPosition]
            val diameter = listOf(10, 12, 14, 16, 18, 20)[binding.spinnerDiameter.selectedItemPosition]

            viewModel.calculateTankPro(type, capacity, height, fcu, fy, diameter, code)
        }
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            if (result != null) {
                startActivity(TankResultActivity.newIntent(this, result))
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
