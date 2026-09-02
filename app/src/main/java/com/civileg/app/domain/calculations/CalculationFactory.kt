package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.calculations.base.BeamDesign
import com.civileg.app.domain.calculations.base.SlabDesign
import com.civileg.app.domain.calculations.base.TankDesign
import com.civileg.app.domain.calculations.base.FootingDesign
import com.civileg.app.domain.calculations.ecp.ECPColumn
import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.sbc.SBCColumn
import com.civileg.app.domain.calculations.aci.ACIBeam
import com.civileg.app.domain.calculations.sbc.SBCBeam
import com.civileg.app.domain.calculations.ecp.ECPBeam
import com.civileg.app.domain.calculations.ecp.ECPSlab
import com.civileg.app.domain.calculations.aci.ACISlab
import com.civileg.app.domain.calculations.sbc.SBCSlab
import com.civileg.app.domain.calculations.ecp.ECPTank
import com.civileg.app.domain.calculations.aci.ACITank
import com.civileg.app.domain.calculations.sbc.SBCTank
import com.civileg.app.domain.calculations.ecp.ECPFooting
import com.civileg.app.domain.calculations.aci.ACIFooting
import com.civileg.app.domain.calculations.sbc.SBCFooting
import com.civileg.app.domain.calculations.ecp.ECPAdvancedColumn
import com.civileg.app.domain.calculations.aci.ACIAdvancedColumn
import com.civileg.app.domain.calculations.sbc.SBCAdvancedColumn
import com.civileg.app.domain.calculations.ecp.ECPAdvancedBeam
import com.civileg.app.domain.calculations.aci.ACIAdvancedBeam
import com.civileg.app.domain.calculations.sbc.SBCAdvancedBeam
import com.civileg.app.domain.calculations.ecp.SteelDesignEngine
import com.civileg.app.domain.calculations.aci.AISCSteelDesignEngine
import com.civileg.app.domain.calculations.sbc.SBCSteelDesignEngine
import com.civileg.app.domain.calculations.ecp.ECPAdvancedSlab
import com.civileg.app.domain.calculations.aci.ACIAdvancedSlab
import com.civileg.app.domain.calculations.sbc.SBCAdvancedSlab
import com.civileg.app.domain.calculations.ecp.ECPDoublyReinforcedBeam
import com.civileg.app.domain.calculations.ecp.ECPCombinedFooting
import com.civileg.app.domain.calculations.ecp.ECPHordiSlabDesign
import com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign
import com.civileg.app.domain.calculations.ecp.ECPRetainingWall
import com.civileg.app.domain.calculations.aci.ACIRetainingWall
import com.civileg.app.domain.calculations.sbc.SBCRetainingWall
import com.civileg.app.domain.calculations.base.RetainingWallDesign
import com.civileg.app.domain.calculations.ecp.ECPStaircase
import com.civileg.app.domain.calculations.aci.ACIStaircase
import com.civileg.app.domain.calculations.sbc.SBCStaircase
import com.civileg.app.domain.calculations.base.StaircaseDesign
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import com.civileg.app.domain.calculations.ecp.ECPFlatSlab
import com.civileg.app.domain.calculations.aci.ACIFlatSlab
import com.civileg.app.domain.calculations.sbc.SBCFlatSlab
import com.civileg.app.domain.calculations.base.ShearWallDesign
import com.civileg.app.domain.calculations.ecp.ECPShearWall
import com.civileg.app.domain.calculations.aci.ACIShearWall
import com.civileg.app.domain.calculations.sbc.SBCShearWall
import com.civileg.app.domain.calculations.base.PileFoundationDesign
import com.civileg.app.domain.calculations.base.SeismicDesign
import com.civileg.app.domain.calculations.ecp.ECPPileFoundation
import com.civileg.app.domain.calculations.aci.ACIPileFoundation
import com.civileg.app.domain.calculations.aci.ACISeismic
import com.civileg.app.domain.calculations.ecp.ECPSeismic
import com.civileg.app.domain.calculations.sbc.SBCPileFoundation
import com.civileg.app.domain.calculations.sbc.SBCSeismic
import com.civileg.core.calculations.entities.DesignCode

object CalculationFactory {

    /**
     * Parse a user-facing code string ("ECP"/"ACI"/"SBC", case-insensitive).
     * Returns null for blank/unrecognized input so the caller can fall back
     * to the settings default (ADR-003) instead of silently assuming ECP.
     */
    fun parseDesignCode(raw: String?): DesignCode? =
        raw?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { s -> DesignCode.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } }

    
    fun getColumnDesign(code: DesignCode): ColumnDesign = when (code) {
        DesignCode.ECP -> ECPColumn()
        DesignCode.ACI -> ACIColumn()
        DesignCode.SBC -> SBCColumn()
    }
    
    fun getBeamDesign(code: DesignCode): BeamDesign = when (code) {
        DesignCode.ECP -> ECPBeam()
        DesignCode.ACI -> ACIBeam()
        DesignCode.SBC -> SBCBeam()
    }

    fun getSlabDesign(code: DesignCode): SlabDesign = when (code) {
        DesignCode.ECP -> ECPSlab()
        DesignCode.ACI -> ACISlab()
        DesignCode.SBC -> SBCSlab()
    }

    fun getTankDesign(code: DesignCode): TankDesign = when (code) {
        DesignCode.ECP -> ECPTank()
        DesignCode.ACI -> ACITank()
        DesignCode.SBC -> SBCTank()
    }

    fun getFootingDesign(code: DesignCode): FootingDesign = when (code) {
        DesignCode.ECP -> ECPFooting()
        DesignCode.ACI -> ACIFooting()
        DesignCode.SBC -> SBCFooting()
    }

    // ========== التصميم المتقدم (Advanced Design) ==========

    fun getAdvancedColumnDesign(code: DesignCode) = when (code) {
        DesignCode.ECP -> ECPAdvancedColumn()
        DesignCode.ACI -> ACIAdvancedColumn()
        DesignCode.SBC -> SBCAdvancedColumn()
    }

    fun getAdvancedBeamDesign(code: DesignCode) = when (code) {
        DesignCode.ECP -> ECPAdvancedBeam()
        DesignCode.ACI -> ACIAdvancedBeam()
        DesignCode.SBC -> SBCAdvancedBeam()
    }

    // ========== المنشآت المعدنية (Steel Structures) ==========

    fun getSteelDesignEngine(code: DesignCode) = when (code) {
        DesignCode.ECP -> SteelDesignEngine()
        DesignCode.ACI -> AISCSteelDesignEngine()
        DesignCode.SBC -> SBCSteelDesignEngine()
    }

    // ========== البلاطات المتخصصة (Specialized Slabs) ==========

    fun getHordiSlabDesign(code: DesignCode): ECPHordiSlabDesign = when (code) {
        DesignCode.ECP -> ECPHordiSlabDesign()
        // Fallback: No ACI/SBC-specific hordi slab implementation exists yet; using ECP as default
        DesignCode.ACI, DesignCode.SBC -> ECPHordiSlabDesign()
    }

    fun getWaffleSlabDesign(code: DesignCode): ECPWaffleSlabDesign = when (code) {
        DesignCode.ECP -> ECPWaffleSlabDesign()
        // Fallback: No ACI/SBC-specific waffle slab implementation exists yet; using ECP as default
        DesignCode.ACI, DesignCode.SBC -> ECPWaffleSlabDesign()
    }

    // ========== كمرات مزدوجة التسليح (Doubly Reinforced Beams) ==========

    fun getDoublyReinforcedBeamDesign(code: DesignCode): ECPDoublyReinforcedBeam = when (code) {
        DesignCode.ECP -> ECPDoublyReinforcedBeam()
        // Fallback: No ACI/SBC-specific doubly reinforced beam implementation exists yet; using ECP as default
        DesignCode.ACI, DesignCode.SBC -> ECPDoublyReinforcedBeam()
    }

    // ========== القواعد المركبة (Combined Footings) ==========

    fun getCombinedFootingDesign(code: DesignCode): ECPCombinedFooting = when (code) {
        DesignCode.ECP -> ECPCombinedFooting()
        // Fallback: No ACI/SBC-specific combined footing implementation exists yet; using ECP as default
        DesignCode.ACI, DesignCode.SBC -> ECPCombinedFooting()
    }

    // ========== البلاطات المتقدمة (Advanced Slab Design) ==========

    fun getAdvancedSlabDesign(code: DesignCode) = when (code) {
        DesignCode.ECP -> ECPAdvancedSlab()
        DesignCode.ACI -> ACIAdvancedSlab()
        DesignCode.SBC -> SBCAdvancedSlab()
    }

    // ========== حوائط السند (Retaining Walls) ==========

    fun getRetainingWallDesign(code: DesignCode): RetainingWallDesign = when (code) {
        DesignCode.ECP -> ECPRetainingWall()
        DesignCode.ACI -> ACIRetainingWall()
        DesignCode.SBC -> SBCRetainingWall()
    }

    // ========== السلالم (Staircases) ==========

    fun getStaircaseDesign(code: DesignCode): StaircaseDesign = when (code) {
        DesignCode.ECP -> ECPStaircase()
        DesignCode.ACI -> ACIStaircase()
        DesignCode.SBC -> SBCStaircase()
    }

    // ========== البلاطات المسطحة (Flat Slabs / DDM + Punching) ==========

    fun getFlatSlabDesign(code: DesignCode): FlatSlabDesign = when (code) {
        DesignCode.ECP -> ECPFlatSlab()
        DesignCode.ACI -> ACIFlatSlab()
        DesignCode.SBC -> SBCFlatSlab()
    }

    // ========== حوائط القص (Shear Walls) ==========

    fun getShearWallDesign(code: DesignCode): ShearWallDesign = when (code) {
        DesignCode.ECP -> ECPShearWall()
        DesignCode.ACI -> ACIShearWall()
        DesignCode.SBC -> SBCShearWall()
    }

    // ========== التحليل الزلزالي (Seismic Analysis) ==========

    fun getSeismicAnalysis(code: DesignCode): SeismicDesign = when (code) {
        DesignCode.ECP -> ECPSeismic()
        DesignCode.ACI -> ACISeismic()
        DesignCode.SBC -> SBCSeismic()
    }

    // ========== الأساسات العميقة (Pile Foundations) ==========

    fun getPileFoundationDesign(code: DesignCode): PileFoundationDesign = when (code) {
        DesignCode.ECP -> ECPPileFoundation()
        DesignCode.ACI -> ACIPileFoundation()
        DesignCode.SBC -> SBCPileFoundation()
    }
}
