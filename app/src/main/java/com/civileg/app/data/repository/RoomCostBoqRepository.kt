package com.civileg.app.data.repository

import com.civileg.app.db.costcontrol.CostBoqDao
import com.civileg.app.db.costcontrol.CostBoqHeadEntity
import com.civileg.app.db.costcontrol.CostBoqItemEntity
import com.civileg.app.domain.repository.CostBoqPayloadValidator
import com.civileg.app.domain.repository.CostBoqRepository
import com.civileg.app.domain.repository.SavedCostBoq
import com.civileg.core.costcontrol.CostDecimal
import com.civileg.core.costcontrol.QuantityOrigin
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Currency

/** UNPUBLISHED draft. Required validator has no production implementation/binding yet. Never defaults corrupt JSON. */
class RoomCostBoqRepository(private val dao: CostBoqDao, private val validator: CostBoqPayloadValidator) : CostBoqRepository {
    override suspend fun loadCurrent(projectId: Long): SavedCostBoq? {
        require(projectId > 0)
        val current = dao.currentSnapshot(projectId) ?: return null
        val saved = SavedCostBoq(current.first, current.second)
        validate(saved)
        return saved
    }

    override suspend fun append(expectedHead: CostBoqHeadEntity?, candidate: SavedCostBoq): SavedCostBoq {
        // Copy caller collection before validation and suspension. Legacy rows are never rewritten here.
        val frozen = candidate.copy(items = candidate.items.toList())
        validate(frozen)
        val committed = dao.appendRevision(expectedHead, frozen.revision, frozen.items)
        val readBack = SavedCostBoq(committed.first,committed.second)
        validate(readBack)
        check(readBack.revision == frozen.revision && readBack.items.toSet() == frozen.items.toSet()) { "BOQ readback mismatch" }
        return readBack
    }

    internal fun validate(saved: SavedCostBoq) {
        val r = saved.revision
        require(r.projectId > 0 && r.revisionId.isNotBlank() && r.revisionReason.isNotBlank())
        r.currency?.let(Currency::getInstance)
        r.asOfDate?.let(LocalDate::parse)
        require(saved.items.isNotEmpty() && saved.items.map { it.itemId }.distinct().size == saved.items.size)
        saved.items.forEach { item ->
            require(item.projectId == r.projectId && item.revisionId == r.revisionId && item.contentHash == r.contentHash)
            require(item.itemId.isNotBlank() && item.originalDescription.isNotBlank() && item.originalUnit.isNotBlank())
            item.quantityText?.let(::CostDecimal); item.quotedRateText?.let(::CostDecimal)
            QuantityOrigin.valueOf(item.quantityOrigin)
            item.currency?.let(Currency::getInstance); item.priceAsOfDate?.let(LocalDate::parse)
            require(item.payloadVersion == 1 && item.priceSource.isNotBlank())
            validator.validateItem(item) // Mandatory strict source/quantity evidence + rate graph validation.
        }
        require(contentHash(saved) == r.contentHash) { "BOQ content fingerprint mismatch" }
    }

    companion object {
        /** Versioned length-framed exact data, sorted by stable item ID. Not an approval signature. */
        fun contentHash(saved: SavedCostBoq): String {
            val digest = MessageDigest.getInstance("SHA-256")
            fun field(v: String?) {
                if (v == null) { digest.update(ByteBuffer.allocate(4).putInt(-1).array()); return }
                val bytes = v.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
            }
            val r = saved.revision
            listOf("cost-boq-v1", r.projectId.toString(), r.revisionId, r.previousRevisionId,
                r.revisionReason, r.currency, r.asOfDate, r.createdAtMillis.toString()).forEach(::field)
            saved.items.sortedBy { it.itemId }.forEach { i ->
                listOf(i.itemId, i.sourceWorkbookHash, i.sourceSheet, i.sourceCell, i.displayedItemNumber,
                    i.originalDescription, i.originalUnit, i.quantityText, i.quantityOrigin, i.quantityEvidencePayload,
                    i.quantityReviewConfirmed.toString(), i.quotedRateText, i.currency, i.priceAsOfDate, i.priceSource,
                    i.payloadVersion.toString(), i.analysisPayload).forEach(::field)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
