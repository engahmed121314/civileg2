package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.BarBondClass
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.calculations.base.HordiDeemedToSatisfy
import com.civileg.app.domain.calculations.base.SlabSupportCondition
import com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطات الهوردي (Joist Construction) حسب ACI 318-25 Section 9.8
 */
class ACIJoistSlab : HordiSlabDesign {

    override fun designHordiSlab(
        fcu: Double,
        fy: Double,
        ribWidth: Double,
        ribSpacing: Double,
        totalThickness: Double,
        toppingThickness: Double,
        span: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination,
        coverMm: Double,
        linkDiaMm: Double,
        mainBarDiaMm: Double,
        barBondClass: BarBondClass,
        supportCondition: SlabSupportCondition,
        loadingApplicability: DeflectionLoadingApplicability
    ): SlabDesignResult {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("toppingThickness", toppingThickness)
        InputGuard.positive("span", span)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.nonNegative("designShear", designShear)

        val fc = fcu * 0.8
        val b = ribSpacing
        val bw = ribWidth
        val h = totalThickness
        val hf = toppingThickness
        val d = h - coverMm - mainBarDiaMm / 2.0 // Effective depth from caller cover + bar radius

        // ACI 9.8.1.1 to 9.8.1.3 Limits
        val isGeometrySafe = bw >= 100.0 && 
                             (h - hf) <= 3.5 * bw &&
                             (ribSpacing - bw) <= 750.0 // ACI 318M-14 9.8.1.4: clear spacing <= 750 mm (was 762.0, imperial 30in)

        // Flexure Design
        val phi = 0.9
        val Rn = (designMoment * 1e6) / (phi * b * d * d)
        val m = fy / (0.85 * fc)
        val rho = if (m > 0) (1.0 / m) * (1 - sqrt(max(0.0, 1 - 2 * m * Rn / fy))) else 0.0
        val astRequired = rho * b * d
        
        // Shear Design
        val Vc = 0.17 * sqrt(fc) * bw * d / 1000.0
        val phiVc = 0.75 * 1.1 * Vc

        // ── Serviceability — ACI 318M-14 Table 9.3.1.1 deemed-to-satisfy l/h ──
        // (supplied bilingual ACI translation, vol 1 code page 211; TOC provenance
        // matched to the official ACI 318M-14 preview per independent recovery check)
        val serv = HordiDeemedToSatisfy.aci(span, h, fy, supportCondition, loadingApplicability)
        val servNotes = mutableListOf<String>()
        val servWarnings = mutableListOf<String>()
        if (!serv.assessed) {
            servNotes.add("ACI 318M-14: " + serv.summary)
        } else if (!serv.safe) {
            servWarnings.add("Deflection deemed-to-satisfy FAILED: " + serv.summary + " — increase depth or satisfy ACI 318M-14 9.3.2 calculated deflection limits")
        } else {
            servNotes.add("Deflection deemed-to-satisfy PASS: " + serv.summary)
        }

        val isSafe = isGeometrySafe && designShear <= phiVc && (!serv.assessed || serv.safe)

        return SlabDesignResult(
            requiredReinforcement = astRequired,
            providedReinforcement = astRequired,
            barDiameter = 12.0,
            barSpacing = ribSpacing,
            minThickness = h,
            shearCapacity = phiVc,
            isSafe = isSafe,
            utilizationRatio = if (phiVc > 0) designShear / phiVc else 0.0,
            warnings = servWarnings,
            codeNotes = listOf("Effective depth d = h - coverMm - mainBarDiaMm/2 = h - ${(coverMm + mainBarDiaMm / 2.0).toInt()} mm (caller inputs; previously fixed h - 25); linkDiaMm=${linkDiaMm.toInt()} accepted, not used in this effective-depth convention") + servNotes,
            bondClassAssessed = false, // bar surface/bond not assessed by this engine
            barBondClass = barBondClass, // caller selection carried for traceability, NOT assessed
            serviceabilityAssessed = serv.assessed // ACI 318M-14 Table 9.3.1.1 deemed-to-satisfy when support+loading explicitly declared
        )
    }
}
