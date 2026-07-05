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
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    primaryContainer = PrimaryVariantLight,
    secondary = SecondaryLight,
    secondaryContainer = SecondaryVariantLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onError = Color.White,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = TextSecondaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    primaryContainer = PrimaryVariantDark,
    secondary = SecondaryDark,
    secondaryContainer = SecondaryVariantDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onError = Color.Black,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = TextSecondaryDark
)

@Composable
fun CivilEngineerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// دالة مساعدة للحصول على الألوان حسب الثيم
object ThemeColors {
    @Composable
    fun concreteColor(): Color = 
        if (isDarkTheme()) ConcreteDark else ConcreteLight
    
    @Composable
    fun steelColor(): Color = 
        if (isDarkTheme()) SteelDark else SteelLight
    
    @Composable
    fun dimensionColor(): Color = 
        if (isDarkTheme()) DimensionDark else DimensionLight
    
    @Composable
    fun safeColor(): Color = 
        if (isDarkTheme()) SafeDark else SafeLight
    
    @Composable
    fun warningColor(): Color = 
        if (isDarkTheme()) WarningDark else WarningLight
    
    @Composable
    fun unsafeColor(): Color = 
        if (isDarkTheme()) UnsafeDark else UnsafeLight
    
    @Composable
    fun chartColors(): List<Color> = 
        if (isDarkTheme()) ChartColorsDark else ChartColorsLight
    
    @Composable
    private fun isDarkTheme(): Boolean {
        return isSystemInDarkTheme()
    }
}
