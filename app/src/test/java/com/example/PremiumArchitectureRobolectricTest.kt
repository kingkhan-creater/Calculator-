package com.example

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.example.core.billing.BillingConnectionState
import com.example.core.billing.BillingConstants
import com.example.core.billing.BillingManager
import com.example.core.billing.BillingPurchaseInfo
import com.example.core.billing.PurchaseFlowResult
import com.example.core.billing.SubscriptionProductDetails
import com.example.core.subscription.Entitlement
import com.example.core.subscription.EntitlementAction
import com.example.core.subscription.EntitlementCheckResult
import com.example.core.subscription.ServerSubscriptionValidator
import com.example.core.subscription.SubscriptionPlan
import com.example.core.subscription.SubscriptionState
import com.example.core.subscription.SubscriptionStatus
import com.example.data.subscription.EntitlementManagerImpl
import com.example.feature.premium.PremiumViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PremiumArchitectureRobolectricTest {

    private lateinit var fakeBillingManager: FakeBillingManager
    private lateinit var fakeServerValidator: FakeServerValidator
    private lateinit var entitlementManager: EntitlementManagerImpl
    private lateinit var premiumViewModel: PremiumViewModel

    class FakeServerValidator : ServerSubscriptionValidator {
        var stateToReturn = SubscriptionState(
            userId = "user_test",
            plan = SubscriptionPlan.FREE,
            status = SubscriptionStatus.FREE,
            isActive = true,
            isServerVerified = true
        )

        override suspend fun fetchAuthoritativeSubscription(userId: String): Result<SubscriptionState> {
            return Result.success(stateToReturn)
        }
    }

    class FakeBillingManager : BillingManager {
        private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
        override val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

        private val _availableProducts = MutableStateFlow<List<SubscriptionProductDetails>>(emptyList())
        override val availableProducts: StateFlow<List<SubscriptionProductDetails>> = _availableProducts.asStateFlow()

        private val _activePurchases = MutableStateFlow<List<BillingPurchaseInfo>>(emptyList())
        override val activePurchases: StateFlow<List<BillingPurchaseInfo>> = _activePurchases.asStateFlow()

        private val _purchaseFlowEvents = MutableSharedFlow<PurchaseFlowResult>(extraBufferCapacity = 16)
        override val purchaseFlowEvents: Flow<PurchaseFlowResult> = _purchaseFlowEvents.asSharedFlow()

        var launchBillingFlowCalled = false
        var acknowledgedTokens = mutableListOf<String>()

        override fun startBillingConnection() {
            _connectionState.value = BillingConnectionState.CONNECTED
        }

        override fun endBillingConnection() {
            _connectionState.value = BillingConnectionState.DISCONNECTED
        }

        override fun querySubscriptionProducts(productIds: List<String>) {
            _availableProducts.value = listOf(
                SubscriptionProductDetails(
                    productId = BillingConstants.PREMIUM_SUBSCRIPTION_PRODUCT_ID,
                    title = "Monthly Premium Subscription",
                    description = "Encrypted cloud backup, restore, 4K video recording and zero ads",
                    formattedPrice = "$4.99",
                    billingPeriod = "Monthly",
                    offerToken = "token_offer_123"
                )
            )
        }

        override fun queryPurchases() {
            // No-op or maintains activePurchases
        }

        fun emitPurchase(purchase: BillingPurchaseInfo, event: PurchaseFlowResult) {
            _activePurchases.value = listOf(purchase)
            _purchaseFlowEvents.tryEmit(event)
        }

        fun emitConnectionState(state: BillingConnectionState) {
            _connectionState.value = state
        }

        override fun launchBillingFlow(activity: Activity, productDetails: SubscriptionProductDetails): Boolean {
            launchBillingFlowCalled = true
            _purchaseFlowEvents.tryEmit(PurchaseFlowResult.Launching)
            return true
        }

        override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> {
            acknowledgedTokens.add(purchaseToken)
            return Result.success(Unit)
        }
    }

    @Before
    fun setup() {
        fakeBillingManager = FakeBillingManager()
        fakeServerValidator = FakeServerValidator()
        entitlementManager = EntitlementManagerImpl(fakeServerValidator)
        premiumViewModel = PremiumViewModel(fakeBillingManager, entitlementManager)
    }

    @Test
    fun `initial state is free with no active premium entitlement`() {
        val state = entitlementManager.currentSubscriptionState.value
        assertEquals(SubscriptionPlan.FREE, state.plan)
        assertEquals(SubscriptionStatus.FREE, state.status)
        assertFalse(state.isPremiumActive)
        assertFalse(entitlementManager.isPremium.value)

        // Free user cannot perform cloud restore or auto backup
        val autoBackupCheck = entitlementManager.canPerformAction(EntitlementAction.TriggerAutoBackup)
        assertTrue(autoBackupCheck is EntitlementCheckResult.Denied)

        val restoreCheck = entitlementManager.canPerformAction(EntitlementAction.PerformCloudRestore)
        assertTrue(restoreCheck is EntitlementCheckResult.Denied)
    }

    @Test
    fun `active google play subscription purchase grants premium status`() = runTest {
        val purchase = BillingPurchaseInfo(
            orderId = "GPA.1234-5678-9012",
            purchaseToken = "token_abc123",
            productId = BillingConstants.PREMIUM_SUBSCRIPTION_PRODUCT_ID,
            purchaseTime = System.currentTimeMillis(),
            purchaseState = Purchase.PurchaseState.PURCHASED,
            isAcknowledged = true,
            isAutoRenewing = true
        )

        entitlementManager.updateBillingPurchases(listOf(purchase))

        val state = entitlementManager.currentSubscriptionState.value
        assertEquals(SubscriptionPlan.PREMIUM, state.plan)
        assertEquals(SubscriptionStatus.SUBSCRIPTION_ACTIVE, state.status)
        assertTrue(state.isPremiumActive)
        assertTrue(entitlementManager.isPremium.value)

        // Verify premium features are unlocked
        assertTrue(entitlementManager.isEntitled(Entitlement.AUTOMATIC_CLOUD_BACKUP))
        assertTrue(entitlementManager.isEntitled(Entitlement.CLOUD_RESTORE))
        assertTrue(entitlementManager.isEntitled(Entitlement.CROSS_DEVICE_VAULT_SYNC))
        assertTrue(entitlementManager.isEntitled(Entitlement.NO_ADVERTISEMENTS))

        val autoBackupCheck = entitlementManager.canPerformAction(EntitlementAction.TriggerAutoBackup)
        assertTrue(autoBackupCheck is EntitlementCheckResult.Allowed)

        val restoreCheck = entitlementManager.canPerformAction(EntitlementAction.PerformCloudRestore)
        assertTrue(restoreCheck is EntitlementCheckResult.Allowed)
    }

    @Test
    fun `canceled but active subscription maintains active entitlement until expiry`() = runTest {
        val purchase = BillingPurchaseInfo(
            orderId = "GPA.9876-5432-1098",
            purchaseToken = "token_xyz789",
            productId = BillingConstants.PREMIUM_SUBSCRIPTION_PRODUCT_ID,
            purchaseTime = System.currentTimeMillis(),
            purchaseState = Purchase.PurchaseState.PURCHASED,
            isAcknowledged = true,
            isAutoRenewing = false // Canceled in Play Store
        )

        entitlementManager.updateBillingPurchases(listOf(purchase))

        val state = entitlementManager.currentSubscriptionState.value
        assertEquals(SubscriptionPlan.PREMIUM, state.plan)
        assertEquals(SubscriptionStatus.SUBSCRIPTION_CANCELED_ACTIVE, state.status)
        assertTrue(state.isPremiumActive)
        assertTrue(entitlementManager.isPremium.value)
    }

    @Test
    fun `pending subscription purchase does not immediately grant premium until settled`() = runTest {
        val pendingPurchase = BillingPurchaseInfo(
            orderId = "GPA.PENDING-1234",
            purchaseToken = "token_pending_000",
            productId = BillingConstants.PREMIUM_SUBSCRIPTION_PRODUCT_ID,
            purchaseTime = System.currentTimeMillis(),
            purchaseState = Purchase.PurchaseState.PENDING,
            isAcknowledged = false,
            isAutoRenewing = true
        )

        entitlementManager.updateBillingPurchases(listOf(pendingPurchase))

        val state = entitlementManager.currentSubscriptionState.value
        assertEquals(SubscriptionPlan.FREE, state.plan)
        assertEquals(SubscriptionStatus.SUBSCRIPTION_PENDING, state.status)
        assertFalse(state.isPremiumActive)
        assertFalse(entitlementManager.isPremium.value)
    }

    @Test
    fun `expired subscription transitions to expired status and revokes premium`() = runTest {
        // First activate
        val activePurchase = BillingPurchaseInfo(
            orderId = "GPA.ACTIVE-123",
            purchaseToken = "token_active_123",
            productId = BillingConstants.PREMIUM_SUBSCRIPTION_PRODUCT_ID,
            purchaseTime = System.currentTimeMillis(),
            purchaseState = Purchase.PurchaseState.PURCHASED,
            isAcknowledged = true,
            isAutoRenewing = true
        )
        entitlementManager.updateBillingPurchases(listOf(activePurchase))
        assertTrue(entitlementManager.isPremium.value)

        // Then subscription ends / expires (no active purchases returned)
        entitlementManager.updateBillingPurchases(emptyList())

        val state = entitlementManager.currentSubscriptionState.value
        assertEquals(SubscriptionPlan.FREE, state.plan)
        assertEquals(SubscriptionStatus.SUBSCRIPTION_EXPIRED, state.status)
        assertFalse(state.isPremiumActive)
        assertFalse(entitlementManager.isPremium.value)
    }

    @Test
    fun `restore purchases queries billing manager and updates ui state`() = runTest {
        fakeBillingManager.startBillingConnection()
        fakeBillingManager.querySubscriptionProducts()

        premiumViewModel.restorePurchases()

        val uiState = premiumViewModel.uiState.value
        assertNotNull(uiState.successMessage)
    }
}
