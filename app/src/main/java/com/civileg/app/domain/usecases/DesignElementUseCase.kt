package com.civileg.app.domain.usecases

import com.civileg.app.domain.base.CalculationResult
import com.civileg.app.domain.base.ErrorCode
import com.civileg.app.domain.repository.DesignRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Generic use case for designing any structural element.
 * Encapsulates the pattern: validate → calculate via strategy → save to repository.
 *
 * @param TInput the input type for the calculation
 * @param TResult the result type returned by the calculation
 */
class DesignElementUseCase @Inject constructor(
    private val designRepository: DesignRepository
) {
    /**
     * Execute a structural design calculation and optionally save the result.
     *
     * @param code The design code to use (ECP/ACI/SBC)
     * @param projectId The project to save under (0 = don't save)
     * @param elementName Display name for the design
     * @param calculate The actual calculation lambda using CalculationFactory
     * @param saveAction Optional lambda to save the result to the specialized table
     */
    suspend fun <T> executeDesign(
        projectId: Long = 0,
        elementName: String,
        calculate: suspend () -> T,
        saveAction: (suspend (DesignRepository, Long, String, T, String) -> Unit)? = null,
        codeUsed: String = ""
    ): CalculationResult<T> {
        return try {
            val result = calculate()
            if (projectId > 0 && saveAction != null) {
                saveAction(designRepository, projectId, elementName, result, codeUsed)
            }
            CalculationResult.Success(result)
        } catch (e: Exception) {
            CalculationResult.Error(e.message ?: "Unknown calculation error", ErrorCode.UNKNOWN)
        }
    }

    /**
     * Execute a design as a Flow for reactive UI updates.
     */
    fun <T> executeDesignFlow(
        projectId: Long = 0,
        elementName: String,
        calculate: suspend () -> T,
        saveAction: (suspend (DesignRepository, Long, String, T, String) -> Unit)? = null,
        codeUsed: String = ""
    ) = flow {
        emit(CalculationResult.Loading)
        emit(executeDesign(projectId, elementName, calculate, saveAction, codeUsed))
    }.flowOn(Dispatchers.IO)
}
