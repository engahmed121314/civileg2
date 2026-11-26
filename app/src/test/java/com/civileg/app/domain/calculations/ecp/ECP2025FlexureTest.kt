package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ECP 203-2025 Table 4-1 (PDF p.86) and Eq 4-4 (PDF p.85). */
class ECP2025FlexureTest {
    @Test fun tabulatedGradesMatchTable41() {
        assertEquals(0.186, ECP2025Flexure.rMax(420.0), 1e-12)
        assertEquals(0.189, ECP2025Flexure.rMax(400.0), 1e-12)
        assertEquals(0.214, ECP2025Flexure.rMax(240.0), 1e-12)
        assertEquals(0.155, ECP2025Flexure.rMax(700.0), 1e-12)
    }

    @Test fun untabulatedGradeUsesNextHigherRowConservatively() {
        assertEquals(0.189, ECP2025Flexure.rMax(360.0), 1e-12)
    }

    @Test fun kLimitIsRmaxOverGammaC() {
        assertEquals(0.186 / 1.5, ECP2025Flexure.kLimit(420.0), 1e-12)
    }

    @Test fun leverArmCappedAt095d() {
        assertEquals(0.95 * 400.0, ECP2025Flexure.leverArm(0.001, 400.0), 1e-9)
        assertTrue(ECP2025Flexure.leverArm(0.12, 400.0) < 0.95 * 400.0)
    }

    @Test fun footingFlagsSectionAboveRmaxOverGammaC() {
        // K = Mu/(fcu b d^2) = 250e6/(25*1000*300^2) = 0.111 < 0.126 (fy 400): ok; 330 kN.m/m gives 0.147 > limit
        val f = ECPFooting()
        assertTrue(f.calculateFootingReinforcement(25.0, 400.0, 1500.0, 1500.0, 300.0, 250.0,
            com.civileg.app.domain.calculations.base.FootingDirection.SHORT).isSafe)
        assertTrue(!f.calculateFootingReinforcement(25.0, 400.0, 1500.0, 1500.0, 300.0, 330.0,
            com.civileg.app.domain.calculations.base.FootingDirection.SHORT).isSafe)
    }
}
