package com.civileg.app.testutils

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.view.drawToBitmap
import com.civileg.app.BuildConfig
import java.io.File
import org.robolectric.shadows.ShadowDialog

/** Call on the UI thread after Compose is idle. No emulator or physical device is needed. */
internal fun saveNativeComposeScreenshot(
    activity: ComponentActivity,
    name: String,
    dialog: Boolean = false
): File {
    require(name.matches(Regex("[a-z0-9-]+")))
    val root = if (dialog) {
        val shown = requireNotNull(ShadowDialog.getLatestDialog()) { "No shown Android dialog" }
        check(shown.isShowing) { "Latest dialog is not showing" }
        requireNotNull(shown.window).decorView
    } else {
        activity.findViewById<ViewGroup>(android.R.id.content)
    }
    check(root.width > 0 && root.height > 0) { "Screenshot root has not been laid out" }
    val directory = File("build/reports/tests/compose-shots").apply { mkdirs() }
    val target = File(directory, "$name-${BuildConfig.FLAVOR}.png")
    target.outputStream().use { stream ->
        check(root.drawToBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
    }
    check(target.isFile && target.length() > 0)
    return target
}

/** Keeps a generated PDF in the uploaded test-report tree so it can be rendered and inspected. */
internal fun saveGeneratedPdfForInspection(source: File, name: String): File {
    require(name.matches(Regex("[a-z0-9-]+")))
    val directory = File("build/reports/tests/compose-shots").apply { mkdirs() }
    val target = File(directory, "$name-${BuildConfig.FLAVOR}.pdf")
    source.copyTo(target, overwrite = true)
    check(target.isFile && target.length() > 0)
    return target
}
