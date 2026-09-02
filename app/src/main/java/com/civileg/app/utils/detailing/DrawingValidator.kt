package com.civileg.app.utils.detailing

import kotlin.math.abs
import kotlin.math.hypot

// ─────────────────────────────────────────────────────────────────────────────
// CIVILEG DRAWING VALIDATOR
//
// Validates a flat entity list + metadata before the DxfWriter serialises it.
// Returns a DrawingValidationResult.  Callers MUST check result.passed before
// writing a production DXF file.
//
// Error   = hard block: drawing must NOT be exported.
// Warning = informational: drawing may still be exported but needs review.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Result types
// ─────────────────────────────────────────────────────────────────────────────

enum class ValidationSeverity { ERROR, WARNING }

data class ValidationIssue(
    val severity: ValidationSeverity,
    val code: String,       // machine-readable rule code e.g. "NAN_COORDINATE"
    val message: String
)

data class DrawingValidationResult(
    val passed: Boolean,    // true only when no ERRORs
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>
) {
    val allIssues: List<ValidationIssue> get() = errors + warnings

    fun toReport(): String = buildString {
        appendLine("DRAWING VALIDATION REPORT")
        appendLine("PASSED: $passed")
        appendLine("ERRORS: ${errors.size}   WARNINGS: ${warnings.size}")
        appendLine("─".repeat(60))
        if (errors.isEmpty() && warnings.isEmpty()) {
            appendLine("No issues found.")
        }
        errors.forEach   { appendLine("[ERROR]   [${it.code}] ${it.message}") }
        warnings.forEach { appendLine("[WARNING] [${it.code}] ${it.message}") }
    }

    /** Throw [DrawingValidationException] if validation failed. */
    fun enforceOrThrow() {
        if (!passed) throw DrawingValidationException(
            "Drawing validation failed with ${errors.size} error(s):\n" +
            errors.joinToString("\n") { "  • [${it.code}] ${it.message}" }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Validator
// ─────────────────────────────────────────────────────────────────────────────

object DrawingValidator {

    // ─── Rule codes ──────────────────────────────────────────────────────────
    private const val NAN_COORDINATE        = "NAN_COORDINATE"
    private const val INF_COORDINATE        = "INF_COORDINATE"
    private const val ZERO_LENGTH_LINE      = "ZERO_LENGTH_LINE"
    private const val NEGATIVE_DIMENSION    = "NEGATIVE_DIMENSION"
    private const val MISSING_CONCRETE      = "MISSING_CONCRETE"
    private const val MISSING_REBAR         = "MISSING_REBAR"
    private const val MISSING_DIMENSIONS    = "MISSING_DIMENSIONS"
    private const val MISSING_TITLE_BLOCK   = "MISSING_TITLE_BLOCK"
    private const val MISSING_UNITS         = "MISSING_UNITS"
    private const val MISSING_SECTION_MARKER = "MISSING_SECTION_MARKER"
    private const val DUPLICATE_BAR_MARK    = "DUPLICATE_BAR_MARK"
    private const val INVALID_BBS_LENGTH    = "INVALID_BBS_LENGTH"
    private const val INVALID_BAR_SPACING   = "INVALID_BAR_SPACING"
    private const val UNSAFE_DESIGN         = "UNSAFE_DESIGN"
    private const val EMPTY_DRAWING         = "EMPTY_DRAWING"
    private const val UNREFERENCED_LAYER    = "UNREFERENCED_LAYER"
    private const val SUSPICIOUS_GEOMETRY   = "SUSPICIOUS_GEOMETRY"

    /**
     * Full structural drawing validation.
     *
     * @param entities      All CAD entities in the drawing (pre-expansion).
     * @param titleBlock    Title block — null triggers a MISSING_TITLE_BLOCK error.
     * @param barSchedule   Optional BBS rows — validated if provided.
     * @param isSafeDesign  If false an UNSAFE_DESIGN error is added.
     * @param requireSectionMarkers  If true and no CadSectionMarker is found, adds WARNING.
     */
    fun validateDrawing(
        entities: List<CadEntity>,
        titleBlock: TitleBlock? = null,
        barSchedule: List<RebarScheduleRow> = emptyList(),
        isSafeDesign: Boolean = true,
        requireSectionMarkers: Boolean = true
    ): DrawingValidationResult {

        val errors   = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        fun err(code: String, msg: String) =
            errors.add(ValidationIssue(ValidationSeverity.ERROR, code, msg))
        fun warn(code: String, msg: String) =
            warnings.add(ValidationIssue(ValidationSeverity.WARNING, code, msg))

        // 1. Empty drawing
        if (entities.isEmpty()) {
            err(EMPTY_DRAWING, "Entity list is empty — nothing to draw.")
            return DrawingValidationResult(false, errors, warnings)
        }

        // 2. Unsafe design flag
        if (!isSafeDesign) {
            err(UNSAFE_DESIGN, "Design is marked unsafe (isSafe=false). " +
                "Do not produce construction drawings from an unsafe design.")
        }

        // 3. Title block
        if (titleBlock == null) {
            err(MISSING_TITLE_BLOCK, "No TitleBlock provided. Every production " +
                "drawing must have a title block with project/revision/date/scale.")
        } else {
            if (titleBlock.project.isBlank())
                warn(MISSING_TITLE_BLOCK, "TitleBlock.project is blank.")
            if (titleBlock.drawingNumber.isBlank())
                warn(MISSING_TITLE_BLOCK, "TitleBlock.drawingNumber is blank.")
            if (titleBlock.scale.isBlank())
                warn(MISSING_UNITS, "TitleBlock.scale is blank — drawing scale not annotated.")
        }

        // 4. NaN / Infinity coordinate check
        validateCoordinates(entities, errors)

        // 5. Zero-length lines
        entities.filterIsInstance<CadLine>().forEach { line ->
            if (line.length < 1e-6) {
                err(ZERO_LENGTH_LINE,
                    "Zero-length line detected at (${fmt(line.x1)}, ${fmt(line.y1)}).")
            }
        }

        // 6. Invalid dimensions (negative measured length)
        entities.filterIsInstance<CadDimLinear>().forEach { dim ->
            if (dim.measuredLength < 1e-6) {
                err(NEGATIVE_DIMENSION,
                    "CadDimLinear has zero or negative length: ${fmt(dim.measuredLength)} mm. " +
                    "Points: (${fmt(dim.x1)},${fmt(dim.y1)}) → (${fmt(dim.x2)},${fmt(dim.y2)}).")
            }
        }

        // 7. Missing concrete outline
        val hasConcrete = entities.any { e ->
            e is CadPolyline && (e.layer == CadLayers.CONC || e.layer == CadLayers.FOUNDATION ||
                e.layer == CadLayers.FOOTING || e.layer == CadLayers.WALL)
        }
        if (!hasConcrete) {
            err(MISSING_CONCRETE,
                "No concrete outline found (expected a CONC/FOUNDATION/FOOTING/WALL layer polyline).")
        }

        // 8. Missing reinforcement
        val hasRebar = entities.any { e ->
            e.layer == CadLayers.REBAR || e.layer == CadLayers.STIRRUP ||
            e is CadRebarSymbol || e is CadCircle && e.layer == CadLayers.REBAR
        }
        if (!hasRebar) {
            warn(MISSING_REBAR,
                "No reinforcement entities found. If this is a plain concrete element, ignore this warning.")
        }

        // 9. Missing dimension lines
        val hasDims = entities.any { e ->
            e is CadDimLinear || e.layer == CadLayers.DIM || e.layer == CadLayers.REBAR_DIM
        }
        if (!hasDims) {
            warn(MISSING_DIMENSIONS,
                "No dimension entities found. Drawings should have at least overall dimensions.")
        }

        // 10. Missing unit annotation (text containing "mm" or "kN" or "kPa")
        val hasUnitText = entities.filterIsInstance<CadText>().any { t ->
            t.text.contains("mm") || t.text.contains("kN") || t.text.contains("kPa") ||
            t.text.contains("MPa") || t.text.contains("m²") || t.text.contains("m3")
        }
        val hasUnitMText = entities.filterIsInstance<CadMText>().any { mt ->
            mt.lines.any { l ->
                l.contains("mm") || l.contains("kN") || l.contains("kPa") || l.contains("MPa")
            }
        }
        if (!hasUnitText && !hasUnitMText) {
            warn(MISSING_UNITS,
                "No unit annotations found in text entities (mm / kN / kPa / MPa).")
        }

        // 11. Section markers
        if (requireSectionMarkers) {
            val hasSectionMarker = entities.any { e ->
                e is CadSectionMarker ||
                (e is CadText && e.text.matches(Regex("SECTION [A-Z]-[A-Z].*")))
            }
            if (!hasSectionMarker) {
                warn(MISSING_SECTION_MARKER,
                    "No section marker (CadSectionMarker or 'SECTION A-A' text) found. " +
                    "Elevation drawings should reference at least one cross-section.")
            }
        }

        // 12. Duplicate bar marks in BBS
        if (barSchedule.isNotEmpty()) {
            val markCounts = barSchedule.groupBy { it.mark }
            markCounts.filter { (_, rows) -> rows.size > 1 }.forEach { (mark, rows) ->
                err(DUPLICATE_BAR_MARK,
                    "Bar mark '$mark' appears ${rows.size} times in BBS. " +
                    "Each mark must be unique within a drawing package.")
            }

            // 13. Invalid BBS cutting lengths
            barSchedule.forEach { row ->
                if (row.cuttingLengthMm <= 0.0) {
                    err(INVALID_BBS_LENGTH,
                        "BBS row mark='${row.mark}': cuttingLengthMm=${fmt(row.cuttingLengthMm)} <= 0.")
                }
                if (row.qty <= 0) {
                    err(INVALID_BBS_LENGTH,
                        "BBS row mark='${row.mark}': qty=${row.qty} <= 0.")
                }
                if (row.diameterMm < 6 || row.diameterMm > 50) {
                    warn(INVALID_BBS_LENGTH,
                        "BBS row mark='${row.mark}': diameterMm=${row.diameterMm} outside " +
                        "typical range 6–50 mm.")
                }
            }
        }

        // 14. Suspicious geometry (coordinates far outside expected structural range)
        val bbox = boundingBox(entities.filterNot { it is CadInteractionDiagram })
        if (bbox[0] != Double.MAX_VALUE) {
            val widthMm = bbox[2] - bbox[0]
            val heightMm = bbox[3] - bbox[1]
            if (widthMm > 5_000_000 || heightMm > 5_000_000) {
                warn(SUSPICIOUS_GEOMETRY,
                    "Drawing extents ${fmt(widthMm)} × ${fmt(heightMm)} mm are very large " +
                    "(> 5000 m). Verify units are in mm.")
            }
            if (widthMm < 1.0 && heightMm < 1.0) {
                warn(SUSPICIOUS_GEOMETRY,
                    "Drawing extents ${fmt(widthMm)} × ${fmt(heightMm)} mm are very small. " +
                    "Verify coordinates are not in metres instead of mm.")
            }
        }

        // 15. Invalid bar spacing in rebarSymbols / stirrup description texts
        entities.filterIsInstance<CadText>().forEach { t ->
            // e.g. "Ø8@0" or spacing = 0 in text like "5Ø8/m'" — basic heuristic
            val spacingMatch = Regex("@(\\d+)").find(t.text)
            if (spacingMatch != null) {
                val sp = spacingMatch.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
                if (sp <= 0) {
                    err(INVALID_BAR_SPACING,
                        "Text '${t.text}' contains zero spacing '@$sp'.")
                } else if (sp > 500) {
                    warn(INVALID_BAR_SPACING,
                        "Text '${t.text}' spacing @$sp mm may be too large (> 500 mm).")
                }
            }
        }

        val passed = errors.isEmpty()
        return DrawingValidationResult(passed, errors.toList(), warnings.toList())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Coordinate NaN / Infinity deep scan
    // ─────────────────────────────────────────────────────────────────────────

    private fun validateCoordinates(
        entities: List<CadEntity>,
        errors: MutableList<ValidationIssue>
    ) {
        fun check(v: Double, ctx: String): Boolean {
            if (v.isNaN()) {
                errors.add(ValidationIssue(ValidationSeverity.ERROR, NAN_COORDINATE,
                    "NaN coordinate detected in $ctx"))
                return false
            }
            if (v.isInfinite()) {
                errors.add(ValidationIssue(ValidationSeverity.ERROR, INF_COORDINATE,
                    "Infinite coordinate detected in $ctx"))
                return false
            }
            return true
        }

        entities.forEach { e ->
            val ctx = "${e::class.simpleName}@layer=${e.layer}"
            when (e) {
                is CadLine -> {
                    check(e.x1, "$ctx x1"); check(e.y1, "$ctx y1")
                    check(e.x2, "$ctx x2"); check(e.y2, "$ctx y2")
                }
                is CadPolyline -> e.points.forEachIndexed { i, p ->
                    check(p.x, "$ctx pt[$i].x"); check(p.y, "$ctx pt[$i].y")
                }
                is CadCircle -> {
                    check(e.cx, "$ctx cx"); check(e.cy, "$ctx cy")
                    check(e.radius, "$ctx radius")
                }
                is CadArc -> {
                    check(e.cx, "$ctx cx"); check(e.cy, "$ctx cy")
                    check(e.radius, "$ctx radius")
                }
                is CadText -> { check(e.x, "$ctx x"); check(e.y, "$ctx y") }
                is CadMText -> { check(e.x, "$ctx x"); check(e.y, "$ctx y") }
                is CadLeader -> e.vertices.forEachIndexed { i, v ->
                    check(v.x, "$ctx v[$i].x"); check(v.y, "$ctx v[$i].y")
                }
                is CadDimLinear -> {
                    check(e.x1, "$ctx x1"); check(e.y1, "$ctx y1")
                    check(e.x2, "$ctx x2"); check(e.y2, "$ctx y2")
                }
                is CadHatch -> e.boundary.forEach { loop ->
                    loop.points.forEachIndexed { i, p ->
                        check(p.x, "$ctx boundary pt[$i].x")
                        check(p.y, "$ctx boundary pt[$i].y")
                    }
                }
                else -> { /* composites validated after expansion */ }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Quick DXF text validation (post-write)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lightweight check on a generated DXF string.
     * Use this to gate file-save after [DxfWriter.write()].
     */
    fun validateDxfText(dxfContent: String): DrawingValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        fun err(code: String, msg: String) =
            errors.add(ValidationIssue(ValidationSeverity.ERROR, code, msg))

        val required = listOf(
            "SECTION" to "HEADER",
            "SECTION" to "TABLES",
            "SECTION" to "BLOCKS",
            "SECTION" to "ENTITIES",
            "SECTION" to "OBJECTS"
        )
        required.forEach { (_, name) ->
            if (!dxfContent.contains("2\r\n$name\r\n"))
                err("MISSING_DXF_SECTION", "DXF output is missing required section: $name")
        }
        if (!dxfContent.contains("0\r\nEOF"))
            err("MISSING_DXF_EOF", "DXF output is missing EOF terminator.")
        if (dxfContent.contains("NaN"))
            err("NAN_IN_DXF", "Literal 'NaN' found in DXF output.")
        if (dxfContent.contains("Infinity"))
            err("INF_IN_DXF", "Literal 'Infinity' found in DXF output.")
        if (!dxfContent.contains("AcDbEntity"))
            err("MISSING_DXF_ENTITY", "No AcDbEntity subclass found — DXF may be empty.")
        if (dxfContent.length < 500)
            warnings.add(ValidationIssue(ValidationSeverity.WARNING, "SMALL_DXF",
                "DXF output is suspiciously small (${dxfContent.length} chars)."))

        return DrawingValidationResult(errors.isEmpty(), errors, warnings)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun fmt(v: Double) = "%.2f".format(v)
}
