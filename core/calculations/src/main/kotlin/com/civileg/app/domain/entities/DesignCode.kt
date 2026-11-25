package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class DesignCode(val displayName: String, val version: String) : Parcelable {
    // The version text is shown in headers and exports. It names the code family only: no edition is claimed
    // unless a particular check is individually verified against the supplied edition (stated beside that check).
    ECP("Egyptian Code", "ECP 203 (edition not verified)"),
    ACI("American Code", "ACI 318 (edition not verified)"),
    SBC("Saudi Code", "SBC 304 (edition not verified)");

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
            return entries.find { it.displayName == name || name.contains(it.name) } ?: ECP
        }
    }
}
