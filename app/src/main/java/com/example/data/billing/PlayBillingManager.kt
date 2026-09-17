package com.example.data.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.core.billing.BillingConnectionState
import com.example.core.billing.BillingConstants
import com.example.core.billing.BillingManager
import com.example.core.billing.BillingPurchaseInfo
import com.example.core.billing.PurchaseFlowResult
import com.example.core.billing.SubscriptionProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

class PlayBillingManager(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : BillingManager, PurchasesUpdatedListener, BillingClientStateListener {

    private val tag = "PlayBillingManager"

    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _availableProducts = MutableStateFlow<List<SubscriptionProductDetails>>(emptyList())
    override val availableProducts: StateFlow<List<SubscriptionProductDetails>> = _availableProducts.asStateFlow()

    private val _activePurchases = MutableStateFlow<List<BillingPurchaseInfo>>(emptyList())
    override val activePurchases: StateFlow<List<BillingPurchaseInfo>> = _activePurchases.asStateFlow()

    private val _purchaseFlowEvents = MutableSharedFlow<PurchaseFlowResult>(extraBufferCapacity = 16)
    override val purchaseFlowEvents: Flow<PurchaseFlowResult> = _purchaseFlowEvents.asSharedFlow()

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val mainHandler = Handler(Looper.getMainLooper())

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()
    }

    override fun startBillingConnection() {
        if (_connectionState.value == BillingConnectionState.CONNECTING ||
            _connectionState.value == BillingConnectionState.CONNECTED
        ) {
            return
        }

        _connectionState.value = BillingConnectionState.CONNECTING
        try {
            billingClient.startConnection(this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start Google Play Billing connection: ${e.message}")
            _connectionState.value = BillingConnectionState.ERROR
        }
    }

    override fun endBillingConnection() {
        try {
            if (billingClient.isReady) {
                billingClient.endConnection()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error closing BillingClient connection: ${e.message}")
        } finally {
            _connectionState.value = BillingConnectionState.DISCONNECTED
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            reconnectAttempts = 0
            _connectionState.value = BillingConnectionState.CONNECTED
            Log.d(tag, "Google Play Billing setup successful (Response: OK)")
            // Query available products & active purchases
            querySubscriptionProducts()
            queryPurchases()
        } else {
            Log.w(tag, "Google Play Billing setup failed with response code: $responseCode")
            _connectionState.value = if (responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ||
                responseCode == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED
            ) {
                BillingConnectionState.UNAVAILABLE
            } else {
                BillingConnectionState.ERROR
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        _connectionState.value = BillingConnectionState.DISCONNECTED
        Log.w(tag, "Google Play Billing service disconnected. Attempting retry...")
        retryBillingConnectionWithBackoff()
    }

    private fun retryBillingConnectionWithBackoff() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w(tag, "Max billing reconnect attempts ($maxReconnectAttempts) reached.")
            _connectionState.value = BillingConnectionState.UNAVAILABLE
            return
        }

        val delayMs = (1L shl min(reconnectAttempts, 6)) * 1000L
        reconnectAttempts++
        Log.d(tag, "Scheduling billing reconnect attempt #$reconnectAttempts in $delayMs ms")

        mainHandler.postDelayed({
            startBillingConnection()
        }, delayMs)
    }

    override fun querySubscriptionProducts(productIds: List<String>) {
        if (!billingClient.isReady) {
            Log.w(tag, "BillingClient is not ready for querying subscription products")
            return
        }

        val productList = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val parsedList = productDetailsList.mapNotNull { pd ->
                    val offer = pd.subscriptionOfferDetails?.firstOrNull()
                    val phase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    val formattedPrice = phase?.formattedPrice ?: "Subscription"
                    val billingPeriod = formatBillingPeriod(phase?.billingPeriod)

                    SubscriptionProductDetails(
                        productId = pd.productId,
                        title = pd.name.ifBlank { pd.title },
                        description = pd.description,
                        formattedPrice = formattedPrice,
                        billingPeriod = billingPeriod,
                        offerToken = offer?.offerToken ?: "",
                        originalProductDetails = pd
                    )
                }
                _availableProducts.value = parsedList
                Log.d(tag, "Successfully queried ${parsedList.size} subscription products")
            } else {
                Log.w(tag, "Failed to query subscription products. Response code: ${billingResult.responseCode}")
            }
        }
    }

    override fun queryPurchases() {
        if (!billingClient.isReady) {
            Log.w(tag, "BillingClient is not ready for querying purchases")
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchaseInfos = purchasesList.mapNotNull { purchase ->
                    processAndMapPurchase(purchase)
                }
                _activePurchases.value = purchaseInfos
                Log.d(tag, "Successfully queried ${purchaseInfos.size} active subscription purchases")
            } else {
                Log.w(tag, "Failed to query purchases. Response code: ${billingResult.responseCode}")
            }
        }
    }

    override fun launchBillingFlow(activity: Activity, productDetails: SubscriptionProductDetails): Boolean {
        if (!billingClient.isReady) {
            Log.w(tag, "Cannot launch billing flow: BillingClient is not ready")
            _purchaseFlowEvents.tryEmit(
                PurchaseFlowResult.BillingUnavailable("Google Play Billing is currently unavailable. Please try again.")
            )
            return false
        }

        val originalDetails = productDetails.originalProductDetails
        if (originalDetails == null) {
            Log.w(tag, "Cannot launch billing flow: ProductDetails is null for ${productDetails.productId}")
            _purchaseFlowEvents.tryEmit(
                PurchaseFlowResult.Error(
                    BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                    "Subscription product details not found."
                )
            )
            return false
        }

        val offerToken = productDetails.offerToken.ifBlank {
            originalDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(originalDetails)
            .apply {
                if (offerToken.isNotBlank()) {
                    setOfferToken(offerToken)
                }
            }
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        _purchaseFlowEvents.tryEmit(PurchaseFlowResult.Launching)
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        val isOk = billingResult.responseCode == BillingClient.BillingResponseCode.OK
        if (!isOk) {
            Log.w(tag, "Launch billing flow returned response: ${billingResult.responseCode}")
            _purchaseFlowEvents.tryEmit(
                PurchaseFlowResult.Error(billingResult.responseCode, billingResult.debugMessage)
            )
        }
        return isOk
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> = suspendCoroutine { continuation ->
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(tag, "Purchase acknowledged successfully")
                continuation.resume(Result.success(Unit))
            } else {
                Log.w(tag, "Failed to acknowledge purchase. Code: ${billingResult.responseCode}")
                continuation.resume(
                    Result.failure(Exception("Acknowledgement failed with code: ${billingResult.responseCode}"))
                )
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val responseCode = billingResult.responseCode
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    val purchaseInfos = mutableListOf<BillingPurchaseInfo>()
                    for (purchase in purchases) {
                        val info = processAndMapPurchase(purchase)
                        if (info != null) {
                            purchaseInfos.add(info)
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                _purchaseFlowEvents.tryEmit(PurchaseFlowResult.Success(info))
                            } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                                _purchaseFlowEvents.tryEmit(PurchaseFlowResult.Pending(info))
                            }
                        }
                    }
                    _activePurchases.update { current ->
                        (current + purchaseInfos).distinctBy { it.purchaseToken }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(tag, "User canceled Google Play purchase flow")
                _purchaseFlowEvents.tryEmit(PurchaseFlowResult.UserCanceled)
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                Log.w(tag, "Billing unavailable during purchase update: ${billingResult.debugMessage}")
                _purchaseFlowEvents.tryEmit(
                    PurchaseFlowResult.BillingUnavailable(billingResult.debugMessage.ifBlank { "Billing service is unavailable." })
                )
            }
            else -> {
                Log.w(tag, "Purchase update error: code $responseCode, message: ${billingResult.debugMessage}")
                _purchaseFlowEvents.tryEmit(
                    PurchaseFlowResult.Error(responseCode, billingResult.debugMessage)
                )
            }
        }
    }

    private fun processAndMapPurchase(purchase: Purchase): BillingPurchaseInfo? {
        val productId = purchase.products.firstOrNull() ?: return null

        // Auto-acknowledge if purchased and unacknowledged
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            externalScope.launch {
                try {
                    acknowledgePurchase(purchase.purchaseToken)
                } catch (e: Exception) {
                    Log.w(tag, "Error acknowledging purchase in background: ${e.message}")
                }
            }
        }

        return BillingPurchaseInfo(
            orderId = purchase.orderId,
            purchaseToken = purchase.purchaseToken,
            productId = productId,
            purchaseTime = purchase.purchaseTime,
            purchaseState = purchase.purchaseState,
            isAcknowledged = purchase.isAcknowledged,
            isAutoRenewing = purchase.isAutoRenewing
        )
    }

    private fun formatBillingPeriod(isoPeriod: String?): String {
        return when (isoPeriod) {
            "P1M" -> "Monthly"
            "P3M" -> "Quarterly"
            "P6M" -> "Bi-annually"
            "P1Y" -> "Yearly"
            "P1W" -> "Weekly"
            null -> "Per Period"
            else -> isoPeriod
        }
    }
}
