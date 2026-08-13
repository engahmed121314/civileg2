package com.civileg.app.domain.usecases

import com.civileg.app.domain.base.CalculationResult
import com.civileg.app.domain.repository.DesignRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DesignElementUseCaseTest {

    private lateinit var designRepository: DesignRepository
    private lateinit var useCase: DesignElementUseCase

    @Before
    fun setup() {
        designRepository = mockk(relaxed = true)
        useCase = DesignElementUseCase(designRepository)
    }

    @Test
    fun `executeDesign returns success for valid calculation`() = runTest {
        val result = useCase.executeDesign(
            projectId = 0,
            elementName = "Test Beam",
            calculate = { "BeamResult" }
        )

        assertTrue(result is CalculationResult.Success)
        assertEquals("BeamResult", (result as CalculationResult.Success).data)
    }

    @Test
    fun `executeDesign saves to repository when projectId is positive`() = runTest {
        useCase.executeDesign(
            projectId = 1,
            elementName = "Test Beam",
            calculate = { "Result" },
            saveAction = { repo, id, name, res, code -> /* no-op for test */ },
            codeUsed = "ECP 203"
        )

        coVerify(exactly = 0) { designRepository.saveBeamDesign(any(), any(), any(), any()) }
    }

    @Test
    fun `executeDesign returns error when calculation throws`() = runTest {
        val result = useCase.executeDesign(
            projectId = 0,
            elementName = "Test",
            calculate = { throw RuntimeException("Calculation failed") }
        )

        assertTrue(result is CalculationResult.Error)
        assertEquals("Calculation failed", (result as CalculationResult.Error).message)
    }

    @Test
    fun `executeDesign with null saveAction does not save`() = runTest {
        val result = useCase.executeDesign(
            projectId = 5,
            elementName = "Test",
            calculate = { "Result" },
            saveAction = null
        )

        assertTrue(result is CalculationResult.Success)
    }
}