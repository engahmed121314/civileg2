package com.civileg.app.domain.calculations.ecp

import com.civileg.core.engineering.StrapFootingDesignEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ECPStrapFootingEcpMethodTest {
    private val inputs = StrapFootingDesignEngine.Inputs(
        column1Load = 500.0, column2Load = 300.0, distanceBetweenColumns = 4000.0,
        column1Width = 300.0, column1Depth = 500.0, column2Width = 300.0, column2Depth = 500.0,
        soilBearingCapacity = 150.0, fcu = 25.0, fy = 360.0, strapBeamWidth = 400.0
    )

    @Test
    fun ecpPunchingCapacityUsesEq446NotAciConstant() {
        val ecp = ECPStrapFooting().design(inputs)
        val d = ecp.footing2.thickness - 60.0
        val bo = 2.0 * ((300.0 + d) + (500.0 + d))
        val qcup = ECP2025ConcreteShear.punchingQcup(25.0, d, bo, 300.0, 500.0)
        assertEquals(qcup * bo * d / 1000.0, ecp.punchingCapacity2, 1e-6)
    }

    @Test
    fun edgeColumnUsesThreeSidedPerimeter() {
        val ecp = ECPStrapFooting().design(inputs)
        val d = ecp.footing1.thickness - 60.0
        val bo = 2.0 * (500.0 + d / 2.0) + (300.0 + d)
        val qcup = ECP2025ConcreteShear.punchingQcup(
            25.0, d, bo, 300.0, 500.0, ECP2025ConcreteShear.ColumnPosition.EDGE
        )
        assertEquals(qcup * bo * d / 1000.0, ecp.punchingCapacity1, 1e-6)
        assertTrue(ecp.punchingShear1 <= ecp.punchingCapacity1 + 1e-6)
    }
}
