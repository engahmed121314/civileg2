package com.civileg.app.ui.compose.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun BiaxialInteractionChart(
    mxPoints: List<Pair<Double, Double>>, // (M, P)
    myPoints: List<Pair<Double, Double>>, // (M, P)
    appliedMx: Double,
    appliedMy: Double,
    appliedP: Double,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().height(250.dp).background(Color.White)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val w = size.width
            val h = size.height
            
            val maxM = maxOf(
                mxPoints.maxOfOrNull { it.first } ?: 100.0,
                myPoints.maxOfOrNull { it.first } ?: 100.0,
                appliedMx, appliedMy
            ) * 1.1
            val maxP = maxOf(
                mxPoints.maxOfOrNull { it.second } ?: 1000.0,
                appliedP
            ) * 1.1
            
            fun toX(m: Double) = (m / maxM * w).toFloat()
            fun toY(p: Double) = (h - (p / maxP * h)).toFloat()
            
            // X-Axis curve (Blue)
            val pathX = Path()
            mxPoints.forEachIndexed { i, pt ->
                val x = toX(pt.first)
                val y = toY(pt.second)
                if (i == 0) pathX.moveTo(x, y) else pathX.lineTo(x, y)
            }
            drawPath(pathX, Color.Blue, style = Stroke(2f))
            
            // Y-Axis curve (Red)
            val pathY = Path()
            myPoints.forEachIndexed { i, pt ->
                val x = toX(pt.first)
                val y = toY(pt.second)
                if (i == 0) pathY.moveTo(x, y) else pathY.lineTo(x, y)
            }
            drawPath(pathY, Color.Red, style = Stroke(2f))
            
            // Applied Point
            drawCircle(Color.Black, 6f, Offset(toX(max(appliedMx, appliedMy)), toY(appliedP)))
        }
    }
}
