package com.civileg.app.utils

import com.civileg.app.utils.CalculatorDetailingV4.BarDefinition
import com.civileg.app.utils.CalculatorDetailingV4.BarShape
import com.civileg.app.utils.CalculatorDetailingV4.DetailingPackage
import com.civileg.app.utils.CalculatorDetailingV4.MemberType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W11 golden regression gate (PHASE00_AUDIT §3.1 W11):
 * buildBarSchedule previously reported quantity = 1 for every spaced bar,
 * understating exported tonnage by ~30x on typical members.
 *
 * All expected values below are hand-derived from detailing conventions:
 *   spaced count n = floor(distribution_length / spacing) + 1
 *   unit weight (kg/m) = 0.006165 * d^2
 */
class BbsQuantityGoldenTest {

    private fun scheduleOf(pkg: DetailingPackage) =
        CalculatorDetailingV4.buildBarSchedule(listOf(pkg))

    @Test
    fun `beam stirrups across full span`() {
        // span 5000, spacing 150 -> n = floor(5000/150)+1 = 34
        // cut length = 170+420+170+420+80 = 1260 mm
        // weight = 34 * 1.26 m * 0.006165 * 64 = 16.903 kg
        val pkg = DetailingPackage(
            memberType = MemberType.BEAM, memberId = "B1",
            geometry = mapOf("span" to 5000.0),
            stirrups = listOf(
                BarDefinition(
                    mark = "S1", diameterMm = 8, shape = BarShape.STIRRUP_135,
                    spacingMm = 150.0,
                    segments = listOf(
                        CalculatorDetailingV4.Segment(90.0, 170.0),
                        CalculatorDetailingV4.Segment(90.0, 420.0),
                        CalculatorDetailingV4.Segment(90.0, 170.0),
                        CalculatorDetailingV4.Segment(90.0, 420.0),
                        CalculatorDetailingV4.Segment(135.0, 80.0)
                    )
                )
            )
        )
        val row = scheduleOf(pkg).rows.single()
        assertEquals(34, row.quantity)
        assertEquals(34 * 1.26, row.totalLengthM, 1e-9)
        assertEquals(34 * 1.26 * 0.006165 * 64, row.totalWeightKg, 1e-6)
    }

    @Test
    fun `zoned stirrups count within zone length only`() {
        // zone from 500 to 4500 on a 5000 span: cut-offs 500 + 500 -> L = 4000
        // spacing 200 -> n = floor(4000/200)+1 = 21
        val pkg = DetailingPackage(
            memberType = MemberType.BEAM, memberId = "B1",
            geometry = mapOf("span" to 5000.0),
            stirrups = listOf(
                BarDefinition(
                    mark = "S1", diameterMm = 8, shape = BarShape.STIRRUP_135,
                    spacingMm = 200.0,
                    cutOffFromStartMm = 500.0,
                    cutOffFromEndMm = 500.0,
                    segments = listOf(CalculatorDetailingV4.Segment(90.0, 400.0))
                )
            )
        )
        assertEquals(21, scheduleOf(pkg).rows.single().quantity)
    }

    @Test
    fun `column ties distributed over member height`() {
        // height 3000, spacing 250 -> n = 13
        val pkg = DetailingPackage(
            memberType = MemberType.COLUMN, memberId = "C1",
            geometry = mapOf("height" to 3000.0),
            stirrups = listOf(
                BarDefinition(
                    mark = "T1", diameterMm = 8, shape = BarShape.STIRRUP_135,
                    spacingMm = 250.0,
                    segments = listOf(CalculatorDetailingV4.Segment(90.0, 400.0))
                )
            )
        )
        assertEquals(13, scheduleOf(pkg).rows.single().quantity)
    }

    @Test
    fun `footing mat distributes across perpendicular clear dimension`() {
        // bar runs along length (2350 = 2500 - 2x75) -> distributes across width - 2x75 = 2350
        // spacing 200 -> n = floor(2350/200)+1 = 12
        val pkg = DetailingPackage(
            memberType = MemberType.FOOTING, memberId = "F1",
            geometry = mapOf("length" to 2500.0, "width" to 2500.0, "cover" to 75.0),
            bars = listOf(
                BarDefinition(
                    mark = "FX", diameterMm = 16, shape = BarShape.STRAIGHT,
                    straightLengthMm = 2350.0, spacingMm = 200.0
                )
            )
        )
        assertEquals(12, scheduleOf(pkg).rows.single().quantity)
    }

    @Test
    fun `tank wall bars distribute along tank length`() {
        // length 4000, spacing 180 -> n = floor(4000/180)+1 = 23
        val pkg = DetailingPackage(
            memberType = MemberType.TANK, memberId = "T1",
            geometry = mapOf("length" to 4000.0, "width" to 3000.0),
            bars = listOf(
                BarDefinition(
                    mark = "WV", diameterMm = 12, shape = BarShape.STRAIGHT,
                    straightLengthMm = 2900.0, spacingMm = 180.0, layer = "REBAR-WALL"
                )
            )
        )
        assertEquals(23, scheduleOf(pkg).rows.single().quantity)
    }

    @Test
    fun `explicit quantity overrides derivation`() {
        val pkg = DetailingPackage(
            memberType = MemberType.BEAM, memberId = "B1",
            geometry = mapOf("span" to 5000.0),
            bars = listOf(
                BarDefinition(
                    mark = "B1", diameterMm = 16, shape = BarShape.STRAIGHT,
                    straightLengthMm = 4920.0, quantity = 4
                )
            )
        )
        assertEquals(4, scheduleOf(pkg).rows.single().quantity)
    }

    @Test
    fun `unspaced bar without explicit quantity stays single`() {
        val pkg = DetailingPackage(
            memberType = MemberType.STAIR, memberId = "S1",
            geometry = mapOf("span" to 4000.0),
            bars = listOf(
                BarDefinition(
                    mark = "M1", diameterMm = 12, shape = BarShape.STRAIGHT,
                    straightLengthMm = 3950.0
                )
            )
        )
        assertEquals(1, scheduleOf(pkg).rows.single().quantity)
    }
}
