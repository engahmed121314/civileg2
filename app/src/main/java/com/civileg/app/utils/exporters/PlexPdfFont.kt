package com.civileg.app.utils.exporters

import android.util.Log
import com.civileg.app.CivilEGApplication
import com.civileg.app.R
import com.itextpdf.io.font.FontProgram
import com.itextpdf.io.font.FontProgramFactory
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory

/**
 * IBM Plex Sans Arabic (the app UI family, res/font) for the English PDF reports.
 * The parsed FontProgram is cached; a FRESH PdfFont is created per call because iText binds a PdfFont
 * to the first PdfDocument that uses it. Falls back to Helvetica if the font cannot be loaded.
 */
object PlexPdfFont {
    private const val TAG = "PlexPdfFont"
    @Volatile private var regular: FontProgram? = null
    @Volatile private var bold: FontProgram? = null

    @Synchronized
    @android.annotation.SuppressLint("ResourceType") // res/font files are read as raw bytes for iText
    private fun program(isBold: Boolean): FontProgram {
        (if (isBold) bold else regular)?.let { return it }
        val res = if (isBold) R.font.ibm_plex_sans_arabic_bold else R.font.ibm_plex_sans_arabic_regular
        val bytes = CivilEGApplication.instance.resources.openRawResource(res).use { it.readBytes() }
        val p = FontProgramFactory.createFont(bytes)
        if (isBold) bold = p else regular = p
        return p
    }

    fun create(isBold: Boolean = false): PdfFont = try {
        PdfFontFactory.createFont(program(isBold), PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED)
    } catch (e: Throwable) {
        Log.e(TAG, "IBM Plex PDF font unavailable, using Helvetica: ${e.message}")
        PdfFontFactory.createFont(if (isBold) StandardFonts.HELVETICA_BOLD else StandardFonts.HELVETICA)
    }
}
