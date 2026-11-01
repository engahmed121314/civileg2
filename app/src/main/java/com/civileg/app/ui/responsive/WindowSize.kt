package com.civileg.app.ui.responsive

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 window width classes.
 *
 * Breakpoints follow the Material 3 adaptive guidance and are based on the
 * *current window* width, so the app also adapts correctly in split-screen,
 * free-form windows and on foldables.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/** Material 3 window height classes. */
enum class WindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/**
 * Coarse device category derived from the smallest width.
 * Used only for design decisions that should not change when the window is
 * resized (for example: "this is a tablet, use a navigation rail").
 */
enum class DeviceCategory {
    PHONE_SMALL,
    PHONE,
    TABLET_SMALL,
    TABLET,
    TABLET_LARGE
}

/**
 * Immutable description of the current window.
 *
 * All sizes are expressed in dp and are independent from the system density,
 * which keeps phone/tablet layouts consistent.
 */
@Immutable
data class WindowSize(
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val isLandscape: Boolean
) {
    val isCompact: Boolean get() = widthClass == WindowWidthClass.COMPACT
    val isMedium: Boolean get() = widthClass == WindowWidthClass.MEDIUM
    val isExpanded: Boolean get() = widthClass == WindowWidthClass.EXPANDED

    /** Tablets are identified by the standard 600dp smallest-width breakpoint. */
    val isTablet: Boolean get() = smallestWidthDp >= TABLET_BREAKPOINT_DP
    val isPhone: Boolean get() = !isTablet

    val deviceCategory: DeviceCategory
        get() = when {
            smallestWidthDp < 360 -> DeviceCategory.PHONE_SMALL
            smallestWidthDp < TABLET_BREAKPOINT_DP -> DeviceCategory.PHONE
            smallestWidthDp < 720 -> DeviceCategory.TABLET_SMALL
            smallestWidthDp < 840 -> DeviceCategory.TABLET
            else -> DeviceCategory.TABLET_LARGE
        }

    /**
     * Number of columns that fit in this window for a grid whose cells should
     * be at least [minItemWidth] wide.
     *
     * This is intentionally a pure helper so it can be unit-tested and reused
     * by non-Compose code. Prefer `GridCells.Adaptive` in Compose because it
     * automatically respects the real measured constraints.
     */
    fun adaptiveColumnCount(
        minItemWidth: Dp,
        horizontalPadding: Dp = 0.dp,
        maxColumns: Int = 6
    ): Int {
        val minWidth = minItemWidth.value.coerceAtLeast(1f)
        val usable = (widthDp - horizontalPadding.value * 2f).coerceAtLeast(minWidth)
        return (usable / minWidth).toInt().coerceIn(1, maxColumns)
    }

    companion object {
        const val COMPACT_BREAKPOINT_DP = 600
        const val MEDIUM_BREAKPOINT_DP = 840
        const val TABLET_BREAKPOINT_DP = 600

        val Default: WindowSize = resolve(
            widthDp = 360,
            heightDp = 640,
            smallestWidthDp = 360,
            isLandscape = false
        )

        /**
         * Pure resolver – keeps the breakpoint rules in one testable place.
         */
        fun resolve(
            widthDp: Int,
            heightDp: Int,
            smallestWidthDp: Int,
            isLandscape: Boolean
        ): WindowSize {
            val safeWidth = widthDp.coerceAtLeast(1)
            val safeHeight = heightDp.coerceAtLeast(1)
            val safeSmallest = smallestWidthDp.coerceAtLeast(1)

            val widthClass = when {
                safeWidth < COMPACT_BREAKPOINT_DP -> WindowWidthClass.COMPACT
                safeWidth < MEDIUM_BREAKPOINT_DP -> WindowWidthClass.MEDIUM
                else -> WindowWidthClass.EXPANDED
            }
            val heightClass = when {
                safeHeight < 480 -> WindowHeightClass.COMPACT
                safeHeight < 900 -> WindowHeightClass.MEDIUM
                else -> WindowHeightClass.EXPANDED
            }

            return WindowSize(
                widthDp = safeWidth,
                heightDp = safeHeight,
                smallestWidthDp = safeSmallest,
                widthClass = widthClass,
                heightClass = heightClass,
                isLandscape = isLandscape
            )
        }

        fun from(configuration: Configuration): WindowSize {
            val width = configuration.screenWidthDp
            val height = configuration.screenHeightDp
            val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                width > height
            return resolve(
                widthDp = width,
                heightDp = height,
                smallestWidthDp = configuration.smallestScreenWidthDp,
                isLandscape = landscape
            )
        }
    }
}

/**
 * Current window size. Provided once by [AppResponsiveProvider] at the top of
 * the Compose tree, so every screen observes the same values.
 */
val LocalWindowSize = compositionLocalOf { WindowSize.Default }

@Composable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.orientation
    ) {
        WindowSize.from(configuration)
    }
}
