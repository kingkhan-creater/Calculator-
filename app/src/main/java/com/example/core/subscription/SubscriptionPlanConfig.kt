package com.example.core.subscription

object SubscriptionPlanConfig {

    private val FREE_ENTITLEMENTS = setOf(
        Entitlement.BASIC_LOCAL_VAULT,
        Entitlement.BASIC_MEDIA_MANAGEMENT,
        Entitlement.LIMITED_CLOUD_STORAGE
    )

    private val PREMIUM_ENTITLEMENTS = setOf(
        Entitlement.BASIC_LOCAL_VAULT,
        Entitlement.BASIC_MEDIA_MANAGEMENT,
        Entitlement.LARGER_CLOUD_STORAGE,
        Entitlement.AUTOMATIC_CLOUD_BACKUP,
        Entitlement.CLOUD_RESTORE,
        Entitlement.CROSS_DEVICE_VAULT_SYNC,
        Entitlement.LARGER_RECORDING_LIMITS,
        Entitlement.HIGHER_MEDIA_LIMITS,
        Entitlement.ADVANCED_RECOVERY_FEATURES,
        Entitlement.NO_ADVERTISEMENTS
    )

    val FREE_PLAN_FEATURES = PlanFeatures(
        plan = SubscriptionPlan.FREE,
        maxCloudStorageBytes = 100L * 1024L * 1024L, // 100 MB limited cloud storage
        maxMediaItemCount = 50,
        maxRecordingDurationSeconds = 60L, // 1 minute
        isAutoBackupEnabled = false,
        isCloudRestoreEnabled = false,
        isCrossDeviceSyncEnabled = false,
        isAdvancedRecoveryEnabled = false,
        hasAds = true,
        grantedEntitlements = FREE_ENTITLEMENTS
    )

    val PREMIUM_PLAN_FEATURES = PlanFeatures(
        plan = SubscriptionPlan.PREMIUM,
        maxCloudStorageBytes = 50L * 1024L * 1024L * 1024L, // 50 GB
        maxMediaItemCount = 100_000,
        maxRecordingDurationSeconds = 3600L * 4L, // 4 hours
        isAutoBackupEnabled = true,
        isCloudRestoreEnabled = true,
        isCrossDeviceSyncEnabled = true,
        isAdvancedRecoveryEnabled = true,
        hasAds = false,
        grantedEntitlements = PREMIUM_ENTITLEMENTS
    )

    fun getFeaturesForPlan(plan: SubscriptionPlan): PlanFeatures {
        return when (plan) {
            SubscriptionPlan.FREE -> FREE_PLAN_FEATURES
            SubscriptionPlan.PREMIUM -> PREMIUM_PLAN_FEATURES
        }
    }
}
