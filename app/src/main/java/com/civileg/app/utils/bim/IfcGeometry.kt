package com.civileg.app.utils.bim

import com.civileg.app.utils.detailing.*
import com.civileg.app.utils.detailing.ColumnDetailingEngine.BarCoordinate
import com.civileg.core.calculations.entities.*
import java.util.UUID
import kotlin.math.*

// Dummy classes for IFC entities as they seem to be missing in the project
data class IFcRepresentationContext(val contextIdentifier: String, val contextType: String, val targetView: String, val precision: Double)
data class IFcReinforcingBar(val id: String, val x: Double, val y: Double, val diameter: Double, val name: String)
data class IFcExtrudedAreaSolid(val inputArea: IFcRectangleProfileDef, val depth: Double, val startPoint: IFcPoint, val extrusionDirection: IFcVector3D)
data class IFcRectangleProfileDef(val profileName: String, val xDim: Double, val yDim: Double, val cornerRadius: Double)
data class IFcPoint(val x: Double, val y: Double, val z: Double)
data class IFcVector3D(val x: Double, val y: Double, val z: Double)
data class IFcAxis2Placement(val point: IFcPoint, val direction: IFcVector3D)

class IfcGeometry {

    fun structuralDrawingToContext(drawing: StructuralDrawing): IFcRepresentationContext {
        return IFcRepresentationContext(
            contextIdentifier = "Model",
            contextType = "Model",
            targetView = "DEFAULT",
            precision = 0.001
        )
    }

    fun barCoordinateToIfcBar(coord: BarCoordinate): IFcReinforcingBar {
        return IFcReinforcingBar(
            id = UUID.randomUUID().toString(),
            x = coord.xMm,
            y = coord.yMm,
            diameter = if (coord.isCorner) 8.0 else 12.0,
            name = if (coord.isCorner) "CORNER_BAR" else "SIDE_BAR"
        )
    }

    fun extrudeAreaSolid(
        area: Double,
        height: Double,
        originX: Double = 0.0,
        originY: Double = 0.0,
        originZ: Double = 0.0,
        directionX: Double = 0.0,
        directionY: Double = 0.0,
        directionZ: Double = 1.0
    ): IFcExtrudedAreaSolid {
        return IFcExtrudedAreaSolid(
            inputArea = IFcRectangleProfileDef(
                profileName = "RECTANGLE",
                xDim = sqrt(area),
                yDim = sqrt(area) / 2.0,
                cornerRadius = 0.0
            ),
            depth = height,
            startPoint = IFcPoint(
                x = originX,
                y = originY,
                z = originZ
            ),
            extrusionDirection = IFcVector3D(
                x = directionX,
                y = directionY,
                z = directionZ
            )
        )
    }

    fun point2DToIfcPoint(x: Double, y: Double): IFcPoint {
        return IFcPoint(x = x, y = y, z = 0.0)
    }

    fun axis2PlacementToIfcPlacement(x: Double, y: Double, z: Double): IFcAxis2Placement {
        return IFcAxis2Placement(
            point = IFcPoint(x = x, y = y, z = z),
            direction = IFcVector3D(x = 0.0, y = 0.0, z = 1.0)
        )
    }
}
