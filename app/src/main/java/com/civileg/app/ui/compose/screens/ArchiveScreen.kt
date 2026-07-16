package com.civileg.app.ui.compose.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import com.civileg.app.db.Project
import com.civileg.app.viewmodel.ProjectViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: ProjectViewModel,
    onProjectClick: (Project) -> Unit,
    onNavigateBack: () -> Unit
) {
    val projectsState = viewModel.allArchiveProjects.observeAsState(initial = emptyList())
    val projects = projectsState.value
    val designsState = viewModel.allDesigns.observeAsState(initial = emptyList())
    val allDesigns = designsState.value

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Create project dialog state
    var newProjectName by remember { mutableStateOf("") }
    var newProjectCode by remember { mutableStateOf("ECP") }
    var newProjectLocation by remember { mutableStateOf("") }

    // Filter projects by search query
    val filteredProjects = if (searchQuery.isBlank()) {
        projects
    } else {
        projects.filter { project ->
            project.name.contains(searchQuery, ignoreCase = true) ||
            project.code.contains(searchQuery, ignoreCase = true) ||
            project.location.contains(searchQuery, ignoreCase = true)
        }
    }

    // Compute design counts per project
    val designCounts = remember(allDesigns) {
        allDesigns.groupBy { it.projectId }
            .mapValues { it.value.size }
    }

    // Compute design type breakdown per project
    val designTypeBreakdown = remember(allDesigns) {
        allDesigns.groupBy { it.projectId }
            .mapValues { entry ->
                entry.value.groupingBy { it.type }.eachCount()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث في المشروعات...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text("المشروعات المحفوظة", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showSearchBar) {
                            showSearchBar = false
                            searchQuery = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (showSearchBar) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) searchQuery = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Project", tint = Color.White)
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("لا توجد مشروعات محفوظة", color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("اضغط + لإنشاء مشروع جديد", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                }
            }
        } else if (filteredProjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("لا توجد نتائج لـ \"$searchQuery\"", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                        Text(
                            "عدد النتائج: ${filteredProjects.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }
                }
                items(filteredProjects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        designCount = designCounts[project.id] ?: 0,
                        typeBreakdown = designTypeBreakdown[project.id] ?: emptyMap(),
                        onClick = { onProjectClick(project) },
                        onDelete = { viewModel.deleteArchiveProject(project) }
                    )
                }
            }
        }
    }

    // Create Project Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("إنشاء مشروع جديد", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("اسم المشروع *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newProjectCode,
                        onValueChange = { newProjectCode = it },
                        label = { Text("كود التصميم (ECP / ACI / SBC)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newProjectLocation,
                        onValueChange = { newProjectLocation = it },
                        label = { Text("الموقع (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            viewModel.insert(
                                Project(
                                    name = newProjectName.trim(),
                                    code = newProjectCode.trim().ifBlank { "ECP" },
                                    location = newProjectLocation.trim()
                                )
                            )
                            newProjectName = ""
                            newProjectCode = "ECP"
                            newProjectLocation = ""
                            showCreateDialog = false
                        }
                    },
                    enabled = newProjectName.isNotBlank()
                ) {
                    Text("إنشاء")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    designCount: Int = 0,
    typeBreakdown: Map<DesignType, Int> = emptyMap(),
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "كود التصميم: ${project.code}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(project.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.7f)
                    )
                }
            }

            // Design count badge + type breakdown
            if (designCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Design count badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Engineering,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$designCount تصميم",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Type breakdown chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        typeBreakdown.entries.forEach { (type, count) ->
                            DesignTypeBadge(type = type, count = count)
                        }
                    }
                }
            }

            // Location if available
            if (project.location.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = project.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignTypeBadge(type: DesignType, count: Int) {
    val (label, color) = when (type) {
        DesignType.BEAM -> "كمرات" to Color(0xFF1565C0)
        DesignType.COLUMN -> "أعمدة" to Color(0xFF2E7D32)
        DesignType.FOOTING -> "قواعد" to Color(0xFFE65100)
        DesignType.SLAB -> "بلاطات" to Color(0xFF6A1B9A)
        DesignType.STAIRCASE -> "سلالم" to Color(0xFF00695C)
        DesignType.RETAINING_WALL -> "سند" to Color(0xFFBF360C)
        DesignType.WATER_TANK -> "خزانات" to Color(0xFF01579B)
        DesignType.PILE -> "خوازيق" to Color(0xFF37474F)
        DesignType.SEISMIC -> "زلزال" to Color(0xFFC62828)
        DesignType.STEEL_MEMBER -> "حديد" to Color(0xFF455A64)
        DesignType.STEEL_WAREHOUSE -> "معدني" to Color(0xFF4E342E)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = "$label $count",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}