package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.db.PourLog
import com.civileg.app.db.SiteInspection
import com.civileg.app.viewmodel.ExecutionViewModel
import java.text.SimpleDateFormat
import java.util.Date
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
    var showAddPourDialog by remember { mutableStateOf(false) }
    var showAddInspectionDialog by remember { mutableStateOf(false) }

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
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(onClick = { showAddInspectionDialog = true }, containerColor = MaterialTheme.colorScheme.secondary) {
                    Icon(Icons.Default.FactCheck, null)
                }
                FloatingActionButton(onClick = { showAddPourDialog = true }) {
                    Icon(Icons.Default.Add, null)
                }
            }
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

            items(inspections) { insp ->
                InspectionItem(insp)
            }
        }
    }

    if (showAddPourDialog) {
        AddPourLogDialog(
            onDismiss = { showAddPourDialog = false },
            onConfirm = { elementId, slump, vol ->
                viewModel.addPourLog(
                    PourLog(
                        projectId = projectId,
                        elementId = elementId,
                        slumpMm = slump,
                        volumeM3 = vol,
                        date = Date()
                    )
                )
                showAddPourDialog = false
            }
        )
    }

    if (showAddInspectionDialog) {
        AddInspectionDialog(
            onDismiss = { showAddInspectionDialog = false },
            onConfirm = { comments, fw, rb, cv, cl ->
                viewModel.addInspection(
                    SiteInspection(
                        projectId = projectId,
                        designId = null,
                        inspectorName = "Site Engineer",
                        comments = comments,
                        formworkSafe = fw,
                        rebarMatchesDesign = rb,
                        coverAdequate = cv,
                        cleanlinessPassed = cl,
                        date = Date()
                    )
                )
                showAddInspectionDialog = false
            }
        )
    }
}

@Composable
fun InspectionItem(insp: SiteInspection) {
    val isApproved = insp.formworkSafe && insp.rebarMatchesDesign && insp.coverAdequate && insp.cleanlinessPassed
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isApproved) Icons.Default.CheckCircle else Icons.Default.Cancel,
                null,
                tint = if (isApproved) Color(0xFF2E7D32) else Color.Red
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Inspection: ${SimpleDateFormat("dd MMM HH:mm", Locale.US).format(insp.date)}", fontWeight = FontWeight.Bold)
                Text(insp.comments, fontSize = 12.sp, color = Color.Gray)
                Text(
                    "FW: ${if(insp.formworkSafe) "OK" else "NG"} | RB: ${if(insp.rebarMatchesDesign) "OK" else "NG"} | CV: ${if(insp.coverAdequate) "OK" else "NG"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AddInspectionDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean, Boolean, Boolean, Boolean) -> Unit) {
    var comments by remember { mutableStateOf("") }
    var fw by remember { mutableStateOf(false) }
    var rb by remember { mutableStateOf(false) }
    var cv by remember { mutableStateOf(false) }
    var cl by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quality Inspection Checklist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = comments, onValueChange = { comments = it }, label = { Text("Comments") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = fw, onCheckedChange = { fw = it }); Text("Formwork Stable & Dimensions Correct") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = rb, onCheckedChange = { rb = it }); Text("Rebar Counts & Diameters Match Design") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = cv, onCheckedChange = { cv = it }); Text("Concrete Cover Spacers in Place") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = cl, onCheckedChange = { cl = it }); Text("Area Clean & Free of Debris") }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(comments, fw, rb, cv, cl) }) { Text("Approve & Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddPourLogDialog(onDismiss: () -> Unit, onConfirm: (String, Double, Double) -> Unit) {
    var elementId by remember { mutableStateOf("") }
    var slump by remember { mutableStateOf("100") }
    var vol by remember { mutableStateOf("10.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Concrete Pour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = elementId, onValueChange = { elementId = it }, label = { Text("Element ID (e.g. B1-L2)") })
                OutlinedTextField(value = slump, onValueChange = { slump = it }, label = { Text("Slump (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = vol, onValueChange = { vol = it }, label = { Text("Volume (m³)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = { 
                onConfirm(elementId, slump.toDoubleOrNull() ?: 100.0, vol.toDoubleOrNull() ?: 0.0)
            }) { Text("Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
