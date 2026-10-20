package com.civileg.app.ui.compose.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.utils.BbsEntry
import java.util.Locale

/**
 * On-screen Bar Bending Schedule shared by the element screens. Entries come straight from
 * [com.civileg.app.utils.BbsGenerator], so the table and the exported BBS use one source.
 */
@Composable
fun BbsTableCard(
    entries: List<BbsEntry>,
    modifier: Modifier = Modifier,
    title: String = "Bar Bending Schedule"
) {
    if (entries.isEmpty()) return
    val totalWeight = entries.sumOf { it.totalWeightKg }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ListAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HeaderRow()
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cell(entry.barMark, 0.5f, TextAlign.Start)
                    Cell("Ø${entry.diameter}", 0.6f)
                    Cell("${entry.shapeCode}", 0.5f)
                    Cell("${entry.count}", 0.5f)
                    Cell("${format(entry.totalLengthPerBar / 1000.0)} m", 0.9f)
                    Cell("${format(entry.totalWeightKg)} kg", 1.0f)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${format(totalWeight)} kg",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cell("Bar", 0.5f, TextAlign.Start, bold = true)
        Cell("Ø", 0.6f, bold = true)
        Cell("Shape", 0.5f, bold = true)
        Cell("Qty", 0.5f, bold = true)
        Cell("Length", 0.9f, bold = true)
        Cell("Weight", 1.0f, bold = true)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.End,
    bold: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontSize = 11.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align
    )
}

private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
