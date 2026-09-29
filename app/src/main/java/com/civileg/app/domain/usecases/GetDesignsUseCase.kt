package com.civileg.app.domain.usecases

import com.civileg.app.db.Design
import com.civileg.app.domain.repository.DesignRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDesignsUseCase @Inject constructor(
    private val designRepository: DesignRepository
) {
    fun getDesignsForProject(projectId: Long): Flow<List<Design>> =
        designRepository.getDesignsForProject(projectId)

    fun searchDesigns(query: String): Flow<List<Design>> =
        designRepository.searchDesigns(query)

    suspend fun deleteDesign(design: Design) =
        designRepository.deleteDesign(design)

    suspend fun getTotalCost(projectId: Long): Double =
        designRepository.getTotalCost(projectId)
}
