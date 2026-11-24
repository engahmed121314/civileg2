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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import java.util.Locale
import com.civileg.app.utils.tools.ScientificEvaluator
import com.civileg.app.utils.tools.ScientificEvaluator.AngleMode
import com.civileg.app.utils.tools.ScientificEvaluator.ErrorKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onNavigateBack: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("0") }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }
    var isNewCalculation by rememberSaveable { mutableStateOf(true) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var angleMode by rememberSaveable { mutableStateOf(AngleMode.DEG) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_calculator_title), fontWeight = FontWeight.Bold) },
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
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaterialTheme.dimens.formMaxWidth)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding, vertical = 12.dp),
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
                    AppText(
                        text = expression,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
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
                        AppText(
                            stringResource(R.string.calc_history),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        history.takeLast(10).reversed().forEach { h ->
                            AppText(
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
                ScientificFuncButton(angleMode.name) {
                    angleMode = when (angleMode) { AngleMode.DEG -> AngleMode.RAD; AngleMode.RAD -> AngleMode.GRAD; AngleMode.GRAD -> AngleMode.DEG }
                }
                ScientificFuncButton("sin") { appendFunc("sin", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("cos") { appendFunc("cos", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("tan") { appendFunc("tan", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("asin") { appendFunc("asin", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("acos") { appendFunc("acos", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("atan") { appendFunc("atan", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("sinh") { appendFunc("sinh", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("cosh") { appendFunc("cosh", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("tanh") { appendFunc("tanh", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("∛") { appendFunc("cbrt", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("eˣ") { appendFunc("exp", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("log") { appendFunc("log", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("ln") { appendFunc("ln", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                ScientificFuncButton("√") { appendFunc("sqrt", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
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
                        // Negate the whole expression: -(2+3) is -5. Prepending "-" alone would only negate the first term.
                        if (expression.isNotEmpty()) {
                            expression = if (expression.startsWith("-(") && expression.endsWith(")")) expression.drop(2).dropLast(1)
                            else "-($expression)"
                        }
                    }
                    CalcButton("0", Modifier.weight(1f)) { appendNumber("0", { expression }, { isNewCalculation }) { expression = it; isNewCalculation = false } }
                    CalcButton(".", Modifier.weight(1f)) { expression += "."; isNewCalculation = false }
                    val msgSyntax = stringResource(R.string.calc_err_syntax)
                    val msgDiv0 = stringResource(R.string.calc_err_div0)
                    val msgDomain = stringResource(R.string.calc_err_domain)
                    val msgOverflow = stringResource(R.string.calc_err_overflow)
                    CalcButton("=", Modifier.weight(1f), Color(0xFF2E7D32)) {
                        when (val r = ScientificEvaluator.evaluate(expression, angleMode)) {
                            is ScientificEvaluator.Result.Value -> {
                                val formatted = ScientificEvaluator.format(r.value)
                                history = history + "$expression = $formatted (${angleMode.name})"
                                result = formatted
                                // Scientific text such as 1.5e-05 is not parsable as typed, so carry it forward as a product.
                                expression = if ('e' in formatted) "(" + formatted.replace("e", "×10^") + ")" else formatted
                                isNewCalculation = true
                            }
                            is ScientificEvaluator.Result.Error -> result = when (r.kind) {
                                ErrorKind.SYNTAX -> msgSyntax
                                ErrorKind.DIVIDE_BY_ZERO -> msgDiv0
                                ErrorKind.DOMAIN -> msgDomain
                                ErrorKind.OVERFLOW -> msgOverflow
                            }
                        }
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

    AppButton(
        label = label,
        onClick = onClick,
        modifier = modifier.height(52.dp),
        fillMaxWidth = false,
        containerOverride = bg,
        contentOverride = fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ScientificFuncButton(
    label: String,
    onClick: () -> Unit
) {
    AppButton(
        label = label,
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 52.dp),
        fillMaxWidth = false,
        containerOverride = MaterialTheme.colorScheme.secondaryContainer,
        contentOverride = MaterialTheme.colorScheme.onSecondaryContainer,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
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
