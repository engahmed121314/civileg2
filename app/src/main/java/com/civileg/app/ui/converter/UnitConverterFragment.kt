package com.civileg.app.ui.converter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.civileg.app.databinding.FragmentUnitConverterBinding
import com.civileg.app.utils.UnitConverter

class UnitConverterFragment : Fragment() {
    
    private var _binding: FragmentUnitConverterBinding? = null
    private val binding get() = _binding!!
    
    private var selectedCategory: UnitConverter.UnitCategory = UnitConverter.UnitCategory.Length
    private var fromUnit = "m"
    private var toUnit = "ft"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUnitConverterBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategoryTabs()
        setupSpinners()
        setupConvertButton()
        setupSwapButton()
        setupNumpad()
        
        // Initial setup
        updateUnitSpinners()
        highlightSelectedTab(binding.tabLength)
    }
    
    private fun setupCategoryTabs() {
        val tabs = listOf(
            binding.tabLength to UnitConverter.UnitCategory.Length,
            binding.tabArea to UnitConverter.UnitCategory.Area,
            binding.tabVolume to UnitConverter.UnitCategory.Volume,
            binding.tabWeight to UnitConverter.UnitCategory.Weight,
            binding.tabForce to UnitConverter.UnitCategory.Force,
            binding.tabPressure to UnitConverter.UnitCategory.Pressure,
            binding.tabMoment to UnitConverter.UnitCategory.Moment
        )
        
        tabs.forEach { (tab, category) ->
            tab.setOnClickListener {
                selectedCategory = category
                updateUnitSpinners()
                highlightSelectedTab(tab)
            }
        }
    }
    
    private fun highlightSelectedTab(selected: View) {
        listOf(binding.tabLength, binding.tabArea, binding.tabVolume, 
               binding.tabWeight, binding.tabForce, binding.tabPressure, binding.tabMoment)
            .forEach { it.alpha = if (it == selected) 1.0f else 0.5f }
    }
    
    private fun updateUnitSpinners() {
        val units = when (selectedCategory) {
            is UnitConverter.UnitCategory.Length -> arrayOf("m", "cm", "mm", "km", "ft", "in", "yd")
            is UnitConverter.UnitCategory.Area -> arrayOf("m²", "cm²", "mm²", "ft²", "in²", "yd²", "acre")
            is UnitConverter.UnitCategory.Volume -> arrayOf("m³", "cm³", "L", "ft³", "gal")
            is UnitConverter.UnitCategory.Weight -> arrayOf("kg", "g", "ton", "lb", "oz")
            is UnitConverter.UnitCategory.Force -> arrayOf("N", "kN", "MN", "kgf", "tonf", "lbf", "kip")
            is UnitConverter.UnitCategory.Pressure -> arrayOf("Pa", "kPa", "MPa", "bar", "psi", "ksf", "tsf")
            is UnitConverter.UnitCategory.Moment -> arrayOf("N.m", "kN.m", "ton.m", "kgf.m", "lb.ft", "kip.ft")
        }
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, units)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        binding.spinnerFrom.adapter = adapter
        binding.spinnerTo.adapter = adapter
        
        // Set default selections
        binding.spinnerFrom.setSelection(0)
        binding.spinnerTo.setSelection(if (units.size > 4) 4 else 1)
        
        fromUnit = units[0]
        toUnit = units[if (units.size > 4) 4 else 1]
    }
    
    private fun setupSpinners() {
        binding.spinnerFrom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fromUnit = parent?.getItemAtPosition(position).toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        binding.spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                toUnit = parent?.getItemAtPosition(position).toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupSwapButton() {
        binding.btnSwap.setOnClickListener {
            val fromPos = binding.spinnerFrom.selectedItemPosition
            val toPos = binding.spinnerTo.selectedItemPosition
            
            binding.spinnerFrom.setSelection(toPos)
            binding.spinnerTo.setSelection(fromPos)
            
            // Swap values too
            val tempValue = binding.etFrom.text.toString()
            binding.etFrom.setText(binding.etTo.text.toString())
            binding.etTo.setText(tempValue)
        }
    }
    
    private fun setupConvertButton() {
        binding.btnConvert.setOnClickListener {
            convert()
        }
    }
    
    private fun setupNumpad() {
        val numpadButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnDot
        )
        
        numpadButtons.forEach { button ->
            button.setOnClickListener {
                val currentText = binding.etFrom.text.toString()
                val btnText = button.text.toString()
                val newText = when (btnText) {
                    "." -> if (currentText.contains(".")) currentText else currentText + "."
                    else -> if (currentText == "0") btnText else currentText + btnText
                }
                binding.etFrom.setText(newText)
                convert() // Auto-convert on input
            }
        }
        
        binding.btnClear.setOnClickListener {
            binding.etFrom.setText("0")
            binding.etTo.setText("")
        }
        
        binding.btnDelete.setOnClickListener {
            val currentText = binding.etFrom.text.toString()
            binding.etFrom.setText(currentText.dropLast(1).ifEmpty { "0" })
            convert() // Auto-convert on input
        }
    }
    
    private fun convert() {
        try {
            val inputStr = binding.etFrom.text.toString()
            if (inputStr.isEmpty() || inputStr == ".") {
                binding.etTo.setText("")
                return
            }
            val inputValue = inputStr.toDouble()
            val result = UnitConverter.convert(inputValue, fromUnit, toUnit, selectedCategory)
            binding.etTo.setText(String.format("%.4f", result))
        } catch (e: Exception) {
            binding.etTo.setText("Error")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
