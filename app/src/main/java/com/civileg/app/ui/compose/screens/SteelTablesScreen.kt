package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.civileg.app.utils.SteelTables
import com.civileg.app.utils.SteelTables.SectionProperties
import com.civileg.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelTablesScreen(
    onNavigateBack: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(0) }
    var selectedSection by remember { mutableStateOf<SectionProperties?>(null) }

    val sectionTypes = listOf("IPE", "HEA", "HEB", "UPN", "زوايا", "الكل")
    val typeColors = listOf(
        Color(0xFF1976D2), Color(0xFF7B1FA2), Color(0xFF00838F),
        Color(0xFFE65100), Color(0xFF2E7D32), Color(0xFF37474F)
    )

    val filteredSections = remember(searchQuery, selectedType) {
        val base = when (selectedType) {
            0 -> SteelTables.ipeSections
            1 -> SteelTables.heaSections
            2 -> SteelTables.hebSections
            3 -> SteelTables.upnSections
            4 -> SteelTables.angleSections
            else -> SteelTables.getAllSections()
        }
        if (searchQuery.isBlank()) base
        else base.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.depth.toString().contains(searchQuery)
        }
    }

    // Detail Dialog
    selectedSection?.let { section ->
        AlertDialog(
            onDismissRequest = { selectedSection = null },
            title = {
                Text(
                    section.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val displayProps = if (section.Zx > 0) {
                        listOf(
                            "العمق h" to "${section.depth} mm",
                            "العرض b" to "${section.width} mm",
                            "سماكة النفق tw" to "${section.tw} mm",
                            "سماكة الشنش tf" to "${section.tf} mm",
                            "المساحة" to "${section.area} cm²",
                            "الوزن" to "${section.weight} kg/m",
                            "عزم القصور القوي Iy" to "${section.iy} cm⁴",
                            "عزم القصور الضعيف Iz" to "${section.iz} cm⁴",
                            "معامل المقطع القوي Sy" to "${section.sy} cm³",
                            "معامل المقطع الضعيف Sz" to "${section.sz} cm³",
                            "نصف قطر الدوران القوي ry" to "${section.ry} cm",
                            "نصف قطر الدوران الضعيف rz" to "${section.rz} cm",
                            "معامل البلاستك القوي Zx" to "${section.Zx} cm³",
                            "معامل البلاستك الضعيف Zy" to "${section.Zy} cm³"
                        )
                    } else {
                        listOf(
                            "العمق h" to "${section.depth} mm",
                            "العرض b" to "${section.width} mm",
                            "سماكة النفق tw" to "${section.tw} mm",
                            "سماكة الشنش tf" to "${section.tf} mm",
                            "المساحة" to "${section.area} cm²",
                            "الوزن" to "${section.weight} kg/m",
                            "عزم القصور القوي Iy" to "${section.iy} cm⁴",
                            "عزم القصور الضعيف Iz" to "${section.iz} cm⁴",
                            "معامل المقطع القوي Sy" to "${section.sy} cm³",
                            "معامل المقطع الضعيف Sz" to "${section.sz} cm³",
                            "نصف قطر الدوران القوي ry" to "${section.ry} cm",
                            "نصف قطر الدوران الضعيف rz" to "${section.rz} cm"
                        )
                    }
                    displayProps.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSection = null }) {
                    Text("إغلاق", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_steel_tables_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث بالمقاطع...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Section Type Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedType,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                sectionTypes.forEachIndexed { index, type ->
                    Tab(
                        selected = selectedType == index,
                        onClick = { selectedType = index },
                        text = {
                            Text(
                                type,
                                fontWeight = if (selectedType == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedType == index) typeColors[index] else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // Count
            Text(
                text = "${filteredSections.size} مقطع",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Section List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Table Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TableHeaderCell("المقطع", 90.dp)
                        TableHeaderCell("h", 50.dp)
                        TableHeaderCell("b", 50.dp)
                        TableHeaderCell("A", 55.dp)
                        TableHeaderCell("وزن", 55.dp)
                        TableHeaderCell("Iy", 65.dp)
                        TableHeaderCell("Iz", 65.dp)
                        TableHeaderCell("Sy", 55.dp)
                        TableHeaderCell("Sz", 55.dp)
                    }
                    HorizontalDivider()
                }

                items(filteredSections) { section ->
                    val colorIdx = when {
                        section.name.startsWith("IPE") -> 0
                        section.name.startsWith("HEA") -> 1
                        section.name.startsWith("HEB") -> 2
                        section.name.startsWith("UPN") -> 3
                        section.name.startsWith("L ") -> 4
                        else -> 5
                    }

                    Card(
                        onClick = { selectedSection = section },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = typeColors[colorIdx].copy(alpha = 0.05f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TableCell(section.name, 90.dp, true, typeColors[colorIdx])
                            TableCell(section.depth.toInt().toString(), 50.dp)
                            TableCell(section.width.toInt().toString(), 50.dp)
                            TableCell(section.area.toString(), 55.dp)
                            TableCell(section.weight.toString(), 55.dp)
                            TableCell(section.iy.toInt().toString(), 65.dp)
                            TableCell(section.iz.toInt().toString(), 65.dp)
                            TableCell(section.sy.toInt().toString(), 55.dp)
                            TableCell(section.sz.toInt().toString(), 55.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TableCell(text: String, width: Dp, isName: Boolean = false, color: Color = Color.Unspecified) {
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (isName) 12.sp else 11.sp,
            fontWeight = if (isName) FontWeight.Bold else FontWeight.Normal,
            color = if (isName && color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
        )
    }
}