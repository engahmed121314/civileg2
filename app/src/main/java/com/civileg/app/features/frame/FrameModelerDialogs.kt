package com.civileg.app.features.frame

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.civileg.app.R
import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabDistribution
import com.civileg.app.ui.compose.components.drawings.SteelSectionDrawing
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.domain.entities.orderQuadJoints
import com.civileg.app.domain.calculations.SteelFrameDesign
import com.civileg.app.domain.modeler.ModelerLayerVisibility
import com.civileg.app.utils.FrameDxfExporter
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.SteelTables
import com.civileg.app.features.frame.FrameAnalysisViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

fun MemberPropsDialog(
    member: FrameMember,
    onDismiss: () -> Unit,
    onSave: (FrameMember) -> Unit
) {
    var material by remember(member) { mutableStateOf(member.materialType) }
    var type by remember(member) { mutableStateOf(member.memberType) }
    var widthMm by remember(member) {
        mutableStateOf(member.concreteSection?.width?.let(::formatCoord) ?: "250")
    }
    var depthMm by remember(member) {
        mutableStateOf(member.concreteSection?.depth?.let(::formatCoord) ?: "500")
    }
    var steelSection by remember(member) {
        mutableStateOf(member.steelSectionName ?: SteelFrameDesign.AUTO_SECTION)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(t("خصائص العضو ${member.id}", "Member ${member.id} properties")) },
        text = {
            Column {
                AppText(t("المادة", "Material"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = material == FrameMaterialType.Concrete,
                        onClick = { material = FrameMaterialType.Concrete },
                        label = { AppText(t("خرسانة", "Concrete"), fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = material == FrameMaterialType.Steel,
                        onClick = { material = FrameMaterialType.Steel },
                        label = { AppText(t("حديد", "Steel"), fontSize = 11.sp) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                AppText(t("نوع العضو", "Member type"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (mt in FrameMemberType.entries) {
                        FilterChip(
                            selected = type == mt,
                            onClick = { type = mt },
                            label = { AppText(mt.name, fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (material == FrameMaterialType.Concrete) {
                    Row {
                        NumberField(widthMm, { widthMm = it }, t("العرض (مم)", "Width (mm)"), Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        NumberField(depthMm, { depthMm = it }, t("الارتفاع (مم)", "Depth (mm)"), Modifier.weight(1f))
                    }
                } else {
                    OutlinedTextField(
                        value = steelSection,
                        onValueChange = { steelSection = it },
                        label = { AppText(t("المقطع الحديدي", "Steel Section")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                label = t("حفظ", "Save"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text,
                onClick = {
                    val w = widthMm.toDoubleOrNull() ?: 250.0
                    val d = depthMm.toDoubleOrNull() ?: 500.0
                    val updated = member.copy(
                        materialType = material,
                        memberType = type,
                        concreteSection = if (material == FrameMaterialType.Concrete) ConcreteSectionProps(w, d) else null,
                        steelSectionName = if (material == FrameMaterialType.Steel) {
                            steelSection.trim().takeUnless { SteelFrameDesign.isAutoSection(it) }
                        } else null
                    )
                    onSave(updated)
                }
            )
        },
        dismissButton = {
            AppButton(
                label = t("إلغاء", "Cancel"),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

@Composable
fun NodeInspector(
    node: FrameNode,
    nodalLoads: List<IndexedValue<NodalLoad>>,
    modifier: Modifier = Modifier,
    onUpdate: (Double, Double) -> Unit,
    onSupportChange: (SupportType) -> Unit,
    onEditNodalLoad: (Int, NodalLoad) -> Unit,
    onDeleteNodalLoad: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var xText by remember(node.id, node.x) { mutableStateOf(formatCoord(node.x)) }
    var yText by remember(node.id, node.y) { mutableStateOf(formatCoord(node.y)) }
    Card(
        modifier = modifier.widthIn(max = 430.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    t("العقدة ${node.id}", "Joint ${node.id}"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1565C0)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(xText, { xText = it }, "X (m)", Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                NumberField(yText, { yText = it }, "Y (m)", Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                AppButton(
                    label = t("تطبيق", "Apply"),
                    enabled = xText.toDoubleOrNull() != null &&
                        yText.toDoubleOrNull() != null &&
                        (xText.toDoubleOrNull() != node.x || yText.toDoubleOrNull() != node.y),
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false,
                    fontSize = 12.sp,
                    onClick = {
                        val x = xText.toDoubleOrNull() ?: return@AppButton
                        val y = yText.toDoubleOrNull() ?: return@AppButton
                        onUpdate(x, y)
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            AppText(t("الاستناد", "Support"), fontSize = 10.sp, color = Color.Gray)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (st in SupportType.entries) {
                    FilterChip(
                        selected = node.support == st,
                        onClick = { onSupportChange(st) },
                        label = { AppText(st.displayName, fontSize = 9.sp) },
                        modifier = Modifier.padding(horizontal = 2.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2E7D32),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            if (nodalLoads.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                AppText(t("أحمال العقدة", "Joint loads"), fontSize = 10.sp, color = Color.Gray)
                for (entry in nodalLoads.take(3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            "${entry.value.loadCase}: " +
                                "Fx=${fmtLoad(entry.value.fx)}  " +
                                "Fy=${fmtLoad(entry.value.fy)}  " +
                                "Mz=${fmtLoad(entry.value.mz)}",
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onEditNodalLoad(entry.index, entry.value) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = { onDeleteNodalLoad(entry.index) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(15.dp))
                        }
                    }
                }
                if (nodalLoads.size > 3) {
                    AppText(t("+${nodalLoads.size - 3} أحمال أخرى", "+${nodalLoads.size - 3} more"), fontSize = 9.sp, color = Color.Gray)
                }
            }
            AppButton(
                label = t("حذف العقدة", "Delete joint"),
                onClick = onDelete,
                style = AppButtonStyle.Text,
                fillMaxWidth = false,
                icon = Icons.Default.Delete,
                contentOverride = Color(0xFFC62828),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun MemberInspector(
    member: FrameMember,
    length: Double,
    memberLoads: List<IndexedValue<MemberLoad>>,
    modifier: Modifier = Modifier,
    onReleaseI: () -> Unit,
    onReleaseJ: () -> Unit,
    onEditMemberLoad: (Int, MemberLoad) -> Unit,
    onDeleteMemberLoad: (Int) -> Unit,
    onEditProperties: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = modifier.widthIn(max = 430.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    t("العضو ${member.id}", "Member ${member.id}"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1565C0)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEditProperties, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }
            AppText(
                t(
                    "من ${member.nodeI} إلى ${member.nodeJ} • الطول ${"%.3f".format(length)} م",
                    "Joint ${member.nodeI} → ${member.nodeJ} • ${"%.3f".format(length)} m"
                ),
                fontSize = 10.sp,
                color = Color.Gray
            )
            AppText(
                "${member.materialType.name} • ${member.memberType.name}" +
                    (member.concreteSection?.takeIf { member.materialType == FrameMaterialType.Concrete }
                        ?.let { " • ${it.width.toInt()}×${it.depth.toInt()}" } ?: "") +
                    (if (member.materialType == FrameMaterialType.Steel) {
                        " • " + (member.steelSectionName
                            ?.takeUnless { SteelFrameDesign.isAutoSection(it) }
                            ?: t("تلقائي", "Auto"))
                    } else ""),
                fontSize = 10.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = member.releaseI,
                    onClick = onReleaseI,
                    label = { AppText(t("مفصل I", "Hinge I"), fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF57C00),
                        selectedLabelColor = Color.White
                    )
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = member.releaseJ,
                    onClick = onReleaseJ,
                    label = { AppText(t("مفصل J", "Hinge J"), fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF57C00),
                        selectedLabelColor = Color.White
                    )
                )
            }
            if (memberLoads.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                AppText(t("أحمال العضو", "Member loads"), fontSize = 10.sp, color = Color.Gray)
                for (entry in memberLoads.take(3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            "${entry.value.loadCase}: ${entry.value.loadType.name} " +
                                "${fmtLoad(entry.value.value)} @ ${fmtLoad(entry.value.position)}m",
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onEditMemberLoad(entry.index, entry.value) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = { onDeleteMemberLoad(entry.index) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(15.dp))
                        }
                    }
                }
                if (memberLoads.size > 3) {
                    AppText(t("+${memberLoads.size - 3} أحمال أخرى", "+${memberLoads.size - 3} more"), fontSize = 9.sp, color = Color.Gray)
                }
            }
            AppButton(
                label = t("حذف العضو", "Delete member"),
                onClick = onDelete,
                style = AppButtonStyle.Text,
                fillMaxWidth = false,
                icon = Icons.Default.Delete,
                contentOverride = Color(0xFFC62828),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun MemberPropertiesDialog(
    member: FrameMember,
    memberLengthM: Double,
    onDismiss: () -> Unit,
    onSave: (FrameMember) -> Unit
) {
    var memberType by remember(member.id) { mutableStateOf(member.memberType) }
    var materialType by remember(member.id) { mutableStateOf(member.materialType) }
    val existingSection = member.concreteSection ?: ConcreteSectionProps(250.0, 500.0)
    var width by remember(member.id) { mutableStateOf(formatCoord(existingSection.width)) }
    var depth by remember(member.id) { mutableStateOf(formatCoord(existingSection.depth)) }
    var fcu by remember(member.id) { mutableStateOf(formatCoord(existingSection.fcu)) }
    var fy by remember(member.id) { mutableStateOf(formatCoord(existingSection.fy)) }
    var cover by remember(member.id) { mutableStateOf(formatCoord(existingSection.cover)) }
    val steelSections = remember { SteelTables.getAllSections() }
    var steelSectionName by remember(member.id) {
        mutableStateOf(member.steelSectionName ?: SteelFrameDesign.AUTO_SECTION)
    }
    var steelExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val widthValue = width.toDoubleOrNull()
    val depthValue = depth.toDoubleOrNull()
    val fcuValue = fcu.toDoubleOrNull()
    val fyValue = fy.toDoubleOrNull()
    val coverValue = cover.toDoubleOrNull()
    val concreteValid = widthValue != null && widthValue > 0.0 &&
        depthValue != null && depthValue > 0.0 &&
        fcuValue != null && fcuValue > 0.0 &&
        fyValue != null && fyValue > 0.0 &&
        coverValue != null && coverValue >= 0.0
    val isValid = if (materialType == FrameMaterialType.Concrete) {
        concreteValid
    } else {
        steelSectionName.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(t("خصائص العضو ${member.id}", "Member ${member.id} properties")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                AppText(t("نوع العضو", "Member type"), fontSize = 10.sp, color = Color.Gray)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (type in FrameMemberType.entries) {
                        FilterChip(
                            selected = memberType == type,
                            onClick = { memberType = type },
                            label = { AppText(type.displayName, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                AppText(t("المادة", "Material"), fontSize = 10.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (material in FrameMaterialType.entries) {
                        FilterChip(
                            selected = materialType == material,
                            onClick = { materialType = material },
                            label = { AppText(material.displayName, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                if (materialType == FrameMaterialType.Concrete) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        NumberField(width, { width = it }, t("العرض b (مم)", "Width b (mm)"), Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        NumberField(depth, { depth = it }, t("العمق h (مم)", "Depth h (mm)"), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        NumberField(fcu, { fcu = it }, t("fcu (MPa)", "fcu (MPa)"), Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        NumberField(fy, { fy = it }, t("fy (MPa)", "fy (MPa)"), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    NumberField(cover, { cover = it }, t("الغطاء (مم)", "Cover (mm)"), Modifier.fillMaxWidth())
                } else {
                    val isAutoSection = SteelFrameDesign.isAutoSection(steelSectionName)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        AppButton(
                            label = if (isAutoSection) t("تلقائي", "Auto") else steelSectionName,
                            onClick = { steelExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            style = AppButtonStyle.Outline,
                            fontSize = 12.sp
                        )
                        DropdownMenu(
                            expanded = steelExpanded,
                            onDismissRequest = { steelExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    AppText(
                                        t("تلقائي - اختيار أفضل قطاع", "Auto - select optimal section"),
                                        fontSize = 11.sp
                                    )
                                },
                                onClick = {
                                    steelSectionName = SteelFrameDesign.AUTO_SECTION
                                    steelExpanded = false
                                }
                            )
                            for (section in steelSections) {
                                DropdownMenuItem(
                                    text = {
                                        AppText(
                                            "${section.name}  (${section.depth.toInt()}×${section.width.toInt()})",
                                            fontSize = 11.sp
                                        )
                                    },
                                    onClick = {
                                        steelSectionName = section.name
                                        steelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AppText(
                        t(
                            "تُؤخذ خصائص القطاع من جدول القطاعات القياسي.",
                            "Section properties are taken from the standard steel table."
                        ),
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                    if (isAutoSection) {
                        AppText(
                            t(
                                "سيُختار القطاع الأمثل تلقائياً عند التحليل.",
                                "The optimal section is selected automatically during analysis."
                            ),
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    SteelSectionDrawing(
                        sectionName = if (isAutoSection) "IPE 300" else steelSectionName,
                        memberLengthMm = (memberLengthM * 1000.0).let { if (it > 1.0) it else 6000.0 },
                        memberType = memberType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                label = t("حفظ", "Save"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text,
                enabled = isValid,
                onClick = {
                    val section = if (materialType == FrameMaterialType.Concrete) {
                        ConcreteSectionProps(
                            width = widthValue ?: existingSection.width,
                            depth = depthValue ?: existingSection.depth,
                            fcu = fcuValue ?: existingSection.fcu,
                            fy = fyValue ?: existingSection.fy,
                            cover = coverValue ?: existingSection.cover
                        )
                    } else {
                        member.concreteSection ?: existingSection
                    }
                    onSave(
                        member.copy(
                            memberType = memberType,
                            materialType = materialType,
                            concreteSection = section,
                            steelSectionName = if (materialType == FrameMaterialType.Steel) {
                                steelSectionName.trim()
                                    .takeUnless { SteelFrameDesign.isAutoSection(it) }
                            } else {
                                null
                            }
                        )
                    )
                }
            )
        },
        dismissButton = {
            AppButton(
                label = t("إلغاء", "Cancel"),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

fun memberLength(member: FrameMember, nodes: List<FrameNode>): Double {
    val a = nodes.find { it.id == member.nodeI } ?: return 0.0
    val b = nodes.find { it.id == member.nodeJ } ?: return 0.0
    return kotlin.math.hypot(b.x - a.x, b.y - a.y)
}

fun toolLabel(tool: ModelerTool): String = when (tool) {
    ModelerTool.Select -> t("تحديد", "Select")
    ModelerTool.Pan -> t("تحريك العرض", "Pan")
    ModelerTool.Move -> t("تحريك", "Move")
    ModelerTool.Joint -> t("عقدة", "Joint")
    ModelerTool.Member -> t("عضو", "Member")
    ModelerTool.Support -> t("استناد", "Support")
    ModelerTool.Hinge -> t("مفصل", "Hinge")
    ModelerTool.NodalLoad -> t("حمل عقدة", "Nodal load")
    ModelerTool.MemberLoad -> t("حمل عضو", "Member load")
    ModelerTool.Slab -> t("بلاطة", "Slab")
    ModelerTool.Erase -> t("حذف", "Erase")
}

fun fmtLoad(value: Double): String = formatCoord(value)

fun formatCoord(value: Double): String =
    if (kotlin.math.abs(value - kotlin.math.round(value)) < 1e-6) {
        kotlin.math.round(value).toInt().toString()
    } else {
        "%.3f".format(value)
    }

@Composable
fun ToolChip(
    label: String,
    value: ModelerTool,
    selected: ModelerTool,
    onClick: (ModelerTool) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onClick(value) },
        label = { AppText(label, fontSize = 10.sp, maxLines = 1) },
        modifier = Modifier.padding(horizontal = 2.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF1565C0),
            selectedLabelColor = Color.White
        )
    )
}

@Composable
fun GridDropdown(
    label: String,
    options: List<GridOption>,
    selectedMm: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val option = options.find { it.mm == selectedMm } ?: options.first()
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                AppText(
                    "$label: ${if (LocaleHelper.isArabic()) option.labelAr else option.labelEn}",
                    fontSize = 10.sp
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (o in options) {
                DropdownMenuItem(
                    text = {
                        AppText(if (LocaleHelper.isArabic()) o.labelAr else o.labelEn, fontSize = 12.sp)
                    },
                    onClick = {
                        expanded = false
                        onSelect(o.mm)
                    }
                )
            }
        }
    }
}

@Composable
fun LayerDropdown(
    layers: ModelerLayerVisibility,
    onToggle: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val mask = layers.toMask()
    val items = listOf(
        Triple(t("الشبكة", "Grid"), ModelerLayerVisibility.GRID, layers.grid),
        Triple(t("العقد", "Joints"), ModelerLayerVisibility.JOINTS, layers.joints),
        Triple(t("الأعضاء", "Members"), ModelerLayerVisibility.MEMBERS, layers.members),
        Triple(t("الاستنادات", "Supports"), ModelerLayerVisibility.SUPPORTS, layers.supports),
        Triple(t("الأحمال", "Loads"), ModelerLayerVisibility.LOADS, layers.loads),
        Triple(t("البلاطات", "Slabs"), ModelerLayerVisibility.SLABS, layers.slabs),
        Triple(t("الأبعاد", "Dimensions"), ModelerLayerVisibility.DIMENSIONS, layers.dimensions)
    )
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { AppText(t("الطبقات", "Layers"), fontSize = 10.sp) },
            leadingIcon = {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((label, bit, enabled) in items) {
                DropdownMenuItem(
                    text = { AppText(label, fontSize = 12.sp) },
                    trailingIcon = {
                        if (enabled) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = { onToggle(mask xor bit) }
                )
            }
            DropdownMenuItem(
                text = {
                    AppText(
                        t("إظهار الكل", "Show all"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                onClick = { onToggle(ModelerLayerVisibility.ALL) }
            )
        }
    }
}

fun gridLabel(mm: Int): String =
    if (mm >= 1000) "${mm / 1000} m" else "$mm mm"

fun snapLabel(mm: Int): String =
    if (mm == 0) "Off" else gridLabel(mm)

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
fun NodalLoadDialog(
    nodeId: Int,
    initial: NodalLoad? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double, String) -> Unit
) {
    var fx by remember(initial) { mutableStateOf(initial?.fx?.let(::formatCoord) ?: "0") }
    var fy by remember(initial) { mutableStateOf(initial?.fy?.let(::formatCoord) ?: "-10") }
    var mz by remember(initial) { mutableStateOf(initial?.mz?.let(::formatCoord) ?: "0") }
    var loadCase by remember(initial) { mutableStateOf(initial?.loadCase ?: "DL") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                if (initial == null) t("حمل عقدة $nodeId", "Nodal load at joint $nodeId")
                else t("تعديل حمل العقدة $nodeId", "Edit load at joint $nodeId")
            )
        },
        text = {
            Column {
                Row {
                    NumberField(fx, { fx = it }, "Fx (kN)", Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(fy, { fy = it }, t("Fy (kN)", "Fy (kN)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    NumberField(mz, { mz = it }, t("Mz (kN·m)", "Mz (kN·m)"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            AppButton(
                label = if (initial == null) t("إضافة", "Add") else t("تحديث", "Update"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text,
                onClick = {
                    onConfirm(
                        fx.toDoubleOrNull() ?: 0.0,
                        fy.toDoubleOrNull() ?: 0.0,
                        mz.toDoubleOrNull() ?: 0.0,
                        loadCase.ifBlank { "DL" }
                    )
                }
            )
        },
        dismissButton = {
            AppButton(
                label = t("إلغاء", "Cancel"),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

@Composable
fun MemberLoadDialog(
    memberId: Int,
    initial: MemberLoad? = null,
    onDismiss: () -> Unit,
    onConfirm: (MemberLoadType, Double, Double, String) -> Unit
) {
    var loadType by remember(initial) { mutableStateOf(initial?.loadType ?: MemberLoadType.UDL) }
    var value by remember(initial) { mutableStateOf(initial?.value?.let(::formatCoord) ?: "20") }
    var position by remember(initial) { mutableStateOf(initial?.position?.let(::formatCoord) ?: "0") }
    var loadCase by remember(initial) { mutableStateOf(initial?.loadCase ?: "DL") }
    var typeExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                if (initial == null) t("حمل عضو $memberId", "Member load $memberId")
                else t("تعديل حمل العضو $memberId", "Edit member load $memberId")
            )
        },
        text = {
            Column {
                Box {
                    AppButton(
                        label = when (loadType) {
                            MemberLoadType.UDL -> t("حمل موزع منتظم", "Uniform load")
                            MemberLoadType.PointLoad -> t("حمل مركز", "Point load")
                            MemberLoadType.Moment -> t("عزم", "Moment")
                            MemberLoadType.LinearVarying -> t("موزع متغير", "Varying load")
                            MemberLoadType.Trapezoidal -> t("شبه منحرف (بلاطة)", "Trapezoidal (slab)")
                        },
                        onClick = { typeExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = AppButtonStyle.Outline,
                        fontSize = 12.sp
                    )
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        for (type in MemberLoadType.entries) {
                            DropdownMenuItem(
                                text = {
                                    AppText(
                                        when (type) {
                                            MemberLoadType.UDL -> t("حمل موزع منتظم", "Uniform load")
                                            MemberLoadType.PointLoad -> t("حمل مركز", "Point load")
                                            MemberLoadType.Moment -> t("عزم", "Moment")
                                            MemberLoadType.LinearVarying -> t("موزع متغير", "Varying load")
                                            MemberLoadType.Trapezoidal -> t("شبه منحرف (بلاطة)", "Trapezoidal (slab)")
                                        },
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    typeExpanded = false
                                    loadType = type
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    NumberField(value, { value = it }, t("القيمة", "Value"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(position, { position = it }, t("الموقع (م)", "Position (m)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            AppButton(
                label = if (initial == null) t("إضافة", "Add") else t("تحديث", "Update"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text,
                onClick = {
                    onConfirm(
                        loadType,
                        value.toDoubleOrNull() ?: 0.0,
                        position.toDoubleOrNull() ?: 0.0,
                        loadCase.ifBlank { "DL" }
                    )
                }
            )
        },
        dismissButton = {
            AppButton(
                label = t("إلغاء", "Cancel"),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

@Composable
fun HingeDialog(
    member: FrameMember,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Boolean) -> Unit
) {
    var releaseI by remember { mutableStateOf(member.releaseI) }
    var releaseJ by remember { mutableStateOf(member.releaseJ) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(t("مفاصل العضو ${member.id}", "Member ${member.id} releases")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = releaseI, onCheckedChange = { releaseI = it })
                    Spacer(Modifier.width(8.dp))
                    AppText(t("مفصل عند العقدة ${member.nodeI}", "Hinge at joint ${member.nodeI}"), fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = releaseJ, onCheckedChange = { releaseJ = it })
                    Spacer(Modifier.width(8.dp))
                    AppText(t("مفصل عند العقدة ${member.nodeJ}", "Hinge at joint ${member.nodeJ}"), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            AppButton(
                label = t("تطبيق", "Apply"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text,
                onClick = { onConfirm(releaseI, releaseJ) }
            )
        },
        dismissButton = {
            AppButton(
                label = t("إلغاء", "Cancel"),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

@Composable
fun SlabDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, String, SlabDistribution) -> Unit
) {
    var thickness by remember { mutableStateOf("200") }
    var load by remember { mutableStateOf("10") }
    var loadCase by remember { mutableStateOf("DL") }
    var distribution by remember { mutableStateOf(SlabDistribution.TwoWay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(t("بلاطة جديدة", "New slab panel")) },
        text = {
            Column {
                Row {
                    NumberField(thickness, { thickness = it }, t("السماكة (مم)", "Thickness (mm)"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(load, { load = it }, t("الحمل (kN/m²)", "Load (kN/m²)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AppText(t("توزيع الحمل", "Load distribution"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = distribution == SlabDistribution.OneWay,
                        onClick = { distribution = SlabDistribution.OneWay },
                        label = { AppText(t("اتجاه واحد", "One-way"), fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = distribution == SlabDistribution.TwoWay,
                        onClick = { distribution = SlabDistribution.TwoWay },
                        label = { AppText(t("اتجاهين", "Two-way"), fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                label = t("إضافة", "Add"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text,
                onClick = {
                    onConfirm(
                        thickness.toDoubleOrNull() ?: 200.0,
                        load.toDoubleOrNull() ?: 10.0,
                        loadCase.ifBlank { "DL" },
                        distribution
                    )
                }
            )
        },
        dismissButton = {
            AppButton(
                label = t("إلغاء", "Cancel"),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

@Composable
fun EditSlabDialog(
    panel: SlabPanel,
    onDismiss: () -> Unit,
    onSave: (SlabPanel) -> Unit,
    onDelete: () -> Unit
) {
    var thickness by remember { mutableStateOf(panel.thicknessMm.toString()) }
    var load by remember { mutableStateOf(panel.loadKNm2.toString()) }
    var loadCase by remember { mutableStateOf(panel.loadCase) }
    var distribution by remember { mutableStateOf(panel.distribution) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(t("بلاطة ${panel.id}", "Slab ${panel.id}")) },
        text = {
            Column {
                Row {
                    NumberField(thickness, { thickness = it }, t("السماكة (مم)", "Thickness (mm)"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(load, { load = it }, t("الحمل (kN/m²)", "Load (kN/m²)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = distribution == SlabDistribution.OneWay,
                        onClick = { distribution = SlabDistribution.OneWay },
                        label = { AppText(t("اتجاه واحد", "One-way"), fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = distribution == SlabDistribution.TwoWay,
                        onClick = { distribution = SlabDistribution.TwoWay },
                        label = { AppText(t("اتجاهين", "Two-way"), fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                label = t("حفظ", "Save"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text,
                onClick = {
                    onSave(
                        panel.copy(
                            thicknessMm = thickness.toDoubleOrNull() ?: panel.thicknessMm,
                            loadKNm2 = load.toDoubleOrNull() ?: panel.loadKNm2,
                            loadCase = loadCase.ifBlank { panel.loadCase },
                            distribution = distribution
                        )
                    )
                }
            )
        },
        dismissButton = {
            Row {
                AppButton(
                    label = t("حذف", "Delete"),
                    onClick = onDelete,
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false,
                    icon = Icons.Default.Delete,
                    contentOverride = Color(0xFFC62828)
                )
                AppButton(
                    label = t("إلغاء", "Cancel"),
                    onClick = onDismiss,
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        }
    )
}
