package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import java.io.ByteArrayOutputStream

/**
 * Utility for capturing drawings as Bitmaps for PDF embedding.
 * Supports both Android View system and Compose Canvas capture.
 */
object DrawingCaptureUtil {

    /**
     * Capture a legacy Android View as a Bitmap.
     * Used by ResultActivities that use XML-inflated custom drawing views.
     */
    fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    /**
     * Convert a Bitmap to iText-compatible byte array for PDF embedding.
     */
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}