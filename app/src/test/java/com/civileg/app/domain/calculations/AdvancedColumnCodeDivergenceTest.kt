package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIAdvancedColumn
import com.civileg.app.domain.calculations.ecp.ECPAdvancedColumn
import com.civileg.app.domain.calculations.sbc.SBCAdvancedColumn
import com.civileg.app.domain.entities.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdvancedColumnCodeDivergenceTest {

    private lateinit var ecpEngine: ECPAdvancedColumn
    private lateinit var aciEngine: ACIAdvancedColumn
    private lateinit var sbcEngine: SBCAdvancedColumn

    private val endConditions = ColumnEndConditions(
        topCondition = EndCondition.FIXED,
        bottomCondition = EndCondition.FIXED
    )

    @Before
    fun setUp() {
        ecpEngine = ECPAdvancedColumn()
        aciEngine = ACIAdvancedColumn()
        sbcEngine = SBCAdvancedColumn()
    }

    @Test
    fun testCrossCodeAdvancedColumnDivergence() {
        val columnType = ColumnType.Rectangular(300.0, 600.0)
        val fcu = 30.0
        val fy = 400.0
        val pu = 1200.0
        val mx = 80.0
        val my = 40.0
        val length = 3.5

        val ecpRes = ecpEngine.designAdvancedColumn(
            columnType, fcu, fy, pu, mx, my, length, endConditions,
            ConnectedSlabType.SOLID, false, null, LoadCombination.DEAD_LIVE
        )
        val aciRes = aciEngine.designAdvancedColumn(
            columnType, fcu, fy, pu, mx, my, length, endConditions,
            ConnectedSlabType.SOLID, false, null, LoadCombination.DEAD_LIVE
        )
        val sbcRes = sbcEngine.designAdvancedColumn(
            columnType, fcu, fy, pu, mx, my, length, endConditions,
            ConnectedSlabType.SOLID, false, null, LoadCombination.DEAD_LIVE
        )

        assertNotNull(ecpRes)
        assertNotNull(aciRes)
        assertNotNull(sbcRes)

        assertTrue(ecpRes.axialCapacity > 0.0)
        assertTrue(aciRes.axialCapacity > 0.0)
        assertTrue(sbcRes.axialCapacity > 0.0)

        // All three codes must calculate required steel area
        assertTrue(ecpRes.reinforcementResult.astRequired > 0.0)
        assertTrue(aciRes.reinforcementResult.astRequired > 0.0)
        assertTrue(sbcRes.reinforcementResult.astRequired > 0.0)

        // Compare axial capacities (ECP vs ACI vs SBC)
        val ratioEcpAci = ecpRes.axialCapacity / aciRes.axialCapacity
        val ratioSbcAci = sbcRes.axialCapacity / aciRes.axialCapacity

        assertTrue("ECP/ACI axial capacity ratio within expected analytical bounds", ratioEcpAci in 0.80..1.30)
        assertTrue("SBC/ACI axial capacity ratio within expected analytical bounds", ratioSbcAci in 0.85..1.25)
    }
}
