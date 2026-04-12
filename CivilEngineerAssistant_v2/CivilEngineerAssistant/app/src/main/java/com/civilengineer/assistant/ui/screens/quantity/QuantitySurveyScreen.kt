package com.civilengineer.assistant.ui.screens.quantity

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.ui.components.*
import com.civilengineer.assistant.ui.theme.*
import com.civilengineer.assistant.utils.EngineeringConstants
import kotlin.math.PI
import kotlin.math.ceil

/**
 * شاشة الحصر الشامل
 * يمكن للمستخدم إدخال أبعاد أي عنصر إنشائي وحساب الكميات
 */
@Composable
fun QuantitySurveyScreen(navController: NavController) {
    var selectedElement by remember { mutableStateOf("عمود") }
    val elements = listOf("عمود", "كمرة", "بلاطة مصمتة", "بلاطة هوردي", "قاعدة منفصلة", "قاعدة شريطية", "لبشة", "حائط سند", "سلم")

    // الأبعاد العامة
    var dim1Text by remember { mutableStateOf("") } // عرض أو طول
    var dim2Text by remember { mutableStateOf("") } // ارتفاع أو عرض
    var dim3Text by remember { mutableStateOf("") } // طول أو سمك
    var countText by remember { mutableStateOf("1") } // العدد
    var rebarRatioText by remember { mutableStateOf("1.5") } // نسبة التسليح %

    var showResults by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "الحصر الشامل",
                subtitle = "Quantity Surveying",
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EngineeringDropdown(
                label = "نوع العنصر الإنشائي",
                items = elements,
                selectedItem = selectedElement,
                onItemSelected = { selectedElement = it },
                itemLabel = { it },
                icon = Icons.Default.Category
            )

            CollapsibleSection(title = "الأبعاد", icon = Icons.Default.Straighten, initiallyExpanded = true) {
                when (selectedElement) {
                    "عمود" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            EngineeringInputField(label = "العرض b", value = dim1Text,
                                onValueChange = { dim1Text = it }, unit = "cm", hint = "25", modifier = Modifier.weight(1f))
                            EngineeringInputField(label = "العمق t", value = dim2Text,
                                onValueChange = { dim2Text = it }, unit = "cm", hint = "60", modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringInputField(label = "الارتفاع h", value = dim3Text,
                            onValueChange = { dim3Text = it }, unit = "m", hint = "3.0")
                    }
                    "كمرة" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            EngineeringInputField(label = "العرض b", value = dim1Text,
                                onValueChange = { dim1Text = it }, unit = "cm", hint = "25", modifier = Modifier.weight(1f))
                            EngineeringInputField(label = "الارتفاع t", value = dim2Text,
                                onValueChange = { dim2Text = it }, unit = "cm", hint = "60", modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringInputField(label = "الطول L", value = dim3Text,
                            onValueChange = { dim3Text = it }, unit = "m", hint = "6.0")
                    }
                    "بلاطة مصمتة", "بلاطة هوردي", "لبشة" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            EngineeringInputField(label = "الطول", value = dim1Text,
                                onValueChange = { dim1Text = it }, unit = "m", hint = "5.0", modifier = Modifier.weight(1f))
                            EngineeringInputField(label = "العرض", value = dim2Text,
                                onValueChange = { dim2Text = it }, unit = "m", hint = "4.0", modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringInputField(label = "السمك", value = dim3Text,
                            onValueChange = { dim3Text = it }, unit = "cm", hint = "12")
                    }
                    "قاعدة منفصلة" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            EngineeringInputField(label = "الطول", value = dim1Text,
                                onValueChange = { dim1Text = it }, unit = "m", hint = "2.0", modifier = Modifier.weight(1f))
                            EngineeringInputField(label = "العرض", value = dim2Text,
                                onValueChange = { dim2Text = it }, unit = "m", hint = "2.0", modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringInputField(label = "السمك", value = dim3Text,
                            onValueChange = { dim3Text = it }, unit = "cm", hint = "60")
                    }
                    else -> {
                        EngineeringInputField(label = "البعد 1", value = dim1Text,
                            onValueChange = { dim1Text = it }, unit = "m", hint = "1.0")
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringInputField(label = "البعد 2", value = dim2Text,
                            onValueChange = { dim2Text = it }, unit = "m", hint = "1.0")
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringInputField(label = "البعد 3", value = dim3Text,
                            onValueChange = { dim3Text = it }, unit = "m", hint = "1.0")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EngineeringInputField(label = "العدد", value = countText,
                        onValueChange = { countText = it }, unit = "", hint = "1", modifier = Modifier.weight(1f))
                    EngineeringInputField(label = "نسبة التسليح", value = rebarRatioText,
                        onValueChange = { rebarRatioText = it }, unit = "%", hint = "1.5", modifier = Modifier.weight(1f))
                }
            }

            CalculateButton(
                onClick = { showResults = true },
                text = "حساب الحصر"
            )

            if (showResults) {
                val d1 = dim1Text.toDoubleOrNull() ?: 0.0
                val d2 = dim2Text.toDoubleOrNull() ?: 0.0
                val d3 = dim3Text.toDoubleOrNull() ?: 0.0
                val count = countText.toIntOrNull() ?: 1
                val rebarRatio = (rebarRatioText.toDoubleOrNull() ?: 1.5) / 100.0

                // حساب الحجم
                val volume: Double
                val formwork: Double
                val elementType: String

                when (selectedElement) {
                    "عمود" -> {
                        volume = d1 / 100.0 * d2 / 100.0 * d3 * count
                        formwork = 2 * (d1 / 100.0 + d2 / 100.0) * d3 * count
                        elementType = "column"
                    }
                    "كمرة" -> {
                        volume = d1 / 100.0 * d2 / 100.0 * d3 * count
                        formwork = (d1 / 100.0 + 2 * d2 / 100.0) * d3 * count
                        elementType = "beam"
                    }
                    "بلاطة مصمتة" -> {
                        volume = d1 * d2 * d3 / 100.0 * count
                        formwork = d1 * d2 * count
                        elementType = "slab"
                    }
                    "بلاطة هوردي" -> {
                        volume = d1 * d2 * d3 / 100.0 * 0.55 * count // 55% concrete
                        formwork = d1 * d2 * count
                        elementType = "slab"
                    }
                    "قاعدة منفصلة" -> {
                        volume = d1 * d2 * d3 / 100.0 * count
                        formwork = 2 * (d1 + d2) * d3 / 100.0 * count
                        elementType = "foundation"
                    }
                    "لبشة" -> {
                        volume = d1 * d2 * d3 / 100.0 * count
                        formwork = 2 * (d1 + d2) * d3 / 100.0 * count
                        elementType = "foundation"
                    }
                    else -> {
                        volume = d1 * d2 * d3 * count
                        formwork = 2 * (d1 * d2 + d1 * d3 + d2 * d3) * count
                        elementType = "wall"
                    }
                }

                val steelWeight = volume * rebarRatio * 7850 // kg
                val wasteC = EngineeringConstants.WasteFactors.getConcreteWaste(elementType)
                val wasteS = EngineeringConstants.WasteFactors.getSteelWaste("straight")

                ResultCard(title = "نتائج الحصر - $selectedElement × $count") {
                    ResultRow("حجم الخرسانة", String.format("%.3f", volume), "م³", isHighlighted = true, icon = Icons.Default.Layers)
                    ResultRow("+ هالك خرسانة (${String.format("%.1f", wasteC * 100)}%)", String.format("%.3f", volume * (1 + wasteC)), "م³")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ResultRow("وزن الحديد", String.format("%.1f", steelWeight), "كجم", isHighlighted = true, icon = Icons.Default.LinearScale)
                    ResultRow("وزن الحديد بالطن", String.format("%.3f", steelWeight / 1000), "طن")
                    ResultRow("+ هالك حديد (${String.format("%.1f", wasteS * 100)}%)", String.format("%.1f", steelWeight * (1 + wasteS)), "كجم")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ResultRow("مساحة الشدات", String.format("%.2f", formwork), "م²", icon = Icons.Default.GridOn)
                    ResultRow("+ هالك شدات (5%)", String.format("%.2f", formwork * 1.05), "م²")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ResultRow("معدل الحديد / م³ خرسانة", String.format("%.1f", if (volume > 0) steelWeight / volume else 0.0), "كجم/م³",
                        icon = Icons.Default.Analytics)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
