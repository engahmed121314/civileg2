package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.viewmodel.SettingsViewModel
import androidx.activity.ComponentActivity

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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Default Design Code
            item {
                SettingsSection(title = "🏗️ Default Design Code") {
                    DesignCode.entries.forEach { code ->
                        RadioButtonRow(
                            label = "${code.displayName} (${code.version})",
                            selected = settings.defaultCode == code,
                            onClick = { viewModel.setDefaultCode(code) }
                        )
                    }
                }
            }
            
            // Material Prices
            item {
                SettingsSection(title = "💰 Material Prices") {
                    PriceInput(
                        label = "Concrete Price (per m³)",
                        value = settings.concretePrice.toString(),
                        currency = settings.currency,
                        onValueChange = { 
                            it.toDoubleOrNull()?.let { v -> viewModel.setConcretePrice(v) }
                        }
                    )
                    PriceInput(
                        label = "Steel Price (per ton)",
                        value = settings.steelPrice.toString(),
                        currency = settings.currency,
                        onValueChange = { 
                            it.toDoubleOrNull()?.let { v -> viewModel.setSteelPrice(v) }
                        }
                    )
                    PriceInput(
                        label = "Formwork Price (per m²)",
                        value = settings.formworkPrice.toString(),
                        currency = settings.currency,
                        onValueChange = { 
                            it.toDoubleOrNull()?.let { v -> viewModel.setFormworkPrice(v) }
                        }
                    )
                }
            }
            
            // Currency
            item {
                SettingsSection(title = "💱 Currency") {
                    listOf("EGP", "USD", "SAR", "AED").forEach { currency ->
                        RadioButtonRow(
                            label = currency,
                            selected = settings.currency == currency,
                            onClick = { viewModel.setCurrency(currency) }
                        )
                    }
                }
            }
            
            // Unit System
            item {
                SettingsSection(title = "📏 Unit System") {
                    RadioButtonRow(
                        label = "SI Units (mm, kN, MPa)",
                        selected = settings.unitSystem == "SI",
                        onClick = { viewModel.setUnitSystem("SI") }
                    )
                    RadioButtonRow(
                        label = "Imperial (in, kip, psi)",
                        selected = settings.unitSystem == "Imperial",
                        onClick = { viewModel.setUnitSystem("Imperial") }
                    )
                }
            }

            // App Language (UI)
            item {
                SettingsSection(title = "🌐 App Language / لغة التطبيق") {
                    RadioButtonRow(
                        label = "العربية (Arabic)",
                        selected = settings.reportLanguage == "ar",
                        onClick = {
                            viewModel.setReportLanguage("ar")
                            viewModel.setAppLanguage("ar")
                            try {
                                val activity = context.findActivity()
                                activity?.let { act ->
                                    com.civileg.app.utils.LocaleHelper.setLocale(act, "ar")
                                    act.recreate()
                                }
                            } catch (_: Exception) {}
                        }
                    )
                    RadioButtonRow(
                        label = "English (الإنجليزية)",
                        selected = settings.reportLanguage == "en",
                        onClick = {
                            viewModel.setReportLanguage("en")
                            viewModel.setAppLanguage("en")
                            try {
                                val activity = context.findActivity()
                                activity?.let { act ->
                                    com.civileg.app.utils.LocaleHelper.setLocale(act, "en")
                                    act.recreate()
                                }
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
            
            // About
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ℹ️ About", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Civil EG Pro v1.0.0")
                        Text("Supports: ECP 203-2020, ACI 318-19, SBC 304-2018")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "⚠️ Disclaimer: This app is for educational and preliminary design purposes only. " +
                            "All calculations must be verified by a licensed structural engineer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun RadioButtonRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

private fun android.content.Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun PriceInput(
    label: String,
    value: String,
    currency: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(currency) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
}
