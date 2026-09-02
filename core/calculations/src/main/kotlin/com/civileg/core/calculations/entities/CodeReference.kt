package com.civileg.core.calculations.entities

/**
 * مرجع موحد لجميع بنود الأكواد المستخدمة في التطبيق
 */
object CodeReference {
    
    object ECP {
        const val COLUMN_AXIAL = "ECP 203-2020: Section 4-2-3"
        const val COLUMN_REINFORCEMENT_MIN = "ECP 203-2020: Section 4-2-5-1 (ρmin = 0.8%)"
        const val COLUMN_REINFORCEMENT_MAX = "ECP 203-2020: Section 4-2-5-2 (ρmax = 8%)"
        const val COLUMN_TIES = "ECP 203-2020: Section 4-2-6"
        const val COLUMN_COVER = "ECP 203-2020: Section 4-1-4 (Cover ≥ 40mm)"
        const val BEAM_FLEXURE = "ECP 203-2020: Section 4-2-1"
        const val BEAM_SHEAR = "ECP 203-2020: Section 4-2-2"
        const val BEAM_REINFORCEMENT_MIN = "ECP 203-2020: Section 4-2-5"
        const val BEAM_REINFORCEMENT_MAX = "ECP 203-2020: Section 4-2-1-2 (Ductility requirement)"
        const val BEAM_DEVELOPMENT_LENGTH = "ECP 203-2020: Section 5-3"
        const val BEAM_DOUBLY_REINFORCED = "ECP 203-2020: Section 4-2-2-2"
        const val SLAB_ONE_WAY = "ECP 203-2020: Section 6-2"
        const val SLAB_TWO_WAY = "ECP 203-2020: Section 6-2-2"
        const val SLAB_FLAT = "ECP 203-2020: Section 6-2-3 (Flat slab — DDM/EFM)"
        const val FOOTING = "ECP 203-2020: Section 7-1"
        const val FOOTING_PUNCHING = "ECP 203-2020: Section 4-3-2"
        const val TANK = "ECP 203-2020: Section 8-1 (Water-Retaining Structures)"
        const val RETAINING_WALL = "ECP 203-2020: Section 4-2 (earth-retaining wall flexure)"
        const val SHEAR_WALL = "ECP 203-2020: Section 6-7 (Structural walls / ECP 201: Seismic walls)"
        const val STAIR = "ECP 203-2020: Section 4-2 (waist slab flexure)"
        const val STAIR_SHEAR = "ECP 203-2020: Section 4-3-1-2"
        const val STAIR_DEFLECTION = "ECP 203-2020: Section 6-3"
        const val STAIR_GEOMETRY = "ECP 201-2012: Staircase geometry"
        const val SEISMIC_BASE_SHEAR = "ECP 201: Equivalent Static Method"
    }
    
    object ACI {
        const val COLUMN_AXIAL = "ACI 318-19: Chapter 10"
        const val COLUMN_REINFORCEMENT_MIN = "ACI 318-19: Section 10.6.1 (ρmin = 1%)"
        const val COLUMN_REINFORCEMENT_MAX = "ACI 318-19: Section 10.6.1 (ρmax = 8%)"
        const val COLUMN_TIES = "ACI 318-19: Section 25.7.2"
        const val BEAM_FLEXURE = "ACI 318-19: Chapter 9"
        const val BEAM_SHEAR = "ACI 318-19: Chapter 22"
        const val BEAM_REINFORCEMENT_MIN = "ACI 318-19: Section 9.6.1"
        const val BEAM_REINFORCEMENT_MAX = "ACI 318-19: Section 9.3.3"
        const val SLAB_DEFLECTION = "ACI 318-19: Section 24.2"
        const val BEAM_DEVELOPMENT_LENGTH = "ACI 318-19: Chapter 25"
        const val SLAB_ONE_WAY = "ACI 318-19: Section 7.3"
        const val SLAB_TWO_WAY = "ACI 318-19: Section 8.3"
        const val SLAB_FLAT = "ACI 318-19: Section 8 (Two-way flat plate — DDM/EFM)"
        const val FOOTING = "ACI 318-19: Chapter 13"
        const val FOOTING_PUNCHING = "ACI 318-19: Section 22.6.5"
        const val TANK = "ACI 350-06 / ACI 318-19: Water-Retaining Structure"
        const val RETAINING_WALL = "ACI 318-19: Chapter 9 (earth-retaining wall flexure)"
        const val SHEAR_WALL = "ACI 318-19: Section 18.10 (Structural shear walls)"
        const val STAIR = "ACI 318-19: Chapter 9 (one-way slabs)"
        const val STAIR_SHEAR = "ACI 318-19: Chapter 22"
        const val STAIR_DEFLECTION = "ACI 318-19: Table 24.2.2"
        const val STAIR_GEOMETRY = "IBC Section 1011.5"
        const val SEISMIC_BASE_SHEAR = "ASCE 7-16: Equivalent Lateral Force"
    }
    
    object SBC {
        const val COLUMN_AXIAL = "SBC 304-2018: Section 10"
        const val COLUMN_REINFORCEMENT_MIN = "SBC 304-2018: Section 10.6.1 (ρmin = 1%)"
        const val COLUMN_TIES = "SBC 304-2018: Section 25.7.2"
        const val BEAM_FLEXURE = "SBC 304-2018: Section 9"
        const val BEAM_SHEAR = "SBC 304-2018: Section 22"
        const val BEAM_DEVELOPMENT_LENGTH = "SBC 304-2018: Section 25"
        const val FOOTING = "SBC 304-2018: Section 13"
        const val FOOTING_PUNCHING = "SBC 304-2018: Section 22.6"
        const val TANK = "SBC 304-2018: Section 8 (Water-Retaining)"
        const val RETAINING_WALL = "SBC 304-2018: Section 9 (earth-retaining wall flexure)"
        const val SHEAR_WALL = "SBC 304-2018: Section 18.10 (Structural shear walls)"
        const val STAIR = "SBC 304-2018: Section 9 (waist slab flexure)"
        const val SLAB_FLAT = "SBC 304-2018: Section 8 (Two-way flat plate — DDM/EFM)"
        const val STAIR_SHEAR = "SBC 304-2018: Section 11"
        const val STAIR_DEFLECTION = "SBC 304-2018: Section 9.5"
        const val STAIR_GEOMETRY = "SBC 304-2018: Stair geometry"
        const val SEISMIC_BASE_SHEAR = "SBC 301: Section 12.8"
    }
    
    fun getReference(code: DesignCode, key: String): String = try {
        val innerClass = when (code) {
            DesignCode.ECP -> ECP::class.java
            DesignCode.ACI -> ACI::class.java
            DesignCode.SBC -> SBC::class.java
        }
        innerClass.getDeclaredField(key).get(null) as String
    } catch (e: Exception) {
        "Reference not found"
    }
}
