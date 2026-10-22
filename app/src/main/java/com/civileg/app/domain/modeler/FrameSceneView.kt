package com.civileg.app.domain.modeler

data class FrameSceneView(
    val scale: Float,
    val panX: Float,
    val panY: Float,
    val width: Int,
    val height: Int,
    val gridMm: Int
) {
    val isValid: Boolean
        get() = scale.isFinite() && scale > 0f &&
            panX.isFinite() && panY.isFinite() &&
            width > 0 && height > 0
}
