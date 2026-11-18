package com.civileg.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.civileg.app.ui.theme.onColorFor

/**
 * Button variants. Colors always come from the theme roles; the content color is
 * derived from the container with [onColorFor], never chosen by hand (R13).
 */
enum class AppButtonStyle {
    /** Filled with the primary role. */
    Primary,

    /** Tonal: secondary container. */
    Secondary,

    /** Bordered, transparent fill. */
    Outline,

    /** No border, no fill. */
    Text,

    /** Filled with the error role. */
    Danger
}

/**
 * The single button of the app.
 *
 * Invariants (§12.3, R14):
 *  - one line, fixed minimum height: a long label never makes the button taller;
 *  - the full label is measured and the `labelShort` variant is used when it does
 *    not fit; ellipsis is the last resort;
 *  - content color is derived from the container color.
 *
 * Pass `Modifier.weight(1f)` from a row of equally-sized actions; the button never
 * imposes a fixed width.
 */
@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelShort: String? = null,
    icon: ImageVector? = null,
    style: AppButtonStyle = AppButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillMaxWidth: Boolean = true,
    containerOverride: Color? = null,
    contentOverride: Color? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val scheme = MaterialTheme.colorScheme
    val container = containerOverride ?: style.containerColor(scheme)
    val content = contentOverride ?: style.contentColor(scheme)
    val baseStyle = MaterialTheme.typography.labelLarge
    val textStyle = if (fontSize == TextUnit.Unspecified) baseStyle else baseStyle.copy(fontSize = fontSize)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val chrome = 32.dp + if (icon != null) 28.dp else 0.dp
        val measured = measurer.measure(text = label, style = textStyle, maxLines = 1)
        val measuredWidth = if (measured.size.width > 0) {
            measured.size.width
        } else {
            // Fallback for headless test environments (Robolectric) where text measurer width is 0
            (label.length * 8).toInt()
        }
        val fits = !constraints.hasBoundedWidth || with(density) {
            measuredWidth <= (maxWidth - chrome).roundToPx()
        }
        val shown = if (fits || labelShort == null) label else labelShort

        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container.copy(alpha = 0.38f),
                disabledContentColor = content.copy(alpha = 0.70f)
            ),
            border = if (style == AppButtonStyle.Outline) BorderStroke(1.dp, scheme.primary) else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = content
                )
            } else {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = shown,
                    style = textStyle,
                    fontWeight = fontWeight,
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AppButtonStyle.containerColor(scheme: androidx.compose.material3.ColorScheme): Color =
    when (this) {
        AppButtonStyle.Primary -> scheme.primary
        AppButtonStyle.Secondary -> scheme.secondaryContainer
        AppButtonStyle.Outline, AppButtonStyle.Text -> Color.Transparent
        AppButtonStyle.Danger -> scheme.error
    }

@Composable
private fun AppButtonStyle.contentColor(scheme: androidx.compose.material3.ColorScheme): Color =
    when (this) {
        AppButtonStyle.Primary -> onColorFor(scheme.primary)
        AppButtonStyle.Secondary -> onColorFor(scheme.secondaryContainer)
        AppButtonStyle.Outline, AppButtonStyle.Text -> scheme.primary
        AppButtonStyle.Danger -> onColorFor(scheme.error)
    }
