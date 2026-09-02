package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import com.civileg.app.billing.BillingManager
import com.civileg.app.billing.FeatureFlags
import com.civileg.app.billing.PremiumFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Shared ViewModel for premium/billing state.
 * Inject this alongside the element-specific ViewModel in any screen
 * that needs paywall gating.
 *
 * Usage:
 * ```kotlin
 * val premiumVm: PremiumViewModel = hiltViewModel()
 * val featureFlags = premiumVm.featureFlags
 * val billingManager = premiumVm.billingManager
 * ```
 */
@HiltViewModel
class PremiumViewModel @Inject constructor(
    val featureFlags: FeatureFlags,
    val billingManager: BillingManager
) : ViewModel()
