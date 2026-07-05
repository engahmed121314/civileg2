package com.civileg.app.ui.watertank

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
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
    private var projectId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTankInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra("project_id", -1L)
        if (projectId == -1L) {
            Toast.makeText(this, "Project ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupSpinners()
        setupListeners()
        setupObservers()

        // Initial calculation
        triggerCalculation()
    }

    private fun setupSpinners() {
        // Design Codes
        val codes = CalculatorEngine.DesignCode.values()
        binding.spinnerCode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes.map { it.displayName })

        // Tank Types
        val tankTypes = CalculatorEngine.TankType.values()
        binding.spinnerTankType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tankTypes.map { it.displayName })

        // Diameters
        val diameters = listOf(10, 12, 14, 16, 18, 20)
        binding.spinnerDiameter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, diameters.map { "${it} mm" })
        binding.spinnerDiameter.setSelection(1) // Default 12mm
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { triggerCalculation() }
        }

        binding.etCapacity.addTextChangedListener(watcher)
        binding.etHeight.addTextChangedListener(watcher)
        binding.etFcu.addTextChangedListener(watcher)
        binding.etFy.addTextChangedListener(watcher)

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { triggerCalculation() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        binding.spinnerCode.onItemSelectedListener = spinnerListener
        binding.spinnerTankType.onItemSelectedListener = spinnerListener
        binding.spinnerDiameter.onItemSelectedListener = spinnerListener

        binding.btnSave.setOnClickListener {
            val result = viewModel.result.value
            if (result != null) {
                viewModel.saveTank(projectId, "Tank Design ${System.currentTimeMillis() % 1000}", result)
                viewModel.exportToPdf(this) { path ->
                    Toast.makeText(this, "Report saved: $path", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun triggerCalculation() {
        val capacity = binding.etCapacity.text.toString().toDoubleOrNull() ?: return
        val height = binding.etHeight.text.toString().toDoubleOrNull() ?: return
        val fcu = binding.etFcu.text.toString().toDoubleOrNull() ?: return
        val fy = binding.etFy.text.toString().toDoubleOrNull() ?: return
        
        val type = CalculatorEngine.TankType.values()[binding.spinnerTankType.selectedItemPosition]
        val code = CalculatorEngine.DesignCode.values()[binding.spinnerCode.selectedItemPosition]
        val diameter = binding.spinnerDiameter.selectedItem.toString().replace(" mm", "").toInt()

        viewModel.calculateTankPro(type, capacity, height, fcu, fy, diameter, code)
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            if (result != null) {
                binding.resultCard.visibility = View.VISIBLE
                binding.tvStatus.text = "Status: ${if (result.isSafe) "SAFE" else "UNSAFE"}"
                binding.tvStatus.setTextColor(if (result.isSafe) Color.parseColor("#4CAF50") else Color.RED)
                
                binding.tvDimensions.text = "Dimensions: ${"%.2f".format(result.length)} x ${"%.2f".format(result.width)} m"
                binding.tvThickness.text = "Wall: ${result.wallThickness.toInt()}mm, Base: ${result.baseThickness.toInt()}mm"
                
                // Efficiency Logic (Placeholder or from engine if available)
                val efficiency = if (result.isOptimal) 90 else 65
                binding.tvEfficiency.text = "Efficiency Score: $efficiency%"
                
                val warnings = result.safetyChecks.filter { !it.isSafe }.map { it.name }
                if (warnings.isNotEmpty()) {
                    binding.tvWarnings.visibility = View.VISIBLE
                    binding.tvWarnings.text = "Warnings: ${warnings.joinToString(", ")}"
                } else {
                    binding.tvWarnings.visibility = View.GONE
                }
            } else {
                binding.resultCard.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(this) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }
    }
}
