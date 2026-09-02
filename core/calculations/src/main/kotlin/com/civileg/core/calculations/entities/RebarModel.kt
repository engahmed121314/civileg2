package com.civileg.core.calculations.entities

/**
 * CIVILEG RebarModel (roadmap pillar) — per-bar identity + traceability.
 *
 * A [RebarModel] expands a grouped [ReinforcementSet] into one
 * [BarInstance] per physical bar, each with a globally unique [BarInstance.id].
 * It is the single contract used by BBS, PDF, DXF and QA to refer to
 * individual bars; no downstream view re-derives bar identity.
 *
 * Geometry is deliberately NOT recomputed here: cut length [BarInstance.totalLengthMm]
 * and hooks stay the single-source values already produced where the design is
 * computed (e.g. [DrawingModelBuilder] / the detailing engines). Bending-time
 * bend-deduction math lives in the app detailing engine (`BarBendingEngine`),
 * which consumes this model's cut lengths — no duplication of that rule set.
 */
data class BarInstance(
    val id: String,
    val mark: String,
    val diameter: Double,
    val totalLengthMm: Double,
    val shape: String,
    val element: String,
    val codeReference: String,
    val quantityIndex: Int,
    val category: String = "main",
    val shapeCode: String? = null,
    val spacing: Double? = null,
    val hookType: String? = null,
    val hookLength: Double? = null
) {
    val totalLengthM: Double get() = totalLengthMm / 1000.0
}

/** Immutable per-bar model with invariants enforced at build time. */
class RebarModel private constructor(
    val bars: List<BarInstance>
) {
    /** Every unique bar id in the model. */
    val ids: Set<String> get() = bars.mapTo(linkedSetOf()) { it.id }

    /** Map: element -> its bar instances (in insertion order). */
    val byElement: Map<String, List<BarInstance>> get() = bars.groupBy { it.element }

    /** Map: diameter -> its bar instances. */
    val byDiameter: Map<Double, List<BarInstance>> get() = bars.groupBy { it.diameter }

    /** Map: element -> unique marks used in that element. */
    val elementMarks: Map<String, Set<String>>
        get() = byElement.mapValues { (_, instances) -> instances.mapTo(linkedSetOf()) { it.mark } }

    /** Total steel weight (kg) = Σ (L_m × 0.006165·d²). */
    val totalWeightKg: Double
        get() = bars.sumOf { bar ->
            (bar.totalLengthM) * 0.006165 * bar.diameter * bar.diameter
        }

    /** Stable BBS-style text for PDF/DXF/QA — English only (ADR-009). */
    val scheduleText: String
        get() = buildString {
            appendLine("BAR SCHEDULE")
            appendLine("────────────")
            bars.sortedWith(compareBy({ it.element }, { it.mark }, { it.quantityIndex }))
                .forEach { b ->
                    val sp = b.spacing?.let { "  @$it mm" } ?: ""
                    val sc = b.shapeCode?.let { "  (§$it)" } ?: ""
                    appendLine("${b.id}  Ø${diameterLabel(b.diameter)}$sp$sc  L=${fmt(b.totalLengthMm)}  ${b.element}")
                }
        }

    companion object {
        /** Build with strict invariants: finite positive lengths, unique ids, non-empty never implied. */
        fun build(bars: List<BarInstance>): RebarModel {
            bars.forEach { b ->
                require(b.id.isNotBlank()) { "RebarModel: blank bar id" }
                require(b.totalLengthMm > 0.0 && !b.totalLengthMm.isNaN() && !b.totalLengthMm.isInfinite()) {
                    "RebarModel: invalid length ${b.totalLengthMm} on '${b.id}'"
                }
                require(b.diameter > 0.0 && !b.diameter.isNaN() && !b.diameter.isInfinite()) {
                    "RebarModel: invalid diameter ${b.diameter} on '${b.id}'"
                }
            }
            val dupes = bars.groupingBy { it.id }.eachCount().filter { it.value > 1 }.keys
            require(dupes.isEmpty()) { "RebarModel: duplicate bar ids ${dupes.joinToString()} " }
            return RebarModel(bars)
        }
    }
}

/** Expand a grouped [ReinforcementSet] into one [BarInstance] per physical bar. */
fun ReinforcementSet.toRebarModel(): RebarModel {
    val instances = mutableListOf<BarInstance>()
    fun expand1(
        source: ReinforcementBar,
        fallbackElement: String,
        category: String,
        shapeCode: String?
    ) {
        val elementId = source.element.ifBlank { fallbackElement }
        repeat(source.quantity) { k ->
            instances += BarInstance(
                id = "${source.mark}-$elementId-${k + 1}",
                mark = source.mark,
                diameter = source.diameter,
                totalLengthMm = source.totalLengthMm,
                shape = source.shape,
                element = elementId,
                codeReference = source.codeReference,
                quantityIndex = k + 1,
                category = category,
                shapeCode = shapeCode,
                spacing = source.spacing,
                hookType = source.hookType,
                hookLength = source.hookLength
            )
        }
    }

    mainTensionBars.forEach { expand1(it, it.element.ifBlank { "beam" }, "tension", "00") }
    mainCompressionBars.forEach { expand1(it, it.element.ifBlank { "beam" }, "compression", "00") }
    stirrups.forEach { expand1(it, it.element.ifBlank { "beam" }, "stirrup", "22") }
    distributionBars.forEach { expand1(it, it.element.ifBlank { "slab" }, "distribution", "00") }
    return RebarModel.build(instances)
}

/** Collapse a [RebarModel] back into the grouped [ReinforcementSet] contract. */
fun RebarModel.toReinforcementSet(): ReinforcementSet {
    val collapse: (List<BarInstance>) -> List<ReinforcementBar> = { group ->
        group.groupBy { it.mark }.map { (mark, instances) ->
            val first = instances.first()
            ReinforcementBar(
                mark = mark,
                diameter = first.diameter,
                totalLengthMm = instances.maxOf { it.totalLengthMm },
                shape = first.shape,
                element = first.element,
                codeReference = first.codeReference,
                quantity = instances.size,
                spacing = first.spacing,
                hookType = first.hookType,
                hookLength = first.hookLength
            )
        }
    }
    val byCategory = bars.groupBy { it.category }
    return ReinforcementSet(
        mainTensionBars = collapse(byCategory["tension"].orEmpty()),
        mainCompressionBars = collapse(byCategory["compression"].orEmpty()),
        stirrups = collapse(byCategory["stirrup"].orEmpty()),
        distributionBars = collapse(byCategory["distribution"].orEmpty())
    )
}