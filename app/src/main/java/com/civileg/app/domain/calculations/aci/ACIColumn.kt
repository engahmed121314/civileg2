package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.ColumnShearDesignResult
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class ACIColumn : ColumnDesign {
    
    companion object {
        private const val BETA_1 = 0.85  // Concrete stress block factor
        private const val PHI_TIED = 0.65     // معامل الاختزال للأعمدة المربوطة (ACI 318-21.2.1)
        private const val PHI_SPIRAL = 0.75   // معامل الاختزال للأعمدة الحلزونية (ACI 318-21.2.1)
    }

    /**
     * Result of a strain-compatibility interaction check at the demand axial load.
     * ACI 318M-14 §22.2 (general principles), §20.2.2 (steel), Table 21.2.2 (φ).
     */
    data class InteractionCheckResult(
        val neutralAxis: Double,  // c, mm from compression face
        val epsT: Double,         // net tensile strain in extreme tension steel (22.2.1.2 sense)
        val phi: Double,          // strength reduction factor actually used (Table 21.2.2)
        val phiPn: Double,        // N
        val phiMn: Double         // N.mm about the centroidal axis
    )

    /** β1 stress-block factor per ACI 318M-14 Table 22.2.2.4.3. */
    private fun beta1(fc: Double): Double = when {
        fc <= 28.0 -> 0.85
        fc >= 55.0 -> 0.65
        else -> 0.85 - 0.05 * (fc - 28.0) / 7.0
    }

    /** φ for a tied column per ACI 318M-14 Table 21.2.2 (εty = fy/Es per 21.2.2.1). */
    private fun phiTied(epsT: Double, epsTy: Double): Double = when {
        epsT <= epsTy -> 0.65
        epsT >= 0.005 -> 0.90
        else -> 0.65 + 0.25 * (epsT - epsTy) / (0.005 - epsTy)
    }

    /**
     * Deterministic perimeter bar layout: 4 corner bars plus face bars evenly spaced;
     * the indivisible pair (when (nBars-4) % 4 == 2) goes to the top and bottom faces.
     * Coordinates in mm from the top-left corner; y runs along [depth].
     */
    fun layoutPerimeterBars(nBars: Int, width: Double, depth: Double, dPrime: Double): List<Pair<Double, Double>> {
        require(nBars >= 4 && nBars % 2 == 0) { "nBars must be even and >= 4" }
        val bars = mutableListOf(
            dPrime to dPrime, width - dPrime to dPrime,
            dPrime to depth - dPrime, width - dPrime to depth - dPrime
        )
        val extra = nBars - 4
        if (extra == 0) return bars
        val perFace = extra / 4
        val rem = (extra % 4) / 2          // 0 or 1 extra bar on each of top/bottom faces
        val nTop = perFace + rem
        val nSide = perFace
        val nBottom = nTop
        fun facePositions(n: Int, lo: Double, hi: Double): List<Double> =
            (1..n).map { lo + (hi - lo) * it / (n + 1.0) }
        for (x in facePositions(nTop, dPrime, width - dPrime)) bars += x to dPrime
        for (x in facePositions(nBottom, dPrime, width - dPrime)) bars += x to depth - dPrime
        for (y in facePositions(nSide, dPrime, depth - dPrime)) {
            bars += dPrime to y
            bars += width - dPrime to y
        }
        return bars
    }

    /** Groups bar ordinates into layers: (ordinate from compression face, summed area). */
    private fun groupLayers(ordinates: List<Double>, barArea: Double): List<Pair<Double, Double>> {
        val layers = mutableListOf<Pair<Double, Double>>()
        for (y in ordinates.sorted()) {
            val last = layers.lastOrNull()
            if (last != null && abs(last.first - y) < 1e-6) {
                layers[layers.lastIndex] = last.first to last.second + barArea
            } else {
                layers += y to barArea
            }
        }
        return layers
    }

    /**
     * Strain-compatibility uniaxial interaction check per ACI 318M-14:
     * §22.2.1.1 equilibrium, §22.2.1.2 plane sections, §22.2.2.1 εcu = 0.003,
     * §22.2.2.2 tension in concrete neglected, §22.2.2.4.1 0.85fc' block with a = β1c
     * (β1 per Table 22.2.2.4.3), §20.2.2.1 elastic-plastic steel, §20.2.2.2 Es = 200000 MPa,
     * φ per Table 21.2.2 tied-column transition (εty = fy/Es per 21.2.2.1).
     *
     * Solves φPn(c) = Pu by bisection (φPn is continuous and non-decreasing in c) and
     * returns the section strength at that neutral axis, or null when the section cannot
     * carry Pu even at full compression (φPn,max reached).
     *
     * @param width  section dimension parallel to the neutral axis (b), mm
     * @param depth  section dimension in the bending direction (h), mm
     * @param barOrds (ordinate from the compression face mm, area mm²) per bar layer
     * @param Pu factored axial load, N (compression positive)
     * @param Mu unused in the solve; the caller compares [InteractionCheckResult.phiMn] against it
     */
    fun checkInteraction(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        barOrds: List<Pair<Double, Double>>,
        Pu: Double
    ): InteractionCheckResult? {
        val fc = 0.8 * fcu
        val Es = 200000.0
        val epsTy = fy / Es
        val b1 = beta1(fc)
        val dt = barOrds.maxOf { it.first }
        fun pnMn(c: Double): Triple<Double, Double, Double> {
            val a = min(b1 * c, depth)
            var Pn = 0.85 * fc * a * width
            var Mn = Pn * (depth / 2.0 - a / 2.0)
            for ((y, As) in barOrds) {
                val es = 0.003 * (c - y) / c
                var fs = (Es * es).coerceIn(-fy, fy)
                if (y < a) fs -= 0.85 * fc  // displace concrete under the bar (22.2.1.2/R22.2)
                Pn += fs * As
                Mn += fs * As * (depth / 2.0 - y)
            }
            val epsT = 0.003 * (dt - c) / c
            return Triple(Pn, Mn, phiTied(epsT, epsTy))
        }
        var lo = 1e-3
        var hi = 1e9
        val hiRes = pnMn(hi)
        if (hiRes.third * hiRes.first < Pu) return null
        repeat(200) {
            val mid = (lo + hi) / 2.0
            val r = pnMn(mid)
            if (r.third * r.first < Pu) lo = mid else hi = mid
        }
        val c = (lo + hi) / 2.0
        val (Pn, Mn, phi) = pnMn(c)
        return InteractionCheckResult(c, 0.003 * (dt - c) / c, phi, phi * Pn, phi * Mn)
    }

    /**
     * حساب نسبة النحافة ونسبة التحميل المسموحة
     * ACI 318-22.4: λ = KL/r, Pn = Fcr × Ag
     */
    fun calculateSlendernessEffect(
        unsupportedLength: Double, // m
        effectiveLengthFactor: Double, // K
        width: Double, depth: Double, // mm
        fcu: Double, reinforcementRatio: Double = 0.01
    ): Triple<Double, Boolean, Double> {
        InputGuard.positive("unsupportedLength", unsupportedLength)
        InputGuard.positive("effectiveLengthFactor", effectiveLengthFactor)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("fcu", fcu)
        val Ag = width * depth
        // نصف القطر الدوراني (المحور الأضع)
        val minDimension = min(width, depth)
        val r = minDimension / sqrt(12.0)
        val L_mm = unsupportedLength * 1000.0
        val slendernessRatio = effectiveLengthFactor * L_mm / r
        
        // ACI 318-22.4.2.1: العمود قصير إذا λ ≤ 22 (مربوط) أو λ ≤ 28 (حلزوني)
        val isShort = slendernessRatio <= 22.0
        
        // حساب إجهاد الانبعاج الحرج Fcr
        // ACI 22.4.2.1: EI = 0.4 × Ec × Ig + Es × As × (h/2 - d')^2
        // Ig = b × h^3 / 12 (moment of inertia of gross concrete section)
        val fc_prime = 0.8 * fcu
        val Ig = width * depth.pow(3) / 12.0  // mm^4
        val Ec = 4700.0 * sqrt(fc_prime)      // MPa
        val Es = 200000.0                       // MPa
        val As = reinforcementRatio * Ag
        val d_prime = 40.0  // cover + tie dia (approximate, mm)
        val EI = 0.4 * Ec * Ig + Es * As * ((minDimension / 2.0) - d_prime).pow(2)  // N.mm^2
        val PI2_EI = PI * PI * EI / 1e6  // kN.m^2 (unit conversion)
        val Pc = PI2_EI / (unsupportedLength * unsupportedLength)  // kN
        
        return Triple(slendernessRatio, !isShort, Pc)
    }

    override fun calculateAxialCapacity(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        reinforcementArea: Double,
        loadCombination: LoadCombination
    ): Double {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        
        // ACI 318-22.4.2: Pn = 0.85*fc'*(Ag-Ast) + fy*Ast
        // fc' = 0.8 × fcu (cube to cylinder conversion)
        val fc_prime = 0.8 * fcu
        val concreteCapacity = 0.85 * fc_prime * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        
        // ACI 318-25 §22.4.2.2: Pn,max = 0.80*Po for tied, 0.85*Po for spiral (حد اللامركزية الدنيا)
        // ثم φPn,max = φ * Pn,max
        val alpha = 0.80 // tied column limit
        val phi = PHI_TIED
        return phi * alpha * nominalCapacity / 1000.0 // kN
    }
    
    /**
     * حساب القدرة المحورية مع اختيار نوع العمود (مربوط/حلزوني)
     * ACI 318-21.2.1: φ=0.65 (tied) أو φ=0.75 (spiral)
     */
    fun calculateAxialCapacityWithPhi(
        fcu: Double, fy: Double, width: Double, depth: Double,
        reinforcementArea: Double, isSpiral: Boolean
    ): Double {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        val Ag = width * depth
        val Ast = reinforcementArea.coerceAtMost(Ag * 0.08)
        val fc_prime = 0.8 * fcu
        val concreteCapacity = 0.85 * fc_prime * (Ag - Ast)
        val steelCapacity = fy * Ast
        val nominalCapacity = concreteCapacity + steelCapacity
        val phi = if (isSpiral) PHI_SPIRAL else PHI_TIED
        val alpha = if (isSpiral) 0.85 else 0.80 // ACI 22.4.2.2
        return phi * alpha * nominalCapacity / 1000.0
    }
    

    /** Clips the centered rectangle by the compression-zone half-plane (a from the extreme fiber, normal n). Returns the polygon and the extreme-fiber distance tmax. */
    private fun clipRectHalfPlane(
        width: Double, depth: Double, nx: Double, ny: Double, a: Double
    ): Pair<List<Pair<Double, Double>>, Double> {
        val pts = listOf(
            -width / 2 to -depth / 2, width / 2 to -depth / 2,
            width / 2 to depth / 2, -width / 2 to depth / 2
        )
        val tmax = pts.maxOf { it.first * nx + it.second * ny }
        val lim = tmax - a
        fun f(p: Pair<Double, Double>) = p.first * nx + p.second * ny - lim
        val out = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until 4) {
            val cur = pts[i]
            val prv = pts[(i + 3) % 4]
            val fCur = f(cur)
            val fPrv = f(prv)
            if (fCur >= 0) {
                if (fPrv < 0) {
                    val t = fPrv / (fPrv - fCur)
                    out += (prv.first + t * (cur.first - prv.first)) to (prv.second + t * (cur.second - prv.second))
                }
                out += cur
            } else if (fPrv >= 0) {
                val t = fPrv / (fPrv - fCur)
                out += (prv.first + t * (cur.first - prv.first)) to (prv.second + t * (cur.second - prv.second))
            }
        }
        return out to tmax
    }

    private fun polyAreaCentroid(poly: List<Pair<Double, Double>>): Pair<Double, Pair<Double, Double>> {
        var A = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in poly.indices) {
            val (x0, y0) = poly[i]
            val (x1, y1) = poly[(i + 1) % poly.size]
            val cr = x0 * y1 - x1 * y0
            A += cr
            cx += (x0 + x1) * cr
            cy += (y0 + y1) * cr
        }
        A *= 0.5
        if (abs(A) < 1e-12) return 0.0 to (0.0 to 0.0)
        return abs(A) to (cx / (6 * A) to cy / (6 * A))
    }

    /**
     * معامل تكبير العزم بالطريقة الصحيحة من الكود — ACI 318M-14 §6.2.5 + §6.6.4.4/§6.6.4.5
     * (F-09 fix; owner-approved 2026-11-21).
     *
     * Verified printed pages: 6.2.5 + 6.2.5.1 (printed pp.103-104),
     * 6.6.4.4.2/4.4.3/4.4.4 (printed pp.122-123),
     * 6.6.4.5.1/5.2/5.3 (printed pp.124-125) — CLAUSE_LOG A-17.
     *
     * Sign convention (6.2.5): M1/M2 is NEGATIVE in single curvature,
     * POSITIVE in double curvature.
     *
     * NONSWAY frames only (6.6.4.5). Sway frames: calculateSwayMagnification (6.6.4.6).
     *
     * 6.6.4.5.4 minimum moment is enforced here: when M2,min = Pu(15+0.03h) mm
     * exceeds M2, M2 is replaced by M2,min and Cm is taken 1.0 (printed pp.125-126;
     * metric form 15+0.03h — the bilingual printing shows the US-inch 0.6+0.03h
     * form, see CLAUSE_LOG F-09/A-18).
     *
     * @return Pair(δ, M2_used): δ >= 1.0 and the moment to magnify (M2 or M2,min);
     *         design moment Mc = δ × M2_used (6.6.4.5.1).
     */
    fun calculateMomentMagnification(
        Pu: Double,                      // kN, factored axial load
        M1: Double,                      // kNm, smaller end moment, signed
        M2: Double,                      // kNm, larger end moment, positive
        kLu: Double,                     // mm, effective length k×ℓu
        width: Double,                   // mm, perpendicular to bending
        depth: Double,                   // mm, in bending direction
        fcu: Double,
        betaDns: Double = 0.0            // 6.6.4.4.4 sustained-axial ratio (0.6 → simplified 0.25EcIg)
    ): Pair<Double, Double> {
        InputGuard.positive("Pu", Pu)
        InputGuard.positive("M2", M2)
        InputGuard.positive("kLu", kLu)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("fcu", fcu)
        require(M2 >= abs(M1)) { "M2 must be the larger end moment in absolute value" }
        require(betaDns in 0.0..1.0) { "betaDns must be in 0..1" }

        // 6.2.5.1(b): r = 0.30 × dimension in the direction stability is considered
        val r = 0.30 * depth
        val kluOverR = kLu / r
        val mRatio = M1 / M2
        // 6.2.5(b): nonsway (braced) neglect limit — 6.2.5b AND 6.2.5c (cap 40).
        // Sway frames are NOT this method: use calculateSwayMagnification (6.6.4.6).
        val mayNeglect = kluOverR <= (34.0 + 12.0 * mRatio).coerceAtMost(40.0)
        // 6.6.4.5.4: minimum design moment M2,min = Pu(15 + 0.03h) mm → kNm
        val m2Min = Pu * (15.0 + 0.03 * depth) / 1000.0
        val m2Used = max(M2, m2Min)
        if (mayNeglect) return Pair(1.0, m2Used)

        // 6.6.4.4.4(a): EI = 0.4EcIg / (1+βdns)
        val fcPrime = 0.8 * fcu
        val ec = 4700.0 * sqrt(fcPrime)
        val ig = width * depth.pow(3) / 12.0
        val ei = 0.4 * ec * ig / (1.0 + betaDns)
        // 6.6.4.4.2: Pc = π²EI / (kℓu)²
        val pc = PI * PI * ei / (kLu * kLu) / 1000.0  // N → kN
        // 6.6.4.5.3(a): Cm = 0.6 − 0.4(M1/M2); 6.6.4.5.4: Cm = 1.0 when M2,min governs
        val cm = if (m2Min > M2) 1.0 else 0.6 - 0.4 * mRatio
        // 6.6.4.5.2: δ = Cm / (1 − Pu/(0.75Pc)) ≥ 1.0
        val delta = cm / (1.0 - Pu / (0.75 * pc))
        return Pair(max(1.0, delta), m2Used)
    }

    /**
     * تكبير العزوم للإطارات غير المقيدة — ACI 318M-14 §6.6.4.6 (sway frames).
     * Verified printed pages 125-126 (CLAUSE_LOG A-17).
     *
     * 6.6.4.6.1: M = Mns + δs·Ms (sway moments amplified, nonsway are not).
     * 6.6.4.6.2: δs = 1/(1−Q) ≥ 1 (a) when [stabilityIndexQ] is given, else
     * δs = 1/(1 − ΣPu/(0.75ΣPc)) ≥ 1 (b) from the story sums. Method (a) is
     * only permitted while δs ≤ 1.5; method (b) has no such limit.
     *
     * @return Pair(δs, magnified moment M = Mns + δs·Ms)
     */
    fun calculateSwayMagnification(
        Mns: Double,                     // kNm, first-order NONSWAY moment
        Ms: Double,                      // kNm, first-order SWAY moment
        sumPu: Double,                   // kN, story ΣPu (needed for 6.6.4.6.2b)
        sumPc: Double,                   // kN, story ΣPc (needed for 6.6.4.6.2b)
        stabilityIndexQ: Double? = null  // 6.6.4.6.2a; when null, method (b) is used
    ): Pair<Double, Double> {
        InputGuard.positive("sumPu", sumPu)
        InputGuard.positive("sumPc", sumPc)
        val deltaS = if (stabilityIndexQ != null) {
            require(stabilityIndexQ < 1.0) { "Q must be < 1.0 (unstable otherwise)" }
            val d = 1.0 / (1.0 - stabilityIndexQ)
            require(d <= 1.5) {
                "6.6.4.6.2: Q-method only permitted while δs ≤ 1.5 (δs=$d); use the ΣPc method (stabilityIndexQ = null) or second-order analysis"
            }
            d
        } else {
            1.0 / (1.0 - sumPu / (0.75 * sumPc))
        }
        val ds = max(1.0, deltaS)
        return Pair(ds, Mns + ds * Ms)
     * φPn, φMnx, φMny at neutral-axis orientation [theta] (normal from the centroid toward
     * the maximum-compression fiber) and neutral-axis depth [c], per ACI 318M-14
     * §22.2.1/§22.2.2/§22.2.4.1-2 and Table 21.2.2. Public for golden-vector verification
     * against closed-form benchmarks.
     */
    fun contourPointAt(
        fcu: Double, fy: Double, width: Double, depth: Double,
        bars: List<Triple<Double, Double, Double>>, theta: Double, c: Double
    ): Triple<Double, Double, Double> {
        val fc = 0.8 * fcu
        val nx = cos(theta)
        val ny = sin(theta)
        val a = beta1(fc) * c
        val (poly, tmax) = clipRectHalfPlane(width, depth, nx, ny, min(a, 2.0 * max(width, depth)))
        val (Ac, centroid) = if (poly.isEmpty()) 0.0 to (0.0 to 0.0) else polyAreaCentroid(poly)
        val Cc = 0.85 * fc * Ac
        var Pn = Cc
        var Mnx = Cc * centroid.second
        var Mny = Cc * centroid.first
        var dt = 0.0
        for ((x, y, As) in bars) {
            val sBar = tmax - (x * nx + y * ny)
            dt = max(dt, sBar)
            val es = 0.003 * (c - sBar) / c
            var fs = (200000.0 * es).coerceIn(-fy, fy)
            if (sBar < a) fs -= 0.85 * fc
            val F = fs * As
            Pn += F
            Mnx += F * y
            Mny += F * x
        }
        val epsT = 0.003 * (dt - c) / c
        val phi = phiTied(epsT, fy / 200000.0)
        return Triple(phi * Pn, phi * Mnx, phi * Mny)
    }

    /**
     * Biaxial axial-moment interaction check per ACI 318M-14 §22.2.1.1 (equilibrium at
     * each section), §22.2.4.1 (0.85fc' over the compression zone bounded by the section
     * edges and a line parallel to the neutral axis at a = β1c) and §22.2.4.2 (c measured
     * perpendicular to the neutral axis). Finds the neutral-axis orientation whose moment
     * vector direction matches the demand direction ([steps]-direction scan at 1 degree,
     * each bracketing interval bisected), solves φPn(c) = Pu
     * there by bisection, and passes when the contour magnitude at the demand direction
     * covers |Mu|. No polygon interpolation is used, so no convexity assumption is made;
     * when several orientations match the demand direction the LOWEST capacity governs
     * (conservative), and a demand exactly on the boundary counts as pass only if
     * |Mu| <= capacity exactly (ties fail closed on floating-point error).
     *
     * Convergence: c-bisection over [1e-3, 1e12] mm with 60 iterations (|Δc| < 1e-6 mm);
     * direction scan 1 degree + 60 bisection iterations (direction error < 1e-15 rad).
     * Verified against a closed-form benchmark in ACIColumnInteractionTest.
     *
     * @param bars bars in CENTROID coordinates: (x mm, y mm, area mm²)
     */
    fun checkBiaxial(
        fcu: Double, fy: Double, width: Double, depth: Double,
        bars: List<Triple<Double, Double, Double>>,
        Pu: Double, Mux: Double, Muy: Double,
        steps: Int = 360
    ): Boolean {
        require(Mux > 0.0 || Muy > 0.0) { "checkBiaxial needs a nonzero moment demand" }
        val alpha = atan2(Muy, Mux)
        val demandMag = hypot(Mux, Muy)
        fun solveC(theta: Double): Double? {
            var lo = 1e-3
            var hi = 1e12
            if (contourPointAt(fcu, fy, width, depth, bars, theta, hi).first < Pu) return null
            repeat(60) {
                val mid = (lo + hi) / 2.0
                if (contourPointAt(fcu, fy, width, depth, bars, theta, mid).first < Pu) lo = mid else hi = mid
            }
            return (lo + hi) / 2.0
        }
        fun delta(theta: Double): Double? {
            val c = solveC(theta) ?: return null
            val p = contourPointAt(fcu, fy, width, depth, bars, theta, c)
            var d = atan2(p.third, p.second) - alpha
            while (d > PI) d -= 2 * PI
            while (d <= -PI) d += 2 * PI
            return d
        }
        var minCap = Double.POSITIVE_INFINITY
        var prevTheta = 0.0
        var prev = delta(0.0) ?: return false
        for (k in 1..steps) {
            val theta = 2 * PI * k / steps
            val cur = delta(theta) ?: return false
            if (prev * cur < 0.0) {
                var lo = prevTheta
                var hi = theta
                repeat(60) {
                    val mid = (lo + hi) / 2.0
                    val d = delta(mid) ?: return false
                    if (prev * d < 0.0) hi = mid else lo = mid
                }
                val th = (lo + hi) / 2.0
                val c = solveC(th) ?: return false
                val p = contourPointAt(fcu, fy, width, depth, bars, th, c)
                minCap = min(minCap, hypot(p.second, p.third))
            }
            prev = cur
            prevTheta = theta
        }
        if (minCap == Double.POSITIVE_INFINITY) return false
        return demandMag <= minCap
    }

    override fun calculateReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        val Ag = width * depth
        val Pu = axialLoad * 1000.0  // N - الحمل المحوري التصميمي (مضروب في معامل التحميل بالفعل)
        
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ACI 318-25: Simplified reinforcement calculation
        // fc' = 0.8 × fcu (cube to cylinder)
        val fc_prime = 0.8 * fcu
        val phi = 0.65
        val alpha = 0.80
        var requiredSteelArea = (Pu / (phi * alpha) - 0.85 * fc_prime * Ag) / (fy - 0.85 * fc_prime)
        
        
        // Reinforcement limits per ACI
        val minSteel = getMinReinforcementRatio() * Ag
        val maxSteel = getMaxReinforcementRatio() * Ag
        
        var astRequired = requiredSteelArea.coerceIn(minSteel, maxSteel)
        
        if (requiredSteelArea < minSteel) {
            warnings.add("Minimum reinforcement (1%) applied per ACI 318-10.6.1")
        }
        
        // Selection of bar diameters مع بدائل اقتصادية وآمنة
        val availableBars = listOf(14.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0) // No.4 to No.10
        var selectedBarDia = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 12
        } ?: 19.0
        
        // حساب البدائل
        val alternatives = mutableListOf<String>()
        for (dia in availableBars) {
            val area = PI * dia * dia / 4
            val numBars = ceil(astRequired / area).toInt().coerceIn(4, 16)
            val asProv = numBars * area
            val cap = calculateAxialCapacity(fcu, fy, width, depth, asProv, loadCombination)
            val util = if (cap > 0) axialLoad / cap else 2.0
            if (util in 0.5..1.0 && dia != selectedBarDia) {
                alternatives.add("${numBars}Ø${dia.toInt()} (${(util*100).toInt()}%)")
            }
        }
        if (alternatives.size >= 2) {
            codeNotes.add("Economical: ${alternatives.first()}")
            codeNotes.add("Safest: ${alternatives.last()}")
        }
        
        // F-08: strain-compatibility interaction per ACI 318M-14 §22.2 + Table 21.2.2
        // replaces the removed invented 1+2e/h moment-amplification heuristic.
        // Axial cap per Table 22.4.2.1 (0.80φPo for tied); φ from the actual strain state.
        // Biaxial demands checked on the full φM contour (§22.2.1.1/§22.2.4.1/§22.2.4.2),
        // bar clear spacing per §25.2.3 (max(40, 1.5db); the (4/3)dagg term needs the
        // aggregate size, which the inputs do not carry). If the selected diameter cannot
        // satisfy both, escalate to larger diameters before declaring the section inadequate.
        val Mux = abs(momentX) * 1e6  // N.mm, bending in the depth direction
        val Muy = abs(momentY) * 1e6  // N.mm, bending in the width direction
        val diameters = availableBars.dropWhile { it < selectedBarDia }
        var barDiameter = selectedBarDia
        var numberOfBars = 4
        var interactionOk = false
        var dPrime = 0.0
        var barArea = 0.0
        outer@ for (dia in diameters) {
            barArea = PI * dia * dia / 4
            val tiesD = if (dia <= 32.0) 10.0 else 12.0
            dPrime = getMinCover() + tiesD + dia / 2.0
            var n = ceil(astRequired / barArea).toInt().coerceIn(4, 24)
            if (n % 2 == 1) n += 1
            while (n <= 24) {
                val astTrial = n * barArea
                val po = 0.85 * fc_prime * (Ag - astTrial) + fy * astTrial
                if (Pu > 0.80 * PHI_TIED * po) { n += 2; continue }
                val layout = layoutPerimeterBars(n, width, depth, dPrime)
                val extra = n - 4
                val perFace = extra / 4
                val rem = (extra % 4) / 2
                val spacingX = (width - 2 * dPrime) / (perFace + rem + 1)
                val spacingY = (depth - 2 * dPrime) / (perFace + 1)
                val spacingOk = min(spacingX, spacingY) - dia >= max(40.0, 1.5 * dia)
                val barsCentered = layout.map { Triple(it.first - width / 2.0, it.second - depth / 2.0, barArea) }
                val contourOk = (Mux <= 0.0 && Muy <= 0.0) ||
                    checkBiaxial(fcu, fy, width, depth, barsCentered, Pu, Mux, Muy)
                if (contourOk && spacingOk) {
                    barDiameter = dia
                    numberOfBars = n
                    interactionOk = true
                    break@outer
                }
                n += 2
            }
            numberOfBars = n
        }
        if (!interactionOk) {
            barArea = PI * barDiameter * barDiameter / 4
            warnings.add("Section inadequate for the axial-moment combination per ACI 318M-14 22.2 interaction and 25.2.3 spacing - increase section dimensions")
            numberOfBars = numberOfBars.coerceAtMost(24)
        } else if (Mux > 0.0 || Muy > 0.0) {
            codeNotes.add("Axial-moment interaction checked by strain compatibility incl. biaxial contour (ACI 318M-14 22.2.1.1/22.2.4.1/22.2.4.2, Table 21.2.2)")
        }
        val astProvided = numberOfBars * barArea
        codeNotes.add("Bar clear spacing checked vs max(40mm, 1.5db) per ACI 318M-14 25.2.3; the (4/3)dagg term is UNVERIFIED - the app has no aggregate-size input")

        // Ties per ACI 318M-14 §25.7.2.2 (Ø10 for longitudinal bars ≤ Ø32)
        val tiesDiameter = if (barDiameter <= 32.0) 10.0 else 12.0
        val tiesSpacing = minOf(
            16 * barDiameter,           // 16 x longitudinal bar diameter
            48 * tiesDiameter,          // 48 x tie bar diameter
            width, depth,               // smallest dimension of member
            300.0                        // 300 mm max
        ).coerceIn(getMinSpacing(), getMaxSpacing())
        
        // Safety check
        val capacity = calculateAxialCapacity(fcu, fy, width, depth, astProvided, loadCombination)
        val utilizationRatio = axialLoad / capacity
        
        codeNotes.add(CodeReference.ACI.COLUMN_AXIAL)
        codeNotes.add(CodeReference.ACI.COLUMN_REINFORCEMENT_MIN)
        codeNotes.add(CodeReference.ACI.COLUMN_REINFORCEMENT_MAX)
        codeNotes.add(CodeReference.ACI.COLUMN_COVER)
        codeNotes.add("phi = 0.65 for tied columns")
        codeNotes.add("Min cover: ${getMinCover()}mm for cast-in-place")
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = tiesDiameter,
            tiesSpacing = tiesSpacing,
            isSafe = utilizationRatio <= 1.0 && interactionOk,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun getMinReinforcementRatio(): Double = 0.01   // 1% ACI 10.6.1
    override fun getMaxReinforcementRatio(): Double = 0.08   // 8% ACI 10.6.1
    override fun getMinSpacing(): Double = 40.0
    override fun getMaxSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0

    // ── Shear Design per ACI 318-25 §22.5 ─────────────────────────────────────

    /**
     * Column shear reinforcement design — ACI 318-25 §22.5
     * @param Vu     factored shear force (kN)
     * @param width  column width b (mm)
     * @param depth  column depth h (mm)
     * @param fcu    concrete cube strength (MPa);  f'c = 0.8 × fcu
     * @param fy     steel yield strength (MPa)
     * @param cover  concrete cover (mm), default 40
     */
    fun calculateShearDesign(
        Vu: Double,
        width: Double,
        depth: Double,
        fcu: Double,
        fy: Double,
        cover: Double = 40.0,
        longitudinalBarDia: Double = 20.0
    ): ColumnShearDesignResult {
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("cover", cover)
        val b = width
        val d = depth - cover
        val fc = 0.8 * fcu  // cylinder strength
        val phi = 0.75
        val codeNotes = mutableListOf<String>()

        // Vc = 0.17 × √(f'c) × b × d   (ACI 318-22.5.5.1)
        val Vc = 0.17 * sqrt(fc) * b * d / 1000.0  // kN
        val phiVc = phi * Vc

        val needsStirrups = Vu > phiVc

        // Asv/s = (Vu - φVc) / (φ × fy × d)
        val requiredAsvPerS = if (needsStirrups) {
            (Vu - phiVc) * 1000.0 / (phi * fy * d)  // mm²/mm
        } else 0.0

        // Max spacing per ACI 318-25 §25.7.2.2: min(b/4, 6×db, 150mm)
        val maxSpacing = minOf(min(width, depth) / 4.0, 6.0 * longitudinalBarDia, 150.0)

        // Min Asv per ACI 22.5.10.1: Av,min = 0.062√f'c × b×s / fy  (but not less than 0.35×b×s/fy)
        // As Av/s:  minAvPerS = max(0.062√f'c × b / fy,  0.35 × b / fy)
        val minAsvPerS = max(0.062 * sqrt(fc) * b / fy, 0.35 * b / fy)

        val designAsvPerS = max(requiredAsvPerS, if (needsStirrups) minAsvPerS else 0.0)

        // Select stirrup diameter and spacing
        val availableTies = listOf(9.5, 12.7, 15.9, 19.1)  // #3, #4, #5, #6
        var selectedDia = 9.5
        var selectedSpacing = maxSpacing

        if (designAsvPerS > 0) {
            for (dia in availableTies) {
                val asv = 2.0 * PI * dia * dia / 4.0
                val spacing = asv / designAsvPerS
                if (spacing <= maxSpacing && spacing >= getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = min(spacing, maxSpacing)
                    break
                }
                if (spacing < getMinSpacing()) {
                    selectedDia = dia
                    selectedSpacing = getMinSpacing()
                }
            }
        }

        val providedAsvPerS = if (designAsvPerS > 0) {
            2.0 * PI * selectedDia * selectedDia / 4.0 / selectedSpacing
        } else 0.0

        val totalCapacity = phiVc + if (needsStirrups) phi * fy * providedAsvPerS * d / 1000.0 else 0.0
        val utilizationRatio = if (totalCapacity > 0) Vu / totalCapacity else 2.0

        codeNotes.add("ACI 318-25 §22.5: Column Shear Design")
        codeNotes.add("f'c = 0.8×fcu = ${String.format("%.0f", fc)} MPa")
        codeNotes.add("Vc = 0.17√f'c·b·d = ${String.format("%.1f", Vc)} kN")
        codeNotes.add(CodeReference.ACI.COLUMN_TIES)
        codeNotes.add("φVc = ${String.format("%.1f", phiVc)} kN  (φ=$phi)")
        if (needsStirrups) {
            codeNotes.add("Vu (${String.format("%.1f", Vu)} kN) > φVc → Stirrups required")
            codeNotes.add("Asv/s = ${String.format("%.3f", designAsvPerS)} mm²/mm")
            codeNotes.add(String.format("%.1fmm ties @ %.0fmm c/c", selectedDia, selectedSpacing))
        } else {
            codeNotes.add("Vu (${String.format("%.1f", Vu)} kN) ≤ φVc → Concrete alone sufficient")
        }

        return ColumnShearDesignResult(
            Vu = Vu,
            Vc = Vc,
            phiVc = phiVc,
            asvPerS = requiredAsvPerS,
            minAsvPerS = minAsvPerS,
            designAsvPerS = designAsvPerS,
            stirrupDiameter = selectedDia,
            stirrupSpacing = selectedSpacing,
            providedAsvPerS = providedAsvPerS,
            maxSpacing = maxSpacing,
            needsStirrups = needsStirrups,
            isSafe = Vu <= totalCapacity,
            utilizationRatio = utilizationRatio,
            codeNotes = codeNotes
        )
    }
}
