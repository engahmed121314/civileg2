package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorDetailingV4

/**
 * R-CAD Phase A — rebar zoning / marking / annotation domain (spec §15, §28-§30).
 * All lengths in millimetres unless annotated otherwise.
 */

/** Densification / spacing zone along a member (spec §15). */
data class RebarZone(
    val startMm: Double,
    val endMm: Double,
    val diameterMm: Int,
    val spacingMm: Double,
    val mark: String,
    val description: String
) {
    init {
        require(endMm > startMm) { "RebarZone $mark: endMm ($endMm) must exceed startMm ($startMm)" }
        require(spacingMm > 0.0) { "RebarZone $mark: spacing must be > 0" }
    }

    /** Number of ties/bars inside this zone (inclusive both ends). */
    val count: Int get() = ((endMm - startMm) / spacingMm).toInt() + 1

    fun overlaps(other: RebarZone): Boolean =
        startMm < other.endMm && other.startMm < endMm
}

/**
 * §28 — unique bar-mark allocator per drawing package.
 * Rejects duplicates; prefixes by element family (B/C/F/S/W/T/K).
 */
class BarMarkRegistry {
    private val issued = linkedMapOf<String, String>() // mark -> description

    fun next(prefix: String, description: String): String {
        require(prefix.isNotBlank()) { "BarMarkRegistry: prefix must not be blank" }
        var n = 1
        var candidate = "$prefix$n"
        while (issued.containsKey(candidate)) {
            n++
            candidate = "$prefix$n"
        }
        issued[candidate] = description
        return candidate
    }

    fun register(manual: String, description: String): String {
        require(!issued.containsKey(manual)) {
            "Duplicate bar mark '$manual' in the same drawing package"
        }
        issued[manual] = description
        return manual
    }

    val allMarks: Set<String> get() = issued.keys
    fun describe(mark: String): String? = issued[mark]
}

/** §29 — leader with elbow + anchored text. */
data class Leader(
    val text: String,
    val targetX: Double, val targetY: Double,
    val elbowX: Double, val elbowY: Double,
    val textAnchorX: Double,
    val layer: String = CadLayers.REBAR_DIM
)

/** §30 — section marker pair (A—A style). */
data class SectionMarker(
    val label: String,
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    val layer: String = CadLayers.TEXT
)

// ────────────────────────────────────────────────────────────────
// §27 — typed code-driven lengths (no silent constants)
// ────────────────────────────────────────────────────────────────

@JvmInline value class DevelopmentLengthMm(val value: Double)
@JvmInline value class LapLengthMm(val value: Double)
@JvmInline value class HookLengthMm(val value: Double)
@JvmInline value class AnchorageLengthMm(val value: Double)

/**
 * ECP 203-2020 development length:
 *   Ld = (0.25 · fy · db) / (α · √fcu)   with α = 1.25 (good bond conditions)
 * Lap = 1.3 · Ld (tension lap, ≤50% bars lapped at one section),
 * Hook (stirrup, 135° seismic) = 12Ø per IS 2502 / ACI 315 practice.
 * Sources verified via ADR-010 web research round 2026-08-26.
 */
object CodeLengths {
    fun development(fyMPa: Double, barDiaMm: Double, fcuMPa: Double, bondFactor: Double = 1.25): DevelopmentLengthMm {
        require(fyMPa > 0 && barDiaMm > 0 && fcuMPa > 0) { "CodeLengths.development: non-positive input" }
        return DevelopmentLengthMm((0.25 * fyMPa * barDiaMm) / (kotlin.math.sqrt(fcuMPa) * bondFactor))
    }
    fun tensionLap(ld: DevelopmentLengthMm, factor: Double = 1.3) =
        LapLengthMm(ld.value * factor)
    fun stirrupHook135(barDiaMm: Int): HookLengthMm = HookLengthMm(12.0 * barDiaMm)
}

/** ISO/BS-style shape codes supported by the bending engine (spec §25 subset). */
enum class IsoShapeCode(val code: String, val description: String) {
    S00("00", "Straight bar"),
    S11("11", "Rectangular stirrup / link (4×90°)"),
    S12("12", "Cranked / bent-up bar"),
    S21("21", "U-bar (180° or 2×90°)"),
    S22("22", "Closed tie with 135° seismic hooks"),
    S31("31", "L-bar (90° bend)"),
    S41("41", "Four-bend hook shape")
}
