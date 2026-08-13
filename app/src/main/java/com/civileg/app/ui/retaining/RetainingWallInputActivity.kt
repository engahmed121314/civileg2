package com.civileg.app.ui.retaining

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityRetainingWallInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.RetainingWallViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RetainingWallInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRetainingWallInputBinding
    private val viewModel: RetainingWallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRetainingWallInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupObservers()
        setupListeners()
    }

    private fun setupSpinners() {
        val codes = CalculatorEngine.DesignCode.values().map { it.displayName }
        binding.spinnerCode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)

        val diameters = listOf(12, 14, 16, 18, 20, 22, 25)
        binding.spinnerDiameter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "$it mm" })
        binding.spinnerDiameter.setSelection(2) // Default 16mm
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            val h = binding.etHeight.text.toString().toDoubleOrNull()
            val soil = binding.etSoilDensity.text.toString().toDoubleOrNull()
            val phi = binding.etFrictionAngle.text.toString().toDoubleOrNull()
            val surcharge = binding.etSurcharge.text.toString().toDoubleOrNull()
            val fcu = binding.etFcu.text.toString().toDoubleOrNull()
            val fy = binding.etFy.text.toString().toDoubleOrNull()

            if (h == null || soil == null || phi == null || surcharge == null || fcu == null || fy == null) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val code = CalculatorEngine.DesignCode.values()[binding.spinnerCode.selectedItemPosition]
            val diameter = listOf(12, 14, 16, 18, 20, 22, 25)[binding.spinnerDiameter.selectedItemPosition]

            viewModel.calculateRetainingWallPro(h, soil, phi, surcharge, fcu, fy, diameter, code)
        }
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            if (result != null) {
                startActivity(RetainingWallResultActivity.newIntent(this, result))
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
