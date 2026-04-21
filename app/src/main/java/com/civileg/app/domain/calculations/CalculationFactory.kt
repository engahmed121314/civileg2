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
}
