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
import com.civilengineer.app.data.models.ColumnDesign
import com.civilengineer.app.presentation.viewmodel.ColumnDesignViewModel

/**
 * الشاشة الرئيسية
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: ColumnDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMenu by remember { mutableStateOf("home") }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مساعد المهندس المدني") },
                navigationIcon = {
                    IconButton(onClick = { /* Open drawer */ }) {
                        Icon(Icons.Filled.Menu, contentDescription = "القائمة")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Filled.Search, contentDescription = "بحث")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedMenu == "home",
                    onClick = { selectedMenu = "home" },
                    label = { Text("الرئيسية") },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "الرئيسية") }
                )
                NavigationBarItem(
                    selected = selectedMenu == "columns",
                    onClick = { selectedMenu = "columns" },
                    label = { Text("الأعمدة") },
                    icon = { Icon(Icons.Filled.Architecture, contentDescription = "الأعمدة") }
                )
                NavigationBarItem(
                    selected = selectedMenu == "slabs",
                    onClick = { selectedMenu = "slabs" },
                    label = { Text("البلاطات") },
                    icon = { Icon(Icons.Filled.GridOn, contentDescription = "البلاطات") }
                )
                NavigationBarItem(
                    selected = selectedMenu == "footings",
                    onClick = { selectedMenu = "footings" },
                    label = { Text("القواعد") },
                    icon = { Icon(Icons.Filled.Foundation, contentDescription = "القواعد") }
                )
                NavigationBarItem(
                    selected = selectedMenu == "walls",
                    onClick = { selectedMenu = "walls" },
                    label = { Text("حوائط السند") },
                    icon = { Icon(Icons.Filled.WallpaperadTag, contentDescription = "حوائط") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("column_design") }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة جديد")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showSearchBar) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.searchColumns(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            when (selectedMenu) {
                "home" -> HomeScreen()
                "columns" -> ColumnsListScreen(
                    viewModel = viewModel,
                    navController = navController
                )
                "slabs" -> SlabsListScreen()
                "footings" -> FootingsListScreen()
                "walls" -> WallsListScreen()
            }
        }
    }

    if (uiState.error != null) {
        ErrorDialog(
            message = uiState.error ?: "",
            onDismiss = { viewModel.clearError() }
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("ابحث...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Architecture,
            contentDescription = "شعار التطبيق",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "مساعد المهندس المدني",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "تطبيق شامل لتصميم المنشآت الخرسانية",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        // خيارات سريعة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickActionCard(
                title = "عمود جديد",
                icon = Icons.Filled.Add,
                onClick = { }
            )
            QuickActionCard(
                title = "بلاطة جديدة",
                icon = Icons.Filled.Add,
                onClick = { }
            )
            QuickActionCard(
                title = "قاعدة جديدة",
                icon = Icons.Filled.Add,
                onClick = { }
            )
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: androidx.compose.material.icons.Icons,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .height(100.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp)
            )
            Text(text = title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ColumnsListScreen(
    viewModel: ColumnDesignViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (uiState.columns.isEmpty()) {
        EmptyStateScreen(
            message = "لا توجد أعمدة",
            actionText = "إضافة عمود جديد",
            onAction = { navController.navigate("column_design") }
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
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

@Composable
fun ColumnCard(
    column: ColumnDesign,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = column.columnName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${column.lengthM} م × ${column.widthM} م × ${column.heightM} م",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (column.isSafe) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "آمن",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "غير آمن",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip(
                    label = "الحمل",
                    value = "${column.axialLoadKN} kN"
                )
                InfoChip(
                    label = "الكود",
                    value = column.codeType.name
                )
                InfoChip(
                    label = "معامل الأمان",
                    value = "%.2f".format(column.safetyFactor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف")
                }
            }
        }
    }
}

@Composable
fun InfoChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun EmptyStateScreen(
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = "فارغ",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAction) {
            Text(actionText)
        }
    }
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خطأ") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("حسناً")
            }
        }
    )
}

@Composable
fun SlabsListScreen() {
    EmptyStateScreen(
        message = "قيد الإنشاء",
        actionText = "عودة",
        onAction = { }
    )
}

@Composable
fun FootingsListScreen() {
    EmptyStateScreen(
        message = "قيد الإنشاء",
        actionText = "عودة",
        onAction = { }
    )
}

@Composable
fun WallsListScreen() {
    EmptyStateScreen(
        message = "قيد الإنشاء",
        actionText = "عودة",
        onAction = { }
    )
}