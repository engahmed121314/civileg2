package com.civileg.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.civileg.app.ui.compose.components.drawings.DrawingColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 contrast guard for the app palettes and the engineering drawing palettes.
 *
 * Thresholds: 4.5:1 for text, 3:1 for non-text (lines, fills, icons, strokes).
 * A failing entry here means a real user cannot read the value on screen, so the
 * tolerance is the threshold itself — no slack.
 *
 * Guards the regression fixed in `fix/theme-contrast`:
 *  - scheme `onBackground` was mapped to `textOnAccent` -> white on white (1.08:1)
 *  - scheme `primary` in dark mode was the navy brand surface (1.00:1 on surface)
 *  - dark drawing palette inherited light-only colours (dimColor 1.83:1, etc.)
 */
class ThemeContrastTest {

    private fun channel(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private class Check(val label: String, val fg: Color, val bg: Color, val min: Double)

    private fun assertAll(label: String, checks: List<Check>) {
        val failures = checks.mapNotNull { c ->
            val v = ratio(c.fg, c.bg)
            if (v < c.min) "${c.label} = ${"%.2f".format(v)}:1 (min ${c.min})" else null
        }
        assertTrue("$label contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun schemeChecks(label: String, scheme: androidx.compose.material3.ColorScheme) = listOf(
        Check("$label/onPrimary", scheme.onPrimary, scheme.primary, TEXT),
        Check("$label/onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, TEXT),
        Check("$label/onSecondary", scheme.onSecondary, scheme.secondary, TEXT),
        Check("$label/onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, TEXT),
        Check("$label/onTertiary", scheme.onTertiary, scheme.tertiary, TEXT),
        Check("$label/onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, TEXT),
        Check("$label/onError", scheme.onError, scheme.error, TEXT),
        Check("$label/onErrorContainer", scheme.onErrorContainer, scheme.errorContainer, TEXT),
        Check("$label/onBackground", scheme.onBackground, scheme.background, TEXT),
        Check("$label/onSurface", scheme.onSurface, scheme.surface, TEXT),
        Check("$label/onSurfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, TEXT),
        Check("$label/outline", scheme.outline, scheme.surface, NON_TEXT),
        Check("$label/outlineVariant", scheme.outlineVariant, scheme.surface, NON_TEXT)
    )

    /** Text drawn with a drawing-palette colour on the sheet it is rendered on. */
    private fun drawingTextChecks(label: String, p: DrawingColors, sheet: Color) = listOf(
        Check("$label/textPrimary", p.textPrimary, sheet, TEXT),
        Check("$label/textSecondary", p.textSecondary, sheet, TEXT),
        Check("$label/tableCellText", p.tableCellText, sheet, TEXT),
        Check("$label/dimColor", p.dimColor, sheet, TEXT),
        Check("$label/dimLineColor", p.dimLineColor, sheet, TEXT),
        Check("$label/labelGray", p.labelGray, sheet, TEXT),
        Check("$label/coverColor", p.coverColor, sheet, TEXT),
        Check("$label/sectionLineColor", p.sectionLineColor, sheet, TEXT),
        Check("$label/shearPerimColor", p.shearPerimColor, sheet, TEXT),
        Check("$label/safeGreen", p.safeGreen, sheet, TEXT),
        Check("$label/unsafeRed", p.unsafeRed, sheet, TEXT),
        Check("$label/warningOrange", p.warningOrange, sheet, TEXT)
    )

    /** Bar marks, stirrups and hatches: non-text, 3:1 is enough. */
    private fun drawingGraphicChecks(label: String, p: DrawingColors, sheet: Color) = listOf(
        Check("$label/rebarBlue", p.rebarBlue, sheet, NON_TEXT),
        Check("$label/topRebarBlue", p.topRebarBlue, sheet, NON_TEXT),
        Check("$label/stirrupPurple", p.stirrupPurple, sheet, NON_TEXT),
        Check("$label/secondaryRed", p.secondaryRed, sheet, NON_TEXT),
        Check("$label/distBarGreen", p.distBarGreen, sheet, NON_TEXT),
        Check("$label/concreteStroke", p.concreteStroke, sheet, NON_TEXT),
        Check("$label/extensionGray", p.extensionGray, sheet, NON_TEXT),
        Check("$label/stressPink", p.stressPink, sheet, NON_TEXT),
        Check("$label/soilBrown", p.soilBrown, sheet, NON_TEXT),
        Check("$label/waterBlue", p.waterBlue, sheet, NON_TEXT)
    )

    @Test
    fun lightSchemeMeetsAaContrast() {
        assertAll("Light scheme", schemeChecks("Light", PremiumLightColorScheme))
    }

    @Test
    fun darkSchemeMeetsAaContrast() {
        assertAll("Dark scheme", schemeChecks("Dark", PremiumDarkColorScheme))
    }

    @Test
    fun lightPaletteMeetsAaContrast() {
        val p = PremiumColorsLight
        assertAll(
            "Light palette",
            listOf(
                Check("textMuted/background", p.textMuted, p.background, TEXT),
                Check("textMuted/surface", p.textMuted, p.surface, TEXT),
                Check("textOnAccent/accent", p.textOnAccent, p.accent, TEXT),
                Check("safe/surface", p.safe, p.surface, TEXT),
                Check("warning/surface", p.warning, p.surface, TEXT),
                Check("danger/surface", p.danger, p.surface, TEXT),
                Check("textSecondary/surface", p.textSecondary, p.surface, TEXT)
            )
        )
    }

    @Test
    fun darkPaletteMeetsAaContrast() {
        val p = PremiumColorsDark
        assertAll(
            "Dark palette",
            listOf(
                Check("textMuted/background", p.textMuted, p.background, TEXT),
                Check("textMuted/surface", p.textMuted, p.surface, TEXT),
                Check("textOnAccent/accent", p.textOnAccent, p.accent, TEXT),
                Check("safe/surface", p.safe, p.surface, TEXT),
                Check("warning/surface", p.warning, p.surface, TEXT),
                Check("textSecondary/surface", p.textSecondary, p.surface, TEXT),
                Check("steelColor/surface", p.steelColor(), p.surface, NON_TEXT),
                Check("concreteColor/surface", p.concreteColor(), p.surface, NON_TEXT),
                Check("dimensionColor/surface", p.dimensionColor(), p.surface, TEXT)
            )
        )
    }

    @Test
    fun lightDrawingsAreLegibleOnWhiteSheet() {
        val sheet = PremiumColorsLight.surface
        assertAll("Light drawings", drawingTextChecks("Light", DrawingColors.Light, sheet) + drawingGraphicChecks("Light", DrawingColors.Light, sheet))
    }

    @Test
    fun darkDrawingsAreLegibleOnDarkSheet() {
        val sheet = PremiumColorsDark.surface
        assertAll("Dark drawings", drawingTextChecks("Dark", DrawingColors.Dark, sheet) + drawingGraphicChecks("Dark", DrawingColors.Dark, sheet))
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
    }
}
