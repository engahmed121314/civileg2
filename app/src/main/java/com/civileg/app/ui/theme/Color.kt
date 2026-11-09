package com.civileg.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ===== Elite Cyber Industrial Titanium Color System =====

// Light Theme Colors - Modern Tech & Engineering Palette
object PremiumColorsLight {
    val primary = Color(0xFF0B192C)
    val primaryContainer = Color(0xFF1E3E62)
    val onPrimary = Color(0xFFFFFFFF)
    val onPrimaryContainer = Color(0xFFE0F2FE)
    
    val secondary = Color(0xFF0077B6)
    val secondaryContainer = Color(0xFFCAF0F8)
    val onSecondary = Color(0xFFFFFFFF)
    val onSecondaryContainer = Color(0xFF03045E)
    
    val tertiary = Color(0xFFD4A373)
    val tertiaryContainer = Color(0xFFFAEDCD)
    val onTertiary = Color(0xFF281C08)
    val onTertiaryContainer = Color(0xFFFEFAE0)
    
    val error = Color(0xFFDC2626)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFEE2E2)
    val onErrorContainer = Color(0xFF991B1B)
    
    val outline = Color(0xFF64748B)
    val outlineVariant = Color(0xFF94A3B8)
    val gray = Color(0xFF6B7280)
    val grayLight = Color(0xFFF1F5F9)
    val grayDark = Color(0xFF334155)
    
    val background = Color(0xFFF4F6F9)
    val onBackground = Color(0xFF0B192C)
    val surface = Color(0xFFFFFFFF)
    val onSurface = Color(0xFF0B192C)
    val surfaceVariant = Color(0xFFE2E8F0)
    val onSurfaceVariant = Color(0xFF334155)
    
    val accent = Color(0xFF00A8E8)
    val accentGradientStart = Color(0xFF00A8E8)
    val accentGradientEnd = Color(0xFF0096C7)
    val success = Color(0xFF059669)
    val successGradientStart = Color(0xFF059669)
    val successGradientEnd = Color(0xFF10B981)
    val danger = Color(0xFFDC2626)
    val dangerGradientStart = Color(0xFFDC2626)
    val dangerGradientEnd = Color(0xFFEF4444)
    
    val primaryGradient: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF0B192C), Color(0xFF1E3E62))
    )

    val gradientNavyToBlue: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF0B192C), Color(0xFF0077B6))
    )
    
    val gradientBlueToPurple: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF0077B6), Color(0xFF00A8E8))
    )
    
    val gradientPurpleToAmber: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF00A8E8), Color(0xFFD4A373))
    )
    
    val gradientAmberToOrange: Brush = Brush.horizontalGradient(
        listOf(Color(0xFFD4A373), Color(0xFFF59E0B))
    )
    
    val gradientSuccessToGreen: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF059669), Color(0xFF10B981))
    )
    
    val gradientDangerToRed: Brush = Brush.horizontalGradient(
        listOf(Color(0xFFDC2626), Color(0xFFEF4444))
    )
    
    val surfaceElevated = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF8FAFC)
    val surfaceContainerHigh = Color(0xFFE2E8F0)
    
    val textPrimary = Color(0xFF0B192C)
    val textSecondary = Color(0xFF334155)
    val textMuted = Color(0xFF64748B)
    val textInverse = Color(0xFFFFFFFF)
    val textOnAccent = Color(0xFFFFFFFF)

    val safe = Color(0xFF059669)
    val safeBright = Color(0xFF10B981)
    val warning = Color(0xFFD97706)
    val warningBright = Color(0xFFF59E0B)
    val unsafe = Color(0xFFDC2626)
    val unsafeBright = Color(0xFFEF4444)
    val info = Color(0xFF0077B6)

    val navyDeep = Color(0xFF0B192C)
    val navyMedium = Color(0xFF1E3E62)
    val navyLight = Color(0xFFE2E8F0)

    fun safeColor(): Color = safe
    fun unsafeColor(): Color = unsafe
    fun chartColors(): List<Color> = listOf(primary, secondary, tertiary, accent, safe, warning)
    fun concreteColor(): Color = primaryContainer
    fun steelColor(): Color = primary
    fun dimensionColor(): Color = outline
}

// Dark Theme Colors - Cyber Obsidian Palette
object PremiumColorsDark {
    val primary = Color(0xFF161A30)
    val primaryContainer = Color(0xFF31304D)
    val onPrimary = Color(0xFFF8FAFC)
    val onPrimaryContainer = Color(0xFFE2E8F0)
    
    val secondary = Color(0xFF00B4D8)
    val secondaryContainer = Color(0xFF1E293B)
    val onSecondary = Color(0xFF0B0F19)
    val onSecondaryContainer = Color(0xFFCAF0F8)
    
    val tertiary = Color(0xFFFACC15)
    val tertiaryContainer = Color(0xFF422006)
    val onTertiary = Color(0xFF422006)
    val onTertiaryContainer = Color(0xFFFEF08A)
    
    val error = Color(0xFFEF4444)
    val onError = Color(0xFF0B0F19)
    val errorContainer = Color(0xFF7F1D1D)
    val onErrorContainer = Color(0xFFFEE2E2)
    
    val outline = Color(0xFF94A3B8)
    val outlineVariant = Color(0xFF64748B)
    val gray = Color(0xFF9CA3AF)
    val grayLight = Color(0xFF1E293B)
    val grayDark = Color(0xFF0B0F19)
    
    val background = Color(0xFF0B0F19)
    val onBackground = Color(0xFFF8FAFC)
    val surface = Color(0xFF131B2E)
    val onSurface = Color(0xFFF8FAFC)
    val surfaceVariant = Color(0xFF1E293B)
    val onSurfaceVariant = Color(0xFF94A3B8)
    
    val accent = Color(0xFF00E5FF)
    val accentGradientStart = Color(0xFF00E5FF)
    val accentGradientEnd = Color(0xFF00B4D8)
    val success = Color(0xFF10B981)
    val successGradientStart = Color(0xFF10B981)
    val successGradientEnd = Color(0xFF059669)
    val danger = Color(0xFFEF4444)
    val dangerGradientStart = Color(0xFFEF4444)
    val dangerGradientEnd = Color(0xFFDC2626)
    
    val primaryGradient: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF161A30), Color(0xFF31304D))
    )

    val gradientNavyToBlueDark: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF161A30), Color(0xFF00B4D8))
    )
    
    val gradientBlueToPurpleDark: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF00B4D8), Color(0xFF00E5FF))
    )
    
    val surfaceElevated = Color(0xFF161A30)
    val surfaceContainerLow = Color(0xFF131B2E)
    val surfaceContainerHigh = Color(0xFF1E293B)
    
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF94A3B8)
    val textMuted = Color(0xFF64748B)
    val textInverse = Color(0xFF0B0F19)
    val textOnAccent = Color(0xFF0B0F19)
    
    val safe = Color(0xFF10B981)
    val safeBright = Color(0xFF34D399)
    val warning = Color(0xFFF59E0B)
    val warningBright = Color(0xFFFBBF24)
    val unsafe = Color(0xFFEF4444)
    val unsafeBright = Color(0xFFF87171)
    val info = Color(0xFF00B4D8)

    val navyDeep = Color(0xFF0B0F19)
    val navyMedium = Color(0xFF161A30)
    val navyLight = Color(0xFF1E293B)

    fun safeColor(): Color = safe
    fun unsafeColor(): Color = unsafe
    fun chartColors(): List<Color> = listOf(primary, secondary, tertiary, accent, safe, warning)
    fun concreteColor(): Color = primaryContainer
    fun steelColor(): Color = primary
    fun dimensionColor(): Color = outline
}

// ThemeColors compatibility object
object ThemeColors {
    @Composable fun safeColor(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.safe else PremiumColorsLight.safe
    @Composable fun unsafeColor(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.unsafe else PremiumColorsLight.unsafe
    @Composable fun chartColors(): List<Color> = if (isSystemInDarkTheme()) PremiumColorsDark.chartColors() else PremiumColorsLight.chartColors()
    @Composable fun concreteColor(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.concreteColor() else PremiumColorsLight.concreteColor()
    @Composable fun steelColor(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.steelColor() else PremiumColorsLight.steelColor()
    @Composable fun dimensionColor(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.dimensionColor() else PremiumColorsLight.dimensionColor()
}

object PremiumColorScheme {
    @Composable
    fun getPrimary(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.primary else PremiumColorsLight.primary
    
    @Composable
    fun getOnPrimary(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.onPrimary else PremiumColorsLight.onPrimary
    
    @Composable
    fun getSecondary(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.secondary else PremiumColorsLight.secondary
    
    @Composable
    fun getOnSecondary(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.onSecondary else PremiumColorsLight.onSecondary
    
    @Composable
    fun getTertiary(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.tertiary else PremiumColorsLight.tertiary
    
    @Composable
    fun getOnTertiary(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.onTertiary else PremiumColorsLight.onTertiary
    
    @Composable
    fun getError(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.error else PremiumColorsLight.error
    
    @Composable
    fun getOnError(): Color = if (isSystemInDarkTheme()) PremiumColorsDark.onError else PremiumColorsLight.onError
}
