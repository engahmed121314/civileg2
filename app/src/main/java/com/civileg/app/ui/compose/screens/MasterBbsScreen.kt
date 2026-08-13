package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.utils.BbsEntry
import com.civileg.app.utils.BbsGenerator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterBbsScreen(
    projectName: String,
    allEntries: List<BbsEntry>,
    onNavigateBack: () -> Unit = {}
) {
    val combinedBbs = remember(allEntries) { BbsGenerator.combineProjectBbs(listOf(allEntries)) }
    val optimization = remember(combinedBbs) { BbsGenerator.optimizeCutting(combinedBbs) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Master BBS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(projectName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("Factory Fabrication List", style = MaterialTheme.typography.bodyMedium)
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Text(optimization, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            items(combinedBbs) { entry ->
                BbsListItem(entry)
            }
        }
    }
}

@Composable
fun BbsListItem(entry: BbsEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Ø${entry.diameter} - Shape ${entry.shapeCode}", fontWeight = FontWeight.Bold)
                Text("L = ${entry.totalLengthPerBar.toInt()} mm", fontSize = 12.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Qty: ${entry.count}", fontWeight = FontWeight.Black)
                Text("${String.format(Locale.US, "%.1f", entry.totalWeightKg)} kg", fontSize = 11.sp)
            }
        }
    }
}
