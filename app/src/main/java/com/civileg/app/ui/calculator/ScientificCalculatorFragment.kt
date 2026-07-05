package com.civileg.app.ui.calculator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.civileg.app.databinding.FragmentScientificCalculatorBinding
import kotlin.math.*

class ScientificCalculatorFragment : Fragment() {
    
    private var _binding: FragmentScientificCalculatorBinding? = null
    private val binding get() = _binding!!
    
    private var currentExpression = ""
    private var isNewCalculation = true
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScientificCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupNumpad()
        setupScientificFunctions()
    }
    
    private fun setupNumpad() {
        val numberButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnDot
        )
        
        numberButtons.forEach { button ->
            button.setOnClickListener {
                appendNumber(button.text.toString())
            }
        }
        
        binding.btnPlus.setOnClickListener { appendOperator("+") }
        binding.btnMinus.setOnClickListener { appendOperator("-") }
        binding.btnMultiply.setOnClickListener { appendOperator("×") }
        binding.btnDivide.setOnClickListener { appendOperator("÷") }
        binding.btnPower.setOnClickListener { appendOperator("^") }
        
        binding.btnClear.setOnClickListener { clear() }
        binding.btnDelete.setOnClickListener { delete() }
        binding.btnEquals.setOnClickListener { calculate() }
    }
    
    private fun setupScientificFunctions() {
        binding.btnSin.setOnClickListener { applyFunction("sin") }
        binding.btnCos.setOnClickListener { applyFunction("cos") }
        binding.btnTan.setOnClickListener { applyFunction("tan") }
        binding.btnLog.setOnClickListener { applyFunction("log") }
        binding.btnLn.setOnClickListener { applyFunction("ln") }
        binding.btnSqrt.setOnClickListener { applyFunction("√") }
        binding.btnSquare.setOnClickListener { appendOperator("^2") }
        binding.btnPi.setOnClickListener { appendNumber("π") }
        binding.btnE.setOnClickListener { appendNumber("e") }
        binding.btnFactorial.setOnClickListener { appendOperator("!") }
        binding.btnInverse.setOnClickListener { applyFunction("1/") }
        binding.btnAbs.setOnClickListener { applyFunction("abs") }
    }
    
    private fun appendNumber(number: String) {
        if (isNewCalculation) {
            currentExpression = ""
            isNewCalculation = false
        }
        currentExpression += when (number) {
            "π" -> "3.14159"
            "e" -> "2.71828"
            else -> number
        }
        updateDisplay()
    }
    
    private fun appendOperator(operator: String) {
        isNewCalculation = false
        currentExpression += operator
        updateDisplay()
    }
    
    private fun applyFunction(func: String) {
        isNewCalculation = false
        currentExpression = "$func($currentExpression)"
        updateDisplay()
    }
    
    private fun clear() {
        currentExpression = ""
        updateDisplay()
        binding.tvResult.text = "0"
    }
    
    private fun delete() {
        if (currentExpression.isNotEmpty()) {
            currentExpression = currentExpression.dropLast(1)
            updateDisplay()
        }
    }
    
    private fun calculate() {
        try {
            val result = evaluateExpression(currentExpression)
            binding.tvResult.text = formatResult(result)
            binding.tvExpression.text = currentExpression
            currentExpression = result.toString()
            isNewCalculation = true
        } catch (e: Exception) {
            binding.tvResult.text = "Error"
        }
    }
    
    private fun evaluateExpression(expr: String): Double {
        // Simplified evaluation logic
        // For a real app, integrate a library like exp4j or mXparser
        return when {
            expr.startsWith("sin(") -> sin(Math.toRadians(expr.substringAfter("(").substringBefore(")").toDouble()))
            expr.startsWith("cos(") -> cos(Math.toRadians(expr.substringAfter("(").substringBefore(")").toDouble()))
            expr.startsWith("tan(") -> tan(Math.toRadians(expr.substringAfter("(").substringBefore(")").toDouble()))
            expr.startsWith("log(") -> log10(expr.substringAfter("(").substringBefore(")").toDouble())
            expr.startsWith("ln(") -> ln(expr.substringAfter("(").substringBefore(")").toDouble())
            expr.startsWith("√(") -> sqrt(expr.substringAfter("(").substringBefore(")").toDouble())
            expr.contains("^") -> {
                val parts = expr.split("^")
                parts[0].toDouble().pow(parts[1].toDouble())
            }
            expr.contains("+") -> expr.split("+")[0].toDouble() + expr.split("+")[1].toDouble()
            expr.contains("-") -> expr.split("-")[0].toDouble() - expr.split("-")[1].toDouble()
            expr.contains("×") -> expr.split("×")[0].toDouble() * expr.split("×")[1].toDouble()
            expr.contains("÷") -> expr.split("÷")[0].toDouble() / expr.split("÷")[1].toDouble()
            else -> expr.toDouble()
        }
    }
    
    private fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.6f", value).trimEnd('0').trimEnd('.')
        }
    }
    
    private fun updateDisplay() {
        binding.tvExpression.text = currentExpression
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
