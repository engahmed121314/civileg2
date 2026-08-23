package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.db.PourLog
import com.civileg.app.viewmodel.ExecutionViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionLogScreen(
    projectId: Long,
    viewModel: ExecutionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val pourLogs by viewModel.getPourLogs(projectId).observeAsState(emptyList())
    val inspections by viewModel.getInspections(projectId).observeAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.execution_logs_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(stringResource(R.string.concrete_pour_logs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (pourLogs.isEmpty()) {
                item { Text(stringResource(R.string.no_pours_recorded), color = Color.Gray, fontSize = 12.sp) }
            }

            items(pourLogs) { log ->
                PourLogItem(log)
            }

            item {
                Text(stringResource(R.string.quality_inspections), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }

            if (inspections.isEmpty()) {
                item { Text(stringResource(R.string.no_inspections_recorded), color = Color.Gray, fontSize = 12.sp) }
            }
            
            // inspections items...
        }
    }
}

@Composable
fun PourLogItem(log: PourLog) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalDrink, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.elementId, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.execution_pour_log_format, log.slumpMm, log.volumeM3), fontSize = 12.sp)
            }
            Text(SimpleDateFormat("dd MMM", Locale.US).format(log.date), fontSize = 11.sp, color = Color.Gray)
        }
    }
}
