package com.civileg.app.utils

import com.civileg.app.db.InspectionTestPlan

/**
 * Standard Inspection & Test Plan (ITP) templates per discipline.
 *
 * Provides pre-defined inspection points aligned with ECP / ACI / SBC
 * acceptance criteria so the quality engineer can load a baseline ITP and
 * start tracking results immediately.
 */
object ItpTemplates {

    data class ItpRow(
        val discipline: String,
        val activity: String,
        val inspectionItem: String,
        val referenceCode: String,
        val acceptanceCriteria: String,
        val frequency: String,
        val responsibleParty: String
    )

    val TEMPLATES: List<ItpRow> = listOf(
        ItpRow("CONCRETE", "Ready mix delivery", "Slump test", "ACI 318 §26.12.1 / ECP 203 §6.3", "Slump within code range", "Each truck", "QC Engineer"),
        ItpRow("CONCRETE", "Concrete placement", "28-day compressive strength", "ACI 318 Table 26.12.3.1", "≥ f'cu and ≥ f'cu - 3.5 MPa", "Per element", "QC Engineer"),
        ItpRow("SOIL", "Soil bearing", "Allowable bearing capacity", "Terzaghi / Meyerhof", "q_allow ≥ applied pressure", "Per foundation", "Geotech"),
        ItpRow("SOIL", "Compaction", "Field density (sand cone)", "AASHTO T191", "≥ 95% MDD", "Per 250 m²", "QC Engineer"),
        ItpRow("REBAR", "Fixing", "Bar diameter & spacing", "ACI 318 §25", "Spacing ≥ 3db and ≤ max allowed", "Per element", "QC Engineer"),
        ItpRow("REBAR", "Cover", "Concrete cover verification", "ACI 318 §20.6", "Cover ≥ minimum per exposure", "Per element", "QC Engineer"),
        ItpRow("STEEL", "Erection", "Member alignment & plumb", "AISC 303", "Within erection tolerances", "Per member", "Supervisor"),
        ItpRow("STEEL", "Connections", "High-strength bolt torque", "AISC Specification §J3", "Snug-tight / pretension as per loads", "Per connection", "QC Engineer"),
        ItpRow("ROADS", "Pavement", "Asphalt density & air voids", "ASTM D2041 / D2726", "≥ 97% Marshall density", "Per lot", "Lab"),
        ItpRow("ROADS", "Layer thickness", "Surface/base/subbase thickness", "AASHTO M147", "Within ± 5 mm", "Per 500 m", "Supervisor"),
        ItpRow("BRIDGES", "Superstructure", "Deck concrete strength", "ACI 318 §26.12", "≥ f'c", "Per deck pour", "QC Engineer"),
        ItpRow("BRIDGES", "Bearings", "Bearing installation", "AASHTO LRFD §14", "Level, centred, free to move", "Each bearing", "Supervisor"),
        ItpRow("TUNNELS", "Excavation", "Convergence monitoring", "ECP 202", "Convergence ≤ allowable", "Per 10 m", "Surveyor"),
        ItpRow("TUNNELS", "Lining", "Shotcrete / lining thickness", "ACI 506", "Thickness ≥ design", "Per 10 m", "QC Engineer"),
        ItpRow("BUILDINGS", "Structure", "Drift / deflection check", "IBC / SBC 301", "Interstory drift ≤ allow", "Per storey", "Structural Eng."),
        ItpRow("BUILDINGS", "Fire", "Fire-stopping & ratings", "SBC 801", "Rated assemblies verified", "Per floor", "Fire Eng."),
        ItpRow("EARTHWORKS", "Excavation", "Depth & slope stability", "AASHTO T99", "Slope ≥ safe minimum", "Per 10 m", "QC Engineer"),
        ItpRow("EARTHWORKS", "Backfill", "Compaction & moisture", "AASHTO T99 / ASTM D698", "≥ 95% MDD, OMC ± 2%", "Per 250 m²", "QC Engineer"),
        ItpRow("PILING", "Driving", "Pile driving record", "AASHTO §10.7", "Set / length per design", "Each pile", "QC Engineer"),
        ItpRow("PILING", "Integrity", "Pile integrity test (PIT)", "ASTM D5882", "No major defects", "Per pile", "Geotech"),
        ItpRow("RETAINING_WALLS", "Construction", "Wall alignment & joints", "ACI 318", "FS overturning ≥ 1.5", "Per 10 m", "QC Engineer"),
        ItpRow("RETAINING_WALLS", "Drainage", "Weep holes & backfill drain", "AASHTO §11", "Present and clear", "Per 10 m", "Supervisor"),
        ItpRow("WATERPROOFING", "Application", "Membrane thickness & overlap", "ASTM D4068 / BS 8102", "Thickness ≥ 1.5 mm, overlap ≥ 100 mm", "Per roll / area", "QC Engineer"),
        ItpRow("WATERPROOFING", "Testing", "Ponding / leak test", "ASTM D4068", "No leakage after 48 h", "Per area", "QC Engineer"),
        ItpRow("MASONRY", "Blockwork", "Verticality & flatness", "ASTM C926", "≤ 5 mm/m and ≤ 3 mm/m", "Per 10 m²", "QC Engineer"),
        ItpRow("MASONRY", "Plaster", "Adhesion & curing", "ASTM C926 / ECP 204", "Bond ≥ 0.3 MPa, cured ≥ 7 days", "Per 50 m²", "QC Engineer"),
        ItpRow("FLOORING", "Screed", "Flatness & levelness", "ASTM E1155 / ACI 302", "≤ 3 mm/2m, ± 10 mm level", "Per 100 m²", "QC Engineer"),
        ItpRow("FLOORING", "Joints", "Joint spacing & sealing", "ACI 302", "Spacing ≤ 6 m, width 6 ± 2 mm", "Per area", "QC Engineer"),
        ItpRow("PRECAST", "Production", "Element dimensions & cover", "ACI 318 §17 / PCI", "Within ± 5 mm, cover ≥ 40 mm", "Per element", "Factory QC"),
        ItpRow("PRECAST", "Erection", "Tolerances & connections", "PCI / ACI 318", "Erection tolerance ≤ ±10 mm", "Per element", "QC Engineer"),
        ItpRow("POST_TENSION", "Tensioning", "Elongation & jacking force", "ACI 318 §20 / PTI", "Elongation within ± 5%", "Each tendon", "QC Engineer"),
        ItpRow("POST_TENSION", "Grouting", "Grout strength & flow", "ACI 318 §20 / ASTM C939", "≥ 30 MPa, flow ≥ 25 sec", "Each duct", "QC Engineer"),
        ItpRow("GROUTING", "Injection", "Injection pressure & volume", "ACI 304", "0.2 - 0.5 MPa, full volume", "Each hole", "QC Engineer"),
        ItpRow("GROUTING", "Testing", "Bond strength", "ACI 304", "Bond ≥ 1.5 MPa", "Per 50 m²", "Lab"),
        ItpRow("SOIL_IMPROVEMENT", "Treatment", "SPT before / after", "FHWA", "Improvement ≥ 50%", "Per grid", "Geotech"),
        ItpRow("SOIL_IMPROVEMENT", "Verification", "Settlement after treatment", "AASHTO", "≤ 25 mm", "Per area", "Geotech"),
        ItpRow("WATER_TANKS", "Construction", "Wall / floor thickness", "ACI 350", "Wall ≥ 200 mm, floor ≥ 150 mm", "Per element", "QC Engineer"),
        ItpRow("WATER_TANKS", "Testing", "Hydrostatic / leak test", "ACI 350 / AWWA D100", "Water-tight at 1.5× working head", "Once", "QC Engineer"),
        ItpRow("DRAINAGE", "Pipe laying", "Slope & invert level", "ECP 204", "Slope ≥ 1%, invert ± 20 mm", "Per 50 m", "Surveyor"),
        ItpRow("DRAINAGE", "Testing", "Leak / air test", "ASTM C923", "No leakage", "Per section", "QC Engineer"),
        ItpRow("PAINTING", "Surface prep", "Cleanliness & profile", "SSPC-SP6", "Sa 2.5 / SP6 verified", "Per area", "QC Engineer"),
        ItpRow("PAINTING", "Coating", "DFT & adhesion", "ASTM D3359", "DFT per spec, adhesion ≥ 1.5 MPa", "Per 50 m²", "QC Engineer"),
        ItpRow("SITE_WORKS", "Grading", "Levels & slope", "Project specs", "Within ± 50 mm", "Per 500 m²", "Surveyor"),
        ItpRow("SITE_WORKS", "Utilities", "Drainage & safety systems", "Project specs", "Installed and functional", "Per area", "Supervisor"),
        ItpRow("FIRE_PROTECTION", "Coating", "Spray-applied thickness", "ASTM E119 / ACI 216", "Thickness per fire rating", "Per area", "QC Engineer"),
        ItpRow("FIRE_PROTECTION", "Verification", "Adhesion & coverage", "ASTM E119", "Adhesion ≥ 1.0 MPa", "Per floor", "QC Engineer")
    )

    fun toPlan(rows: List<ItpRow>): List<InspectionTestPlan> =
        rows.map { r ->
            InspectionTestPlan(
                discipline = r.discipline,
                activity = r.activity,
                inspectionItem = r.inspectionItem,
                referenceCode = r.referenceCode,
                acceptanceCriteria = r.acceptanceCriteria,
                frequency = r.frequency,
                responsibleParty = r.responsibleParty
            )
        }

    /** Full default ITP covering every discipline. */
    fun defaultTemplate(): List<InspectionTestPlan> = toPlan(TEMPLATES)

    /** ITP rows for a single discipline (case-insensitive). */
    fun templateFor(discipline: String): List<InspectionTestPlan> =
        toPlan(TEMPLATES.filter { it.discipline.equals(discipline.trim(), ignoreCase = true) })

    /** Distinct disciplines covered by the standard template. */
    fun coveredDisciplines(): Set<String> = TEMPLATES.map { it.discipline }.toSet()
}