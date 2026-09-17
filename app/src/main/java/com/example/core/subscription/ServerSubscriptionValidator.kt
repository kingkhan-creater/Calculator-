package com.example.core.subscription

interface ServerSubscriptionValidator {
    /**
     * Queries the secure backend / Firestore server endpoint to retrieve
     * the authoritative subscription state for the user.
     */
    suspend fun fetchAuthoritativeSubscription(userId: String): Result<SubscriptionState>
}
