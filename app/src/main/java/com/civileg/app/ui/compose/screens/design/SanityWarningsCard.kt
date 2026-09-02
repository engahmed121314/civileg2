package com.civileg.app.ui.compose.screens.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.safety.SanityResult
import com.civileg.app.domain.safety.SanityCheck
import com.civileg.app.domain.safety.SanityStatus
import com.civileg.app.ui.designsystem.components.EngineeringCard
import com.civileg.app.ui.designsystem.components.EngineeringSectionHeader

/**
 * Surfaces QA / sanity findings (roadmap: `out.sanity.warnings`) in the design UI.
 * Rendered only when the design carries findings — hidden when clean or unknown.
 */
@Composable
fun SanityWarningsCard(
    sanity: SanityResult?,
    modifier: Modifier = Modifier
) {
    val warnings = sanity?.checks?.filter { it.severity == SanityStatus.WARNING }.orEmpty()
    val errors = sanity?.checks?.filter { it.severity == SanityStatus.ERROR }.orEmpty()
    if (warnings.isEmpty() && errors.isEmpty()) return

    EngineeringCard(modifier = modifier) {
        EngineeringSectionHeader(
            title = when {
                errors.isNotEmpty() && warnings.isNotEmpty() -> "Design Review — Errors & Warnings"
                errors.isNotEmpty() -> "Design Review — Errors"
                else -> "Design Review — Warnings"
            }
        )
        Column(Modifier.padding(top = 8.dp)) {
            errors.forEach { check ->
                FindingRow(check, isError = true)
            }
            warnings.forEach { check ->
                FindingRow(check, isError = false)
            }
        }
    }
}

@Composable
private fun FindingRow(check: SanityCheck, isError: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (isError) "✖" else "⚠",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = check.message,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}