package com.example

import com.example.core.subscription.Entitlement
import com.example.core.subscription.EntitlementAction
import com.example.core.subscription.EntitlementCheckResult
import com.example.core.subscription.ServerSubscriptionValidator
import com.example.core.subscription.SubscriptionPlan
import com.example.core.subscription.SubscriptionState
import com.example.data.subscription.EntitlementManagerImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntitlementManagerTest {

    private lateinit var entitlementManager: EntitlementManagerImpl
    private lateinit var mockServerValidator: FakeServerSubscriptionValidator

    class FakeServerSubscriptionValidator : ServerSubscriptionValidator {
        var returnState: SubscriptionState = SubscriptionState(
            userId = "user_123",
            plan = SubscriptionPlan.FREE,
            isActive = true,
            serverVerifiedAt = System.currentTimeMillis(),
            isServerVerified = true
        )

        override suspend fun fetchAuthoritativeSubscription(userId: String): Result<SubscriptionState> {
            return Result.success(returnState)
        }
    }

    @Before
    fun setup() {
        mockServerValidator = FakeServerSubscriptionValidator()
        entitlementManager = EntitlementManagerImpl(mockServerValidator)
    }

    @Test
    fun `default free plan allows basic features and disallows premium features`() {
        assertTrue(entitlementManager.isEntitled(Entitlement.BASIC_LOCAL_VAULT))
        assertTrue(entitlementManager.isEntitled(Entitlement.BASIC_MEDIA_MANAGEMENT))
        assertTrue(entitlementManager.isEntitled(Entitlement.LIMITED_CLOUD_STORAGE))

        assertFalse(entitlementManager.isEntitled(Entitlement.AUTOMATIC_CLOUD_BACKUP))
        assertFalse(entitlementManager.isEntitled(Entitlement.CLOUD_RESTORE))
        assertFalse(entitlementManager.isEntitled(Entitlement.CROSS_DEVICE_VAULT_SYNC))
        assertFalse(entitlementManager.isEntitled(Entitlement.ADVANCED_RECOVERY_FEATURES))
        assertFalse(entitlementManager.isEntitled(Entitlement.NO_ADVERTISEMENTS))
    }

    @Test
    fun `free plan action checks enforce storage and duration constraints`() {
        // Free cloud storage limit is 100 MB
        val allowedUpload = entitlementManager.canPerformAction(
            EntitlementAction.UploadCloudMedia(
                fileSizeBytes = 50L * 1024L * 1024L,
                currentUsageBytes = 20L * 1024L * 1024L
            )
        )
        assertTrue(allowedUpload is EntitlementCheckResult.Allowed)

        val deniedUpload = entitlementManager.canPerformAction(
            EntitlementAction.UploadCloudMedia(
                fileSizeBytes = 90L * 1024L * 1024L,
                currentUsageBytes = 20L * 1024L * 1024L
            )
        )
        assertTrue(deniedUpload is EntitlementCheckResult.Denied)

        // Free recording duration limit is 60s
        val allowedRecording = entitlementManager.canPerformAction(
            EntitlementAction.StartRecording(durationSeconds = 45L)
        )
        assertTrue(allowedRecording is EntitlementCheckResult.Allowed)

        val deniedRecording = entitlementManager.canPerformAction(
            EntitlementAction.StartRecording(durationSeconds = 120L)
        )
        assertTrue(deniedRecording is EntitlementCheckResult.Denied)
    }

    @Test
    fun `server verified premium subscription unlocks all premium entitlements`() = runTest {
        mockServerValidator.returnState = SubscriptionState(
            userId = "user_123",
            plan = SubscriptionPlan.PREMIUM,
            isActive = true,
            expiryTimestamp = System.currentTimeMillis() + 86400000L,
            serverVerifiedAt = System.currentTimeMillis(),
            isServerVerified = true
        )

        val result = entitlementManager.refreshSubscriptionFromServer("user_123")
        assertTrue(result.isSuccess)

        assertTrue(entitlementManager.isEntitled(Entitlement.AUTOMATIC_CLOUD_BACKUP))
        assertTrue(entitlementManager.isEntitled(Entitlement.CLOUD_RESTORE))
        assertTrue(entitlementManager.isEntitled(Entitlement.CROSS_DEVICE_VAULT_SYNC))
        assertTrue(entitlementManager.isEntitled(Entitlement.LARGER_CLOUD_STORAGE))
        assertTrue(entitlementManager.isEntitled(Entitlement.ADVANCED_RECOVERY_FEATURES))
        assertTrue(entitlementManager.isEntitled(Entitlement.NO_ADVERTISEMENTS))

        val autoBackupCheck = entitlementManager.canPerformAction(EntitlementAction.TriggerAutoBackup)
        assertTrue(autoBackupCheck is EntitlementCheckResult.Allowed)
    }

    @Test
    fun `unverified server response falls back safely to free plan`() = runTest {
        mockServerValidator.returnState = SubscriptionState(
            userId = "user_123",
            plan = SubscriptionPlan.PREMIUM,
            isActive = true,
            serverVerifiedAt = 0L,
            isServerVerified = false // Untrusted / unverified state
        )

        entitlementManager.refreshSubscriptionFromServer("user_123")
        assertFalse(entitlementManager.isEntitled(Entitlement.AUTOMATIC_CLOUD_BACKUP))
    }
}
