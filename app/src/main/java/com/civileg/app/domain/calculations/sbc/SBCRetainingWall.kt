package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.aci.ACIRetainingWall
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode

class SBCRetainingWall : RetainingWallDesign {

    companion object {
        private const val COVER_INTERIOR = 40.0
        private const val COVER_EXTERIOR = 65.0
        private const val COVER_COASTAL = 75.0
        private const val MIN_STEEL_RATIO = 0.002
        private const val OT_FS_LIMIT = 1.5
        private const val SLIDE_FS_LIMIT = 1.5
    }

    private val aciEngine = ACIRetainingWall()

    override fun designRetainingWall(input: RetainingWallInput): RetainingWallResult {
        // Use ACI calculations as base with SBC modifications
        val aciResult = aciEngine.designRetainingWall(input)

        // SBC-specific: different safety factors for overturning
        val sbcChecks = aciResult.safetyChecks.map { check ->
            when {
                check.name == "OT FS" -> check.copy(
                    limit = OT_FS_LIMIT,
                    isSafe = aciResult.overturningFS >= OT_FS_LIMIT,
                    description = "SBC 304: FS=${"%.2f".format(aciResult.overturningFS)} >= ${OT_FS_LIMIT}"
                )
                check.name == "Sliding FS" -> check.copy(
                    limit = SLIDE_FS_LIMIT,
                    isSafe = aciResult.slidingFS >= SLIDE_FS_LIMIT,
                    description = "SBC 304: FS=${"%.2f".format(aciResult.slidingFS)} >= ${SLIDE_FS_LIMIT}"
                )
                else -> check
            }
        }

        val isSafe = sbcChecks.all { it.isSafe }

        val notes = mutableListOf(
            "SBC 304: Based on ACI 318 methodology with Saudi modifications",
            "Min \u03C1 = ${MIN_STEEL_RATIO} (hot/arid climate durability)",
            "Cover: ${COVER_EXTERIOR}mm exterior, ${COVER_INTERIOR}mm interior",
            "Coastal areas: ${COVER_COASTAL}mm cover recommended"
        )

        // SBC seismic consideration
        if (aciResult.overturningFS < 2.0) {
            notes.add("Consider seismic earth pressure increment (0.5\u00D7Ka) for seismic zones")
        }

        return aciResult.copy(
            isSafe = isSafe,
            designCode = DesignCode.SBC,
            safetyChecks = sbcChecks,
            codeNotes = notes
        )
    }
}