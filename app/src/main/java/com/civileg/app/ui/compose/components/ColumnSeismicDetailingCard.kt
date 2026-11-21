package com.civileg.app.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.entities.EcpColumnSeismicDetailing
import com.civileg.app.ui.components.AppText

/**
 * ECP 203-2020 §6-8 ductile detailing section of the column result.
 * Rendered only when a ductile mode (limited/adequate) was requested; the
 * header states the design is INCOMPLETE while frame-context checks are
 * blocked, and blocked checks are listed with their clauses and reasons.
 * Extracted from ColumnScreen for direct render/screenshot testing.
 */
@Composable
fun ColumnSeismicDetailingSection(d: EcpColumnSeismicDetailing) {
    val ar = com.civileg.app.utils.LocaleHelper.isArabic()
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    )
    AppText(
        if (ar) "تكسيح الممطولية — ${d.mode.displayNameAr} (غير مكتمل: فحوصات الإطار معلقة)" else "Ductile detailing — ${d.mode.displayNameEn} (INCOMPLETE: frame checks pending)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag("seismic-detailing-header")
    )
    DetailingRow(if (ar) "طول المنطقة الحرجة Lo (كل طرف)" else "Critical region Lo (each end)", String.format("%.0f mm", d.criticalRegionLengthMm))
    DetailingRow(if (ar) "تباعد الكانات بالمنطقة الحرجة S0" else "Tie spacing in critical region S0", String.format("%.0f mm", d.criticalTieSpacingMm))
    DetailingRow(if (ar) "أول كانة من وجه الكمرة" else "First tie from beam face", String.format("≤ %.0f mm", d.firstTieMaxOffsetMm))
    DetailingRow(if (ar) "تباعد الكانات خارج المنطقة الحرجة" else "Tie spacing outside critical region", String.format("≤ %.0f mm", d.elsewhereTieSpacingMm))
    d.checks.forEach { c ->
        AppText(
            "${if (c.satisfied) "✓" else "✗"} ${c.name}: ${c.demand} — ${c.limit}  [${c.clause}]",
            fontSize = 11.sp,
            color = if (c.satisfied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
    d.blockedChecks.forEach { b ->
        AppText(
            "⚠ BLOCKED: ${b.name} — ${b.reason}  [${b.clause}]",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("seismic-blocked-${b.name}")
        )
    }
}

@Composable
private fun DetailingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
