package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onNavigateBack: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    var history by remember { mutableStateOf(listOf<String>()) }
    var isNewCalculation by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_calculator_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showHistory = !showHistory }) {
                            Icon(Icons.Default.History, contentDescription = "History")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Display Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = expression,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // History collapsible
            if (showHistory && history.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            stringResource(R.string.calc_history),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        history.takeLast(10).reversed().forEach { h ->
                            Text(
                                h,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Scientific functions scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScientificFuncButton("sin") { appendFunc("sin", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("cos") { appendFunc("cos", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("tan") { appendFunc("tan", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("log") { appendFunc("log", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("ln") { appendFunc("ln", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("√") { appendFunc("√", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("x²") { expression += "^2"; isNewCalculation = false }
                ScientificFuncButton("π") { appendNumber("π", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("e") { appendNumber("e", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("n!") { expression += "!"; isNewCalculation = false }
                ScientificFuncButton("1/x") { appendFunc("1/", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("|x|") { appendFunc("abs", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("(") { expression += "("; isNewCalculation = false }
                ScientificFuncButton(")") { expression += ")"; isNewCalculation = false }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main keypad - 5 rows x 4 columns
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: C, ⌫, ^, ÷
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalcButton("C", Modifier.weight(1f), Color(0xFFD32F2F)) {
                        expression = ""; result = "0"; isNewCalculation = true
                    }
                    CalcButton("⌫", Modifier.weight(1f), Color(0xFFD32F2F)) {
                        if (expression.isNotEmpty()) expression = expression.dropLast(1)
                    }
                    CalcButton("^", Modifier.weight(1f), Color(0xFF1565C0)) {
                        expression += "^"; isNewCalculation = false
                    }
                    CalcButton("÷", Modifier.weight(1f), Color(0xFF1565C0)) {
                        expression += "÷"; isNewCalculation = false
                    }
                }
                // Row 2: 7, 8, 9, ×
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalcButton("7", Modifier.weight(1f)) { appendNumber("7", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("8", Modifier.weight(1f)) { appendNumber("8", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("9", Modifier.weight(1f)) { appendNumber("9", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("×", Modifier.weight(1f), Color(0xFF1565C0)) {
                        expression += "×"; isNewCalculation = false
                    }
                }
                // Row 3: 4, 5, 6, -
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalcButton("4", Modifier.weight(1f)) { appendNumber("4", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("5", Modifier.weight(1f)) { appendNumber("5", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("6", Modifier.weight(1f)) { appendNumber("6", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("-", Modifier.weight(1f), Color(0xFF1565C0)) {
                        expression += "-"; isNewCalculation = false
                    }
                }
                // Row 4: 1, 2, 3, +
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalcButton("1", Modifier.weight(1f)) { appendNumber("1", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("2", Modifier.weight(1f)) { appendNumber("2", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("3", Modifier.weight(1f)) { appendNumber("3", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton("+", Modifier.weight(1f), Color(0xFF1565C0)) {
                        expression += "+"; isNewCalculation = false
                    }
                }
                // Row 5: ±, 0, ., =
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalcButton("±", Modifier.weight(1f)) {
                        if (expression.isNotEmpty()) {
                            if (expression.startsWith("-")) expression = expression.drop(1)
                            else expression = "-$expression"
                        }
                    }
                    CalcButton("0", Modifier.weight(1f)) { appendNumber("0", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton(".", Modifier.weight(1f)) { expression += "."; isNewCalculation = false }
                    val calcErrorMsg = stringResource(R.string.error)
                    CalcButton("=", Modifier.weight(1f), Color(0xFF2E7D32)) {
                        try {
                            val evalResult = evaluateExpression(expression)
                            val formatted = formatResult(evalResult, calcErrorMsg)
                            history = history + "$expression = $formatted"
                            result = formatted
                            expression = formatted
                            isNewCalculation = true
                        } catch (e: Exception) {
                            result = calcErrorMsg
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    modifier: Modifier = Modifier,
    bgColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val bg = if (bgColor != Color.Unspecified) bgColor else MaterialTheme.colorScheme.surface
    val fg = if (bgColor != Color.Unspecified) Color.White else MaterialTheme.colorScheme.onSurface

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ScientificFuncButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun appendNumber(
    num: String,
    getExpr: () -> String,
    getIsNew: () -> Boolean,
    setExpr: (String) -> Unit
) {
    val expr = getExpr()
    if (getIsNew()) {
        setExpr(num)
    } else {
        setExpr(expr + num)
    }
}

private fun appendFunc(
    func: String,
    getExpr: () -> String,
    getIsNew: () -> Boolean,
    setExpr: (String) -> Unit
) {
    val expr = getExpr()
    if (getIsNew() || expr == "0") {
        setExpr("$func(")
    } else {
        setExpr(expr + "$func(")
    }
}

private fun evaluateExpression(expr: String): Double {
    val sanitized = expr
        .replace("×", "*")
        .replace("÷", "/")
        .replace("π", PI.toString())
        .replace("e", E.toString())

    return object : Any() {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < sanitized.length) sanitized[pos].toInt() else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.toInt()) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < sanitized.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.toInt())) x += parseTerm() // addition
                else if (eat('-'.toInt())) x -= parseTerm() // subtraction
                else return x
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('*'.toInt())) x *= parseFactor() // multiplication
                else if (eat('/'.toInt())) x /= parseFactor() // division
                else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.toInt())) return parseFactor()
            if (eat('-'.toInt())) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.toInt())) {
                x = parseExpression()
                eat(')'.toInt())
            } else if ((ch >= '0'.toInt() && ch <= '9'.toInt()) || ch == '.'.toInt()) {
                while ((ch >= '0'.toInt() && ch <= '9'.toInt()) || ch == '.'.toInt()) nextChar()
                x = sanitized.substring(startPos, pos).toDouble()
            } else if (ch >= 'a'.toInt() && ch <= 'z'.toInt()) {
                while (ch >= 'a'.toInt() && ch <= 'z'.toInt()) nextChar()
                val func = sanitized.substring(startPos, pos)
                if (eat('('.toInt())) {
                    x = parseExpression()
                    eat(')'.toInt())
                } else {
                    x = parseFactor()
                }
                x = when (func) {
                    "sin" -> sin(Math.toRadians(x))
                    "cos" -> cos(Math.toRadians(x))
                    "tan" -> tan(Math.toRadians(x))
                    "log" -> log10(x)
                    "ln" -> ln(x)
                    "sqrt", "√" -> sqrt(x)
                    "abs" -> abs(x)
                    else -> throw RuntimeException("Unknown function: $func")
                }
            } else {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }

            if (eat('^'.toInt())) x = x.pow(parseFactor()) // exponentiation
            if (eat('!'.toInt())) {
                var fact = 1.0
                for (i in 1..x.toInt()) fact *= i.toDouble()
                x = fact
            }

            return x
        }
    }.parse()
}

private fun formatResult(value: Double, errorMsg: String): String {
    if (!value.isFinite()) return errorMsg
    return if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
    }
}
