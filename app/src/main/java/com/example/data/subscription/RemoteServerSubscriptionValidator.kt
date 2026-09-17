package com.example.data.subscription

import com.example.core.subscription.ServerSubscriptionValidator
import com.example.core.subscription.SubscriptionPlan
import com.example.core.subscription.SubscriptionState
import com.example.core.subscription.SubscriptionStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RemoteServerSubscriptionValidator(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ServerSubscriptionValidator {

    override suspend fun fetchAuthoritativeSubscription(userId: String): Result<SubscriptionState> {
        return runCatching {
            val now = System.currentTimeMillis()
            val userDoc = firestore.collection("users").document(userId).get().await()

            if (userDoc.exists()) {
                val isPremium = userDoc.getBoolean("isPremium") ?: false
                val expiryTimestamp = userDoc.getLong("subscriptionExpiryTimestamp")?.takeIf { it > 0L }
                val planStr = userDoc.getString("subscriptionPlan") ?: "PREMIUM"
                val licenseKey = userDoc.getString("licenseKey")

                // Check if premium is active and not expired
                val isNotExpired = expiryTimestamp == null || expiryTimestamp > now

                if (isPremium && isNotExpired) {
                    return@runCatching SubscriptionState(
                        userId = userId,
                        plan = SubscriptionPlan.PREMIUM,
                        status = SubscriptionStatus.PREMIUM_ACTIVE,
                        isActive = true,
                        expiryTimestamp = expiryTimestamp,
                        productId = licenseKey ?: "PREMIUM_ACCESS",
                        isAutoRenewing = false,
                        serverVerifiedAt = now,
                        isServerVerified = true,
                        isBillingVerified = false
                    )
                }
            }

            // Default Free Plan
            SubscriptionState(
                userId = userId,
                plan = SubscriptionPlan.FREE,
                status = SubscriptionStatus.FREE,
                isActive = true,
                expiryTimestamp = null,
                serverVerifiedAt = now,
                isServerVerified = true,
                isBillingVerified = false
            )
        }
    }
}
