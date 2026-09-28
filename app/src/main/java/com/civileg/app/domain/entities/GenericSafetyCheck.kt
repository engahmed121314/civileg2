package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GenericSafetyCheck(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean
) : Parcelable
