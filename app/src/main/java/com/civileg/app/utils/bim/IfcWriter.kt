package com.civileg.app.utils.bim

import com.civileg.app.utils.detailing.StructuralDrawing
import com.civileg.core.calculations.entities.DesignCode
import java.util.*
import java.io.File

object IfcWriter {

    private fun generateGlobalId(entityType: String): String {
        val uuid = UUID.randomUUID()
        return "IFCGLOBALID('$uuid')"
    }

    fun exportIfc(
        structuralDrawing: StructuralDrawing,
        projectName: String = "CivilEG Project"
    ): File {
        val writer = IfcWriterImpl()
        val stepContent = writer.write(structuralDrawing, projectName)
        val file = File(
            System.getProperty("java.io.tmpdir"),
            "civileg_ifc_${structuralDrawing.drawingId}.ifc"
        )
        file.writeText(stepContent, java.nio.charset.StandardCharsets.UTF_8)
        return file
    }
}

class IfcWriterImpl {

    fun write(structuralDrawing: StructuralDrawing, projectName: String): String {
        val lines = mutableListOf<String>()

        // HEADER section
        lines.add("# HEADER SECTION")
        lines.add("FILE_NAME('AC203');")

        val author = "CivilEG User"
        lines.add("OWNER_HISTORY{")
        lines.add("  #19=OwnerDeclaration{")
        lines.add("    #20=Agreement$,") // simplified
        lines.add("    #21=ApplicationInstance{(#22=AC203)},")
        lines.add("    #23=FormatsTrustingTarget((#24=AC203)),")
        lines.add("    #25=Authorization{}")
        lines.add("  },")
        lines.add("  #26=PersonAndOrganization(#27,#28),")
        lines.add("  #27=Person('CivilEG'),")
        lines.add("  #28=Organization('CivilEG'),")
        lines.add("  #29=Organization('CivilEG'),")
        lines.add("  #30=Organization('CivilEG'),")
        lines.add("},")

        // Simplified owner history end
        lines.add("}")

        lines.add("DATE_AND_TIME{")
        lines.add("  #32=Now},")
        lines.add("}")

        lines.add("BUILD('AC203')") // simplified

        // DATA section
        lines.add("")
        lines.add("# DATA root")
        lines.add("")

        // IfcProject
        val projectGlobalId = generateGlobalId("PROJECT")
        lines.add("") // separator
        lines.add("40=IFCPROJECT{")
        lines.add("    GlobalId = $projectGlobalId,")
        lines.add("    OwnerHistory = #26,")
        lines.add("    Name = \"$projectName\",")
        lines.add("    Description = \"CivilEG Generated Project\",")
        lines.add("    Units = .IFCMILIMETERS.,") // simplified
        lines.add("    ContextOfItems = #44,")
        lines.add("    CoordinateSpaceEditor = #45,")
        lines.add("    LayerAssignments = #46,")
        lines.add("    PresentationAssignments = #47,")
        lines.add("}")

        // IfcAxis2Placement (world coordinate origin)
        lines.add("")
        lines.add("44=IFCAXIS2PLACEMENT{3D,")
        lines.add("    Location = (#41),")
        lines.add("    RefDirection = (#42),")
        lines.add("    UnitDirection = (#43)")
        lines.add("}")

        // World origin point
        lines.add("41=IFCPOINT{")
        lines.add("    Coordinates = (0.0, 0.0, 0.0)")
        lines.add("}")

        // World X axis direction
        lines.add("42=IFCVECTOR{")
        lines.add("    magnitude = (1.0, 0.0, 0.0)")
        lines.add("}")

        // World Y axis direction (up)
        lines.add("43=IFCVECTOR{")
        lines.add("    magnitude = (0.0, 1.0, 0.0)")
        lines.add("}")

        // World Z axis direction
        lines.add("45=IFCVECTOR{")
        lines.add("    magnitude = (0.0, 0.0, 1.0)")
        lines.add("}")

        // IfcGeometricRepresentationContext
        lines.add("")
        lines.add("46=IFCGEOMETRICREPRESENTATIONCONTEXT{")
        lines.add("    ContextIdentifier = 'Model',")
        lines.add("    ContextType = 'Model',")
        lines.add("    TargetView = 'DEFAULT',")
        lines.add("    Precision = 0.001")
        lines.add("}")

        // IfcRepresentation
        lines.add("")
        lines.add("47=IFCREPRESENTATION{")
        lines.add("    RepresentationIdentifier = 'Body',")
        lines.add("    RepresentationType = 'Representation',")
        lines.add("    Items = #40")
        lines.add("}")

        // Add structural elements
        lines.add("")
        lines.add("# STRUCTURAL ELEMENTS")
        lines.add("")

        val structuralLines = when (structuralDrawing.elementType) {
            "BEAM" -> writeBeam(structuralDrawing)
            "COLUMN" -> writeColumn(structuralDrawing)
            "SLAB" -> writeSlab(structuralDrawing)
            "FOOTING" -> writeFooting(structuralDrawing)
            "TANK" -> writeTank(structuralDrawing)
            "STAIR" -> writeStair(structuralDrawing)
            "WALL" -> writeRetainingWall(structuralDrawing)
            "SEISMIC" -> writeSeismic(structuralDrawing)
            "STEEL_MEMBER" -> writeSteelMember(structuralDrawing)
            else -> writeGenericElement(structuralDrawing)
        }
        lines.addAll(structuralLines)

        // Close STEP file
        lines.add("")
        lines.add("END-ISO-10303-21;")

        return lines.joinToString("\n") + "\n"
    }

    private fun generateGlobalId(entityType: String): String {
        return "'${UUID.randomUUID()}'"
    }

    private fun writeBeam(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("#100=IFCBEAM(")
        lines.add("    ${generateGlobalId("BEAM")},")
        lines.add("    #26, \"Beam ${structuralDrawing.elementType}\", $, $, #110, #120, $")
        lines.add(");")
        return lines
    }

    private fun writeColumn(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("#200=IFCCOLUMN(")
        lines.add("    ${generateGlobalId("COLUMN")},")
        lines.add("    #26, \"Column ${structuralDrawing.elementType}\", $, $, #210, #220, $")
        lines.add(");")
        return lines
    }

    private fun writeSlab(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("SLAB_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("SLAB")},")
        lines.add("    Name = \"Slab ${structuralDrawing.elementType}\",")
        lines.add("    OverallHeight = ${formatDim(200.0)},")
        lines.add("    Length = ${formatDim(5000.0)},")
        lines.add("    Width = ${formatDim(5000.0)},")
        lines.add("}")
        return lines
    }

    private fun writeFooting(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("FOOTING_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("FOOTING")},")
        lines.add("    Name = \"Footing ${structuralDrawing.elementType}\",")
        lines.add("    OverallHeight = ${formatDim(500.0)},")
        lines.add("    Length = ${formatDim(2000.0)},")
        lines.add("    Width = ${formatDim(2000.0)},")
        lines.add("}")
        return lines
    }

    private fun writeTank(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("TANK_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("TANK")},")
        lines.add("    Name = \"Tank ${structuralDrawing.elementType}\",")
        lines.add("    OverallHeight = ${formatDim(3000.0)},")
        lines.add("    Diameter = ${formatDim(3000.0)},")
        lines.add("    Width = ${formatDim(3000.0)},")
        lines.add("}")
        return lines
    }

    private fun writeStair(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("STAIR_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("STAIR")},")
        lines.add("    Name = \"Stair ${structuralDrawing.elementType}\",")
        lines.add("    Rise = ${formatDim(200.0)},")
        lines.add("    Run = ${formatDim(3000.0)},")
        lines.add("}")
        return lines
    }

    private fun writeRetainingWall(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("RETAININGWALL_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("WALL")},")
        lines.add("    Name = \"Retaining Wall ${structuralDrawing.elementType}\",")
        lines.add("    OverallHeight = ${formatDim(3000.0)},")
        lines.add("    Length = ${formatDim(5000.0)},")
        lines.add("    Thickness = ${formatDim(300.0)},")
        lines.add("}")
        return lines
    }

    private fun writeSeismic(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("STRUCTURALCURVE_SWEEP_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("SEISMIC")},")
        lines.add("    Name = \"Seismic ${structuralDrawing.elementType}\",")
        lines.add("    AxisRatio = 1.0,")
        lines.add("}")
        return lines
    }

    private fun writeSteelMember(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("STEELMEMBER_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("STEEL_MEMBER")},")
        lines.add("    Name = \"Steel Member ${structuralDrawing.elementType}\",")
        lines.add("    Profile = \"IPE\",")
        lines.add("    Length = ${formatDim(6000.0)},")
        lines.add("}")
        return lines
    }

    private fun writeGenericElement(structuralDrawing: StructuralDrawing): List<String> {
        val lines = mutableListOf<String>()
        lines.add("ELEMENT_ENTITY{")
        lines.add("    GlobalId = ${generateGlobalId("ELEMENT")},")
        lines.add("    Name = \"${structuralDrawing.elementType}\"")
        lines.add("}")
        return lines
    }

    private fun formatDim(v: Double): String = String.format("%.0f", v)
}
