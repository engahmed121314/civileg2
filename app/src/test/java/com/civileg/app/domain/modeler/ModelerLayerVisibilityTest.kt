package com.civileg.app.domain.modeler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelerLayerVisibilityTest {

    @Test
    fun defaultVisibilityShowsEveryLayer() {
        val layers = ModelerLayerVisibility()

        assertEquals(ModelerLayerVisibility.ALL, layers.toMask())
        assertTrue(layers.grid)
        assertTrue(layers.joints)
        assertTrue(layers.members)
        assertTrue(layers.supports)
        assertTrue(layers.loads)
        assertTrue(layers.slabs)
        assertTrue(layers.dimensions)
    }

    @Test
    fun maskRoundTripPreservesVisibility() {
        val expected = ModelerLayerVisibility(
            grid = false,
            joints = true,
            members = false,
            supports = true,
            loads = false,
            slabs = true,
            dimensions = false
        )

        assertEquals(expected, ModelerLayerVisibility.fromMask(expected.toMask()))
    }

    @Test
    fun hidingDimensionsDoesNotHideMembers() {
        val layers = ModelerLayerVisibility.fromMask(
            ModelerLayerVisibility.ALL and ModelerLayerVisibility.DIMENSIONS.inv()
        )

        assertFalse(layers.dimensions)
        assertTrue(layers.members)
        assertTrue(layers.grid)
    }
}
