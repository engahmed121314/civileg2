package com.civileg.app.utils

import android.content.Context
import android.util.Log
import com.itextpdf.io.font.FontProgram
import com.itextpdf.io.font.FontProgramFactory
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory

/**
 * Embedded Unicode (Latin+Greek+math) font for English PDF reports.
 *
 * The PDF base-14 Helvetica font is WinAnsi-only, so Greek letters (rho, delta),
 * math signs (sqrt, <=), and technical symbols (diameter sign) silently dropped
 * out of calculation steps. DejaVu Sans covers all of them and is embedded with
 * IDENTITY_H so extraction keeps the real codepoints.
 *
 * Mirrors [ArabicFontProvider]: the parsed FontProgram is cached once (expensive),
 * while every document gets a FRESH PdfFont (an iText PdfFont binds to the first
 * PdfDocument that uses it and must never be reused across documents).
 *
 * Call [init] once from Application.onCreate (and in tests). Falls back to
 * base-14 Helvetica when not initialized, preserving the previous behavior.
 */
object EnglishFontProvider {

    private const val TAG = "EnglishFontProvider"
    private const val REGULAR = "fonts/DejaVuSans.ttf"
    private const val BOLD = "fonts/DejaVuSans-Bold.ttf"

    @Volatile private var regularProgram: FontProgram? = null
    @Volatile private var boldProgram: FontProgram? = null

    @Synchronized
    fun init(context: Context) {
        if (regularProgram == null) {
            regularProgram = loadProgram(context, REGULAR)
        }
        if (boldProgram == null) {
            boldProgram = loadProgram(context, BOLD)
        }
    }

    val isReady: Boolean get() = regularProgram != null

    private fun loadProgram(context: Context, assetPath: String): FontProgram? {
        return try {
            context.assets.open(assetPath).use { input ->
                FontProgramFactory.createFont(input.readBytes(), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $assetPath: ${e.message}")
            null
        }
    }

    /** Fresh PdfFont per call. Never cache the returned font across PdfDocuments. */
    fun getFont(bold: Boolean = false): PdfFont {
        val program = (if (bold) boldProgram else regularProgram) ?: regularProgram
        if (program != null) {
            return try {
                PdfFontFactory.createFont(
                    program,
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                )
            } catch (e: Exception) {
                Log.e(TAG, "Embedded font creation failed: ${e.message}")
                baseFont(bold)
            }
        }
        return baseFont(bold)
    }

    private fun baseFont(bold: Boolean): PdfFont {
        return try {
            if (bold) PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
            else PdfFontFactory.createFont(StandardFonts.HELVETICA)
        } catch (e: Exception) {
            throw RuntimeException("No font available")
        }
    }
}
