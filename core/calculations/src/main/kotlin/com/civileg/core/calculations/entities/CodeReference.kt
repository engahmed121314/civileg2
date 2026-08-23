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
    }
    
    object ACI {
        const val COLUMN_AXIAL = "ACI 318-19: Chapter 10"
        const val COLUMN_REINFORCEMENT_MIN = "ACI 318-19: Section 10.6.1 (ρmin = 1%)"
        const val COLUMN_REINFORCEMENT_MAX = "ACI 318-19: Section 10.6.1 (ρmax = 8%)"
        const val COLUMN_TIES = "ACI 318-19: Section 25.7.2"
        const val BEAM_FLEXURE = "ACI 318-19: Chapter 9"
        const val BEAM_SHEAR = "ACI 318-19: Chapter 22"
    }
    
    object SBC {
        const val COLUMN_AXIAL = "SBC 304-2018: Section 10"
        const val COLUMN_REINFORCEMENT_MIN = "SBC 304-2018: Section 10.6.1 (ρmin = 1%)"
        const val BEAM_FLEXURE = "SBC 304-2018: Section 9"
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
