package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.FrameAnalysisSettings
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.LoadCombinationFrame
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabDistribution
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal data class FrameDraftData(
    val nodes: List<FrameNode>,
    val members: List<FrameMember>,
    val nodalLoads: List<NodalLoad>,
    val memberLoads: List<MemberLoad>,
    val slabs: List<SlabPanel>,
    val settings: FrameAnalysisSettings,
    val steelFy: Double,
    /** Referential inconsistencies found when decoding; never repaired, only reported. */
    val warnings: List<String> = emptyList()
)

internal enum class FrameDraftSource { V2, V1_NAMED, V1_RELEASE_1_8_0_OBFUSCATED }

/**
 * Draft persistence without Gson reflection, so nothing depends on generic Signature
 * metadata, private holder classes or renamed fields surviving R8. Entities are built through
 * their constructors from literal keys, enums by name, with strict validation. Anything that
 * does not validate is rejected with a reason, never sanitized into something plausible.
 *
 * Entity JSON keys are the Kotlin property names of the kept domain entities (verified present
 * in the shipped v1.8.0 dex: FrameNode id/x/y/support, FrameAnalysisSettings designCode/
 * eColumn/eSteel/includeSelfWeight/loadCombinations/steelFy).
 */
internal class FrameFrameDraftInvalid(message: String) : Exception(message)

internal class FrameDraftInvalid(message: String) : Exception(message)

internal object FrameDraftCodec {
    const val FORMAT = 2

    fun encode(d: FrameDraftData): String {
        val o = JsonObject()
        o.addProperty("format", FORMAT)
        o.add("nodes", arr(d.nodes) { n ->
            JsonObject().apply {
                addProperty("id", n.id); addProperty("x", n.x); addProperty("y", n.y)
                addProperty("support", n.support.name)
            }
        })
        o.add("members", arr(d.members) { m ->
            JsonObject().apply {
                addProperty("id", m.id); addProperty("nodeI", m.nodeI); addProperty("nodeJ", m.nodeJ)
                addProperty("materialType", m.materialType.name)
                addProperty("memberType", m.memberType.name)
                m.concreteSection?.let { c ->
                    add("concreteSection", JsonObject().apply {
                        addProperty("width", c.width); addProperty("depth", c.depth)
                        addProperty("fcu", c.fcu); addProperty("fy", c.fy); addProperty("cover", c.cover)
                    })
                }
                m.steelSectionName?.let { addProperty("steelSectionName", it) }
                addProperty("name", m.name)
                addProperty("releaseI", m.releaseI); addProperty("releaseJ", m.releaseJ)
            }
        })
        o.add("nodalLoads", arr(d.nodalLoads) { l ->
            JsonObject().apply {
                addProperty("nodeId", l.nodeId); addProperty("fx", l.fx); addProperty("fy", l.fy)
                addProperty("mz", l.mz); addProperty("loadCase", l.loadCase)
            }
        })
        o.add("memberLoads", arr(d.memberLoads) { l ->
            JsonObject().apply {
                addProperty("memberId", l.memberId); addProperty("loadType", l.loadType.name)
                addProperty("value", l.value); addProperty("position", l.position)
                addProperty("loadCase", l.loadCase)
            }
        })
        o.add("slabs", arr(d.slabs) { s ->
            JsonObject().apply {
                addProperty("id", s.id)
                add("nodeIds", JsonArray().also { a -> s.nodeIds.forEach { a.add(it) } })
                addProperty("thicknessMm", s.thicknessMm); addProperty("loadKNm2", s.loadKNm2)
                addProperty("loadCase", s.loadCase); addProperty("distribution", s.distribution.name)
            }
        })
        o.add("settings", JsonObject().apply {
            addProperty("designCode", d.settings.designCode.name)
            addProperty("steelFy", d.settings.steelFy)
            addProperty("eColumn", d.settings.eColumn); addProperty("eSteel", d.settings.eSteel)
            add("loadCombinations", arr(d.settings.loadCombinations) { c ->
                JsonObject().apply {
                    addProperty("name", c.name); addProperty("dlFactor", c.dlFactor)
                    addProperty("llFactor", c.llFactor); addProperty("wlFactor", c.wlFactor)
                }
            })
            addProperty("includeSelfWeight", d.settings.includeSelfWeight)
        })
        o.addProperty("steelFy", d.steelFy)
        return o.toString()
    }

    private fun <T> arr(items: List<T>, f: (T) -> JsonObject): JsonArray =
        JsonArray().also { a -> items.forEach { a.add(f(it)) } }

    fun decode(raw: String, source: FrameDraftSource): FrameDraftData {
        try {
            val root = JsonParser.parseString(raw)
            if (!root.isJsonObject) throw FrameDraftInvalid("not an object")
            val o = root.asJsonObject
            val keys: List<String>
            when (source) {
                FrameDraftSource.V2 -> {
                    if (int(o, "format") != FORMAT) throw FrameDraftInvalid("format is not $FORMAT")
                    keys = listOf("nodes", "members", "nodalLoads", "memberLoads", "slabs", "settings", "steelFy")
                    exactKeys(o, keys + "format")
                }
                FrameDraftSource.V1_NAMED -> {
                    // Source holder at master a5c1faee serialized exactly these eight keys when
                    // not minified (version default 1). Anything else is not this format.
                    keys = listOf("nodes", "members", "nodalLoads", "memberLoads", "slabs", "settings", "steelFy")
                    exactKeys(o, keys + "version")
                    if (int(o, "version") != 1) throw FrameDraftInvalid("legacy version is not 1")
                }
                FrameDraftSource.V1_RELEASE_1_8_0_OBFUSCATED -> {
                    // Release v1.8.0 dex: holder class la0 ctor/fields a..e = five lists, f = settings,
                    // g = double, written by ua0.n() from LiveData fields b,c,d,e,f,h,i in source order
                    // (nodes, members, nodalLoads, memberLoads, slabs, settings, steelFy); `version` removed.
                    keys = listOf("a", "b", "c", "d", "e", "f", "g")
                    exactKeys(o, keys)
                }
            }
            val nodes = list(o, keys[0]).map(::node)
            val members = list(o, keys[1]).map(::member)
            val nodalLoads = list(o, keys[2]).map(::nodalLoad)
            val memberLoads = list(o, keys[3]).map(::memberLoad)
            val slabs = list(o, keys[4]).map(::slab)
            val settings = settings(obj(o, keys[5]))
            val fy = num(o, keys[6])
            val warnings = checkReferences(nodes, members, nodalLoads, memberLoads, slabs)
            return FrameDraftData(nodes, members, nodalLoads, memberLoads, slabs, settings, fy, warnings)
        } catch (e: IllegalStateException) {
            throw FrameDraftInvalid("wrong JSON shape")
        } catch (e: UnsupportedOperationException) {
            throw FrameDraftInvalid("wrong JSON shape")
        } catch (e: NumberFormatException) {
            throw FrameDraftInvalid("bad number")
        }
    }

    private fun exactKeys(o: JsonObject, allowed: List<String>) {
        val have = o.keySet()
        val missing = allowed.filter { it !in have }
        if (missing.isNotEmpty()) throw FrameDraftInvalid("missing keys $missing")
        val extra = have.filter { it !in allowed }
        if (extra.isNotEmpty()) throw FrameDraftInvalid("unexpected keys ${extra.size}")
    }

    private fun get(o: JsonObject, k: String): JsonElement {
        val e = o.get(k)
        if (e == null || e.isJsonNull) throw FrameDraftInvalid("missing/null $k")
        return e
    }
    private fun obj(o: JsonObject, k: String): JsonObject =
        get(o, k).let { if (!it.isJsonObject) throw FrameDraftInvalid("$k not object") else it.asJsonObject }
    private fun list(o: JsonObject, k: String): List<JsonObject> =
        get(o, k).let { a ->
            if (!a.isJsonArray) throw FrameDraftInvalid("$k not array")
            a.asJsonArray.map { if (!it.isJsonObject) throw FrameDraftInvalid("$k element not object") else it.asJsonObject }
        }
    private fun num(o: JsonObject, k: String): Double {
        val p = get(o, k)
        if (!p.isJsonPrimitive || !p.asJsonPrimitive.isNumber) throw FrameDraftInvalid("$k not number")
        val v = p.asDouble
        if (!v.isFinite()) throw FrameDraftInvalid("$k not finite")
        return v
    }
    private fun int(o: JsonObject, k: String): Int {
        val v = num(o, k)
        if (v != Math.rint(v) || v < Int.MIN_VALUE || v > Int.MAX_VALUE) throw FrameDraftInvalid("$k not int")
        return v.toInt()
    }
    private fun str(o: JsonObject, k: String): String {
        val p = get(o, k)
        if (!p.isJsonPrimitive || !p.asJsonPrimitive.isString) throw FrameDraftInvalid("$k not string")
        return p.asString
    }
    private fun bool(o: JsonObject, k: String): Boolean {
        val p = get(o, k)
        if (!p.isJsonPrimitive || !p.asJsonPrimitive.isBoolean) throw FrameDraftInvalid("$k not boolean")
        return p.asBoolean
    }
    private fun <E : Enum<E>> enumOf(o: JsonObject, k: String, values: Array<E>): E {
        val s = str(o, k)
        return values.firstOrNull { it.name == s } ?: throw FrameDraftInvalid("$k unknown value")
    }
    private fun optStr(o: JsonObject, k: String): String? =
        if (!o.has(k) || o.get(k).isJsonNull) null else str(o, k)

    private fun node(o: JsonObject) =
        FrameNode(int(o, "id"), num(o, "x"), num(o, "y"), enumOf(o, "support", SupportType.values()))

    private fun member(o: JsonObject): FrameMember {
        val cs = if (!o.has("concreteSection") || o.get("concreteSection").isJsonNull) null else {
            val c = obj(o, "concreteSection")
            val s = ConcreteSectionProps(num(c, "width"), num(c, "depth"), num(c, "fcu"), num(c, "fy"), num(c, "cover"))
            s
        }
        return FrameMember(
            int(o, "id"), int(o, "nodeI"), int(o, "nodeJ"),
            enumOf(o, "materialType", FrameMaterialType.values()),
            enumOf(o, "memberType", FrameMemberType.values()),
            cs, optStr(o, "steelSectionName"), str(o, "name"), bool(o, "releaseI"), bool(o, "releaseJ")
        )
    }

    private fun nodalLoad(o: JsonObject) =
        NodalLoad(int(o, "nodeId"), num(o, "fx"), num(o, "fy"), num(o, "mz"), str(o, "loadCase"))

    private fun memberLoad(o: JsonObject) = MemberLoad(
        int(o, "memberId"), enumOf(o, "loadType", MemberLoadType.values()),
        num(o, "value"), num(o, "position"), str(o, "loadCase")
    )

    private fun slab(o: JsonObject): SlabPanel {
        val ids = get(o, "nodeIds").let { a ->
            if (!a.isJsonArray) throw FrameDraftInvalid("nodeIds not array")
            a.asJsonArray.map { e ->
                if (!e.isJsonPrimitive || !e.asJsonPrimitive.isNumber) throw FrameDraftInvalid("nodeIds element")
                val v = e.asDouble
                if (!v.isFinite() || v != Math.rint(v) || v < Int.MIN_VALUE || v > Int.MAX_VALUE)
                    throw FrameDraftInvalid("nodeIds element not int")
                v.toInt()
            }
        }
        val t = num(o, "thicknessMm")
        return SlabPanel(int(o, "id"), ids, t, num(o, "loadKNm2"), str(o, "loadCase"),
            enumOf(o, "distribution", SlabDistribution.values()))
    }

    private fun settings(o: JsonObject): FrameAnalysisSettings {
        val combos = list(o, "loadCombinations").map {
            LoadCombinationFrame(str(it, "name"), num(it, "dlFactor"), num(it, "llFactor"), num(it, "wlFactor"))
        }
        val s = FrameAnalysisSettings(
            designCode = enumOf(o, "designCode", DesignCode.values()),
            steelFy = num(o, "steelFy"), eColumn = num(o, "eColumn"), eSteel = num(o, "eSteel"),
            loadCombinations = combos, includeSelfWeight = bool(o, "includeSelfWeight")
        )
        return s
    }

    /**
     * Only structure and types reject. Solved-graph constraints the editor itself does not
     * enforce (addMember/addNodalLoad do not check nodes) are reachable incomplete edits: they
     * are restored as stored and reported, never repaired and never silently passed.
     */
    private fun checkReferences(
        nodes: List<FrameNode>, members: List<FrameMember>, nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>, slabs: List<SlabPanel>
    ): List<String> {
        val nodeIds = nodes.map { it.id }.toSet()
        val memberIds = members.map { it.id }.toSet()
        val w = ArrayList<String>()
        if (nodeIds.size != nodes.size) w.add("duplicate node ids")
        if (memberIds.size != members.size) w.add("duplicate member ids")
        if (slabs.map { it.id }.toSet().size != slabs.size) w.add("duplicate slab ids")
        val badMembers = members.count { it.nodeI == it.nodeJ || it.nodeI !in nodeIds || it.nodeJ !in nodeIds }
        if (badMembers > 0) w.add("$badMembers member(s) reference missing or identical nodes")
        val badNodal = nodalLoads.count { it.nodeId !in nodeIds }
        if (badNodal > 0) w.add("$badNodal nodal load(s) reference a missing node")
        val badMemberLoads = memberLoads.count { it.memberId !in memberIds }
        if (badMemberLoads > 0) w.add("$badMemberLoads member load(s) reference a missing member")
        val badSlabs = slabs.count { sl -> sl.nodeIds.size != 4 || sl.nodeIds.toSet().size != 4 || sl.nodeIds.any { it !in nodeIds } }
        if (badSlabs > 0) w.add("$badSlabs slab(s) reference missing or repeated nodes")
        return w
    }
}
