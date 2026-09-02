package com.civileg.app.ui.designsystem

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.civileg.app.R

enum class EngineeringStatus(@StringRes val labelRes: Int) {
    PASS(R.string.eg_status_pass),
    WARNING(R.string.eg_status_warning),
    FAIL(R.string.eg_status_fail),
    NOT_CHECKED(R.string.eg_status_not_checked),
    INFO(R.string.eg_status_info);

    @Composable
    fun color(): Color = when (this) {
        PASS -> engineeringColors().safe
        WARNING -> engineeringColors().warning
        FAIL -> engineeringColors().fail
        NOT_CHECKED -> engineeringColors().notChecked
        INFO -> engineeringColors().info
    }

    @Composable
    fun containerColor(): Color = when (this) {
        PASS -> engineeringColors().safeContainer
        WARNING -> engineeringColors().warningContainer
        FAIL -> engineeringColors().failContainer
        NOT_CHECKED -> engineeringColors().notCheckedContainer
        INFO -> engineeringColors().infoContainer
    }

    val icon: ImageVector
        get() = when (this) {
            PASS -> Icons.Filled.CheckCircle
            WARNING -> Icons.Filled.WarningAmber
            FAIL -> Icons.Filled.Error
            NOT_CHECKED -> Icons.Filled.HelpOutline
            INFO -> Icons.Filled.Info
        }

    companion object {
        fun fromUtilization(utilization: Double, warnThreshold: Double = 0.90): EngineeringStatus =
            when {
                utilization <= 1.0 && utilization < warnThreshold -> PASS
                utilization >= 1.0 && utilization <= 1.05 -> WARNING
                utilization > 1.05 -> FAIL
                else -> WARNING
            }
    }
}
