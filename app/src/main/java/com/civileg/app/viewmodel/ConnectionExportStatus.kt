package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.GenericSafetyCheck

/**
 * Status rules for the steel weld and bolt PDF exports.
 *
 * A capacity on its own is not a verdict. The old code printed SAFE for any capacity above zero and
 * checked it against a demand of 0.0. A verdict exists only when a full design run supplied a real
 * applied force, which shows up as a utilization ratio and a design pass flag.
 */
internal object ConnectionExportStatus {
    fun isAssessed(utilizationRatio: Double?, designIsSafe: Boolean?): Boolean =
        utilizationRatio != null && designIsSafe != null && utilizationRatio.isFinite() && utilizationRatio > 0.0

    fun isSafe(utilizationRatio: Double?, designIsSafe: Boolean?): Boolean =
        isAssessed(utilizationRatio, designIsSafe) && designIsSafe == true

    fun statusText(utilizationRatio: Double?, designIsSafe: Boolean?): String = when {
        !isAssessed(utilizationRatio, designIsSafe) -> "Capacity only. No demand was supplied, so the connection is not assessed."
        designIsSafe == true -> "Capacity check passed for the entered force (utilization ${"%.2f".format(utilizationRatio)}). Other connection checks are not part of this report."
        else -> "Capacity check failed (utilization ${"%.2f".format(utilizationRatio)})"
    }

    fun checks(name: String, capacity: Double, utilizationRatio: Double?, designIsSafe: Boolean?): List<GenericSafetyCheck> =
        if (!isAssessed(utilizationRatio, designIsSafe)) emptyList()
        else listOf(GenericSafetyCheck(name, utilizationRatio!! * capacity, capacity, "kN", designIsSafe == true))
}
