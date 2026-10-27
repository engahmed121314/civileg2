package com.civileg.app.domain.modeler

data class ModelerLayerVisibility(
    val grid: Boolean = true,
    val joints: Boolean = true,
    val members: Boolean = true,
    val supports: Boolean = true,
    val loads: Boolean = true,
    val slabs: Boolean = true,
    val dimensions: Boolean = true
) {
    fun toMask(): Int =
        (if (grid) GRID else 0) or
            (if (joints) JOINTS else 0) or
            (if (members) MEMBERS else 0) or
            (if (supports) SUPPORTS else 0) or
            (if (loads) LOADS else 0) or
            (if (slabs) SLABS else 0) or
            (if (dimensions) DIMENSIONS else 0)

    companion object {
        const val GRID = 1
        const val JOINTS = 2
        const val MEMBERS = 4
        const val SUPPORTS = 8
        const val LOADS = 16
        const val SLABS = 32
        const val DIMENSIONS = 64
        const val ALL = 127

        fun fromMask(mask: Int): ModelerLayerVisibility = ModelerLayerVisibility(
            grid = mask and GRID != 0,
            joints = mask and JOINTS != 0,
            members = mask and MEMBERS != 0,
            supports = mask and SUPPORTS != 0,
            loads = mask and LOADS != 0,
            slabs = mask and SLABS != 0,
            dimensions = mask and DIMENSIONS != 0
        )
    }
}
