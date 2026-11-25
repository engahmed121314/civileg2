package com.civileg.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.civileg.app.R

/** Bundled, unchanged IBM Plex Sans Arabic. License ships in assets/licenses. */
val ArabicUiFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold)
)

private fun TextStyle.arabicUiStyle() = copy(
    fontFamily = ArabicUiFontFamily,
    fontWeight = fontWeight?.let { if (it.weight > 700) FontWeight.Bold else it },
    fontSynthesis = FontSynthesis.None,
    letterSpacing = 0.sp
)

/** English typography is returned unchanged, including explicit mathematical text styles. */
fun typographyForUiLanguage(base: Typography, language: String): Typography {
    if (language.substringBefore('-').substringBefore('_').lowercase() != "ar") return base
    return base.copy(
        displayLarge = base.displayLarge.arabicUiStyle(),
        displayMedium = base.displayMedium.arabicUiStyle(),
        displaySmall = base.displaySmall.arabicUiStyle(),
        headlineLarge = base.headlineLarge.arabicUiStyle(),
        headlineMedium = base.headlineMedium.arabicUiStyle(),
        headlineSmall = base.headlineSmall.arabicUiStyle(),
        titleLarge = base.titleLarge.arabicUiStyle(),
        titleMedium = base.titleMedium.arabicUiStyle(),
        titleSmall = base.titleSmall.arabicUiStyle(),
        bodyLarge = base.bodyLarge.arabicUiStyle(),
        bodyMedium = base.bodyMedium.arabicUiStyle(),
        bodySmall = base.bodySmall.arabicUiStyle(),
        labelLarge = base.labelLarge.arabicUiStyle(),
        labelMedium = base.labelMedium.arabicUiStyle(),
        labelSmall = base.labelSmall.arabicUiStyle()
    )
}

private fun isArabicCodePoint(codePoint: Int) =
    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC

/**
 * Bind only Arabic runs, even in mixed or explicitly monospaced AppText.
 * Latin units, mathematical symbols and the original string/ordering are untouched.
 * Android's native text engine performs joining and bidi, not manual glyph substitution.
 */
fun arabicUiText(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        if (!isArabicCodePoint(codePoint)) {
            index += Character.charCount(codePoint)
            continue
        }
        val start = index
        index += Character.charCount(codePoint)
        while (index < text.length) {
            val next = Character.codePointAt(text, index)
            val category = Character.getType(next)
            if (!isArabicCodePoint(next) && next != 0x200C && next != 0x200D &&
                category != Character.NON_SPACING_MARK.toInt() &&
                category != Character.COMBINING_SPACING_MARK.toInt()) break
            index += Character.charCount(next)
        }
        addStyle(SpanStyle(fontFamily = ArabicUiFontFamily,
            fontSynthesis = FontSynthesis.None, letterSpacing = 0.sp), start, index)
    }
}
