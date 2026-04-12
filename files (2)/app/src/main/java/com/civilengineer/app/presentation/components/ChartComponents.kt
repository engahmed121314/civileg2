package com.civilengineer.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * مكونات الرسوم البيانية والجداول
 */

/**
 * رسم بياني دائري لتوزيع التكاليف
 */
@Composable
fun CostPieChart(
    concretePercentage: Double,
    steelPercentage: Double,
    laborPercentage: Double,
    otherPercentage: Double,
    totalCost: Double,
    currency: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "توزيع التكاليف",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // رسم بياني بسيط بأشرطة
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CostBar(
                    label = "الخرسانة",
                    percentage = concretePercentage,
                    color = Color(0xFFC4A374),
                    cost = totalCost * (concretePercentage / 100),
                    currency = currency
                )
                CostBar(
                    label = "الفولاذ",
                    percentage = steelPercentage,
                    color = Color(0xFF333333),
                    cost = totalCost * (steelPercentage / 100),
                    currency = currency
                )
                CostBar(
                    label = "الأجور",
                    percentage = laborPercentage,
                    color = Color(0xFFFF6B6B),
                    cost = totalCost * (laborPercentage / 100),
                    currency = currency
                )
                CostBar(
                    label = "أخرى",
                    percentage = otherPercentage,
                    color = Color(0xFF4CAF50),
                    cost = totalCost * (otherPercentage / 100),
                    currency = currency
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "الإجمالي: ${"%.2f".format(totalCost)} $currency",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun CostBar(
    label: String,
    percentage: Double,
    color: Color,
    cost: Double,
    currency: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "${"%.1f".format(percentage)}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color.LightGray, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (percentage / 100).toFloat())
                    .background(color, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
            Text(
                "${"%.2f".format(cost)} $currency",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                color = Color.White,
                fontSize = 9.sp
            )
        }
    }
}

/**
 * جدول تفصيلي للنتائج
 */
@Composable
fun ResultsTable(
    rows: List<Pair<String, String>>,
    title: String = "الملخص"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                rows.forEach { (label, value) ->
                    TableRow(label, value)
                }
            }
        }
    }
}

@Composable
fun TableRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary
        )
    }
    Divider()
}

/**
 * جدول مقارن بين الأكواد
 */
@Composable
fun CodeComparisonTable(
    egyptianValue: Double,
    saudiValue: Double,
    americanValue: Double,
    label: String,
    unit: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "مقارنة بين الأكواد - $label",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ComparisonRow("الكود المصري", egyptianValue, unit, Color(0xFF2196F3))
            ComparisonRow("الكود السعودي", saudiValue, unit, Color(0xFFFF9800))
            ComparisonRow("الكود الأمريكي", americanValue, unit, Color(0xFF4CAF50))
        }
    }
}

@Composable
fun ComparisonRow(label: String, value: Double, unit: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(color.copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            "${"%.2f".format(value)} $unit",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

/**
 * رسم بياني للأحمال والضغوط
 */
@Composable
fun LoadDistributionChart(
    labels: List<String>,
    values: List<Double>,
    unit: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "توزيع الأحمال",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val maxValue = values.maxOrNull() ?: 1.0

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    val value = values.getOrNull(index) ?: 0.0
                    val percentage = (value / maxValue) * 100

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${"%.2f".format(value)} $unit",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(Color.LightGray, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = (percentage / 100).toFloat())
                                    .background(
                                        Color(
                                            (255 * (percentage / 100)).toInt(),
                                            50,
                                            50
                                        ),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}