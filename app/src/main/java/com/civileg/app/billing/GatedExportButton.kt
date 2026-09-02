package com.civileg.app.billing

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Export button wrapper that shows a premium paywall when the feature is gated.
 *
 * Usage:
 * ```kotlin
 * GatedExportButton(
 *     feature = PremiumFeature.PDF_EXPORT,
 *     billingManager = billingManager,
 *     onClick = { exportPdf() }
 * ) {
 *     Icon(Icons.Default.PictureAsPdf, contentDescription = null)
 *     Text("PDF")
 * }
 * ```
 */
@Composable
fun GatedExportButton(
    feature: PremiumFeature,
    featureFlags: FeatureFlags,
    billingManager: BillingManager,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    var showPaywall by remember { mutableStateOf(false) }
    val isPremium = featureFlags.isPremium()

    if (showPaywall) {
        PremiumPaywallSheet(
            feature = feature,
            billingManager = billingManager,
            onDismiss = { showPaywall = false }
        )
    }

    OutlinedButton(
        onClick = {
            if (isPremium) onClick()
            else showPaywall = true
        },
        modifier = modifier,
        enabled = enabled
    ) {
        content()
    }
}

/**
 * Simple non-composable check for use in ViewModels or non-Compose contexts.
 * Returns true if the user has premium or the feature is free.
 */
@Composable
fun rememberFeatureAccess(feature: PremiumFeature, featureFlags: FeatureFlags): Boolean {
    return featureFlags.isAvailable(feature)
}
