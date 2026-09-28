package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.calculations.ecp.*
import com.civileg.app.domain.calculations.aci.*
import com.civileg.app.domain.calculations.sbc.*
import com.civileg.app.domain.entities.DesignCode

object CalculationFactory {
    
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

    fun getHordiSlabDesign(code: DesignCode): HordiSlabDesign = when (code) {
        DesignCode.ECP -> ECPHordiSlabWrapper()
        DesignCode.ACI -> ACIJoistSlab()
        DesignCode.SBC -> ACIJoistSlab()
    }

    fun getWaffleSlabDesign(code: DesignCode): WaffleSlabDesign = when (code) {
        DesignCode.ECP -> ECPWaffleSlabDesign()
        DesignCode.ACI -> ACIWaffleSlabDesign()
        DesignCode.SBC -> SBCWaffleSlabDesign()
    }

    // ========== كمرات مزدوجة التسليح (Doubly Reinforced Beams) ==========

    fun getDoublyReinforcedBeamDesign(code: DesignCode): Any = when (code) {
        DesignCode.ECP -> ECPDoublyReinforcedBeam()
        DesignCode.ACI -> ACIDoublyReinforcedBeam()
        DesignCode.SBC -> SBCDoublyReinforcedBeam()
    }

    // ========== القواعد المركبة (Combined Footings) ==========

    fun getCombinedFootingDesign(code: DesignCode): ECPCombinedFooting = when (code) {
        DesignCode.ECP -> ECPCombinedFooting()
        DesignCode.ACI, DesignCode.SBC -> ECPCombinedFooting()
    }

    // ========== البلاطات المتقدمة (Advanced Slab Design) ==========

    fun getAdvancedSlabDesign(code: DesignCode) = when (code) {
        DesignCode.ECP -> ECPAdvancedSlab()
        DesignCode.ACI -> ACIAdvancedSlab()
        DesignCode.SBC -> SBCAdvancedSlab()
    }

    fun getFlatSlabDesign(code: DesignCode): FlatSlabDesign = when (code) {
        DesignCode.ECP -> ECPFlatSlab()
        DesignCode.ACI -> ACIFlatSlab()
        DesignCode.SBC -> SBCFlatSlab()
    }

    // ========== حوائط السند (Retaining Walls) ==========

    fun getRetainingWallDesign(code: DesignCode): RetainingWallDesign = when (code) {
        DesignCode.ECP -> ECPRetainingWall()
        DesignCode.ACI -> ACIRetainingWall()
        DesignCode.SBC -> SBCRetainingWall()
    }

    // ========== قواعد الخوازيق (Pile Foundations) ==========

    fun getPileFoundationDesign(code: DesignCode): PileFoundationDesign = when (code) {
        DesignCode.ECP -> ECPPileFoundation()
        DesignCode.ACI -> ACIPileFoundation()
        DesignCode.SBC -> SBCPileFoundation()
    }

    // ========== السلالم (Staircases) ==========

    fun getStaircaseDesign(code: DesignCode): StaircaseDesign = when (code) {
        DesignCode.ECP -> ECPStaircase()
        DesignCode.ACI -> ACIStaircase()
        DesignCode.SBC -> SBCStaircase()
    }

    // ========== القواعد الشداد (Strap Footings) ==========

    fun getStrapFootingDesign(code: DesignCode): StrapFootingDesign = when (code) {
        DesignCode.ECP -> ECPStrapFooting()
        DesignCode.ACI -> ACIStrapFooting()
        DesignCode.SBC -> SBCStrapFooting()
    }
}
