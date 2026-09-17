package com.example.data.subscription

import com.android.billingclient.api.Purchase
import com.example.core.billing.BillingPurchaseInfo
import com.example.core.subscription.Entitlement
import com.example.core.subscription.EntitlementAction
import com.example.core.subscription.EntitlementCheckResult
import com.example.core.subscription.EntitlementManager
import com.example.core.subscription.PlanFeatures
import com.example.core.subscription.ServerSubscriptionValidator
import com.example.core.subscription.SubscriptionPlan
import com.example.core.subscription.SubscriptionPlanConfig
import com.example.core.subscription.SubscriptionState
import com.example.core.subscription.SubscriptionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class EntitlementManagerImpl(
    private val serverValidator: ServerSubscriptionValidator
) : EntitlementManager {

    private val _currentSubscriptionState = MutableStateFlow(
        SubscriptionState(
            userId = "anonymous",
            plan = SubscriptionPlan.FREE,
            status = SubscriptionStatus.FREE,
            isActive = true,
            expiryTimestamp = null,
            serverVerifiedAt = 0L,
            isServerVerified = false,
            isBillingVerified = false
        )
    )
    override val currentSubscriptionState: StateFlow<SubscriptionState> = _currentSubscriptionState.asStateFlow()

    private val _currentPlanFeatures = MutableStateFlow(
        SubscriptionPlanConfig.FREE_PLAN_FEATURES
    )
    override val currentPlanFeatures: StateFlow<PlanFeatures> = _currentPlanFeatures.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private var adminOverride: Boolean? = null

    override fun setAdminOverrideMode(isPremium: Boolean?) {
        adminOverride = isPremium
        applyState(_currentSubscriptionState.value)
    }

    override fun observeEntitlements(): Flow<Set<Entitlement>> {
        return _currentPlanFeatures.map { it.grantedEntitlements }
    }

    override fun isEntitled(entitlement: Entitlement): Boolean {
        val effectiveIsPremium = adminOverride ?: _currentSubscriptionState.value.isPremiumActive
        val effectivePlan = if (effectiveIsPremium) SubscriptionPlan.PREMIUM else SubscriptionPlan.FREE
        val features = SubscriptionPlanConfig.getFeaturesForPlan(effectivePlan)
        return features.grantedEntitlements.contains(entitlement)
    }

    override fun canPerformAction(action: EntitlementAction): EntitlementCheckResult {
        val effectiveIsPremium = adminOverride ?: _currentSubscriptionState.value.isPremiumActive
        val effectivePlan = if (effectiveIsPremium) SubscriptionPlan.PREMIUM else SubscriptionPlan.FREE
        val features = SubscriptionPlanConfig.getFeaturesForPlan(effectivePlan)

        return when (action) {
            is EntitlementAction.UploadCloudMedia -> {
                if (action.currentUsageBytes + action.fileSizeBytes > features.maxCloudStorageBytes) {
                    EntitlementCheckResult.Denied(
                        requiredEntitlement = Entitlement.LARGER_CLOUD_STORAGE,
                        reason = "Cloud storage limit reached for ${effectivePlan.name} plan"
                    )
                } else {
                    EntitlementCheckResult.Allowed
                }
            }
            is EntitlementAction.ImportMediaItem -> {
                if (action.currentItemCount >= features.maxMediaItemCount) {
                    EntitlementCheckResult.Denied(
                        requiredEntitlement = Entitlement.HIGHER_MEDIA_LIMITS,
                        reason = "Media item limit of ${features.maxMediaItemCount} reached"
                    )
                } else {
                    EntitlementCheckResult.Allowed
                }
            }
            is EntitlementAction.StartRecording -> {
                if (action.durationSeconds > features.maxRecordingDurationSeconds) {
                    EntitlementCheckResult.Denied(
                        requiredEntitlement = Entitlement.LARGER_RECORDING_LIMITS,
                        reason = "Recording length exceeds ${features.maxRecordingDurationSeconds} seconds limit on Free plan"
                    )
                } else {
                    EntitlementCheckResult.Allowed
                }
            }
            is EntitlementAction.TriggerAutoBackup -> {
                if (features.isAutoBackupEnabled) {
                    EntitlementCheckResult.Allowed
                } else {
                    EntitlementCheckResult.Denied(
                        requiredEntitlement = Entitlement.AUTOMATIC_CLOUD_BACKUP,
                        reason = "Automatic cloud backup is a Premium feature"
                    )
                }
            }
            is EntitlementAction.PerformCloudRestore -> {
                if (features.isCloudRestoreEnabled) {
                    EntitlementCheckResult.Allowed
                } else {
                    EntitlementCheckResult.Denied(
                        requiredEntitlement = Entitlement.CLOUD_RESTORE,
                        reason = "Cloud restore is a Premium feature"
                    )
                }
            }
            is EntitlementAction.SyncCrossDevice -> {
                if (features.isCrossDeviceSyncEnabled) {
                    EntitlementCheckResult.Allowed
                } else {
                    EntitlementCheckResult.Denied(
                        requiredEntitlement = Entitlement.CROSS_DEVICE_VAULT_SYNC,
                        reason = "Cross-device Vault sync is a Premium feature"
                    )
                }
            }
            is EntitlementAction.UseAdvancedRecovery -> {
                if (features.isAdvancedRecoveryEnabled) {
                    EntitlementCheckResult.Allowed
                } else {
                    EntitlementCheckResult.Denied(
                        requiredEntitlement = Entitlement.ADVANCED_RECOVERY_FEATURES,
                        reason = "Advanced recovery features require Premium subscription"
                    )
                }
            }
        }
    }

    override suspend fun refreshSubscriptionFromServer(userId: String): Result<SubscriptionState> {
        val result = serverValidator.fetchAuthoritativeSubscription(userId)
        result.onSuccess { verifiedState ->
            if (verifiedState.isServerVerified) {
                applyState(verifiedState)
            } else {
                applyState(
                    verifiedState.copy(
                        plan = SubscriptionPlan.FREE,
                        status = SubscriptionStatus.FREE,
                        isServerVerified = false
                    )
                )
            }
        }.onFailure {
            // Keep current verified state if present
        }
        return result
    }

    override fun updateBillingPurchases(purchases: List<BillingPurchaseInfo>) {
        val activePurchase = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pendingPurchase = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PENDING }

        val currentState = _currentSubscriptionState.value

        when {
            activePurchase != null -> {
                val status = if (activePurchase.isAutoRenewing) {
                    SubscriptionStatus.SUBSCRIPTION_ACTIVE
                } else {
                    SubscriptionStatus.SUBSCRIPTION_CANCELED_ACTIVE
                }
                val newState = currentState.copy(
                    plan = SubscriptionPlan.PREMIUM,
                    status = status,
                    isActive = true,
                    productId = activePurchase.productId,
                    isAutoRenewing = activePurchase.isAutoRenewing,
                    isBillingVerified = true
                )
                applyState(newState)
            }
            pendingPurchase != null -> {
                val newState = currentState.copy(
                    plan = SubscriptionPlan.FREE,
                    status = SubscriptionStatus.SUBSCRIPTION_PENDING,
                    isActive = false,
                    productId = pendingPurchase.productId,
                    isBillingVerified = false
                )
                applyState(newState)
            }
            else -> {
                // No active or pending Google Play subscription
                if (!currentState.isServerVerified) {
                    val status = if (currentState.status == SubscriptionStatus.SUBSCRIPTION_ACTIVE ||
                        currentState.status == SubscriptionStatus.PREMIUM_ACTIVE
                    ) {
                        SubscriptionStatus.SUBSCRIPTION_EXPIRED
                    } else {
                        SubscriptionStatus.FREE
                    }
                    val newState = currentState.copy(
                        plan = SubscriptionPlan.FREE,
                        status = status,
                        isActive = false,
                        isBillingVerified = false
                    )
                    applyState(newState)
                }
            }
        }
    }

    private fun applyState(state: SubscriptionState) {
        _currentSubscriptionState.value = state
        val effectiveIsPremium = adminOverride ?: state.isPremiumActive
        val effectivePlan = if (effectiveIsPremium) SubscriptionPlan.PREMIUM else SubscriptionPlan.FREE
        _currentPlanFeatures.value = SubscriptionPlanConfig.getFeaturesForPlan(effectivePlan)
        _isPremium.value = effectiveIsPremium
    }
}
