package com.civileg.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test

class ArabicUiTypographyTest {
    @Test fun `English typography remains the identical original object`() {
        val base = Typography()
        assertSame(base, typographyForUiLanguage(base, "en"))
        assertSame(base, typographyForUiLanguage(base, "en-GB"))
    }

    @Test fun `Arabic regional locales use all fifteen bundled roles without changing sizes`() {
        val base = Typography()
        for (language in listOf("ar", "ar-EG", "ar_SA")) {
            val changed = typographyForUiLanguage(base, language)
            val before = listOf(base.displayLarge, base.displayMedium, base.displaySmall,
                base.headlineLarge, base.headlineMedium, base.headlineSmall,
                base.titleLarge, base.titleMedium, base.titleSmall,
                base.bodyLarge, base.bodyMedium, base.bodySmall,
                base.labelLarge, base.labelMedium, base.labelSmall)
            val after = listOf(changed.displayLarge, changed.displayMedium, changed.displaySmall,
                changed.headlineLarge, changed.headlineMedium, changed.headlineSmall,
                changed.titleLarge, changed.titleMedium, changed.titleSmall,
                changed.bodyLarge, changed.bodyMedium, changed.bodySmall,
                changed.labelLarge, changed.labelMedium, changed.labelSmall)
            before.zip(after).forEach { (old, new) ->
                assertEquals(ArabicUiFontFamily, new.fontFamily)
                assertEquals(old.fontSize, new.fontSize)
                assertEquals(old.lineHeight, new.lineHeight)
                assertEquals(0.sp, new.letterSpacing)
            }
        }
    }

    @Test fun `mixed Arabic spans keep exact Unicode and exclude Latin units and math`() {
        val raw = "التسليح: 9Ø20 | As = 2827.43 mm² | σ ≤ 420 MPa | ١٢٣"
        val annotated = arabicUiText(raw)
        assertEquals(raw, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
        annotated.spanStyles.forEach { span ->
            assertEquals(ArabicUiFontFamily, span.item.fontFamily)
            assertFalse(annotated.text.substring(span.start, span.end).any { it in 'A'..'Z' || it in 'a'..'z' || it == 'σ' || it == '≤' })
        }
        for (index in raw.indices.filter { raw[it] in 'A'..'Z' || raw[it] in 'a'..'z' || raw[it] == 'σ' || raw[it] == '≤' }) {
            assertFalse(annotated.spanStyles.any { index >= it.start && index < it.end })
        }
    }

    @Test fun `Latin-only mathematical and empty content get no font override`() {
        for (raw in listOf("", "As = 2827.43 mm², φ = 0.9, 9Ø20", "0123456789")) {
            val annotated = arabicUiText(raw)
            assertEquals(raw, annotated.text)
            assertTrue(annotated.spanStyles.isEmpty())
        }
    }

    @Test fun `joining marks and surrogate pairs remain unmodified`() {
        val raw = "🧱 فَحْصُ التَّسْلِيحِ، ل\u200Dا ١٢٣"
        val annotated = arabicUiText(raw)
        assertEquals(raw, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
        assertTrue(annotated.spanStyles.all { it.start >= 3 })
    }
}
