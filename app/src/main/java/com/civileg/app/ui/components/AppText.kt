package com.civileg.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text roles (§12.4). Each role maps to a typography style, a default color and an
 * overflow policy, so screens never have to remember the pair.
 */
enum class AppTextRole {
    /** Screen / section heading. Up to two lines. */
    Headline,

    /** Card / row title. Up to two lines. */
    Title,

    /** Descriptions and notes: wraps over as many lines as needed. */
    Body,

    /** Chips, captions, table headers: one line, ellipsized. */
    Caption,

    /** Numbers, units and utilization ratios: never truncated (R14). */
    Result
}

/**
 * The single text composable of the app.
 *
 *  - colors come from the theme roles (no raw colors in screens, R13);
 *  - [AppTextRole.Caption] is single-line with ellipsis, [AppTextRole.Body]
 *    wraps, and [AppTextRole.Result] is never ellipsized so a value can always be
 *    read in full.
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    role: AppTextRole = AppTextRole.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    val scheme = MaterialTheme.colorScheme
    val style = when (role) {
        AppTextRole.Headline -> MaterialTheme.typography.headlineSmall
        AppTextRole.Title -> MaterialTheme.typography.titleMedium
        AppTextRole.Body -> MaterialTheme.typography.bodyMedium
        AppTextRole.Caption -> MaterialTheme.typography.bodySmall
        AppTextRole.Result -> MaterialTheme.typography.titleMedium
    }
    val defaultColor = when (role) {
        AppTextRole.Caption -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    val effectiveMaxLines = when (role) {
        AppTextRole.Caption -> minOf(maxLines, 1)
        AppTextRole.Headline, AppTextRole.Title -> minOf(maxLines, 2)
        AppTextRole.Body -> maxLines
        AppTextRole.Result -> Int.MAX_VALUE
    }

    Text(
        text = text,
        modifier = modifier,
        color = if (color == Color.Unspecified) defaultColor else color,
        style = style,
        maxLines = effectiveMaxLines,
        softWrap = role != AppTextRole.Caption,
        overflow = if (role == AppTextRole.Result) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}
