package com.civileg.app.utils.exporters

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DxfExporter - Public-facing API for exporting structural design results to DXF format.
 *
 * Provides a simple, high-level interface for ViewModels and Screens to call.
 * Internally delegates to [DxfExportEngine] and [DxfStructuralExporter].
 *
 * Usage:
 * ```
 * val content = DxfExporter.exportBeamSection(beamData)
 * // or
 * DxfExporter.exportToFile(beamData, DxfExporter.ElementType.BEAM_RECT, file)
 * ```
 *
 * Supports both Arabic and English annotations (English is used in DXF for
 * encoding compatibility; Arabic labels are transliterated).
 */
public object DxfExporter {

    // ------------------------------------------------------------------
    // Element type enumeration
    // ------------------------------------------------------------------

    enum class ElementType {
        BEAM_RECT,
        BEAM_TEE,
        COLUMN_RECT,
        COLUMN_CIRC,
        SLAB_SECTION,
        FOOTING_PLAN,
        FOOTING_SECTION,
        STAIRCASE_SECTION,
        STAIRCASE_PLAN,
        RETAINING_WALL,
        WATER_TANK,
        STEEL_I_SECTION,
        STEEL_CHANNEL,
        STEEL_ANGLE,
        STEEL_BOX,
        BMD,
        SFD,
        CUSTOM
    }

    // ------------------------------------------------------------------
    // Data classes for accepting design results from ViewModels
    // ------------------------------------------------------------------

    /**
     * Generic design result that can represent any structural element.
     * Fields are optional; only the ones relevant to the element type will be used.
     */
    data class DesignResult(
        // --- General ---
        val elementType: ElementType = ElementType.CUSTOM,
        val titleEn: String = "STRUCTURAL DETAIL",
        val titleAr: String = "",
        val scale: String = "1:1",
        val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        val sheetNum: String = "1",
        val useArabicLabels: Boolean = false,

        // --- Beam / Column ---
        val width: Double = 0.0,
        val depth: Double = 0.0,
        val flangeWidth: Double = 0.0,
        val flangeThickness: Double = 0.0,
        val webWidth: Double = 0.0,
        val totalHeight: Double = 0.0,

        // --- Reinforcement ---
        val topBars: String = "",
        val bottomBars: String = "",
        val leftBars: String = "",
        val rightBars: String = "",
        val longitudinalBars: String = "",
        val stirrupsTies: String = "",
        val spiralTies: String = "",
        val topBarsShort: String = "",
        val topBarsLong: String = "",
        val bottomBarsShort: String = "",
        val bottomBarsLong: String = "",
        val wallBarsOuter: String = "",
        val wallBarsInner: String = "",
        val baseBarsTop: String = "",
        val baseBarsBot: String = "",

        // --- Concrete / Cover ---
        val cover: Double = 40.0,
        val clearCover: Double = 25.0,
        val concreteGrade: String = "",
        val steelGrade: String = "",

        // --- Slab ---
        val thickness: Double = 0.0,
        val span: Double = 0.0,
        val isTwoWay: Boolean = false,

        // --- Column circular ---
        val diameter: Double = 0.0,

        // --- Footing ---
        val footingLength: Double = 0.0,
        val footingWidth: Double = 0.0,
        val footingThickness: Double = 0.0,
        val columnWidth: Double = 0.0,
        val columnDepth: Double = 0.0,
        val columnHeight: Double = 0.0,

        // --- Staircase ---
        val treadDepth: Double = 0.0,
        val riserHeight: Double = 0.0,

        // --- Retaining Wall ---
        val topThickness: Double = 0.0,
        val baseThickness: Double = 0.0,
        val toeLength: Double = 0.0,
        val heelLength: Double = 0.0,
        val stemBars: String = "",

        // --- Water Tank ---
        val innerLength: Double = 0.0,
        val innerWidth: Double = 0.0,
        val wallThickness: Double = 0.0,
        val baseThicknessTank: Double = 0.0,

        // --- Frame Analysis ---
        val spans: List<Double> = emptyList(),
        val supports: List<Int> = emptyList(),
        val bmdValues: List<Double> = emptyList(),
        val sfdValues: List<Double> = emptyList(),

        // --- Steel ---
        val steelDesignation: String = "",
        val webThicknessSteel: Double = 0.0,
        val legLength: Double = 0.0,
        val otherLeg: Double = 0.0,
        val isEqualLeg: Boolean = true,
        val wallThicknessSteel: Double = 0.0,

        // --- Positioning ---
        val originX: Double = 80.0,
        val originY: Double = 100.0,
        val sheetWidth: Double = 841.0,  // A1
        val sheetHeight: Double = 594.0
    )

    // ------------------------------------------------------------------
    // Bilingual label mapping
    // ------------------------------------------------------------------

    private val labelMap = mapOf(
        "SECTION" to mapOf(
            "en" to "SECTION",
            "ar" to "SECTION"
        ),
        "BEAM" to mapOf(
            "en" to "BEAM",
            "ar" to "BEAM"
        ),
        "COLUMN" to mapOf(
            "en" to "COLUMN",
            "ar" to "COLUMN"
        ),
        "SLAB" to mapOf(
            "en" to "SLAB",
            "ar" to "SLAB"
        ),
        "FOOTING" to mapOf(
            "en" to "FOOTING",
            "ar" to "FOOTING"
        ),
        "STAIRCASE" to mapOf(
            "en" to "STAIRCASE",
            "ar" to "STAIRCASE"
        ),
        "RETAINING WALL" to mapOf(
            "en" to "RETAINING WALL",
            "ar" to "RET. WALL"
        ),
        "WATER TANK" to mapOf(
            "en" to "WATER TANK",
            "ar" to "WATER TANK"
        ),
        "PLAN" to mapOf(
            "en" to "PLAN",
            "ar" to "PLAN"
        ),
        "BMD" to mapOf(
            "en" to "BMD",
            "ar" to "BMD"
        ),
        "SFD" to mapOf(
            "en" to "SFD",
            "ar" to "SFD"
        )
    )

    /**
     * Get a label in the appropriate language.
     * Falls back to English for DXF output to avoid encoding issues.
     */
    private fun label(key: String, useArabic: Boolean): String {
        // Always use English in DXF to avoid encoding issues with Arabic characters
        return labelMap[key]?.get("en") ?: key
    }

    // ------------------------------------------------------------------
    // PUBLIC API: Export to String
    // ------------------------------------------------------------------

    /**
     * Export a structural element to DXF content as a String.
     *
     * @param result The design result containing element parameters.
     * @return Complete DXF file content (AC1015 format, CRLF line endings).
     */
    fun exportToString(result: DesignResult): String {
        val engine = DxfExportEngine()
        val exporter = DxfStructuralExporter(engine)

        // Draw the frame and title
        engine.addDrawingFrame(margin = 10.0, sheetWidth = result.sheetWidth, sheetHeight = result.sheetHeight)

        // Draw the element
        drawElement(exporter, result)

        // Draw title block
        val tbWidth = 180.0
        val tbHeight = 50.0
        engine.addTitleBlock(
            title = result.titleEn,
            subtitle = result.titleAr.ifEmpty { getElementSubtitle(result.elementType) },
            scale = result.scale,
            date = result.date,
            sheetNum = result.sheetNum,
            originX = result.sheetWidth - tbWidth - 10,
            originY = 10.0,
            width = tbWidth,
            height = tbHeight
        )

        // Notes
        val noteX = result.sheetWidth - tbWidth - 30.0
        val noteY = 80.0
        if (result.concreteGrade.isNotEmpty()) {
            engine.addText("Concrete: $result.concreteGrade", noteX, noteY, height = 3.0, layer = "TEXT")
        }
        if (result.steelGrade.isNotEmpty()) {
            engine.addText("Steel: $result.steelGrade", noteX, noteY + 6, height = 3.0, layer = "TEXT")
        }

        return engine.build()
    }

    /**
     * Export a structural element and save to a file.
     *
     * @param result The design result containing element parameters.
     * @param file The output file (.dxf extension recommended).
     * @return true if the file was written successfully.
     */
    fun exportToFile(result: DesignResult, file: File): Boolean {
        return try {
            val content = exportToString(result)
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ------------------------------------------------------------------
    // CONVENIENCE OVERLOADS: Quick export for common element types
    // ------------------------------------------------------------------

    /**
     * Quick export for a rectangular beam section.
     */
    fun exportBeamSection(
        width: Double, depth: Double,
        topBars: String, bottomBars: String, stirrups: String,
        cover: Double = 40.0,
        concreteGrade: String = "C30",
        steelGrade: String = "B500B",
        scale: String = "1:1"
    ): String {
        return exportToString(DesignResult(
            elementType = ElementType.BEAM_RECT,
            titleEn = "BEAM SECTION",
            width = width, depth = depth,
            topBars = topBars, bottomBars = bottomBars,
            stirrupsTies = stirrups, cover = cover,
            concreteGrade = concreteGrade, steelGrade = steelGrade,
            scale = scale
        ))
    }

    /**
     * Quick export for a rectangular column section.
     */
    fun exportColumnSection(
        width: Double, depth: Double,
        longitudinalBars: String, ties: String,
        cover: Double = 40.0,
        concreteGrade: String = "C35",
        steelGrade: String = "B500B"
    ): String {
        return exportToString(DesignResult(
            elementType = ElementType.COLUMN_RECT,
            titleEn = "COLUMN SECTION",
            width = width, depth = depth,
            longitudinalBars = longitudinalBars, stirrupsTies = ties,
            cover = cover,
            concreteGrade = concreteGrade, steelGrade = steelGrade
        ))
    }

    /**
     * Quick export for a circular column section.
     */
    fun exportCircularColumnSection(
        diameter: Double,
        longitudinalBars: String, spiral: String,
        cover: Double = 40.0
    ): String {
        return exportToString(DesignResult(
            elementType = ElementType.COLUMN_CIRC,
            titleEn = "CIRCULAR COLUMN SECTION",
            diameter = diameter,
            longitudinalBars = longitudinalBars, spiralTies = spiral,
            cover = cover
        ))
    }

    /**
     * Quick export for a slab section.
     */
    fun exportSlabSection(
        thickness: Double, span: Double,
        topBars: String, bottomBars: String,
        isTwoWay: Boolean = false
    ): String {
        return exportToString(DesignResult(
            elementType = ElementType.SLAB_SECTION,
            titleEn = "SLAB SECTION",
            thickness = thickness, span = span,
            topBarsShort = topBars, bottomBarsShort = bottomBars,
            isTwoWay = isTwoWay
        ))
    }

    /**
     * Quick export for a footing plan.
     */
    fun exportFootingPlan(
        length: Double, width: Double, thickness: Double,
        columnWidth: Double, columnDepth: Double,
        bottomBarsX: String, bottomBarsY: String
    ): String {
        return exportToString(DesignResult(
            elementType = ElementType.FOOTING_PLAN,
            titleEn = "FOOTING PLAN",
            footingLength = length, footingWidth = width, footingThickness = thickness,
            columnWidth = columnWidth, columnDepth = columnDepth,
            bottomBarsShort = bottomBarsX, bottomBarsLong = bottomBarsY
        ))
    }

    /**
     * Quick export for frame analysis BMD and SFD on the same sheet.
     */
    fun exportFrameAnalysis(
        spans: List<Double>,
        supports: List<Int>,
        bmdValues: List<Double>,
        sfdValues: List<Double>,
        title: String = "CONTINUOUS BEAM ANALYSIS"
    ): String {
        return exportToString(DesignResult(
            elementType = ElementType.CUSTOM,  // We'll handle this specially
            titleEn = title,
            spans = spans, supports = supports,
            bmdValues = bmdValues, sfdValues = sfdValues
        ))
    }

    // ------------------------------------------------------------------
    // INTERNAL: Element dispatch
    // ------------------------------------------------------------------

    private fun drawElement(exporter: DxfStructuralExporter, result: DesignResult) {
        val ox = result.originX
        val oy = result.originY

        when (result.elementType) {
            ElementType.BEAM_RECT -> exporter.drawBeamRectSection(
                DxfStructuralExporter.BeamRectInput(
                    width = result.width, depth = result.depth,
                    topBars = result.topBars, bottomBars = result.bottomBars,
                    stirrups = result.stirrupsTies, cover = result.cover,
                    clearCover = result.clearCover,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.BEAM_TEE -> exporter.drawBeamTeeSection(
                DxfStructuralExporter.BeamTeeInput(
                    flangeWidth = result.flangeWidth, flangeThickness = result.flangeThickness,
                    webWidth = result.webWidth, totalDepth = result.totalHeight,
                    topBars = result.topBars, bottomBars = result.bottomBars,
                    stirrups = result.stirrupsTies, cover = result.cover,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.COLUMN_RECT -> exporter.drawColumnRectSection(
                DxfStructuralExporter.ColumnRectInput(
                    width = result.width, depth = result.depth,
                    longitudinalBars = result.longitudinalBars,
                    ties = result.stirrupsTies, cover = result.cover,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.COLUMN_CIRC -> exporter.drawColumnCircSection(
                DxfStructuralExporter.ColumnCircInput(
                    diameter = result.diameter,
                    longitudinalBars = result.longitudinalBars,
                    spiralTies = result.spiralTies, cover = result.cover,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.SLAB_SECTION -> exporter.drawSlabSection(
                DxfStructuralExporter.SlabSectionInput(
                    thickness = result.thickness, span = result.span,
                    topBarsShort = result.topBarsShort, topBarsLong = result.topBarsLong,
                    bottomBarsShort = result.bottomBarsShort, bottomBarsLong = result.bottomBarsLong,
                    cover = result.cover, isTwoWay = result.isTwoWay,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.FOOTING_PLAN -> exporter.drawFootingPlan(
                DxfStructuralExporter.FootingPlanInput(
                    length = result.footingLength, width = result.footingWidth,
                    thickness = result.footingThickness,
                    columnWidth = result.columnWidth, columnDepth = result.columnDepth,
                    bottomBarsX = result.bottomBarsShort, bottomBarsY = result.bottomBarsLong,
                    cover = result.cover, title = result.titleEn
                ), ox, oy
            )

            ElementType.FOOTING_SECTION -> exporter.drawFootingSection(
                DxfStructuralExporter.FootingSectionInput(
                    footingLength = result.footingLength, footingThickness = result.footingThickness,
                    columnWidth = result.columnWidth, columnHeight = result.columnHeight,
                    bottomBars = result.bottomBars, cover = result.cover,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.STAIRCASE_SECTION -> exporter.drawStaircaseSection(
                DxfStructuralExporter.StaircaseInput(
                    totalWidth = result.span, totalHeight = result.totalHeight,
                    waistThickness = result.thickness,
                    treadDepth = result.treadDepth, riserHeight = result.riserHeight,
                    topBars = result.topBars, bottomBars = result.bottomBars,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.STAIRCASE_PLAN -> exporter.drawStaircasePlan(
                DxfStructuralExporter.StaircaseInput(
                    totalWidth = result.span, totalHeight = result.totalHeight,
                    waistThickness = result.thickness,
                    treadDepth = result.treadDepth, riserHeight = result.riserHeight,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.RETAINING_WALL -> exporter.drawRetainingWallSection(
                DxfStructuralExporter.RetainingWallInput(
                    totalHeight = result.totalHeight, baseWidth = result.width,
                    topThickness = result.topThickness, baseThickness = result.baseThickness,
                    toeLength = result.toeLength, heelLength = result.heelLength,
                    stemBars = result.stemBars,
                    baseBarsTop = result.baseBarsTop, baseBarsBot = result.baseBarsBot,
                    cover = result.cover, title = result.titleEn
                ), ox, oy
            )

            ElementType.WATER_TANK -> exporter.drawWaterTankSection(
                DxfStructuralExporter.WaterTankInput(
                    innerLength = result.innerLength, innerWidth = result.innerWidth,
                    wallThickness = result.wallThickness, baseThickness = result.baseThicknessTank,
                    height = result.totalHeight,
                    wallBarsOuter = result.wallBarsOuter, wallBarsInner = result.wallBarsInner,
                    baseBarsTop = result.baseBarsTop, baseBarsBot = result.baseBarsBot,
                    cover = result.cover, title = result.titleEn
                ), ox, oy
            )

            ElementType.STEEL_I_SECTION -> exporter.drawSteelISection(
                DxfStructuralExporter.SteelISectionInput(
                    designation = result.steelDesignation, depth = result.depth,
                    flangeWidth = result.flangeWidth, flangeThickness = result.flangeThickness,
                    webThickness = result.webThicknessSteel, title = result.titleEn
                ), ox, oy
            )

            ElementType.STEEL_CHANNEL -> exporter.drawSteelChannelSection(
                DxfStructuralExporter.SteelChannelInput(
                    designation = result.steelDesignation, depth = result.depth,
                    flangeWidth = result.flangeWidth, flangeThickness = result.flangeThickness,
                    webThickness = result.webThicknessSteel, title = result.titleEn
                ), ox, oy
            )

            ElementType.STEEL_ANGLE -> exporter.drawSteelAngleSection(
                DxfStructuralExporter.SteelAngleInput(
                    designation = result.steelDesignation, legLength = result.legLength,
                    thickness = result.flangeThickness, isEqualLeg = result.isEqualLeg,
                    otherLeg = result.otherLeg, title = result.titleEn
                ), ox, oy
            )

            ElementType.STEEL_BOX -> exporter.drawSteelBoxSection(
                DxfStructuralExporter.SteelBoxInput(
                    designation = result.steelDesignation, depth = result.depth,
                    width = result.width, wallThickness = result.wallThicknessSteel,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.BMD -> exporter.drawBMD(
                DxfStructuralExporter.FrameAnalysisInput(
                    spans = result.spans, supports = result.supports,
                    bmdValues = result.bmdValues, sfdValues = result.sfdValues,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.SFD -> exporter.drawSFD(
                DxfStructuralExporter.FrameAnalysisInput(
                    spans = result.spans, supports = result.supports,
                    bmdValues = result.bmdValues, sfdValues = result.sfdValues,
                    title = result.titleEn
                ), ox, oy
            )

            ElementType.CUSTOM -> {
                // For CUSTOM, draw both BMD and SFD if data is available
                val hasBMD = result.bmdValues.size >= 2
                val hasSFD = result.sfdValues.size >= 2
                val frameInput = DxfStructuralExporter.FrameAnalysisInput(
                    spans = result.spans, supports = result.supports,
                    bmdValues = result.bmdValues, sfdValues = result.sfdValues,
                    title = result.titleEn
                )
                if (hasSFD) {
                    exporter.drawSFD(frameInput, ox, oy + 120)
                }
                if (hasBMD) {
                    exporter.drawBMD(frameInput, ox, oy)
                }
            }
        }
    }

    private fun getElementSubtitle(type: ElementType): String {
        return when (type) {
            ElementType.BEAM_RECT, ElementType.BEAM_TEE -> "Rectangular Beam Detail"
            ElementType.COLUMN_RECT -> "Rectangular Column Detail"
            ElementType.COLUMN_CIRC -> "Circular Column Detail"
            ElementType.SLAB_SECTION -> "Reinforced Concrete Slab"
            ElementType.FOOTING_PLAN -> "Isolated Footing"
            ElementType.FOOTING_SECTION -> "Footing Cross-Section"
            ElementType.STAIRCASE_SECTION -> "Staircase Longitudinal Section"
            ElementType.STAIRCASE_PLAN -> "Staircase Plan View"
            ElementType.RETAINING_WALL -> "Cantilever Retaining Wall"
            ElementType.WATER_TANK -> "Reinforced Concrete Water Tank"
            ElementType.STEEL_I_SECTION -> "Steel I-Beam Section"
            ElementType.STEEL_CHANNEL -> "Steel Channel Section"
            ElementType.STEEL_ANGLE -> "Steel Angle Section"
            ElementType.STEEL_BOX -> "Steel Hollow Section"
            ElementType.BMD -> "Bending Moment Diagram"
            ElementType.SFD -> "Shear Force Diagram"
            ElementType.CUSTOM -> "Structural Analysis"
        }
    }
}
