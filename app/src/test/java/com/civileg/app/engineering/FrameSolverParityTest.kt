package com.civileg.app.engineering

import com.civileg.app.domain.calculations.FrameAnalysisEngine
import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.FrameAnalysisSettings
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.utils.ContinuousBeamAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * PHASE 00 STEP 1 — Analytic parity tests for FrameAnalysisEngine and ContinuousBeamAnalysis.
 *
 * Expected values are textbook closed-form solutions computed independently of the code.
 *
 * DEFECT REFERENCES (PHASE00_AUDIT.md Addendum A):
 *  A1 — member-load equivalent nodal loads assembled with inverted sign
 *       (getFixedEndForces returns the work-equivalent load vector; assembly subtracts it again).
 *       A1/CBA-1 fixed: P_eq table unified and reconstruction sign flipped (P021).
 */
class FrameSolverParityTest {

    private val section = ConcreteSectionProps(width = 300.0, depth = 600.0, fcu = 25.0)
    // E = 4700*sqrt(25)*1e3 = 23.5e6 kN/m² ; I = 0.3*0.6³/12 = 5.4e-3 m⁴ ; A = 0.18 m²
    private val EI = 23.5e6 * 5.4e-3 // = 126900 kN.m²

    private fun settings() = FrameAnalysisSettings()

    // ------------------------------------------------------------------
    // Cantilever with TIP NODAL LOAD — bypasses FEF path entirely,
    // validates stiffness assembly / solve / recovery core.
    // ------------------------------------------------------------------

    @Test
    fun cantilever_tipNodalLoad_matchesClosedForm() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Free)
        )
        val members = listOf(
            FrameMember(1, 1, 2, materialType = FrameMaterialType.Concrete, concreteSection = section)
        )
        val loads = listOf(NodalLoad(nodeId = 2, fy = -100.0))
        val r = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings())

        assertTrue("solver failed: ${r.errorMessage}", r.isSolved)

        val tip = r.nodeResults.first { it.nodeId == 2 }
        // delta = PL³/3EI = 100*216/(3*126900) = 0.056738 m downward
        assertEquals(-0.056738, tip.dy, 1e-4)
        // theta = PL²/2EI = 3600/253800 = 0.014185 rad clockwise -> negative CCW
        assertEquals(-0.014185, tip.rz, 1e-5)

        val wall = r.nodeResults.first { it.nodeId == 1 }
        assertEquals(+100.0, wall.reactionFy, 0.01)
        // Moment equilibrium about wall: M_R + (-P*L) = 0 -> M_R = +600 kN.m CCW
        assertEquals(+600.0, wall.reactionMz, 0.5)

        val mf = r.memberEndForces.single()
        assertEquals(600.0, Math.abs(mf.mi_z), 0.5)
        assertEquals(100.0, Math.abs(mf.fi_y), 0.05)
        // mj_z must vanish at the free tip (k*u recovery closes the member)
        assertTrue("tip end moment must be ~0, got ${mf.mj_z}", Math.abs(mf.mj_z) < 0.1)
    }

    /**
     * DEFECT D-1 (fixed): diagram now interpolates M(x)=mI(1-t)+mJ*t+M_free(x),
     * satisfying both end boundary conditions.
     */
    @Test
    fun cantilever_momentDiagram_decaysToZeroAtTip() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Free)
        )
        val members = listOf(
            FrameMember(1, 1, 2, materialType = FrameMaterialType.Concrete, concreteSection = section)
        )
        val r = FrameAnalysisEngine.solveFrame(
            nodes, members, listOf(NodalLoad(nodeId = 2, fy = -100.0)), emptyList(), settings()
        )
        val d = r.memberDiagrams.single()
        assertEquals(600.0, d.maxMoment, 0.5)
        val tipM = d.momentDiagram.last().value
        assertTrue("moment at free tip must be ~0, got $tipM", kotlin.math.abs(tipM) < 0.5)
    }

    // ------------------------------------------------------------------
    // Pure axial bar — nodal load along member axis.
    // ------------------------------------------------------------------

    @Test
    fun axialBar_displacementAndReaction_matchEA_over_L() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),           // restrains dx & dy
            FrameNode(2, 4.0, 0.0, SupportType.Roller)         // restrains dy only -> free in x
        )
        val members = listOf(
            FrameMember(1, 1, 2, materialType = FrameMaterialType.Concrete, concreteSection = section)
        )
        val loads = listOf(NodalLoad(nodeId = 2, fx = -500.0))
        val r = FrameAnalysisEngine.solveFrame(nodes, members, loads, emptyList(), settings())

        assertTrue("solver failed: ${r.errorMessage}", r.isSolved)

        val free = r.nodeResults.first { it.nodeId == 2 }
        // delta = PL/EA = 500*4/(23.5e6*0.18) = 4.7281e-4 m (compression, negative x)
        assertEquals(-4.7281e-4, free.dx, 1e-7)
        // node2 is a roller: NO horizontal reaction
        assertEquals(0.0, free.reactionFx, 1e-9)

        // horizontal equilibrium: pin at node1 takes the full load
        val wall = r.nodeResults.first { it.nodeId == 1 }
        assertEquals(+500.0, wall.reactionFx, 0.05)

        val mf = r.memberEndForces.single()
        assertEquals(500.0, Math.abs(mf.axialForce), 0.1)
    }

    // ------------------------------------------------------------------
    // Simply-supported beam with MEMBER UDL — exercises FEF path.
    // KNOWN DEFECT A1: expected values below are the correct engineering
    // answers; engine currently returns inverted-load results.
    // ------------------------------------------------------------------

    @Test
    fun ssBeam_udl_reactionsShearMoment_matchTextbook() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Roller)
        )
        val members = listOf(
            FrameMember(1, 1, 2, materialType = FrameMaterialType.Concrete, concreteSection = section)
        )
        val udl = MemberLoad(memberId = 1, loadType = MemberLoadType.UDL, value = 10.0)
        val r = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), listOf(udl), settings())

        assertTrue("solver failed: ${r.errorMessage}", r.isSolved)

        val reactions = r.nodeResults.map { it.reactionFy }.sortedDescending()
        assertEquals(30.0, reactions[0], 0.05)   // wL/2 each
        assertEquals(30.0, reactions[1], 0.05)

        val mf = r.memberEndForces.single()
        assertEquals(30.0, mf.maxShear, 0.05)
        assertTrue("end moments of SS beam must vanish, got ${mf.mi_z}, ${mf.mj_z}",
            Math.abs(mf.mi_z) < 0.1 && Math.abs(mf.mj_z) < 0.1)

        // M_max = wL²/8 = 45 kN.m at midspan ; delta_max = 5wL⁴/384EI = 1.33 mm
        assertEquals(45.0, r.memberDiagrams.single().maxMoment, 0.25)
    }

    @Test
    fun ssBeam_midspanPointLoad_reactionsAndMoment_matchTextbook() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Roller)
        )
        val members = listOf(
            FrameMember(1, 1, 2, materialType = FrameMaterialType.Concrete, concreteSection = section)
        )
        val point = MemberLoad(memberId = 1, loadType = MemberLoadType.PointLoad, value = 80.0, position = 3.0)
        val r = FrameAnalysisEngine.solveFrame(nodes, members, emptyList(), listOf(point), settings())

        assertTrue("solver failed: ${r.errorMessage}", r.isSolved)
        val reactions = r.nodeResults.map { it.reactionFy }.sortedDescending()
        assertEquals(40.0, reactions[0], 0.05)
        assertEquals(40.0, reactions[1], 0.05)
        // M_max = PL/4 = 120 kN.m
        assertEquals(120.0, r.memberDiagrams.single().maxMoment, 0.5)
    }

    // ------------------------------------------------------------------
    // ContinuousBeamAnalysis (three-moment) — two equal spans, UDL.
    // NEW DEFECT CBA-1: support moments are correct (-wL²/8) but shear /
    // reaction reconstruction uses (M_left - M_right)/L instead of
    // (M_right - M_left)/L -> wrong distribution (37.5/45/37.5 vs 22.5/75/22.5).
    // ------------------------------------------------------------------

    @Test
    fun continuousBeam_twoEqualSpans_classicResults() {
        val result = ContinuousBeamAnalysis().solve(
            listOf(
                ContinuousBeamAnalysis.Span(length = 6.0, load = 10.0),
                ContinuousBeamAnalysis.Span(length = 6.0, load = 10.0)
            )
        )
        // Support moments: M_B = -wL²/8 = -45 kN.m ; ends pinned = 0
        assertEquals(0.0, result.moments[0], 1e-6)
        assertEquals(-45.0, result.moments[1], 1e-3)
        assertEquals(0.0, result.moments[2], 1e-6)
        // Reactions: ends 3wL/8 = 22.5 ; interior 10wL/8 = 75
        assertEquals(22.5, result.reactions[0], 0.05)
        assertEquals(75.0, result.reactions[1], 0.05)
        assertEquals(22.5, result.reactions[2], 0.05)
        // Max span shear = 3wL/8 at end supports, interior 5wL/8? -> V_max interior span edge = 62.5;
        // engine reports per-span max(|V_left|,|V_right|): span1 = max(22.5, 62.5)=62.5
        assertEquals(37.5, result.shearForces[0], 0.05) // max(|22.5|,|37.5|) at B edge
        // Midspan sagging moment span 1 = wL²/16 = 22.5 kN.m
        val midSpan1 = result.points.first { abs(it.x - 3.0) < 0.07 }
        assertEquals(22.5, midSpan1.moment, 0.1)
    }

    @Test
    fun continuousBeam_singleSpan_behavesAsSimplySupported() {
        val result = ContinuousBeamAnalysis().solve(listOf(ContinuousBeamAnalysis.Span(6.0, 10.0)))
        assertEquals(30.0, result.reactions[0], 1e-9)
        assertEquals(30.0, result.reactions[1], 1e-9)
        val mid = result.points.first { abs(it.x - 3.0) < 0.07 }
        assertEquals(45.0, mid.moment, 1e-6)
    }

    private fun abs(v: Double) = kotlin.math.abs(v)
}
