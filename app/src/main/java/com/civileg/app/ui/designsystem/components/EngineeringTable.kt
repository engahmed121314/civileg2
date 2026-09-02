package com.civileg.app.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.ui.designsystem.engineeringColors
import com.civileg.app.ui.designsystem.engineeringType

data class TableColumn(
    val title: String,
    val width: Int,
    val numeric: Boolean = false
)

data class TableRowData(
    val cells: List<String>,
    val highlighted: Boolean = false
)

@Composable
fun EngineeringTable(
    columns: List<TableColumn>,
    rows: List<TableRowData>,
    modifier: Modifier = Modifier,
    searchable: Boolean = true,
    sortable: Boolean = true,
    onRowClick: ((TableRowData) -> Unit)? = null
) {
    val type = engineeringType()
    val colors = engineeringColors()
    var query by remember { mutableStateOf("") }
    var sortColumn by rememberSaveable { mutableIntStateOf(-1) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }

    val filteredRows = remember(rows, query) {
        if (query.isBlank()) rows
        else rows.filter { row -> row.cells.any { it.contains(query, ignoreCase = true) } }
    }

    val sortedRows = remember(filteredRows, sortColumn, sortAscending) {
        if (sortColumn < 0 || sortColumn >= columns.size) filteredRows
        else filteredRows.sortedWith { a, b ->
            val ca = a.cells.getOrNull(sortColumn) ?: ""
            val cb = b.cells.getOrNull(sortColumn) ?: ""
            val cmp = columns[sortColumn].numeric.compareByNumeric(ca, cb)
            if (sortAscending) cmp else -cmp
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (searchable && rows.size > 4) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                label = { Text(stringResource(R.string.eg_table_search)) },
                singleLine = true
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.neutralContainer.copy(alpha = 0.5f))
        ) {
            LazyRow(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(columns) { colIndex, col ->
                    Row(
                        modifier = Modifier
                            .width(col.width.dp)
                            .clickable(enabled = sortable) {
                                if (sortColumn == colIndex) sortAscending = !sortAscending
                                else { sortColumn = colIndex; sortAscending = true }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            col.title,
                            style = type.tableHeader,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = if (col.numeric) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                        if (sortable && sortColumn == colIndex) {
                            Icon(
                                imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(Modifier.horizontalScroll(rememberScrollState())) {
                sortedRows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    row.highlighted -> colors.warningContainer.copy(alpha = 0.35f)
                                    rowIndex % 2 == 1 -> colors.neutralContainer.copy(alpha = 0.25f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable(enabled = onRowClick != null) { onRowClick?.invoke(row) }
                            .padding(vertical = 6.dp)
                    ) {
                        row.cells.forEachIndexed { cellIndex, cellText ->
                            Text(
                                text = cellText,
                                style = type.tableCell,
                                textAlign = if (columns.getOrNull(cellIndex)?.numeric == true) TextAlign.End else TextAlign.Start,
                                modifier = Modifier
                                    .width(columns.getOrNull(cellIndex)?.width?.dp ?: 100.dp)
                                    .padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
                if (sortedRows.isEmpty()) {
                    Text(
                        stringResource(R.string.eg_table_no_results),
                        style = type.tableCell,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun Boolean.compareByNumeric(a: String, b: String): Int = if (this) {
    a.toDoubleOrNull()?.compareTo(b.toDoubleOrNull() ?: 0.0) ?: a.compareTo(b)
} else a.compareTo(b)
