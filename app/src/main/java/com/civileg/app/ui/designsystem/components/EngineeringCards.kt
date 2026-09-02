package com.civileg.app.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.ui.designsystem.engineeringColors
import com.civileg.app.ui.designsystem.engineeringType

@Composable
fun EngineeringCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = engineeringColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.75.dp, colors.gridLine),
        shadowElevation = 1.dp,
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
fun EngineeringSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = engineeringType().sectionTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        trailing?.invoke()
    }
}

@Composable
fun EngineeringPropertyRow(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    val type = engineeringType()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = type.metricLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = type.valueSmall.copy(fontWeight = FontWeight.SemiBold),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
            if (!unit.isNullOrBlank()) {
                Text(
                    text = " $unit",
                    style = type.unit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
