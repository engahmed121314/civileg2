package com.civileg.app.ui.theme

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.x contrast ratio between two opaque colors, in the range 1.0 .. 21.0.
 *
 * Text needs >= 4.5:1; icons, borders and other non-text graphics need >= 3:1.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = calculateSrgbLuminance(a)
    val lb = calculateSrgbLuminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Relative luminance (WCAG 2.x) of an opaque sRGB color. */
private fun calculateSrgbLuminance(c: Color): Float {
    fun linear(channel: Float): Float =
        if (channel <= 0.03928f) channel / 12.92f
        else ((channel + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linear(c.red) + 0.7152f * linear(c.green) + 0.0722f * linear(c.blue)
}

/**
 * The more legible of [light] / [dark] on [bg].
 *
 * Use this instead of picking a text color by hand: any container with a custom
 * background gets a content color that is guaranteed to meet the WCAG threshold
 * against that exact background.
 */
fun onColorFor(
    bg: Color,
    light: Color = Color.White,
    dark: Color = Color(0xFF0B192C)
): Color = if (contrastRatio(light, bg) >= contrastRatio(dark, bg)) light else dark

/**
 * A surface whose content color is derived from its own background, so even a
 * `Text` with no explicit color is legible (R13).
 */
@Composable
fun ColoredSurface(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = color,
        contentColor = onColorFor(color),
        modifier = modifier,
        content = content
    )
}
