package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.utils.ExportUtils
import com.civileg.app.viewmodel.ExportState
import com.civileg.app.viewmodel.ExportViewModel
import com.civileg.app.billing.PremiumFeature
import com.civileg.app.billing.PremiumPaywallSheet
import com.civileg.app.viewmodel.PremiumViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * §58 EXPORT CENTER — one screen that lists every artifact this app has
 * generated (calculation/drawing PDFs, DXF sheets, BBS/BOQ exports) and lets
 * the engineer assemble them into the §93 FINAL PACKAGE with a manifest.
 *
 * Result-first: state banner on top (Idle/Exporting/Success/Error), then
 * progressive disclosure — selection list, package action, manifest preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportCenterScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
    premiumVm: PremiumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsState()

    var artifacts by remember { mutableStateOf<List<File>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var projectName by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<com.civileg.app.utils.CompletePackageGenerator.PackageResult?>(null) }
    val featureFlags = premiumVm.featureFlags
    var showPaywallFor by remember { mutableStateOf<PremiumFeature?>(null) }
    val isPremium = featureFlags.isPremium()
    showPaywallFor?.let { feature ->
        PremiumPaywallSheet(feature = feature, billingManager = premiumVm.billingManager, onDismiss = { showPaywallFor = null })
    }

    // Scan known output roots (top level only — these dirs are flat by design)
    LaunchedEffect(Unit) {
        val roots = listOfNotNull(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            context.filesDir
        )
        artifacts = roots.flatMap { root ->
            root.walkTopDown().maxDepth(3)
                .filter { it.isFile }
                .filter {
                    val n = it.name.lowercase()
                    (n.endsWith(".pdf") || n.endsWith(".dxf") ||
                        n.endsWith(".xlsx") || n.endsWith(".csv")) &&
                        !n.contains("manifest")
                }
                .toList()
        }.sortedByDescending { it.lastModified() }
            .distinctBy { it.absolutePath }
        scanned = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_center), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.export_center_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── RESULT-FIRST status banner ───────────────────────────
            when (val st = exportState) {
                is ExportState.Exporting -> item {
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.exporting_pdf))
                        }
                    }
                }
                is ExportState.Success -> item {
                    if (lastResult != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.export_done),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    lastResult!!.rootDir.absolutePath,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                lastResult!!.files.forEach { f ->
                                    Text("• $f", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(onClick = {
                                    ExportUtils.openFile(context, lastResult!!.manifestFile, "application/json")
                                }) {
                                    Icon(Icons.Default.OpenInNew, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("MANIFEST.json")
                                }
                            }
                        }
                    } else if (st.file != null) {
                        // A single-report success from an earlier export — ignore here
                    }
                }
                is ExportState.Error -> item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            "${stringResource(R.string.export_error)}: ${st.message}",
                            Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {}
            }

            // ── Project name ─────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(stringResource(R.string.export_project_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // ── Artifact selection ───────────────────────────────────
            if (scanned && artifacts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.export_no_artifacts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.export_select_hint),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(artifacts, key = { it.absolutePath }) { file ->
                    val checked = selected[file.absolutePath] == true
                    Card(onClick = { selected[file.absolutePath] = !checked }) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selected[file.absolutePath] = it
                            })
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    file.parentFile?.name ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                SimpleDateFormat("MM-dd HH:mm", Locale.US)
                                    .format(Date(file.lastModified())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    val chosen = artifacts.filter { selected[it.absolutePath] == true }

                    // §42 QA PREVIEW — same engine the packager runs at
                    // generation time; display-only, never authoritative.
                    if (chosen.isNotEmpty()) {
                        val preview = com.civileg.app.domain.audit.EngineeringAuditEngine.run {
                            report(
                                projectName.ifBlank { "CivilEG" },
                                chosen.map {
                                    artifactExistsCheck(
                                        com.civileg.app.domain.audit.AuditStage.DRAWING, it.name, it
                                    )
                                }
                            )
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when (preview.status) {
                                    com.civileg.app.domain.audit.AuditStatus.PASS ->
                                        MaterialTheme.colorScheme.primaryContainer
                                    com.civileg.app.domain.audit.AuditStatus.FAIL ->
                                        MaterialTheme.colorScheme.errorContainer
                                    else ->
                                        MaterialTheme.colorScheme.secondaryContainer
                                }
                            )
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "QA ${preview.status.label} — ${preview.healthPercent}%",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                preview.failures.take(3).forEach {
                                    Text("✗ $it", style = MaterialTheme.typography.labelSmall)
                                }
                                preview.warnings.take(3).forEach {
                                    Text("⚠ $it", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!isPremium) { showPaywallFor = PremiumFeature.COMPLETE_PACKAGE; return@Button }
                            viewModel.generateCompletePackage(
                                context = context,
                                projectName = projectName.ifBlank { "CivilEG" },
                                artifacts = chosen,
                                codeVersion = com.civileg.core.calculations.entities.DesignCode.ECP,
                                revision = "R0"
                            ) { result -> lastResult = result }
                        },
                        enabled = chosen.isNotEmpty() && projectName.isNotBlank() &&
                            exportState !is ExportState.Exporting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Archive, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_generate_package))
                    }
                    if (projectName.isBlank()) {
                        Text(
                            stringResource(R.string.export_project_name) + " →",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // No dead end (§67): back to work
            item {
                OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.back))
                }
            }
        }
    }
}
