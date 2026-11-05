package com.civileg.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeight
import androidx.compose.ui.unit.sp

// ===== Premium Geometric Typography System =====

// Font family: Inter - geometric sans-serif with Arabic support
val Inter = FontFamily(
    FontFamily.Serif,  // Fallback; actual Inter loaded via @font/inter_*
    FontFamily.Serif,
    FontFamily.Serif,
    FontFamily.Serif,
    FontFamily.Serif,
    FontFamily.Serif,
    FontFamily.Serif,
    FontFamily.Serif,
    FontFamily.Serif,
    FontFamily.Serif
)

// Typography configurations for Civil EG Premium Geometric Design
val Typography = Typography(
    // Display Large - Main headlines, section titles
    displayLarge = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = LineHeight.Exactly40,
        letterSpacing = 0.02f,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Display Medium - Section subtitles, important labels
    displayMedium = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = LineHeight.Exactly36,
        letterSpacing = 0.015f,
        color = androidx.compose.ui.graphics.Color(0xFF1E293B),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Display Small - Section headers, module titles
    displaySmall = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = LineHeight.Exactly32,
        letterSpacing = 0.01f,
        color = androidx.compose.ui.graphics.Color(0xFF4A5C7E),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Headline Large - Primary action titles
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = LineHeight.Exactly32,
        letterSpacing = 0.01f,
        color = androidx.compose.ui.graphics.Color(0xFF003A70),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Headline Medium - Secondary action titles
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = LineHeight.Exactly28,
        letterSpacing = 0.01f,
        color = androidx.compose.ui.graphics.Color(0xFF4A5C7E),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Headline Small - Tertiary action titles
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = LineHeight.Exactly24,
        letterSpacing = 0.01f,
        color = androidx.compose.ui.graphics.Color(0xFFC68400),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Title Large - Card headers, screen titles
    titleLarge = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = LineHeight.Exactly28,
        letterSpacing = 0.02f,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Title Medium - Input labels, section descriptions
    titleMedium = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = LineHeight.Exactly24,
        letterSpacing = 0.02f,
        color = androidx.compose.ui.graphics.Color(0xFF475569),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Title Small - Muted text, secondary labels
    titleSmall = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = LineHeight.Exactly20,
        letterSpacing = 0.02f,
        color = androidx.compose.ui.graphics.Color(0xFF64748B),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Body Large - Primary body text
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = LineHeight.Exactly24,
        letterSpacing = 0.01f,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Body Medium - Standard body text
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = LineHeight.Exactly20,
        letterSpacing = 0.01f,
        color = androidx.compose.ui.graphics.Color(0xFF475569),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Body Small - Captions, secondary text
    bodySmall = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = LineHeight.Exactly16,
        letterSpacing = 0.01f,
        color = androidx.compose.ui.graphics.Color(0xFF64748B),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Label Large - Form labels, button text
    labelLarge = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = LineHeight.Exactly20,
        letterSpacing = 0.02f,
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Label Medium - Secondary labels
    labelMedium = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = LineHeight.Exactly16,
        letterSpacing = 0.02f,
        color = androidx.compose.ui.graphics.Color(0xFF475569),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    ),
    
    // Label Small - Small annotations
    labelSmall = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = LineHeight.Exactly16,
        letterSpacing = 0.03f,
        color = androidx.compose.ui.graphics.Color(0xFF64748B),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    )
)

// Premium Arabic Typography Support
object ArabicTypography {
    // Arabic-optimized typography with proper Qur'anic metric
    val arabicDisplayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = LineHeight.Exactly40,
        letterSpacing = 0.01f,
        textAlign = androidx.compose.ui.text.style.TextAlign.End
    )
    
    val arabicBodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = LineHeight.Exactly24,
        letterSpacing = 0.02f,
        textAlign = androidx.compose.ui.text.style.TextAlign.End
    )
    
    val arabicLabelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = LineHeight.Exactly20,
        letterSpacing = 0.03f,
        textAlign = androidx.compose.ui.text.style.TextAlign.End
    )
}

// English Typography optimized
object EnglishTypography {
    val englishDisplayLarge = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = LineHeight.Exactly40,
        letterSpacing = -0.01f,  // Slight negative for English
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    )
    
    val englishBodyLarge = TextStyle(
        fontFamily = FontFamily.Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = LineHeight.Exactly24,
        letterSpacing = 0.01f,
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    )
}