package com.civileg.app.domain.entities

/**
 * مرجع موحد لجميع بنود الأكواد المستخدمة في التطبيق
 * يضمن توحيد التحذيرات والملاحظات حسب كل كود
 */
object CodeReference {
    
    // === الكود المصري ECP 203-2020 ===
    object ECP {
        // الأعمدة
        const val COLUMN_AXIAL = "ECP 203-2020: Section 4-2-3"
        const val COLUMN_REINFORCEMENT_MIN = "ECP 203-2020: Section 4-2-5-1 (ρmin = 0.8%)"
        const val COLUMN_REINFORCEMENT_MAX = "ECP 203-2020: Section 4-2-5-2 (ρmax = 8%)"
        const val COLUMN_TIES = "ECP 203-2020: Section 4-2-6"
        const val COLUMN_COVER = "ECP 203-2020: Section 4-1-4 (Cover ≥ 40mm)"
        
        // الكمرات
        const val BEAM_FLEXURE = "ECP 203-2020: Section 4-2-1"
        const val BEAM_SHEAR = "ECP 203-2020: Section 4-2-2"
        const val BEAM_REINFORCEMENT_MIN = "ECP 203-2020: Section 4-2-5"
        const val BEAM_REINFORCEMENT_MAX = "ECP 203-2020: Section 4-2-1-2 (Ductility requirement)"
        const val BEAM_DEVELOPMENT_LENGTH = "ECP 203-2020: Section 5-3"
        const val BEAM_DOUBLY_REINFORCED = "ECP 203-2020: Section 4-2-2-2"
        
        // البلاطات
        const val SLAB_ONE_WAY = "ECP 203-2020: Section 6-2-1"
        const val SLAB_TWO_WAY = "ECP 203-2020: Section 6-2-2"
        const val SLAB_DEFLECTION = "ECP 203-2020: Section 6-3"
        
        // القواعد
        const val FOOTING_BEARING = "ECP 203-2020: Section 7-2"
        const val FOOTING_SHEAR_PUNCHING = "ECP 203-2020: Section 7-3"
        const val FOOTING_REINFORCEMENT = "ECP 203-2020: Section 7-4"
        
        // الزلازل
        const val SEISMIC_BASE_SHEAR = "ECP 203-2020: Appendix B"
        const val SEISMIC_RESPONSE_SPECTRUM = "ECP 203-2020: Section B-2"
        
        // الحوائط (السند والقِص)
        const val RW_LATERAL_EARTH = "ECP 203-2020: Section 8-1 (Lateral Earth Pressure)"
        const val RW_STABILITY = "ECP 203-2020: Section 8-2 (Stability Checks)"
        const val SW_SHEAR = "ECP 203-2020: Section 8-3 (Shear Walls)"
        
        // الأساسات الخازوقية والقواعد المشتركة
        const val PILE_CAPACITY = "ECP 203-2020: Section 7-5 (Pile Caps)"
        const val COMBINED_FOOTING = "ECP 203-2020: Section 7-3 (Combined Footings)"
        
        // الخزانات
        const val TANK_HYDROSTATIC = "ECP 203-2020: Section 6-4 (Liquid-Retaining Structures)"
        
        // الهياكل المعدنية
        const val STEEL_TENSION_MEMBER = "ECP 205-2007: Chapter 5 (Tension Members)"
        const val STEEL_BEAM_FLEXURE = "ECP 205-2007: Chapter 6 (Flexural Members)"
        const val STEEL_BEAM_SHEAR = "ECP 205-2007: Chapter 6 (Shear)"
        const val STEEL_COLUMN_COMPRESSION = "ECP 205-2007: Chapter 7 (Compression Members)"
        const val STEEL_COMBINED_STRESS = "ECP 205-2007: Chapter 8 (Combined Forces)"
        const val STEEL_DEFLECTION = "ECP 205-2007: Serviceability Limit State (Deflection)"
        const val STEEL_BASE_PLATE = "ECP 205-2007: Column Base Plates"
        const val STEEL_BOLTED_CONNECTION = "ECP 205-2007: Chapter 10 (Bolted Connections)"
        const val STEEL_WELDED_CONNECTION = "ECP 205-2007: Chapter 10 (Welded Connections)"
        const val STEEL_BLOCK_SHEAR = "ECP 205-2007: Block Shear Rupture"
        
        // الإطارات الخرسانية
        const val FRAME_FLEXURE = "ECP 203-2020: Section 4-2-1 (Frame Members)"
        const val FRAME_SHEAR = "ECP 203-2020: Section 4-2-2 (Frame Members)"
    }
    
    // === الكود الأمريكي ACI 318-19 ===
    object ACI {
        // الأعمدة
        const val COLUMN_AXIAL = "ACI 318-19: Chapter 10"
        const val COLUMN_REINFORCEMENT_MIN = "ACI 318-19: Section 10.6.1 (ρmin = 1%)"
        const val COLUMN_REINFORCEMENT_MAX = "ACI 318-19: Section 10.6.1 (ρmax = 8%)"
        const val COLUMN_TIES = "ACI 318-19: Section 25.7.2"
        const val COLUMN_COVER = "ACI 318-19: Section 20.5.1.3 (Cover ≥ 40mm)"
        
        // الكمرات
        const val BEAM_FLEXURE = "ACI 318-19: Chapter 9"
        const val BEAM_SHEAR = "ACI 318-19: Chapter 22"
        const val BEAM_REINFORCEMENT_MIN = "ACI 318-19: Section 9.6.1"
        const val BEAM_REINFORCEMENT_MAX = "ACI 318-19: Section 9.3.3.1 (Tension-controlled)"
        const val BEAM_DEVELOPMENT_LENGTH = "ACI 318-19: Chapter 25"
        const val BEAM_DOUBLY_REINFORCED = "ACI 318-19: Section 9.3.3.2"
        
        // البلاطات
        const val SLAB_ONE_WAY = "ACI 318-19: Chapter 7 (One-Way Slabs)"
        const val SLAB_TWO_WAY = "ACI 318-19: Chapter 8 (Two-Way Slabs)"
        const val SLAB_DEFLECTION = "ACI 318-19: Section 24.2"
        
        // القواعد
        const val FOOTING_BEARING = "ACI 318-19: Section 22.8"
        const val FOOTING_SHEAR_PUNCHING = "ACI 318-19: Section 22.6"
        const val FOOTING_REINFORCEMENT = "ACI 318-19: Section 13.3"
        
        // الزلازل
        const val SEISMIC_BASE_SHEAR = "ACI 318-19: Chapter 18"
        const val SEISMIC_RESPONSE_SPECTRUM = "ACI 318-19: Section 18.2"
        
        // الحوائط (السند والقِص)
        const val RW_LATERAL_EARTH = "ACI 318-19: Section 13.2 (Walls)"
        const val RW_STABILITY = "ACI 318-19: Section 13.2.2 (Stability)"
        const val SW_SHEAR = "ACI 318-19: Section 11.5 (Wall Shear)"
        
        // الأساسات الخازوقية والقواعد المشتركة
        const val PILE_CAPACITY = "ACI 318-19: Section 13.3 (Pile Caps)"
        const val COMBINED_FOOTING = "ACI 318-19: Section 13.3 (Combined Footings)"
        
        // الخزانات
        const val TANK_HYDROSTATIC = "ACI 350-20: Liquid-Containing Structures"
        
        // الهياكل المعدنية
        const val STEEL_TENSION_MEMBER = "AISC 360-22: Chapter D (Tension Members)"
        const val STEEL_BEAM_FLEXURE = "AISC 360-22: Chapter F (Flexure)"
        const val STEEL_BEAM_SHEAR = "AISC 360-22: Chapter G (Shear)"
        const val STEEL_COLUMN_COMPRESSION = "AISC 360-22: Chapter E (Compression)"
        const val STEEL_COMBINED_STRESS = "AISC 360-22: Chapter H (Combined Forces)"
        const val STEEL_DEFLECTION = "AISC 360-22: Chapter L (Serviceability)"
        const val STEEL_BASE_PLATE = "AISC 360-22: Chapter J (Column Base Plates)"
        const val STEEL_BOLTED_CONNECTION = "AISC 360-22: Chapter J3 (Bolted Connections)"
        const val STEEL_WELDED_CONNECTION = "AISC 360-22: Chapter J2 (Welded Connections)"
        const val STEEL_BLOCK_SHEAR = "AISC 360-22: Section J4.3 (Block Shear)"
        
        // الإطارات الخرسانية
        const val FRAME_FLEXURE = "ACI 318-19: Chapter 9 (Frame Members)"
        const val FRAME_SHEAR = "ACI 318-19: Chapter 22 (Frame Members)"
    }
    
    // === الكود السعودي SBC 304-2018 ===
    object SBC {
        // الأعمدة
        const val COLUMN_AXIAL = "SBC 304-2018: Section 10"
        const val COLUMN_REINFORCEMENT_MIN = "SBC 304-2018: Section 10.6.1 (ρmin = 1%)"
        const val COLUMN_REINFORCEMENT_MAX = "SBC 304-2018: Section 10.6.1 (ρmax = 8%)"
        const val COLUMN_TIES = "SBC 304-2018: Section 10.7.6"
        const val COLUMN_COVER = "SBC 304-2018: Section 7.7 (Cover ≥ 40mm)"
        
        // الكمرات
        const val BEAM_FLEXURE = "SBC 304-2018: Section 9"
        const val BEAM_SHEAR = "SBC 304-2018: Section 11"
        const val BEAM_REINFORCEMENT_MIN = "SBC 304-2018: Section 9.6.1"
        const val BEAM_REINFORCEMENT_MAX = "SBC 304-2018: Section 9.3.3.1"
        const val BEAM_DEVELOPMENT_LENGTH = "SBC 304-2018: Section 12"
        const val BEAM_DOUBLY_REINFORCED = "SBC 304-2018: Section 9.3.3.2"
        
        // البلاطات
        const val SLAB_ONE_WAY = "SBC 304-2018: Chapter 7 (One-Way Slabs)"
        const val SLAB_TWO_WAY = "SBC 304-2018: Section 8.4"
        const val SLAB_DEFLECTION = "SBC 304-2018: Section 9.5"
        
        // القواعد
        const val FOOTING_BEARING = "SBC 304-2018: Section 15.2"
        const val FOOTING_SHEAR_PUNCHING = "SBC 304-2018: Section 15.5"
        const val FOOTING_REINFORCEMENT = "SBC 304-2018: Section 15.4"
        
        // الزلازل
        const val SEISMIC_BASE_SHEAR = "SBC 304-2018: Section 21"
        const val SEISMIC_RESPONSE_SPECTRUM = "SBC 304-2018: Section 21.2"
        
        // الحوائط (السند والقِص)
        const val RW_LATERAL_EARTH = "SBC 304-2018: Section 13.2 (Walls)"
        const val RW_STABILITY = "SBC 304-2018: Section 13.2.2 (Stability)"
        const val SW_SHEAR = "SBC 304-2018: Section 11.5 (Wall Shear)"
        
        // الأساسات الخازوقية والقواعد المشتركة
        const val PILE_CAPACITY = "SBC 304-2018: Section 15.9 (Pile Caps)"
        const val COMBINED_FOOTING = "SBC 304-2018: Section 15 (Combined Footings)"
        
        // الخزانات
        const val TANK_HYDROSTATIC = "SBC 304-2018: Section 350 (Liquid-Containing Structures)"
        
        // الهياكل المعدنية
        const val STEEL_TENSION_MEMBER = "SBC 306-2018: Chapter D (Tension Members)"
        const val STEEL_BEAM_FLEXURE = "SBC 306-2018: Chapter F (Flexure)"
        const val STEEL_BEAM_SHEAR = "SBC 306-2018: Chapter G (Shear)"
        const val STEEL_COLUMN_COMPRESSION = "SBC 306-2018: Chapter E (Compression)"
        const val STEEL_COMBINED_STRESS = "SBC 306-2018: Chapter H (Combined Forces)"
        const val STEEL_DEFLECTION = "SBC 306-2018: Chapter L (Serviceability)"
        const val STEEL_BASE_PLATE = "SBC 306-2018: Chapter J (Column Base Plates)"
        const val STEEL_BOLTED_CONNECTION = "SBC 306-2018: Chapter J3 (Bolted Connections)"
        const val STEEL_WELDED_CONNECTION = "SBC 306-2018: Chapter J2 (Welded Connections)"
        const val STEEL_BLOCK_SHEAR = "SBC 306-2018: Section J4.3 (Block Shear)"
        
        // الإطارات الخرسانية
        const val FRAME_FLEXURE = "SBC 304-2018: Section 9 (Frame Members)"
        const val FRAME_SHEAR = "SBC 304-2018: Section 11 (Frame Members)"
    }
    
    // دالة مساعدة للحصول على المرجع حسب الكود
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
