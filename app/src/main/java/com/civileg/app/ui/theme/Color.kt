package com.civileg.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ===== Premium Geometric Color System =====

// Light Theme Colors - Premium Geometric Palette
object PremiumColorsLight {
    // Primary Geometric Palette - Deep Navy Base
    val primary = Color(0xFF003A70)
    val primaryContainer = Color(0xFF0F172A)
    val onPrimary = Color(0xFFFFFFFF)
    val onPrimaryContainer = Color(0xFFD6E4F0)
    
    // Secondary Geometric Accent - Slate
    val secondary = Color(0xFF4A5C7E)
    val secondaryContainer = Color(0xFF1E293B)
    val onSecondary = Color(0xFFFFFFFF)
    val onSecondaryContainer = Color(0xFFf1f5f9)
    
    // Tertiary Geometric Warm - Amber
    val tertiary = Color(0xFFC68400)
    val tertiaryContainer = Color(0xFF2E1B00)
    val onTertiary = Color(0xFFFFFFFF)
    val onTertiaryContainer = Color(0xFFFFEAA7)
    
    // Extended Premium Geometric Palette
    val error = Color(0xFFBA1A1A)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFF4F1A1A)
    val onErrorContainer = Color(0xFFFFD9D9)
    
    val outline = Color(0xFF73777F)
    val outlineVariant = Color(0xFF98A2AF)
    val gray = Color(0xFF757575)
    val grayLight = Color(0xFFE2E8F0)
    val grayDark = Color(0xFF475569)
    
    // Premium Background Geometrics
    val background = Color(0xFFF8FAFC)
    val onBackground = Color(0xFF0F172A)
    val surface = Color(0xFFFFFFFF)
    val onSurface = Color(0xFF0F172A)
    val surfaceVariant = Color(0xFFF1F5F9)
    val onSurfaceVariant = Color(0xFF475569)
    
    // Premium App Specific Geometric Colors
    val accent = Color(0xFFFF9800)
    val accentGradientStart = Color(0xFFFF9800)
    val accentGradientEnd = Color(0xFFFFB74D)
    val success = Color(0xFF2E7D32)
    val successGradientStart = Color(0xFF2E7D32)
    val successGradientEnd = Color(0xFF4CAF50)
    val danger = Color(0xFFC62828)
    val dangerGradientStart = Color(0xFFC62828)
    val dangerGradientEnd = Color(0xFFEF4444)
    
    // Premium Geometric Gradient References (as Compose Brush)
    val primaryGradient: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF003A70), Color(0xFF0F172A))
    )

    val gradientNavyToBlue: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF003A70), Color(0xFF0F172A))
    )
    
    val gradientBlueToPurple: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF0F172A), Color(0xFF4A5C7E))
    )
    
    val gradientPurpleToAmber: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF4A5C7E), Color(0xFFC68400))
    )
    
    val gradientAmberToOrange: Brush = Brush.horizontalGradient(
        listOf(Color(0xFFC68400), Color(0xFFFF9800))
    )
    
    val gradientSuccessToGreen: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF2E7D32), Color(0xFF4CAF50))
    )
    
    val gradientDangerToRed: Brush = Brush.horizontalGradient(
        listOf(Color(0xFFC62828), Color(0xFFEF4444))
    )
    
    // Surface Variants
    val surfaceElevated = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF8FAFC)
    val surfaceContainerHigh = Color(0xFFF1F5F9)
    
    // Text Variants
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF475569)
    val textMuted = Color(0xFF64748B)
    val textInverse = Color(0xFFFFFFFF)
    val textOnAccent = Color(0xFF000000)
    
    // Premium Status Colors
    val safe = Color(0xFF2E7D32)
    val safeBright = Color(0xFF38A13F)
    val warning = Color(0xFFC41E3A)
    val warningBright = Color(0xFFF6871F)
    val unsafe = Color(0xFFC62828)
    val unsafeBright = Color(0xFFEF4444)
    val info = Color(0xFF0F172A)
    
    // Premium Geometric Pattern Colors
    val patternLight = Color(0xFF0F172A).copy(alpha = 0.03f)
    val patternMedium = Color(0xFF0F172A).copy(alpha = 0.08f)
    val patternDark = Color(0xFF0F172A).copy(alpha = 0.15f)
    
    // Premium Navy Shades
    val navyDeep = Color(0xFF0F172A)
    val navyMedium = Color(0xFF1E293B)
    val navyLight = Color(0xFFF1F5F9)
}

// Dark Theme Colors - Premium Geometric Palette
object PremiumColorsDark {
    // Primary Geometric Palette - Dark Navy Base
    val primary = Color(0xFF0F172A)
    val primaryContainer = Color(0xFF1E293B)
    val onPrimary = Color(0xFFF8FAFC)
    val onPrimaryContainer = Color(0xFFF1F5F9)
    
    // Secondary Geometric Accent - Slate (lighter in dark)
    val secondary = Color(0xFF6B7280)
    val secondaryContainer = Color(0xFF1E293B)
    val onSecondary = Color(0xFF1F2937)
    val onSecondaryContainer = Color(0xFFf1f5f9)
    
    // Tertiary Geometric Warm - Amber (adjusted for dark)
    val tertiary = Color(0xFFD9AA35)
    val tertiaryContainer = Color(0xFF2E1B00)
    val onTertiary = Color(0xFF1F2937)
    val onTertiaryContainer = Color(0xFFFFEAA7)
    
    // Extended Premium Geometric Palette (Dark)
    val error = Color(0xFFEF4444)
    val onError = Color(0xFF1F2937)
    val errorContainer = Color(0xFF933300)
    val onErrorContainer = Color(0xFFFFF0F0)
    
    val outline = Color(0xFF9CA3AF)
    val outlineVariant = Color(0xFF6B7280)
    val gray = Color(0xFFD1D5DB)
    val grayLight = Color(0xFF374151)
    val grayDark = Color(0xFF1E293B)
    
    // Dark Premium Background Geometrics
    val background = Color(0xFF0F172A)
    val onBackground = Color(0xFFF8FAFC)
    val surface = Color(0xFF1E293B)
    val onSurface = Color(0xFFF8FAFC)
    val surfaceVariant = Color(0xFF1E293B)
    val onSurfaceVariant = Color(0xFF6B7280)
    
    // Dark App Specific Geometric Colors
    val accent = Color(0xFFFB923C)
    val accentGradientStart = Color(0xFFFB923C)
    val accentGradientEnd = Color(0xFFFD7E14)
    val success = Color(0xFF38A13F)
    val successGradientStart = Color(0xFF38A13F)
    val successGradientEnd = Color(0xFF22C55E)
    val danger = Color(0xFFEF4444)
    val dangerGradientStart = Color(0xFFEF4444)
    val dangerGradientEnd = Color(0xFFF87171)
    
    // Geometric Gradients (Dark)
    val primaryGradient: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF0F172A), Color(0xFF1E293B))
    )

    val gradientNavyToBlueDark: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF0F172A), Color(0xFF1E293B))
    )
    
    val gradientBlueToPurpleDark: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF1E293B), Color(0xFF6B7280))
    )
    
    // Surface Variants (Dark)
    val surfaceElevated = Color(0xFF1E293B)
    val surfaceContainerLow = Color(0xFF1E293B)
    val surfaceContainerHigh = Color(0xFF374151)
    
    // Text Variants (Dark)
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF6B7280)
    val textMuted = Color(0xFF9CA3AF)
    val textInverse = Color(0xFF1F2937)
    val textOnAccent = Color(0xFF1F2937)
    
    // Dark Status Colors
    val safe = Color(0xFF38A13F)
    val safeBright = Color(0xFF48BBD0)
    val warning = Color(0xFFF6871F)
    val warningBright = Color(0xFFFDBB2D)
    val unsafe = Color(0xFFEF4444)
    val unsafeBright = Color(0xFFF87171)
    val info = Color(0xFF0F172A)
    
    // Dark Pattern Colors
    val patternLight = Color(0xFF0F172A).copy(alpha = 0.03f)
    val patternMedium = Color(0xFF0F172A).copy(alpha = 0.08f)
    val patternDark = Color(0xFF0F172A).copy(alpha = 0.15f)
    
    // Dark Navy Shades
    val navyDeep = Color(0xFF0F172A)
    val navyMedium = Color(0xFF1E293B)
    val navyLight = Color(0xFF374151)
}

// Object for easy access to current theme colors
object PremiumColorScheme {
    @Composable
    fun getPrimary(): Color = if (isDarkTheme()) PremiumColorsDark.primary else PremiumColorsLight.primary
    
    @Composable
    fun getOnPrimary(): Color = if (isDarkTheme()) PremiumColorsDark.onPrimary else PremiumColorsLight.onPrimary
    
    @Composable
    fun getSecondary(): Color = if (isDarkTheme()) PremiumColorsDark.secondary else PremiumColorsLight.secondary
    
    @Composable
    fun getOnSecondary(): Color = if (isDarkTheme()) PremiumColorsDark.onSecondary else PremiumColorsLight.onSecondary
    
    @Composable
    fun getTertiary(): Color = if (isDarkTheme()) PremiumColorsDark.tertiary else PremiumColorsLight.tertiary
    
    @Composable
    fun getOnTertiary(): Color = if (isDarkTheme()) PremiumColorsDark.onTertiary else PremiumColorsLight.onTertiary
    
    @Composable
    fun getError(): Color = if (isDarkTheme()) PremiumColorsDark.error else PremiumColorsLight.error
    
    @Composable
    fun getOnError(): Color = if (isDarkTheme()) PremiumColorsDark.onError else PremiumColorsLight.onError
    
    @Composable
    fun getOutline(): Color = if (isDarkTheme()) PremiumColorsDark.outline else PremiumColorsLight.outline
    
    @Composable
    fun getGray(): Color = if (isDarkTheme()) PremiumColorsDark.gray else PremiumColorsLight.gray
    
    @Composable
    fun getGrayLight(): Color = if (isDarkTheme()) PremiumColorsDark.grayLight else PremiumColorsLight.grayLight
    
    @Composable
    fun getGrayDark(): Color = if (isDarkTheme()) PremiumColorsDark.grayDark else PremiumColorsLight.grayDark
    
    @Composable
    fun getBackground(): Color = if (isDarkTheme()) PremiumColorsDark.background else PremiumColorsLight.background
    
    @Composable
    fun getOnBackground(): Color = if (isDarkTheme()) PremiumColorsDark.onBackground else PremiumColorsLight.onBackground
    
    @Composable
    fun getSurface(): Color = if (isDarkTheme()) PremiumColorsDark.surface else PremiumColorsLight.surface
    
    @Composable
    fun getOnSurface(): Color = if (isDarkTheme()) PremiumColorsDark.onSurface else PremiumColorsLight.onSurface
    
    @Composable
    fun getSurfaceVariant(): Color = if (isDarkTheme()) PremiumColorsDark.surfaceVariant else PremiumColorsLight.surfaceVariant
    
    @Composable
    fun getOnSurfaceVariant(): Color = if (isDarkTheme()) PremiumColorsDark.onSurfaceVariant else PremiumColorsLight.onSurfaceVariant
    
    @Composable
    fun getAccent(): Color = if (isDarkTheme()) PremiumColorsDark.accent else PremiumColorsLight.accent
    
    @Composable
    fun getSafe(): Color = if (isDarkTheme()) PremiumColorsDark.safe else PremiumColorsLight.safe
    
    @Composable
    fun getWarning(): Color = if (isDarkTheme()) PremiumColorsDark.warning else PremiumColorsLight.warning
    
    @Composable
    fun getUnsafe(): Color = if (isDarkTheme()) PremiumColorsDark.unsafe else PremiumColorsLight.unsafe
    
    @Composable
    fun getInfo(): Color = if (isDarkTheme()) PremiumColorsDark.info else PremiumColorsLight.info
    
    @Composable
    private fun isDarkTheme(): Boolean {
        return isSystemInDarkTheme()
    }
}

typealias ThemeColors = PremiumColorsLight
typealias ThemeColorsLight = PremiumColorsLight
typealias ThemeColorsDark = PremiumColorsDark