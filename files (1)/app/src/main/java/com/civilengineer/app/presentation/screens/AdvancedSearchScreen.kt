package com.civilengineer.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.presentation.viewmodel.ColumnDesignViewModel

/**
 * شاشة البحث والفلترة المتقدمة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchScreen(
    navController: NavController,
    viewModel: ColumnDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var filterCode by remember { mutableStateOf<CodeType?>(null) }
    var filterSafety by remember { mutableStateOf<Boolean?>(null) }
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بحث متقدم") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "عودة")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // شريط البحث
            TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchColumns(it)
                },
                placeholder = { Text("ابحث عن العمود...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // زر الفلترة
            Button(
                onClick = { showFilters = !showFilters },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("خيارات الفلترة")
            }

            // الفلاتر
            if (showFilters) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "فلترة حسب الكود",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        CodeType.values().forEach { code ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = filterCode == code,
                                    onCheckedChange = {
                                        filterCode = if (it) code else null
                                        if (filterCode != null) {
                                            viewModel.filterByCodeType(filterCode)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(code.name)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "فلترة حسب الأمان",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = filterSafety == true,
                                onCheckedChange = {
                                    filterSafety = if (it) true else null
                                    if (filterSafety != null) {
                                        viewModel.filterBySafety(filterSafety)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("آمن فقط")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = filterSafety == false,
                                onCheckedChange = {
                                    filterSafety = if (it) false else null
                                    if (filterSafety != null) {
                                        viewModel.filterBySafety(filterSafety)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("غير آمن فقط")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                filterCode = null
                                filterSafety = null
                                searchQuery = ""
                                viewModel.searchColumns("")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("مسح الفلاتر")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // النتائج
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.columns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "لم يتم العثور على نتائج",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.columns) { column ->
                        ColumnCard(
                            column = column,
                            onSelect = {
                                viewModel.selectColumn(column)
                                navController.navigate("column_details/${column.id}")
                            },
                            onDelete = {
                                viewModel.deleteColumn(column.id)
                            }
                        )
                    }
                }
            }
        }
    }
}