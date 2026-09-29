package com.civileg.app.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Reusable Rebar Result entity for various structural elements.
 */
@Parcelize
data class RebarResult(
    val bars: Int,
    val diameter: Int,
    val spacing: Int,
    val providedArea: Double,
    val requiredArea: Double,
    val ratio: Double
) : Parcelable {
    val barString: String get() = "φ$diameter@$spacing mm"
}
