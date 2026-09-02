package com.civileg.core.engineering

/**
 * Seismic-analysis chart sheet detail, fed to the DrawingModel seismic builder.
 *
 * Pure passthrough of the app-side seismic engine results
 * ([com.civileg.app.domain.calculations.base.SeismicDesign]): the response
 * spectrum curve points (T, Sa), the per-floor lateral forces, the base shear
 * and its zone/soil/importance/reduction factors, and the code citation are all
 * the engine's own values — nothing strength-related is recomputed here. The
 * builder only turns the curve/forces into paper positions (pure layout), so
 * the emitter draws a verified chart whose numbers are identical to the
 * on-screen results. [SeismicDetailResult.isSafe] plus
 * [SeismicDetailResult.codeReference] drive the drawing state/note, and the
 * base-shear terms feed the parameter ledger in the sheet table.
 */
data class SeismicSpectrumPoint(
    /** Vibration period (s) — engine passthrough. */
    val period: Double,
    /** Spectral acceleration (g) — engine passthrough. */
    val acceleration: Double
)

data class SeismicFloorForcePoint(
    /** Engine 0-based floor index (label only). */
    val floorIndex: Int,
    /** Floor height from base (m) — engine passthrough. */
    val floorHeight: Double,
    /** Lateral force at this floor, Fi (kN) — engine passthrough. */
    val forceKn: Double,
    /** Cumulative story shear, Vi (kN) — engine passthrough. */
    val storyShearKn: Double
)

data class SeismicDetailResult(
    /** Response spectrum curve — every point passed through verbatim. */
    val spectrumPoints: List<SeismicSpectrumPoint>,
    /** Per-floor lateral force distribution. */
    val floorForces: List<SeismicFloorForcePoint>,
    /** Base shear V (kN) — engine passthrough. */
    val baseShearKn: Double,
    /** Zone factor Z — engine passthrough. */
    val zoneFactor: Double,
    /** Soil factor S — engine passthrough. */
    val soilFactor: Double,
    /** Importance factor I — engine passthrough. */
    val importanceFactor: Double,
    /** Response modification factor R — engine passthrough. */
    val responseModification: Double,
    /** Fundamental period T1 (s) — engine passthrough. */
    val fundamentalPeriod: Double,
    /** Spectral acceleration Sa(T1) (g) — engine passthrough. */
    val spectralAccel: Double,
    /** Engine's own equation label (ledger/note). */
    val calculationFormula: String,
    /** Engine's own code citation. */
    val codeReference: String,
    /** Overall verdict — base shear > 0 (tone only). */
    val isSafe: Boolean,
    /** Engine warnings, echoed as WARNING-layer notes. */
    val warnings: List<String> = emptyList()
)