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

        // ── P4-4 (§9.38): بنود إضافية لسد فجوات العناصر المتبقية ──
        // توليفات الأحمال
        const val LOAD_COMBINATION = "ECP 203-2020: Section 2-3-1 (1.4D + 1.6L)"

        // الأغطية الخرسانية
        const val BEAM_COVER = "ECP 203-2020: Section 4-1-4 (Beams ≥ 40mm)"
        const val SLAB_COVER = "ECP 203-2020: Section 4-1-4 (Slabs ≥ 25mm)"

        // السماكات الدنيا والتسليح الأدنى للبلاطات
        const val SLAB_MIN_THICKNESS = "ECP 203-2020: Section 6-3-1 (Minimum Thickness)"
        const val SLAB_MIN_STEEL = "ECP 203-2020: Section 6-1-3 (Shrinkage Reinforcement ≥ 0.15%)"

        // السلالم (بلاطة مائلة أحادية الاتجاه)
        const val STAIRCASE_FLEXURE = "ECP 203-2020: Section 6-2-1 (Staircase — Inclined One-Way Slab)"
        const val STAIRCASE_SHEAR = "ECP 203-2020: Section 4-2-2 / qcu §4-3-1-2 (Staircase Shear)"
        const val STAIRCASE_DEFLECTION = "ECP 203-2020: Section 6-3 (Staircase Deflection)"

        // البلاطات اللاكمرية (Flat Slabs)
        const val FLAT_SLAB_METHOD = "ECP 203-2020: Section 6-5 (Flat Slabs — Empirical Method)"
        const val FLAT_SLAB_PUNCHING = "ECP 203-2020: Section 6-5 (Punching Shear — Two-Way Shear)"

        // البلاطات المفرغة (Waffle) والهوردي (Hordi/Joist)
        const val WAFFLE_SLAB = "ECP 203-2020: Section 6-6 (Paneled/Ribbed Slabs)"
        const val HORDI_JOIST_SLAB = "ECP 203-2020: Section 6-1-2 (Hollow-Block / Ribbed Slabs)"

        // القواعد الشداد
        const val STRAP_FOOTING = "ECP 203-2020: Section 7-2 (Strap Footings)"

        // حوائط القص — الانحناء داخل المستوى
        const val SHEAR_WALL_FLEXURE = "ECP 203-2020: Section 8-3 (Shear Walls — In-Plane Flexure)"
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

        // ── P4-4 (§9.38): clauses for the remaining element types ──
        // Load combinations
        const val LOAD_COMBINATION = "ACI 318-19: Section 5.3.1 (1.2D + 1.6L)"

        // Concrete cover
        const val BEAM_COVER = "ACI 318-19: Table 20.5.1.3.1 (Beams ≥ 40mm)"
        const val SLAB_COVER = "ACI 318-19: Table 20.5.1.3.1 (Slabs ≥ 20mm)"

        // Slab minimum thickness / minimum steel
        const val SLAB_MIN_THICKNESS = "ACI 318-19: Tables 7.3.1.1 / 8.3.1.1 (Minimum Thickness)"
        const val SLAB_MIN_STEEL = "ACI 318-19: Sections 7.6.1.1 / 24.4.3.2 (Shrinkage & Temperature)"

        // Staircases (inclined one-way slabs)
        const val STAIRCASE_FLEXURE = "ACI 318-19: Chapter 7 (Staircase — Inclined One-Way Slab)"
        const val STAIRCASE_SHEAR = "ACI 318-19: Section 22.5 (Staircase Shear)"
        const val STAIRCASE_DEFLECTION = "ACI 318-19: Table 24.2.2 (Staircase Deflection)"

        // Flat slabs
        const val FLAT_SLAB_METHOD = "ACI 318-19: Section 8.10 (Direct Design Method)"
        const val FLAT_SLAB_PUNCHING = "ACI 318-19: Section 22.6.5 (Punching Shear — Two-Way Shear)"

        // Waffle and joist/hordi slabs
        const val WAFFLE_SLAB = "ACI 318-19: Chapter 8 + Section 9.8 (Two-Way Ribbed / Joist Construction)"
        const val HORDI_JOIST_SLAB = "ACI 318-19: Section 9.8 (Joist Construction)"

        // Strap footings
        const val STRAP_FOOTING = "ACI 318-19: Chapter 13 (Foundations — Strap/Ground Beams)"

        // Shear walls — in-plane flexure
        const val SHEAR_WALL_FLEXURE = "ACI 318-19: Chapter 11 (Walls — In-Plane Forces)"
    }
    
    // === الكود السعودي SBC 304-2018 ===
    // Chapter structure verified against the official SBC 304-2018 table of contents,
    // which follows ACI 318-14: Ch.7 One-Way Slabs, Ch.8 Two-Way Slabs, Ch.9 Beams,
    // Ch.10 Columns, Ch.11 Walls, Ch.12 Diaphragms, Ch.13 Foundations, Ch.15 Joints,
    // Ch.18 Earthquake-Resistant Structures, Ch.22 Sectional Strength,
    // Ch.24 Serviceability, Ch.25 Reinforcement Details.
    object SBC {
        // الأعمدة
        const val COLUMN_AXIAL = "SBC 304-2018: Section 10"
        const val COLUMN_REINFORCEMENT_MIN = "SBC 304-2018: Section 10.6.1 (ρmin = 1%)"
        const val COLUMN_REINFORCEMENT_MAX = "SBC 304-2018: Section 10.6.1 (ρmax = 8%)"
        const val COLUMN_TIES = "SBC 304-2018: Section 10.7.6"
        const val COLUMN_COVER = "SBC 304-2018: Section 20.5.1 (Cover ≥ 40mm)"
        
        // الكمرات
        const val BEAM_FLEXURE = "SBC 304-2018: Section 9 / 22.2 (Flexure)"
        const val BEAM_SHEAR = "SBC 304-2018: Section 22.5 (One-Way Shear)"
        const val BEAM_REINFORCEMENT_MIN = "SBC 304-2018: Section 9.6.1"
        const val BEAM_REINFORCEMENT_MAX = "SBC 304-2018: Section 9.3.3.1"
        const val BEAM_DEVELOPMENT_LENGTH = "SBC 304-2018: Section 25.4 (Development of Reinforcement)"
        const val BEAM_DOUBLY_REINFORCED = "SBC 304-2018: Section 9.3.3.2"
        
        // البلاطات
        const val SLAB_ONE_WAY = "SBC 304-2018: Chapter 7 (One-Way Slabs)"
        const val SLAB_TWO_WAY = "SBC 304-2018: Chapter 8 (Two-Way Slabs)"
        const val SLAB_DEFLECTION = "SBC 304-2018: Section 24.2 (Deflections)"
        
        // القواعد
        const val FOOTING_BEARING = "SBC 304-2018: Section 13.3 / 22.8 (Bearing)"
        const val FOOTING_SHEAR_PUNCHING = "SBC 304-2018: Section 22.6 (Two-Way Shear)"
        const val FOOTING_REINFORCEMENT = "SBC 304-2018: Section 13.3 (Foundations)"
        
        // الزلازل
        const val SEISMIC_BASE_SHEAR = "SBC 304-2018: Section 18 (Earthquake-Resistant Structures)"
        const val SEISMIC_RESPONSE_SPECTRUM = "SBC 304-2018: Section 18.2"
        
        // الحوائط (السند والقِص)
        const val RW_LATERAL_EARTH = "SBC 304-2018: Section 11.5 (Walls — Lateral Earth)"
        const val RW_STABILITY = "SBC 304-2018: Section 11.5 (Wall Stability)"
        const val SW_SHEAR = "SBC 304-2018: Section 11.5 (Wall Shear)"
        
        // الأساسات الخازوقية والقواعد المشتركة
        const val PILE_CAPACITY = "SBC 304-2018: Section 13.3 (Pile Caps)"
        const val COMBINED_FOOTING = "SBC 304-2018: Section 13.3 (Combined Footings)"
        
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
        const val FRAME_FLEXURE = "SBC 304-2018: Section 9 / 22.2 (Frame Members)"
        const val FRAME_SHEAR = "SBC 304-2018: Section 22.5 (Frame Members)"

        // ── P4-4 (§9.38): بنود العناصر المتبقية — SBC 304 يتبع ترقيم ACI 318-14 ──
        // توليفات الأحمال
        const val LOAD_COMBINATION = "SBC 304-2018: Section 5.3.1 (1.2D + 1.6L)"

        // الأغطية الخرسانية (بيئة السعودية الحارة/المالحة)
        const val BEAM_COVER = "SBC 304-2018: Section 20.5.1 (Beams: 40mm interior / 50mm exterior)"
        const val SLAB_COVER = "SBC 304-2018: Section 20.5.1 (Slabs: 30mm interior / 40mm exterior)"

        // السماكات الدنيا والتسليح الأدنى للبلاطات
        const val SLAB_MIN_THICKNESS = "SBC 304-2018: Tables 7.3.1.1 / 8.3.1.1 (Minimum Thickness)"
        const val SLAB_MIN_STEEL = "SBC 304-2018: Sections 7.6.1 / 24.4.3.2 (Shrinkage & Temperature)"

        // السلالم (بلاطة مائلة أحادية الاتجاه)
        const val STAIRCASE_FLEXURE = "SBC 304-2018: Chapter 7 (Staircase — Inclined One-Way Slab)"
        const val STAIRCASE_SHEAR = "SBC 304-2018: Section 22.5 (Staircase Shear)"
        const val STAIRCASE_DEFLECTION = "SBC 304-2018: Table 24.2.2 (Staircase Deflection)"

        // البلاطات اللاكمرية
        const val FLAT_SLAB_METHOD = "SBC 304-2018: Section 8.10 (Direct Design Method)"
        const val FLAT_SLAB_PUNCHING = "SBC 304-2018: Section 22.6.5 (Punching Shear — Two-Way Shear)"

        // البلاطات المفرغة والهوردي
        const val WAFFLE_SLAB = "SBC 304-2018: Chapter 8 + Section 9.8 (Two-Way Ribbed / Joist Construction)"
        const val HORDI_JOIST_SLAB = "SBC 304-2018: Section 9.8 (Joist Construction)"

        // القواعد الشداد
        const val STRAP_FOOTING = "SBC 304-2018: Chapter 13 (Foundations — Strap/Ground Beams)"

        // حوائط القص — الانحناء داخل المستوى
        const val SHEAR_WALL_FLEXURE = "SBC 304-2018: Chapter 11 (Walls — In-Plane Forces)"
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
