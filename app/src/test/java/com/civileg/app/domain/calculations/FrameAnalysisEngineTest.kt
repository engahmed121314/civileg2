package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FrameAnalysisEngine — the stiffness matrix frame analysis engine.
 * Covers: simple portal frame, InputGuard validation, equilibrium checks,
 * basic structural behavior verification, Phase-1 critical fixes
 * (C1..C5 model validation + S1/S2/S3/S4 sign & formula corrections),
 * Phase-2 hardening (C6..C13), and Frame-Modeler member end releases.
 *
 * VERIFIED BENCHMARKS (classical solutions):
 *  - Simply supported beam, UDL w over L:  M_mid = +wL²/8, M_ends = 0, R = wL/2
 *  - Fixed-fixed beam, UDL w over L:       M_ends = -wL²/12 (hogging), M_mid = +wL²/24
 *  - Fixed-fixed beam, CCW moment M at a:  m_i = M·b(3a−L)/L², m_j = M·a(2L−3a)/L²,
 *                                          v_i = 6Mab/L³  (S3 re-derived & cross-checked)
 */
class FrameAnalysisEngineTest {

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private fun createSimplePortalFrame(): Triple<List<FrameNode>, List<FrameMember>, FrameAnalysisSettings> {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 0.0, 4.0, SupportType.Free),
            FrameNode(3, 6.0, 4.0, SupportType.Free),
            FrameNode(4, 6.0, 0.0, SupportType.Fixed)
        )

        val colSection = ConcreteSectionProps(300.0, 300.0, 25.0, 400.0, 50.0)
        val beamSection = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)

        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Column, colSection, name = "LeftCol"),
            FrameMember(2, 2, 3, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSection, name = "Beam"),
            FrameMember(3, 4, 3, FrameMaterialType.Concrete, FrameMemberType.Column, colSection, name = "RightCol")
        )

        val settings = FrameAnalysisSettings(designCode = DesignCode.ECP)
        return Triple(nodes, members, settings)
    }

    private fun simplySupportedBeam(): Triple<List<FrameNode>, List<FrameMember>, FrameAnalysisSettings> {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Pin)
        )
        val beamSection = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSection)
        )
        return Triple(nodes, members, FrameAnalysisSettings(designCode = DesignCode.ACI))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. InputGuard — empty collections (loud failures, ADR-010)
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun solveFrame_emptyNodes_throwsException() {
        val settings = FrameAnalysisSettings()
        FrameAnalysisEngine.solveFrame(
            emptyList(),
            listOf(FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, ConcreteSectionProps(300.0, 500.0))),
            emptyList(), emptyList(), settings
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun solveFrame_emptyMembers_throwsException() {
        val settings = FrameAnalysisSettings()
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed)
        )
        FrameAnalysisEngine.solveFrame(nodes, emptyList(), emptyList(), emptyList(), settings)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Insufficient nodes — returns error result
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_insufficientNodes_returnsErrorMessage() {
        val settings = FrameAnalysisSettings()
        val nodes = listOf(FrameNode(1, 0.0, 0.0, SupportType.Fixed))
        val members = listOf(FrameMember(1, 1, 1, FrameMaterialType.Concrete, FrameMemberType.Beam, ConcreteSectionProps(300.0, 500.0)))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), emptyList(), settings)
        assertNotNull("Should have error message for insufficient nodes", result.errorMessage)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Simple portal frame — horizontal nodal load
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_portalWithHorizontalLoad_producesResults() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings)
        assertTrue("Frame should solve successfully: ${result.errorMessage}", result.isSolved)
    }

    @Test
    fun solveFrame_portalWithHorizontalLoad_hasNodeResults() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings)
        if (result.isSolved) {
            assertTrue("Should have node results", result.nodeResults.isNotEmpty())
        }
    }

    @Test
    fun solveFrame_portalWithHorizontalLoad_hasMemberForces() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings)
        if (result.isSolved) {
            assertTrue("Should have member end forces", result.memberEndForces.isNotEmpty())
        }
    }

    @Test
    fun solveFrame_portalWithHorizontalLoad_horizontalEquilibrium() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings)
        assertTrue(result.isSolved)
        val sumRx = result.nodeResults.sumOf { it.reactionFx }
        assertEquals("ΣRx reactions must balance applied horizontal load",
            50.0, sumRx, 1e-6)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Simple portal frame — distributed load on beam
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_portalWithUDL_producesResults() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val memberLoads = listOf(MemberLoad(2, MemberLoadType.UDL, 20.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Frame should solve successfully with UDL", result.isSolved)
    }

    @Test
    fun solveFrame_portalWithUDL_hasDiagrams() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val memberLoads = listOf(MemberLoad(2, MemberLoadType.UDL, 20.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        if (result.isSolved) {
            assertTrue("Should have member diagrams", result.memberDiagrams.isNotEmpty())
        }
    }

    @Test
    fun solveFrame_portalWithUDL_verticalEquilibrium() {
        // S1 regression: with corrected P_eq sign, ΣRy must equal wL = 20×6 = 120 kN
        val (nodes, members, settings) = createSimplePortalFrame()
        val memberLoads = listOf(MemberLoad(2, MemberLoadType.UDL, 20.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)
        val sumRy = result.nodeResults.sumOf { it.reactionFy }
        assertEquals("ΣRy reactions must balance UDL (wL = 120 kN)",
            120.0, sumRy, 0.05)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Vertical load — reactions should balance
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_verticalLoad_reactionsSumEqualsLoad() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = 0.0, fy = -100.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings)
        if (result.isSolved && result.nodeResults.size >= 2) {
            val totalVerticalReaction = result.nodeResults.sumOf { it.reactionFy }
            assertEquals("Vertical reactions should balance applied load",
                100.0, totalVerticalReaction, 5.0)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. No loads — zero or near-zero results
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_noLoads_producesZeroOrNearZeroResults() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), emptyList(), settings)
        if (result.isSolved) {
            result.nodeResults.forEach { node ->
                assertTrue("All displacements should be near zero with no loads",
                    kotlin.math.abs(node.dx) < 1e-6 && kotlin.math.abs(node.dy) < 1e-6)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Simply supported beam — CLASSICAL BENCHMARK (S1 + S2 regression)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_simplySupportedBeam_producesResults() {
        val (nodes, members, settings) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Simply supported beam should solve", result.isSolved)
    }

    @Test
    fun solveFrame_simplySupportedBeam_correctMaxMoment() {
        // Classical: M_max = wL²/8 = 10×6²/8 = 45 kN.m  (was 104.9 before S1/S2 fixes)
        val (nodes, members, settings) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)
        val maxMoment = result.memberDiagrams[0].maxMoment
        assertEquals("Max moment must equal wL²/8 = 45 kN.m (S1/S2 regression)",
            45.0, maxMoment, 0.5)
    }

    @Test
    fun solveFrame_simplySupportedBeam_zeroEndMoments() {
        // Pin ends must carry zero member end moment
        val (nodes, members, settings) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)
        val f = result.memberEndForces[0]
        assertEquals("End moment at pin I must be zero", 0.0, kotlin.math.abs(f.mi_z), 1e-6)
        assertEquals("End moment at pin J must be zero", 0.0, kotlin.math.abs(f.mj_z), 1e-6)
    }

    @Test
    fun solveFrame_simplySupportedBeam_reactionsAndDiagram() {
        val (nodes, members, settings) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)

        // Reactions: wL/2 = 30 kN each
        assertEquals("Reaction at I must be wL/2", 30.0, result.nodeResults[0].reactionFy, 0.01)
        assertEquals("Reaction at J must be wL/2", 30.0, result.nodeResults[1].reactionFy, 0.01)

        // Diagram: M(0) = 0, M(L/2) = +45, M(L) = 0
        val md = result.memberDiagrams[0].momentDiagram
        assertEquals("M(0) must be zero", 0.0, kotlin.math.abs(md.first().value), 1e-6)
        assertEquals("M(L) must be zero", 0.0, kotlin.math.abs(md.last().value), 1e-6)
        val mid = md.minByOrNull { kotlin.math.abs(it.x - 3.0) }!!
        assertEquals("M(L/2) must be +wL²/8 (sagging)", 45.0, mid.value, 0.5)

        // Shear: V(0) = +30, V(L) = -30
        val sd = result.memberDiagrams[0].shearDiagram
        assertEquals("V(0) must be +wL/2", 30.0, sd.first().value, 0.01)
        assertEquals("V(L) must be -wL/2", -30.0, sd.last().value, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Fixed-fixed beam — CLASSICAL BENCHMARK (S2 regression)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_fixedFixedBeam_udlEndAndMidspanMoments() {
        // Classical: M_end = -wL²/12 = -30 kN.m (hogging), M_mid = +wL²/24 = +15 kN.m
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed)
        )
        val beamSection = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSection)
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val settings = FrameAnalysisSettings(designCode = DesignCode.ACI)

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Fixed-fixed beam should solve: ${result.errorMessage}", result.isSolved)

        val md = result.memberDiagrams[0].momentDiagram
        assertEquals("M(0) must be -wL²/12 (hogging)", -30.0, md.first().value, 0.01)
        val mid = md.minByOrNull { kotlin.math.abs(it.x - 3.0) }!!
        assertEquals("M(L/2) must be +wL²/24 (sagging)", 15.0, mid.value, 0.01)
        assertEquals("M(L) must be -wL²/12 (hogging)", -30.0, md.last().value, 0.01)

        // End forces: |m_i| = |m_j| = wL²/12 = 30, v = wL/2 = 30
        val f = result.memberEndForces[0]
        assertEquals("|m_i| = wL²/12", 30.0, kotlin.math.abs(f.mi_z), 0.01)
        assertEquals("|m_j| = wL²/12", 30.0, kotlin.math.abs(f.mj_z), 0.01)

        // Reactions balance the UDL
        assertEquals("ΣRy = wL = 60", 60.0, result.nodeResults.sumOf { it.reactionFy }, 0.05)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. Applied moment on fixed-fixed beam — S3 regression
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_fixedFixedBeam_appliedMomentAtMidspan() {
        // CCW M = 20 kN.m at midspan (a = b = 3).
        // S3 (مُعاد اشتقاقه والتحقق بطريقتين مستقلتين — تكامل مباشر + طريقة القوى):
        //   q0 = [0, +6Mab/L³, +Mb(3a-L)/L², 0, -6Mab/L³, +Ma(2L-3a)/L²]
        //   → m_i = m_j = +5 kN.m ، v_i = 6Mab/L³ = +5 kN
        //   Internal: M(0) = -m_i = -5, jump of 20 at midspan, M(L) = +m_j = +5
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed)
        )
        val beamSection = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSection)
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.Moment, 20.0, 3.0))
        val settings = FrameAnalysisSettings(designCode = DesignCode.ACI)

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)

        val f = result.memberEndForces[0]
        assertEquals("m_i = Mb(3a-L)/L² = +5", 5.0, f.mi_z, 0.01)
        assertEquals("m_j = Ma(2L-3a)/L² = +5", 5.0, f.mj_z, 0.01)

        val md = result.memberDiagrams[0].momentDiagram
        assertEquals("M(0) = -m_i = -5", -5.0, md.first().value, 0.01)
        val beforeA = md.filter { it.x < 3.0 }.maxByOrNull { it.x }!!   // a⁻ε
        val afterA = md.filter { it.x > 3.0 }.minByOrNull { it.x }!!    // a⁺ε
        assertEquals("M(a⁻) = -5 + v·a = +10", 10.0, beforeA.value, 0.01)
        assertEquals("M(a⁺) = +10 - M₀ = -10", -10.0, afterA.value, 0.01)
        assertEquals("M(L) = +m_j = +5", 5.0, md.last().value, 0.01)

        // Global equilibrium of the member: m_i + m_j + v_j·L + M₀ = 0
        val vI = 6.0 * 20.0 * 3.0 * 3.0 / 216.0 // = 5.0
        assertEquals("v_i = 6Mab/L³", vI, f.fi_y, 0.01)
        assertEquals("v_j = -v_i", -vI, f.fj_y, 0.01)
    }

    @Test
    fun solveFrame_fixedFixedBeam_pointLoadAtMidspan() {
        // Classical fixed-fixed, P = 24 kN at midspan: M_end = -PL/8 = -18, M_mid(P side) = +PL/8 = +18
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed)
        )
        val beamSection = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSection)
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.PointLoad, 24.0, 3.0))
        val settings = FrameAnalysisSettings(designCode = DesignCode.ACI)

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)

        val md = result.memberDiagrams[0].momentDiagram
        assertEquals("M(0) = -PL/8", -18.0, md.first().value, 0.01)
        val beforeMid = md.filter { it.x < 3.0 }.maxByOrNull { it.x }!!   // a⁻ε
        assertEquals("M(a⁻) = +PL/8", 18.0, beforeMid.value, 0.01)
        assertEquals("M(L) = -PL/8", -18.0, md.last().value, 0.01)
        assertEquals("maxMoment = PL/8 (C7)", 18.0, result.memberDiagrams[0].maxMoment, 0.01)
        assertEquals("ΣRy = P = 24", 24.0, result.nodeResults.sumOf { it.reactionFy }, 0.05)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10. Settings null check
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = NullPointerException::class)
    fun solveFrame_nullSettings_throwsException() {
        FrameAnalysisEngine.solveFrame(
            listOf(FrameNode(1, 0.0, 0.0, SupportType.Fixed)),
            listOf(FrameMember(1, 1, 1, FrameMaterialType.Concrete, FrameMemberType.Beam, ConcreteSectionProps(300.0, 500.0))),
            emptyList(), emptyList(), null as FrameAnalysisSettings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 11. Frame result integrity
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_portalFrame_resultHasValidNodeIds() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings)
        if (result.isSolved) {
            val resultNodeIds = result.nodeResults.map { it.nodeId }.sorted()
            val inputNodeIds = nodes.map { it.id }.sorted()
            assertEquals("Result node IDs should match input", inputNodeIds, resultNodeIds)
        }
    }

    @Test
    fun solveFrame_portalFrame_hasResultsProperty() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings)
        if (result.isSolved && result.errorMessage == null) {
            assertTrue("hasResults should be true", result.hasResults)
        }
    }

    @Test
    fun solveFrame_portalFrame_allResultsFinite() {
        // C1: no NaN/Infinity may leak into any output
        val (nodes, members, settings) = createSimplePortalFrame()
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val memberLoads = listOf(MemberLoad(2, MemberLoadType.UDL, 20.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, loads, memberLoads, settings)
        assertTrue(result.isSolved)
        result.nodeResults.forEach { nr ->
            assertTrue("node ${nr.nodeId} results must be finite",
                nr.dx.isFinite() && nr.dy.isFinite() && nr.rz.isFinite() &&
                nr.reactionFx.isFinite() && nr.reactionFy.isFinite() && nr.reactionMz.isFinite())
        }
        result.memberDiagrams.forEach { d ->
            d.momentDiagram.forEach { assertTrue("moment must be finite", it.value.isFinite()) }
            d.shearDiagram.forEach { assertTrue("shear must be finite", it.value.isFinite()) }
            d.axialDiagram.forEach { assertTrue("axial must be finite", it.value.isFinite()) }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 12. C3 — Isolated / disconnected nodes
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_isolatedFreeNode_returnsClearError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val withIsolated = nodes + FrameNode(99, 10.0, 10.0, SupportType.Free) // غير متصلة
        val result = FrameAnalysisEngine.solveFrame(withIsolated, members, emptyList(), emptyList(), settings)
        assertFalse("Isolated free node must fail", result.isSolved)
        assertNotNull(result.errorMessage)
        assertTrue("Error must name the isolated node #99, got: ${result.errorMessage}",
            result.errorMessage!!.contains("99"))
    }

    @Test
    fun solveFrame_isolatedFixedNode_isHarmless() {
        // عقدة مثبتة تثبيتاً كاملاً وغير متصلة: كل درجاتها مقيّدة → لا شذوذ
        val (nodes, members, settings) = createSimplePortalFrame()
        val withIsolated = nodes + FrameNode(99, 10.0, 10.0, SupportType.Fixed)
        val loads = listOf(NodalLoad(2, fx = -50.0, fy = 0.0, mz = 0.0))
        val result = FrameAnalysisEngine.solveFrame(withIsolated, members, loads, emptyList(), settings)
        assertTrue("Isolated FIXED node must not break the solve: ${result.errorMessage}", result.isSolved)
    }

    @Test
    fun solveFrame_componentWithoutSupport_returnsMechanismError() {
        // كمرية معلقة حرة تماماً بجانب إطار سليم → مكوّن غير مرتكز
        val (nodes, members, settings) = createSimplePortalFrame()
        val floatingBeamNodes = listOf(
            FrameNode(50, 20.0, 0.0, SupportType.Free),
            FrameNode(51, 26.0, 0.0, SupportType.Free)
        )
        val floatingMember = FrameMember(10, 50, 51, FrameMaterialType.Concrete, FrameMemberType.Beam,
            ConcreteSectionProps(250.0, 500.0))
        val result = FrameAnalysisEngine.solveFrame(
            nodes + floatingBeamNodes, members + floatingMember, emptyList(), emptyList(), settings
        )
        assertFalse("Unsupported floating component must fail", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun solveFrame_noSupportsAtAll_returnsMechanismError() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Free),
            FrameNode(2, 6.0, 0.0, SupportType.Free)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, ConcreteSectionProps(300.0, 500.0))
        )
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), emptyList(), FrameAnalysisSettings())
        assertFalse("Unsupported structure must fail", result.isSolved)
        assertNotNull("Should get mechanism/connectivity error", result.errorMessage)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 13. C4 — Member reference validation
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_memberWithMissingNode_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val bad = FrameMember(77, 1, 404, FrameMaterialType.Concrete, FrameMemberType.Beam,
            ConcreteSectionProps(250.0, 500.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members + bad, emptyList(), emptyList(), settings)
        assertFalse(result.isSolved)
        assertNotNull(result.errorMessage)
        assertTrue("Error must mention member #77, got: ${result.errorMessage}",
            result.errorMessage!!.contains("77"))
    }

    @Test
    fun solveFrame_zeroLengthMember_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val dupNode = FrameNode(5, 0.0, 0.0, SupportType.Free) // نفس إحداثيات العقدة 1
        val zeroLen = FrameMember(78, 1, 5, FrameMaterialType.Concrete, FrameMemberType.Beam,
            ConcreteSectionProps(250.0, 500.0))
        val result = FrameAnalysisEngine.solveFrame(nodes + dupNode, members + zeroLen, emptyList(), emptyList(), settings)
        assertFalse("Zero-length member must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun solveFrame_duplicateNodeIds_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val dup = FrameNode(1, 12.0, 0.0, SupportType.Fixed) // معرف مكرر
        val result = FrameAnalysisEngine.solveFrame(nodes + dup, members, emptyList(), emptyList(), settings)
        assertFalse("Duplicate node ID must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 14. C5 — Load & section validation
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_pointLoadPositionOutOfRange_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val badLoad = MemberLoad(2, MemberLoadType.PointLoad, 10.0, 99.0) // خارج الطول
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), listOf(badLoad), settings)
        assertFalse("Out-of-range load position must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun solveFrame_nanLoadValue_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val badLoad = MemberLoad(2, MemberLoadType.UDL, Double.NaN, 0.0)
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), listOf(badLoad), settings)
        assertFalse("NaN load must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun solveFrame_nodalLoadOnMissingNode_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val badLoad = NodalLoad(404, fx = 0.0, fy = -10.0, mz = 0.0)
        val result = FrameAnalysisEngine.solveFrame(nodes, members, listOf(badLoad), emptyList(), settings)
        assertFalse("Nodal load on missing node must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun solveFrame_invalidConcreteSection_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val bad = FrameMember(80, 2, 3, FrameMaterialType.Concrete, FrameMemberType.Beam,
            ConcreteSectionProps(0.0, 500.0)) // b = 0
        val result = FrameAnalysisEngine.solveFrame(nodes, members + bad, emptyList(), emptyList(), settings)
        assertFalse("Zero-width section must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun solveFrame_unknownSteelSection_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val steel = FrameMember(81, 2, 3, FrameMaterialType.Steel, FrameMemberType.Beam,
            steelSectionName = "NOT_A_SECTION")
        val result = FrameAnalysisEngine.solveFrame(nodes, members + steel, emptyList(), emptyList(), settings)
        assertFalse("Unknown steel section must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun solveFrame_nanNodeCoordinate_returnsError() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val badNode = FrameNode(60, Double.NaN, 5.0, SupportType.Free)
        val result = FrameAnalysisEngine.solveFrame(nodes + badNode, members, emptyList(), emptyList(), settings)
        assertFalse("NaN coordinates must be rejected", result.isSolved)
        assertNotNull(result.errorMessage)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 15. Point load at member end — must NOT be dropped (S3 clamp)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_pointLoadAtMemberEnd_transfersToSupportNotDropped() {
        // P = 40 kN عند العقدة 1 (بداية الكمرية المفصلية) → يذهب للارتكاز مباشرة
        val (nodes, members, settings) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.PointLoad, 40.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)
        // الحمل لا يُهمل: مجموع ردود الأفعال الرأسية = 40
        assertEquals("Point load at end must transfer to support (ΣRy = P)",
            40.0, result.nodeResults.sumOf { it.reactionFy }, 0.05)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 16. Steel member — E units fix (eSteel is already kN/m²)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_steelPortalFrame_solvesAndBalances() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 0.0, 4.0, SupportType.Free),
            FrameNode(3, 6.0, 4.0, SupportType.Free),
            FrameNode(4, 6.0, 0.0, SupportType.Fixed)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Steel, FrameMemberType.Column, steelSectionName = "IPE 300"),
            FrameMember(2, 2, 3, FrameMaterialType.Steel, FrameMemberType.Beam, steelSectionName = "IPE 300"),
            FrameMember(3, 4, 3, FrameMaterialType.Steel, FrameMemberType.Column, steelSectionName = "IPE 300")
        )
        val memberLoads = listOf(MemberLoad(2, MemberLoadType.UDL, 15.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, FrameAnalysisSettings())
        assertTrue("Steel portal should solve: ${result.errorMessage}", result.isSolved)
        assertEquals("ΣRy = wL = 90 kN", 90.0, result.nodeResults.sumOf { it.reactionFy }, 0.05)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C6: Fully-restrained models (zero free DOFs) must solve
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_fullyRestrainedPortal_udl_solvesWithFixedEndForces() {
        // إطار سقلي مثبت بالكامل — كل العقد Fixed
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 0.0, 4.0, SupportType.Fixed),
            FrameNode(3, 6.0, 4.0, SupportType.Fixed),
            FrameNode(4, 6.0, 0.0, SupportType.Fixed)
        )
        val colSec = ConcreteSectionProps(300.0, 300.0, 25.0, 400.0, 50.0)
        val beamSec = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Column, colSec),
            FrameMember(2, 2, 3, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSec),
            FrameMember(3, 4, 3, FrameMaterialType.Concrete, FrameMemberType.Column, colSec)
        )
        val memberLoads = listOf(MemberLoad(2, MemberLoadType.UDL, 20.0, 0.0))

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, FrameAnalysisSettings())
        assertTrue("Fully restrained frame must solve (C6): ${result.errorMessage}", result.isSolved)

        // كل الإزاحات صفر
        result.nodeResults.forEach {
            assertEquals("dx=0 at node ${it.nodeId}", 0.0, it.dx, 1e-12)
            assertEquals("dy=0 at node ${it.nodeId}", 0.0, it.dy, 1e-12)
        }
        // قوى نهايات الكمر = -FEF: |m| = wL²/12 = 60
        val beamForces = result.memberEndForces.first { it.memberId == 2 }
        assertEquals("|m_i| = wL²/12 = 60", 60.0, kotlin.math.abs(beamForces.mi_z), 0.01)
        assertEquals("|m_j| = wL²/12 = 60", 60.0, kotlin.math.abs(beamForces.mj_z), 0.01)
        // ΣRy = wL = 120
        assertEquals("ΣRy = wL = 120", 120.0, result.nodeResults.sumOf { it.reactionFy }, 0.05)
    }

    @Test
    fun solveFrame_fullyRestrained_nodalLoadGoesStraightToReactions() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam,
                ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0))
        )
        val nodalLoads = listOf(NodalLoad(2, fx = 0.0, fy = -50.0, mz = 0.0))

        val result = FrameAnalysisEngine.solveFrame(nodes, members, nodalLoads, emptyList(), FrameAnalysisSettings())
        assertTrue("Should solve (C6): ${result.errorMessage}", result.isSolved)
        val n2 = result.nodeResults.first { it.nodeId == 2 }
        assertEquals("Ry at loaded fixed node = +50", 50.0, n2.reactionFy, 0.01)
        assertEquals("ΣRy = 50", 50.0, result.nodeResults.sumOf { it.reactionFy }, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C7: Critical-section sampling captures off-grid peaks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_offGridPointLoad_maxMomentIsExact() {
        // P = 30 kN عند a = 2.2 m على كمرية بسيطة L = 6:
        //   M_max = P·a·b/L = 30 × 2.2 × 3.8 / 6 = 41.8 kN.m
        val (nodes, members, settings) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.PointLoad, 30.0, 2.2))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)

        val d = result.memberDiagrams[0]
        assertEquals("maxMoment must capture the exact peak P·a·b/L (C7)",
            41.8, d.maxMoment, 0.01)
        val atA = d.momentDiagram.firstOrNull { kotlin.math.abs(it.x - 2.2) < 1e-6 }
        assertNotNull("A sample must exist at x = a (C7)", atA)
        assertEquals("M(a) = P·a·b/L", 41.8, atA!!.value, 0.01)
    }

    @Test
    fun solveFrame_offGridPointLoad_shearJumpTwoSidedCaptured() {
        val (nodes, members, settings) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.PointLoad, 30.0, 2.2))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)

        val shear = result.memberDiagrams[0].shearDiagram
        val before = shear.filter { it.x < 2.2 - 1e-8 }.maxByOrNull { it.x }
        val after = shear.filter { it.x > 2.2 + 1e-8 }.minByOrNull { it.x }
        assertNotNull(before); assertNotNull(after)
        assertEquals("V(a⁻) = P·b/L = +19 (C7 two-sided)", 19.0, before!!.value, 0.05)
        assertEquals("V(a⁺) = -P·a/L = -11 (C7 two-sided)", -11.0, after!!.value, 0.05)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C8: Global equilibrium residuals exposed and ~zero
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_portalWithUdlAndWind_globalEquilibriumResidualsNearZero() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val memberLoads = listOf(MemberLoad(2, MemberLoadType.UDL, 18.0, 0.0, loadCase = "DL"))
        val nodalLoads = listOf(NodalLoad(2, fx = 25.0, fy = 0.0, mz = 0.0, loadCase = "WL"))

        val result = FrameAnalysisEngine.solveFrame(nodes, members, nodalLoads, memberLoads, settings)
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)

        assertEquals("ΣFx residual ≈ 0 (C8)", 0.0, result.equilibriumFx, 1e-6)
        assertEquals("ΣFy residual ≈ 0 (C8)", 0.0, result.equilibriumFy, 1e-6)
        assertEquals("ΣMz residual ≈ 0 (C8)", 0.0, result.equilibriumMz, 1e-6)
    }

    @Test
    fun solveFrame_fullyRestrained_equilibriumAlsoHolds() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam,
                ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0))
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 12.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, FrameAnalysisSettings())
        assertTrue(result.isSolved)
        assertEquals("ΣFy residual ≈ 0 (C8 on zero-DOF path)", 0.0, result.equilibriumFy, 1e-6)
        assertEquals("ΣMz residual ≈ 0 (C8 on zero-DOF path)", 0.0, result.equilibriumMz, 1e-6)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C9: Load combination envelope
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_loadCombinationEnvelope_governingCombinationAndMaxMoment() {
        // كمرية بسيطة: DL = 10, LL = 20 على L = 6
        // التوفيق الافتراضي الأعلى عزماً: 1.4DL+1.6LL → w = 14 + 32 = 46
        //   M_max = wL²/8 = 46 × 36 / 8 = 207 kN.m
        val (nodes, members, _) = simplySupportedBeam()
        val settings = FrameAnalysisSettings(
            loadCombinations = listOf(
                LoadCombinationFrame("1.4DL+1.6LL", 1.4, 1.6, 0.0),
                LoadCombinationFrame("1.0DL+1.0LL", 1.0, 1.0, 0.0)
            )
        )
        val memberLoads = listOf(
            MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0, loadCase = "DL"),
            MemberLoad(1, MemberLoadType.UDL, 20.0, 0.0, loadCase = "LL")
        )

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)

        val env = result.loadEnvelopes
        assertTrue("Envelope must be computed when loads exist (C9)", env.isNotEmpty())
        val e1 = env.first { it.memberId == 1 }
        assertEquals("Governing combination is 1.4DL+1.6LL (C9)",
            "1.4DL+1.6LL", e1.governingCombination)
        assertEquals("Envelope maxMoment = wL²/8 = 207 (C9)", 207.0, e1.maxMoment, 0.5)
        assertEquals("Envelope minMoment = 0 (C9)", 0.0, e1.minMoment, 0.01)
        assertEquals("Envelope maxShear = wL/2 = 138 (C9)", 138.0, e1.maxShear, 0.5)
    }

    @Test
    fun solveFrame_noCombinationsConfigured_envelopeEmpty() {
        val (nodes, members, _) = simplySupportedBeam()
        val settings = FrameAnalysisSettings(loadCombinations = emptyList())
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)
        assertTrue("No combos → empty envelope (C9)", result.loadEnvelopes.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C10: Self-weight option
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_includeSelfWeight_addsUdlAndIncreasesReactions() {
        // مقطع 300×500 → w_sw = 25 × 0.3 × 0.5 = 3.75 kN/m
        val (nodes, members, _) = simplySupportedBeam()
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))

        val without = FrameAnalysisEngine.solveFrame(
            nodes, members, emptyList(), memberLoads, FrameAnalysisSettings(includeSelfWeight = false))
        assertTrue(without.isSolved)
        assertEquals("ΣRy without self-weight = wL = 60", 60.0,
            without.nodeResults.sumOf { it.reactionFy }, 0.05)

        val with = FrameAnalysisEngine.solveFrame(
            nodes, members, emptyList(), memberLoads, FrameAnalysisSettings(includeSelfWeight = true))
        assertTrue(with.isSolved)
        assertEquals("ΣRy with self-weight = (w+γbh)L = 82.5 (C10)", 82.5,
            with.nodeResults.sumOf { it.reactionFy }, 0.05)
    }

    @Test
    fun solveFrame_selfWeight_scalesWithDeadLoadFactorInEnvelope() {
        val (nodes, members, _) = simplySupportedBeam()
        val settings = FrameAnalysisSettings(
            includeSelfWeight = true,
            loadCombinations = listOf(LoadCombinationFrame("1.5DL", 1.5, 0.0, 0.0))
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0, loadCase = "DL"))

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue(result.isSolved)
        val e1 = result.loadEnvelopes.first { it.memberId == 1 }
        assertEquals("Self-weight scales with DL factor (C9+C10)", 92.8125, e1.maxMoment, 0.5)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C11: E from fcu (units audit)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun getMemberStiffnessProps_concreteUnitsAreConsistent() {
        // Ec = 4700·√fcu MPa = 4700×5×10³ = 2.35e7 kN/m² لـ fcu = 25
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Pin)
        )
        val member = FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam,
            ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0))
        val props = FrameAnalysisEngine.getMemberStiffnessProps(member, nodes, FrameAnalysisSettings())

        assertNotNull("Props must be computed for a valid member (C11)", props)
        assertEquals("E = 4700·√fcu·10³ kN/m² (C11)", 2.35e7, props!!.E, 1.0)
        assertEquals("A = b·h m² (C11)", 0.15, props.A, 1e-9)
        assertEquals("I cracked = 0.35·Ig (C11)", 0.35 * 3.125e-3, props.I, 1e-9)
        assertEquals("Ig gross (C11)", 3.125e-3, props.Ig, 1e-9)
        assertEquals("L = 6 m (C11)", 6.0, props.L, 1e-12)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C12: Axial force tension-positive
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_axialTensionMember_positiveAxialDiagram() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Free)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam,
                ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0))
        )
        val nodalLoads = listOf(NodalLoad(2, fx = 100.0, fy = 0.0, mz = 0.0))

        val result = FrameAnalysisEngine.solveFrame(nodes, members, nodalLoads, emptyList(), FrameAnalysisSettings())
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)

        val axial = result.memberDiagrams[0].axialDiagram
        assertTrue("Axial diagram must have samples (C12)", axial.isNotEmpty())
        axial.forEach {
            assertEquals("Tension must be positive N = +100 (C12)", 100.0, it.value, 0.01)
        }
        assertEquals("Rx = -100 at fixed end", -100.0,
            result.nodeResults.sumOf { it.reactionFx }, 0.01)
    }

    @Test
    fun solveFrame_axialCompressionMember_negativeAxialDiagram() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Free)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Column,
                ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0))
        )
        val nodalLoads = listOf(NodalLoad(2, fx = -80.0, fy = 0.0, mz = 0.0))

        val result = FrameAnalysisEngine.solveFrame(nodes, members, nodalLoads, emptyList(), FrameAnalysisSettings())
        assertTrue(result.isSolved)

        val axial = result.memberDiagrams[0].axialDiagram
        axial.forEach {
            assertEquals("Compression must be negative N = -80 (C12)", -80.0, it.value, 0.01)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE 2 — C13: End forces ↔ diagrams consistency
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_endForcesMatchDiagramStartValues() {
        val (nodes, members, settings) = createSimplePortalFrame()
        val memberLoads = listOf(
            MemberLoad(2, MemberLoadType.UDL, 15.0, 0.0),
            MemberLoad(1, MemberLoadType.PointLoad, 8.0, 2.0)
        )
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)

        // الكمر الأفقي (member 2): fi_y العالمي = المحلي → V(0) = fi_y و M(0) = -mi_z
        val beamF = result.memberEndForces.first { it.memberId == 2 }
        val beamD = result.memberDiagrams.first { it.memberId == 2 }
        assertEquals("V(0) = fi_y (C13)", beamF.fi_y, beamD.shearDiagram.first().value, 0.01)
        assertEquals("M(0) = -mi_z (C13)", -beamF.mi_z, beamD.momentDiagram.first().value, 0.01)

        // V(L) = v_i - wL ، M(L) = +mj_z
        val w = 15.0; val L = 6.0
        assertEquals("V(L) = v_i - wL = -fj_y (C13)",
            beamF.fi_y - w * L, beamD.shearDiagram.last().value, 0.05)
        assertEquals("M(L) = +mj_z (C13)",
            beamF.mj_z, beamD.momentDiagram.last().value, 0.05)
    }

    @Test
    fun solveFrame_fixedFixedBeam_endForcesMatchDiagramBothEnds() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam,
                ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0))
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, FrameAnalysisSettings())
        assertTrue(result.isSolved)

        val f = result.memberEndForces[0]
        val d = result.memberDiagrams[0]
        assertEquals("M(0) = -mi_z (C13)", -f.mi_z, d.momentDiagram.first().value, 0.01)
        assertEquals("M(L) = +mj_z (C13)", f.mj_z, d.momentDiagram.last().value, 0.01)
        assertEquals("V(0) = +wL/2 (C13)", 30.0, d.shearDiagram.first().value, 0.01)
        assertEquals("V(L) = -wL/2 (C13)", -30.0, d.shearDiagram.last().value, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════
    // 17. Member end releases (Frame Modeler) — condensation path
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_memberEndReleases_produceSimplySupportedBeam() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Roller)
        )
        val member = FrameMember(
            id = 1, nodeI = 1, nodeJ = 2,
            materialType = FrameMaterialType.Concrete,
            memberType = FrameMemberType.Beam,
            concreteSection = ConcreteSectionProps(300.0, 500.0),
            releaseI = true, releaseJ = true
        )
        val loads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, loadCase = "DL"))

        val result = FrameAnalysisEngine.solveFrame(
            nodes, listOf(member), emptyList(), loads, FrameAnalysisSettings()
        )

        assertTrue("Released beam should solve: ${result.errorMessage}", result.isSolved)
        val forces = result.memberEndForces.single()
        assertEquals(0.0, forces.mi_z, 1e-6)
        assertEquals(0.0, forces.mj_z, 1e-6)
        assertEquals(30.0, forces.fi_y, 1e-6)
        assertEquals(45.0, result.memberDiagrams.single().maxMoment, 0.2)
    }

    @Test
    fun solveFrame_singleEndRelease_transfersZeroMomentAtReleasedEnd() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Free)
        )
        val member = FrameMember(
            id = 1, nodeI = 1, nodeJ = 2,
            materialType = FrameMaterialType.Concrete,
            memberType = FrameMemberType.Beam,
            concreteSection = ConcreteSectionProps(300.0, 500.0),
            releaseJ = true
        )
        val loads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, loadCase = "DL"))

        val result = FrameAnalysisEngine.solveFrame(
            nodes, listOf(member), emptyList(), loads, FrameAnalysisSettings()
        )

        assertTrue("Cantilever with tip hinge should solve: ${result.errorMessage}", result.isSolved)
        assertEquals(0.0, result.memberEndForces.single().mj_z, 1e-6)
    }
    @Test
    fun getMemberStiffnessProps_steelUnitsAreConsistent() {
        // eSteel = 200e6 kN/m² كما هي (كان المحرك يضربها ×1e3 خطأً وحداتياً)
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Pin)
        )
        val member = FrameMember(1, 1, 2, FrameMaterialType.Steel, FrameMemberType.Beam,
            steelSectionName = "IPE 300")
        val props = FrameAnalysisEngine.getMemberStiffnessProps(member, nodes, FrameAnalysisSettings())

        assertNotNull("Props must be computed for a valid steel member (C11)", props)
        assertEquals("E steel = eSteel = 2e8 kN/m² without ×1e3 (C11)", 2.0e8, props!!.E, 1.0)
        assertEquals("A = 53.8 cm² = 5.38e-3 m² (C11)", 5.38e-3, props.A, 1e-9)
        assertEquals("I = iy = 8356 cm⁴ = 8.356e-5 m⁴ (C11)", 8.356e-5, props.I, 1e-10)
    }

    @Test
    fun solveFrame_selfWeightOnVerticalColumn_isGravityNotHorizontal() {
        // C10: عمود رأسي 4 م، 300×600 خرسانة → w_sw = 25×0.3×0.6 = 4.5 kN/m
        // الوزن الذاتي ثقلي (رأسي): ΣRy = w·L = 18 kN و ΣRx = 0.
        // (تطبيق UDL عرضي على عمود رأسي — كما في المسودة القديمة — يعطي
        //  حملاً أفقياً 18 kN و ΣRy = 0: خطأ فيزيائي يكشفه هذا الاختبار)
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 0.0, 4.0, SupportType.Free)
        )
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Column,
                ConcreteSectionProps(300.0, 600.0))
        )
        val settings = FrameAnalysisSettings(includeSelfWeight = true, loadCombinations = emptyList())

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), emptyList(), settings)
        assertTrue("Should solve: ${result.errorMessage}", result.isSolved)

        assertEquals("ΣRy = self-weight w·L = 18 kN (gravity, C10)", 18.0,
            result.nodeResults.sumOf { it.reactionFy }, 0.05)
        assertEquals("ΣRx = 0 — no horizontal load from self-weight (C10)", 0.0,
            result.nodeResults.sumOf { kotlin.math.abs(it.reactionFx) }, 1e-9)

        // المخطط المحوري: ضغط فقط (الوزن الذاتي لا يولّد شدًّا)
        result.memberDiagrams.single().axialDiagram.forEach {
            assertTrue("Column self-weight must be compression (N ≤ 0), got ${it.value}", it.value <= 0.01)
        }
    }

}
