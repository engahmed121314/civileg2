package com.civileg.app.ui.compose.components.drawings

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.civileg.app.domain.entities.SteelWarehouseAnalysisResult
import com.civileg.app.domain.entities.SteelWarehouseInputs
import java.util.Locale
import kotlin.math.*

@Composable
fun ProfessionalWarehouseDrawing(
    inputs: SteelWarehouseInputs,
    result: SteelWarehouseAnalysisResult,
    viewMode: Int = 0, // 0: Front Elevation, 1: Plan View, 2: Side Elevation, 3: 3D
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().fillMaxSize()) {
        when (viewMode) {
            0 -> drawWarehouseFront(inputs, result)
            1 -> drawWarehousePlan(inputs, result)
            2 -> drawWarehouseSide(inputs, result)
            3 -> drawWarehouse3D(inputs, result)
        }
    }
}

private fun DrawScope.drawWarehouseFront(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    val w = size.width
    val h = size.height
    val pad = 60f
    
    val span = max(inputs.span, 1.0)
    val eaveH = max(inputs.eaveHeight, 1.0)
    val ridgeH = if (inputs.ridgeHeight > eaveH) inputs.ridgeHeight else (eaveH + span * inputs.slope)
    
    val scaleX = (w - 2 * pad) / span.toFloat()
    val scaleY = (h - 2 * pad) / ridgeH.toFloat()
    val scale = min(scaleX, scaleY)
    
    val drawSpan = span.toFloat() * scale
    val drawEave = eaveH.toFloat() * scale
    val drawRidge = ridgeH.toFloat() * scale
    
    val x1 = pad
    val x2 = pad + drawSpan
    val xMid = pad + drawSpan / 2
    val yBase = h - pad
    val yEave = yBase - drawEave
    val yRidge = yBase - drawRidge
    
    // Draw Frame
    val mainColor = Color.White
    val framePath = Path().apply {
        moveTo(x1, yBase)
        lineTo(x1, yEave)
        lineTo(xMid, yRidge)
        lineTo(x2, yEave)
        lineTo(x2, yBase)
    }
    drawPath(framePath, mainColor, style = Stroke(3f))
    
    // Multi-Story / Mezzanine Floor Beam
    if (inputs.numberOfStories == 2) {
        val yFloor = yBase - drawEave / 2
        drawLine(Color.Green, Offset(x1, yFloor), Offset(x2, yFloor), 3f)
        drawWarehouseText("Floor Beam: ${result.mainFrame.floorBeamSection?.sectionName ?: "IPE 300"}", xMid, yFloor - 10f, Color.Green.copy(alpha = 0.9f), 11f, center = true)
        
        // Floor Bracings
        drawLine(Color.Yellow.copy(alpha = 0.6f), Offset(x1, yBase), Offset(x1 + drawSpan/4, yFloor), 1.5f)
        drawLine(Color.Yellow.copy(alpha = 0.6f), Offset(x2, yBase), Offset(x2 - drawSpan/4, yFloor), 1.5f)
    }

    // Draw Detailed Base Plates with Bolt Callouts
    val tp = result.mainFrame.basePlateThickness
    val bolts = result.mainFrame.basePlateBoltsCount
    
    // Left Base Plate
    drawRect(Color.Yellow, Offset(x1 - 15f, yBase - 4f), Size(30f, 8f))
    drawCircle(Color.Red, radius = 3f, center = Offset(x1 - 8f, yBase))
    drawCircle(Color.Red, radius = 3f, center = Offset(x1 + 8f, yBase))
    
    // Right Base Plate
    drawRect(Color.Yellow, Offset(x2 - 15f, yBase - 4f), Size(30f, 8f))
    drawCircle(Color.Red, radius = 3f, center = Offset(x2 - 8f, yBase))
    drawCircle(Color.Red, radius = 3f, center = Offset(x2 + 8f, yBase))
    
    // Base Plate text callouts
    drawWarehouseText("Base Plate: tp=${tp.toInt()}mm, Bolts: ${bolts}xM24", x1 + 20f, yBase - 10f, Color.Yellow, 10f)
    
    // Roof Bracing (Schematic in front view)
    drawLine(Color(0xFFFFA500).copy(alpha = 0.5f), Offset(x1, yEave), Offset(xMid, yRidge), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
    drawLine(Color(0xFFFFA500).copy(alpha = 0.5f), Offset(x2, yEave), Offset(xMid, yRidge), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))

    // Draw Purlins in Front View (Schematic dots/small rects)
    val numPurlinEachSide = 4
    for (i in 0..numPurlinEachSide) {
        val f = i.toFloat() / numPurlinEachSide
        // Left side
        val pxL = x1 + (xMid - x1) * f
        val pyL = yEave + (yRidge - yEave) * f
        drawRect(Color.Gray, Offset(pxL - 4f, pyL - 8f), Size(8f, 8f))
        
        // Right side
        val pxR = xMid + (x2 - xMid) * f
        val pyR = yRidge + (yEave - yRidge) * f
        drawRect(Color.Gray, Offset(pxR - 4f, pyR - 8f), Size(8f, 8f))
    }
    
    // Draw Ground
    drawLine(Color.Gray, Offset(0f, yBase), Offset(w, yBase), 2f)
    
    // Annotations
    drawWarehouseText("${span}m", xMid, yBase + 35f, Color.Cyan, 14f, center = true)
    drawWarehouseText("${eaveH}m", x1 - 25f, yEave + drawEave / 2, Color.Cyan, 12f, center = true, rotation = -90f)
    
    // Member Labels
    drawWarehouseText("Column: ${result.mainFrame.columnSection.sectionName}", x1 + 10f, yBase - 20f, Color.White.copy(alpha = 0.7f), 10f)
    drawWarehouseText("Rafter: ${result.mainFrame.rafterSection.sectionName}", xMid - 50f, yRidge + 30f, Color.White.copy(alpha = 0.7f), 10f)
    
    // Labels
    drawWarehouseText("FRONT ELEVATION", w/2, pad/2, Color.White, 16f, center = true, bold = true)
}

private fun DrawScope.drawWarehousePlan(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    val w = size.width
    val h = size.height
    val pad = 60f
    
    val length = inputs.length
    val span = inputs.span
    val bay = inputs.baySpacing
    
    val scale = min((w - 2 * pad) / length.toFloat(), (h - 2 * pad) / span.toFloat())
    val drawL = length.toFloat() * scale
    val drawS = span.toFloat() * scale
    val drawB = bay.toFloat() * scale
    
    val xStart = (w - drawL) / 2
    val yStart = (h - drawS) / 2
    
    // Boundary
    drawRect(Color.Gray.copy(alpha = 0.3f), Offset(xStart, yStart), Size(drawL, drawS))
    drawRect(Color.White, Offset(xStart, yStart), Size(drawL, drawS), style = Stroke(2f))
    
    // Bays
    val numBays = (length / bay).toInt()
    for (i in 0..numBays) {
        val x = xStart + i * drawB
        drawLine(Color.White.copy(alpha = 0.6f), Offset(x, yStart), Offset(x, yStart + drawS), 1.5f)
    }
    
    // Purlins (Schematic)
    val numPurlins = 6
    for (j in 0..numPurlins) {
        val y = yStart + j * (drawS / numPurlins)
        drawLine(Color.DarkGray, Offset(xStart, y), Offset(xStart + drawL, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    }
    
    drawWarehouseText("PLAN VIEW", w/2, pad/2, Color.White, 16f, center = true, bold = true)
}

private fun DrawScope.drawWarehouseSide(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    val w = size.width
    val h = size.height
    val pad = 60f
    
    val length = max(inputs.length, 1.0)
    val eaveH = max(inputs.eaveHeight, 1.0)
    val bay = max(inputs.baySpacing, 1.0)
    
    val scaleX = (w - 2 * pad) / length.toFloat()
    val scaleY = (h - 2 * pad) / eaveH.toFloat()
    val scale = min(scaleX, scaleY)
    val drawL = length.toFloat() * scale
    val drawE = eaveH.toFloat() * scale
    val drawB = bay.toFloat() * scale
    
    val xStart = pad
    val yBase = h - pad
    val yEave = yBase - drawE

    // Columns
    val numBays = (length / bay).toInt()
    for (i in 0..numBays) {
        val x = xStart + i * drawB
        drawLine(Color.White, Offset(x, yBase), Offset(x, yEave), 2f)
    }
    
    // Eave and Base lines
    drawLine(Color.White, Offset(xStart, yEave), Offset(xStart + drawL, yEave), 3f)
    
    // Side Girts (Schematic)
    val numGirts = 3
    for (j in 1..numGirts) {
        val y = yBase - j * (drawE / (numGirts + 1))
        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(xStart, y), Offset(xStart + drawL, y), 1f)
    }
    
    drawLine(Color.Gray, Offset(0f, yBase), Offset(w, yBase), 2f)
    
    // Bracing (X-Bracing in first and last bay)
    val bays = if (numBays >= 1) listOf(0, numBays - 1) else listOf(0)
    bays.forEach { i ->
        val x1 = xStart + i * drawB
        val x2 = xStart + (i + 1) * drawB
        drawLine(Color.Yellow.copy(alpha = 0.4f), Offset(x1, yBase), Offset(x2, yEave), 1.5f)
        drawLine(Color.Yellow.copy(alpha = 0.4f), Offset(x2, yBase), Offset(x1, yEave), 1.5f)
    }
    
    // Annotations
    drawWarehouseText("${length}m Total", xStart + drawL / 2, yBase + 30f, Color.Cyan, 12f, center = true)
    drawWarehouseText("${bay}m Bays", xStart + drawB / 2, yBase + 50f, Color.Cyan, 10f, center = true)
    
    drawWarehouseText("SIDE ELEVATION", w/2, pad/2, Color.White, 16f, center = true, bold = true)
}

private fun DrawScope.drawWarehouse3D(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    val w = size.width
    val h = size.height
    
    val centerX = w / 2
    val centerY = h / 2 + 80f
    val eaveH = max(inputs.eaveHeight, 1.0)
    val ridgeH = if (inputs.ridgeHeight > eaveH) inputs.ridgeHeight else (eaveH + inputs.span * inputs.slope)
    val scale = min(w / inputs.span.toFloat(), h / ridgeH.toFloat()) * 0.45f
    
    val s = inputs.span.toFloat() * scale
    val eh = eaveH.toFloat() * scale
    val rh = ridgeH.toFloat() * scale
    val l = inputs.length.toFloat() * scale * 0.35f
    
    val ang = Math.toRadians(25.0)
    val dx = (cos(ang) * s).toFloat()
    val dy = (sin(ang) * s).toFloat()
    val dzx = (cos(ang) * l).toFloat()
    val dzy = (sin(ang) * l).toFloat()
    
    // Front Frame Points
    val p1 = Offset(centerX - dx/2, centerY + dy/2)      // Base L
    val p2 = Offset(centerX + dx/2, centerY - dy/2)      // Base R
    val p3 = Offset(p1.x, p1.y - eh)                     // Eave L
    val p4 = Offset(p2.x, p2.y - eh)                     // Eave R
    val p5 = Offset(centerX, (p3.y + p4.y)/2 - (rh - eh)) // Ridge
    
    // Back Frame Points (Projected)
    val b1 = Offset(p1.x - dzx, p1.y - dzy)
    val b2 = Offset(p2.x - dzx, p2.y - dzy)
    val b3 = Offset(p3.x - dzx, p3.y - dzy)
    val b4 = Offset(p4.x - dzx, p4.y - dzy)
    val b5 = Offset(p5.x - dzx, p5.y - dzy)

    val colorWall = Color(0xFF1A237E).copy(alpha = 0.4f)
    val colorRoof = Color(0xFF3F51B5).copy(alpha = 0.5f)
    val colorFrame = Color.White
    val colorBracing = Color(0xFFFFD600).copy(alpha = 0.7f)

    // 1. Draw Rear Wall (Fill)
    drawPath(Path().apply {
        moveTo(b1.x, b1.y); lineTo(b2.x, b2.y)
        lineTo(b4.x, b4.y); lineTo(b5.x, b5.y)
        lineTo(b3.x, b3.y); close()
    }, colorWall)

    // 2. Draw Side Walls (Fill)
    drawPath(Path().apply {
        moveTo(p1.x, p1.y); lineTo(b1.x, b1.y)
        lineTo(b3.x, b3.y); lineTo(p3.x, p3.y); close()
    }, colorWall.copy(alpha = 0.6f))
    
    drawPath(Path().apply {
        moveTo(p2.x, p2.y); lineTo(b2.x, b2.y)
        lineTo(b4.x, b4.y); lineTo(p4.x, p4.y); close()
    }, colorWall.copy(alpha = 0.3f))

    // 3. Draw Floor / Mezzanine if 2 stories
    if (inputs.numberOfStories == 2) {
        val f3 = Offset(p3.x, p3.y + eh/2)
        val f4 = Offset(p4.x, p4.y + eh/2)
        val fb3 = Offset(b3.x, b3.y + eh/2)
        val fb4 = Offset(b4.x, b4.y + eh/2)
        
        drawPath(Path().apply {
            moveTo(f3.x, f3.y); lineTo(f4.x, f4.y)
            lineTo(fb4.x, fb4.y); lineTo(fb3.x, fb3.y); close()
        }, Color.Green.copy(alpha = 0.3f))
        drawLine(Color.Green, f3, f4, 4f)
    }

    // 4. Draw Roof (Fill)
    drawPath(Path().apply {
        moveTo(p3.x, p3.y); lineTo(b3.x, b3.y)
        lineTo(b5.x, b5.y); lineTo(p5.x, p5.y); close()
    }, colorRoof)
    drawPath(Path().apply {
        moveTo(p4.x, p4.y); lineTo(b4.x, b4.y)
        lineTo(b5.x, b5.y); lineTo(p5.x, p5.y); close()
    }, colorRoof.copy(alpha = 0.7f))

    // 5. Draw Main Frame Outlines (Thick for volume)
    fun drawSolidFrame(bl: Offset, br: Offset, el: Offset, er: Offset, rd: Offset) {
        drawLine(colorFrame, bl, el, 6f)
        drawLine(colorFrame, br, er, 6f)
        drawLine(colorFrame, el, rd, 5f)
        drawLine(colorFrame, er, rd, 5f)
    }
    drawSolidFrame(b1, b2, b3, b4, b5) // Back
    drawSolidFrame(p1, p2, p3, p4, p5) // Front

    // 6. Bracing (X-Bracing on side walls)
    drawLine(colorBracing, p1, b3, 2f)
    drawLine(colorBracing, b1, p3, 2f)
    
    // 7. Base Plates
    drawRect(Color.Yellow, Offset(p1.x - 8f, p1.y - 3f), Size(16f, 6f))
    drawRect(Color.Yellow, Offset(p2.x - 8f, p2.y - 3f), Size(16f, 6f))

    drawWarehouseText("3D VOLUMETRIC MODEL", w/2, 50f, Color.White, 18f, center = true, bold = true)
    drawWarehouseText("Steel: ${String.format(Locale.US, "%.1f", result.totalWeight)} Tons", 20f, h - 30f, Color.Cyan, 14f)
}

private fun DrawScope.drawWarehouseText(
    text: String, x: Float, y: Float, color: Color, sizePx: Float,
    center: Boolean = false, bold: Boolean = false, rotation: Float = 0f
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        this.textSize = sizePx
        this.isFakeBoldText = bold
        this.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        this.isAntiAlias = true
    }
    if (rotation != 0f) {
        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.rotate(rotation, x, y)
        drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
        drawContext.canvas.nativeCanvas.restore()
    } else {
        drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
    }
}
