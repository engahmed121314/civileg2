package com.civileg.app.ui.compose.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.db.InventoryItem
import com.civileg.app.db.InventoryType
import com.civileg.app.utils.PdfGenerator
import com.civileg.app.viewmodel.InventoryViewModel
import com.civileg.app.ui.compose.components.ResultDataCard
import com.civileg.app.ui.compose.components.PremiumSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.civileg.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    val filteredItems = uiState.items.filter { 
        it.name.contains(searchQuery, ignoreCase = true) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_inventory_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // FIX: Added PDF export button for inventory (was missing — only beam had PDF)
                    IconButton(
                        onClick = {
                            if (filteredItems.isEmpty()) {
                                Toast.makeText(context, "No items to export", Toast.LENGTH_SHORT).show()
                            } else {
                                isExporting = true
                                scope.launch {
                                    val pdfFile = withContext(Dispatchers.IO) {
                                        try {
                                            PdfGenerator.generateInventoryReport(
                                                context,
                                                filteredItems,
                                                "Inventory Report - ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}"
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            null
                                        }
                                    }
                                    isExporting = false
                                    if (pdfFile != null) {
                                        Toast.makeText(context, "PDF saved: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                                        // Open share intent
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                pdfFile
                                            )
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"))
                                    } else {
                                        Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Dashboard Card
            InventoryDashboard(uiState.items)

            // User Guide (Expandable)
            InventoryUserGuide()

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.inventory_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )

            // Type Filter Chips
            TypeFilterRow(
                selectedType = uiState.selectedType,
                onTypeSelected = { viewModel.filterByType(it) }
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if(searchQuery.isEmpty()) stringResource(R.string.inventory_empty) else stringResource(R.string.inventory_no_results), color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        InventoryItemCard(
                            item = item,
                            context = context,
                            onUpdateQuantity = { viewModel.updateQuantity(item, it) },
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddInventoryItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, qty, unit, alertQty ->
                viewModel.addItem(name, type, qty, unit, alertQty)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun InventoryDashboard(items: List<InventoryItem>) {
    val lowStockCount = items.count { it.alertQuantity > 0 && it.quantity <= it.alertQuantity }
    
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.boq_feasibility_section), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DashboardStat(label = "Total Items", value = "${items.size}", color = MaterialTheme.colorScheme.primary)
                DashboardStat(label = "Low Stock", value = "$lowStockCount", color = if (lowStockCount > 0) Color.Red else Color.Gray)
            }
        }
    }
}

@Composable
fun DashboardStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun InventoryUserGuide() {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("How to use Site Inventory?", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("1. Add items like Cement, Sand, or Equipment.\n" +
                     "2. Set an 'Alert Quantity' to get notified when stock is low.\n" +
                     "3. Use the PDF button in the top bar to generate a report for the site manager.\n" +
                     "4. Low stock items are highlighted in red.", 
                     fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
fun TypeFilterRow(selectedType: InventoryType?, onTypeSelected: (InventoryType?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = { onTypeSelected(null) },
            label = { Text(stringResource(R.string.view_all)) }
        )
        InventoryType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(getInventoryTypeNameAr(type)) }
            )
        }
    }
}

@Composable
fun InventoryItemCard(item: InventoryItem, context: Context, onUpdateQuantity: (Double) -> Unit, onDelete: () -> Unit) {
    val isLowStock = item.quantity <= item.alertQuantity
    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(getInventoryTypeNameAr(item.type), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                if (isLowStock) {
                    Icon(Icons.Default.Warning, contentDescription = "Low Stock", tint = Color.Red)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${item.quantity} ${item.unit}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                
                Row {
                    IconButton(onClick = { onUpdateQuantity(item.quantity - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    IconButton(onClick = { onUpdateQuantity(item.quantity + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                    // Quick Action for Reorder
                    if (isLowStock) {
                        IconButton(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "Urgent Site Request: Need to reorder ${item.name}. Current stock is only ${item.quantity} ${item.unit}.")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Request Reorder"))
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Request Reorder", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.inventory_last_updated, dateFormat.format(item.lastUpdated)), fontSize = 10.sp, color = Color.Gray)
                if (isLowStock) {
                    Text(stringResource(R.string.inventory_low_stock), color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryItemDialog(onDismiss: () -> Unit, onConfirm: (String, InventoryType, Double, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(InventoryType.RAW_MATERIAL) }
    var quantity by remember { mutableStateOf("") }
    val defaultUnit = stringResource(R.string.inventory_bag_ton)
    var unit by remember { mutableStateOf(defaultUnit) }
    var alertQty by remember { mutableStateOf("10") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inventory_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.inventory_name_hint)) }, modifier = Modifier.fillMaxWidth())
                
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = getInventoryTypeNameAr(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.water_level_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        InventoryType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(getInventoryTypeNameAr(t)) },
                                onClick = {
                                    type = t
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text(stringResource(R.string.inventory_quantity_label)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.inventory_unit_label)) }, modifier = Modifier.weight(1f))
                }
                
                OutlinedTextField(value = alertQty, onValueChange = { alertQty = it }, label = { Text(stringResource(R.string.inventory_alert_hint)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(name, type, quantity.toDoubleOrNull() ?: 0.0, unit, alertQty.toDoubleOrNull() ?: 0.0)
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun getInventoryTypeNameAr(type: InventoryType): String = when (type) {
    InventoryType.EQUIPMENT -> stringResource(R.string.inventory_category_heavy)
    InventoryType.TOOLS -> stringResource(R.string.inventory_category_tools)
    InventoryType.RAW_MATERIAL -> stringResource(R.string.inventory_category_materials)
    InventoryType.ACCESSORIES -> stringResource(R.string.inventory_category_accessories)
}
