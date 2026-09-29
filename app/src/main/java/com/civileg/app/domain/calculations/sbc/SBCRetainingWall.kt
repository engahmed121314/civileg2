package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

/**
 * تصميم جدار الاستناد حسب الكود السعودي SBC 304-2018 — مستقل بالكامل عن ACI
 *
 * فروقات SBC 304 عن ACI 318 في جدران الاستناد:
 * - معاملات أمان مختلفة: FS_OT ≥ 1.5 (SBC) مقابل FS_OT ≥ 2.0 (ACI)
 * - FS_Slide ≥ 1.5 (كلاهما متفق)
 * - الغطاء الخرساني أكبر: 50mm خارجي / 65mm شديد / 75mm ساحلي
 * - معاملات تحميل مختلفة: γD = 1.2, γL = 1.6, γLateral = 1.6
 * - اعتبارات البيئة المالحة والمناخ الحار السعودية
 * - زيادة ضغط الأرض الزلزالي (0.5×Ka) للمناطق الزلزالية
 * - ضغط الماء الجوفي معامل تحميل 1.6
 *
 * المراجع:
 * - SBC 304-2018 البند 13 (القواعد والجدران الاستنادية)
 * - SBC 304-2018 البند 9.3 (معاملات الاختزال)
 * - SBC 304-2018 البند 4 (متطلبات عامة — غطاء خرساني)
 * - SBC 304-2018 البند 18 (متطلبات زلزالية)
 */
class SBCRetainingWall : RetainingWallDesign {

    companion object {
        // ── SBC 304-2018 معاملات الاختزال ──
        private const val PHI_FLEXURE = 0.9    // §9.3.3
        private const val PHI_SHEAR = 0.75     // §9.3.4

        // ── SBC 304-2018 معاملات التحميل ──
        private const val LF_DEAD = 1.2        // §9.2
        private const val LF_LIVE = 1.6        // §9.2
        private const val LF_LATERAL = 1.6     // §9.2 — ضغط الأرض

        // ── SBC 304-2018 الغطاء الخرساني (§4 — أكثر تحفظاً من ACI) ──
        private const val COVER_EARTH = 75.0       // mm — تلامس التربة
        private const val COVER_INTERIOR = 40.0    // mm — داخلية
        private const val COVER_EXTERIOR = 50.0    // mm — خارجية
        private const val COVER_SEVERE = 65.0      // mm — شديد التآكل
        private const val COVER_COASTAL = 75.0     // mm — مناطق ساحلية

        // ── SBC 304-2018 معاملات الأمان ──
        private const val MIN_STEEL_RATIO = 0.002
        private const val OT_FS_LIMIT = 1.5        // SBC: 1.5 (أقل من ACI 2.0)
        private const val SLIDE_FS_LIMIT = 1.5     // SBC: 1.5
        private const val BEARING_FS_LIMIT = 2.0   // SBC: 2.0
    }

    override fun designRetainingWall(input: RetainingWallInput): RetainingWallResult {
        // ── InputGuard: تحقق صارم قبل أي حساب (ADR-010) ──
        InputGuard.notNull("input", input)
        InputGuard.positive("wallHeight", input.wallHeight)
        InputGuard.positive("stemBaseThickness", input.stemBaseThickness)
        InputGuard.positive("stemTopThickness", input.stemTopThickness)
        InputGuard.positive("baseWidth", input.baseWidth)
        InputGuard.positive("baseThickness", input.baseThickness)
        InputGuard.positive("toeLength", input.toeLength)
        InputGuard.positive("heelLength", input.heelLength)
        InputGuard.positive("soilDensity", input.soilDensity)
        InputGuard.positive("frictionAngle", input.frictionAngle)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)
        InputGuard.positive("baseFrictionCoeff", input.baseFrictionCoeff)
        InputGuard.positive("soilBearingCapacity", input.soilBearingCapacity)
        InputGuard.nonNegative("surchargeLoad", input.surchargeLoad)

        val H = input.wallHeight
        val tBase = input.stemBaseThickness
        val tTop = input.stemTopThickness
        val B = input.baseWidth
        val tFooting = input.baseThickness
        val toe = input.toeLength
        val heel = input.heelLength
        val gamma = input.soilDensity
        val phi = input.frictionAngle
        val q = input.surchargeLoad
        val zwt = input.waterTableDepth
        val fcu = input.fcu
        val fy = input.fy
        val mu = input.baseFrictionCoeff

        // ── تحويل المقاومة: f'c = 0.8 × fcu — SBC 304 §4.1 ──
        val fc = 0.8 * fcu

        // ══════════════════════════════════════════════════════════════
        // 1. ضغط الأرض النشط — Rankine Theory
        // ══════════════════════════════════════════════════════════════
        val phiRad = Math.toRadians(phi)
        val Ka = tan(Math.PI / 4 - phiRad / 2).pow(2)

        // حساب ضغط التربة والماء
        val hSoil = if (zwt >= H) H else zwt
        val hWater = max(0.0, H - zwt)
        val gammaSub = gamma - 9.81  // وزن التربة المغمورة

        // قوى الأرض النشطة
        val paSoil = 0.5 * Ka * gamma * hSoil.pow(2)
        val paWater = 0.5 * 9.81 * hWater.pow(2)
        val paSurcharge = Ka * q * H
        val totalPa = paSoil + paWater + paSurcharge
        val paArm = H / 3.0

        // عزوم الانقلاب
        val momentOT = paSoil * paArm + paWater * (hWater / 3.0 + hSoil) + paSurcharge * (H / 2.0)

        // ══════════════════════════════════════════════════════════════
        // 2. قوى المقاومة (أوزان المنشأ + التربة)
        // ══════════════════════════════════════════════════════════════
        val stemW = 0.5 * (tBase + tTop) * H * 25.0
        val baseW = B * tFooting * 25.0
        val soilW = heel * (H - tFooting) * gamma
        val totalW = stemW + baseW + soilW

        // أذرع القوى من مقدمة القاعدة
        val stemArm = toe + (tBase * (H + 2 * tTop)) / (3 * (tBase + tTop))
        val baseArm = B / 2.0
        val heelArm = toe + tBase / 2.0 + heel / 2.0

        val momentR = stemW * stemArm + baseW * baseArm + soilW * heelArm

        // ══════════════════════════════════════════════════════════════
        // 3. فحوصات الاستقرار — SBC 304 §13
        // ══════════════════════════════════════════════════════════════

        // أ) الانقلاب — SBC 304: FS_OT ≥ 1.5
        val otFS = momentR / momentOT

        // ب) الانزلاق — SBC 304: FS_Slide ≥ 1.5
        val Kp = tan(Math.PI / 4 + phiRad / 2).pow(2)
        val pp = 0.5 * gamma * tFooting.pow(2) * Kp
        val slideFS = (mu * totalW + pp * 0.5) / totalPa

        // ج) إجهاد التربة — SBC 304 §13.3
        val ecc = abs(B / 2.0 - (momentR - momentOT) / totalW)
        val maxBP = totalW / B * (1 + 6 * ecc / B)
        val minBP = max(0.0, totalW / B * (1 - 6 * ecc / B))
        val bearingFS = if (maxBP > 0) input.soilBearingCapacity / maxBP else 0.0

        // ══════════════════════════════════════════════════════════════
        // 4. تصميم الذراع (Stem) — SBC 304 §9 (انحناء + قص)
        // ══════════════════════════════════════════════════════════════
        val stemH = H - tFooting

        // عزم التصميم مع معاملات تحميل SBC
        // Mu = γ_Lateral × (Ka × γ × H³/6 + Ka × q × H²/2) × 1e6
        val MuStem = LF_LATERAL * (Ka * gamma * stemH.pow(3) / 6 + Ka * q * stemH.pow(2) / 2) * 1e6  // N.mm
        val VuStem = LF_LATERAL * (Ka * gamma * stemH.pow(2) / 2 + Ka * q * stemH)  // kN

        val b = 1000.0  // mm — شريط بعرض 1m
        val d = tBase * 1000 - COVER_EARTH - 10.0

        // طريقة Rn-ρ — SBC 304 §9.3.3
        val Rn = MuStem / (PHI_FLEXURE * b * d * d)
        val rho = 0.85 * fc / fy * (1 - sqrt(max(0.0, 1 - 2 * Rn / (0.85 * fc))))
        val rhoMin = MIN_STEEL_RATIO
        val rhoMax = 0.025  // SBC 304 §9.3.3.1
        val rhoFinal = rho.coerceIn(rhoMin, rhoMax)
        val As = rhoFinal * b * d

        val (nBars, barDia) = RetainingWallDesign.selectBars(As)
        val AsProv = nBars * PI * (barDia / 2.0).pow(2)

        // تسليح توزيعي — SBC 304 §9.6.1
        val distAs = max(rhoMin * b * d * 0.25, 100.0)
        val distBars = RetainingWallDesign.selectBars(distAs)

        // فحص القص — SBC 304 §11
        val qu = VuStem * 1000 / (b * d)
        val phiVc = PHI_SHEAR * 0.17 * sqrt(fc) * b * d  // N
        val shearOk = VuStem * 1000 <= phiVc

        // ══════════════════════════════════════════════════════════════
        // 5. تصميم الإصبع (Toe) — SBC 304 §13
        // ══════════════════════════════════════════════════════════════
        val toeMu = max(0.0, (maxBP * toe * toe / 2 - minBP * toe * toe / 6)) * 1e6
        val toeD = tFooting * 1000 - COVER_EARTH - 10.0
        val toeRn = toeMu / (PHI_FLEXURE * b * toeD * toeD)
        val toeRho = 0.85 * fc / fy * (1 - sqrt(max(0.0, 1 - 2 * toeRn / (0.85 * fc))))
        val toeAs = max(toeRho, rhoMin) * b * toeD
        val tb = RetainingWallDesign.selectBars(toeAs)

        // ══════════════════════════════════════════════════════════════
        // 6. تصميم الكعب (Heel) — SBC 304 §13
        // ══════════════════════════════════════════════════════════════
        // SBC 304: وزن التربة = حمل ميت → γD = 1.2
        val heelLoad = (H - tFooting) * gamma + q
        val heelMu = heelLoad * heel * heel / 2 * LF_DEAD * 1e6  // N.mm
        val heelD = tFooting * 1000 - COVER_EARTH - 10.0
        val heelRn = heelMu / (PHI_FLEXURE * b * heelD * heelD)
        val heelRho = 0.85 * fc / fy * (1 - sqrt(max(0.0, 1 - 2 * heelRn / (0.85 * fc))))
        val heelAs = max(heelRho, rhoMin) * b * heelD
        val hb = RetainingWallDesign.selectBars(heelAs)

        // ══════════════════════════════════════════════════════════════
        // 7. تجميع فحوصات الأمان — SBC 304
        // ══════════════════════════════════════════════════════════════
        val checks = listOf(
            WallSafetyCheck("OT FS", otFS >= OT_FS_LIMIT, otFS, OT_FS_LIMIT,
                "SBC 304: FS=${"%.2f".format(otFS)} >= ${OT_FS_LIMIT}"),
            WallSafetyCheck("Sliding FS", slideFS >= SLIDE_FS_LIMIT, slideFS, SLIDE_FS_LIMIT,
                "SBC 304: FS=${"%.2f".format(slideFS)} >= ${SLIDE_FS_LIMIT}"),
            WallSafetyCheck("Bearing", maxBP <= input.soilBearingCapacity, maxBP, input.soilBearingCapacity,
                "${"%.1f".format(maxBP)} <= ${"%.1f".format(input.soilBearingCapacity)} kN/m\u00B2"),
            WallSafetyCheck("Stem Flexure", AsProv >= As * 0.95, AsProv, As,
                "\u03C1=${"%.4f".format(rhoFinal)} (min=${rhoMin})"),
            WallSafetyCheck("Shear", shearOk, qu, phiVc / (b * d),
                "Vu=${"%.0f".format(qu)} <= \u03C6Vc=${"%.0f".format(phiVc / (b * d))} N/mm\u00B2")
        )

        val isSafe = checks.all { it.isSafe }

        // ══════════════════════════════════════════════════════════════
        // 8. ملاحظات الكود السعودي
        // ══════════════════════════════════════════════════════════════
        val notes = mutableListOf(
            "SBC 304-2018 §13: جدران الاستناد",
            "SBC 304 §9.3: \u03C6f=${PHI_FLEXURE}, \u03C6v=${PHI_SHEAR}",
            "SBC 304 §9.2: \u03B3D=${LF_DEAD}, \u03B3L=${LF_LIVE}, \u03B3Lat=${LF_LATERAL}",
            "f'c = 0.8\u00D7fcu = ${"%.0f".format(fc)} MPa",
            "Ka = ${"%.3f".format(Ka)} (Rankine)",
            "Cover = ${COVER_EARTH}mm (تلامس التربة — SBC §4)",
            "Min \u03C1 = ${MIN_STEEL_RATIO} (مناخ حار — متانة)",
            "FS_OT ≥ ${OT_FS_LIMIT}, FS_Slide ≥ ${SLIDE_FS_LIMIT} (SBC §13)"
        )

        if (hWater > 0) {
            notes.add("منسوب الماء عند ${"%.1f".format(zwt)}m — ضغط جوفي مُضمَّن")
        }

        // SBC 304 §18: اعتبارات زلزالية
        if (otFS < 2.0) {
            notes.add("SBC 304 §18: ضع في الاعتبار زيادة ضغط الأرض الزلزالي (0.5\u00D7Ka) في المناطق الزلزالية")
        }

        // SBC 304 §4: اعتبارات البيئة المالحة
        notes.add("بيئة مالحة: غطاء ${COVER_EXTERIOR}mm خارجي / ${COVER_COASTAL}mm ساحلي")

        return RetainingWallResult(
            isSafe = isSafe,
            designCode = DesignCode.SBC,
            overturningFS = otFS,
            slidingFS = slideFS,
            bearingFS = bearingFS,
            maxBearingPressure = maxBP,
            minBearingPressure = minBP,
            stemMoment = MuStem / 1e6,
            stemShear = VuStem,
            stemMainRebar = RetainingWallDesign.formatRebar(nBars, barDia),
            stemMainRebarArea = AsProv,
            stemDistributionRebar = "${distBars.first}\u03A6${distBars.second}",
            toeMoment = toeMu / 1e6,
            toeShear = maxBP * toe,
            toeRebar = "${tb.first}\u03A6${tb.second}",
            heelMoment = heelMu / 1e6,
            heelShear = heelLoad * heel,
            heelRebar = "${hb.first}\u03A6${hb.second}",
            safetyChecks = checks,
            codeNotes = notes
        )
    }
}
