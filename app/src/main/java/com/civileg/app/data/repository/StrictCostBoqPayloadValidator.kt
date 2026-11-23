package com.civileg.app.data.repository

import com.civileg.app.db.costcontrol.CostBoqItemEntity
import com.civileg.app.domain.repository.CostBoqPayloadValidator
import com.civileg.core.costcontrol.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.time.LocalDate

/** Strict schema-v1 payload validation draft. No reflection/default-object decoding or formula execution. */
class StrictCostBoqPayloadValidator : CostBoqPayloadValidator {
    override fun validateItem(item: CostBoqItemEntity) {
        require((item.analysisPayload?.length ?: 0) <= 1_000_000 && item.quantityEvidencePayload.length <= 100_000)
        item.analysisPayload?.let { payload ->
        val root = objectOf(parseStrict(payload), setOf("version", "resultNodeId", "nodes"))
        require(string(root, "version") == "1")
        val nodesElement = root.get("nodes")
        require(nodesElement.isJsonArray && nodesElement.asJsonArray.size() in 1..2000)
        val nodes = nodesElement.asJsonArray.map { entry ->
            require(entry.isJsonObject)
            val type = string(entry.asJsonObject, "type")
            val common = setOf("id", "type", "source")
            val obj = when (type) {
                "resource" -> objectOf(entry, common + setOf("description", "unit", "coefficient", "rate", "priceEvidence"))
                "scaledSum" -> objectOf(entry, common + setOf("references", "factor"))
                else -> throw IllegalArgumentException("Unknown rate node type")
            }
            val source = source(obj.get("source"))
            when (type) {
                "resource" -> RateAnalysisNode.Resource(string(obj,"id"), source, string(obj,"description"), string(obj,"unit"),
                    CostDecimal(string(obj,"coefficient")), CostDecimal(string(obj,"rate")), price(obj.get("priceEvidence")))
                else -> {
                    val refs = obj.get("references")
                    require(refs.isJsonArray && refs.asJsonArray.size() in 1..2000)
                    RateAnalysisNode.ScaledSum(string(obj,"id"), source, refs.asJsonArray.map { strictString(it) }, CostDecimal(string(obj,"factor")))
                }
            }
        }
        val analysis = UnitRateAnalysis(nodes, string(root,"resultNodeId"))
        UnitRateAnalysisEngine.calculate(analysis)
        }
        val q = objectOf(parseStrict(item.quantityEvidencePayload),
            setOf("version", "origin", "quantity", "unit", "sourceReference", "sourceRevision", "method", "missingInputs", "reviewerConfirmed"))
        require(string(q,"version") == "1")
        val missing = q.get("missingInputs")
        require(missing.isJsonArray && missing.asJsonArray.size() <= 200)
        val reviewed = q.get("reviewerConfirmed")
        require(reviewed.isJsonPrimitive && reviewed.asJsonPrimitive.isBoolean)
        val evidence = BoqQuantityEvidence(QuantityOrigin.valueOf(string(q,"origin")),
            nullableString(q,"quantity")?.let(::CostDecimal), string(q,"unit"), string(q,"sourceReference"),
            nullableString(q,"sourceRevision"), string(q,"method"), missing.asJsonArray.map { strictString(it) }, reviewed.asBoolean)
        require(evidence.origin.name == item.quantityOrigin && evidence.unit == item.originalUnit &&
            evidence.quantity?.original == item.quantityText && evidence.reviewerConfirmed == item.quantityReviewConfirmed)
        // Drafts may carry missing/assumed/unconfirmed evidence. Final BOQ gate is a separate explicit action.
    }

    internal fun parseStrict(text: String): JsonElement {
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            fun scan(depth: Int) {
                require(depth <= 64) { "Payload nesting too deep" }
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject(); val names = mutableSetOf<String>()
                        while (reader.hasNext()) { require(names.add(reader.nextName())) { "Duplicate JSON field" }; scan(depth + 1) }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) scan(depth + 1); reader.endArray() }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> throw IllegalArgumentException("Malformed JSON")
                }
            }
            scan(0); require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        return JsonParser.parseString(text)
    }

    private fun source(element: JsonElement): RateAnalysisSource {
        val o = objectOf(element, setOf("workbookHash", "sheet", "cell", "formula", "label"))
        return RateAnalysisSource(BoqSourceCell(string(o,"workbookHash"),string(o,"sheet"),string(o,"cell")),
            nullableString(o,"formula"),string(o,"label"))
    }
    private fun price(element: JsonElement): RatePriceEvidence {
        val o = objectOf(element, setOf("source", "asOf", "original", "currency"))
        return RatePriceEvidence(string(o,"source"),nullableString(o,"asOf")?.let(LocalDate::parse),
            string(o,"original"),nullableString(o,"currency"))
    }
    private fun objectOf(element: JsonElement, fields: Set<String>): JsonObject {
        require(element.isJsonObject)
        return element.asJsonObject.also { require(it.keySet() == fields) { "Unknown/missing payload field" } }
    }
    private fun strictString(element: JsonElement): String {
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString) { "Expected exact text value" }
        return element.asString
    }
    private fun string(o: JsonObject, key: String) = strictString(o.get(key))
    private fun nullableString(o: JsonObject, key: String): String? = if (o.get(key).isJsonNull) null else string(o,key)
}
