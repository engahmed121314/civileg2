package com.civileg.app.utils.bim

import com.civileg.core.calculations.entities.CodeReference
import java.util.UUID

class IfcRebar(private val codeRef: CodeReference) {

    fun toIfcReinforcingBar(mark: String, diameterMm: Double, lengthMm: Double): String {
        val codeStr = "ECP§5"
        val uuid = UUID.randomUUID().toString()
        return "IFCREINFORCINGBAR(\n" +
            "    GlobalId = IFCGLOBALID('$uuid'),\n" +
            "    Mark = \"$mark\",\n" +
            "    Name = \"Bar Ø${diameterMm.toInt()}\",\n" +
            "    Description = \"$codeStr\",\n" +
            "    NominalDiameter = $diameterMm,\n" +
            "    NominalLength = $lengthMm,\n" +
            "    Status = .ACTIVE.\n" +
            ")"
    }

    fun toIfcReinforcingBar(layout: com.civileg.app.utils.detailing.ColumnDetailingEngine.ColumnRebarLayout): String {
        val bars = mutableListOf<String>()
        val cornerBars = layout.cornerBars
        val sideBars = layout.sideBars

        cornerBars.forEach { _ ->
            bars.add(toIfcReinforcingBar(
                mark = "C-CORNER",
                diameterMm = 8.0,
                lengthMm = 3000.0
            ))
        }

        sideBars.forEach { _ ->
            bars.add(toIfcReinforcingBar(
                mark = "C-SIDE",
                diameterMm = 8.0,
                lengthMm = 3000.0
            ))
        }

        return bars.joinToString("\n  // ")
    }
}
