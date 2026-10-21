package com.civileg.app.ui.compose.components.common

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
import androidx.compose.material.icons.filled.Assignment
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
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.BoqRow
import java.util.Locale

/**
 * On-screen Bill of Quantities shared by the element screens. Rows come straight from
 * [com.civileg.app.utils.BoqEstimator], so the table and the exported BOQ use one source.
 */
@Composable
fun BoqTableCard(
    rows: List<BoqRow>,
    modifier: Modifier = Modifier,
    title: String = "Bill of Quantities"
) {
    if (rows.isEmpty()) return
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
                    imageVector = Icons.Default.Assignment,
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
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cell(row.item, 0.4f, TextAlign.Start)
                    Cell(row.description, 2.2f, TextAlign.Start)
                    Cell(row.unit, 0.5f)
                    Cell(format(row.quantity), 0.8f)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cell("No.", 0.4f, TextAlign.Start, bold = true)
        Cell("Item", 2.2f, TextAlign.Start, bold = true)
        Cell("Unit", 0.5f, bold = true)
        Cell("Qty", 0.8f, bold = true)
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
