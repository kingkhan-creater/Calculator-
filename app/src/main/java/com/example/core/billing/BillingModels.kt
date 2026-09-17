package com.example.core.billing

import com.android.billingclient.api.ProductDetails

enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE,
    ERROR
}

data class SubscriptionProductDetails(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val offerToken: String,
    val originalProductDetails: ProductDetails? = null
)

data class BillingPurchaseInfo(
    val orderId: String?,
    val purchaseToken: String,
    val productId: String,
    val purchaseTime: Long,
    val purchaseState: Int,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean
)

sealed class PurchaseFlowResult {
    object Idle : PurchaseFlowResult()
    object Launching : PurchaseFlowResult()
    data class Success(val purchase: BillingPurchaseInfo) : PurchaseFlowResult()
    data class Pending(val purchase: BillingPurchaseInfo) : PurchaseFlowResult()
    object UserCanceled : PurchaseFlowResult()
    data class BillingUnavailable(val message: String) : PurchaseFlowResult()
    data class Error(val responseCode: Int, val message: String) : PurchaseFlowResult()
}
