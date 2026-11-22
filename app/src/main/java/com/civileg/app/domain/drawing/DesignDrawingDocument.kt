package com.civileg.app.domain.drawing

import java.util.Collections

/** View names describe orientation, never a substitute for member reinforcement cuts. */
enum class DrawingViewKind(val title: String) {
    PLAN("Plan"), SIDE("Side"), ELEVATION("Elevation"), SECTION("Section"),
    DETAIL("Detail"), ISOMETRIC("3D reinforcement projection")
}

/**
 * A caller-supplied view of one calculated member. Coordinates (including text heights)
 * are in mm, x right and y up. Bounds include dimensions and labels, not only concrete.
 * This class neither designs bars nor infers missing geometry or reinforcement.
 */
class DesignDrawingView(
    val id: String,
    val kind: DrawingViewKind,
    scene: DrawingScene
) {
    val scene: DrawingScene = scene.snapshot()
    init {
        require(id.isNotBlank() && id.length <= 128) { "Drawing view ID must contain 1 to 128 characters" }
        DrawingGeometry.validate(this.scene)
        require(this.scene.primitives.any { primitive ->
            when (primitive) {
                is Primitive.Line -> primitive.p1 != primitive.p2
                is Primitive.Polyline -> primitive.points.distinct().size >= 2
                is Primitive.Circle, is Primitive.Arc, is Primitive.RebarDot -> true
                else -> false
            }
        }) { "A design view requires non-annotation geometry, not labels or dimensions alone" }
    }
}

/**
 * One immutable input/result revision shared by UI, report and any 3D view.
 * The producer must bind revisionId to a persistent digest of the frozen input/result
 * snapshot and supply actual geometry, positions and method provenance. A document
 * contract is not proof of engineering completeness. Missing checks remain visible.
 */
class DesignDrawingDocument(
    val revisionId: String,
    val memberMark: String,
    val methodReference: String,
    views: List<DesignDrawingView>,
    incompleteChecks: List<String> = emptyList()
) {
    val views: List<DesignDrawingView> = Collections.unmodifiableList(views.toList())
    val incompleteChecks: List<String> = Collections.unmodifiableList(incompleteChecks.toList())
    init {
        require(revisionId.isNotBlank() && memberMark.isNotBlank() && methodReference.isNotBlank()) {
            "Drawing revision, member mark and method provenance are required"
        }
        require(revisionId.length <= 256 && memberMark.length <= 128) { "Drawing revision or member mark exceeds page-label limits" }
        require(this.views.map { it.id }.distinct().size == this.views.size) { "Duplicate drawing view ID" }
        for (kind in listOf(DrawingViewKind.PLAN, DrawingViewKind.SIDE, DrawingViewKind.ELEVATION)) {
            require(this.views.count { it.kind == kind } == 1) { "Exactly one ${kind.title} view is required" }
        }
        require(this.incompleteChecks.none { it.isBlank() }) { "Incomplete checks must be named" }
    }

    fun orderedViews(): List<DesignDrawingView> = views.sortedBy {
        when (it.kind) {
            DrawingViewKind.PLAN -> 0
            DrawingViewKind.SIDE -> 1
            DrawingViewKind.ELEVATION -> 2
            else -> 3
        }
    }
}

private fun DrawingScene.snapshot(): DrawingScene = copy(
    primitives = Collections.unmodifiableList(primitives.map {
        when (it) {
            is Primitive.Polyline -> it.copy(points = Collections.unmodifiableList(it.points.toList()))
            is Primitive.Leader -> it.copy(points = Collections.unmodifiableList(it.points.toList()))
            else -> it
        }
    }),
    layers = Collections.unmodifiableList(layers.toList())
)
