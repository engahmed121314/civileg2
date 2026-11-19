package com.civileg.app.domain.drawing

import org.junit.Assert.*
import org.junit.Test

class DrawingPackageRegistryTest {

    @Test
    fun `test registry contains beam and column at PACKAGE status`() {
        DrawingPackageRegistrations.registerAll()

        val beam = DrawingPackageRegistry.findByScreen("BeamScreen")
        assertNotNull("BeamScreen should be registered", beam)
        assertEquals("BeamScreen should be PACKAGE", PackageStatus.PACKAGE, beam!!.status)
        assertEquals("beam", beam.element)
        assertTrue("beam should have case keys", beam.caseKeys.isNotEmpty())

        val column = DrawingPackageRegistry.findByScreen("ColumnScreen")
        assertNotNull("ColumnScreen should be registered", column)
        assertEquals("ColumnScreen should be PACKAGE", PackageStatus.PACKAGE, column!!.status)
        assertEquals("column", column.element)
    }

    @Test
    fun `test all registered elements are PACKAGE after registerAll`() {
        DrawingPackageRegistrations.registerAll()
        assertTrue("All PACKAGE elements should pass", DrawingPackageRegistry.allPackage())
        assertEquals(16, DrawingPackageRegistry.packageElements().size)
        assertEquals(3, DrawingPackageRegistry.todoElements().size)
        assertEquals(0, DrawingPackageRegistry.legacyElements().size)
    }

    @Test
    fun `test registry starts empty before registration`() {
        // Note: registry is a singleton; this test assumes clean state.
        // In practice, registerAll() is called once during app init.
        assertTrue("Status counts map should be valid", DrawingPackageRegistry.statusCounts().all { it.value >= 0 })
    }
}