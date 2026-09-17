package com.example.core.subscription

import android.content.Context
import android.util.Log
import com.example.core.auth.GuestCloudIdentityManager
import com.example.feature.backup.AnonymousCloudBackupManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages No-Gmail Premium subscriptions using License Keys / Activation Vouchers.
 * Features:
 * - Redeems Monthly (30 days), Yearly (365 days), and Lifetime keys
 * - Persists offline in private SharedPreferences (persists across sessions)
 * - Safe against uninstalls: User can re-enter their key anytime or restore by device ID
 * - Authoritatively synchronizes with Firebase Firestore ('license_keys' and 'users')
 */
object LicenseKeyManager {

    private const val TAG = "LicenseKeyManager"
    private const val PREFS_NAME = "vault_license_prefs"
    private const val KEY_CODE = "active_license_code"
    private const val KEY_PLAN = "active_license_plan"
    private const val KEY_EXPIRY = "active_license_expiry"
    private const val KEY_IS_PREMIUM = "active_license_is_premium"

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    /**
     * Attempts to redeem an activation key for the current user/device.
     */
    suspend fun redeemLicenseKey(context: Context, rawKey: String): Result<SubscriptionState> = withContext(Dispatchers.IO) {
        val cleanKey = rawKey.trim().uppercase()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid activation code."))
        }

        try {
            val uid = GuestCloudIdentityManager.getOrCreateGuestIdentity(context)
            val deviceId = AnonymousCloudBackupManager.getDeviceId(context)

            val keyDocRef = firestore.collection("license_keys").document(cleanKey)
            val docSnap = keyDocRef.get().await()

            if (!docSnap.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid activation code. Please check and try again."))
            }

            val status = docSnap.getString("status") ?: "ACTIVE"
            if (status.equals("REVOKED", ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException("This activation code has been revoked by admin."))
            }

            val isRedeemed = docSnap.getBoolean("isRedeemed") ?: false
            val redeemedByUid = docSnap.getString("redeemedByUid")
            val redeemedByDeviceId = docSnap.getString("redeemedByDeviceId")
            val planType = (docSnap.getString("planType") ?: "LIFETIME").uppercase()
            val validityDays = docSnap.getLong("validityDays") ?: if (planType == "MONTHLY") 30L else if (planType == "YEARLY") 365L else 99999L

            val now = System.currentTimeMillis()

            // If already redeemed, verify if it belongs to this device/user (Restore flow)
            if (isRedeemed) {
                val matchesUser = (redeemedByUid != null && redeemedByUid == uid) || 
                                  (redeemedByDeviceId != null && redeemedByDeviceId == deviceId)
                if (!matchesUser) {
                    return@withContext Result.failure(IllegalStateException("This activation code is already redeemed on another device."))
                }
            }

            // Calculate expiry timestamp
            val existingExpiresAt = docSnap.getLong("expiresAt")
            val expiresAt: Long? = if (planType == "LIFETIME") {
                null
            } else if (existingExpiresAt != null && existingExpiresAt > 0L) {
                existingExpiresAt
            } else {
                now + (validityDays * 24L * 60L * 60L * 1000L)
            }

            if (expiresAt != null && expiresAt <= now) {
                return@withContext Result.failure(IllegalStateException("This license code has expired."))
            }

            // Update license_keys document in Firestore
            val keyUpdate = mapOf(
                "isRedeemed" to true,
                "redeemedByUid" to uid,
                "redeemedByDeviceId" to deviceId,
                "redeemedAt" to (docSnap.getLong("redeemedAt") ?: now),
                "expiresAt" to (expiresAt ?: 0L),
                "lastVerifiedAt" to now,
                "status" to "ACTIVE"
            )
            keyDocRef.set(keyUpdate, SetOptions.merge()).await()

            // Update user profile document in Firestore
            val userUpdate = mapOf(
                "isPremium" to true,
                "subscriptionPlan" to planType,
                "licenseKey" to cleanKey,
                "subscriptionExpiryTimestamp" to (expiresAt ?: 0L),
                "premiumUpdatedAt" to FieldValue.serverTimestamp(),
                "accountStatus" to "ACTIVE",
                "deviceId" to deviceId
            )
            firestore.collection("users").document(uid).set(userUpdate, SetOptions.merge()).await()

            // Save to local private cache
            cacheLicense(context, cleanKey, planType, expiresAt, isPremium = true)

            val subState = SubscriptionState(
                userId = uid,
                plan = SubscriptionPlan.PREMIUM,
                status = SubscriptionStatus.PREMIUM_ACTIVE,
                isActive = true,
                expiryTimestamp = expiresAt,
                productId = cleanKey,
                isAutoRenewing = false,
                serverVerifiedAt = now,
                isServerVerified = true,
                isBillingVerified = false
            )

            Log.d(TAG, "Successfully activated Premium using key $cleanKey (Plan: $planType, Expiry: $expiresAt)")
            return@withContext Result.success(subState)

        } catch (e: Exception) {
            Log.e(TAG, "Error redeeming license key: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Checks if a previously saved license or remote user profile is active.
     */
    suspend fun checkSubscriptionStatus(context: Context): SubscriptionState = withContext(Dispatchers.IO) {
        val uid = GuestCloudIdentityManager.getActiveUid() ?: GuestCloudIdentityManager.getOrCreateGuestIdentity(context)
        val deviceId = AnonymousCloudBackupManager.getDeviceId(context)

        // 1. Check local cache first for instant responsive UI
        val cachedCode = getCachedLicenseCode(context)
        val cachedPlan = getCachedPlan(context)
        val cachedExpiry = getCachedExpiry(context)
        val cachedIsPremium = isCachedPremium(context)

        val now = System.currentTimeMillis()
        if (cachedIsPremium && cachedCode != null) {
            if (cachedExpiry == null || cachedExpiry > now) {
                // Background verify with Firestore asynchronously
                verifyWithFirestoreAsync(uid, deviceId, cachedCode, context)
                return@withContext SubscriptionState(
                    userId = uid,
                    plan = SubscriptionPlan.PREMIUM,
                    status = SubscriptionStatus.PREMIUM_ACTIVE,
                    isActive = true,
                    expiryTimestamp = cachedExpiry,
                    productId = cachedCode,
                    isServerVerified = true
                )
            } else {
                clearCache(context)
            }
        }

        // 2. Query Firestore user profile
        try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                val isPrem = userDoc.getBoolean("isPremium") ?: false
                val exp = userDoc.getLong("subscriptionExpiryTimestamp")?.takeIf { it > 0L }
                val code = userDoc.getString("licenseKey")
                val planStr = userDoc.getString("subscriptionPlan") ?: "PREMIUM"

                if (isPrem && (exp == null || exp > now)) {
                    cacheLicense(context, code ?: "ADMIN_GRANT", planStr, exp, true)
                    return@withContext SubscriptionState(
                        userId = uid,
                        plan = SubscriptionPlan.PREMIUM,
                        status = SubscriptionStatus.PREMIUM_ACTIVE,
                        isActive = true,
                        expiryTimestamp = exp,
                        productId = code,
                        isServerVerified = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed remote user status check: ${e.message}")
        }

        return@withContext SubscriptionState(
            userId = uid,
            plan = SubscriptionPlan.FREE,
            status = SubscriptionStatus.FREE,
            isActive = true,
            expiryTimestamp = null,
            isServerVerified = true
        )
    }

    private fun verifyWithFirestoreAsync(uid: String, deviceId: String, key: String, context: Context) {
        // Asynchronous verification to revoke if cancelled on server
        try {
            firestore.collection("license_keys").document(key).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val status = doc.getString("status")
                    if (status.equals("REVOKED", ignoreCase = true)) {
                        clearCache(context)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun cacheLicense(context: Context, code: String, plan: String, expiry: Long?, isPremium: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, code)
            .putString(KEY_PLAN, plan)
            .putLong(KEY_EXPIRY, expiry ?: -1L)
            .putBoolean(KEY_IS_PREMIUM, isPremium)
            .apply()
    }

    fun getCachedLicenseCode(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CODE, null)
    }

    fun getCachedPlan(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PLAN, "PREMIUM") ?: "PREMIUM"
    }

    fun getCachedExpiry(context: Context): Long? {
        val exp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_EXPIRY, -1L)
        return if (exp > 0L) exp else null
    }

    fun isCachedPremium(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_IS_PREMIUM, false)
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
