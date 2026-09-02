package com.civileg.app.domain.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §42 QA/QC engine contracts — the "no false PASS" law:
 *  1. One FAIL ⇒ overall FAIL regardless of everything else.
 *  2. NOT CHECKED alone ⇒ overall NOT CHECKED (never PASS).
 *  3. Empty audit ⇒ NOT CHECKED with health 0.
 *  4. Health formula: pass=1, warn/notChecked=0.5, fail=0.
 *  5. Manifest feeds: failures/warnings formatted, passed counted clean-only.
 *  6. Duplicate check identities rejected loudly.
 */
class EngineeringAuditEngineTest {

    private val eng = EngineeringAuditEngine

    @Test fun oneFail_dominates_everything() {
        val r = eng.report("P", listOf(
            AuditCheck(AuditStage.DESIGN, "Flexure", AuditStatus.PASS),
            AuditCheck(AuditStage.DESIGN, "Punching", AuditStatus.FAIL, "Vu > φVc"),
            AuditCheck(AuditStage.BBS, "Schedule", AuditStatus.WARNING, "high congestion")
        ))
        assertEquals(AuditStatus.FAIL, r.status)
        assertEquals(1, r.failures.size)
        assertTrue(r.failures[0].contains("Punching"))
    }

    @Test fun notChecked_alone_isNeverPass() {
        val r = eng.report("P", listOf(
            AuditCheck(AuditStage.ANALYSIS, "Drift", AuditStatus.NOT_CHECKED)
        ))
        assertEquals(AuditStatus.NOT_CHECKED, r.status)
        // and it must surface in the warnings channel — visible doubt
        assertTrue(r.warnings.any { it.contains("NOT CHECKED") && it.contains("Drift") })
    }

    @Test fun emptyAudit_notChecked_healthZero() {
        val r = eng.report("P", emptyList())
        assertEquals(AuditStatus.NOT_CHECKED, r.status)
        assertEquals(0, r.healthPercent)
        assertEquals(0, r.passedChecksForManifest)
    }

    @Test fun allPass_isTheOnlyPathToOverallPass() {
        val r = eng.report("P", listOf(
            AuditCheck(AuditStage.INPUT, "Geometry", AuditStatus.PASS),
            AuditCheck(AuditStage.DXF, "QA gate", AuditStatus.PASS)
        ))
        assertEquals(AuditStatus.PASS, r.status)
        assertEquals(100, r.healthPercent)
    }

    @Test fun healthFormula_halfCreditForWarningsAndUnchecked() {
        val r = eng.report("P", listOf(
            AuditCheck(AuditStage.DESIGN, "A", AuditStatus.PASS),     // 1.0
            AuditCheck(AuditStage.DESIGN, "B", AuditStatus.PASS),     // 1.0
            AuditCheck(AuditStage.PDF, "C", AuditStatus.WARNING),     // 0.5
            AuditCheck(AuditStage.BOQ, "D", AuditStatus.NOT_CHECKED)  // 0.5
        ))
        assertEquals(75, r.healthPercent)   // (1+1+0.5+0.5)/4 = 75%
        assertEquals(AuditStatus.NOT_CHECKED, r.status) // unchecked still caps verdict
    }

    @Test fun manifestFeeds_formattedLines() {
        val r = eng.report("Tower", listOf(
            AuditCheck(AuditStage.ANALYSIS, "Equilibrium", AuditStatus.FAIL, "ΣFx ≠ 0"),
            AuditCheck(AuditStage.BBS, "Weights", AuditStatus.WARNING, "unit weight mismatch"),
            AuditCheck(AuditStage.PDF, "Cover page", AuditStatus.PASS)
        ))
        assertEquals(1, r.passedChecksForManifest)
        assertEquals(listOf("Analysis/Equilibrium: ΣFx ≠ 0"), r.failures)
        assertEquals(1, r.warnings.size)
        assertTrue(r.warnings[0].startsWith("BBS/WARNING/"))
    }

    @Test fun duplicateIdentity_rejected() {
        val dupes = listOf(
            AuditCheck(AuditStage.DESIGN, "Flexure", AuditStatus.PASS),
            AuditCheck(AuditStage.DESIGN, "Flexure", AuditStatus.FAIL)
        )
        val err = runCatching { eng.report("P", dupes) }
        assertTrue(err.isFailure)
    }

    @Test fun capacityProbe_thresholds() {
        assertEquals(AuditStatus.PASS, eng.capacityCheck(AuditStage.DESIGN, "Mu", 0.55).status)
        assertEquals(AuditStatus.WARNING, eng.capacityCheck(AuditStage.DESIGN, "Mu", 0.95).status)
        assertEquals(AuditStatus.FAIL, eng.capacityCheck(AuditStage.DESIGN, "Vu", 1.20).status)
        assertEquals(AuditStatus.NOT_CHECKED, eng.capacityCheck(AuditStage.DESIGN, "Mx", null).status)
        assertEquals(AuditStatus.NOT_CHECKED, eng.capacityCheck(AuditStage.DESIGN, "NaN", Double.NaN).status)
    }

    @Test fun artifactProbe_loudAbsence() {
        assertEquals(AuditStatus.NOT_CHECKED,
            eng.artifactExistsCheck(AuditStage.DXF, "Sheets", null).status)
        assertEquals(AuditStatus.NOT_CHECKED,
            eng.artifactExistsCheck(AuditStage.DXF, "Ghost", File("Z:/nope/x.dxf")).status)
        val real = File.createTempFile("sheet", ".dxf").apply { writeText("0\nEOF\n") }
        try {
            assertEquals(AuditStatus.PASS,
                eng.artifactExistsCheck(AuditStage.DXF, "Sheets", real).status)
        } finally { real.delete() }
    }
}
