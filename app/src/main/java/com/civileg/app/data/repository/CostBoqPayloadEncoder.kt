package com.civileg.app.data.repository

import com.civileg.core.costcontrol.*
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject

/** Schema-v1 exact-text encoder paired with StrictCostBoqPayloadValidator. No formulas are executed. */
object CostBoqPayloadEncoder {
    private fun JsonObject.text(name: String, value: String?) {
        if (value == null) add(name, JsonNull.INSTANCE) else addProperty(name, value)
    }
    fun analysis(value: UnitRateAnalysis): String {
        UnitRateAnalysisEngine.calculate(value)
        val root = JsonObject(); root.text("version","1"); root.text("resultNodeId",value.resultNodeId)
        val array = JsonArray()
        value.nodes.forEach { node ->
            val obj = JsonObject(); obj.text("id",node.id)
            val src = JsonObject(); src.text("workbookHash",node.source.source.workbookFingerprint)
            src.text("sheet",node.source.source.sheet); src.text("cell",node.source.source.cell)
            src.text("formula",node.source.originalFormula); src.text("label",node.source.originalLabel)
            obj.add("source",src)
            when(node) {
                is RateAnalysisNode.Resource -> {
                    obj.text("type","resource"); obj.text("description",node.description); obj.text("unit",node.resourceUnit)
                    obj.text("coefficient",node.coefficient.original); obj.text("rate",node.resourceRate.original)
                    val p = JsonObject(); p.text("source",node.priceEvidence.sourceDescription)
                    p.text("asOf",node.priceEvidence.asOf?.toString()); p.text("original",node.priceEvidence.originalText)
                    p.text("currency",node.priceEvidence.currency); obj.add("priceEvidence",p)
                }
                is RateAnalysisNode.ScaledSum -> {
                    obj.text("type","scaledSum"); obj.text("factor",node.factor.original)
                    obj.add("references",JsonArray().also { a -> node.references.forEach(a::add) })
                }
            }
            array.add(obj)
        }
        root.add("nodes",array); return root.toString()
    }
    fun quantity(value: BoqQuantityEvidence): String {
        val obj = JsonObject(); obj.text("version","1"); obj.text("origin",value.origin.name)
        obj.text("quantity",value.quantity?.original); obj.text("unit",value.unit)
        obj.text("sourceReference",value.sourceReference); obj.text("sourceRevision",value.sourceRevision)
        obj.text("method",value.method); obj.addProperty("reviewerConfirmed",value.reviewerConfirmed)
        obj.add("missingInputs",JsonArray().also { a -> value.missingInputs.forEach(a::add) })
        return obj.toString()
    }
}
