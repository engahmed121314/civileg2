package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.BoundaryElementType
import com.civileg.app.domain.ShearWallInput
import com.civileg.app.domain.ShearWallResult

/**
 * Unified interface for structural shear wall design.
 *
 * Covers:
 *  - Flexural design (wall as column under axial + bending)
 *  - Shear design (Vc + Vs)
 *  - Boundary element requirements (confined end zones)
 *  - Coupling beam design (diagonal or conventional)
 *  - Slenderness check (h/t ratio)
 */
interface ShearWallDesign {

    /**
     * Full shear wall design — main entry point.
     * @param input all design parameters
     * @return complete result including flexure, shear, boundary, coupling, quantities
     */
    fun designWall(input: ShearWallInput): ShearWallResult

    /**
     * Calculate flexural strength: moment capacity (Mn) and axial capacity (Pn).
     * Treats wall as a cantilever column with distributed + concentrated reinforcement.
     *
     * @return Pair<Mn_kN_m, Pn_kN>
     */
    fun calculateFlexuralStrength(input: ShearWallInput): Pair<Double, Double>

    /**
     * Calculate shear strength: Vc + Vs components.
     *
     * @return Pair<Vc_kN, Vs_kN>
     */
    fun calculateShearStrength(input: ShearWallInput): Pair<Double, Double>

    /**
     * Design boundary elements when compression zone is large.
     *
     * @return boundary element type and required reinforcement
     */
    fun designBoundaryElements(input: ShearWallInput): Pair<BoundaryElementType, com.civileg.app.domain.RebarResult?>

    /**
     * Design coupling beam for coupled shear walls.
     */
    fun designCouplingBeam(input: ShearWallInput): com.civileg.app.domain.CouplingBeamResult?

    /**
     * Check slenderness: total wall height / thickness ratio.
     * @return Pair<isOk, ratio>
     */
    fun checkSlenderness(input: ShearWallInput): Pair<Boolean, Double>
}
