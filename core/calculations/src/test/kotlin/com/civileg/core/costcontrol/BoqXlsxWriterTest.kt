package com.civileg.core.costcontrol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoqXlsxWriterTest {
    private fun parts(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; result[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return result
    }
    private fun sheet(text: String) = BoqWorkbookSheet("مقايسة", listOf(
        BoqWorkbookRow(listOf(BoqWorkbookCell.Text("بند"), BoqWorkbookCell.Text("بيان الأعمال")), true),
        BoqWorkbookRow(listOf(BoqWorkbookCell.Text(text), BoqWorkbookCell.Number(BigDecimal("0.03"))))))
    private fun refused(block: () -> Unit) { try { block(); throw AssertionError("Invalid workbook accepted") } catch (_: IllegalArgumentException) { } }

    @Test fun validPackageTypedTextUnicodeAndNumericWithoutFormulas() {
        val output = ByteArrayOutputStream()
        BoqXlsxWriter.write(listOf(sheet("001234567890123456789 & <عربي> =SUM(A1)")), output)
        val entries = parts(output.toByteArray())
        assertEquals(6, entries.size)
        assertTrue(entries.containsKey("[Content_Types].xml"))
        entries.values.forEach { xml -> DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray())) }
        val xml = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(xml.contains("t=\"inlineStr\"")); assertTrue(xml.contains("001234567890123456789 &amp; &lt;عربي&gt; =SUM(A1)"))
        assertTrue(xml.contains("t=\"n\"><v>0.03</v>")); assertTrue(xml.contains("rightToLeft=\"1\""))
        assertFalse(xml.contains("<f>")); assertFalse(entries.keys.any { it.contains("external") || it.contains("vba") })
    }

    @Test fun callerOutputIsNotClosedAndFailuresPropagate() {
        val stream = object : ByteArrayOutputStream() { var closed = false; override fun close() { closed = true; super.close() } }
        BoqXlsxWriter.write(listOf(sheet("text")), stream)
        assertFalse(stream.closed)
        var failed = false
        try { BoqXlsxWriter.write(listOf(sheet("text")), object : java.io.OutputStream() { override fun write(b: Int) { throw java.io.IOException("synthetic write failure") } }) }
        catch (_: java.io.IOException) { failed = true }
        assertTrue(failed)
    }

    @Test fun rejectsExtremeExponentsBeforePlainStringAllocationAndAllowsExplicitText() {
        listOf("1E+400", "1E-400", "0E-2147483647").forEach { unsafe ->
            val out = ByteArrayOutputStream()
            refused { BoqXlsxWriter.write(listOf(BoqWorkbookSheet("one", listOf(BoqWorkbookRow(listOf(BoqWorkbookCell.Number(BigDecimal(unsafe))))))), out) }
            assertEquals(0, out.size())
            BoqXlsxWriter.write(listOf(sheet(unsafe)), ByteArrayOutputStream())
        }
    }

    @Test fun rejectsBadNamesControlsAndUnsafeNumericPrecision() {
        refused { BoqXlsxWriter.write(listOf(sheet("a").copy(name = "bad/name")), ByteArrayOutputStream()) }
        refused { BoqXlsxWriter.write(listOf(sheet("a"), sheet("b")), ByteArrayOutputStream()) }
        refused { BoqXlsxWriter.write(listOf(sheet("bad\u0001")), ByteArrayOutputStream()) }
        refused { BoqXlsxWriter.write(listOf(BoqWorkbookSheet("one", listOf(BoqWorkbookRow(listOf(BoqWorkbookCell.Number(BigDecimal("1234567890123456"))))))), ByteArrayOutputStream()) }
    }
}
