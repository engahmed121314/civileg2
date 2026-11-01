package com.civileg.app.ui.responsive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Provides the window size, dimensions and typography-aware values for the
 * entire Compose tree. Wrap the app theme content with this once.
 */
@Composable
fun AppResponsiveProvider(content: @Composable () -> Unit) {
    val windowSize = rememberWindowSize()
    val dimensions = remember(windowSize) { AppDimens.forWindow(windowSize) }

    CompositionLocalProvider(
        LocalWindowSize provides windowSize,
        LocalAppDimens provides dimensions,
        content = content
    )
}

/**
 * Centers the content and limits its width on large tablets / desktop windows.
 *
 * The limit is important: a form that fills a 13" tablet edge-to-edge is hard
 * to read, while a hub page with a 5-column grid looks well composed.
 */
@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = MaterialTheme.dimens.contentMaxWidth,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize(),
            content = content
        )
    }
}

/**
 * Constrains a single content column (usually a form) to a readable width and
 * keeps it centered. Use inside a `Scaffold` content lambda:
 *
 * ```
 * FormContent { Column { ... } }
 * ```
 */
@Composable
fun FormContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = MaterialTheme.dimens.formMaxWidth,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            content = content
        )
    }
}

/**
 * Two-pane layout used on tablets and landscape phones.
 *
 * - Wide windows: panes are placed side-by-side.
 * - Narrow windows: panes are stacked, so nothing is squeezed.
 */
@Composable
fun AdaptiveTwoPane(
    modifier: Modifier = Modifier,
    breakpoint: Dp = WindowSize.MEDIUM_BREAKPOINT_DP.dp,
    spacing: Dp = MaterialTheme.dimens.sectionSpacing,
    firstWeight: Float = 0.45f,
    secondWeight: Float = 0.55f,
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit
) {
    val window = LocalWindowSize.current
    if (window.widthDp >= breakpoint.value) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(firstWeight)) { firstPane() }
            Box(modifier = Modifier.weight(secondWeight)) { secondPane() }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            firstPane()
            secondPane()
        }
    }
}

/**
 * Applies the standard screen-level horizontal padding for the current window.
 */
@Composable
fun Modifier.responsiveScreenPadding(): Modifier =
    padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding)
