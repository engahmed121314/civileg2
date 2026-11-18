package com.civileg.app.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.domain.entities.DesignCode
import com.civileg.core.output.dxf.DxfEngine
import com.civileg.core.output.pdf.PdfReportEngine
import com.civileg.core.output.pdf.PdfReportEngine.PdfSection
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

/**
 * شاشة تصدير المخرجات (Export Screen) — Phase 6 من خارطة الطريق.
 *
 * تستخدم محركات Phase 5 (DxfEngine و PdfReportEngine من core:calculations)
 * لتوليد ملفات PDF و DXF من نتائج الحسابات الإنشائية.
 *
 * دعم كامل للـ RTL/LTR حسب لغة الجهاز المختارة (العربية / الإنجليزية).
 *
 * // Phase 5 engines are Pure Kotlin — Zero Android / Zero UI dependencies.
 * // This Compose screen is the UI binding layer (Step 6 of 7-step protocol).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    var selectedExportType by remember { mutableStateOf(ExportType.PDF) }
    var selectedDesignCode by remember { mutableStateOf(DesignCode.ECP) }
    var selectedLanguage by remember { mutableStateOf(AppLanguage.ARABIC) }
    var projectName by remember { mutableStateOf("") }
    var elementType by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }

    val codeText = when (selectedDesignCode) {
        DesignCode.ECP -> stringResource(R.string.code_ecp)
        DesignCode.ACI -> stringResource(R.string.code_aci)
        DesignCode.SBC -> stringResource(R.string.code_sbc)
    }

    val languageText = when (selectedLanguage) {
        AppLanguage.ARABIC -> stringResource(R.string.language_arabic)
        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppText(
                        text = stringResource(R.string.screen_export_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ─── نوع التصدير ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppText(
                        text = stringResource(R.string.export_type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedExportType == ExportType.PDF,
                            onClick = { selectedExportType = ExportType.PDF }
                        )
                        AppText(
                            text = stringResource(R.string.export_pdf),
                            fontWeight = FontWeight.Medium
                        )
                        AppText(
                            text = stringResource(R.string.export_pdf_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedExportType == ExportType.DXF,
                            onClick = { selectedExportType = ExportType.DXF }
                        )
                        AppText(
                            text = stringResource(R.string.export_dxf),
                            fontWeight = FontWeight.Medium
                        )
                        AppText(
                            text = stringResource(R.string.export_dxf_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── معلومات المشروع ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppText(
                        text = stringResource(R.string.project_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { AppText(stringResource(R.string.label_project_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = elementType,
                        onValueChange = { elementType = it },
                        label = { AppText(stringResource(R.string.label_element_type)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── الكود التصميمي واللغة ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppText(
                        text = stringResource(R.string.design_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AppText(
                        text = stringResource(R.string.design_code) + ": $codeText",
                        fontWeight = FontWeight.Medium
                    )
                    AppText(
                        text = stringResource(R.string.language_selection) + ": $languageText",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── زر التصدير ───
            AppButton(
                label = stringResource(R.string.btn_export_now),
                onClick = {
                    if (projectName.isBlank() || elementType.isBlank()) {
                        return@AppButton
                    }

                    isExporting = true
                    exportResult = null

                    try {
                        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val safeProjectName = projectName.replace(" ", "_")

                        when (selectedExportType) {
                            ExportType.PDF -> {
                                val fileName = "${safeProjectName}_${elementType}_${timestamp}.pdf"
                                val outputFile = File(outputDir, fileName)

                                val pdfContent = PdfReportEngine.generateReport(
                                    titleAr = "ملاحظة حساب $elementType",
                                    titleEn = "$elementType Calculation Note",
                                    sections = listOf(
                                        PdfSection(
                                            titleAr = "معلومات المشروع",
                                            titleEn = "Project Info",
                                            contentAr = "الاسم: $projectName",
                                            contentEn = "Name: $projectName"
                                        ),
                                        PdfSection(
                                            titleAr = "الكود التصميمي",
                                            titleEn = "Design Code",
                                            contentAr = codeText,
                                            contentEn = selectedDesignCode.version
                                        ),
                                        PdfSection(
                                            titleAr = "اللغة",
                                            titleEn = "Language",
                                            contentAr = languageText,
                                            contentEn = selectedLanguage.toString()
                                        )
                                    )
                                )
                                outputFile.writeText(pdfContent)
                                exportResult = outputFile.absolutePath
                            }
                            ExportType.DXF -> {
                                val fileName = "${safeProjectName}_${elementType}_${timestamp}.dxf"
                                val outputFile = File(outputDir, fileName)

                                val dxfContent = DxfEngine.generateCompleteDxfWithCode(
                                    drawingName = elementType,
                                    designCode = selectedDesignCode.version,
                                    projectName = projectName,
                                    elementType = elementType,
                                    layers = listOf("0", "CONCRETE", "REBAR", "DIMENSION"),
                                    entities = listOf(
                                        DxfEngine.lineEntity(0.0, 0.0, 1000.0, 0.0, "CONCRETE"),
                                        DxfEngine.circleEntity(500.0, 500.0, 16.0, "REBAR")
                                    )
                                )
                                outputFile.writeText(dxfContent)
                                exportResult = outputFile.absolutePath
                            }
                        }
                    } catch (e: Exception) {
                        exportResult = "Error: ${e.message}"
                    } finally {
                        isExporting = false
                    }
                },
                enabled = !isExporting && projectName.isNotBlank() && elementType.isNotBlank(),
                loading = isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                fontWeight = FontWeight.Bold,
                icon = Icons.Default.FileDownload
            )

            Spacer(Modifier.height(16.dp))

            // ─── نتيجة التصدير ───
            exportResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            AppText(
                                text = stringResource(R.string.export_success),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        AppText(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3
                        )
                        Spacer(Modifier.height(12.dp))
                        AppButton(
                            label = stringResource(R.string.btn_open_file),
                            onClick = {
                                val uri = Uri.fromFile(java.io.File(result))
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * نوع التصدير المدعوم.
 */
enum class ExportType {
    PDF,
    DXF
}

/**
 * لغة التطبيق المدعومة.
 */
enum class AppLanguage {
    ARABIC,
    ENGLISH
}
