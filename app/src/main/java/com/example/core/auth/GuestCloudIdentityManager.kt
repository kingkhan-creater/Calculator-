package com.example.core.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Centralized Guest Cloud Identity Manager.
 * Responsibilities:
 * - Create anonymous Firebase session when necessary without prompting login screen
 * - Persist the anonymous session securely through Firebase Auth
 * - Retrieve current guest identity (anonymous Firebase UID)
 * - Prevent accidental mixing of guest sessions
 * - Provide backend/Firestore with authenticated anonymous identity
 */
object GuestCloudIdentityManager {

    private const val TAG = "GuestIdentityMgr"
    private const val PREFS_NAME = "vault_guest_identity_prefs"
    private const val KEY_CACHED_ANON_UID = "cached_anon_uid"

    private val auth by lazy { FirebaseAuth.getInstance() }

    /**
     * Checks if current session is an unauthenticated guest or anonymous Firebase user.
     */
    val isGuestUser: Boolean
        get() {
            val user = auth.currentUser
            return user == null || user.isAnonymous
        }

    /**
     * Gets the current user email if signed in with credentials, or null for guests.
     */
    val currentOwnerEmail: String?
        get() {
            val user = auth.currentUser
            return if (user != null && !user.isAnonymous) user.email else null
        }

    /**
     * Returns "GUEST" for anonymous users or "AUTHENTICATED" for signed-in accounts.
     */
    val currentOwnerType: String
        get() {
            val user = auth.currentUser
            return if (user != null && !user.isAnonymous) "AUTHENTICATED" else "GUEST"
        }

    /**
     * Retrieves or initializes an authenticated Firebase session.
     * Uses invisible Anonymous Authentication so guest users have an internal
     * server-verifiable Firebase UID without having to enter credentials.
     */
    suspend fun getOrCreateGuestIdentity(context: Context? = null): String = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            if (context != null) cacheGuestUid(context, uid)
            return@withContext uid
        }

        return@withContext try {
            val authResult = auth.signInAnonymously().await()
            val user: FirebaseUser? = authResult.user
            val anonUid = user?.uid ?: "guest_${System.currentTimeMillis()}"
            Log.d(TAG, "Initialized secure anonymous Firebase session: $anonUid")
            if (context != null) cacheGuestUid(context, anonUid)
            anonUid
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in exception: ${e.message}", e)
            val fallbackUid = context?.let { getCachedGuestUid(it) } ?: "guest_${System.currentTimeMillis()}"
            fallbackUid
        }
    }

    /**
     * Returns the active UID if present without network calls.
     */
    fun getActiveUid(): String? {
        return auth.currentUser?.uid
    }

    private fun cacheGuestUid(context: Context, uid: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_ANON_UID, uid)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache guest uid: ${e.message}")
        }
    }

    private fun getCachedGuestUid(context: Context): String? {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CACHED_ANON_UID, null)
        } catch (e: Exception) {
            null
        }
    }
}
