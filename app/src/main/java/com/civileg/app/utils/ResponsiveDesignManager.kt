package com.civileg.app.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.min

/**
 * Responsive Design Manager
 * يدير الأبعاد والتخطيطات حسب حجم الشاشة
 */
object ResponsiveDesignManager {

    // ==================== Screen Size Classification ====================
    enum class DeviceType {
        PHONE_SMALL,      // < 4.5 inches
        PHONE_MEDIUM,     // 4.5 - 5.5 inches
        PHONE_LARGE,      // 5.5 - 6.5 inches
        PHONE_XLARGE,     // > 6.5 inches
        TABLET_7,         // 7 inches
        TABLET_10,        // 10 inches
        TABLET_XLARGE     // > 10 inches
    }

    enum class OrientationType {
        PORTRAIT,
        LANDSCAPE
    }

    // ==================== Get Device Type ====================
    fun getDeviceType(context: Context): DeviceType {
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val screenInches = getScreenDiagonalInches(context)

        return when {
            screenInches < 4.5 -> DeviceType.PHONE_SMALL
            screenInches < 5.5 -> DeviceType.PHONE_MEDIUM
            screenInches < 6.5 -> DeviceType.PHONE_LARGE
            screenInches < 7 -> DeviceType.PHONE_XLARGE
            screenInches < 8.5 -> DeviceType.TABLET_7
            screenInches < 11 -> DeviceType.TABLET_10
            else -> DeviceType.TABLET_XLARGE
        }
    }

    fun getOrientationType(context: Context): OrientationType {
        return if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            OrientationType.LANDSCAPE
        } else {
            OrientationType.PORTRAIT
        }
    }

    // ==================== Get Screen Dimensions ====================
    fun getScreenDiagonalInches(context: Context): Float {
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val widthInches = displayMetrics.widthPixels / displayMetrics.xdpi
        val heightInches = displayMetrics.heightPixels / displayMetrics.ydpi
        val diagonalInches = kotlin.math.sqrt((widthInches * widthInches + heightInches * heightInches).toDouble()).toFloat()

        return diagonalInches
    }

    fun getScreenDensity(context: Context): Float {
        return context.resources.displayMetrics.density
    }

    // ==================== Get Responsive Dimensions ====================
    fun getPaddingHorizontal(context: Context): Int {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> dpToPx(context, 12)
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> dpToPx(context, 16)
            DeviceType.PHONE_XLARGE -> dpToPx(context, 20)
            DeviceType.TABLET_7 -> dpToPx(context, 24)
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> dpToPx(context, 32)
        }
    }

    fun getPaddingVertical(context: Context): Int {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> dpToPx(context, 8)
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> dpToPx(context, 12)
            DeviceType.PHONE_XLARGE -> dpToPx(context, 16)
            DeviceType.TABLET_7 -> dpToPx(context, 20)
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> dpToPx(context, 24)
        }
    }

    fun getTextSizeBody(context: Context): Float {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> 13f
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> 14f
            DeviceType.PHONE_XLARGE -> 15f
            DeviceType.TABLET_7 -> 16f
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> 18f
        }
    }

    fun getTextSizeHeading(context: Context): Float {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> 18f
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> 20f
            DeviceType.PHONE_XLARGE -> 22f
            DeviceType.TABLET_7 -> 24f
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> 28f
        }
    }

    fun getCardElevation(context: Context): Float {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL, DeviceType.PHONE_MEDIUM -> 2f
            DeviceType.PHONE_LARGE, DeviceType.PHONE_XLARGE -> 4f
            DeviceType.TABLET_7 -> 6f
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> 8f
        }
    }

    fun getIconSize(context: Context): Int {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> dpToPx(context, 24)
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> dpToPx(context, 28)
            DeviceType.PHONE_XLARGE -> dpToPx(context, 32)
            DeviceType.TABLET_7 -> dpToPx(context, 36)
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> dpToPx(context, 44)
        }
    }

    fun getButtonHeight(context: Context): Int {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> dpToPx(context, 40)
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> dpToPx(context, 44)
            DeviceType.PHONE_XLARGE -> dpToPx(context, 48)
            DeviceType.TABLET_7 -> dpToPx(context, 52)
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> dpToPx(context, 56)
        }
    }

    // ==================== Drawing Scale ====================
    fun getDrawingScale(context: Context, baseWidth: Float, baseHeight: Float): Float {
        val displayMetrics = context.resources.displayMetrics
        val availableWidth = displayMetrics.widthPixels * 0.85f
        val availableHeight = displayMetrics.heightPixels * 0.65f

        return min(availableWidth / baseWidth, availableHeight / baseHeight)
    }

    // ==================== Column Layout ====================
    fun getColumnCount(context: Context): Int {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL, DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> 1
            DeviceType.PHONE_XLARGE -> 2
            DeviceType.TABLET_7 -> 2
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> 3
        }
    }

    fun getGridSpacing(context: Context): Int {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> dpToPx(context, 4)
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> dpToPx(context, 8)
            DeviceType.PHONE_XLARGE -> dpToPx(context, 12)
            DeviceType.TABLET_7, DeviceType.TABLET_10 -> dpToPx(context, 16)
            DeviceType.TABLET_XLARGE -> dpToPx(context, 20)
        }
    }

    // ==================== Utility Functions ====================
    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun pxToDp(context: Context, px: Int): Int {
        return (px / context.resources.displayMetrics.density).toInt()
    }

    fun spToPx(context: Context, sp: Float): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }

    // ==================== Landscape Adjustments ====================
    fun getMaxInputFieldWidth(context: Context): Int {
        return when (getOrientationType(context)) {
            OrientationType.PORTRAIT -> {
                val metrics = context.resources.displayMetrics
                (metrics.widthPixels * 0.9).toInt()
            }
            OrientationType.LANDSCAPE -> {
                val metrics = context.resources.displayMetrics
                (metrics.widthPixels * 0.45).toInt()
            }
        }
    }

    fun shouldShowSideBySide(context: Context): Boolean {
        return getDeviceType(context) in listOf(
            DeviceType.TABLET_7,
            DeviceType.TABLET_10,
            DeviceType.TABLET_XLARGE
        )
    }

    fun shouldStackVertically(context: Context): Boolean {
        return getDeviceType(context) in listOf(
            DeviceType.PHONE_SMALL,
            DeviceType.PHONE_MEDIUM,
            DeviceType.PHONE_LARGE,
            DeviceType.PHONE_XLARGE
        )
    }

    // ==================== Safe Area ====================
    fun getSafeAreaPadding(context: Context): SafeAreaPadding {
        val metrics = context.resources.displayMetrics
        val orientation = getOrientationType(context)

        return when (orientation) {
            OrientationType.PORTRAIT -> {
                SafeAreaPadding(
                    top = dpToPx(context, 16),
                    bottom = dpToPx(context, 16),
                    left = dpToPx(context, 12),
                    right = dpToPx(context, 12)
                )
            }
            OrientationType.LANDSCAPE -> {
                SafeAreaPadding(
                    top = dpToPx(context, 12),
                    bottom = dpToPx(context, 12),
                    left = dpToPx(context, 20),
                    right = dpToPx(context, 20)
                )
            }
        }
    }

    data class SafeAreaPadding(
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int
    )

    // ==================== Drawing Annotations ====================
    fun getAnnotationTextSize(context: Context): Float {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> 12f
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> 14f
            DeviceType.PHONE_XLARGE -> 16f
            DeviceType.TABLET_7 -> 18f
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> 22f
        }
    }

    fun getStrokeWidth(context: Context): Float {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> 2f
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> 3f
            DeviceType.PHONE_XLARGE -> 4f
            DeviceType.TABLET_7 -> 5f
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> 6f
        }
    }

    fun getBarRadius(context: Context): Float {
        return when (getDeviceType(context)) {
            DeviceType.PHONE_SMALL -> 4f
            DeviceType.PHONE_MEDIUM, DeviceType.PHONE_LARGE -> 5f
            DeviceType.PHONE_XLARGE -> 6f
            DeviceType.TABLET_7 -> 7f
            DeviceType.TABLET_10, DeviceType.TABLET_XLARGE -> 8f
        }
    }
}

