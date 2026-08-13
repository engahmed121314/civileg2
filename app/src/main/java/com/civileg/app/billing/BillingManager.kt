package com.civileg.app.billing

import android.app.Activity
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import com.civileg.app.data.local.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Play Billing subscriptions for CivilEG Pro.
 * Supports monthly and yearly premium plans.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        // Product IDs — must match Google Play Console configuration
        const val SUBSCRIPTION_MONTHLY = "civileg_premium_monthly"
        const val SUBSCRIPTION_YEARLY = "civileg_premium_yearly"
        
        // Base64-encoded public key for purchase verification (set from server)
        private const val LICENSE_KEY = ""
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { handlePurchases(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingEvent.postValue(BillingEvent.PurchaseCancelled)
            }
            else -> {
                _billingEvent.postValue(
                    BillingEvent.PurchaseError(billingResult.debugMessage)
                )
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    private val _isPremium = MutableLiveData<Boolean>()
    val isPremium: LiveData<Boolean> = _isPremium

    private val _billingEvent = MutableLiveData<BillingEvent>()
    val billingEvent: LiveData<BillingEvent> = _billingEvent

    private val _subscriptionDetails = MutableLiveData<List<SubscriptionDetail>>()
    val subscriptionDetails: LiveData<List<SubscriptionDetail>> = _subscriptionDetails

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    sealed class BillingEvent {
        data object PurchaseSuccess : BillingEvent()
        data object PurchaseCancelled : BillingEvent()
        data class PurchaseError(val message: String) : BillingEvent()
        data object AlreadySubscribed : BillingEvent()
        data class SubscriptionRestored(val isPremium: Boolean) : BillingEvent()
    }

    data class SubscriptionDetail(
        val productId: String,
        val title: String,
        val description: String,
        val price: String,
        val currencyCode: String,
        val offerToken: String,
        val isFreeTrial: Boolean = false,
        val freeTrialPeriod: String? = null
    )

    /**
     * Start billing client connection. Call in MainActivity.onCreate().
     */
    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryActivePurchases()
                    querySubscriptionDetails()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Retry connection on next purchase attempt
            }
        })
    }

    /**
     * Launch subscription purchase flow.
     */
    fun launchSubscription(activity: Activity, productId: String = SUBSCRIPTION_MONTHLY) {
        if (!billingClient.isReady) {
            startConnection()
            _billingEvent.postValue(BillingEvent.PurchaseError("Billing not ready"))
            return
        }

        val productDetails = productDetailsMap[productId]
        val offerToken = _subscriptionDetails.value?.find { it.productId == productId }?.offerToken

        if (productDetails == null || offerToken == null) {
            _billingEvent.postValue(BillingEvent.PurchaseError("Product details not loaded"))
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, params)
    }

    /**
     * Restore previously purchased subscriptions.
     */
    fun restorePurchases() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        queryActivePurchases()
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                } else {
                    grantPremiumAccess(purchase)
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            billingClient.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    grantPremiumAccess(purchase)
                }
            }
        }
    }

    private fun grantPremiumAccess(purchase: Purchase) {
        val isSub = purchase.products.any { 
            it == SUBSCRIPTION_MONTHLY || it == SUBSCRIPTION_YEARLY 
        }
        if (isSub) {
            CoroutineScope(Dispatchers.IO).launch {
                preferencesManager.setPremiumUser(true)
            }
            _isPremium.postValue(true)
            _billingEvent.postValue(BillingEvent.PurchaseSuccess)
        }
    }

    private fun queryActivePurchases() {
        if (!billingClient.isReady) return

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasActiveSub = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    it.products.any { p -> p == SUBSCRIPTION_MONTHLY || p == SUBSCRIPTION_YEARLY }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    preferencesManager.setPremiumUser(hasActiveSub)
                }
                _isPremium.postValue(hasActiveSub)
                _billingEvent.postValue(BillingEvent.SubscriptionRestored(hasActiveSub))
                
                // Acknowledge unacknowledged purchases
                purchases.filter { !it.isAcknowledged }.forEach { acknowledgePurchase(it) }
            }
        }
    }

    private fun querySubscriptionDetails() {
        val productList = listOf(SUBSCRIPTION_MONTHLY, SUBSCRIPTION_YEARLY).map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { detail ->
                    productDetailsMap[detail.productId] = detail
                }
                val details = productDetailsList.mapNotNull { detail ->
                    val offer = detail.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                    val phaseList = offer.pricingPhases.pricingPhaseList
                    val lastPhase = phaseList.lastOrNull() ?: return@mapNotNull null
                    val firstPhase = phaseList.firstOrNull()
                    SubscriptionDetail(
                        productId = detail.productId,
                        title = detail.title,
                        description = detail.description,
                        price = lastPhase.formattedPrice,
                        currencyCode = lastPhase.priceCurrencyCode,
                        offerToken = offer.offerToken,
                        isFreeTrial = phaseList.size > 1,
                        freeTrialPeriod = firstPhase?.formattedPrice
                    )
                }
                _subscriptionDetails.postValue(details)
            }
        }
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
