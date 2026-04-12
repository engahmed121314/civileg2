package com.civilengineer.assistant.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.ui.theme.*

// ═══════════════════════════════════════════════════════════
// مكون إدخال رقمي هندسي
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineeringInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String = "",
    hint: String = "",
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String = "",
    icon: ImageVector? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onValueChange(newValue)
                }
            },
            label = { Text(label) },
            placeholder = if (hint.isNotEmpty()) {{ Text(hint, color = TextSecondary) }} else null,
            leadingIcon = icon?.let { { Icon(it, contentDescription = null, tint = PrimaryBlue) } },
            trailingIcon = if (unit.isNotEmpty()) {
                {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else null,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                focusedLabelColor = PrimaryBlue,
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError && errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = UnsafeRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// مكون القائمة المنسدلة
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EngineeringDropdown(
    label: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = itemLabel(selectedItem),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = icon?.let { { Icon(it, contentDescription = null, tint = PrimaryBlue) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemLabel(item),
                            fontWeight = if (item == selectedItem) FontWeight.Bold else FontWeight.Normal,
                            color = if (item == selectedItem) PrimaryBlue else TextPrimary
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                    leadingIcon = if (item == selectedItem) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue) }
                    } else null
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// زر حساب رئيسي
// ═══════════════════════════════════════════════════════════

@Composable
fun CalculateButton(
    onClick: () -> Unit,
    text: String = "حساب",
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            contentColor = Color.White,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════
// بطاقة النتائج
// ═══════════════════════════════════════════════════════════

@Composable
fun ResultCard(
    title: String,
    isSafe: Boolean? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (isSafe) {
                true -> SafeGreenBg
                false -> UnsafeRedBg
                null -> SurfaceLight
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlueDark
                )
                isSafe?.let {
                    SafetyBadge(isSafe = it)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════════
// شارة الأمان
// ═══════════════════════════════════════════════════════════

@Composable
fun SafetyBadge(isSafe: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSafe) SafeGreenLight.copy(alpha = 0.3f) else UnsafeRedLight.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, if (isSafe) SafeGreen else UnsafeRed)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSafe) SafeGreen else UnsafeRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isSafe) "آمن" else "غير آمن",
                color = if (isSafe) SafeGreen else UnsafeRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// صف نتيجة
// ═══════════════════════════════════════════════════════════

@Composable
fun ResultRow(
    label: String,
    value: String,
    unit: String = "",
    isHighlighted: Boolean = false,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (isHighlighted) Modifier.background(
                    PrimaryBlueVeryLight.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                ).padding(8.dp)
                else Modifier.padding(horizontal = 8.dp)
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) PrimaryBlueDark else TextPrimary
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// بطاقة خطوة معادلة
// ═══════════════════════════════════════════════════════════

@Composable
fun EquationStepCard(
    step: EquationStep,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        border = BorderStroke(1.dp, PrimaryBlueVeryLight)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // رقم الخطوة
                Surface(
                    shape = CircleShape,
                    color = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${step.stepNumber}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlueDark
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = PrimaryBlue
                )
            }

            // المعادلة
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFECEFF1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = step.equation,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    // التعويض
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "التعويض:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE3F2FD),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = step.substitution,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryBlueDark
                        )
                    }

                    // النتيجة
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SafeGreenBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = step.result,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeGreen
                        )
                    }

                    // مرجع الكود
                    if (step.codeReference.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = SecondaryOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = step.codeReference,
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryOrange,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // الشرح
                    if (step.explanation.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// بطاقة فحص الكود
// ═══════════════════════════════════════════════════════════

@Composable
fun CodeCheckCard(check: CodeCheck) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (check.isPassed) SafeGreenBg else UnsafeRedBg,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (check.isPassed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (check.isPassed) SafeGreen else UnsafeRed,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.checkName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = check.codeClause,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Row {
                Text("المطلوب: ${check.requiredValue}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("الفعلي: ${check.actualValue}", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (check.isPassed) SafeGreen else UnsafeRed
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// بطاقة القسم (الشاشة الرئيسية)
// ═══════════════════════════════════════════════════════════

@Composable
fun CategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.8f)
                        )
                    )
                )
        ) {
            // أيقونة خلفية كبيرة شفافة
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// شريط العنوان العلوي
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineeringTopBar(
    title: String,
    subtitle: String = "",
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextOnPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        },
        navigationIcon = {
            onBackClick?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = Color.White)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PrimaryBlue,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

// ═══════════════════════════════════════════════════════════
// قسم قابل للطي
// ═══════════════════════════════════════════════════════════

@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector = Icons.Default.Info,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlueDark
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = PrimaryBlue
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// بطاقة الحصر
// ═══════════════════════════════════════════════════════════

@Composable
fun QuantitySurveyCard(
    result: QuantitySurveyResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Assessment, contentDescription = null, tint = SecondaryOrange)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "الحصر الشامل",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryOrangeDark
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SecondaryOrangeLight.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            ResultRow("حجم الخرسانة", String.format("%.3f", result.concreteVolume), "م³", icon = Icons.Default.Layers)
            ResultRow("حجم الخرسانة + هالك (${String.format("%.1f", result.wasteFactor * 100)}%)",
                String.format("%.3f", result.concreteWithWaste), "م³")
            ResultRow("وزن الحديد", String.format("%.2f", result.steelWeight), "كجم", icon = Icons.Default.LinearScale)
            ResultRow("وزن الحديد + هالك", String.format("%.2f", result.steelWithWaste), "كجم")
            ResultRow("مساحة الشدات", String.format("%.2f", result.formworkArea), "م²", icon = Icons.Default.GridOn)

            if (result.rebarDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "تفاصيل الحديد:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryOrangeDark
                )
                result.rebarDetails.forEach { detail ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(detail.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("${detail.count} × φ${detail.diameter.toInt()} × ${String.format("%.2f", detail.length)}م = ${String.format("%.2f", detail.totalWeight)} كجم",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// بطاقة التكلفة
// ═══════════════════════════════════════════════════════════

@Composable
fun CostEstimationCard(
    result: CostResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = SafeGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "تقدير التكلفة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeGreen
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            result.breakdown.forEach { item ->
                ResultRow(
                    item.description,
                    "${String.format("%,.0f", item.totalPrice)} ${result.currency.symbol}"
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ResultRow(
                "التكلفة الإجمالية",
                "${String.format("%,.0f", result.totalCost)} ${result.currency.symbol}",
                isHighlighted = true,
                icon = Icons.Default.Payments
            )
        }
    }
}
