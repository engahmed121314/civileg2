package com.civilengineer.assistant.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.ui.components.*
import com.civilengineer.assistant.ui.theme.*

@Composable
fun SettingsScreen(navController: NavController) {
    var selectedCode by remember { mutableStateOf(DesignCode.EGYPTIAN) }
    var selectedCurrency by remember { mutableStateOf(Currency.EGP) }
    var darkMode by remember { mutableStateOf(false) }
    var showEquations by remember { mutableStateOf(true) }
    var autoQuantity by remember { mutableStateOf(true) }
    var autoCost by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "الإعدادات",
                subtitle = "Settings",
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
            // الكود الافتراضي
            CollapsibleSection(title = "الإعدادات الافتراضية", icon = Icons.Default.Tune, initiallyExpanded = true) {
                EngineeringDropdown(
                    label = "الكود الافتراضي",
                    items = DesignCode.entries,
                    selectedItem = selectedCode,
                    onItemSelected = { selectedCode = it },
                    itemLabel = { it.displayName },
                    icon = Icons.Default.MenuBook
                )
                Spacer(modifier = Modifier.height(8.dp))
                EngineeringDropdown(
                    label = "العملة الافتراضية",
                    items = Currency.entries,
                    selectedItem = selectedCurrency,
                    onItemSelected = { selectedCurrency = it },
                    itemLabel = { "${it.displayName} (${it.symbol})" },
                    icon = Icons.Default.Payments
                )
            }

            // إعدادات العرض
            CollapsibleSection(title = "إعدادات العرض", icon = Icons.Default.Visibility, initiallyExpanded = true) {
                SettingsSwitch("الوضع الداكن", "تفعيل المظهر الداكن", darkMode) { darkMode = it }
                SettingsSwitch("عرض المعادلات", "عرض خطوات الحل والمعادلات", showEquations) { showEquations = it }
                SettingsSwitch("حصر تلقائي", "حساب الحصر تلقائياً مع كل تصميم", autoQuantity) { autoQuantity = it }
                SettingsSwitch("تكلفة تلقائية", "حساب التكلفة تلقائياً", autoCost) { autoCost = it }
            }

            // معلومات التطبيق
            CollapsibleSection(title = "عن التطبيق", icon = Icons.Default.Info, initiallyExpanded = false) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlueVeryLight.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Architecture, contentDescription = null, tint = PrimaryBlue,
                            modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("مساعد المهندس المدني", fontWeight = FontWeight.Bold, color = PrimaryBlueDark)
                        Text("Civil Engineer Assistant", color = TextSecondary)
                        Text("الإصدار 2.0.0", color = TextSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "تطبيق متكامل لتصميم جميع العناصر الإنشائية الخرسانية " +
                            "حسب الكود المصري ECP 203 والكود السعودي SBC 304 " +
                            "والكود الأمريكي ACI 318-19.\n\n" +
                            "يشمل: تصميم الأعمدة والبلاطات والقواعد والكمرات " +
                            "وحوائط السند والخزانات والسلالم وتحليل الزلازل " +
                            "مع حصر شامل للكميات وحساب التكلفة ومحول وحدات.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
        )
    }
}
