package com.civileg.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// ===== Premium Geometric Typography System =====
// NOTE: The previous revision of this file referenced non-existent APIs
// (FontFamily.Inter, androidx.compose.ui.text.style.LineHeight.Exactly*,
// Float letterSpacing, FontFamily(FontFamily.Serif, ...)) and never compiled.
// Inter is not bundled with the app, so the geometric sans-serif roles are
// mapped onto the system SansSerif family. Sizes, weights, colors and spacing
// follow the intended Premium Geometric design.

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.02.sp,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = TextAlign.Start
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.015.sp,
        color = androidx.compose.ui.graphics.Color(0xFF1E293B),
        textAlign = TextAlign.Start
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.01.sp,
        color = androidx.compose.ui.graphics.Color(0xFF4A5C7E),
        textAlign = TextAlign.Start
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.01.sp,
        color = androidx.compose.ui.graphics.Color(0xFF003A70),
        textAlign = TextAlign.Start
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.01.sp,
        color = androidx.compose.ui.graphics.Color(0xFF4A5C7E),
        textAlign = TextAlign.Start
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.sp,
        color = androidx.compose.ui.graphics.Color(0xFFC68400),
        textAlign = TextAlign.Start
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.02.sp,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = TextAlign.Start
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.02.sp,
        color = androidx.compose.ui.graphics.Color(0xFF475569),
        textAlign = TextAlign.Start
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.02.sp,
        color = androidx.compose.ui.graphics.Color(0xFF64748B),
        textAlign = TextAlign.Start
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.sp,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = TextAlign.Start
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.sp,
        color = androidx.compose.ui.graphics.Color(0xFF475569),
        textAlign = TextAlign.Start
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.sp,
        color = androidx.compose.ui.graphics.Color(0xFF64748B),
        textAlign = TextAlign.Start
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.02.sp,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = TextAlign.Start
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.sp,
        color = androidx.compose.ui.graphics.Color(0xFF475569),
        textAlign = TextAlign.Start
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.03.sp,
        color = androidx.compose.ui.graphics.Color(0xFF64748B),
        textAlign = TextAlign.Start
    )
)