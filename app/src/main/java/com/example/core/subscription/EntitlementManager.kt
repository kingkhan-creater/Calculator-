package com.example.core.subscription

import com.example.core.billing.BillingPurchaseInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EntitlementManager {
    val currentSubscriptionState: StateFlow<SubscriptionState>
    val currentPlanFeatures: StateFlow<PlanFeatures>
    val isPremium: StateFlow<Boolean>

    fun observeEntitlements(): Flow<Set<Entitlement>>
    fun isEntitled(entitlement: Entitlement): Boolean
    fun canPerformAction(action: EntitlementAction): EntitlementCheckResult

    suspend fun refreshSubscriptionFromServer(userId: String): Result<SubscriptionState>
    fun updateBillingPurchases(purchases: List<BillingPurchaseInfo>)
    fun setAdminOverrideMode(isPremium: Boolean?)
}

sealed class EntitlementAction {
    data class UploadCloudMedia(val fileSizeBytes: Long, val currentUsageBytes: Long) : EntitlementAction()
    data class ImportMediaItem(val currentItemCount: Int) : EntitlementAction()
    data class StartRecording(val durationSeconds: Long) : EntitlementAction()
    object TriggerAutoBackup : EntitlementAction()
    object PerformCloudRestore : EntitlementAction()
    object SyncCrossDevice : EntitlementAction()
    object UseAdvancedRecovery : EntitlementAction()
}

sealed class EntitlementCheckResult {
    object Allowed : EntitlementCheckResult()
    data class Denied(val requiredEntitlement: Entitlement, val reason: String) : EntitlementCheckResult()
}
