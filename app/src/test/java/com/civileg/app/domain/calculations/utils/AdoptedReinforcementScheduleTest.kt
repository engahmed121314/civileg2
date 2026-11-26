package com.civileg.app.domain.calculations.utils

import org.junit.Assert.*
import org.junit.Test

class AdoptedReinforcementScheduleTest {
    private fun group() = AdoptedBarGroup(
        mark = "H1-01", role = "Rib bottom", diameterMm = 12,
        barsPerMember = 4, membersCount = 2, bodyLengthMm = 5950.0,
        startExtensionMm = 0.0, endExtensionMm = 0.0,
        startStationMm = 25.0, endStationMm = 5975.0,
        code = "ECP", edition = null, clause = null,
        shapeDescription = "Straight body only. Support anchorage not adopted."
    )
    @Test fun `four adopted bars times two ribs is eight physical bars`() {
        val g = group()
        assertEquals(8, g.totalCount)
        assertEquals(452.389342, g.areaPerMemberMm2, 1e-5)
        assertEquals(47.6, g.totalLengthM, 1e-8)
        assertEquals(47.6 * 0.0061654 * 144, g.totalWeightKg, 1e-8)
    }
    @Test fun `missing engineering basis cannot be execution ready`() {
        val schedule = AdoptedReinforcementSchedule("ECP", listOf(group()))
        assertFalse(schedule.executionReady)
        assertEquals(group().totalWeightKg, schedule.totalWeightKg, 1e-8)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `verified group requires edition and clause`() { group().copy(verified = true) }
    @Test(expected = IllegalArgumentException::class)
    fun `different selected code cannot reuse group`() {
        AdoptedReinforcementSchedule("SBC", listOf(group()))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `duplicate marks rejected`() {
        AdoptedReinforcementSchedule("ECP", listOf(group(), group()))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `unknown count rejected`() { group().copy(barsPerMember = 0) }
    @Test(expected = IllegalArgumentException::class)
    fun `non finite dimension rejected`() { group().copy(bodyLengthMm = Double.NaN) }
    @Test fun `caller list mutation cannot change scheduled bars weight or code identity`() {
        val source = mutableListOf(group())
        val schedule = AdoptedReinforcementSchedule("ECP", source)
        val weight = schedule.totalWeightKg
        source.clear()
        source.add(group().copy(code = "ACI"))
        assertEquals(1, schedule.groups.size)
        assertEquals("ECP", schedule.groups.single().code)
        assertEquals(weight, schedule.totalWeightKg, 0.0)
    }
    @Test(expected = UnsupportedOperationException::class)
    fun `schedule does not expose a mutable backing list`() {
        val schedule = AdoptedReinforcementSchedule("ECP", listOf(group()))
        (schedule.groups as MutableList<AdoptedBarGroup>).clear()
    }
    @Test(expected = IllegalArgumentException::class)
    fun `finite components with overflowing cut length are rejected`() {
        group().copy(bodyLengthMm = Double.MAX_VALUE, startExtensionMm = Double.MAX_VALUE)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `finite station endpoints with overflowing range are rejected`() {
        group().copy(startStationMm = -Double.MAX_VALUE, endStationMm = Double.MAX_VALUE)
    }
}
