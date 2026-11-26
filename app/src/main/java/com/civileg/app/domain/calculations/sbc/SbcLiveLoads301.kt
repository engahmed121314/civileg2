package com.civileg.app.domain.calculations.sbc

/**
 * Minimum uniformly distributed live loads Lo from SBC 301-CR-2018 Table 4-1 (printed pages 30 to 32, PDF pages 48 to 50; printed = PDF - 18).
 * Attic rows (footnote-dependent) are also not listed. Only occupancies whose value is a single uniform number are listed. Rows that depend on a footnote (garages for trucks and buses, helipads,
 * roofs for special purposes, balconies) are NOT listed: the app asks for a user value there.
 * Section 4.3.2 (p.26): where partitions can be erected or rearranged, a partition live load of at least 0.75 kN/m2 is added.
 * NOT ASSESSED: live load reduction (Section 4.7), concentrated loads, roof live load Lr reduction (4.8).
 */
data class SbcLiveLoad(val id: String, val nameEn: String, val nameAr: String, val loKnM2: Double, val reductionNotPermitted: Boolean, val note: String = "")

object SbcLiveLoads301 {
    const val PARTITION_MIN_KN_M2 = 0.75   // 4.3.2

    val all: List<SbcLiveLoad> = listOf(
        SbcLiveLoad("office", "Offices", "مكاتب", 2.5, false),
        SbcLiveLoad("office_lobby_ground_corridor", "Lobbies and first-floor corridors (offices)", "ردهات وممرات الدور الأول", 5.0, false),
        SbcLiveLoad("office_corridor_upper", "Corridors above first floor (offices)", "ممرات الأدوار العليا", 4.0, false),
        SbcLiveLoad("assembly_fixed_seats", "Assembly areas, fixed seats", "قاعات تجمع بمقاعد ثابتة", 3.0, true),
        SbcLiveLoad("assembly_movable", "Assembly areas, movable seats", "قاعات تجمع بمقاعد متحركة", 5.0, true),
        SbcLiveLoad("assembly_lobby", "Assembly areas, lobbies", "ردهات قاعات التجمع", 5.0, true),
        SbcLiveLoad("assembly_other", "Assembly areas (other)", "أماكن تجمع أخرى", 5.0, true),
        SbcLiveLoad("dining", "Dining rooms and restaurants", "مطاعم وقاعات طعام", 5.0, true),
        SbcLiveLoad("mosque", "Mosques", "مساجد", 5.0, true),
        SbcLiveLoad("corridor_first_floor", "Corridors, first floor", "ممرات الدور الأرضي", 5.0, false),
        SbcLiveLoad("garage_passenger", "Garages, passenger vehicles only", "جراجات سيارات ركاب فقط", 2.0, true, "or 13.0 kN on 100 x 100 mm (footnote b)"),
        SbcLiveLoad("hospital_operating_lab", "Hospitals, operating rooms and laboratories", "غرف عمليات ومعامل مستشفيات", 3.0, false),
        SbcLiveLoad("hospital_patient", "Hospitals, patient rooms", "غرف مرضى", 2.0, false),
        SbcLiveLoad("hospital_corridor_upper", "Hospitals, corridors above first floor", "ممرات مستشفيات بالأدوار العليا", 4.0, false),
        SbcLiveLoad("library_reading", "Libraries, reading rooms", "قاعات قراءة", 3.0, false),
        SbcLiveLoad("library_stack", "Libraries, stack rooms", "مخازن كتب", 7.5, true, "footnote h limits apply"),
        SbcLiveLoad("manufacturing_light", "Manufacturing, light", "تصنيع خفيف", 6.0, true),
        SbcLiveLoad("manufacturing_heavy", "Manufacturing, heavy", "تصنيع ثقيل", 12.0, true),
        SbcLiveLoad("dwelling_other_areas", "One- and two-family dwellings, all other areas except stairs", "فيلات ومساكن عائلية، باقي المساحات", 2.0, false),
        SbcLiveLoad("residential_private", "Other residential, private rooms and corridors serving them", "سكني: غرف خاصة وممراتها", 2.0, false),
        SbcLiveLoad("residential_public", "Other residential, public rooms and corridors serving them", "سكني: غرف عامة وممراتها", 5.0, true),
        SbcLiveLoad("roof_ordinary", "Ordinary flat, pitched and curved roofs", "أسطح عادية", 1.0, false, "footnote n"),
        SbcLiveLoad("roof_garden", "Roofs used for roof gardens", "أسطح حدائق", 5.0, false),
        SbcLiveLoad("school_classroom", "Schools, classrooms", "فصول مدارس", 3.0, false),
        SbcLiveLoad("school_corridor_upper", "Schools, corridors above first floor", "ممرات مدارس بالأدوار العليا", 4.0, false),
        SbcLiveLoad("school_corridor_first", "Schools, first-floor corridors", "ممرات مدارس بالدور الأرضي", 5.0, false),
        SbcLiveLoad("stairs_exit", "Stairs and exit ways", "سلالم ومخارج", 5.0, false, "see stairs_dwelling for one- and two-family dwellings"),
        SbcLiveLoad("stairs_dwelling", "Stairs, one- and two-family dwellings only", "سلالم مسكن عائلي", 2.0, false),
        SbcLiveLoad("storage_light", "Storage warehouses, light", "مخازن خفيفة", 6.0, true, "design heavier if the anticipated storage is heavier"),
        SbcLiveLoad("storage_heavy", "Storage warehouses, heavy", "مخازن ثقيلة", 12.0, true),
        SbcLiveLoad("store_retail_first", "Stores, retail, first floor", "محلات تجزئة الدور الأول", 5.0, false),
        SbcLiveLoad("store_retail_upper", "Stores, retail, upper floors", "محلات تجزئة الأدوار العليا", 4.0, false),
        SbcLiveLoad("store_wholesale", "Stores, wholesale, all floors", "محلات جملة", 6.0, true),
        SbcLiveLoad("walkway", "Walkways and elevated platforms (other than exit ways)", "ممرات مشاة ومنصات مرتفعة", 3.0, false),
        SbcLiveLoad("yard_pedestrian", "Yards and terraces, pedestrian", "ساحات وتراسات مشاة", 5.0, true)
    )

    fun byId(id: String): SbcLiveLoad = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown Table 4-1 occupancy '$id'")

    /** Unit weight of reinforced concrete with stone aggregate, SBC 301-CR-2018 Table 3-2 (printed p.22): 24.0 kN/m3. */
    const val REINFORCED_CONCRETE_STONE_KN_M3 = 24.0
}
