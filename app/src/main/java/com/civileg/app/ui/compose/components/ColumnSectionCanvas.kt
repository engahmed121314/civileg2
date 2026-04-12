package com.civileg.app.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun ColumnSectionCanvas(
    width: Double,      // mm
    depth: Double,      // mm
    bars: Int,
    barDiameter: Double,
    tiesDiameter: Double,
    isSafe: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(300.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Add padding to canvas
        val padding = 40f
        val availableWidth = canvasWidth - 2 * padding
        val availableHeight = canvasHeight - 2 * padding

        val scaleX = availableWidth / width.toFloat()
        val scaleY = availableHeight / depth.toFloat()
        val scale = min(scaleX, scaleY)

        val rectWidth = (width.toFloat() * scale)
        val rectHeight = (depth.toFloat() * scale)
        
        val left = (canvasWidth - rectWidth) / 2
        val top = (canvasHeight - rectHeight) / 2

        // Draw Concrete Section
        drawRect(
            color = Color.DarkGray,
            topLeft = Offset(left, top),
            size = Size(rectWidth, rectHeight),
            style = Stroke(width = 4f)
        )

        // Draw Ties (Stirrups) - assume 25mm cover
        val cover = 25f * scale
        val tieLeft = left + cover
        val tieTop = top + cover
        val tieWidth = rectWidth - 2 * cover
        val tieHeight = rectHeight - 2 * cover
        
        if (tieWidth > 0 && tieHeight > 0) {
            drawRect(
                color = Color.Gray,
                topLeft = Offset(tieLeft, tieTop),
                size = Size(tieWidth, tieHeight),
                style = Stroke(width = 2f)
            )
        }

        // Draw Reinforcement Bars
        val barRadius = (barDiameter.toFloat() * scale / 2f).coerceAtLeast(4f)
        val barColor = if (isSafe) Color(0xFF4CAF50) else Color(0xFFF44336)
        
        // Simple 4-corner distribution for demo, or more complex for 'bars' count
        val horizontalCount = if (bars <= 4) 2 else if (bars <= 6) 3 else 4
        val verticalCount = (bars + horizontalCount - 1) / horizontalCount
        
        // For simplicity in this Canvas, we'll place them at corners first
        val cornerOffsets = listOf(
            Offset(tieLeft, tieTop),
            Offset(tieLeft + tieWidth, tieTop),
            Offset(tieLeft, tieTop + tieHeight),
            Offset(tieLeft + tieWidth, tieTop + tieHeight)
        )
        
        cornerOffsets.forEach { pos ->
            drawCircle(
                color = barColor,
                radius = barRadius,
                center = pos
            )
        }
        
        // Add more bars if count > 4
        if (bars > 4) {
            val remaining = bars - 4
            // Simplified placement: add along the longer side
            // This is a placeholder for a real engineering layout algorithm
        }
    }
}
