package com.civileg.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.civileg.app.ui.responsive.AppResponsiveProvider

// ============================================================================
// PREMIUM GEOMETRIC COLOR SCHEMES
// ============================================================================

// Premium Light Color Scheme - Geometric Harmony
private val PremiumLightColorScheme = lightColorScheme(
    primary = PremiumColorsLight.primary,
    primaryContainer = PremiumColorsLight.primaryContainer,
    secondary = PremiumColorsLight.secondary,
    secondaryContainer = PremiumColorsLight.secondaryContainer,
    background = PremiumColorsLight.background,
    surface = PremiumColorsLight.surface,
    error = PremiumColorsLight.error,
    onPrimary = PremiumColorsLight.onPrimary,
    onSecondary = PremiumColorsLight.onSecondary,
    onBackground = PremiumColorsLight.textOnAccent,
    onSurface = PremiumColorsLight.onSurface,
    onError = PremiumColorsLight.onError,
    surfaceVariant = PremiumColorsLight.surfaceVariant,
    onSurfaceVariant = PremiumColorsLight.onSurfaceVariant
)

// Premium Dark Color Scheme - Geometric Harmony
private val PremiumDarkColorScheme = darkColorScheme(
    primary = PremiumColorsDark.primary,
    primaryContainer = PremiumColorsDark.primaryContainer,
    secondary = PremiumColorsDark.secondary,
    secondaryContainer = PremiumColorsDark.secondaryContainer,
    background = PremiumColorsDark.background,
    surface = PremiumColorsDark.surface,
    error = PremiumColorsDark.error,
    onPrimary = PremiumColorsDark.onPrimary,
    onSecondary = PremiumColorsDark.onSecondary,
    onBackground = PremiumColorsDark.textOnAccent,
    onSurface = PremiumColorsDark.onSurface,
    onError = PremiumColorsDark.onError,
    surfaceVariant = PremiumColorsDark.surfaceVariant,
    onSurfaceVariant = PremiumColorsDark.onSurfaceVariant
)

// ============================================================================
// PREMIUM GEOMETRIC THEME COMPOSABLE
// ============================================================================

@Composable
fun CivilEngineerPremiumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) PremiumDarkColorScheme else PremiumLightColorScheme
        }
        darkTheme -> PremiumDarkColorScheme
        else -> PremiumLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color to primary for premium feel
            window.statusBarColor = colorScheme.primary.toArgb()
            // Make status bar icons light on dark, dark on light
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    // Premium responsive geometry provider
    AppResponsiveProvider {
        MaterialTheme(
            colorScheme = colorScheme,
            // Use premium geometric typography
            typography = PremiumTypography.Typography,
            content = content
        )
    }
}

// ============================================================================
// PREMIUM GEOMETRIC TYPOGRAPHY OBJECT
// ============================================================================

object PremiumTypography {
    val Typography = Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.02.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.015.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        displaySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.01.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.01.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.01.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.01.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.02.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.02.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.02.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.01.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.01.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.01.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.02.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.02.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.03.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
    )
    
    // Arabic typography support
    @Composable
    fun arabicDisplayLarge(
        text: String,
        fontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
        color: Color = PremiumColorsLight.onBackground,
        modifier: Modifier = Modifier
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            lineHeight = 40.sp,
            letterSpacing = 0.01.sp,
            color = color,
            modifier = modifier,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
    
    @Composable
    fun arabicBodyLarge(
        text: String,
        fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
        color: Color = PremiumColorsLight.onSurface,
        modifier: Modifier = Modifier
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            lineHeight = 24.sp,
            letterSpacing = 0.02.sp,
            color = color,
            modifier = modifier,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

// ============================================================================
// PREMIUM GEOMETRIC COLOR ACCESSORS
// ============================================================================

// Convenience properties for easy access in Compose
val Corners = object {
    val sm = 8.dp
    val md = 12.dp
    val lg = 20.dp
    val xl = 24.dp
    val pill = 50.dp
    val circle = 50.dp
}

val Radii = object {
    val cardSm = 8.dp
    val card = 12.dp
    val cardMd = 16.dp
    val cardLg = 20.dp
    val cardXl = 24.dp
    val pill = 50.dp
    val circle = 50.dp
}

val Spacing = object {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
}

val Elevation = object {
    val sm = 1.dp
    val md = 4.dp
    val lg = 8.dp
    val xl = 16.dp
}

val IconSizes = object {
    val sm = 20.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 40.dp
}

@Composable
fun CivilEngineerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    CivilEngineerPremiumTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}