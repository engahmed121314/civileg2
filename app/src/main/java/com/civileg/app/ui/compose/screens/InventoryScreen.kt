package com.civileg.app.ui.compose.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.db.InventoryItem
import com.civileg.app.db.InventoryType
import com.civileg.app.viewmodel.InventoryViewModel
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
fun InventoryItemCard(item: InventoryItem, onUpdateQuantity: (Double) -> Unit, onDelete: () -> Unit) {
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
    var unit by remember { mutableStateOf(stringResource(R.string.inventory_bag_ton)) }
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

fun getInventoryTypeNameAr(type: InventoryType): String = when (type) {
    InventoryType.EQUIPMENT -> stringResource(R.string.inventory_category_heavy)
    InventoryType.TOOLS -> stringResource(R.string.inventory_category_tools)
    InventoryType.RAW_MATERIAL -> stringResource(R.string.inventory_category_materials)
    InventoryType.ACCESSORIES -> stringResource(R.string.inventory_category_accessories)
}
