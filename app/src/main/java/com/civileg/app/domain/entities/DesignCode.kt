package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class DesignCode(val displayName: String, val version: String) : Parcelable {
    ECP("Egyptian Code", "ECP 203-2020"),
    ACI("American Code", "ACI 318-19"),
    SBC("Saudi Code", "SBC 304-2018");

    fun getDeadLoadFactor(): Double = when(this) {
        ECP -> 1.4
        ACI, SBC -> 1.2
    }

    fun getLiveLoadFactor(): Double = when(this) {
        ECP -> 1.6
        ACI, SBC -> 1.6
    }

    companion object {
        fun fromDisplayName(name: String): DesignCode {
            return values().find { it.displayName == name || name.contains(it.name) } ?: ECP
        }
    }
}
