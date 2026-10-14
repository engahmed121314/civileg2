package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode

/**
 * ComplianceMatrix
 *
 * Defines the checklist items required by each design code/standard for every discipline.
 * Used to generate compliance checklists and verify completeness of QC records.
 */
object ComplianceMatrix {

    data class ChecklistItem(
        val code: String,
        val description: String,
        val reference: String, // code clause reference
        val discipline: String, // CONCRETE, SOIL, REBAR, STEEL, ROADS, BRIDGES, TUNNELS, BUILDINGS
        val mandatory: Boolean = true
    )

    data class ComplianceStandard(
        val name: String,
        val code: String, // ECP, ACI, SBC, AISC, AASHTO, ASCE
        val version: String,
        val checklist: List<ChecklistItem>
    )

    private val ecpConcrete = listOf(
        ChecklistItem("ECP-CON-001", "Concrete grade verification (fcu)", "ECP 203 §6.3", "CONCRETE"),
        ChecklistItem("ECP-CON-002", "Slump test within limits", "ECP 203 §6.3", "CONCRETE"),
        ChecklistItem("ECP-CON-003", "7/28 day strength ratio ≥ 0.65", "ECP 203 §6.3", "CONCRETE"),
        ChecklistItem("ECP-CON-004", "Individual strength ≥ fcu - 3.5 MPa", "ECP 203 §6.3", "CONCRETE"),
        ChecklistItem("ECP-CON-005", "Cover thickness verification", "ECP 203 §4.2", "CONCRETE"),
        ChecklistItem("ECP-CON-006", "Formwork dimensional tolerances", "ECP 203 §8.2", "CONCRETE"),
        ChecklistItem("ECP-CON-007", "Rebar placement per drawings", "ECP 203 §4.2", "CONCRETE"),
        ChecklistItem("ECP-CON-008", "Concrete consolidation/vibration", "ECP 203 §6.4", "CONCRETE"),
        ChecklistItem("ECP-CON-009", "Curing method & duration", "ECP 203 §6.5", "CONCRETE"),
        ChecklistItem("ECP-CON-010", "Joint preparation & treatment", "ECP 203 §8.3", "CONCRETE")
    )

    private val aciConcrete = listOf(
        ChecklistItem("ACI-CON-001", "Concrete compressive strength f'c", "ACI 318 §26.12", "CONCRETE"),
        ChecklistItem("ACI-CON-002", "Slump within specified range", "ACI 318 §26.12.1", "CONCRETE"),
        ChecklistItem("ACI-CON-003", "Air content for freeze-thaw", "ACI 318 §26.12.2", "CONCRETE"),
        ChecklistItem("ACI-CON-004", "Strength acceptance criteria", "ACI 318 §26.12.3", "CONCRETE"),
        ChecklistItem("ACI-CON-005", "Cover requirements", "ACI 318 §20.5", "CONCRETE"),
        ChecklistItem("ACI-CON-006", "Reinforcement placement tolerances", "ACI 318 §20.5", "CONCRETE"),
        ChecklistItem("ACI-CON-007", "Consolidation", "ACI 318 §26.5", "CONCRETE"),
        ChecklistItem("ACI-CON-008", "Curing", "ACI 318 §26.5", "CONCRETE")
    )

    private val ecpRebar = listOf(
        ChecklistItem("ECP-REB-001", "Bar grade & diameter verification", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-002", "Minimum reinforcement ratio ρmin", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-003", "Maximum reinforcement ratio ρmax", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-004", "Cover thickness", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-005", "Clear spacing between bars", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-006", "Lap splice length", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-007", "Development length", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-008", "Anchorage & hooks", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-009", "Stirrup spacing", "ECP 203 §4.2", "REBAR"),
        ChecklistItem("ECP-REB-010", "Bar bending & placement per drawings", "ECP 203 §4.2", "REBAR")
    )

    private val ecpSteel = listOf(
        ChecklistItem("ECP-STL-001", "Steel grade verification", "ECP 205", "STEEL"),
        ChecklistItem("ECP-STL-002", "Section dimensions & tolerances", "ECP 205", "STEEL"),
        ChecklistItem("ECP-STL-003", "Weld quality (visual/NDT)", "ECP 205", "STEEL"),
        ChecklistItem("ECP-STL-004", "Bolt torque verification", "ECP 205", "STEEL"),
        ChecklistItem("ECP-STL-005", "Paint/coating thickness", "ECP 205", "STEEL"),
        ChecklistItem("ECP-STL-006", "Alignment & plumbness", "ECP 205", "STEEL"),
        ChecklistItem("ECP-STL-007", "Connection detailing per drawings", "ECP 205", "STEEL")
    )

    private val aiscSteel = listOf(
        ChecklistItem("AISC-STL-001", "Material certification (MTR)", "AISC 360 §A3", "STEEL"),
        ChecklistItem("AISC-STL-002", "Section classification (compact/noncompact)", "AISC 360 §B4", "STEEL"),
        ChecklistItem("AISC-STL-003", "Weld procedure qualification (WPS/PQR)", "AISC 360 §J2", "STEEL"),
        ChecklistItem("AISC-STL-004", "Bolt installation (snug-tight/pretensioned)", "AISC 360 §J3", "STEEL"),
        ChecklistItem("AISC-STL-005", "Coating system (thickness/adhesion)", "AISC 360", "STEEL"),
        ChecklistItem("AISC-STL-006", "Member tolerances (camber/sweep)", "AISC 360 §6.4", "STEEL"),
        ChecklistItem("AISC-STL-007", "Connection design per drawings", "AISC 360", "STEEL")
    )

    private val soilChecks = listOf(
        ChecklistItem("SOIL-001", "Bearing capacity calculation method", "ECP 202 / AASHTO", "SOIL"),
        ChecklistItem("SOIL-002", "Soil investigation report review", "ECP 202", "SOIL"),
        ChecklistItem("SOIL-003", "Water table level verification", "ECP 202", "SOIL"),
        ChecklistItem("SOIL-004", "Settlement analysis", "ECP 202", "SOIL"),
        ChecklistItem("SOIL-005", "Factor of safety ≥ 3.0", "ECP 202", "SOIL"),
        ChecklistItem("SOIL-006", "Excavation shoring design", "ECP 202", "SOIL"),
        ChecklistItem("SOIL-007", "Dewatering system if needed", "ECP 202", "SOIL"),
        ChecklistItem("SOIL-008", "Backfill material & compaction", "ECP 202", "SOIL")
    )

    private val roadsChecks = listOf(
        ChecklistItem("RD-001", "Subgrade CBR verification", "AASHTO / Egyptian Roads", "ROADS"),
        ChecklistItem("RD-002", "Subbase thickness & compaction", "Egyptian Roads Specs", "ROADS"),
        ChecklistItem("RD-003", "Base course thickness & compaction", "Egyptian Roads Specs", "ROADS"),
        ChecklistItem("RD-004", "Surface course thickness", "Egyptian Roads Specs", "ROADS"),
        ChecklistItem("RD-005", "Asphalt mix design approval", "Egyptian Roads Specs", "ROADS"),
        ChecklistItem("RD-006", "Marshall stability & flow", "ASTM D6927", "ROADS"),
        ChecklistItem("RD-007", "Air voids 3-5%", "ASTM D3203", "ROADS"),
        ChecklistItem("RD-008", "Field density ≥ 95% MDD", "Egyptian Roads Specs", "ROADS"),
        ChecklistItem("RD-009", "Surface smoothness (IRI)", "Egyptian Roads Specs", "ROADS"),
        ChecklistItem("RD-010", "Joint sealing", "Egyptian Roads Specs", "ROADS")
    )

    private val bridgesChecks = listOf(
        ChecklistItem("BR-001", "Concrete class ≥ 30 MPa", "ECP 203 / AASHTO", "BRIDGES"),
        ChecklistItem("BR-002", "Post-tensioning ducts & grouting", "ECP 203 / AASHTO", "BRIDGES"),
        ChecklistItem("BR-003", "Bearing installation & alignment", "AASHTO", "BRIDGES"),
        ChecklistItem("BR-004", "Expansion joint installation", "AASHTO", "BRIDGES"),
        ChecklistItem("BR-005", "Deflection control L/250", "ECP 203 / AASHTO", "BRIDGES"),
        ChecklistItem("BR-006", "Natural frequency > 2 Hz", "AASHTO", "BRIDGES"),
        ChecklistItem("BR-007", "Scour protection", "AASHTO", "BRIDGES"),
        ChecklistItem("BR-008", "Waterproofing membrane", "ECP 203", "BRIDGES")
    )

    private val tunnelsChecks = listOf(
        ChecklistItem("TN-001", "Rock mass classification (RMR/Q)", "ISRM / Bieniawski", "TUNNELS"),
        ChecklistItem("TN-002", "Convergence monitoring", "Convergence-Confinement", "TUNNELS"),
        ChecklistItem("TN-003", "Shotcrete thickness & strength", "NFPA 502 / AASHTO", "TUNNELS"),
        ChecklistItem("TN-004", "Rock bolt/anchor installation", "ISRM", "TUNNELS"),
        ChecklistItem("TN-005", "Waterproofing membrane", "AASHTO / NFPA", "TUNNELS"),
        ChecklistItem("TN-006", "Ventilation system testing", "NFPA 502", "TUNNELS"),
        ChecklistItem("TN-007", "Fire life safety systems", "NFPA 502", "TUNNELS")
    )

    private val buildingsChecks = listOf(
        ChecklistItem("BD-001", "Seismic design category", "ASCE 7 / ECP 201", "BUILDINGS"),
        ChecklistItem("BD-002", "Story drift ≤ 2%", "ASCE 7 Table 12.12-1", "BUILDINGS"),
        ChecklistItem("BD-003", "Base shear verification", "ASCE 7 §12.8", "BUILDINGS"),
        ChecklistItem("BD-004", "P-delta effects check", "ASCE 7 §12.8.7", "BUILDINGS"),
        ChecklistItem("BD-005", "Foundation settlement", "ECP 202 / ASCE 7", "BUILDINGS"),
        ChecklistItem("BD-006", "Diaphragm design", "ASCE 7 §12.10", "BUILDINGS"),
        ChecklistItem("BD-007", "Collector elements", "ASCE 7 §12.10", "BUILDINGS"),
        ChecklistItem("BD-008", "Non-structural component bracing", "ASCE 7 §13.1", "BUILDINGS"),
        ChecklistItem("BD-009", "Importance factor Ie", "ASCE 7 Table 1.5-2", "BUILDINGS"),
        ChecklistItem("BD-010", "Redundancy factor ρ", "ASCE 7 §12.3.4", "BUILDINGS")
    )

    val standards: Map<String, ComplianceStandard> = mapOf(
        "ECP" to ComplianceStandard("Egyptian Code of Practice", "ECP", "203-2020", ecpConcrete + ecpRebar + ecpSteel + soilChecks),
        "ACI" to ComplianceStandard("ACI 318-19", "ACI", "318-19", aciConcrete + ecpRebar + aiscSteel + soilChecks),
        "SBC" to ComplianceStandard("Saudi Building Code", "SBC", "304-2018", ecpConcrete + ecpRebar + ecpSteel + soilChecks),
        "AISC" to ComplianceStandard("AISC 360-16", "AISC", "360-16", aiscSteel),
        "AASHTO" to ComplianceStandard("AASHTO LRFD", "AASHTO", "8th Ed.", roadsChecks + bridgesChecks),
        "ASCE" to ComplianceStandard("ASCE 7-16", "ASCE", "7-16", buildingsChecks),
        "TUNNEL" to ComplianceStandard("Tunnel Standards", "TUNNEL", "NFPA 502 / ISRM", tunnelsChecks)
    )

    fun getChecklist(designCode: String, discipline: String? = null): List<ChecklistItem> {
        val standard = standards[designCode.uppercase()] ?: standards["ECP"]!!
        return standard.checklist.filter { discipline == null || it.discipline == discipline }
    }

    fun getStandards(): List<ComplianceStandard> = standards.values.toList()
}