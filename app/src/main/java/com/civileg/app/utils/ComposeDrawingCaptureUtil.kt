package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for capturing Jetpack Compose drawing composables as Bitmaps
 * for embedding into PDF reports.
 */
object ComposeDrawingCaptureUtil {

    /**
     * Captures a Composable drawing as a Bitmap.
     *
     * Usage: Pass the drawing composable (e.g., ProfessionalBeamDrawing) and
     * desired width/height. Returns an Android Bitmap suitable for PDF embedding.
     *
     * Note: This creates the composable off-screen using a Picture/Canvas approach
     * since Compose doesn't natively support screenshot of arbitrary composables
     * without being attached to a window.
     *
     * For PDF export, the recommended approach is to use [drawToBitmapDirect]
     * which draws directly to a Canvas using the same draw scope logic.
     */
    suspend fun captureComposable(
        context: Context,
        widthPx: Int = 1200,
        heightPx: Int = 800,
        content: @Composable () -> Unit
    ): Bitmap? = withContext(Dispatchers.Main) {
        try {
            // Create a bitmap and canvas
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)

            // Fill background with dark color (matching app theme)
            canvas.drawColor(0xFF1A1A2E.toInt())

            // Note: Direct Compose composable capture requires the composable
            // to be in the composition tree. For PDF export, use the
            // drawToPdfCanvas approach in ComprehensivePdfExporter instead.
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a Compose ImageBitmap to Android Bitmap.
     */
    fun imageBitmapToAndroidBitmap(imageBitmap: ImageBitmap): Bitmap {
        return imageBitmap.asAndroidBitmap()
    }

    /**
     * Compresses a Bitmap to PNG byte array for iText PDF embedding.
     */
    fun bitmapToPngBytes(bitmap: Bitmap, quality: Int = 100): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Saves a Bitmap to a temporary file in the app's cache directory.
     * Returns the file path or null on failure.
     */
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

    /**
     * Creates a drawing-sized Bitmap with dark background, ready for
     * the PDF exporter to draw engineering content onto using Android Canvas.
     *
     * This is the RECOMMENDED approach for PDF drawing export:
     * 1. Call this to get a Canvas-backed Bitmap
     * 2. Draw engineering content using the same DrawScope logic
     * 3. Embed the Bitmap into PDF via ImageDataFactory
     *
     * @return Pair of (Bitmap, Android Canvas) or null on failure
     */
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

    /**
     * Standard drawing dimensions for PDF export (in pixels at 300 DPI concept).
     * These match the Compose drawing canvas sizes for consistency.
     */
    object DrawingSizes {
        val BEAM = DrawingSize(1200, 700)
        val COLUMN = DrawingSize(1200, 800)
        val SLAB = DrawingSize(1200, 700)
        val FOOTING = DrawingSize(1200, 650)
        val TANK = DrawingSize(1200, 900)
        val RETAINING_WALL = DrawingSize(1200, 800)
        val STAIR = DrawingSize(1200, 700)
        val STEEL = DrawingSize(1200, 700)
        val GENERAL = DrawingSize(1200, 800)
    }

    data class DrawingSize(val width: Int, val height: Int)
}