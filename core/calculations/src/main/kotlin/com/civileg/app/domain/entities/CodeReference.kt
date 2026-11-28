package com.civileg.app.domain.entities

/**
 * مرجع موحد لجميع بنود الأكواد المستخدمة في التطبيق
 * يضمن توحيد التحذيرات والملاحظات حسب كل كود
 */
object CodeReference {
    
    // === الكود المصري ECP 203 ===
    object ECP {
        // الأعمدة
        const val COLUMN_AXIAL = "ECP 203-2020 Eq. 4-12-a (checked against the supplied edition; valid for e <= max(0.05t, 20 mm))"
        const val COLUMN_REINFORCEMENT_MIN = "ECP 203-2025 Sec. 6-4-7 (a), p.6-55: ρmin = 0.80% of the required area, not below 0.60% of the actual area (applied to the gross area)"
        const val COLUMN_REINFORCEMENT_MAX = "ECP 203-2025 Sec. 6-4-7 (b), p.6-55: ρmax = 4% middle, 5% edge, 6% corner columns (8% only at laps)"
        const val COLUMN_TIES = "ECP 203-2025 Sec. 6-4-7 (g, h), p.6-55: tie spacing ≤ min(15 x smallest bar dia, 200 mm); tie dia ≥ max(8 mm, 1/4 largest bar dia)"
        const val COLUMN_COVER = "ECP 203: Section 4-1-4 (Cover ≥ 40mm) (clause not verified)"
        
        // الكمرات
        const val BEAM_FLEXURE = "ECP 203: Section 4-2-1 (clause not verified)"
        const val BEAM_SHEAR = "ECP 203: Section 4-2-2 (clause not verified)"
        const val BEAM_REINFORCEMENT_MIN = "ECP 203: Section 4-2-5 (clause not verified)"
        const val BEAM_REINFORCEMENT_MAX = "ECP 203: Section 4-2-1-2 (Ductility requirement) (clause not verified)"
        const val BEAM_DEVELOPMENT_LENGTH = "ECP 203: Section 5-3 (clause not verified)"
        const val BEAM_DOUBLY_REINFORCED = "ECP 203: Section 4-2-2-2 (clause not verified)"
        
        // البلاطات
        const val SLAB_ONE_WAY = "ECP 203: Section 6-2-1 (clause not verified)"
        const val SLAB_TWO_WAY = "ECP 203: Section 6-2-2 (clause not verified)"
        const val SLAB_DEFLECTION = "ECP 203: Section 6-3 (clause not verified)"
        
        // القواعد
        const val FOOTING_BEARING = "ECP 203: Section 7-2 (clause not verified)"
        const val FOOTING_SHEAR_PUNCHING = "ECP 203: Section 7-3 (clause not verified)"
        const val FOOTING_REINFORCEMENT = "ECP 203: Section 7-4 (clause not verified)"
        
        // الزلازل
        const val SEISMIC_BASE_SHEAR = "ECP seismic method: governing seismic code not supplied, indicative only"
        const val SEISMIC_RESPONSE_SPECTRUM = "ECP seismic method: governing seismic code not supplied, indicative only"
        
        // الحوائط (السند والقِص)
        const val RW_LATERAL_EARTH = "ECP 203: Section 8-1 (Lateral Earth Pressure) (clause not verified)"
        const val RW_STABILITY = "ECP 203: Section 8-2 (Stability Checks) (clause not verified)"
        const val SW_SHEAR = "ECP 203: Section 8-3 (Shear Walls) (clause not verified)"
        
        // الأساسات الخازوقية والقواعد المشتركة
        const val PILE_CAPACITY = "ECP 203: Section 7-5 (Pile Caps) (clause not verified)"
        const val COMBINED_FOOTING = "ECP 203: Section 7-3 (Combined Footings) (clause not verified)"
        
        // الخزانات
        const val TANK_HYDROSTATIC = "ECP 203: Section 6-4 (Liquid-Retaining Structures) (clause not verified)"
        
        // الهياكل المعدنية
        const val STEEL_TENSION_MEMBER = "ECP 205-2007: Chapter 5 (Tension Members) (clause not verified)"
        const val STEEL_BEAM_FLEXURE = "ECP 205-2007: Chapter 6 (Flexural Members) (clause not verified)"
        const val STEEL_BEAM_SHEAR = "ECP 205-2007: Chapter 6 (Shear) (clause not verified)"
        const val STEEL_COLUMN_COMPRESSION = "ECP 205-2007: Chapter 7 (Compression Members) (clause not verified)"
        const val STEEL_COMBINED_STRESS = "ECP 205-2007: Chapter 8 (Combined Forces) (clause not verified)"
        const val STEEL_DEFLECTION = "ECP 205-2007: Serviceability Limit State (Deflection) (clause not verified)"
        const val STEEL_BASE_PLATE = "ECP 205-2007: Column Base Plates (clause not verified)"
        const val STEEL_BOLTED_CONNECTION = "ECP 205-2007: Chapter 10 (Bolted Connections) (clause not verified)"
        const val STEEL_WELDED_CONNECTION = "ECP 205-2007: Chapter 10 (Welded Connections) (clause not verified)"
        const val STEEL_BLOCK_SHEAR = "ECP 205-2007: Block Shear Rupture (clause not verified)"
        
        // الإطارات الخرسانية
        const val FRAME_FLEXURE = "ECP 203: Section 4-2-1 (Frame Members) (clause not verified)"
        const val FRAME_SHEAR = "ECP 203: Section 4-2-2 (Frame Members) (clause not verified)"

        // ── P4-4 (§9.38): بنود إضافية لسد فجوات العناصر المتبقية ──
        // توليفات الأحمال
        const val LOAD_COMBINATION = "ECP 203: Section 2-3-1 (1.4D + 1.6L) (clause not verified)"

        // الأغطية الخرسانية
        const val BEAM_COVER = "ECP 203: Section 4-1-4 (Beams ≥ 40mm) (clause not verified)"
        const val SLAB_COVER = "ECP 203: Section 4-1-4 (Slabs ≥ 25mm) (clause not verified)"

        // السماكات الدنيا والتسليح الأدنى للبلاطات
        const val SLAB_MIN_THICKNESS = "ECP 203: Section 6-3-1 (Minimum Thickness) (clause not verified)"
        const val SLAB_MIN_STEEL = "ECP 203: Section 6-1-3 (Shrinkage Reinforcement ≥ 0.15%) (clause not verified)"

        // السلالم (بلاطة مائلة أحادية الاتجاه)
        const val STAIRCASE_FLEXURE = "ECP 203-2025: §4-2-1-2 + Table 4-1 no redistribution (verified, printed 4-6/4-7); stair as inclined one-way slab §6-2-1-2 (verified, printed 6-4..6-7)"
        const val STAIRCASE_SHEAR = "ECP 203-2025: Eq. 4-27/4-29 + §4-2-2-2-1 slabs concrete-only (verified, printed 4-18/4-19/4-25)"
        const val STAIRCASE_DEFLECTION = "ECP 203-2025: §4-3 Table 4-10 + Eq. 4-81 (verified, printed 4-65)"

        // البلاطات اللاكمرية (Flat Slabs)
        const val FLAT_SLAB_METHOD = "ECP 203: Section 6-5 (Flat Slabs — Empirical Method) (clause not verified)"
        const val FLAT_SLAB_PUNCHING = "ECP 203: Section 6-5 (Punching Shear — Two-Way Shear) (clause not verified)"

        // البلاطات المفرغة (Waffle) والهوردي (Hordi/Joist)
        const val WAFFLE_SLAB = "ECP 203: Section 6-6 (Paneled/Ribbed Slabs) (clause not verified)"
        const val HORDI_JOIST_SLAB = "ECP 203: Section 6-1-2 (Hollow-Block / Ribbed Slabs) (clause not verified)"

        // القواعد الشداد
        const val STRAP_FOOTING = "ECP 203: Section 7-2 (Strap Footings) (clause not verified)"

        // حوائط القص — الانحناء داخل المستوى
        const val SHEAR_WALL_FLEXURE = "ECP 203: Section 8-3 (Shear Walls — In-Plane Flexure) (clause not verified)"
    }
    
    // === الكود الأمريكي ACI 318 ===
    object ACI {
        // الأعمدة
        const val COLUMN_AXIAL = "ACI 318: Chapter 10 (clause not verified)"
        const val COLUMN_REINFORCEMENT_MIN = "ACI 318: Section 10.6.1 (ρmin = 1%) (clause not verified)"
        const val COLUMN_REINFORCEMENT_MAX = "ACI 318: Section 10.6.1 (ρmax = 8%) (clause not verified)"
        const val COLUMN_TIES = "ACI 318: Section 25.7.2 (clause not verified)"
        const val COLUMN_COVER = "ACI 318: Section 20.5.1.3 (Cover ≥ 40mm) (clause not verified)"
        
        // الكمرات
        const val BEAM_FLEXURE = "ACI 318: Chapter 9 (clause not verified)"
        const val BEAM_SHEAR = "ACI 318: Chapter 22 (clause not verified)"
        const val BEAM_REINFORCEMENT_MIN = "ACI 318: Section 9.6.1 (clause not verified)"
        const val BEAM_REINFORCEMENT_MAX = "ACI 318: Section 9.3.3.1 (Tension-controlled) (clause not verified)"
        const val BEAM_DEVELOPMENT_LENGTH = "ACI 318: Chapter 25 (clause not verified)"
        const val BEAM_DOUBLY_REINFORCED = "ACI 318: Section 9.3.3.2 (clause not verified)"
        
        // البلاطات
        const val SLAB_ONE_WAY = "ACI 318: Chapter 7 (One-Way Slabs) (clause not verified)"
        const val SLAB_TWO_WAY = "ACI 318: Chapter 8 (Two-Way Slabs) (clause not verified)"
        const val SLAB_DEFLECTION = "ACI 318: Section 24.2 (clause not verified)"
        
        // القواعد
        const val FOOTING_BEARING = "ACI 318: Section 22.8 (clause not verified)"
        const val FOOTING_SHEAR_PUNCHING = "ACI 318: Section 22.6 (clause not verified)"
        const val FOOTING_REINFORCEMENT = "ACI 318: Section 13.3 (clause not verified)"
        
        // الزلازل
        const val SEISMIC_BASE_SHEAR = "ACI seismic method: governing seismic code not supplied, indicative only"
        const val SEISMIC_RESPONSE_SPECTRUM = "ACI seismic method: governing seismic code not supplied, indicative only"
        
        // الحوائط (السند والقِص)
        const val RW_LATERAL_EARTH = "ACI 318: Section 13.2 (Walls) (clause not verified)"
        const val RW_STABILITY = "ACI 318: Section 13.2.2 (Stability) (clause not verified)"
        const val SW_SHEAR = "ACI 318: Section 11.5 (Wall Shear) (clause not verified)"
        
        // الأساسات الخازوقية والقواعد المشتركة
        const val PILE_CAPACITY = "ACI 318: Section 13.3 (Pile Caps) (clause not verified)"
        const val COMBINED_FOOTING = "ACI 318: Section 13.3 (Combined Footings) (clause not verified)"
        
        // الخزانات
        const val TANK_HYDROSTATIC = "ACI 350-20: Liquid-Containing Structures (clause not verified)"
        
        // الهياكل المعدنية
        const val STEEL_TENSION_MEMBER = "AISC 360-22: Chapter D (Tension Members) (clause not verified)"
        const val STEEL_BEAM_FLEXURE = "AISC 360-22: Chapter F (Flexure) (clause not verified)"
        const val STEEL_BEAM_SHEAR = "AISC 360-22: Chapter G (Shear) (clause not verified)"
        const val STEEL_COLUMN_COMPRESSION = "AISC 360-22: Chapter E (Compression) (clause not verified)"
        const val STEEL_COMBINED_STRESS = "AISC 360-22: Chapter H (Combined Forces) (clause not verified)"
        const val STEEL_DEFLECTION = "AISC 360-22: Chapter L (Serviceability) (clause not verified)"
        const val STEEL_BASE_PLATE = "AISC 360-22: Chapter J (Column Base Plates) (clause not verified)"
        const val STEEL_BOLTED_CONNECTION = "AISC 360-22: Chapter J3 (Bolted Connections) (clause not verified)"
        const val STEEL_WELDED_CONNECTION = "AISC 360-22: Chapter J2 (Welded Connections) (clause not verified)"
        const val STEEL_BLOCK_SHEAR = "AISC 360-22: Section J4.3 (Block Shear) (clause not verified)"
        
        // الإطارات الخرسانية
        const val FRAME_FLEXURE = "ACI 318: Chapter 9 (Frame Members) (clause not verified)"
        const val FRAME_SHEAR = "ACI 318: Chapter 22 (Frame Members) (clause not verified)"

        // ── P4-4 (§9.38): clauses for the remaining element types ──
        // Load combinations
        const val LOAD_COMBINATION = "ACI 318: Section 5.3.1 (1.2D + 1.6L) (clause not verified)"

        // Concrete cover
        const val BEAM_COVER = "ACI 318: Table 20.5.1.3.1 (Beams ≥ 40mm) (clause not verified)"
        const val SLAB_COVER = "ACI 318: Table 20.5.1.3.1 (Slabs ≥ 20mm) (clause not verified)"

        // Slab minimum thickness / minimum steel
        const val SLAB_MIN_THICKNESS = "ACI 318: Tables 7.3.1.1 / 8.3.1.1 (Minimum Thickness) (clause not verified)"
        const val SLAB_MIN_STEEL = "ACI 318: Sections 7.6.1.1 / 24.4.3.2 (Shrinkage & Temperature) (clause not verified)"

        // Staircases (inclined one-way slabs)
        const val STAIRCASE_FLEXURE = "ACI 318: Chapter 7 (Staircase — Inclined One-Way Slab) (clause not verified)"
        const val STAIRCASE_SHEAR = "ACI 318: Section 22.5 (Staircase Shear) (clause not verified)"
        const val STAIRCASE_DEFLECTION = "ACI 318: Table 24.2.2 (Staircase Deflection) (clause not verified)"

        // Flat slabs
        const val FLAT_SLAB_METHOD = "ACI 318: Section 8.10 (Direct Design Method) (clause not verified)"
        const val FLAT_SLAB_PUNCHING = "ACI 318: Section 22.6.5 (Punching Shear — Two-Way Shear) (clause not verified)"

        // Waffle and joist/hordi slabs
        const val WAFFLE_SLAB = "ACI 318: Chapter 8 + Section 9.8 (Two-Way Ribbed / Joist Construction) (clause not verified)"
        const val HORDI_JOIST_SLAB = "ACI 318: Section 9.8 (Joist Construction) (clause not verified)"

        // Strap footings
        const val STRAP_FOOTING = "ACI 318: Chapter 13 (Foundations — Strap/Ground Beams) (clause not verified)"

        // Shear walls — in-plane flexure
        const val SHEAR_WALL_FLEXURE = "ACI 318: Chapter 11 (Walls — In-Plane Forces) (clause not verified)"
    }
    
    // === الكود السعودي SBC 304 ===
    // Chapter structure verified against the official SBC 304 table of contents,
    // which follows ACI 318-14: Ch.7 One-Way Slabs, Ch.8 Two-Way Slabs, Ch.9 Beams,
    // Ch.10 Columns, Ch.11 Walls, Ch.12 Diaphragms, Ch.13 Foundations, Ch.15 Joints,
    // Ch.18 Earthquake-Resistant Structures, Ch.22 Sectional Strength,
    // Ch.24 Serviceability, Ch.25 Reinforcement Details.
    object SBC {
        // الأعمدة
        const val COLUMN_AXIAL = "SBC 304: Section 10 (clause not verified)"
        const val COLUMN_REINFORCEMENT_MIN = "SBC 304: Section 10.6.1 (ρmin = 1%) (clause not verified)"
        const val COLUMN_REINFORCEMENT_MAX = "SBC 304: Section 10.6.1 (ρmax = 8%) (clause not verified)"
        const val COLUMN_TIES = "SBC 304: Section 10.7.6 (clause not verified)"
        const val COLUMN_COVER = "SBC 304: Section 20.5.1 (Cover ≥ 40mm) (clause not verified)"
        
        // الكمرات
        const val BEAM_FLEXURE = "SBC 304: Section 9 / 22.2 (Flexure) (clause not verified)"
        const val BEAM_SHEAR = "SBC 304: Section 22.5 (One-Way Shear) (clause not verified)"
        const val BEAM_REINFORCEMENT_MIN = "SBC 304: Section 9.6.1 (clause not verified)"
        const val BEAM_REINFORCEMENT_MAX = "SBC 304: Section 9.3.3.1 (clause not verified)"
        const val BEAM_DEVELOPMENT_LENGTH = "SBC 304: Section 25.4 (Development of Reinforcement) (clause not verified)"
        const val BEAM_DOUBLY_REINFORCED = "SBC 304: Section 9.3.3.2 (clause not verified)"
        
        // البلاطات
        const val SLAB_ONE_WAY = "SBC 304: Chapter 7 (One-Way Slabs) (clause not verified)"
        const val SLAB_TWO_WAY = "SBC 304: Chapter 8 (Two-Way Slabs) (clause not verified)"
        const val SLAB_DEFLECTION = "SBC 304: Section 24.2 (Deflections) (clause not verified)"
        
        // القواعد
        const val FOOTING_BEARING = "SBC 304: Section 13.3 / 22.8 (Bearing) (clause not verified)"
        const val FOOTING_SHEAR_PUNCHING = "SBC 304: Section 22.6 (Two-Way Shear) (clause not verified)"
        const val FOOTING_REINFORCEMENT = "SBC 304: Section 13.3 (Foundations) (clause not verified)"
        
        // الزلازل
        const val SEISMIC_BASE_SHEAR = "SBC seismic method: governing seismic code not supplied, indicative only"
        const val SEISMIC_RESPONSE_SPECTRUM = "SBC seismic method: governing seismic code not supplied, indicative only"
        
        // الحوائط (السند والقِص)
        const val RW_LATERAL_EARTH = "SBC 304: Section 11.5 (Walls — Lateral Earth) (clause not verified)"
        const val RW_STABILITY = "SBC 304: Section 11.5 (Wall Stability) (clause not verified)"
        const val SW_SHEAR = "SBC 304: Section 11.5 (Wall Shear) (clause not verified)"
        
        // الأساسات الخازوقية والقواعد المشتركة
        const val PILE_CAPACITY = "SBC 304: Section 13.3 (Pile Caps) (clause not verified)"
        const val COMBINED_FOOTING = "SBC 304: Section 13.3 (Combined Footings) (clause not verified)"
        
        // الخزانات
        const val TANK_HYDROSTATIC = "SBC 304: Section 350 (Liquid-Containing Structures) (clause not verified)"
        
        // الهياكل المعدنية
        const val STEEL_TENSION_MEMBER = "SBC 306-2018: Chapter D (Tension Members) (clause not verified)"
        const val STEEL_BEAM_FLEXURE = "SBC 306-2018: Chapter F (Flexure) (clause not verified)"
        const val STEEL_BEAM_SHEAR = "SBC 306-2018: Chapter G (Shear) (clause not verified)"
        const val STEEL_COLUMN_COMPRESSION = "SBC 306-2018: Chapter E (Compression) (clause not verified)"
        const val STEEL_COMBINED_STRESS = "SBC 306-2018: Chapter H (Combined Forces) (clause not verified)"
        const val STEEL_DEFLECTION = "SBC 306-2018: Chapter L (Serviceability) (clause not verified)"
        const val STEEL_BASE_PLATE = "SBC 306-2018: Chapter J (Column Base Plates) (clause not verified)"
        const val STEEL_BOLTED_CONNECTION = "SBC 306-2018: Chapter J3 (Bolted Connections) (clause not verified)"
        const val STEEL_WELDED_CONNECTION = "SBC 306-2018: Chapter J2 (Welded Connections) (clause not verified)"
        const val STEEL_BLOCK_SHEAR = "SBC 306-2018: Section J4.3 (Block Shear) (clause not verified)"
        
        // الإطارات الخرسانية
        const val FRAME_FLEXURE = "SBC 304: Section 9 / 22.2 (Frame Members) (clause not verified)"
        const val FRAME_SHEAR = "SBC 304: Section 22.5 (Frame Members) (clause not verified)"

        // ── P4-4 (§9.38): بنود العناصر المتبقية — SBC 304 يتبع ترقيم ACI 318-14 ──
        // توليفات الأحمال
        const val LOAD_COMBINATION = "SBC 304: Section 5.3.1 (1.2D + 1.6L) (clause not verified)"

        // الأغطية الخرسانية (بيئة السعودية الحارة/المالحة)
        const val BEAM_COVER = "SBC 304: Section 20.5.1 (Beams: 40mm interior / 50mm exterior) (clause not verified)"
        const val SLAB_COVER = "SBC 304: Section 20.5.1 (Slabs: 30mm interior / 40mm exterior) (clause not verified)"

        // السماكات الدنيا والتسليح الأدنى للبلاطات
        const val SLAB_MIN_THICKNESS = "SBC 304: Tables 7.3.1.1 / 8.3.1.1 (Minimum Thickness) (clause not verified)"
        const val SLAB_MIN_STEEL = "SBC 304: Sections 7.6.1 / 24.4.3.2 (Shrinkage & Temperature) (clause not verified)"

        // السلالم (بلاطة مائلة أحادية الاتجاه)
        const val STAIRCASE_FLEXURE = "SBC 304: Chapter 7 (Staircase — Inclined One-Way Slab) (clause not verified)"
        const val STAIRCASE_SHEAR = "SBC 304: Section 22.5 (Staircase Shear) (clause not verified)"
        const val STAIRCASE_DEFLECTION = "SBC 304: Table 24.2.2 (Staircase Deflection) (clause not verified)"

        // البلاطات اللاكمرية
        const val FLAT_SLAB_METHOD = "SBC 304: Section 8.10 (Direct Design Method) (clause not verified)"
        const val FLAT_SLAB_PUNCHING = "SBC 304: Section 22.6.5 (Punching Shear — Two-Way Shear) (clause not verified)"

        // البلاطات المفرغة والهوردي
        const val WAFFLE_SLAB = "SBC 304: Chapter 8 + Section 9.8 (Two-Way Ribbed / Joist Construction) (clause not verified)"
        const val HORDI_JOIST_SLAB = "SBC 304: Section 9.8 (Joist Construction) (clause not verified)"

        // القواعد الشداد
        const val STRAP_FOOTING = "SBC 304: Chapter 13 (Foundations — Strap/Ground Beams) (clause not verified)"

        // حوائط القص — الانحناء داخل المستوى
        const val SHEAR_WALL_FLEXURE = "SBC 304: Chapter 11 (Walls — In-Plane Forces) (clause not verified)"
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
