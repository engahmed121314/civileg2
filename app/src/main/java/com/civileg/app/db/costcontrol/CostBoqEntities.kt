package com.civileg.app.db.costcontrol

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.civileg.app.db.Project

/** UNPUBLISHED integration draft. Not registered in AppDatabase or provided by DI yet. */
@Entity(
    tableName = "cost_boq_revisions",
    primaryKeys = ["projectId", "revisionId"],
    indices = [Index(value = ["projectId", "revisionId", "contentHash"], unique = true)],
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)]
)
data class CostBoqRevisionEntity(
    val projectId: Long,
    val revisionId: String,
    val contentHash: String,
    val previousRevisionId: String?,
    val revisionReason: String,
    val currency: String?,
    val asOfDate: String?,
    val createdAtMillis: Long
)

/** Draft payload is versioned exact-text JSON, decoded strictly by repository, never defaulted on failure. */
@Entity(
    tableName = "cost_boq_items",
    primaryKeys = ["projectId", "revisionId", "itemId"],
    indices = [Index(value = ["projectId", "revisionId", "contentHash"])],
    foreignKeys = [ForeignKey(
        entity = CostBoqRevisionEntity::class,
        parentColumns = ["projectId", "revisionId", "contentHash"],
        childColumns = ["projectId", "revisionId", "contentHash"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CostBoqItemEntity(
    val projectId: Long,
    val revisionId: String,
    val contentHash: String,
    val itemId: String,
    val sourceWorkbookHash: String?,
    val sourceSheet: String?,
    val sourceCell: String?,
    val displayedItemNumber: String,
    val originalDescription: String,
    val originalUnit: String,
    val quantityText: String?,
    val quantityOrigin: String,
    val quantityEvidencePayload: String,
    val quantityReviewConfirmed: Boolean,
    val quotedRateText: String?,
    val currency: String?,
    val priceAsOfDate: String?,
    val priceSource: String,
    val payloadVersion: Int,
    val analysisPayload: String?
)

/** Mutable pointer only. Expected-head transaction guards immutable append-only revisions. */
@Entity(
    tableName = "cost_boq_heads",
    primaryKeys = ["projectId"],
    indices = [Index(value = ["projectId", "revisionId", "contentHash"])],
    foreignKeys = [ForeignKey(
        entity = CostBoqRevisionEntity::class,
        parentColumns = ["projectId", "revisionId", "contentHash"],
        childColumns = ["projectId", "revisionId", "contentHash"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CostBoqHeadEntity(val projectId: Long, val revisionId: String, val contentHash: String)
