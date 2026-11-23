package com.civileg.app.domain.repository

import com.civileg.app.db.costcontrol.CostBoqHeadEntity
import com.civileg.app.db.costcontrol.CostBoqItemEntity
import com.civileg.app.db.costcontrol.CostBoqRevisionEntity

/** Draft contract only. No production DI binding before migration/codec/Android tests are ready. */
data class SavedCostBoq(val revision: CostBoqRevisionEntity, val items: List<CostBoqItemEntity>)

interface CostBoqRepository {
    suspend fun loadCurrent(projectId: Long): SavedCostBoq?
    suspend fun append(expectedHead: CostBoqHeadEntity?, candidate: SavedCostBoq): SavedCostBoq
}

/** Strict versioned codec boundary. Fail closed on unknown payload version, enum, missing data or invalid graph. */
interface CostBoqPayloadValidator {
    fun validateItem(item: CostBoqItemEntity)
}
