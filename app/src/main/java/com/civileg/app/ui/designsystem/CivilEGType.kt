package com.civileg.app.ui.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class EngineeringType(
    val valueLarge: TextStyle,
    val valueMedium: TextStyle,
    val valueSmall: TextStyle,
    val unit: TextStyle,
    val codeRef: TextStyle,
    val formula: TextStyle,
    val tableHeader: TextStyle,
    val tableCell: TextStyle,
    val statusLabel: TextStyle,
    val metricLabel: TextStyle,
    val sectionTitle: TextStyle,
    val breadcrumb: TextStyle
)

private val MONO_FONT_WEIGHT = FontWeight.Medium

val LightEngineeringType = EngineeringType(
    valueLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    valueMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = MONO_FONT_WEIGHT, fontSize = 18.sp, lineHeight = 24.sp),
    valueSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
    unit = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    codeRef = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    formula = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    tableHeader = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    tableCell = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    statusLabel = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    metricLabel = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    sectionTitle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.2.sp),
    breadcrumb = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

val DarkEngineeringType = LightEngineeringType

val LocalEngineeringType = staticCompositionLocalOf { LightEngineeringType }

@Composable
fun engineeringType(): EngineeringType = LocalEngineeringType.current
