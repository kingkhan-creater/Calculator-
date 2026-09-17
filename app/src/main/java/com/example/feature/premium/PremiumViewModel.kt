package com.example.feature.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.billing.BillingConnectionState
import com.example.core.billing.BillingConstants
import com.example.core.billing.BillingManager
import com.example.core.billing.PurchaseFlowResult
import com.example.core.billing.SubscriptionProductDetails
import com.example.core.subscription.EntitlementManager
import com.example.core.subscription.SubscriptionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PremiumUiState(
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    val isPremium: Boolean = false,
    val activePlanName: String? = null,
    val activeLicenseKey: String? = null,
    val billingConnectionState: BillingConnectionState = BillingConnectionState.DISCONNECTED,
    val availableProducts: List<SubscriptionProductDetails> = emptyList(),
    val selectedProduct: SubscriptionProductDetails? = null,
    val isProcessingPurchase: Boolean = false,
    val isRestoring: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class PremiumViewModel(
    private val billingManager: BillingManager,
    private val entitlementManager: EntitlementManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        observeSubscriptionState()
        observeBillingState()
        observeBillingPurchases()
        observePurchaseEvents()
        
        // Start connection
        billingManager.startBillingConnection()
    }

    private fun observeSubscriptionState() {
        viewModelScope.launch {
            entitlementManager.currentSubscriptionState.collectLatest { state ->
                _uiState.update {
                    it.copy(
                        subscriptionStatus = state.status,
                        isPremium = state.isPremiumActive
                    )
                }
            }
        }
    }

    private fun observeBillingState() {
        viewModelScope.launch {
            billingManager.connectionState.collectLatest { connState ->
                _uiState.update { it.copy(billingConnectionState = connState) }
                if (connState == BillingConnectionState.CONNECTED) {
                    billingManager.querySubscriptionProducts()
                    billingManager.queryPurchases()
                }
            }
        }

        viewModelScope.launch {
            billingManager.availableProducts.collectLatest { products ->
                val defaultProduct = products.firstOrNull {
                    it.productId == BillingConstants.PREMIUM_SUBSCRIPTION_PRODUCT_ID
                } ?: products.firstOrNull()

                _uiState.update {
                    it.copy(
                        availableProducts = products,
                        selectedProduct = it.selectedProduct ?: defaultProduct
                    )
                }
            }
        }
    }

    private fun observeBillingPurchases() {
        viewModelScope.launch {
            billingManager.activePurchases.collectLatest { purchases ->
                entitlementManager.updateBillingPurchases(purchases)
            }
        }
    }

    private fun observePurchaseEvents() {
        viewModelScope.launch {
            billingManager.purchaseFlowEvents.collectLatest { event ->
                when (event) {
                    is PurchaseFlowResult.Launching -> {
                        _uiState.update { it.copy(isProcessingPurchase = true, errorMessage = null) }
                    }
                    is PurchaseFlowResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isProcessingPurchase = false,
                                successMessage = "Subscription activated! Welcome to Calculator Vault Premium."
                            )
                        }
                    }
                    is PurchaseFlowResult.Pending -> {
                        _uiState.update {
                            it.copy(
                                isProcessingPurchase = false,
                                successMessage = "Purchase is pending confirmation from Google Play."
                            )
                        }
                    }
                    is PurchaseFlowResult.UserCanceled -> {
                        _uiState.update { it.copy(isProcessingPurchase = false) }
                    }
                    is PurchaseFlowResult.BillingUnavailable -> {
                        _uiState.update {
                            it.copy(
                                isProcessingPurchase = false,
                                errorMessage = event.message
                            )
                        }
                    }
                    is PurchaseFlowResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isProcessingPurchase = false,
                                errorMessage = "Purchase failed: ${event.message.ifBlank { "Error code ${event.responseCode}" }}"
                            )
                        }
                    }
                    PurchaseFlowResult.Idle -> {
                        _uiState.update { it.copy(isProcessingPurchase = false) }
                    }
                }
            }
        }
    }

    fun selectProduct(product: SubscriptionProductDetails) {
        _uiState.update { it.copy(selectedProduct = product) }
    }

    fun subscribe(activity: Activity) {
        val product = _uiState.value.selectedProduct
        if (product == null) {
            _uiState.update { it.copy(errorMessage = "Subscription product is not yet available. Please check connection.") }
            return
        }

        val success = billingManager.launchBillingFlow(activity, product)
        if (!success) {
            _uiState.update { it.copy(errorMessage = "Unable to launch Google Play purchase flow.") }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, errorMessage = null) }
            try {
                billingManager.queryPurchases()
                val purchases = billingManager.activePurchases.value
                entitlementManager.updateBillingPurchases(purchases)
                val isPrem = entitlementManager.currentSubscriptionState.value.isPremiumActive
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        successMessage = if (isPrem) {
                            "Purchases restored successfully! Premium active."
                        } else {
                            "No active subscriptions found for this Google Play account."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        errorMessage = "Restore purchases error: ${e.message}"
                    )
                }
            }
        }
    }

    fun retryConnection() {
        billingManager.startBillingConnection()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun redeemLicenseKey(context: android.content.Context, code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, errorMessage = null) }
            val result = com.example.core.subscription.LicenseKeyManager.redeemLicenseKey(context, code)
            result.onSuccess { subState ->
                entitlementManager.setAdminOverrideMode(true)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        isPremium = true,
                        activePlanName = com.example.core.subscription.LicenseKeyManager.getCachedPlan(context),
                        activeLicenseKey = code.trim().uppercase(),
                        successMessage = "License Key redeemed successfully! Premium activated."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        errorMessage = error.message ?: "Failed to redeem license key."
                    )
                }
            }
        }
    }

    fun restoreWithLicenseKey(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, errorMessage = null) }
            val state = com.example.core.subscription.LicenseKeyManager.checkSubscriptionStatus(context)
            if (state.isPremiumActive) {
                entitlementManager.setAdminOverrideMode(true)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        isPremium = true,
                        activePlanName = com.example.core.subscription.LicenseKeyManager.getCachedPlan(context),
                        activeLicenseKey = com.example.core.subscription.LicenseKeyManager.getCachedLicenseCode(context),
                        successMessage = "Subscription restored! Premium is active on this device."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        errorMessage = "No active license key found for this device. Please enter your activation key."
                    )
                }
            }
        }
    }

    class Factory(
        private val billingManager: BillingManager,
        private val entitlementManager: EntitlementManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PremiumViewModel::class.java)) {
                return PremiumViewModel(billingManager, entitlementManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
