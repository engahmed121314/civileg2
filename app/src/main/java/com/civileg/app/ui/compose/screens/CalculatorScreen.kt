package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onNavigateBack: () -> Unit = {}
) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    var history by remember { mutableStateOf(listOf<String>()) }
    var isNewCalculation by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الحاسبة العلمية", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Display Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Expression
                    Text(
                        text = expression,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Result
                    Text(
                        text = result,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // History
            if (history.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            "السجل",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        history.takeLast(3).reversed().forEach { h ->
                            Text(
                                h,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 1.dp)
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
                // Row 1: C, (, ), ÷
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
                    CalcButton("=", Modifier.weight(1f), Color(0xFF2E7D32)) {
                        try {
                            val evalResult = evaluateExpression(expression)
                            val formatted = formatResult(evalResult)
                            history = history + "$expression = $formatted"
                            result = formatted
                            expression = formatted
                            isNewCalculation = true
                        } catch (e: Exception) {
                            result = "خطأ"
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun appendNumber(num: String, getExpr: () -> String, getIsNew: () -> Boolean, set: (String) -> Unit) {
    val expanded = when (num) {
        "π" -> "3.14159265"
        "e" -> "2.71828183"
        else -> num
    }
    if (getIsNew()) {
        set(expanded)
    } else {
        set(getExpr() + expanded)
    }
}

private fun appendFunc(func: String, getExpr: () -> String, getIsNew: () -> Boolean, set: (String) -> Unit) {
    if (getIsNew()) {
        set("$func(")
    } else {
        set(getExpr() + "$func(")
    }
}

// ═══════════════════════════════════════════════════════════════
// Expression Evaluator with proper precedence
// ═══════════════════════════════════════════════════════════════

private fun evaluateExpression(expr: String): Double {
    val tokens = tokenize(expr)
    val parser = ExprParser(tokens)
    val result = parser.parseExpression()
    return result
}

private fun tokenize(expr: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    val s = expr.replace("×", "*").replace("÷", "/").replace("π", "3.14159265").replace("e", "2.71828183")

    while (i < s.length) {
        val c = s[i]
        when {
            c.isWhitespace() -> i++
            c.isDigit() || c == '.' -> {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                tokens.add(Token.Number(s.substring(start, i).toDouble()))
            }
            c == '+' -> { tokens.add(Token.Plus); i++ }
            c == '-' -> {
                if (tokens.isEmpty() || tokens.last() is Token.Plus || tokens.last() is Token.Minus ||
                    tokens.last() is Token.Multiply || tokens.last() is Token.Divide || tokens.last() is Token.Power ||
                    tokens.last() is Token.LParen) {
                    tokens.add(Token.UnaryMinus); i++
                } else {
                    tokens.add(Token.Minus); i++
                }
            }
            c == '*' -> { tokens.add(Token.Multiply); i++ }
            c == '/' -> { tokens.add(Token.Divide); i++ }
            c == '^' -> { tokens.add(Token.Power); i++ }
            c == '(' -> { tokens.add(Token.LParen); i++ }
            c == ')' -> { tokens.add(Token.RParen); i++ }
            c == '!' -> { tokens.add(Token.Factorial); i++ }
            c.isLetter() -> {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val func = s.substring(start, i)
                tokens.add(Token.Function(func))
            }
            else -> i++
        }
    }
    return tokens
}

private sealed class Token {
    data class Number(val value: Double) : Token()
    object Plus : Token()
    object Minus : Token()
    object UnaryMinus : Token()
    object Multiply : Token()
    object Divide : Token()
    object Power : Token()
    object LParen : Token()
    object RParen : Token()
    object Factorial : Token()
    data class Function(val name: String) : Token()
}

private class ExprParser(private val tokens: List<Token>) {
    private var pos = 0

    fun parseExpression(): Double {
        return parseAddSub()
    }

    private fun parseAddSub(): Double {
        var left = parseMulDiv()
        while (pos < tokens.size && (tokens[pos] is Token.Plus || tokens[pos] is Token.Minus)) {
            val op = tokens[pos]
            pos++
            val right = parseMulDiv()
            left = if (op is Token.Minus) left - right else left + right
        }
        return left
    }

    private fun parseMulDiv(): Double {
        var left = parsePower()
        while (pos < tokens.size && (tokens[pos] is Token.Multiply || tokens[pos] is Token.Divide)) {
            val op = tokens[pos]
            pos++
            val right = parsePower()
            left = if (op is Token.Divide) left / right else left * right
        }
        return left
    }

    private fun parsePower(): Double {
        var base = parseUnary()
        if (pos < tokens.size && tokens[pos] is Token.Power) {
            pos++
            val exp = parsePower() // right-associative
            base = base.pow(exp)
        }
        return base
    }

    private fun parseUnary(): Double {
        if (pos < tokens.size && tokens[pos] is Token.UnaryMinus) {
            pos++
            return -parseUnary()
        }
        return parsePostfix()
    }

    private fun parsePostfix(): Double {
        var value = parsePrimary()
        while (pos < tokens.size && tokens[pos] is Token.Factorial) {
            pos++
            value = factorial(value.toInt())
        }
        return value
    }

    private fun parsePrimary(): Double {
        if (pos >= tokens.size) return 0.0
        return when (val tok = tokens[pos]) {
            is Token.Number -> { pos++; tok.value }
            is Token.LParen -> {
                pos++
                val parenValue = parseExpression()
                if (pos < tokens.size && tokens[pos] is Token.RParen) pos++
                parenValue
            }
            is Token.Function -> {
                pos++
                val funcName = tok.name
                if (pos < tokens.size && tokens[pos] is Token.LParen) pos++
                val arg = parseExpression()
                if (pos < tokens.size && tokens[pos] is Token.RParen) pos++
                applyFunction(funcName, arg)
            }
            else -> 0.0
        }
    }

    private fun applyFunction(name: String, arg: Double): Double {
        val degrees = Math.toRadians(arg)
        return when (name.lowercase()) {
            "sin" -> sin(degrees)
            "cos" -> cos(degrees)
            "tan" -> tan(degrees)
            "log" -> log10(arg)
            "ln" -> ln(arg)
            "√", "sqrt" -> sqrt(arg)
            "abs" -> abs(arg)
            "1/" -> 1.0 / arg
            else -> arg
        }
    }

    private fun factorial(n: Int): Double {
        if (n < 0) return Double.NaN
        var result = 1.0
        for (i in 2..n) result *= i
        return result
    }
}

private fun formatResult(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "خطأ"
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.8f", value).trimEnd('0').trimEnd('.')
    }
}