package com.civileg.app.utils

import com.civileg.app.utils.CalculatorEngine.*
import java.util.*

/**
 * DXF Export Engine for CAD
 */
object DxfExportEngine {
    fun exportColumnToDxf(result: ColumnResult, fcu: Double, fy: Double): String {
        return "0\nSECTION\n2\nHEADER\n0\nENDSEC\n0\nEOF"
    }
    fun exportBeamToDxf(result: BeamResult, fcu: Double, fy: Double): String {
        return "0\nSECTION\n2\nHEADER\n0\nENDSEC\n0\nEOF"
    }
    fun exportSlabToDxf(result: SlabResult, fcu: Double, fy: Double): String {
        return "0\nSECTION\n2\nHEADER\n0\nENDSEC\n0\nEOF"
    }
    fun exportRetainingWallToDxf(result: RetainingWallResult, fcu: Double, fy: Double): String {
        return "0\nSECTION\n2\nHEADER\n0\nENDSEC\n0\nEOF"
    }
}
