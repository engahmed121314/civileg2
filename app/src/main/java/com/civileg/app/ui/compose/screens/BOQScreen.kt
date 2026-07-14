package com.civileg.app.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.calculations.ecp.TrialRunManager
import com.civileg.app.utils.EstimationEngine
import com.civileg.app.utils.PdfGenerator
import com.civileg.app.viewmodel.BOQViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BOQScreen(
    projectViewModel: ProjectViewModel = hiltViewModel(),
    boqViewModel: BOQViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    var selectedMainTab by remember { mutableIntStateOf(1) }
    val mainTabs = listOf("مقايسة التصميمات", "المقدّر الذكي Pro")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حساب الكميات والتكاليف Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SecondaryTabRow(selectedTabIndex = selectedMainTab) {
                mainTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedMainTab == index,
                        onClick = { selectedMainTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedMainTab) {
                0 -> DesignsBOQContent(projectViewModel)
                1 -> SmartEstimatorProContent(boqViewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmartEstimatorProContent(viewModel: BOQViewModel) {
    val context = LocalContext.current
    val estimationResult by viewModel.estimationResult.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    var category by remember { mutableStateOf(EstimationEngine.ProjectCategory.FULL_PROJECT) }
    var projectType by remember { mutableStateOf(EstimationEngine.FullProjectType.RESIDENTIAL) }
    var factoryType by remember { mutableStateOf(EstimationEngine.FactoryStructureType.BOTH) }
    
    var area by remember { mutableStateOf("150") }
    var floors by remember { mutableStateOf("4") }
    var hasBasement by remember { mutableStateOf(false) }
    var landPrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("EGP") }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    
    var trialRunLog by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ماذا تريد أن تحسب اليوم؟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ExposedDropdownMenuBox(expanded = showCurrencyMenu, onExpandedChange = { showCurrencyMenu = !showCurrencyMenu }, modifier = Modifier.width(100.dp)) {
                    OutlinedTextField(value = selectedCurrency, onValueChange = {}, readOnly = true, label = { Text("العملة", fontSize = 10.sp) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                    ExposedDropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) {
                        listOf("EGP", "SAR", "USD", "AED").forEach { curr -> DropdownMenuItem(text = { Text(curr) }, onClick = { selectedCurrency = curr; showCurrencyMenu = false }) }
                    }
                }
            }
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstimationEngine.ProjectCategory.entries.forEach { cat ->
                    val label = when(cat) {
                        EstimationEngine.ProjectCategory.FULL_PROJECT -> "مشروع كامل"
                        EstimationEngine.ProjectCategory.APARTMENT_FINISHING -> "تشطيب شقة"
                        EstimationEngine.ProjectCategory.SPECIFIC_ITEM -> "بند معين"
                        EstimationEngine.ProjectCategory.INVESTMENT_STUDY -> "دراسة جدوى"
                    }
                    FilterChip(selected = category == cat, onClick = { category = cat; viewModel.clearResult() }, label = { Text(label) })
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (category == EstimationEngine.ProjectCategory.FULL_PROJECT || category == EstimationEngine.ProjectCategory.INVESTMENT_STUDY) {
                        Text("نوع المنشأ", style = MaterialTheme.typography.labelMedium)
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EstimationEngine.FullProjectType.entries.forEach { type ->
                                ElevatedFilterChip(selected = projectType == type, onClick = { projectType = type }, label = { Text(type.displayName) })
                            }
                        }
                        OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("مساحة الأرض (م٢)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AspectRatio, null) })
                        if (category == EstimationEngine.ProjectCategory.INVESTMENT_STUDY) {
                            OutlinedTextField(value = landPrice, onValueChange = { landPrice = it }, label = { Text("سعر متر الأرض") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Payments, null) }, suffix = { Text(selectedCurrency) })
                            OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = it }, label = { Text("سعر بيع المتر المتوقع") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null) }, suffix = { Text(selectedCurrency) })
                        }
                        if (projectType != EstimationEngine.FullProjectType.FACTORY) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = floors, onValueChange = { floors = it }, label = { Text("عدد الأدوار") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(16.dp))
                                Switch(checked = hasBasement, onCheckedChange = { hasBasement = it })
                                Text("بدروم", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    } else if (category == EstimationEngine.ProjectCategory.APARTMENT_FINISHING) {
                        OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("مساحة الشقة (م٢)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Button(onClick = {
                val a = area.toDoubleOrNull() ?: 0.0
                val f = floors.toIntOrNull() ?: 1
                val lp = landPrice.toDoubleOrNull() ?: 0.0
                val sp = sellingPrice.toDoubleOrNull() ?: 0.0
                viewModel.estimateFullProject(projectType, a, f, hasBasement, factoryType, lp, sp, selectedCurrency)
                
                // تشغيل المحرك التجريبي وتخزين النتائج للعرض
                trialRunLog = TrialRunManager.runFullProjectSimulation()
            }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                else Text("إصدار كشف الكميات والجدوى الاستثمارية", fontWeight = FontWeight.Bold)
            }
        }

        trialRunLog?.let { log ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("🛡️ تقرير سلامة البيانات (Trial Run)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(log, fontSize = 10.sp, color = Color.DarkGray)
                    }
                }
            }
        }

        estimationResult?.let { res ->
            item { ProfessionalEstimationCard(res) { exportEstimationPdf(context, res) } }
        }
    }
}

@Composable
fun DesignsBOQContent(viewModel: ProjectViewModel) {
    val context = LocalContext.current
    val designs by viewModel.allDesigns.observeAsState(emptyList())
    val projects by viewModel.allProjects.observeAsState(emptyList())
    var selectedProjectId by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(projects) { if (selectedProjectId == -1L && projects.isNotEmpty()) selectedProjectId = projects.first().id }

    val projectDesigns = remember(designs, selectedProjectId) {
        if (selectedProjectId == -1L) emptyList() else designs.filter { it.projectId == selectedProjectId }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (projects.isNotEmpty()) {
            item {
                val selectedIndex = projects.indexOfFirst { it.id == selectedProjectId }.coerceAtLeast(0)
                ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                    projects.forEach { project -> Tab(selected = project.id == selectedProjectId, onClick = { selectedProjectId = project.id }, text = { Text(project.name) }) }
                }
            }
            item { TotalCostCard(projectDesigns.sumOf { it.totalCost }) }
            item {
                Button(onClick = {
                    val p = projects.find { it.id == selectedProjectId }
                    if (p != null) exportProjectBOQPdf(context, p.name, projectDesigns)
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("تصدير كشف كميات المشروع PDF")
                }
            }
            items(projectDesigns) { design -> DesignBOQCard(design) }
        } else {
            item { EmptyStateView() }
        }
    }
}

private fun exportEstimationPdf(context: Context, result: EstimationEngine.EstimationResult) {
    try {
        val file = PdfGenerator.generateEstimationReport(context, result)
        sharePdf(context, file)
    } catch (e: Exception) { Toast.makeText(context, "فشل التصدير: ${e.message}", Toast.LENGTH_SHORT).show() }
}

private fun exportProjectBOQPdf(context: Context, name: String, designs: List<com.civileg.app.db.Design>) {
    try {
        val file = PdfGenerator.generateBOQReport(context, name, designs.sumOf { it.totalCost }, designs.sumOf { it.concreteVolume }, designs.sumOf { it.steelWeight }, designs.map { it.name to it.totalCost })
        sharePdf(context, file)
    } catch (e: Exception) { Toast.makeText(context, "فشل التصدير", Toast.LENGTH_SHORT).show() }
}

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    context.startActivity(Intent.createChooser(intent, "فتح التقرير"))
}

@Composable
fun ProfessionalEstimationCard(res: EstimationEngine.EstimationResult, onExport: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("تقدير مالي وفني متكامل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f %s", res.totalCost, res.currencySymbol), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            res.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(String.format(Locale.US, "%,.0f", item.totalPrice), fontWeight = FontWeight.Bold)
                }
            }
            res.investmentData?.let { invest ->
                Box(modifier = Modifier.padding(top = 16.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Column {
                        Text("📊 دراسة الجدوى", fontWeight = FontWeight.Bold)
                        ResultSummaryRow("العائد المتوقع (ROI)", "${"%.1f".format(invest.roi)}%")
                        ResultSummaryRow("صافي الربح", String.format(Locale.US, "%,.0f %s", invest.netProfit, res.currencySymbol))
                        ResultSummaryRow("هامش الربح", "${"%.1f".format(invest.profitMargin)}%")
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            InvestmentSmallCard("عدد الوحدات", "${invest.estimatedUnits}")
                            InvestmentSmallCard("مدة التنفيذ", "${invest.constructionDurationMonths} شهر")
                        }
                    }
                }
            }
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("تصدير التقرير PDF")
            }
        }
    }
}

@Composable
fun ResultSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InvestmentSmallCard(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun DesignBOQCard(design: com.civileg.app.db.Design) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(design.name, fontWeight = FontWeight.Bold)
                Text(design.type.name, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(String.format(Locale.US, "%,.0f EGP", design.totalCost), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun TotalCostCard(cost: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("إجمالي تكلفة التصميمات المحفوظة", color = Color.White.copy(alpha = 0.8f))
            Text(String.format(Locale.US, "%,.0f EGP", cost), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun EmptyStateView() {
    Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
            Text("لم يتم العثور على مشاريع", color = Color.Gray); Text("قم بإضافة مشروعك الأول للبدء", fontSize = 12.sp, color = Color.Gray)
        }
    }
}
