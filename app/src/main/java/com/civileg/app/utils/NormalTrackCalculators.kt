package com.civileg.app.utils

import kotlin.math.ceil

/**
 * Plain-language calculators for the NORMAL user track (research-and-ux-protocol.md §2.2).
 * No code clauses / φ / ρ appear anywhere — inputs are everyday dimensions.
 * All internal math in metres & m³; results rounded for display.
 */
object NormalTrackCalculators {

    // ── Quantity takeoff ─────────────────────────────────────────────────

    data class SlabQuantity(val concreteM3: Double, val formworkM2: Double)

    fun slabQuantity(lengthM: Double, widthM: Double, thicknessCm: Double): SlabQuantity {
        val t = thicknessCm / 100.0
        val volume = lengthM * widthM * t
        // Formwork ≈ soffit area only (edges negligible for simple estimate)
        return SlabQuantity(volume.round2(), (lengthM * widthM).round2())
    }

    data class BeamQuantity(val concreteM3: Double, val formworkM2: Double)

    fun beamQuantity(count: Int, spanM: Double, widthCm: Double, depthCm: Double): BeamQuantity {
        val w = widthCm / 100.0
        val d = depthCm / 100.0
        val volume = count * spanM * w * d
        // Soffit + two sides per beam; top is slab (cast together)
        val formwork = count * spanM * (w + 2 * d)
        return BeamQuantity(volume.round2(), formwork.round2())
    }

    data class ColumnQuantity(val concreteM3: Double, val formworkM2: Double)

    fun columnQuantity(count: Int, widthCm: Double, depthCm: Double, heightM: Double): ColumnQuantity {
        val w = widthCm / 100.0
        val d = depthCm / 100.0
        val volume = count * w * d * heightM
        val formwork = count * (w + d) * 2 * heightM
        return ColumnQuantity(volume.round2(), formwork.round2())
    }

    data class WallQuantity(val concreteM3: Double, val formworkM2: Double)

    fun wallQuantity(lengthM: Double, heightM: Double, thicknessCm: Double): WallQuantity {
        val t = thicknessCm / 100.0
        val volume = lengthM * heightM * t
        val formwork = 2 * lengthM * heightM   // both faces
        return WallQuantity(volume.round2(), formwork.round2())
    }

    // ── Finishing ────────────────────────────────────────────────────────

    data class TilesResult(val tilesCount: Int, val boxesCount: Int, val areaWithWasteM2: Double)

    /**
     * @param tileLengthCm/tileWidthCm single tile size
     * @param boxTiles tiles per box as printed on the box
     * @param wastePercent recommended 8–12% for cutting losses
     */
    fun tiles(areaM2: Double, tileLengthCm: Double, tileWidthCm: Double,
              boxTiles: Int, wastePercent: Double): TilesResult {
        val tileArea = (tileLengthCm / 100.0) * (tileWidthCm / 100.0)
        val areaWithWaste = areaM2 * (1 + wastePercent / 100.0)
        val tiles = ceil(if (tileArea > 0) areaWithWaste / tileArea else 0.0).toInt()
        val boxes = ceil(if (boxTiles > 0) tiles.toDouble() / boxTiles else 0.0).toInt()
        return TilesResult(tiles, boxes, areaWithWaste.round2())
    }

    data class PaintResult(val coatsAreaM2: Double, val litersNeeded: Double, val gallons: Int)

    /**
     * @param coverageM2PerLiter per coat coverage printed on the paint can
     * @param coats typical: 2 (primer excluded)
     */
    fun paint(wallAreaM2: Double, coats: Int, coverageM2PerLiter: Double): PaintResult {
        val totalArea = wallAreaM2 * coats.coerceAtLeast(1)
        val liters = if (coverageM2PerLiter > 0) totalArea / coverageM2PerLiter else 0.0
        // Gallon ≈ 18 L (regional standard packaging)
        return PaintResult(totalArea.round2(), liters.round1(), ceil(liters / 18.0).toInt())
    }

    data class PlasterResult(val areaBothFacesM2: Double, val mortarM3: Double, val cementBags: Int)

    /**
     * Two faces plastered; ~0.025 m³ mortar per m² at 1cm average thickness.
     * Mortar 1:4 → ≈ 6 bags cement per m³ (rule of thumb used on site).
     */
    fun plaster(wallAreaM2: Double): PlasterResult {
        val area = wallAreaM2 * 2
        val mortar = area * 0.025
        return PlasterResult(area.round2(), mortar.round2(), ceil(mortar * 6).toInt())
    }

    // ── Quick concrete estimator ─────────────────────────────────────────

    /** Solid raft/pad footing with a pedestal hole deduction ignored (rough estimate). */
    fun footingVolume(lengthM: Double, widthM: Double, thicknessCm: Double): Double =
        (lengthM * widthM * thicknessCm / 100.0).round2()

    private fun Double.round2() = kotlin.math.round(this * 100) / 100
    private fun Double.round1() = kotlin.math.round(this * 10) / 10
}
