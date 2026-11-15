package com.civileg.core.output

import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityLevel
import org.junit.Assert.*
import org.junit.Test

class StructuralReportExporterTest {

    @Test
    fun beamDxfGenerationContainsStandardLayersAndGeometry() {
        val data = StructuralReportExporter.BeamExportData(
            elementName = "B101",
            designCode = "ECP 203",
            bMm = 250.0,
            hMm = 600.0,
            spanM = 6.0,
            fcuMPa = 30.0,
            fyMPa = 400.0,
            muKNm = 250.0,
            vuKN = 120.0,
            asReqMm2 = 1500.0,
            asProvText = "5Φ20",
            asProvMm2 = 1570.8
        )

        val dxfContent = StructuralReportExporter.generateBeamDxf(data)

        assertTrue("DXF should contain HEADER section", dxfContent.contains("HEADER"))
        assertTrue("DXF should contain ECP_CONCRETE layer", dxfContent.contains("ECP_CONCRETE"))
        assertTrue("DXF should contain ECP_REBAR layer", dxfContent.contains("ECP_REBAR"))
        assertTrue("DXF should contain BEAM B101 title", dxfContent.contains("BEAM B101"))
        assertTrue("DXF should end with EOF", dxfContent.contains("EOF"))
    }

    @Test
    fun beamPdfGenerationContainsReportSectionsAndStatus() {
        val data = StructuralReportExporter.BeamExportData(
            elementName = "B101",
            designCode = "ECP 203",
            bMm = 250.0,
            hMm = 600.0,
            spanM = 6.0,
            fcuMPa = 30.0,
            fyMPa = 400.0,
            muKNm = 250.0,
            vuKN = 120.0,
            asReqMm2 = 1500.0,
            asProvText = "5Φ20",
            asProvMm2 = 1570.8
        )

        val pdfContent = StructuralReportExporter.generateBeamPdf(data)

        assertTrue("PDF should start with %PDF-1.4 header", pdfContent.startsWith("%PDF-1.4"))
        assertTrue("PDF should contain trailer", pdfContent.contains("trailer"))
        assertTrue("PDF should contain EOF tag", pdfContent.contains("%%EOF"))
    }

    @Test
    fun columnDxfAndPdfGenerationWorksCorrectly() {
        val data = StructuralReportExporter.ColumnExportData(
            elementName = "C101",
            designCode = "ECP 203",
            bMm = 300.0,
            tMm = 700.0,
            lengthM = 4.0,
            fcuMPa = 30.0,
            fyMPa = 400.0,
            puKN = 2000.0,
            puCapacityKN = 2800.0,
            slendernessLambda = 10.5,
            asProvText = "8Φ16",
            asProvMm2 = 1608.5
        )

        val dxf = StructuralReportExporter.generateColumnDxf(data)
        val pdf = StructuralReportExporter.generateColumnPdf(data)

        assertTrue("Column DXF should contain COLUMN C101", dxf.contains("COLUMN C101"))
        assertTrue("Column PDF should contain Column Geometry", pdf.contains("Column Geometry"))
    }

    @Test
    fun footingDxfAndPdfGenerationWorksCorrectly() {
        val data = StructuralReportExporter.FootingExportData(
            elementName = "F101",
            designCode = "ECP 203",
            lengthMm = 2200.0,
            widthMm = 1800.0,
            thicknessMm = 600.0,
            fcuMPa = 25.0,
            fyMPa = 400.0,
            columnPuKN = 1500.0,
            qAllowableKgCm2 = 1.5,
            qActualKgCm2 = 1.35,
            rebarXText = "6Φ16/m",
            rebarYText = "5Φ16/m"
        )

        val dxf = StructuralReportExporter.generateFootingDxf(data)
        val pdf = StructuralReportExporter.generateFootingPdf(data)

        assertTrue("Footing DXF should contain FOOTING F101", dxf.contains("FOOTING F101"))
        assertTrue("Footing PDF should contain Footing Geometry", pdf.contains("Footing Geometry"))
    }

    @Test
    fun steelDxfAndPdfGenerationWorksCorrectly() {
        val data = StructuralReportExporter.SteelExportData(
            elementName = "S101",
            designCode = "AISC 360",
            sectionName = "IPE 300",
            lengthM = 6.0,
            puKN = 150.0,
            muKNm = 80.0,
            maxDeflectionMm = 8.5,
            allowDeflectionMm = 15.0,
            utilizationRatio = 0.72
        )

        val dxf = StructuralReportExporter.generateSteelDxf(data)
        val pdf = StructuralReportExporter.generateSteelPdf(data)

        assertTrue("Steel DXF should contain STEEL MEMBER S101", dxf.contains("STEEL MEMBER S101"))
        assertTrue("Steel PDF should contain Steel Section", pdf.contains("Steel Section"))
    }

    @Test
    fun sanityEngineCatchesUnderReinforcedBeam() {
        val sanity = EngineeringSanityEngine.checkBeam(
            bMm = 250.0, hMm = 600.0, spanM = 6.0,
            asReqMm2 = 1500.0, asProvMm2 = 1000.0,
            shearVuKN = 100.0, shearVcKN = 150.0
        )

        assertTrue("Sanity should report error when provided steel < required steel", sanity.hasError)
        val errorMessage = sanity.items.firstOrNull { it.level == SanityLevel.ERROR }?.message
        assertNotNull(errorMessage)
        assertTrue(errorMessage!!.contains("Provided steel"))
    }

    @Test
    fun sanityEngineCatchesOverStressedColumn() {
        val sanity = EngineeringSanityEngine.checkColumn(
            bMm = 300.0, tMm = 600.0, lengthM = 3.5,
            puKN = 3500.0, puCapacityKN = 3000.0,
            slendernessLambda = 12.0, asProvMm2 = 2500.0
        )

        assertTrue("Sanity should report error when Pu > Capacity", sanity.hasError)
    }
}
