package com.example.integration.firebase

interface FirebaseServiceContract {
    suspend fun syncVaultMetadata(): Result<Unit>
    suspend fun isCloudSyncAvailable(): Boolean
}
