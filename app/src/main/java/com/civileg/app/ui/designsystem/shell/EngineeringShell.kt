package com.civileg.app.ui.designsystem.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.ui.designsystem.engineeringColors
import com.civileg.app.ui.designsystem.engineeringType

data class EngineeringContext(
    val project: String? = null,
    val level: String? = null,
    val element: String? = null,
    val code: String? = null,
    val units: String? = null
)

@Composable
fun EngineeringContextBar(
    context: EngineeringContext,
    modifier: Modifier = Modifier
) {
    val type = engineeringType()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val items = listOf(context.project, context.level, context.element, context.code, context.units)
        items.filterNotNull().forEachIndexed { i, item ->
            if (i > 0) {
                Text("›", style = type.breadcrumb, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item, style = type.breadcrumb, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun EngineeringBreadcrumb(
    segments: List<String>,
    modifier: Modifier = Modifier
) {
    val type = engineeringType()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segments.forEachIndexed { i, segment ->
            if (i > 0) {
                Text("/", style = type.breadcrumb, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            Text(
                segment.uppercase(),
                style = type.breadcrumb,
                color = if (i == segments.lastIndex) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EngineeringWorkspaceScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    context: EngineeringContext? = null,
    actionBar: (@Composable () -> Unit)? = null,
    sidePanel: (@Composable () -> Unit)? = null,
    showSidePanelInTwoPane: Boolean = false
) {
    val colors = engineeringColors()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val twoPane = sidePanel != null && screenWidth >= 840

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = modifier.fillMaxSize()) {
            topBar()
            if (context != null) {
                EngineeringContextBar(context = context)
            }
            Box(Modifier.weight(1f)) {
                if (twoPane) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.62f).fillMaxHeight()) { content() }
                        Box(
                            Modifier
                                .weight(0.38f)
                                .fillMaxHeight()
                                .background(colors.neutralContainer.copy(alpha = 0.35f))
                        ) { sidePanel!!() }
                    }
                } else {
                    content()
                    sidePanel?.invoke()
                }
            }
            if (actionBar != null) {
                Surface(tonalElevation = 3.dp) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { actionBar() }
                }
            }
        }
    }
}
