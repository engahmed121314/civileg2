package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطة الهوردي حسب الكود السعودي SBC 304-2018
 * SBC 304 Hordi (Ribbed/Joist) Slab Design — تطبيق مستقل وليس fallback لـ ACI
 *
 * الاختلافات الرئيسية عن ACI 318:
 * - SBC 304 يستخدم fcu مباشرة مع معاملات أمان جزئية: γc = 1.5, γs = 1.15
 * - أقل سماكة للـ topping: 50 مم (SBC) مقابل 38 مم (ACI) — للمناخ الحار
 * - أقل عرض للضلع: 100 مم (SBC) في المناطق الزلزالية
 * - تباعد أقصى للأسياخ مختلف: min(3d, 450mm) مع تعديل زلزالي min(2d, 250mm)
 * - نسبة تسليح أدنى أعلى: ρmin = 0.002 في المناطق الحارة (SBC)
 * - غطاء خرساني أكبر: 40 مم داخلي، 65 مم خارجي/ساحلي
 *
 * المراجع:
 * - SBC 304-2018 البند 6.4 (البلاطات المصمتة والهوردي)
 * - SBC 304-2018 البند 4.2 (الانحناء)
 * - SBC 304-2018 البند 4.3 (القص)
 * - SBC 304-2018 البند 21 (متطلبات الزلازل)
 * - SBC 304-2018 البند 7.7 (الغطاء الخرساني)
 */
class SBCHordiSlabDesign : HordiSlabDesign {

    companion object {
        // SBC 304 material safety factors
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15

        // SBC 304 strength reduction factors
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75

        // SBC 304-2018 limits
        private const val MIN_RIB_WIDTH = 100.0        // mm — SBC 304 §6.4
        private const val MIN_TOPPING = 50.0            // mm — SBC (أعلى من ACI بسبب المناخ الحار)
        private const val MAX_RIB_SPACING = 700.0       // mm
        private const val MIN_REIN_RATIO = 0.002        // SBC 304 (أعلى من ACI بسبب المناخ)
        private const val COVER_INTERIOR = 40.0         // mm — SBC 304 §7.7
        private const val COVER_CORROSIVE = 65.0        // mm — المناطق الساحلية
        private const val COVER_SEISMIC = 50.0          // mm — المناطق الزلزالية
        private const val EPSILON_CU = 0.003
        private const val ES = 200000.0                 // MPa
    }

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
        loadCombination: LoadCombination
    ): SlabDesignResult {
        // ── InputGuard (ADR-010) — SBC 304-2018 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("toppingThickness", toppingThickness)
        InputGuard.positive("span", span)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.nonNegative("designShear", designShear)
        InputGuard.notNull("loadCombination", loadCombination)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ══════════════════════════════════════════════════════════════════
        // 1. SBC 304-specific checks
        // ══════════════════════════════════════════════════════════════════
        if (ribWidth < MIN_RIB_WIDTH) {
            warnings.add("SBC 304-6.4: عرض الضلع ${ribWidth}مم < ${MIN_RIB_WIDTH}مم (الحد الأدنى)")
        }
        if (toppingThickness < MIN_TOPPING) {
            warnings.add("SBC 304-6.4: سماكة الـ topping ${toppingThickness}مم < ${MIN_TOPPING}مم (الحد الأدنى للمناخ الحار)")
        }
        if (ribSpacing > MAX_RIB_SPACING) {
            warnings.add("SBC 304-6.4: تباعد الضلوع ${ribSpacing}مم > ${MAX_RIB_SPACING}مم (الحد الأقصى)")
        }

        // ══════════════════════════════════════════════════════════════════
        // 2. Material Properties (SBC 304)
        // ══════════════════════════════════════════════════════════════════
        // SBC 304 follows ACI 318: f'c = 0.8 × fcu (cube-to-cylinder)
        val fc = 0.8 * fcu
        val fcDesign = fc / GAMMA_C
        val fsDesign = fy / GAMMA_S

        val cover = when (loadCombination) {
            LoadCombination.DEAD_LIVE_EARTHQUAKE, LoadCombination.DEAD_EARTHQUAKE -> COVER_SEISMIC
            else -> COVER_INTERIOR
        }

        // Effective depth
        val ribHeight = totalThickness - toppingThickness
        val d = ribHeight - cover - 10.0  // deduct cover + half bar dia
        val b = ribWidth

        // ══════════════════════════════════════════════════════════════════
        // 3. Flexural Design — Rn-ρ method per SBC 304 §4.2
        // ══════════════════════════════════════════════════════════════════
        val Mu = designMoment * 1e6  // N.mm per rib
        val Rn = if (d > 0 && b > 0) Mu / (PHI_FLEXURE * b * d * d) else 0.0

        // β1 factor per SBC 304
        val beta1 = when {
            fc <= 28.0 -> 0.85
            fc >= 55.0 -> 0.65
            else -> 0.85 - 0.05 * (fc - 28.0) / 7.0
        }

        // Balanced reinforcement ratio
        val epsilonY = fy / ES
        val rhoBal = 0.85 * beta1 * (fc / fy) * (EPSILON_CU / (EPSILON_CU + epsilonY))
        val RnBal = rhoBal * fy * (1.0 - 0.5 * rhoBal * fy / (0.85 * fc))

        // Solve for ρ
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fc)
        val rho = if (discriminant > 0) {
            (0.85 * fc / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add("SBC 304: Compression failure — increase depth or width")
            rhoBal * 0.9  // fallback to near-balanced
        }

        // SBC 304 minimum reinforcement (higher for hot climate)
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fc) / fy)
        val rhoFinal = max(rho, rhoMin)
        val asRequired = rhoFinal * b * d

        // ══════════════════════════════════════════════════════════════════
        // 4. Bar Selection (Saudi market sizes: 12, 14, 16, 20, 25, 32)
        // ══════════════════════════════════════════════════════════════════
        val availableBars = listOf(12.0, 14.0, 16.0, 20.0, 25.0, 32.0)
        val barDiameter = availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            val numBars = ceil(asRequired / area).toInt()
            numBars in 2..6  // reasonable range for rib
        } ?: 16.0

        val barArea = PI * barDiameter * barDiameter / 4.0
        val numBars = ceil(asRequired / barArea).toInt().coerceIn(2, 6)
        val asProvided = numBars * barArea

        // Max spacing per SBC 304 (seismic modification)
        val isSeismic = loadCombination == LoadCombination.DEAD_LIVE_EARTHQUAKE ||
                        loadCombination == LoadCombination.DEAD_EARTHQUAKE
        val maxSpacing = if (isSeismic) {
            min(2.0 * d, 250.0)  // SBC 304-21: stricter spacing in seismic zones
        } else {
            min(3.0 * d, 450.0)  // SBC 304 normal
        }

        // ══════════════════════════════════════════════════════════════════
        // 5. Shear Design per SBC 304 §4.3
        // ══════════════════════════════════════════════════════════════════
        // Vc = 0.24 × √fcu × bw × d (SBC 304 with fcu directly)
        val Vc = 0.24 * sqrt(fcu) * b * d / 1000.0  // kN per rib
        val phiVc = PHI_SHEAR * Vc
        val isShearSafe = designShear <= phiVc

        if (!isShearSafe) {
            warnings.add("SBC 304: القص ${String.format("%.1f", designShear)} كن > φVc ${String.format("%.1f", phiVc)} كن — زِد سماكة الضلع")
        }

        // Seismic shear check (SBC 304-21.5)
        if (isSeismic) {
            val seismicMaxSpacing = min(d / 4.0, 100.0)  // mm
            codeNotes.add("SBC 304-21: Seismic stirrup max spacing = ${seismicMaxSpacing.toInt()}mm")
        }

        // ══════════════════════════════════════════════════════════════════
        // 6. Topping Design (SBC 304-6.4.3)
        // ══════════════════════════════════════════════════════════════════
        val toppingD = toppingThickness - 20.0 - 4.0  // cover + half bar
        val toppingMinAs = MIN_REIN_RATIO * 1000.0 * toppingD  // mm²/m
        val toppingBarDia = 8.0  // typical topping mesh
        val toppingBarArea = PI * toppingBarDia * toppingBarDia / 4.0
        val toppingBarsPerM = ceil(toppingMinAs / toppingBarArea).toInt().coerceIn(5, 12)
        val toppingSpacing = floor(1000.0 / toppingBarsPerM).coerceIn(100.0, 300.0)

        // ══════════════════════════════════════════════════════════════════
        // 7. Code Notes & Results
        // ══════════════════════════════════════════════════════════════════
        codeNotes.add("SBC 304-2018 §6.4: Hordi Slab Design (independent SBC implementation)")
        codeNotes.add("f'c = 0.8 × fcu = ${String.format("%.1f", fc)} MPa (SBC 304/ACI conversion)")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S, φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")
        codeNotes.add("Rib: ${ribWidth.toInt()}×${ribHeight.toInt()}mm, d=${String.format("%.0f", d)}mm")
        codeNotes.add("Main: ${numBars}Ø${barDiameter.toInt()} (${String.format("%.0f", asProvided)} mm²)")
        codeNotes.add("Topping: Ø${toppingBarDia.toInt()} @ ${toppingSpacing.toInt()}mm mesh")
        codeNotes.add("ρ = ${String.format("%.4f", rhoFinal)}, ρ_min = ${String.format("%.4f", rhoMin)}, ρ_bal = ${String.format("%.4f", rhoBal)}")
        codeNotes.add("Vc = ${String.format("%.1f", Vc)} kN, φVc = ${String.format("%.1f", phiVc)} kN")
        codeNotes.add("Cover = ${cover.toInt()}mm (${if (isSeismic) "seismic" else "interior"} per SBC 304 §7.7)")

        if (isSeismic) {
            codeNotes.add("SBC 304-21: Seismic provisions applied")
        }

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asProvided,
            barDiameter = barDiameter,
            barSpacing = if (numBars > 1) ribWidth / (numBars - 1) else ribWidth,
            minThickness = totalThickness,
            shearCapacity = Vc,
            isSafe = discriminant > 0 && isShearSafe && rhoFinal <= rhoBal * 1.5,
            utilizationRatio = if (RnBal > 0) Rn / RnBal else 2.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }
}
