package com.civileg.app.data.repository

import com.civileg.app.db.costcontrol.CostBoqDao
import com.civileg.app.domain.repository.SavedCostBoq
import com.civileg.app.viewmodel.costcontrol.BoqWorkMethod
import com.civileg.app.viewmodel.costcontrol.CostBoqItemInput
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Private recovery state, not a committed BOQ revision. No file names or caller-selected paths. */
data class CostBoqCheckpoint(val version:Int=1,val projectId:Long,val method:BoqWorkMethod,
    val base:SavedCostBoq?,val candidate:SavedCostBoq?,val inputs:Map<String,CostBoqItemInput>)

interface CostBoqCheckpointStore {
    /** Missing is distinct from corrupt/unreadable: only the former returns null. */
    fun load(projectId:Long):CostBoqCheckpoint?
    fun write(value:CostBoqCheckpoint)
    fun delete(projectId:Long)
}

/** AtomicFile owns recovery. The seam permits JVM fault tests without claiming Android proof. */
interface CostBoqAtomicStorage {
    fun read(projectId:Long,maxBytes:Int):ByteArray?
    fun replace(projectId:Long,bytes:ByteArray)
    fun delete(projectId:Long)
}

class BoundedCostBoqCheckpointStore(private val storage:CostBoqAtomicStorage):CostBoqCheckpointStore {
    private val gson=Gson().newBuilder().serializeNulls().create()
    private val strict=StrictCostBoqPayloadValidator()
    private val validator=RoomCostBoqRepository(object:CostBoqDao() {
        override suspend fun head(projectId:Long)=error("Codec only")
        override suspend fun revision(projectId:Long,revisionId:String)=error("Codec only")
        override suspend fun items(projectId:Long,revisionId:String)=error("Codec only")
        override suspend fun insertRevision(value:com.civileg.app.db.costcontrol.CostBoqRevisionEntity)=error("Codec only")
        override suspend fun insertItems(value:List<com.civileg.app.db.costcontrol.CostBoqItemEntity>)=error("Codec only")
        override suspend fun insertHead(value:com.civileg.app.db.costcontrol.CostBoqHeadEntity)=error("Codec only")
        override suspend fun updateHead(value:com.civileg.app.db.costcontrol.CostBoqHeadEntity):Int=error("Codec only")
    },strict)
    companion object { const val MAX_BYTES=4_000_000;const val MAX_ITEMS=2000;const val MAX_FIELD=20_000 }
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun validate(value:CostBoqCheckpoint,projectId:Long) {
        require(projectId>0 && value.version==1 && value.projectId==projectId)
        require(value.method in BoqWorkMethod.entries)
        // Bound aggregate source text before JSON allocation. UTF-8/JSON escaping is checked again below.
        var characters=0L
        listOfNotNull(value.base,value.candidate).forEach { snapshot ->
            require(snapshot.items.size<=MAX_ITEMS)
            val header=snapshot.revision
            listOf(header.revisionId,header.previousRevisionId,header.revisionReason,header.currency,header.asOfDate).forEach { text ->
                require((text?.length ?: 0)<=MAX_FIELD);characters+=text?.length ?: 0;require(characters<=MAX_BYTES/6)
            }
            snapshot.items.forEach { item ->
                val texts=listOf(item.itemId,item.sourceWorkbookHash,item.sourceSheet,item.sourceCell,item.displayedItemNumber,
                    item.originalDescription,item.originalUnit,item.quantityText,item.quantityEvidencePayload,item.quotedRateText,
                    item.currency,item.priceAsOfDate,item.priceSource,item.analysisPayload)
                texts.forEach { text -> characters+=text?.length ?: 0;require(characters<=MAX_BYTES/6) }
            }
        }
        value.inputs.forEach { (key,input) -> characters+=key.length+input.values().sumOf{it.length};require(characters<=MAX_BYTES/6) }
        listOfNotNull(value.base,value.candidate).forEach { snapshot ->
            require(snapshot.revision.projectId==projectId && snapshot.items.size<=MAX_ITEMS)
            validator.validate(snapshot)
        }
        require(value.inputs.size<=MAX_ITEMS)
        val candidate=value.candidate
        require(value.inputs.isEmpty() || candidate!=null)
        value.inputs.forEach { (id,input) ->
            require(candidate!!.items.any{it.itemId==id} && id.length<=MAX_FIELD)
            require(input.values().all{it.length<=MAX_FIELD})
        }
        require(candidate==null || candidate==value.base || candidate.revision.previousRevisionId==value.base?.revision?.revisionId)
    }
    override fun write(value:CostBoqCheckpoint) {
        validate(value,value.projectId)
        load(value.projectId) // Refuse to overwrite corrupt/oversized recovery until explicit discard.
        val payload=gson.toJson(value).toByteArray(StandardCharsets.UTF_8)
        require(payload.size<=MAX_BYTES-100)
        val bytes=(digest(payload)+"\n").toByteArray(StandardCharsets.US_ASCII)+payload
        storage.replace(value.projectId,bytes)
        check(load(value.projectId)==value) { "Checkpoint readback mismatch" }
    }
    override fun load(projectId:Long):CostBoqCheckpoint? {
        require(projectId>0)
        val bytes=storage.read(projectId,MAX_BYTES) ?: return null
        require(bytes.size in 66..MAX_BYTES && bytes[64]==10.toByte())
        val expected=String(bytes,0,64,StandardCharsets.US_ASCII)
        require(expected.matches(Regex("[a-f0-9]{64}")))
        val payload=bytes.copyOfRange(65,bytes.size)
        require(digest(payload)==expected) { "Checkpoint checksum mismatch" }
        val text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(payload)).toString()
        val tree=strict.parseStrict(text)
        val value=checkNotNull(gson.fromJson(tree,CostBoqCheckpoint::class.java))
        // Reject unknown fields, coercion, missing/default fields and unknown enum values.
        require(tree==JsonParser.parseString(gson.toJson(value))) { "Checkpoint schema mismatch" }
        validate(value,projectId)
        return value
    }
    override fun delete(projectId:Long) { require(projectId>0);storage.delete(projectId);check(storage.read(projectId,MAX_BYTES)==null) }
}
