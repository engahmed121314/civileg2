package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.Intent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── الكود الافتراضي ───
            SettingsSectionCard(
                title = "الكود الإنشائي الافتراضي",
                icon = Icons.Default.Gavel,
                iconColor = Color(0xFF1565C0)
            ) {
                DesignCode.entries.forEach { code ->
                    val codeName = when (code) {
                        DesignCode.ECP -> "الكود المصري ECP 203"
                        DesignCode.ACI -> "الكود الأمريكي ACI 318"
                        DesignCode.SBC -> "الكود السعودي SBC 304"
                    }
                    SettingsRadioRow(
                        label = codeName,
                        selected = settings.defaultCode == code,
                        onClick = { viewModel.setDefaultCode(code) }
                    )
                }
            }

            // ─── لغة التطبيق ───
            SettingsSectionCard(
                title = "لغة التطبيق",
                icon = Icons.Default.Language,
                iconColor = Color(0xFF2E7D32)
            ) {
                SettingsRadioRow(
                    label = "العربية",
                    subtitle = "Arabic",
                    selected = LocaleHelper.getLocale(context) == "ar",
                    onClick = {
                        viewModel.setReportLanguage("ar")
                        LocaleHelper.setLocale(context, "ar")
                        restartActivity(context)
                    }
                )
                SettingsRadioRow(
                    label = "English (الإنجليزية)",
                    subtitle = "English",
                    selected = LocaleHelper.getLocale(context) == "en",
                    onClick = {
                        viewModel.setReportLanguage("en")
                        LocaleHelper.setLocale(context, "en")
                        restartActivity(context)
                    }
                )
            }

            // ─── نظام الوحدات ───
            SettingsSectionCard(
                title = "نظام الوحدات",
                icon = Icons.Default.Straighten,
                iconColor = Color(0xFF7B1FA2)
            ) {
                SettingsRadioRow(
                    label = "النظام الدولي SI (mm, kN, MPa)",
                    selected = settings.unitSystem == "SI",
                    onClick = { viewModel.setUnitSystem("SI") }
                )
                SettingsRadioRow(
                    label = "النظام الإمبريالي (in, kip, psi)",
                    selected = settings.unitSystem == "Imperial",
                    onClick = { viewModel.setUnitSystem("Imperial") }
                )
            }

            // ─── أسعار المواد ───
            SettingsSectionCard(
                title = "أسعار المواد",
                icon = Icons.Default.Payments,
                iconColor = Color(0xFFE65100)
            ) {
                SettingsPriceInput(
                    label = "سعر الخرسانة (لكل م³)",
                    value = settings.concretePrice.toString(),
                    currency = settings.currency,
                    onValueChange = {
                        it.toDoubleOrNull()?.let { v -> viewModel.setConcretePrice(v) }
                    }
                )
                SettingsPriceInput(
                    label = "سعر الحديد (لكل طن)",
                    value = settings.steelPrice.toString(),
                    currency = settings.currency,
                    onValueChange = {
                        it.toDoubleOrNull()?.let { v -> viewModel.setSteelPrice(v) }
                    }
                )
                SettingsPriceInput(
                    label = "سعر الشدات (لكل م²)",
                    value = settings.formworkPrice.toString(),
                    currency = settings.currency,
                    onValueChange = {
                        it.toDoubleOrNull()?.let { v -> viewModel.setFormworkPrice(v) }
                    }
                )
            }

            // ─── العملة ───
            SettingsSectionCard(
                title = "العملة الافتراضية",
                icon = Icons.Default.AttachMoney,
                iconColor = Color(0xFF00838F)
            ) {
                listOf(
                    "EGP" to "جنيه مصري",
                    "SAR" to "ريال سعودي",
                    "USD" to "دولار أمريكي",
                    "AED" to "درهم إماراتي"
                ).forEach { (code, name) ->
                    SettingsRadioRow(
                        label = "$name ($code)",
                        selected = settings.currency == code,
                        onClick = { viewModel.setCurrency(code) }
                    )
                }
            }

            // ─── حول التطبيق ───
            SettingsSectionCard(
                title = "حول التطبيق",
                icon = Icons.Default.Info,
                iconColor = Color(0xFF37474F)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الإصدار", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("v2.0.0", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الأكواد المدعومة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("ECP 203 • ACI 318 • SBC 304", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    HorizontalDivider()
                    Text(
                        text = "تنبيه: هذا التطبيق للأغراض التعليمية والتصميم المبدئي فقط. " +
                                "جميع الحسابات يجب أن يتم مراجعتها من قبل مهندس إنشائي مرخص.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تطوير فريق CivilEG — هندسة برمجية متخصصة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Reusable Components
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRadioRow(
    label: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsPriceInput(
    label: String,
    value: String,
    currency: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        suffix = { Text(currency, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
}

private fun restartActivity(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    if (context is android.app.Activity) {
        context.finish()
    }
}