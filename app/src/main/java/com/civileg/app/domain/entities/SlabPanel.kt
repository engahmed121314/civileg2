package com.civileg.app.domain.entities

import android.os.Parcelable
import com.civileg.app.utils.LocaleHelper
import kotlinx.parcelize.Parcelize
import kotlin.math.atan2

enum class SlabDistribution(val displayNameAr: String, val displayNameEn: String) {
    OneWay("اتجاه واحد", "One-way"),
    TwoWay("اتجاهين", "Two-way");

    val displayName: String get() = if (LocaleHelper.isArabic()) displayNameAr else displayNameEn
}

/**
 * A rectangular slab panel bounded by four joints in counter-clockwise order.
 * The panel load is distributed to the surrounding members at solve time.
 */
@Parcelize
data class SlabPanel(
    val id: Int,
    val nodeIds: List<Int>,
    val thicknessMm: Double = 200.0,
    val loadKNm2: Double = 0.0,
    val loadCase: String = "DL",
    val distribution: SlabDistribution = SlabDistribution.TwoWay
) : Parcelable

/**
 * Orders four selected joint IDs into a simple polygon (angular order around the centroid).
 * This keeps user tap order from creating a self-intersecting slab outline.
 */
fun orderQuadJoints(ids: List<Int>, nodes: List<FrameNode>): List<Int> {
    if (ids.size != 4) return ids
    val points = ids.mapNotNull { id -> nodes.find { it.id == id }?.let { id to it } }
    if (points.size != 4) return ids
    val cx = points.sumOf { it.second.x } / 4.0
    val cy = points.sumOf { it.second.y } / 4.0
    return points.sortedBy { atan2(it.second.y - cy, it.second.x - cx) }.map { it.first }
}
