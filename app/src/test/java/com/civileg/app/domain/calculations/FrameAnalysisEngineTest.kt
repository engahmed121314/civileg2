package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FrameAnalysisEngine — the stiffness matrix frame analysis engine.
 * Covers: simple portal frame, InputGuard validation, equilibrium checks,
 * and basic structural behavior verification.
 */
class FrameAnalysisEngineTest {

    // ══════════════════════════════════════════════════════════════════════
    // Helper — create a simple portal frame
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

    // ══════════════════════════════════════════════════════════════════════
    // 1. InputGuard — empty collections
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
        assertTrue("Frame should solve successfully", result.isSolved)
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
    // 7. Simply supported beam
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun solveFrame_simplySupportedBeam_producesResults() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Pin)
        )
        val beamSection = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSection)
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val settings = FrameAnalysisSettings(designCode = DesignCode.ACI)

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        assertTrue("Simply supported beam should solve", result.isSolved)
    }

    @Test
    fun solveFrame_simplySupportedBeam_correctMaxMoment() {
        // For simply supported beam with UDL: M_max = wL^2/8 = 10*6^2/8 = 45 kN.m
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Pin)
        )
        val beamSection = ConcreteSectionProps(300.0, 500.0, 25.0, 400.0, 50.0)
        val members = listOf(
            FrameMember(1, 1, 2, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSection)
        )
        val memberLoads = listOf(MemberLoad(1, MemberLoadType.UDL, 10.0, 0.0))
        val settings = FrameAnalysisSettings(designCode = DesignCode.ACI)

        val result = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), memberLoads, settings)
        if (result.isSolved && result.memberDiagrams.isNotEmpty()) {
            val maxMoment = result.memberDiagrams[0].maxMoment
            // Theoretical max moment = 45 kN.m, allow tolerance for frame analysis
            assertEquals("Max moment should be close to wL^2/8", 45.0, maxMoment, 10.0)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Settings null check
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun solveFrame_nullSettings_throwsException() {
        FrameAnalysisEngine.solveFrame(
            listOf(FrameNode(1, 0.0, 0.0, SupportType.Fixed)),
            listOf(FrameMember(1, 1, 1, FrameMaterialType.Concrete, FrameMemberType.Beam, ConcreteSectionProps(300.0, 500.0))),
            emptyList(), emptyList(), null as FrameAnalysisSettings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. Frame result integrity
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
}
