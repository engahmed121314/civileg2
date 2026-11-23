package com.civileg.core.costcontrol

import java.io.OutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class BoqWorkbookCell {
    data class Text(val value: String) : BoqWorkbookCell()
    /** Spreadsheet numbers have finite precision. Use Text for original input requiring exact fidelity. */
    data class Number(val value: BigDecimal) : BoqWorkbookCell()
}

data class BoqWorkbookRow(val cells: List<BoqWorkbookCell>, val header: Boolean = false, val height: Int = 28)
data class BoqWorkbookSheet(val name: String, val rows: List<BoqWorkbookRow>, val rightToLeft: Boolean = true)

/**
 * Small value-only OOXML XLSX writer. No macros, formulas, external links or copied template parts.
 * Caller supplies reviewed rows and separate source-exact text columns/sheet. This is not an importer,
 * template-preserving editor, formula recalculator or arbitrary large-workbook streaming solution.
 * Uses only platform ZIP/XML text output, no new Android dependency or build edit.
 */
object BoqXlsxWriter {
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    /** Closes the ZIP wrapper but leaves caller stream open. Failure propagates, never reported as saved. */
    fun write(sheets: List<BoqWorkbookSheet>, output: OutputStream) {
        require(sheets.isNotEmpty() && sheets.size <= 100)
        require(sheets.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == sheets.size)
        var totalCharacters = 0L
        var totalCells = 0L
        sheets.forEach { sheet ->
            require(sheet.name.isNotBlank() && sheet.name.length <= 31 && sheet.name.none { it in "[]:*?/\\" })
            require(!sheet.name.startsWith("'") && !sheet.name.endsWith("'"))
            require(sheet.rows.size <= 10000 && sheet.rows.all { it.cells.size <= 64 && it.height in 15..409 })
            xml(sheet.name)
            sheet.rows.forEach { row -> row.cells.forEach { cell ->
                totalCells++
                when (cell) {
                    is BoqWorkbookCell.Text -> {
                        require(cell.value.length <= 32767)
                        totalCharacters += cell.value.length
                        xml(cell.value)
                    }
                    is BoqWorkbookCell.Number -> {
                        require(cell.value.precision() <= 15 && cell.value.scale() in -308..308 && (cell.value.signum() == 0 ||
                            cell.value.precision().toLong() - cell.value.scale().toLong() - 1 in -307L..307L)) {
                            "Use text for numbers outside the supported spreadsheet precision/range"
                        }
                    }
                }
            } }
        }
        require(totalCells <= 100000 && totalCharacters <= 8_000_000) { "Workbook exceeds bounded export size" }
        val zip = ZipOutputStream(object : java.io.FilterOutputStream(output) {
            override fun close() { flush() }
        })
        zip.use {
            fun part(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                zip.write(xml.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            part("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.indices.joinToString("") { i -> "<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""")
            part("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            part("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><bookViews><workbookView/></bookViews><sheets>${sheets.mapIndexed { i, sheet -> "<sheet name=\"${xml(sheet.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("")}</sheets></workbook>""")
            part("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.indices.joinToString("") { i -> "<Relationship Id=\"rId${i + 1}\" Type=\"$REL/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>" }}<Relationship Id="styles" Type="$REL/styles" Target="styles.xml"/></Relationships>""")
            part("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$MAIN"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.##############"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDCE6F1"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left style="thin"><color rgb="FFD9E1F2"/></left><right style="thin"><color rgb="FFD9E1F2"/></right><top style="thin"><color rgb="FFD9E1F2"/></top><bottom style="thin"><color rgb="FFD9E1F2"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            sheets.forEachIndexed { i, sheet -> part("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
        }
    }

    private fun sheetXml(sheet: BoqWorkbookSheet): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"$MAIN\"><sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr><sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"${if (sheet.rightToLeft) 1 else 0}\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"28\"/><cols><col min=\"1\" max=\"1\" width=\"14\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"65\" customWidth=\"1\"/><col min=\"3\" max=\"64\" width=\"20\" customWidth=\"1\"/></cols><sheetData>")
        sheet.rows.forEachIndexed { index, row ->
            val r = index + 1
            append("<row r=\"$r\" ht=\"${row.height}\" customHeight=\"1\">")
            row.cells.forEachIndexed { column, cell ->
                val ref = "${columnName(column)}$r"; val style = if (row.header) 1 else 0
                when (cell) {
                    is BoqWorkbookCell.Text -> {
                        require(cell.value.length <= 32767)
                        append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(cell.value)}</t></is></c>")
                    }
                    is BoqWorkbookCell.Number -> {
                        require(cell.value.precision() <= 15) { "Use a text cell for more than 15 significant digits" }
                        append("<c r=\"$ref\" s=\"$style\" t=\"n\"><v>${cell.value.toPlainString()}</v></c>")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData><pageMargins left=\"0.3\" right=\"0.3\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/><pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>")
    }

    private fun columnName(index: Int): String {
        var n = index + 1; var result = ""
        while (n > 0) { n--; result = ('A' + n % 26) + result; n /= 26 }
        return result
    }
    private fun xml(value: String): String {
        require(value.codePoints().allMatch { it == 9 || it == 10 || it == 13 || it in 32..0xD7FF || it in 0xE000..0xFFFD || it in 0x10000..0x10FFFF }) { "Invalid XML character" }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    }
}
