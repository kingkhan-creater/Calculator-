package com.example.core.subscription

enum class SubscriptionPlan {
    FREE,
    PREMIUM
}

enum class SubscriptionStatus {
    FREE,
    PREMIUM_ACTIVE,
    SUBSCRIPTION_ACTIVE,
    SUBSCRIPTION_CANCELED_ACTIVE,
    SUBSCRIPTION_PENDING,
    SUBSCRIPTION_EXPIRED
}

enum class Entitlement {
    // FREE features:
    BASIC_LOCAL_VAULT,
    BASIC_MEDIA_MANAGEMENT,
    LIMITED_CLOUD_STORAGE,

    // PREMIUM features:
    LARGER_CLOUD_STORAGE,
    AUTOMATIC_CLOUD_BACKUP,
    CLOUD_RESTORE,
    CROSS_DEVICE_VAULT_SYNC,
    LARGER_RECORDING_LIMITS,
    HIGHER_MEDIA_LIMITS,
    ADVANCED_RECOVERY_FEATURES,
    NO_ADVERTISEMENTS
}

data class PlanFeatures(
    val plan: SubscriptionPlan,
    val maxCloudStorageBytes: Long,
    val maxMediaItemCount: Int,
    val maxRecordingDurationSeconds: Long,
    val isAutoBackupEnabled: Boolean,
    val isCloudRestoreEnabled: Boolean,
    val isCrossDeviceSyncEnabled: Boolean,
    val isAdvancedRecoveryEnabled: Boolean,
    val hasAds: Boolean,
    val grantedEntitlements: Set<Entitlement>
)

data class SubscriptionState(
    val userId: String = "anonymous",
    val plan: SubscriptionPlan = SubscriptionPlan.FREE,
    val status: SubscriptionStatus = SubscriptionStatus.FREE,
    val isActive: Boolean = false,
    val expiryTimestamp: Long? = null,
    val productId: String? = null,
    val isAutoRenewing: Boolean = true,
    val serverVerifiedAt: Long = 0L,
    val isServerVerified: Boolean = false,
    val isBillingVerified: Boolean = false
) {
    val isPremiumActive: Boolean
        get() {
            if (plan != SubscriptionPlan.PREMIUM && status != SubscriptionStatus.PREMIUM_ACTIVE && status != SubscriptionStatus.SUBSCRIPTION_ACTIVE && status != SubscriptionStatus.SUBSCRIPTION_CANCELED_ACTIVE) {
                return false
            }
            if (!isActive) return false
            if (expiryTimestamp != null && expiryTimestamp <= System.currentTimeMillis()) return false
            return isServerVerified || isBillingVerified
        }
}

