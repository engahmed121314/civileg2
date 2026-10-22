package com.civileg.app.ui.compose.screens

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelerViewportTest {

    @Test
    fun screenWorldRoundTripKeepsCoordinates() {
        val viewport = ModelerViewport(scale = 50f, panX = 120f, panY = 340f)
        val point = Offset(300f, 220f)

        val (x, y) = viewport.screenToWorld(point)
        val result = viewport.worldToScreen(x, y)

        assertEquals(point.x, result.x, 1e-4f)
        assertEquals(point.y, result.y, 1e-4f)
    }

    @Test
    fun zoomAroundCentroidKeepsWorldPointUnderFinger() {
        val viewport = ModelerViewport(scale = 50f, panX = 100f, panY = 200f)
        val centroid = Offset(300f, 400f)
        val worldBefore = viewport.screenToWorld(centroid)

        val transformed = viewport.transformed(centroid, Offset.Zero, zoom = 2f)
        val worldAfter = transformed.screenToWorld(centroid)

        assertEquals(worldBefore.first, worldAfter.first, 1e-5)
        assertEquals(worldBefore.second, worldAfter.second, 1e-5)
        assertEquals(100f, transformed.scale, 1e-4f)
    }

    @Test
    fun panMovesWorldUnderFingerBySameAmount() {
        val viewport = ModelerViewport(scale = 50f, panX = 100f, panY = 200f)
        val centroid = Offset(300f, 400f)
        val pan = Offset(15f, -10f)
        val worldBefore = viewport.screenToWorld(centroid)

        val transformed = viewport.transformed(centroid, pan, zoom = 1f)
        val worldAfter = transformed.screenToWorld(centroid + pan)

        assertEquals(worldBefore.first, worldAfter.first, 1e-5)
        assertEquals(worldBefore.second, worldAfter.second, 1e-5)
    }

    @Test
    fun scaleIsClampedToModelerLimits() {
        val viewport = ModelerViewport(scale = 60f, panX = 0f, panY = 0f)

        assertTrue(viewport.transformed(Offset.Zero, Offset.Zero, 1_000_000f).scale <= 30000f)
        assertTrue(viewport.transformed(Offset.Zero, Offset.Zero, 0.000001f).scale >= 2f)
    }
}
