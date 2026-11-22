package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.civileg.app.ui.components.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.drawing.ComposeRenderer
import com.civileg.app.domain.drawing.adapters.HordiCalculatedSectionAdapter
import com.civileg.app.domain.entities.HordiCalculatedSectionSnapshot
import java.util.Locale

/**
 * Preview only. The source screen supplies its selected app language and the frozen
 * handoff, never mutable current-form geometry. No execution-export action is exposed.
 */
@Composable
fun HordiCalculatedSectionOutput(
    output: HordiCalculatedSectionSnapshot,
    memberMark: String,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val built = remember(output, memberMark) { runCatching { HordiCalculatedSectionAdapter.build(output, memberMark) } }
    CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Card(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (isArabic) "قطاع عصب محسوب، للمراجعة فقط" else "Calculated rib section, review only", style = MaterialTheme.typography.titleMedium)
                Text(if (isArabic) "قطاع خلية واحدة من عرض التكرار، وليس لوحة كاملة أو رسومات تنفيذية. مواقع الأسياخ معتمدة للحساب وليست رفعًا للمنفذ." else
                    "One repeating-pitch section, not a full panel or execution drawing. Bar positions are adopted for calculation, not as-built.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                val drawing = built.getOrNull()
                if (drawing == null) {
                    Text(if (isArabic) "إحداثيات القطاع غير متاحة أو غير متسقة. لم يُرسم حديد بديل." else
                        "Section coordinates are unavailable or inconsistent. No substitute reinforcement was drawn.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    TechnicalSectionText("$memberMark | Revision: ${drawing.revisionId}")
                    // Technical coordinates remain x-right/y-up in both app languages.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ComposeRenderer.render(drawing.view.scene, Modifier.fillMaxWidth().aspectRatio(1.18f))
                    }
                    Text(if (isArabic) "مرجع الحساب وترتيب الأسياخ" else "Calculation and adopted arrangement", style = MaterialTheme.typography.titleSmall)
                    TechnicalSectionText(drawing.methodReference)
                    TechnicalSectionText(drawing.placementBasis)
                    Text(String.format(Locale.US, if (isArabic)
                        "عرض التكرار الفعلي %.3f مم؛ العرض التحليلي b_eff = %.3f مم ليس حدًا فعليًا أو عرض اللوحة الكاملة." else
                        "Entered physical pitch %.3f mm; analytical b_eff %.3f mm is not a physical edge or full-panel width.",
                        output.section.enteredRibPitchMm, output.section.analyticalFlangeWidthMm), style = MaterialTheme.typography.bodySmall)
                    TechnicalSectionText(String.format(Locale.US, "Required As %.3f mm² | Provided As %.3f mm²", drawing.requiredSteelMm2, drawing.providedSteelMm2))
                    Text(String.format(Locale.US, if (isArabic)
                        "بيانات الكانة: %d أفرع، قطر %.3f مم كل %.3f مم. مسار الغلق والخطافات وإزاحة أول كانة غير متاح." else
                        "Supplied links: %d legs, dia %.3f mm @ %.3f mm. No closure, hooks or first-link offset supplied.",
                        output.section.linkLegs, output.section.linkDiameterMm, output.section.linkSpacingMm), style = MaterialTheme.typography.bodySmall)
                    Text(if (isArabic) "مراكز الأسياخ المعتمدة: x من يسار العصب، وy من أسفل القطاع الفعلي (مم). إحداثيات وليست جدول تفريد." else
                        "Adopted bar centres: x from web left, y from physical bottom (mm). Coordinates, not a bar bending schedule.", style = MaterialTheme.typography.bodySmall)
                    output.section.bars.orEmpty().forEach { bar ->
                        TechnicalSectionText(String.format(Locale.US, "L%d/B%d: x %.3f, y %.3f, dia %.3f mm",
                            bar.layerIndex + 1, bar.indexInLayer + 1, bar.xFromWebLeftMm, bar.yFromBottomMm, bar.diameterMm))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (isArabic) "التنفيذ والتصدير التنفيذي محجوبان. قيود الحساب الأصلية:" else
                        "Execution and execution exports are blocked. Original calculation limitations:",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                    drawing.incompleteChecks.forEach { TechnicalSectionText(it) }
                }
            }
        }
    }
}

@Composable
private fun TechnicalSectionText(value: String) {
    Text(value, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        style = MaterialTheme.typography.bodySmall.merge(TextStyle(textDirection = TextDirection.Ltr)))
}
