package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GenericSafetyCheck(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean,
    val reference: String? = null,
    val pointType: String? = null,
    /** true = not assessed (not a computed failure); passed is false so it can never count as a pass. */
    val notAssessed: Boolean = false
) : Parcelable
