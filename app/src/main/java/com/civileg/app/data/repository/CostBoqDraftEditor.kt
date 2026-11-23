package com.civileg.app.data.repository

import com.civileg.app.db.costcontrol.*
import com.civileg.app.domain.repository.SavedCostBoq
import com.civileg.core.costcontrol.*
import java.util.UUID

/** Shared editor boundary. Every edit creates a candidate NEW revision, not an update of saved history. */
object CostBoqDraftEditor {
    fun add(projectId: Long, saved: SavedCostBoq?, draft: SavedCostBoq?, now: Long): SavedCostBoq {
        require(projectId > 0 && (draft == null || draft.revision.projectId == projectId))
        val id = UUID.randomUUID().toString()
        val quantity = BoqQuantityEvidence(QuantityOrigin.MISSING_INPUT,null,"unit","Manual editor",null,
            "Quantity not entered",listOf("quantity","unit","work description"),false)
        val item = CostBoqItemEntity(projectId,"pending","pending",id,null,null,null,"","New work item","unit",null,
            quantity.origin.name,CostBoqPayloadEncoder.quantity(quantity),false,null,null,null,"Not set",1,null)
        return revision(projectId,saved,draft?.items.orEmpty()+item,now,"Add work item",draft?.revision?.currency)
    }

    fun edit(projectId:Long, saved:SavedCostBoq?, draft:SavedCostBoq, itemId:String,
             number:String, description:String, unit:String, quantityText:String, rateText:String, now:Long):SavedCostBoq {
        require(draft.revision.projectId==projectId && draft.items.any { it.itemId==itemId })
        require(description.isNotBlank() && unit.isNotBlank())
        val quantity = quantityText.takeIf { it.isNotBlank() }?.let(::CostDecimal)
        val rate = rateText.takeIf { it.isNotBlank() }?.let(::CostDecimal)
        val evidence = BoqQuantityEvidence(if(quantity==null) QuantityOrigin.MISSING_INPUT else QuantityOrigin.MANUAL,
            quantity,unit,"Manual editor",null,"Quantity entered by user",if(quantity==null) listOf("quantity") else emptyList(),false)
        val items=draft.items.map { item ->
            if(item.itemId!=itemId) item else {
                val unitChanged=unit!=item.originalUnit
                val quantityChanged=unitChanged || quantity?.original!=item.quantityText
                val priceChanged=unitChanged || rate?.original!=item.quotedRateText
                item.copy(displayedItemNumber=number,originalDescription=description,originalUnit=unit,
                    analysisPayload=if(unitChanged) null else item.analysisPayload,
                    quantityText=quantity?.original,
                    quantityOrigin=if(quantityChanged) evidence.origin.name else item.quantityOrigin,
                    quantityEvidencePayload=if(quantityChanged) CostBoqPayloadEncoder.quantity(evidence) else item.quantityEvidencePayload,
                    quantityReviewConfirmed=if(quantityChanged) false else item.quantityReviewConfirmed,
                    quotedRateText=rate?.original,
                    currency=if(priceChanged) null else item.currency,
                    priceAsOfDate=if(priceChanged) null else item.priceAsOfDate,
                    priceSource=if(priceChanged) "Manual override, currency/date confirmation required" else item.priceSource)
            }
        }
        return revision(projectId,saved,items,now,"Edit work item",draft.revision.currency)
    }

    fun confirmManualQuantity(projectId:Long,saved:SavedCostBoq?,draft:SavedCostBoq,itemId:String,now:Long):SavedCostBoq {
        require(draft.revision.projectId==projectId)
        val item=draft.items.single { it.itemId==itemId }
        StrictCostBoqPayloadValidator().validateItem(item)
        require(item.quantityOrigin==QuantityOrigin.MANUAL.name && item.quantityText!=null)
        val payload=com.google.gson.JsonParser.parseString(item.quantityEvidencePayload).asJsonObject
        require(payload.getAsJsonArray("missingInputs").size()==0)
        payload.addProperty("reviewerConfirmed",true)
        val reviewed=item.copy(quantityEvidencePayload=payload.toString(),quantityReviewConfirmed=true)
        StrictCostBoqPayloadValidator().validateItem(reviewed)
        return revision(projectId,saved,draft.items.map { if(it.itemId==itemId) reviewed else it },now,"Confirm manual quantity",draft.revision.currency)
    }

    private fun revision(projectId:Long,saved:SavedCostBoq?,items:List<CostBoqItemEntity>,now:Long,reason:String,currency:String?):SavedCostBoq {
        require(saved==null || saved.revision.projectId==projectId)
        val id=UUID.randomUUID().toString()
        val header=CostBoqRevisionEntity(projectId,id,"pending",saved?.revision?.revisionId,reason,currency,null,now)
        val candidate=SavedCostBoq(header,items.map { it.copy(projectId=projectId,revisionId=id,contentHash="pending") })
        val hash=RoomCostBoqRepository.contentHash(candidate)
        return candidate.copy(revision=header.copy(contentHash=hash),items=candidate.items.map { it.copy(contentHash=hash) })
    }
}
