package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for capturing Jetpack Compose drawing composables as Bitmaps
 * for embedding into PDF reports.
 */
object ComposeDrawingCaptureUtil {

    /**
     * Creates a [GraphicsLayer] that can be attached to a composable via
     * [DrawingCaptureArea]. Call [captureLayerToBitmap] to obtain the rendered Bitmap.
     */
    @Composable
    fun rememberDrawingCaptureLayer(): GraphicsLayer = rememberGraphicsLayer()

    /**
     * Invisible rendering area that records drawing content into [captureLayer].
     * The content is measured at exactly [widthPx] x [heightPx] pixels but is
     * invisible on screen (alpha = 0). The [captureLayer] captures the full-
     * opacity content so the resulting Bitmap looks identical to the on-screen
     * drawing.
     */
    @Composable
    fun DrawingCaptureArea(
        captureLayer: GraphicsLayer,
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = Modifier
                .layout { measurable, _ ->
                    val placeable = measurable.measure(
                        Constraints.fixed(widthPx, heightPx)
                    )
                    layout(widthPx, heightPx) {
                        placeable.place(0, 0)
                    }
                }
                .alpha(0f)
                .drawWithContent {
                    captureLayer.record { this@drawWithContent.drawContent() }
                }
        ) {
            content()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Legacy helpers (kept for backward-compatibility)
    // ─────────────────────────────────────────────────────────────

    fun createDrawingCanvas(
        widthPx: Int = 1200,
        heightPx: Int = 800,
        backgroundColor: Int = 0xFF1A1A2E.toInt()
    ): Pair<Bitmap, AndroidCanvas>? {
        return try {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            canvas.drawColor(backgroundColor)
            Pair(bitmap, canvas)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun bitmapToPngBytes(bitmap: Bitmap, quality: Int = 100): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }

    fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    object DrawingSizes {
        val BEAM = DrawingSize(1200, 1400)
        val COLUMN = DrawingSize(1200, 900)
        val SLAB = DrawingSize(1200, 800)
        val FOOTING = DrawingSize(1200, 900)
        val TANK = DrawingSize(1200, 1000)
        val RETAINING_WALL = DrawingSize(1200, 900)
        val STAIR = DrawingSize(1200, 1000)
        val STEEL = DrawingSize(1200, 700)
        val GENERAL = DrawingSize(1200, 800)
    }

    data class DrawingSize(val width: Int, val height: Int)
}

/**
 * Captures the recorded content of a [GraphicsLayer] into an Android [Bitmap].
 * This is a suspend function because [GraphicsLayer.toImageBitmap] is suspend.
 * Call from a coroutine (e.g. `rememberCoroutineScope().launch { }`).
 */
suspend fun GraphicsLayer.captureToAndroidBitmap(): Bitmap {
    return toImageBitmap().asAndroidBitmap()
}
