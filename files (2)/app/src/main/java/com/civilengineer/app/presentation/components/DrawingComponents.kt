package com.civilengineer.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * مكونات الرسومات الهندسية المتقدمة
 */

/**
 * رسم قطاع العمود
 */
@Composable
fun ColumnSectionDrawing(
    lengthM: Double,
    widthM: Double,
    steelDiameter: Int,
    steelSpacing: Int,
    coverMM: Int = 40
) {
    val scale = 10f // 1 متر = 10 بكسل
    val width = lengthM * scale
    val height = widthM * scale

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "قطاع العمود الخرساني",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color.White)
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val columnWidth = width.coerceAtMost(size.width - 40)
                val columnHeight = height.coerceAtMost(size.height - 40)

                // رسم خرسانة الغطاء الخارجي
                drawRect(
                    color = Color(0xFFC4A374),
                    topLeft = Offset(centerX - columnWidth / 2, centerY - columnHeight / 2),
                    size = androidx.compose.ui.geometry.Size(columnWidth, columnHeight),
                    style = Stroke(2f)
                )

                // رسم الخرسانة الداخلية
                val innerWidth = columnWidth - (coverMM / 100f * scale * 2)
                val innerHeight = columnHeight - (coverMM / 100f * scale * 2)
                drawRect(
                    color = Color(0xFFDDCCBB),
                    topLeft = Offset(
                        centerX - innerWidth / 2,
                        centerY - innerHeight / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(innerWidth, innerHeight)
                )

                // رسم الفولاذ الرئيسي (أسياخ التسليح)
                val steelRadius = steelDiameter / 2f / 100f * scale
                val spacing = steelSpacing / 1000f * scale
                
                var x = centerX - columnWidth / 2 + (coverMM / 100f * scale)
                while (x < centerX + columnWidth / 2 - (coverMM / 100f * scale)) {
                    var y = centerY - columnHeight / 2 + (coverMM / 100f * scale)
                    while (y < centerY + columnHeight / 2 - (coverMM / 100f * scale)) {
                        drawCircle(
                            color = Color(0xFF333333),
                            center = Offset(x, y),
                            radius = steelRadius
                        )
                        y += spacing
                    }
                    x += spacing
                }

                // رسم أبعاد
                drawLine(
                    color = Color.Black,
                    start = Offset(centerX - columnWidth / 2 - 20, centerY + columnHeight / 2 + 20),
                    end = Offset(centerX + columnWidth / 2 + 20, centerY + columnHeight / 2 + 20),
                    strokeWidth = 1f
                )
            }
        }
    }
}

/**
 * رسم مخطط القاعدة الخرسانية
 */
@Composable
fun FootingPlanDrawing(
    lengthM: Double,
    widthM: Double,
    columnLengthM: Double,
    columnWidthM: Double
) {
    val scale = 15f
    val footingWidth = lengthM * scale
    val footingHeight = widthM * scale
    val columnWidth = columnLengthM * scale
    val columnHeight = columnWidthM * scale

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "مخطط القاعدة من الأعلى",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .background(Color.White)
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2

                // رسم القاعدة
                drawRect(
                    color = Color(0xFFC4A374),
                    topLeft = Offset(centerX - footingWidth / 2, centerY - footingHeight / 2),
                    size = androidx.compose.ui.geometry.Size(
                        footingWidth.coerceAtMost(size.width - 40),
                        footingHeight.coerceAtMost(size.height - 40)
                    ),
                    style = Stroke(2f)
                )

                // رسم العمود في المركز
                drawRect(
                    color = Color(0xFF8B7355),
                    topLeft = Offset(
                        centerX - columnWidth / 2,
                        centerY - columnHeight / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(columnWidth, columnHeight),
                    style = Stroke(3f)
                )

                // تظليل العمود
                drawRect(
                    color = Color(0xFF8B7355).copy(alpha = 0.3f),
                    topLeft = Offset(
                        centerX - columnWidth / 2,
                        centerY - columnHeight / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(columnWidth, columnHeight)
                )
            }

            // الأبعاد
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("الطول: ${"%.2f".format(lengthM)} م", style = MaterialTheme.typography.bodySmall)
                Text("العرض: ${"%.2f".format(widthM)} م", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * رسم البلاطة الخرسانية
 */
@Composable
fun SlabSectionDrawing(
    lengthM: Double,
    thicknessMM: Int,
    steelDiameter: Int,
    steelSpacing: Int
) {
    val scale = 10f
    val length = lengthM * scale
    val thickness = (thicknessMM / 1000.0) * scale

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "قطاع البلاطة الخرسانية",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color.White)
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2

                val actualLength = length.coerceAtMost(size.width - 60)
                val actualThickness = thickness.coerceAtMost(40f)

                // رسم خرسانة البلاطة
                drawRect(
                    color = Color(0xFFC4A374),
                    topLeft = Offset(centerX - actualLength / 2, centerY - actualThickness / 2),
                    size = androidx.compose.ui.geometry.Size(actualLength, actualThickness),
                    style = Stroke(2f)
                )

                drawRect(
                    color = Color(0xFFDDCCBB),
                    topLeft = Offset(centerX - actualLength / 2, centerY - actualThickness / 2),
                    size = androidx.compose.ui.geometry.Size(actualLength, actualThickness),
                    alpha = 0.5f
                )

                // رسم الفولاذ السفلي
                val steelRadius = steelDiameter / 2f / 100f * scale * 0.5f
                val spacing = steelSpacing / 1000f * scale
                
                var x = centerX - actualLength / 2 + 10
                while (x < centerX + actualLength / 2 - 10) {
                    // فولاذ سفلي
                    drawCircle(
                        color = Color(0xFF333333),
                        center = Offset(x, centerY + actualThickness / 2 - 5),
                        radius = steelRadius
                    )
                    x += spacing
                }

                // خطوط بعاد
                drawLine(
                    color = Color.Black,
                    start = Offset(centerX - actualLength / 2 - 10, centerY + actualThickness / 2 + 20),
                    end = Offset(centerX + actualLength / 2 + 10, centerY + actualThickness / 2 + 20),
                    strokeWidth = 1f
                )
            }

            // المعلومات
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("الطول: ${"%.2f".format(lengthM)} م", style = MaterialTheme.typography.bodySmall)
                Text("السمك: $thicknessMM ملم", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * رسم حائط السند
 */
@Composable
fun RetainingWallDrawing(
    wallHeightM: Double,
    baseWidthM: Double,
    stemThicknessM: Double,
    baseThicknessM: Double
) {
    val scale = 8f
    val height = wallHeightM * scale
    val baseWidth = baseWidthM * scale
    val stemThickness = stemThicknessM * scale
    val baseThickness = baseThicknessM * scale

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "قطاع حائط السند",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.White)
            ) {
                val centerX = size.width / 2
                val baseY = size.height - 50

                val actualHeight = height.coerceAtMost(size.height - 100)
                val actualBaseWidth = baseWidth.coerceAtMost(size.width - 60)
                val actualStemThickness = stemThickness.coerceAtMost(30f)

                // رسم القاعدة
                drawRect(
                    color = Color(0xFFC4A374),
                    topLeft = Offset(centerX - actualBaseWidth / 2, baseY - actualHeight / 8),
                    size = androidx.compose.ui.geometry.Size(actualBaseWidth, actualHeight / 8)
                )

                // رسم الساق
                drawRect(
                    color = Color(0xFFC4A374),
                    topLeft = Offset(
                        centerX - actualStemThickness / 2,
                        baseY - actualHeight
                    ),
                    size = androidx.compose.ui.geometry.Size(actualStemThickness, actualHeight)
                )

                // رسم التربة خلف الحائط
                drawRect(
                    color = Color(0xFFA0826D).copy(alpha = 0.4f),
                    topLeft = Offset(
                        centerX + actualStemThickness / 2,
                        baseY - actualHeight
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        (size.width / 2) - (actualStemThickness / 2) - 20,
                        actualHeight
                    )
                )

                // رسم أسهم الضغط
                drawLine(
                    color = Color.Red,
                    start = Offset(centerX + actualStemThickness / 2 + 20, baseY - actualHeight / 2),
                    end = Offset(centerX + actualStemThickness / 2 + 50, baseY - actualHeight / 2),
                    strokeWidth = 2f
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("الارتفاع: ${"%.2f".format(wallHeightM)} م", style = MaterialTheme.typography.bodySmall)
                Text("عرض القاعدة: ${"%.2f".format(baseWidthM)} م", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}