package com.civileg.app.db.costcontrol

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/** UNPUBLISHED draft. Repository must strictly validate content hash/JSON/decimal/currency before append. */
@Dao
abstract class CostBoqDao {
    @Transaction
    open suspend fun currentSnapshot(projectId: Long): Pair<CostBoqRevisionEntity, List<CostBoqItemEntity>>? {
        val current = head(projectId) ?: return null
        val header = checkNotNull(revision(projectId, current.revisionId)) { "Missing BOQ revision" }
        check(header.contentHash == current.contentHash)
        return header to items(projectId, current.revisionId)
    }
    @Query("SELECT * FROM cost_boq_heads WHERE projectId=:projectId")
    abstract suspend fun head(projectId: Long): CostBoqHeadEntity?

    @Query("SELECT * FROM cost_boq_revisions WHERE projectId=:projectId AND revisionId=:revisionId")
    abstract suspend fun revision(projectId: Long, revisionId: String): CostBoqRevisionEntity?

    @Query("SELECT * FROM cost_boq_items WHERE projectId=:projectId AND revisionId=:revisionId ORDER BY itemId")
    abstract suspend fun items(projectId: Long, revisionId: String): List<CostBoqItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRevision(value: CostBoqRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertItems(value: List<CostBoqItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertHead(value: CostBoqHeadEntity)
    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateHead(value: CostBoqHeadEntity): Int

    /** All or nothing. Duplicate save is idempotent only for same immutable content, never REPLACE. */
    @Transaction
    open suspend fun appendRevision(
        expectedHead: CostBoqHeadEntity?,
        next: CostBoqRevisionEntity,
        nextItems: List<CostBoqItemEntity>
    ): Pair<CostBoqRevisionEntity,List<CostBoqItemEntity>> {
        require(next.projectId > 0 && next.revisionId.isNotBlank() && next.contentHash.matches(Regex("[a-f0-9]{64}")))
        require(next.revisionReason.isNotBlank() && nextItems.isNotEmpty())
        require(nextItems.all { it.projectId == next.projectId && it.revisionId == next.revisionId && it.contentHash == next.contentHash })
        require(nextItems.map { it.itemId }.distinct().size == nextItems.size)
        require(expectedHead == null || expectedHead.projectId == next.projectId)
        val current = head(next.projectId)
        val existing = revision(next.projectId, next.revisionId)
        if (existing != null) {
            require(existing == next && items(next.projectId, next.revisionId).toSet() == nextItems.toSet()) {
                "Revision ID is already bound to different content"
            }
            // Exact historical success is idempotent even if another writer advanced the head.
            // Never update the head on this branch.
            return existing to items(next.projectId,next.revisionId)
        }
        require(current == expectedHead) { "Another edit changed this BOQ; reload before saving" }
        require(next.previousRevisionId == current?.revisionId) { "Previous revision ownership mismatch" }
        require(current?.revisionId != next.revisionId)
        insertRevision(next)
        insertItems(nextItems)
        val updated = CostBoqHeadEntity(next.projectId, next.revisionId, next.contentHash)
        if (current == null) insertHead(updated) else check(updateHead(updated) == 1)
        val committed=checkNotNull(revision(next.projectId,next.revisionId))
        val committedItems=items(next.projectId,next.revisionId)
        check(committed==next && committedItems.toSet()==nextItems.toSet())
        return committed to committedItems
    }
}
