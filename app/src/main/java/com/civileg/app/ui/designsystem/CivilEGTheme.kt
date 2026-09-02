package com.civileg.app.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun CivilEGDesignSystem(
    content: @Composable () -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dimens = if (isTablet()) TabletEngineeringDimens else LightEngineeringDimens
    CompositionLocalProvider(
        LocalEngineeringColors provides if (dark) DarkEngineeringColors else LightEngineeringColors,
        LocalEngineeringType provides if (dark) DarkEngineeringType else LightEngineeringType,
        LocalEngineeringDimens provides dimens
    ) {
        content()
    }
}

@Composable
private fun androidx.compose.ui.graphics.Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun isTablet(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.screenWidthDp >= 600
}
