package com.civileg.app.utils

import com.civileg.app.utils.CalculatorDetailingV4
import com.civileg.app.utils.detailing.BeamDetailingEngine
import com.civileg.app.utils.detailing.BarBendingEngine
import com.civileg.app.utils.detailing.CodeLengths
import com.civileg.app.utils.detailing.ColumnDetailingEngine
import com.civileg.app.utils.detailing.DrawingManifest
import com.civileg.app.utils.detailing.DrawingManifestEntry
import com.civileg.app.utils.detailing.DrawingStatus
import java.io.File
import java.time.LocalDate

/**
 * ══════════════════════════════════════════════════════════════
 * CIVILEG STRUCTURAL DETAILING & CAD ENGINE — V2 facade (§40).
 *
 * Pipeline: CalculatorEngine → BeamResult → BeamDetailingEngine
 *           → DetailingPackage(s) → CalculatorCadExporterV7 → DXF + manifest.
 *
 * This facade OWNS NO GEOMETRY. It only:
 *   1) guards inputs loudly (spec §2),
 *   2) delegates to the detailing engines,
 *   3) maps domain data onto the V7 writer,
 *   4) writes drawing_manifest.txt next to the sheets.
 *
 * Legacy APIs in CadDxfExporter / DxfExporter remain untouched (rule §1).
 */
object DxfExporterV2 {

    data class V2Result(
        val directory: File,
        val sheetFiles: List<File>,
        val manifestFile: File?,
        val qaPassed: Boolean,
        val issues: List<String>
    )

    /** §38/§41 — beam multi-sheet export with real marks, zones and BBS geometry. */
    fun exportBeamDetailed(
        context: android.content.Context,
        result: CalculatorEngine.BeamResult,
        spanMm: Double,
        coverMm: Double,
        fcuMPa: Double,
        fyMPa: Double,
        projectName: String = "CIVILEG STRUCTURES"
    ): V2Result? {
        require(spanMm > 0.0) { "Beam DXF export requires a valid span." }
        require(fcuMPa > 0 && fyMPa > 0) { "Beam DXF export requires real material strengths." }

        val input = BeamDetailingEngine.BeamInput(
            spanMm = spanMm, coverMm = coverMm,
            supportTypeName = result.supportType.name,
            codeName = when (result.code) {
                CalculatorEngine.AppDesignCode.ACI -> "ACI 318-19"
                CalculatorEngine.AppDesignCode.SAUDI -> "SBC 304-2018"
                else -> "ECP 203-2020"
            },
            fcuMPa = fcuMPa, fyMPa = fyMPa
        )
        val det = BeamDetailingEngine.build(result, input)

        // ── Map domain bars → V4 package ──
        val bars = mutableListOf<CalculatorDetailingV4.BarDefinition>()
        bars += CalculatorDetailingV4.BarDefinition(
            mark = det.bottomBarsMark, diameterMm = result.reinforcementBottom.diameter,
            shape = CalculatorDetailingV4.BarShape.STRAIGHT,
            straightLengthMm = det.bottomCutLengthMm, layer = "REBAR"
        )
        bars += CalculatorDetailingV4.BarDefinition(
            mark = det.topBarsMark, diameterMm = result.reinforcementTop.diameter,
            shape = CalculatorDetailingV4.BarShape.STRAIGHT,
            straightLengthMm = det.topCutLengthMm, layer = "REBAR_TOP"
        )
        val stirrups = det.stirrupZones.map { z ->
            CalculatorDetailingV4.BarDefinition(
                mark = z.mark, diameterMm = z.diameterMm,
                shape = CalculatorDetailingV4.BarShape.STIRRUP_135,
                spacingMm = z.spacingMm,
                segments = listOf(
                    CalculatorDetailingV4.Segment(90.0, (result.width - 2 * coverMm - z.diameterMm)),
                    CalculatorDetailingV4.Segment(90.0, (result.depth - 2 * coverMm - z.diameterMm)),
                    CalculatorDetailingV4.Segment(90.0, (result.width - 2 * coverMm - z.diameterMm)),
                    CalculatorDetailingV4.Segment(90.0, (result.depth - 2 * coverMm - z.diameterMm)),
                    CalculatorDetailingV4.Segment(135.0, det.stirrupHookMm)
                ),
                cutOffFromStartMm = z.startMm,
                cutOffFromEndMm = (spanMm - z.endMm).coerceAtLeast(0.0)
            )
        }

        val pkg = CalculatorDetailingV4.DetailingPackage(
            memberType = CalculatorDetailingV4.MemberType.BEAM,
            memberId = det.mark,
            title = "BEAM ${result.width.toInt()}x${result.depth.toInt()} — ${input.codeName}",
            geometry = mapOf(
                "span" to spanMm, "width" to result.width, "depth" to result.depth
            ),
            bars = bars, stirrups = stirrups
        )

        val bbs = CalculatorDetailingV4.buildBarSchedule(listOf(pkg))
        val outDir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir,
            "CivilEG_V2/BEAM_${System.currentTimeMillis()}"
        ).apply { mkdirs() }

        val export = runCatching {
            CalculatorCadExporterV7.exportProject(
                packages = listOf(pkg), bbs = bbs, outDir = outDir.absolutePath,
                s = CalculatorCadExporterV7.Settings(projectName = projectName.take(28))
            )
        }.getOrElse { return null }

        // §39 — package manifest from the domain model (real sheet files)
        val manifest = DrawingManifest(
            project = projectName,
            entries = export.sheets.map {
                DrawingManifestEntry(
                    sheetNumber = it.number, element = it.memberId,
                    revision = "00", code = input.codeName,
                    status = DrawingStatus.FOR_CONSTRUCTION,
                    file = it.file.name
                )
            }
        )
        val manifestFile = File(outDir, "drawing_manifest.txt").apply {
            writeText(manifest.toText())
        }

        return V2Result(
            directory = export.directory,
            sheetFiles = export.sheets.map { it.file },
            manifestFile = manifestFile,
            qaPassed = export.qa.passed,
            issues = export.qa.issues + det.drawing.warnings
        )
    }

    /** §16/§17/§40 — column multi-sheet export with real layout, tie zones, BBS geometry. */
    fun exportColumnDetailed(
        context: android.content.Context,
        result: CalculatorEngine.ColumnResult,
        clearHeightMm: Double,
        coverMm: Double,
        fcuMPa: Double,
        fyMPa: Double,
        projectName: String = "CIVILEG STRUCTURES",
        biaxial: com.civileg.app.domain.entities.BiaxialCheckResult? = null
    ): V2Result? {
        require(clearHeightMm > 0.0) { "Column DXF export requires a valid clear height." }
        require(result.width > 0 && result.depth > 0) { "Column section dims missing." }
        require(result.reinforcement.numBars >= 4) {
            "Column needs ≥4 longitudinal bars (corners), got ${result.reinforcement.numBars}"
        }
        require(fcuMPa > 0 && fyMPa > 0) { "Column DXF export requires real material strengths." }

        val codeName = when (result.code) {
            CalculatorEngine.AppDesignCode.ACI -> "ACI 318-19"
            CalculatorEngine.AppDesignCode.SAUDI -> "SBC 304-2018"
            else -> "ECP 203-2020"
        }
        val det = ColumnDetailingEngine.build(
            result = result, clearHeightMm = clearHeightMm, coverMm = coverMm,
            fcuMPa = fcuMPa, fyMPa = fyMPa, codeName = codeName, biaxial = biaxial
        )
        val pkg = columnPackageFrom(det, result.width, result.depth, clearHeightMm, coverMm)

        val bbs = CalculatorDetailingV4.buildBarSchedule(listOf(pkg))
        val outDir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir,
            "CivilEG_V2/COLUMN_${System.currentTimeMillis()}"
        ).apply { mkdirs() }

        val export = runCatching {
            CalculatorCadExporterV7.exportProject(
                packages = listOf(pkg), bbs = bbs, outDir = outDir.absolutePath,
                s = CalculatorCadExporterV7.Settings(projectName = projectName.take(28))
            )
        }.getOrElse { return null }

        // §39 — package manifest from the domain model (real sheet files)
        val manifest = DrawingManifest(
            project = projectName,
            entries = export.sheets.map {
                DrawingManifestEntry(
                    sheetNumber = it.number, element = it.memberId,
                    revision = "00", code = codeName,
                    status = DrawingStatus.FOR_CONSTRUCTION,
                    file = it.file.name
                )
            }
        )
        val manifestFile = File(outDir, "drawing_manifest.txt").apply {
            writeText(manifest.toText())
        }

        return V2Result(
            directory = export.directory,
            sheetFiles = export.sheets.map { it.file },
            manifestFile = manifestFile,
            qaPassed = export.qa.passed,
            issues = export.qa.issues + det.warnings
        )
    }

    /**
     * §16/§17 — column V4 package mapper (pure layout, no strength recompute).
     * Main bars as straight bars at the TRUE cut length (clear height + code Ld);
     * confining ties one V4 definition per engine zone with VERBATIM spacing/
     * diameter and an EXCLUSIVE quantity from the zone's own tie count.
     */
    internal fun columnPackageFrom(
        det: ColumnDetailingEngine.ColumnDetailingData,
        widthMm: Double,
        depthMm: Double,
        clearHeightMm: Double,
        coverMm: Double
    ): CalculatorDetailingV4.DetailingPackage {
        val bars = listOf(
            CalculatorDetailingV4.BarDefinition(
                mark = det.mainMark,
                diameterMm = det.mainDiaMm,
                shape = CalculatorDetailingV4.BarShape.STRAIGHT,
                straightLengthMm = det.mainCutLengthMm,
                layer = "REBAR",
                quantity = det.mainCount,
                lapLocations = listOf(
                    CalculatorDetailingV4.LapLocation(
                        positionFromStartMm = det.lapZoneStartMm,
                        lengthMm = (det.lapZoneEndMm - det.lapZoneStartMm).coerceAtLeast(0.0)
                    )
                )
            )
        )
        val stirrups = det.tieZones.map { z ->
            CalculatorDetailingV4.BarDefinition(
                mark = z.mark,
                diameterMm = z.diameterMm,
                shape = CalculatorDetailingV4.BarShape.STIRRUP_135,
                spacingMm = z.spacingMm,
                quantity = z.count,
                segments = listOf(
                    CalculatorDetailingV4.Segment(90.0, (widthMm - 2 * coverMm - z.diameterMm).coerceAtLeast(0.0)),
                    CalculatorDetailingV4.Segment(90.0, (depthMm - 2 * coverMm - z.diameterMm).coerceAtLeast(0.0)),
                    CalculatorDetailingV4.Segment(90.0, (widthMm - 2 * coverMm - z.diameterMm).coerceAtLeast(0.0)),
                    CalculatorDetailingV4.Segment(90.0, (depthMm - 2 * coverMm - z.diameterMm).coerceAtLeast(0.0)),
                    CalculatorDetailingV4.Segment(135.0, CodeLengths.stirrupHook135(z.diameterMm).value)
                )
            )
        }
        return CalculatorDetailingV4.DetailingPackage(
            memberType = CalculatorDetailingV4.MemberType.COLUMN,
            memberId = det.mark,
            title = "COLUMN ${widthMm.toInt()}x${depthMm.toInt()} — ${det.tieTypeLabel}",
            geometry = mapOf(
                "width" to widthMm,
                "depth" to depthMm,
                "height" to clearHeightMm,
                "cover" to coverMm
            ),
            bars = bars,
            stirrups = stirrups
        )
    }
}
