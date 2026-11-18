package com.civileg.app.domain.calculations.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for InteractionDiagramSolver (P-M Interaction Diagram solver).
 */
class InteractionDiagramTest {

    private fun defaultSection() = RectangularSection(width = 300.0, depth = 600.0)

    private fun defaultReinforcement() = InteractionReinforcementInput(
        totalArea = 2000.0,
        distributionType = "Uniform",
        cover = 40.0,
        numBarsPerFace = 3
    )

    @Test
    fun solvePnMn_validInputs_producesInteractionPoint() {
        val point = InteractionDiagramSolver.solvePnMn(
            fcu = 30.0,
            fy = 400.0,
            section = defaultSection(),
            reinforcement = defaultReinforcement(),
            c = 300.0
        )
        assertNotNull(point)
        assertTrue("Pn should be positive for compression", point.P > 0)
        assertTrue("Mn should be non-negative", point.M >= 0)
    }

    @Test
    fun solvePnMn_largerNeutralAxis_increasesAxialCapacity() {
        val smallC = InteractionDiagramSolver.solvePnMn(30.0, 400.0, defaultSection(), defaultReinforcement(), c = 150.0)
        val largeC = InteractionDiagramSolver.solvePnMn(30.0, 400.0, defaultSection(), defaultReinforcement(), c = 450.0)
        assertTrue("Larger c value should increase axial capacity Pn", largeC.P > smallC.P)
    }

    @Test(expected = IllegalArgumentException::class)
    fun solvePnMn_throwsForZeroFcu() {
        InteractionDiagramSolver.solvePnMn(0.0, 400.0, defaultSection(), defaultReinforcement())
    }

    @Test(expected = IllegalArgumentException::class)
    fun solvePnMn_throwsForZeroFy() {
        InteractionDiagramSolver.solvePnMn(30.0, 0.0, defaultSection(), defaultReinforcement())
    }

    @Test(expected = IllegalArgumentException::class)
    fun solvePnMn_throwsForZeroC() {
        InteractionDiagramSolver.solvePnMn(30.0, 400.0, defaultSection(), defaultReinforcement(), c = 0.0)
    }
}
