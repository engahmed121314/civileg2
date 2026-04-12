package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.calculations.base.BeamDesign
import com.civileg.app.domain.calculations.base.SlabDesign
import com.civileg.app.domain.calculations.base.TankDesign
import com.civileg.app.domain.calculations.ecp.ECPColumn
import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.sbc.SBCColumn
import com.civileg.app.domain.calculations.ecp.ECPBeam
import com.civileg.app.domain.calculations.ecp.ECPSlab
import com.civileg.app.domain.calculations.ecp.ECPTank
import com.civileg.app.domain.entities.DesignCode

object CalculationFactory {
    
    fun getColumnDesign(code: DesignCode): ColumnDesign = when (code) {
        DesignCode.ECP -> ECPColumn()
        DesignCode.ACI -> ACIColumn()
        DesignCode.SBC -> SBCColumn()
    }
    
    fun getBeamDesign(code: DesignCode): BeamDesign = when (code) {
        DesignCode.ECP -> ECPBeam()
        DesignCode.ACI -> ECPBeam() // Placeholder
        DesignCode.SBC -> ECPBeam() // Placeholder
    }

    fun getSlabDesign(code: DesignCode): SlabDesign = when (code) {
        DesignCode.ECP -> ECPSlab()
        DesignCode.ACI -> ECPSlab() // Placeholder
        DesignCode.SBC -> ECPSlab() // Placeholder
    }

    fun getTankDesign(code: DesignCode): TankDesign = when (code) {
        DesignCode.ECP -> ECPTank()
        DesignCode.ACI -> ECPTank() // Placeholder
        DesignCode.SBC -> ECPTank() // Placeholder
    }
}
