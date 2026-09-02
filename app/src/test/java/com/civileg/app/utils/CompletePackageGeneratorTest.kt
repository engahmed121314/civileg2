package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * §93/§94 Final Engineering Package contracts:
 *  - Categorization rules route every artifact to its spec subfolder
 *  - Package generation copies sources, never fabricates missing ones
 *  - MANIFEST.json carries project/revision/code/engine, file list,
 *    checks, warnings, failures, unverified items
 *
 * NOTE: asserted on raw text (org.json is an android.jar stub on local JVM).
 */
class CompletePackageGeneratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── Categorization (first-match-wins contract) ───────────────────

    @Test fun categoryRules_routeByExtensionAndName() {
        assertEquals("DXF", CompletePackageGenerator.categoryFor("Structural_Drawings.dxf"))
        assertEquals("BBS", CompletePackageGenerator.categoryFor("BBS.pdf"))
        assertEquals("BBS", CompletePackageGenerator.categoryFor("bbs_report.PDF"))
        assertEquals("BOQ", CompletePackageGenerator.categoryFor("Project_BOQ.pdf"))
        assertEquals("Excel", CompletePackageGenerator.categoryFor("BBS.xlsx"))
        assertEquals("Excel", CompletePackageGenerator.categoryFor("boq_export.csv"))
        assertEquals("PDF", CompletePackageGenerator.categoryFor("Calculation_Report.pdf"))
        assertEquals("PROJECT", CompletePackageGenerator.categoryFor("Model.civileg"))
    }

    @Test fun safeName_stripsPathAndIllegalChars() {
        // each illegal char (/ \ ?) maps to one underscore
        assertEquals("a_b_c_.pdf", CompletePackageGenerator.safeName("a/b\\c?.pdf"))
        assertEquals("unnamed", CompletePackageGenerator.safeName("  "))
    }

    // ── Generation ───────────────────────────────────────────────────

    private fun makeSource(name: String, content: String = "x") =
        tmp.newFile(name).apply { writeText(content) }

    @Test fun generatePackage_copiesIntoSpecFolders_andWritesManifest() {
        val srcPdf = makeSource("Calculation_Report.pdf")
        val srcDxf = makeSource("Beams.dxf")
        val srcBbs = makeSource("BBS.pdf")
        val out = tmp.newFolder("out")

        val result = CompletePackageGenerator.generatePackage(
            targetRoot = out,
            projectName = "Residential Tower",
            sources = listOf(srcPdf, srcDxf, srcBbs),
            codeVersion = "ECP 203-2020",
            revision = "R1",
            passedChecks = 42,
            warnings = listOf("High congestion at joint J5"),
            failures = emptyList()
        )

        assertTrue(result.missingSources.isEmpty())
        assertTrue(result.files.contains("MANIFEST.json"))
        assertTrue(File(result.rootDir, "PDF${File.separatorChar}Calculation_Report.pdf").exists())
        assertTrue(File(result.rootDir, "DXF${File.separatorChar}Beams.dxf").exists())
        assertTrue(File(result.rootDir, "BBS${File.separatorChar}BBS.pdf").exists())

        val manifest = result.manifestFile.readText()
        assertTrue(manifest.contains("\"project\": \"Residential Tower\""))
        assertTrue(manifest.contains("\"revision\": \"R1\""))
        assertTrue(manifest.contains("\"code\": \"ECP 203-2020\""))
        assertTrue(manifest.contains("\"passedChecks\": 42"))
        assertTrue(manifest.contains("High congestion at joint J5"))
        assertTrue("\"failures\": []" in manifest)
        // File list includes all three copies + manifest itself
        assertTrue(manifest.contains("PDF/Calculation_Report.pdf"))
        assertTrue(manifest.contains("DXF/Beams.dxf"))
        assertTrue(manifest.contains("BBS/BBS.pdf"))
    }

    @Test fun generatePackage_missingSources_listedNotFabricated() {
        val real = makeSource("Design_Summary.pdf")
        val phantom = File(tmp.root, "ghosts/Ghost.dxf") // never created
        val out = tmp.newFolder("out2")

        val result = CompletePackageGenerator.generatePackage(
            targetRoot = out,
            projectName = "Proj",
            sources = listOf(real, phantom),
            codeVersion = "ACI 318-19"
        )

        assertTrue("missing source must be reported", result.missingSources.size == 1)
        assertTrue(result.missingSources[0].contains("Ghost.dxf"))
        val manifest = result.manifestFile.readText()
        // Appears as unverifiedItems — no silent loss
        assertTrue(manifest.contains("\"unverifiedItems\": [") && manifest.contains("Ghost.dxf"))
        // No DXF folder fabricated for a file that doesn't exist
        assertTrue(!File(result.rootDir, "DXF").exists() ||
            File(result.rootDir, "DXF").list()?.isEmpty() == true)
    }

    @Test fun generatePackage_noSilentOverwrites_onNameCollision() {
        val a = tmp.newFolder("inA")
        val b = tmp.newFolder("inB")
        val f1 = File(a, "Report.pdf").apply { writeText("v1") }
        val f2 = File(b, "Report.pdf").apply { writeText("v2") }
        val out = tmp.newFolder("out3")

        val result = CompletePackageGenerator.generatePackage(
            targetRoot = out, projectName = "Dup", sources = listOf(f1, f2),
            codeVersion = "SBC 304-2018"
        )

        val pdfDir = File(result.rootDir, "PDF")
        assertEquals("both files must survive", 2, pdfDir.listFiles()!!.size)
        val contents = pdfDir.listFiles()!!.map { it.readText() }.sorted()
        assertEquals(listOf("v1", "v2"), contents)
        assertEquals(3, result.files.size) // 2 copies + manifest
    }

    @Test fun manifest_escapesSpecialCharacters_safely() {
        val json = CompletePackageGenerator.buildManifest(
            projectName = "Tower \"A\" \\ Phase\t2",
            codeVersion = "ECP \"203\"",
            engineVersion = "X",
            revision = "R0",
            date = "2026-08-25T10:00:00",
            files = listOf("weird\"name.pdf"),
            passedChecks = 0,
            warnings = listOf("line1\nline2"),
            failures = emptyList(),
            missingSources = emptyList()
        )
        // Escaped sequences present exactly once per occurrence; parseable shape
        assertTrue(json.contains("\"project\": \"Tower \\\"A\\\" \\\\ Phase\\t2\""))
        assertTrue(json.contains("\"code\": \"ECP \\\"203\\\"\""))
        assertTrue(json.contains("\\nline2"))
        assertTrue(json.contains("\"weird\\\"name.pdf\""))
        // Balanced quotes sanity: starts/ends as JSON object
        assertTrue(json.trimStart().startsWith("{") && json.trimEnd().endsWith("}"))
    }

    // ── §42 bridge: AuditReport → manifest (single source of QA truth) ──

    @Test fun auditReport_feedsManifest_directly() {
        val srcPdf = makeSource("Calculation_Report.pdf")
        val out = tmp.newFolder("outAudit")

        val audit = com.civileg.app.domain.audit.EngineeringAuditEngine.report(
            "Residential Tower",
            listOf(
                com.civileg.app.domain.audit.AuditCheck(
                    com.civileg.app.domain.audit.AuditStage.DESIGN, "Flexure",
                    com.civileg.app.domain.audit.AuditStatus.PASS),
                com.civileg.app.domain.audit.AuditCheck(
                    com.civileg.app.domain.audit.AuditStage.DESIGN, "Punching",
                    com.civileg.app.domain.audit.AuditStatus.FAIL, "Vu > φVc"),
                com.civileg.app.domain.audit.AuditCheck(
                    com.civileg.app.domain.audit.AuditStage.DXF, "QA gate",
                    com.civileg.app.domain.audit.AuditStatus.WARNING, "minor layer gap")
            )
        )

        val result = CompletePackageGenerator.generatePackage(
            targetRoot = out, projectName = "Tower", sources = listOf(srcPdf),
            codeVersion = "ECP 203-2020", audit = audit
        )
        val manifest = result.manifestFile.readText()
        // passedChecks counts CLEAN checks only — the FAIL never inflates it
        assertTrue(manifest.contains("\"passedChecks\": 1"))
        assertTrue(manifest.contains("Design/Equilibrium") || manifest.contains("Punching: Vu > φVc")
            || manifest.contains("Design/Punching"))
        assertTrue(manifest.contains("Vu > φVc"))          // failure surfaced verbatim
        assertTrue(manifest.contains("WARNING/minor layer gap") || manifest.contains("minor layer gap"))
    }
}

