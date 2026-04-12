package com.civilengineer.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * شاشة الإعدادات
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var selectedCurrency by remember { mutableStateOf("EGP") }
    var steelPrice by remember { mutableStateOf("5000") }
    var concretePrice by remember { mutableStateOf("500") }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "عودة")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "العملة والأسعار",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "اختر العملة",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val currencies = listOf(
                            "EGP" to "الجنيه المصري",
                            "SAR" to "الريال السعودي",
                            "USD" to "الدولار الأمريكي",
                            "EUR" to "اليورو",
                            "AED" to "الدرهم الإماراتي"
                        )

                        currencies.forEach { (code, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCurrency == code,
                                    onClick = { selectedCurrency = code }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    Text(code, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "أسعار المواد",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        TextField(
                            value = steelPrice,
                            onValueChange = { steelPrice = it },
                            label = { Text("سعر الفولاذ لكل طن ($selectedCurrency)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            value = concretePrice,
                            onValueChange = { concretePrice = it },
                            label = { Text("سعر الخرسانة لكل م³ ($selectedCurrency)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "الإجراءات",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Button(
                    onClick = { /* Export data */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = "تصدير")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تصدير جميع البيانات")
                }
            }

            item {
                Button(
                    onClick = { /* Import data */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = "استيراد")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استيراد البيانات")
                }
            }

            item {
                Button(
                    onClick = { showAbout = !showAbout },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "عن التطبيق")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("عن التطبيق")
                }
            }

            item {
                Text(
                    text = "التطبيق",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "مساعد المهندس المدني",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("الإصدار: 2.0.0", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "تطبيق شامل لتصميم المنشآت الخرسانية حسب الأكواد المصرية والسعودية والأمريكية",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عن التطبيق") },
        text = {
            Column {
                Text(
                    "مساعد المهندس المدني v2.0.0\n\n" +
                    "تطبيق احترافي وشامل لتصميم جميع أنواع المنشآت الخرسانية:\n\n" +
                    "• تصميم الأعمدة\n" +
                    "• تصميم البلاطات\n" +
                    "• تصميم القواعس\n" +
                    "• تصميم حوائط السند\n" +
                    "• حساب الزلازل\n\n" +
                    "يدعم الأكواد:\n" +
                    "• الكود المصري\n" +
                    "• الكود السعودي\n" +
                    "• الكود الأمريكي (ACI)\n\n" +
                    "المميزات:\n" +
                    "• حصر شامل بناء على المدخلات\n" +
                    "• شرح للمعادلات المستخدمة\n" +
                    "• تحليل إنشائي\n" +
                    "• حساب التكاليف\n" +
                    "• تصدير PDF و Excel\n" +
                    "• محول وحدات متقدم"
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("حسناً")
            }
        }
    )
}