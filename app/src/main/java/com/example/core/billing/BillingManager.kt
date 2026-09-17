package com.example.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BillingManager {
    val connectionState: StateFlow<BillingConnectionState>
    val availableProducts: StateFlow<List<SubscriptionProductDetails>>
    val activePurchases: StateFlow<List<BillingPurchaseInfo>>
    val purchaseFlowEvents: Flow<PurchaseFlowResult>

    fun startBillingConnection()
    fun endBillingConnection()
    fun querySubscriptionProducts(productIds: List<String> = BillingConstants.ALL_SUBSCRIPTION_PRODUCT_IDS)
    fun queryPurchases()
    fun launchBillingFlow(activity: Activity, productDetails: SubscriptionProductDetails): Boolean
    suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit>
}
