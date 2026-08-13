package com.civileg.app.domain.usecases

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ValidateInputsUseCaseTest {

    private lateinit var validateInputs: ValidateInputsUseCase

    @Before
    fun setup() {
        validateInputs = ValidateInputsUseCase()
    }

    @Test
    fun `valid beam inputs return empty errors`() {
        val errors = validateInputs.validateBeamInputs(
            span = 5.0, width = 0.3, depth = 0.6, load = 50.0, fcu = 25.0, fy = 360.0
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `zero span returns error`() {
        val errors = validateInputs.validateBeamInputs(
            span = 0.0, width = 0.3, depth = 0.6, load = 50.0, fcu = 25.0, fy = 360.0
        )
        assertTrue(errors.any { it.contains("Span") })
    }

    @Test
    fun `negative width returns error`() {
        val errors = validateInputs.validateBeamInputs(
            span = 5.0, width = -0.3, depth = 0.6, load = 50.0, fcu = 25.0, fy = 360.0
        )
        assertTrue(errors.any { it.contains("Width") })
    }

    @Test
    fun `all invalid beam inputs return multiple errors`() {
        val errors = validateInputs.validateBeamInputs(
            span = -1.0, width = 0.0, depth = -0.5, load = 0.0, fcu = 0.0, fy = 0.0
        )
        assertEquals(5, errors.size)
    }

    @Test
    fun `valid column inputs return empty errors`() {
        val errors = validateInputs.validateColumnInputs(
            load = 1000.0, width = 0.3, depth = 0.5, fcu = 30.0, fy = 400.0
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `zero column load returns error`() {
        val errors = validateInputs.validateColumnInputs(
            load = 0.0, width = 0.3, depth = 0.5, fcu = 30.0, fy = 400.0
        )
        assertTrue(errors.any { it.contains("Axial load") })
    }

    @Test
    fun `valid slab inputs return empty errors`() {
        val errors = validateInputs.validateSlabInputs(
            spanX = 4.0, spanY = 5.0, load = 8.0, fcu = 25.0, fy = 360.0
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `valid footing inputs return empty errors`() {
        val errors = validateInputs.validateFootingInputs(
            load = 500.0, fcu = 25.0, fy = 360.0, allowablePressure = 150.0
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `zero soil pressure returns error`() {
        val errors = validateInputs.validateFootingInputs(
            load = 500.0, fcu = 25.0, fy = 360.0, allowablePressure = 0.0
        )
        assertTrue(errors.any { it.contains("soil pressure") })
    }

    @Test
    fun `valid tank inputs return empty errors`() {
        val errors = validateInputs.validateTankInputs(
            length = 4.0, width = 3.0, height = 3.0, fcu = 30.0, fy = 400.0
        )
        assertTrue(errors.isEmpty())
    }
}
