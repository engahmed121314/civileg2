package com.civileg.app.ui.responsive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central spacing and sizing tokens for the whole application.
 *
 * Screens and shared components should read these tokens instead of creating
 * device-specific branches themselves. That keeps phone, tablet, foldable and
 * desktop-window layouts consistent.
 */
@Immutable
data class AppDimens(
    val windowSize: WindowSize,

    // ── Screen layout ──
    val screenHorizontalPadding: Dp,
    val screenVerticalPadding: Dp,
    val sectionSpacing: Dp,
    val cardSpacing: Dp,
    val cardPadding: Dp,

    // ── Responsive containers ──
    val contentMaxWidth: Dp,
    val formMaxWidth: Dp,
    val dialogMaxWidth: Dp,
    val navigationRailWidth: Dp,

    // ── Grids ──
    val gridMinItemWidth: Dp,
    val maxGridColumns: Int,

    // ── Cards / reusable blocks ──
    val statCardMinHeight: Dp,
    val moduleCardMinHeight: Dp,
    val hubCardMinHeight: Dp,
    val quickToolWidth: Dp,

    // ── Icons / touch targets ──
    val iconSmall: Dp,
    val iconMedium: Dp,
    val iconLarge: Dp,
    val minTouchTarget: Dp
) {
    companion object {
        fun forWindow(window: WindowSize): AppDimens = when (window.widthClass) {
            WindowWidthClass.EXPANDED -> AppDimens(
                windowSize = window,
                screenHorizontalPadding = 32.dp,
                screenVerticalPadding = 24.dp,
                sectionSpacing = 24.dp,
                cardSpacing = 20.dp,
                cardPadding = 20.dp,
                contentMaxWidth = 1200.dp,
                formMaxWidth = 920.dp,
                dialogMaxWidth = 720.dp,
                navigationRailWidth = 96.dp,
                gridMinItemWidth = 180.dp,
                maxGridColumns = 6,
                statCardMinHeight = 112.dp,
                moduleCardMinHeight = 190.dp,
                hubCardMinHeight = 178.dp,
                quickToolWidth = 164.dp,
                iconSmall = 20.dp,
                iconMedium = 28.dp,
                iconLarge = 48.dp,
                minTouchTarget = 48.dp
            )

            WindowWidthClass.MEDIUM -> AppDimens(
                windowSize = window,
                screenHorizontalPadding = 24.dp,
                screenVerticalPadding = 20.dp,
                sectionSpacing = 20.dp,
                cardSpacing = 16.dp,
                cardPadding = 18.dp,
                contentMaxWidth = 1040.dp,
                formMaxWidth = 840.dp,
                dialogMaxWidth = 640.dp,
                navigationRailWidth = 88.dp,
                gridMinItemWidth = 160.dp,
                maxGridColumns = 5,
                statCardMinHeight = 100.dp,
                moduleCardMinHeight = 180.dp,
                hubCardMinHeight = 168.dp,
                quickToolWidth = 150.dp,
                iconSmall = 18.dp,
                iconMedium = 26.dp,
                iconLarge = 44.dp,
                minTouchTarget = 48.dp
            )

            WindowWidthClass.COMPACT -> AppDimens(
                windowSize = window,
                screenHorizontalPadding = if (window.widthDp < 360) 12.dp else 16.dp,
                screenVerticalPadding = 16.dp,
                sectionSpacing = 16.dp,
                cardSpacing = 12.dp,
                cardPadding = 14.dp,
                contentMaxWidth = 840.dp,
                formMaxWidth = 720.dp,
                dialogMaxWidth = 560.dp,
                navigationRailWidth = 84.dp,
                gridMinItemWidth = 140.dp,
                maxGridColumns = 4,
                statCardMinHeight = 90.dp,
                moduleCardMinHeight = 170.dp,
                hubCardMinHeight = 155.dp,
                quickToolWidth = 130.dp,
                iconSmall = 18.dp,
                iconMedium = 24.dp,
                iconLarge = 40.dp,
                minTouchTarget = 48.dp
            )
        }
    }
}

val LocalAppDimens = compositionLocalOf { AppDimens.forWindow(WindowSize.Default) }

/**
 * Access the responsive tokens from any composable:
 *
 * ```
 * MaterialTheme.dimens.screenHorizontalPadding
 * ```
 */
val MaterialTheme.dimens: AppDimens
    @Composable
    @ReadOnlyComposable
    get() = LocalAppDimens.current
