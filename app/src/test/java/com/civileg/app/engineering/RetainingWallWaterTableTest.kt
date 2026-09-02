package com.civileg.app.engineering

import com.civileg.app.domain.calculations.aci.ACIRetainingWall
import com.civileg.app.domain.calculations.base.RetainingWallInput
import com.civileg.app.domain.calculations.ecp.ECPRetainingWall
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PHASE 00 STEP 2 — DEFECT A4 regression gate (PHASE00_AUDIT.md Addendum A).
 *
 * Old physics (ECPRetainingWall.kt:43-54, copied into ACI):
 *  - soil below the water table contributed NOTHING (gammaSub computed, never used)
 *  - hydrostatic overturning arm was (hWater/3 + hSoil) -> double-counted the dry layer
 *  - dry-layer triangle acted at H/3 instead of its true centroid H - 2*zw/3
 *
 * Correct layered Rankine model (arms above base):
 *   P_dry  = 0.5*Ka*gamma*zw^2          @ (H - 2zw/3) = (hw + zw/3)
 *   P_rect = Ka*gamma*zw*hw             @ hw/2
 *   P_tri  = 0.5*Ka*gamma'*hw^2         @ hw/3        (gamma' = gamma - 9.81)
 *   P_wat  = 0.5*9.81*hw^2              @ hw/3
 *
 * Case WET (hand-derived): H=5, tBase=.5, tTop=.3, B=3, tF=.5, toe=.7, heel=1.8,
 *   gamma=18, phi=30 (Ka=1/3, Kp=3), q=0, zwt=4.5 -> zw=4.5, hw=0.5, gamma'=8.19
 *   P_dry=60.75 @2.0 ; P_rect=13.5 @0.25 ; P_tri=0.34125 @1/6 ; P_wat=1.22625 @1/6
 *   M_OT = 121.5+3.375+0.0569+0.2044 = 125.136 kN.m/m
 *   W = 50+37.5+145.8 = 233.3 kN ; MR = 93.333+56.25+269.73 = 419.313
 *   OT_FS = 3.3510     (old buggy code gave 3.9199)
 *   Sliding: passive=6.75 ; totalPa=75.8175 -> FS = (0.45*233.3+3.375)/75.8175 = 1.4293
 *                                                     (old buggy code gave 1.7481)
 */
class RetainingWallWaterTableTest {

    private fun input(zwt: Double) = RetainingWallInput(
        wallHeight = 5.0, stemBaseThickness = 0.5, stemTopThickness = 0.3,
        baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.7, heelLength = 1.8,
        soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 0.0,
        waterTableDepth = zwt, fcu = 25.0, fy = 400.0,
        baseFrictionCoeff = 0.45, soilBearingCapacity = 150.0
    )

    @Test
    fun `A4 deep water table overturning and sliding match layered Rankine`() {
        val r = ECPRetainingWall().designRetainingWall(input(zwt = 4.5))
        assertEquals(3.3510, r.overturningFS, 3.3510 * 0.02)
        assertEquals(1.4293, r.slidingFS, 1.4293 * 0.02)
    }

    @Test
    fun `A4 same physics in ACI copy`() {
        val r = ACIRetainingWall().designRetainingWall(input(zwt = 4.5))
        assertEquals(3.3510, r.overturningFS, 3.3510 * 0.02)
        assertEquals(1.4293, r.slidingFS, 1.4293 * 0.02)
    }

    @Test
    fun `A4 dry case reduces to classic triangular Rankine`() {
        // zwt >= H : single dry triangle P=0.5*Ka*gamma*H^2 = 75 kN @ H/3 = 1.6667m
        // M_OT = 125.0 -> OT_FS = 419.313/125.0 = 3.3545
        val r = ECPRetainingWall().designRetainingWall(input(zwt = 50.0))
        assertEquals(3.3545, r.overturningFS, 3.3545 * 0.02)
    }
}
