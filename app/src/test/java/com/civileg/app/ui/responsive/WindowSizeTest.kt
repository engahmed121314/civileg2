package com.civileg.app.ui.responsive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the responsive breakpoints that drive the phone / tablet layouts.
 *
 * These values must stay in sync with the Material 3 window size classes:
 * COMPACT < 600dp, MEDIUM 600-839dp, EXPANDED >= 840dp.
 */
class WindowSizeTest {

    // ── Width classes ────────────────────────────────────────────────

    @Test
    fun widthClassFollowsMaterialBreakpoints() {
        assertEquals(WindowWidthClass.COMPACT, resolve(width = 360, height = 800).widthClass)
        assertEquals(WindowWidthClass.COMPACT, resolve(width = 599, height = 800).widthClass)
        assertEquals(WindowWidthClass.MEDIUM, resolve(width = 600, height = 800).widthClass)
        assertEquals(WindowWidthClass.MEDIUM, resolve(width = 839, height = 800).widthClass)
        assertEquals(WindowWidthClass.EXPANDED, resolve(width = 840, height = 800).widthClass)
        assertEquals(WindowWidthClass.EXPANDED, resolve(width = 1280, height = 900).widthClass)
    }

    @Test
    fun heightClassFollowsMaterialBreakpoints() {
        assertEquals(WindowHeightClass.COMPACT, resolve(width = 400, height = 479).heightClass)
        assertEquals(WindowHeightClass.MEDIUM, resolve(width = 400, height = 480).heightClass)
        assertEquals(WindowHeightClass.MEDIUM, resolve(width = 400, height = 899).heightClass)
        assertEquals(WindowHeightClass.EXPANDED, resolve(width = 400, height = 900).heightClass)
    }

    @Test
    fun splitScreenWindowIsClassifiedByCurrentWidthNotDeviceWidth() {
        // A tablet in split-screen can present a compact window: the layout
        // must follow the window so nothing overflows.
        val split = resolve(width = 420, height = 1024, smallest = 1024)

        assertEquals(WindowWidthClass.COMPACT, split.widthClass)
        assertTrue(split.isTablet)
        assertFalse(split.isPhone)
    }

    // ── Device category ──────────────────────────────────────────────

    @Test
    fun deviceCategoryUsesSmallestWidth() {
        assertEquals(DeviceCategory.PHONE_SMALL, resolve(width = 320, height = 480, smallest = 320).deviceCategory)
        assertEquals(DeviceCategory.PHONE, resolve(width = 360, height = 800, smallest = 360).deviceCategory)
        assertEquals(DeviceCategory.TABLET_SMALL, resolve(width = 800, height = 1280, smallest = 600).deviceCategory)
        assertEquals(DeviceCategory.TABLET, resolve(width = 1024, height = 1366, smallest = 768).deviceCategory)
        assertEquals(DeviceCategory.TABLET_LARGE, resolve(width = 1280, height = 1600, smallest = 900).deviceCategory)
    }

    @Test
    fun landscapeOrientationIsDetected() {
        assertTrue(resolve(width = 800, height = 400, landscape = true).isLandscape)
        assertFalse(resolve(width = 400, height = 800, landscape = false).isLandscape)
    }

    // ── Robustness ───────────────────────────────────────────────────

    @Test
    fun nonPositiveInputIsClampedInsteadOfThrowing() {
        val size = resolve(width = 0, height = -10, smallest = 0)

        assertEquals(WindowWidthClass.COMPACT, size.widthClass)
        assertEquals(WindowHeightClass.COMPACT, size.heightClass)
        assertTrue(size.smallestWidthDp >= 1)
    }

    // ── Grid helper ──────────────────────────────────────────────────

    @Test
    fun adaptiveColumnCountFitsAvailableWidth() {
        val phone = resolve(width = 360, height = 800, smallest = 360)

        // (360 - 32) / 140 = 2.34 -> 2 columns
        assertEquals(2, phone.adaptiveColumnCount(minItemWidth = 140.dp, horizontalPadding = 16.dp))
        // (360 - 32) / 100 = 3.28 -> 3 columns
        assertEquals(3, phone.adaptiveColumnCount(minItemWidth = 100.dp, horizontalPadding = 16.dp))
    }

    @Test
    fun adaptiveColumnCountIsAtLeastOneAndAtMostMaxColumns() {
        val hugePhone = resolve(width = 360, height = 800, smallest = 360)
        assertEquals(1, hugePhone.adaptiveColumnCount(minItemWidth = 500.dp))

        val tablet = resolve(width = 1280, height = 1600, smallest = 1280)
        val unlimited = tablet.adaptiveColumnCount(minItemWidth = 100.dp, maxColumns = 6)
        assertEquals(6, unlimited)
    }

    @Test
    fun adaptiveColumnCountNeverReturnsZeroForDegenerateInput() {
        val phone = resolve(width = 1, height = 1, smallest = 1)

        assertTrue(phone.adaptiveColumnCount(minItemWidth = 0.dp) >= 1)
        assertTrue(phone.adaptiveColumnCount(minItemWidth = (-20).dp) >= 1)
    }

    private fun resolve(
        width: Int,
        height: Int,
        smallest: Int = minOf(width, height),
        landscape: Boolean = width > height
    ): WindowSize = WindowSize.resolve(
        widthDp = width,
        heightDp = height,
        smallestWidthDp = smallest,
        isLandscape = landscape
    )
}
