package com.civileg.app.ui.column

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityColumnInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.ColumnViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ColumnInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColumnInputBinding
    private val viewModel: ColumnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityColumnInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupObservers()
        setupListeners()
    }

    private fun setupSpinners() {
        val codes = CalculatorEngine.DesignCode.values().map { it.displayName }
        binding.spinnerCode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)

        val diameters = listOf(12, 14, 16, 18, 20, 22, 25, 28, 32)
        binding.spinnerDiameter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "$it mm" })
        binding.spinnerDiameter.setSelection(2) // Default 16mm
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            if (binding.etLoad.text.isNullOrEmpty() || 
                binding.etFcu.text.isNullOrEmpty() || 
                binding.etFy.text.isNullOrEmpty() || 
                binding.etWidth.text.isNullOrEmpty() || 
                binding.etDepth.text.isNullOrEmpty()) {
                Toast.makeText(this, "برجاء إدخال كافة الحقول المطلوبة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val code = CalculatorEngine.DesignCode.values()[binding.spinnerCode.selectedItemPosition]
            val diameter = listOf(12, 14, 16, 18, 20, 22, 25, 28, 32)[binding.spinnerDiameter.selectedItemPosition]

            // التحديث لدعم المعاملات الجديدة في الـ ViewModel
            viewModel.updateInputs(
                width = binding.etWidth.text.toString(),
                depth = binding.etDepth.text.toString(),
                height = binding.etHeight.text.toString(),
                fcu = binding.etFcu.text.toString(),
                fy = binding.etFy.text.toString(),
                axialLoad = binding.etLoad.text.toString(),
                preferredDiameter = diameter.toString()
            )
            // Map CalculatorEngine.DesignCode to domain.entities.DesignCode
            val domainCode = when(code) {
                CalculatorEngine.DesignCode.EGYPTIAN -> com.civileg.app.domain.entities.DesignCode.ECP
                CalculatorEngine.DesignCode.ACI -> com.civileg.app.domain.entities.DesignCode.ACI
                CalculatorEngine.DesignCode.SAUDI -> com.civileg.app.domain.entities.DesignCode.SBC
            }
            viewModel.updateDesignCode(domainCode)
        }
    }

    private fun setupObservers() {
        // Use uiStateLiveData for XML activity compatibility
        viewModel.uiStateLiveData.observe(this) { state ->
            state.result?.let { result ->
                startActivity(ColumnResultActivity.newIntent(this, result))
                viewModel.reset() // Reset result to prevent multiple launches
            }
            
            state.errors.firstOrNull()?.let {
                binding.tvError.text = it
                binding.tvError.visibility = View.VISIBLE
            } ?: run {
                binding.tvError.visibility = View.GONE
            }

            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        }
    }
}
