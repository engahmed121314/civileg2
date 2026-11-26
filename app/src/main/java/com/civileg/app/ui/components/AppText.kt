package com.civileg.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

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
 * It is a drop-in replacement for [Text]: every parameter of `Text` is accepted, so
 * a screen can be migrated call by call without visual change. Passing a [role]
 * instead applies the app's typography/color/overflow policy for that role:
 *
 *  - colors come from the theme roles (no raw colors in screens, R13);
 *  - [AppTextRole.Caption] is single-line with ellipsis;
 *  - [AppTextRole.Result] is never ellipsized, so a value can always be read in full.
 */
@Composable
fun AppText(
    text: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    role: AppTextRole? = null
) {
    val scheme = MaterialTheme.colorScheme
    var effectiveColor = color
    var effectiveStyle = style
    var effectiveMaxLines = maxLines
    var effectiveOverflow = overflow
    var effectiveSoftWrap = softWrap

    if (role != null) {
        effectiveStyle = when (role) {
            AppTextRole.Headline -> MaterialTheme.typography.headlineSmall
            AppTextRole.Title -> MaterialTheme.typography.titleMedium
            AppTextRole.Body -> MaterialTheme.typography.bodyMedium
            AppTextRole.Caption -> MaterialTheme.typography.bodySmall
            AppTextRole.Result -> MaterialTheme.typography.titleMedium
        }
        if (color == Color.Unspecified) {
            effectiveColor = when (role) {
                AppTextRole.Caption -> scheme.onSurfaceVariant
                else -> scheme.onSurface
            }
        }
        when (role) {
            AppTextRole.Caption -> {
                effectiveMaxLines = minOf(maxLines, 1)
                effectiveOverflow = TextOverflow.Ellipsis
                effectiveSoftWrap = false
            }
            AppTextRole.Headline, AppTextRole.Title -> {
                effectiveMaxLines = minOf(maxLines, 2)
                effectiveOverflow = TextOverflow.Ellipsis
            }
            AppTextRole.Result -> {
                effectiveMaxLines = Int.MAX_VALUE
                effectiveOverflow = TextOverflow.Clip
                effectiveSoftWrap = true
            }
            AppTextRole.Body -> Unit
        }
    }

    // Resolve the paragraph direction from the text itself (first strong character); Compose otherwise follows the layout direction,
    // which reversed English values such as "700 x 700 mm" inside the Arabic (RTL) screens.
    if (effectiveStyle.textDirection == androidx.compose.ui.text.style.TextDirection.Unspecified) {
        effectiveStyle = effectiveStyle.merge(TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
    }
    // Arabic reads RTL and sits on the right, English reads LTR and sits on the left, whatever the layout direction.
    // Only an unset or default (Start) alignment is resolved from the first strong character; an explicit alignment is kept.
    val contentAlign = if (textAlign == null || textAlign == TextAlign.Start) alignmentFromText(text) else textAlign

    Text(
        text = com.civileg.app.ui.theme.arabicUiText(text),
        modifier = modifier,
        color = effectiveColor,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        lineHeight = lineHeight,
        overflow = effectiveOverflow,
        softWrap = effectiveSoftWrap,
        maxLines = effectiveMaxLines,
        textAlign = contentAlign,
        style = effectiveStyle
    )
}

/** First strong character decides: Arabic-script letter gives Right, Latin letter gives Left, anything else keeps the default. */
internal fun alignmentFromText(text: String): TextAlign? {
    var i = 0
    while (i < text.length) {
        val cp = Character.codePointAt(text, i)
        when (Character.getDirectionality(cp)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return TextAlign.Right
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return TextAlign.Left
            else -> {}
        }
        i += Character.charCount(cp)
    }
    return null
}
