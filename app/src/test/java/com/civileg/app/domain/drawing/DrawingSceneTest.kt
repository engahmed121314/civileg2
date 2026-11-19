package com.civileg.app.domain.drawing

import org.junit.Assert.*
import org.junit.Test

class DrawingSceneTest {

    @Test
    fun `test DrawingScene primitive counts and layer names`() {
        val primitives = listOf(
            Primitive.Line(Point2D(0.0, 0.0), Point2D(100.0, 0.0), DrawingLayer.OUTLINE),
            Primitive.Circle(Point2D(50.0, 50.0), 20.0, DrawingLayer.REBAR_BOTTOM),
            Primitive.Text("SECTION 1-1", Point2D(10.0, 20.0), 3.5, DrawingLayer.TEXT)
        )

        val scene = DrawingScene(
            title = "Test Beam Drawing",
            bounds = Rect2D(0.0, 0.0, 420.0, 297.0),
            primitives = primitives
        )

        assertEquals(3, scene.primitiveCount())
        assertEquals(setOf("0_OUTLINE", "0_REBAR_BOTTOM", "0_TEXT"), scene.layerNames())
    }

    @Test
    fun `test LabelPlacer collision avoidance`() {
        val bounds = Rect2D(0.0, 0.0, 200.0, 200.0)
        val obstacles = listOf(
            Rect2D(40.0, 40.0, 60.0, 60.0) // Obstacle right at (50, 50)
        )
        val placer = LabelPlacer(obstacles, bounds)

        val requests = listOf(
            LabelRequest("L1", "3Ø16", widthMm = 15.0, heightMm = 5.0, anchor = Point2D(50.0, 50.0), priority = 1)
        )

        val placed = placer.placeLabels(requests)
        assertEquals(1, placed.size)

        val label = placed[0]
        assertEquals("L1", label.id)
        // Ensure placed rect does not intersect the obstacle
        assertFalse(obstacles[0].intersects(label.rect))
    }

    @Test
    fun `test SheetComposer scale selection and title block generation`() {
        val scale = SheetComposer.selectScale(viewWidthMm = 6000.0, viewHeightMm = 600.0, targetWidthMm = 200.0, targetHeightMm = 100.0)
        assertEquals(50.0, scale, 0.01) // 6000/200 = 30 -> next scale is 50.0

        val titleBlock = SheetComposer.buildTitleBlock(
            projectTitle = "Villa Project",
            elementTitle = "Ground Floor Beam",
            memberMark = "B1",
            codeVersion = "ECP 203-2018",
            scaleText = "1:50"
        )
        assertTrue(titleBlock.isNotEmpty())
        assertTrue(titleBlock.any { it is Primitive.Text && (it as Primitive.Text).text.contains("B1") })
    }
}
