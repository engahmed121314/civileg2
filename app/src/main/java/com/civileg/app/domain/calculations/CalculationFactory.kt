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

    fun getHordiSlabDesign(code: DesignCode): ECPHordiSlabDesign = ECPHordiSlabDesign()

    fun getWaffleSlabDesign(code: DesignCode): ECPWaffleSlabDesign = ECPWaffleSlabDesign()

    // ========== كمرات مزدوجة التسليح (Doubly Reinforced Beams) ==========

    fun getDoublyReinforcedBeamDesign(code: DesignCode): ECPDoublyReinforcedBeam = ECPDoublyReinforcedBeam()

    // ========== القواعد المركبة (Combined Footings) ==========

    fun getCombinedFootingDesign(code: DesignCode): ECPCombinedFooting = ECPCombinedFooting()

    // ========== البلاطات المتقدمة (Advanced Slab Design) ==========

    fun getAdvancedSlabDesign(code: DesignCode) = when (code) {
        DesignCode.ECP -> ECPAdvancedSlab()
        DesignCode.ACI -> ACIAdvancedSlab()
        DesignCode.SBC -> SBCAdvancedSlab()
    }
}
