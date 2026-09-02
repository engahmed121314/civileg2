package com.civileg.app.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Composable
fun engineeringColors(): EngineeringColorScheme = LocalEngineeringColors.current

@Immutable
data class EngineeringColorScheme(
    val safe: Color,
    val safeContainer: Color,
    val onSafe: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val fail: Color,
    val failContainer: Color,
    val onFail: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfo: Color,
    val notChecked: Color,
    val notCheckedContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val gridLine: Color,
    val blueprintGrid: Color,
    val concrete: Color,
    val rebar: Color,
    val steel: Color,
    val dimensionLine: Color,
    val chartSeries: List<Color>,
    val utilizationGradient: List<Color>
)

val LightEngineeringColors = EngineeringColorScheme(
    safe = Color(0xFF2E7D32),
    safeContainer = Color(0xFFDCEDC8),
    onSafe = Color.White,
    warning = Color(0xFFB26A00),
    warningContainer = Color(0xFFFFECB3),
    onWarning = Color.White,
    fail = Color(0xFFC62828),
    failContainer = Color(0xFFFFCDD2),
    onFail = Color.White,
    info = Color(0xFF1565C0),
    infoContainer = Color(0xFFBBDEFB),
    onInfo = Color.White,
    notChecked = Color(0xFF616161),
    notCheckedContainer = Color(0xFFEEEEEE),
    neutral = Color(0xFF455A64),
    neutralContainer = Color(0xFFECEFF1),
    gridLine = Color(0x1A000000),
    blueprintGrid = Color(0x141976D2),
    concrete = Color(0xFF9E9E9E),
    rebar = Color(0xFF37474F),
    steel = Color(0xFF5D4037),
    dimensionLine = Color(0xFF1976D2),
    chartSeries = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
        Color(0xFF7B1FA2), Color(0xFFC62828), Color(0xFF00838F)
    ),
    utilizationGradient = listOf(
        Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFEF6C00), Color(0xFFC62828)
    )
)

val DarkEngineeringColors = EngineeringColorScheme(
    safe = Color(0xFF81C784),
    safeContainer = Color(0xFF1B3A20),
    onSafe = Color(0xFF0B1F0D),
    warning = Color(0xFFFFD54F),
    warningContainer = Color(0xFF3D2E00),
    onWarning = Color(0xFF241C00),
    fail = Color(0xFFEF5350),
    failContainer = Color(0xFF42191B),
    onFail = Color(0xFF26090A),
    info = Color(0xFF64B5F6),
    infoContainer = Color(0xFF0D3050),
    onInfo = Color(0xFF06182A),
    notChecked = Color(0xFF9E9E9E),
    notCheckedContainer = Color(0xFF262626),
    neutral = Color(0xFF90A4AE),
    neutralContainer = Color(0xFF22292D),
    gridLine = Color(0x22FFFFFF),
    blueprintGrid = Color(0x2264B5F6),
    concrete = Color(0xFFBDBDBD),
    rebar = Color(0xFFCFD8DC),
    steel = Color(0xFF8D6E63),
    dimensionLine = Color(0xFF64B5F6),
    chartSeries = listOf(
        Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
        Color(0xFFBA68C8), Color(0xFFEF5350), Color(0xFF4DD0E1)
    ),
    utilizationGradient = listOf(
        Color(0xFF81C784), Color(0xFFFFD54F), Color(0xFFFF8A50), Color(0xFFEF5350)
    )
)

val LocalEngineeringColors = staticCompositionLocalOf { LightEngineeringColors }
