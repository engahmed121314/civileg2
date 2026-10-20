package com.civileg.app.utils

import android.os.Parcelable
import com.civileg.app.domain.FlatSlabResult
import com.civileg.app.domain.RebarResult
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.utils.CalculatorEngine.*
import kotlinx.parcelize.Parcelize
import kotlin.math.*

@Parcelize
data class BbsEntry(
    val memberMark: String,
    val barMark: String,
    val diameter: Int,
    val shapeCode: Int,
    val count: Int,
    val lengthA: Double, // mm
    val lengthB: Double = 0.0,
    val lengthC: Double = 0.0,
    val totalLengthPerBar: Double,
    val totalWeightKg: Double
) : Parcelable

object BbsGenerator {

    /**
     * Generates a Bar Bending Schedule for a designed Beam.
     * @param storyHeight_mm Column/beam story height in mm (default 3000mm = 3m)
     */
    fun generateBeamBbs(mark: String, result: BeamResult, storyHeight_mm: Double = 3000.0): List<BbsEntry> {
        // ── InputGuard: حراس المدخلات — لا حساب على مدخلات غير صالحة ──
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("width", result.width)               // mm
        InputGuard.positive("depth", result.depth)               // mm
        InputGuard.positive("reinforcementBottom.diameter", result.reinforcementBottom.diameter)
        InputGuard.positive("reinforcementBottom.numBars", result.reinforcementBottom.numBars)
        InputGuard.positive("stirrups.diameter", result.stirrups.diameter)
        InputGuard.inRange("storyHeight", storyHeight_mm, 1000.0, 20000.0)  // mm

        val entries = mutableListOf<BbsEntry>()

        // Span derivation: use storyHeight as span approximation if span not directly available
        val spanMm = if (storyHeight_mm > 0.0) storyHeight_mm else result.width * 1000.0

        // Main Bottom Rebar — computed span + 9φ hook each end (ECP 203 clause)
        val hookLen = 2 * 9 * result.reinforcementBottom.diameter  // 9φ hook per end × 2 ends
        val mainBarLen = spanMm + hookLen
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01",
            diameter = result.reinforcementBottom.diameter,
            shapeCode = 0, count = result.reinforcementBottom.numBars,
            lengthA = mainBarLen, totalLengthPerBar = mainBarLen,
            totalWeightKg = result.reinforcementBottom.numBars * mainBarLen / 1000.0 * (result.reinforcementBottom.diameter.toDouble().pow(2) / 162.0)
        ))

        // Stirrups (Shape Code 51 - Rectangular link)
        val s = result.stirrups
        val a = result.width - 80.0 // clear width inside stirrup (2×40mm cover)
        val b = result.depth - 80.0 // clear depth inside stirrup
        InputGuard.require(a > 0.0, "Stirrup clear width must be positive (width=${result.width} > 80mm)")
        InputGuard.require(b > 0.0, "Stirrup clear depth must be positive (depth=${result.depth} > 80mm)")
        val stirrupLen = 2 * (a + b) + 200.0 // 2×(a+b) + 2×9φ hooks (≈200mm for φ8-φ10)

        var stirrupCount = 0
        result.stirrups.zones.forEach { zone ->
            val zoneLen = zone.endLocation - zone.startLocation
            if (zoneLen > 0 && zone.spacing > 0) {
                stirrupCount += (zoneLen / zone.spacing).toInt() + 1
            }
        }
        InputGuard.require(stirrupCount > 0, "Beam must have at least 1 stirrup zone with valid spacing")

        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.stirrups.diameter,
            shapeCode = 51, count = stirrupCount,
            lengthA = a, lengthB = b,
            totalLengthPerBar = stirrupLen,
            totalWeightKg = stirrupCount * stirrupLen / 1000.0 * (result.stirrups.diameter.toDouble().pow(2) / 162.0)
        ))

        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a designed Column.
     * @param storyHeight_mm Column story height in mm (default 3000mm = 3m)
     */
    fun generateColumnBbs(mark: String, result: ColumnResult, storyHeight_mm: Double = 3000.0): List<BbsEntry> {
        // ── InputGuard: حراس المدخلات — لا حساب على مدخلات غير صالحة ──
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("width", result.width)               // mm
        InputGuard.positive("depth", result.depth)               // mm
        InputGuard.positive("reinforcement.diameter", result.reinforcement.diameter)
        InputGuard.positive("reinforcement.numBars", result.reinforcement.numBars)
        InputGuard.positive("stirrups.diameter", result.stirrups.diameter)

        val entries = mutableListOf<BbsEntry>()

        // Height derivation: storyHeight or estimate from concreteVolume
        val h = if (storyHeight_mm > 0.0) storyHeight_mm else {
            val crossAreaMm2 = result.width * result.depth
            if (crossAreaMm2 > 0.0 && result.concreteVolume > 0.0) result.concreteVolume * 1e9 / crossAreaMm2 else 3000.0
        }
        InputGuard.inRange("columnHeight", h, 500.0, 30000.0)  // mm

        // Longitudinal Bars — full height + 60φ lap splice
        val mainLen = h + 60 * result.reinforcement.diameter
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01",
            diameter = result.reinforcement.diameter,
            shapeCode = 0, count = result.reinforcement.numBars,
            lengthA = mainLen, totalLengthPerBar = mainLen,
            totalWeightKg = result.reinforcement.numBars * mainLen / 1000.0 * (result.reinforcement.diameter.toDouble().pow(2)/162.0)
        ))

        // Ties
        val a = result.width - 80.0
        val b = result.depth - 80.0
        InputGuard.require(a > 0.0, "Tie clear width must be positive (width=${result.width} > 80mm)")
        InputGuard.require(b > 0.0, "Tie clear depth must be positive (depth=${result.depth} > 80mm)")
        val tieLen = 2 * (a + b) + 200.0
        var tieCount = 0
        result.stirrups.zones.forEach { zone ->
            val zoneLen = zone.endLocation - zone.startLocation
            if (zoneLen > 0 && zone.spacing > 0) {
                tieCount += (zoneLen / zone.spacing).toInt() + 1
            }
        }
        if (tieCount == 0) tieCount = 1  // Minimum 1 tie
        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.stirrups.diameter,
            shapeCode = 51, count = tieCount,
            lengthA = a, lengthB = b, totalLengthPerBar = tieLen,
            totalWeightKg = tieCount * tieLen / 1000.0 * (result.stirrups.diameter.toDouble().pow(2)/162.0)
        ))
        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a designed Isolated Footing.
     */
    fun generateFootingBbs(mark: String, result: FootingResult): List<BbsEntry> {
        // ── InputGuard: حراس المدخلات — لا حساب على مدخلات غير صالحة ──
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("length", result.length)              // mm
        InputGuard.positive("width", result.width)               // mm
        InputGuard.positive("thickness", result.thickness)       // mm
        InputGuard.positive("barDiameter", result.barDiameter)
        InputGuard.positive("barsX", result.barsX)
        InputGuard.positive("barsY", result.barsY)

        val entries = mutableListOf<BbsEntry>()
        val lx = result.length
        val ly = result.width
        val t = result.thickness

        // Bottom X (Shape Code 21 - L bar): bar length = lx - 2×cover + 2×(t - cover) bend-up
        val coverMm = 75.0 // typical footing cover
        val bendUpLen = t - coverMm
        InputGuard.require(bendUpLen > 0, "Footing bend-up length must be positive (thickness > cover)")
        val lenX = (lx - 2 * coverMm) + 2 * bendUpLen
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01",
            diameter = result.barDiameter,
            shapeCode = 21, count = result.barsX,
            lengthA = lx - 2 * coverMm, lengthB = bendUpLen,
            totalLengthPerBar = lenX,
            totalWeightKg = result.barsX * lenX / 1000.0 * (result.barDiameter.toDouble().pow(2)/162.0)
        ))

        // Bottom Y
        val lenY = (ly - 2 * coverMm) + 2 * bendUpLen
        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.barDiameter,
            shapeCode = 21, count = result.barsY,
            lengthA = ly - 2 * coverMm, lengthB = bendUpLen,
            totalLengthPerBar = lenY,
            totalWeightKg = result.barsY * lenY / 1000.0 * (result.barDiameter.toDouble().pow(2)/162.0)
        ))
        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a designed slab panel.
     *
     * Main bars run along the short span [lxMm] and are distributed across [lyMm];
     * secondary bars run along the long span. Both use 9d hooks at each end.
     */
    fun generateSlabBbs(
        mark: String,
        result: SlabResult,
        lxMm: Double,
        lyMm: Double,
        coverMm: Double = 25.0
    ): List<BbsEntry> {
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("panelLength", lxMm)
        InputGuard.positive("panelWidth", lyMm)
        InputGuard.inRange("cover", coverMm, 10.0, 75.0)
        InputGuard.positive("mainDiameter", result.reinforcementMain.diameter)
        InputGuard.positive("mainSpacing", result.reinforcementMain.spacing)
        InputGuard.positive("secondaryDiameter", result.reinforcementSecondary.diameter)
        InputGuard.positive("secondarySpacing", result.reinforcementSecondary.spacing)
        InputGuard.require(lxMm > 2 * coverMm && lyMm > 2 * coverMm, "Slab panel must be larger than twice the cover")

        val entries = mutableListOf<BbsEntry>()

        val mainDia = result.reinforcementMain.diameter
        val mainSpacing = result.reinforcementMain.spacing
        val mainCount = (((lyMm - 2 * coverMm) / mainSpacing).toInt() + 1).coerceAtLeast(1)
        val mainLength = (lxMm - 2 * coverMm) + 2 * 9 * mainDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "01",
                diameter = mainDia, shapeCode = 0, count = mainCount,
                lengthA = lxMm - 2 * coverMm,
                totalLengthPerBar = mainLength,
                totalWeightKg = mainCount * mainLength / 1000.0 * (mainDia.toDouble().pow(2) / 162.0)
            )
        )

        val secDia = result.reinforcementSecondary.diameter
        val secSpacing = result.reinforcementSecondary.spacing
        val secCount = (((lxMm - 2 * coverMm) / secSpacing).toInt() + 1).coerceAtLeast(1)
        val secLength = (lyMm - 2 * coverMm) + 2 * 9 * secDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "02",
                diameter = secDia, shapeCode = 0, count = secCount,
                lengthA = lyMm - 2 * coverMm,
                totalLengthPerBar = secLength,
                totalWeightKg = secCount * secLength / 1000.0 * (secDia.toDouble().pow(2) / 162.0)
            )
        )

        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a designed staircase flight.
     *
     * Main bars follow the inclined flight (run + rise), distribution bars span the
     * flight width. The waist thickness supplies the anchorage tail at each end.
     */
    fun generateStairBbs(
        mark: String,
        result: StairResult,
        stairWidthMm: Double,
        coverMm: Double = 25.0
    ): List<BbsEntry> {
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("stairWidth", stairWidthMm)
        InputGuard.positive("span", result.span)
        InputGuard.positive("tread", result.tread)
        InputGuard.positive("riser", result.riser)
        InputGuard.inRange("cover", coverMm, 10.0, 75.0)
        InputGuard.positive("mainDiameter", result.reinforcement.diameter)
        InputGuard.positive("mainSpacing", result.reinforcement.spacing)

        val runMm = result.span * 1000.0
        val nRisers = (runMm / result.tread).toInt().coerceAtLeast(1)
        val totalRiseMm = nRisers * result.riser
        val inclineMm = sqrt(runMm * runMm + totalRiseMm * totalRiseMm)
        val tailMm = (result.thickness - coverMm).coerceAtLeast(0.0)

        val mainDia = result.reinforcement.diameter
        val mainSpacing = result.reinforcement.spacing
        val mainCount = (((stairWidthMm - 2 * coverMm) / mainSpacing).toInt() + 1).coerceAtLeast(1)
        val mainLength = inclineMm + 2 * tailMm + 2 * 9 * mainDia
        val entries = mutableListOf(
            BbsEntry(
                memberMark = mark, barMark = "01",
                diameter = mainDia, shapeCode = 0, count = mainCount,
                lengthA = inclineMm,
                lengthB = tailMm,
                totalLengthPerBar = mainLength,
                totalWeightKg = mainCount * mainLength / 1000.0 * (mainDia.toDouble().pow(2) / 162.0)
            )
        )

        val distDia = result.distributionReinforcement.diameter
        val distSpacing = result.distributionReinforcement.spacing
        if (distDia > 0 && distSpacing > 0) {
            val distCount = ((inclineMm / distSpacing).toInt() + 1).coerceAtLeast(1)
            val distLength = (stairWidthMm - 2 * coverMm) + 2 * 9 * distDia
            entries.add(
                BbsEntry(
                    memberMark = mark, barMark = "02",
                    diameter = distDia, shapeCode = 0, count = distCount,
                    lengthA = stairWidthMm - 2 * coverMm,
                    totalLengthPerBar = distLength,
                    totalWeightKg = distCount * distLength / 1000.0 * (distDia.toDouble().pow(2) / 162.0)
                )
            )
        }
        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a rectangular tank.
     *
     * Wall steel is counted per wall face for all four walls (two faces), with a
     * 40d anchorage into the base; base steel is provided in both directions.
     */
    fun generateTankBbs(
        mark: String,
        result: TankResult,
        coverMm: Double = 40.0
    ): List<BbsEntry> {
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("tankLength", result.length)
        InputGuard.positive("tankWidth", result.width)
        InputGuard.positive("tankHeight", result.height)
        InputGuard.inRange("cover", coverMm, 20.0, 75.0)
        InputGuard.positive("wallDiameter", result.wallReinforcement.diameter)
        InputGuard.positive("wallSpacing", result.wallReinforcement.spacing)
        InputGuard.positive("baseDiameter", result.baseReinforcement.diameter)
        InputGuard.positive("baseSpacing", result.baseReinforcement.spacing)

        val entries = mutableListOf<BbsEntry>()

        val wallDia = result.wallReinforcement.diameter
        val wallSpacing = result.wallReinforcement.spacing
        val wallPerimeterMm = 2 * (result.length + result.width) * 1000.0
        val wallBarsPerFace = ((wallPerimeterMm / wallSpacing).toInt() + 1).coerceAtLeast(1)
        val wallBarCount = wallBarsPerFace * 2 // inner + outer face
        val wallBarLength = result.height * 1000.0 + 40 * wallDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "01",
                diameter = wallDia, shapeCode = 0, count = wallBarCount,
                lengthA = result.height * 1000.0,
                lengthB = 40.0 * wallDia,
                totalLengthPerBar = wallBarLength,
                totalWeightKg = wallBarCount * wallBarLength / 1000.0 * (wallDia.toDouble().pow(2) / 162.0)
            )
        )

        val baseDia = result.baseReinforcement.diameter
        val baseSpacing = result.baseReinforcement.spacing
        val baseLengthMm = result.length * 1000.0
        val baseWidthMm = result.width * 1000.0
        val barsAlongLength = (((baseWidthMm - 2 * coverMm) / baseSpacing).toInt() + 1).coerceAtLeast(1)
        val barsAlongWidth = (((baseLengthMm - 2 * coverMm) / baseSpacing).toInt() + 1).coerceAtLeast(1)
        val baseLenX = (baseLengthMm - 2 * coverMm) + 2 * 9 * baseDia
        val baseLenY = (baseWidthMm - 2 * coverMm) + 2 * 9 * baseDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "02",
                diameter = baseDia, shapeCode = 0, count = barsAlongLength,
                lengthA = baseLengthMm - 2 * coverMm,
                totalLengthPerBar = baseLenX,
                totalWeightKg = barsAlongLength * baseLenX / 1000.0 * (baseDia.toDouble().pow(2) / 162.0)
            )
        )
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "03",
                diameter = baseDia, shapeCode = 0, count = barsAlongWidth,
                lengthA = baseWidthMm - 2 * coverMm,
                totalLengthPerBar = baseLenY,
                totalWeightKg = barsAlongWidth * baseLenY / 1000.0 * (baseDia.toDouble().pow(2) / 162.0)
            )
        )
        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a cantilever retaining wall.
     *
     * The stem is scheduled per metre-run of wall (both faces), with 40d base
     * anchorage and a 12d top hook. Base steel is scheduled in the transverse
     * and longitudinal directions. [wallLengthM] scales every quantity to the
     * actual wall length.
     */
    fun generateRetainingWallBbs(
        mark: String,
        result: RetainingWallResult,
        wallLengthM: Double = 1.0,
        coverMm: Double = 50.0
    ): List<BbsEntry> {
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("wallLength", wallLengthM)
        InputGuard.positive("wallHeight", result.height)
        InputGuard.positive("stemThickness", result.stemThickness)
        InputGuard.positive("baseWidth", result.baseWidth)
        InputGuard.inRange("cover", coverMm, 20.0, 75.0)
        InputGuard.positive("stemDiameter", result.stemReinforcement.diameter)
        InputGuard.positive("stemSpacing", result.stemReinforcement.spacing)
        InputGuard.positive("baseDiameter", result.baseReinforcement.diameter)
        InputGuard.positive("baseSpacing", result.baseReinforcement.spacing)

        val lengthMm = wallLengthM * 1000.0
        val heightMm = result.height * 1000.0
        val baseWidthMm = result.baseWidth
        InputGuard.require(lengthMm > 2 * coverMm, "Wall length must exceed twice the cover")

        val stemDia = result.stemReinforcement.diameter
        val stemSpacing = result.stemReinforcement.spacing
        val entries = mutableListOf<BbsEntry>()

        // 01 - Stem vertical bars, inner + outer face
        val vertPerFace = (((lengthMm - 2 * coverMm) / stemSpacing).toInt() + 1).coerceAtLeast(1)
        val vertCount = vertPerFace * 2
        val vertLength = heightMm + 40 * stemDia + 12 * stemDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "01",
                diameter = stemDia, shapeCode = 0, count = vertCount,
                lengthA = heightMm,
                lengthB = 40.0 * stemDia,
                lengthC = 12.0 * stemDia,
                totalLengthPerBar = vertLength,
                totalWeightKg = vertCount * vertLength / 1000.0 * (stemDia.toDouble().pow(2) / 162.0)
            )
        )

        // 02 - Stem horizontal bars, inner + outer face
        val horizLayers = (((heightMm - 2 * coverMm) / stemSpacing).toInt() + 1).coerceAtLeast(1)
        val horizCount = horizLayers * 2
        val horizLength = (lengthMm - 2 * coverMm) + 2 * 9 * stemDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "02",
                diameter = stemDia, shapeCode = 0, count = horizCount,
                lengthA = lengthMm - 2 * coverMm,
                totalLengthPerBar = horizLength,
                totalWeightKg = horizCount * horizLength / 1000.0 * (stemDia.toDouble().pow(2) / 162.0)
            )
        )

        val baseDia = result.baseReinforcement.diameter
        val baseSpacing = result.baseReinforcement.spacing

        // 03 - Base transverse bars (toe to heel)
        val transverseCount = (((lengthMm - 2 * coverMm) / baseSpacing).toInt() + 1).coerceAtLeast(1)
        val transverseLength = (baseWidthMm - 2 * coverMm) + 2 * 40 * baseDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "03",
                diameter = baseDia, shapeCode = 0, count = transverseCount,
                lengthA = baseWidthMm - 2 * coverMm,
                totalLengthPerBar = transverseLength,
                totalWeightKg = transverseCount * transverseLength / 1000.0 * (baseDia.toDouble().pow(2) / 162.0)
            )
        )

        // 04 - Base longitudinal distribution bars
        val distCount = (((baseWidthMm - 2 * coverMm) / baseSpacing).toInt() + 1).coerceAtLeast(1)
        val distLength = (lengthMm - 2 * coverMm) + 2 * 9 * baseDia
        entries.add(
            BbsEntry(
                memberMark = mark, barMark = "04",
                diameter = baseDia, shapeCode = 0, count = distCount,
                lengthA = lengthMm - 2 * coverMm,
                totalLengthPerBar = distLength,
                totalWeightKg = distCount * distLength / 1000.0 * (baseDia.toDouble().pow(2) / 162.0)
            )
        )
        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a flat slab panel.
     *
     * Column-strip and middle-strip bars are scheduled top and bottom in both
     * directions. Strip widths come from the design result; the middle strip is
     * the remainder of the panel. Bars carry 9d end hooks.
     */
    fun generateFlatSlabBbs(
        mark: String,
        result: FlatSlabResult,
        lxMm: Double,
        lyMm: Double,
        coverMm: Double = 25.0
    ): List<BbsEntry> {
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("panelLength", lxMm)
        InputGuard.positive("panelWidth", lyMm)
        InputGuard.inRange("cover", coverMm, 10.0, 75.0)

        val colStripX = result.columnStripWidthX
        val colStripY = result.columnStripWidthY
        val midStripX = lyMm - colStripX
        val midStripY = lxMm - colStripY
        if (colStripX <= 2 * coverMm || colStripY <= 2 * coverMm ||
            midStripX <= 2 * coverMm || midStripY <= 2 * coverMm
        ) {
            return emptyList()
        }

        val entries = mutableListOf<BbsEntry>()
        var barMark = 1

        fun addStrip(
            barLengthMm: Double,
            stripWidthMm: Double,
            rebar: RebarResult
        ) {
            val dia = rebar.diameter
            val spacing = rebar.spacing.toDouble()
            if (dia <= 0 || spacing <= 0.0) return
            val count = (((stripWidthMm - 2 * coverMm) / spacing).toInt() + 1).coerceAtLeast(1)
            val barLength = barLengthMm + 2 * 9 * dia
            entries.add(
                BbsEntry(
                    memberMark = mark,
                    barMark = String.format(java.util.Locale.US, "%02d", barMark++),
                    diameter = dia, shapeCode = 0, count = count,
                    lengthA = barLengthMm,
                    totalLengthPerBar = barLength,
                    totalWeightKg = count * barLength / 1000.0 * (dia.toDouble().pow(2) / 162.0)
                )
            )
        }

        val xBar = lxMm - 2 * coverMm
        val yBar = lyMm - 2 * coverMm
        addStrip(xBar, colStripX, result.columnStripBotRebar)
        addStrip(xBar, midStripX, result.middleStripBotRebar)
        addStrip(xBar, colStripX, result.columnStripTopRebar)
        addStrip(xBar, midStripX, result.middleStripTopRebar)
        addStrip(yBar, colStripY, result.columnStripBotRebar)
        addStrip(yBar, midStripY, result.middleStripBotRebar)
        addStrip(yBar, colStripY, result.columnStripTopRebar)
        addStrip(yBar, midStripY, result.middleStripTopRebar)
        return entries
    }

    /**
     * AI Waste Minimization (Bin Packing Algorithm)
     */
    fun optimizeCutting(entries: List<BbsEntry>, stockLength: Double = 12000.0): String {
        InputGuard.positive("stockLength", stockLength)
        InputGuard.inRange("stockLength", stockLength, 6000.0, 18000.0)  // mm — standard bar lengths

        val sortedBars = entries.flatMap { entry -> List(entry.count) { entry.totalLengthPerBar } }
            .filter { it <= stockLength }
            .sortedDescending()

        if (sortedBars.isEmpty()) return "No valid bars to optimize."

        var stocksCount = 0
        val bins = mutableListOf<Double>()

        sortedBars.forEach { barLen ->
            var placed = false
            for (i in bins.indices) {
                if (bins[i] >= barLen) {
                    bins[i] -= barLen
                    placed = true
                    break
                }
            }
            if (!placed) {
                stocksCount++
                bins.add(stockLength - barLen)
            }
        }

        val totalUsed = sortedBars.sum()
        val totalBought = stocksCount * stockLength
        val efficiency = (totalUsed / totalBought) * 100.0

        return "Optimization Results: Use $stocksCount stock bars (12m). Site Efficiency: ${String.format(java.util.Locale.US, "%.1f", efficiency)}% (Waste: ${String.format(java.util.Locale.US, "%.1f", 100 - efficiency)}%)"
    }

    /**
     * Combines multiple elements into a single project-level BBS.
     */
    fun combineProjectBbs(allElements: List<List<BbsEntry>>): List<BbsEntry> {
        return allElements.flatten()
            .groupBy { "${it.diameter}-${it.shapeCode}-${it.lengthA}-${it.lengthB}" }
            .map { entry ->
                val first = entry.value.first()
                first.copy(
                    count = entry.value.sumOf { it.count },
                    totalWeightKg = entry.value.sumOf { it.totalWeightKg }
                )
            }
    }
}
