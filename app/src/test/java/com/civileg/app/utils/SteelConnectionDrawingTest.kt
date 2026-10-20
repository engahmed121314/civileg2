package com.civileg.app.utils

import com.civileg.app.domain.entities.BoltConnectionType
import com.civileg.app.domain.entities.BoltGrade
import com.civileg.app.domain.entities.BoltPattern
import com.civileg.app.domain.entities.ConnectionDesignResult
import com.civileg.app.domain.entities.ConnectionType
import com.civileg.app.domain.entities.ElectrodeType
import com.civileg.app.domain.entities.WeldType
import org.junit.Assert.assertEquals
import org.junit.Test

class SteelConnectionDrawingTest {

    private fun result(type: ConnectionType) = ConnectionDesignResult(
        connectionType = type,
        capacity = 100.0,
        appliedForce = 80.0,
        utilizationRatio = 0.8,
        isSafe = true,
        detailedCalculations = "",
        warnings = emptyList(),
        codeNotes = emptyList(),
        endPlateThickness = 12.0
    )

    @Test
    fun bolted_usesActualDiameterAndCount_andDerivesSpacing() {
        val p = SteelConnectionDrawing.from(
            result(
                ConnectionType.Bolted(
                    boltDiameter = 20.0,
                    boltGrade = BoltGrade.GRADE_8_8,
                    numberOfBolts = 6,
                    boltPattern = BoltPattern.DOUBLE_ROW,
                    connectionType = BoltConnectionType.BEARING
                )
            )
        )
        assertEquals(20.0, p.boltDia, 1e-9)
        assertEquals(6, p.boltCount)
        assertEquals(60.0, p.boltGauge, 1e-9)
        assertEquals(60.0, p.boltPitch, 1e-9)
        assertEquals(6.0, p.weldSize, 1e-9)
    }

    @Test
    fun welded_usesActualWeldSize() {
        val p = SteelConnectionDrawing.from(
            result(ConnectionType.Welded(WeldType.FILLET, 8.0, 200.0, ElectrodeType.E70XX))
        )
        assertEquals(8.0, p.weldSize, 1e-9)
        assertEquals(20.0, p.boltDia, 1e-9)
        assertEquals(4, p.boltCount)
    }

    @Test
    fun hybrid_readsWeldAndBolt_andRoundsSpacingToNearest5mm() {
        val p = SteelConnectionDrawing.from(
            result(
                ConnectionType.Hybrid(
                    welded = ConnectionType.Welded(WeldType.FILLET, 5.0, 150.0, ElectrodeType.E70XX),
                    bolted = ConnectionType.Bolted(16.0, BoltGrade.GRADE_8_8, 8, BoltPattern.GRID, BoltConnectionType.BEARING)
                )
            )
        )
        assertEquals(5.0, p.weldSize, 1e-9)
        assertEquals(16.0, p.boltDia, 1e-9)
        assertEquals(8, p.boltCount)
        assertEquals(50.0, p.boltGauge, 1e-9)
    }

    @Test
    fun noConnection_fallsBackToDefaults() {
        val p = SteelConnectionDrawing.from(null)
        assertEquals(20.0, p.boltDia, 1e-9)
        assertEquals(4, p.boltCount)
        assertEquals(6.0, p.weldSize, 1e-9)
    }
}
