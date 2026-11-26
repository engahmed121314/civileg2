package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import com.civileg.app.domain.calculations.base.SeismicForceDistribution
import com.civileg.app.domain.calculations.base.SpectrumValue

/** Maps the SBC 301-CR-2018 ELF result onto the result types the seismic screen, drawing and PDF already use. No values are added here. */
object SbcSeismic301Adapter {
    const val REFERENCE = "SBC 301-CR-2018 Eq 12-12, 12-13 to 12-17 (p.80), Table 12-1 (p.98), Tables 11-1 to 11-4 (p.66)"

    fun baseShear(input: SbcElfInput, r: SbcElfResult): SeismicBaseShearResult = SeismicBaseShearResult(
        baseShear = r.v,
        zoneFactor = input.ss,                  // the screen labels this row; for SBC it is Ss (mapped 0.2 s acceleration, g)
        soilFactor = r.fa,
        importanceFactor = r.ie,
        responseModification = r.r,
        calculationFormula = "V = Cs W = %.4f x %.1f kN,  Cs = %s".format(r.cs, r.w, r.csGoverning),
        codeReference = REFERENCE,
        warnings = r.warnings,
        fundamentalPeriod = r.t,
        // Drift needs elastic analysis deflections (12.8.6): reported NOT ASSESSED, so no value and no pass flag is invented here.
        roofDisplacement = 0.0, maxStoryDrift = 0.0, allowableDrift = 0.0, driftRatio = 0.0, driftIsSafe = true
    )

    fun floorForces(input: SbcElfInput, r: SbcElfResult): List<SeismicForceDistribution> =
        input.storyWeights.indices.map { i ->
            var m = 0.0
            for (j in i until r.levelForces.size) m += r.levelForces[j] * (input.storyHeights[j] - input.storyHeights[i])
            SeismicForceDistribution(i, input.storyWeights[i], input.storyHeights[i], r.levelForces[i], r.storyShears[i], m)
        }

    /** Design spectrum (Eq 11-5 to 11-8) at periods up to 3 s for the curve; the design point uses [SbcElfResult.t]. */
    fun spectrum(input: SbcElfInput, r: SbcElfResult): List<SpectrumValue> = (1..50).map { i ->
        val t = i * 0.06
        SpectrumValue(SbcSeismic301.designSpectrum(t, r.sds, r.sd1, input.tl), t, 0.05, "SBC 301 Eq 11-5 to 11-8, SDS=%.3f g, SD1=%.3f g".format(r.sds, r.sd1))
    }

    fun spectralAccelAtT(input: SbcElfInput, r: SbcElfResult): Double = SbcSeismic301.designSpectrum(r.t, r.sds, r.sd1, input.tl)
}
