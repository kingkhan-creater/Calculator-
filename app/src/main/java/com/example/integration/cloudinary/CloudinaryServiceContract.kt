package com.example.integration.cloudinary

data class CloudinaryUploadResult(
    val publicId: String,
    val secureUrl: String,
    val assetType: String,
    val bytes: Long
)

interface CloudinaryServiceContract {
    /**
     * Upload raw unencrypted media (photos/videos) directly to Cloudinary for ultra-fast performance
     * and zero mobile CPU freezing.
     */
    suspend fun uploadMedia(
        mediaId: String,
        fileBytes: ByteArray,
        mimeType: String,
        fileName: String = ""
    ): Result<CloudinaryUploadResult>

    suspend fun uploadEncryptedMedia(
        mediaId: String,
        encryptedFileBytes: ByteArray,
        mimeType: String
    ): Result<CloudinaryUploadResult>

    suspend fun deleteAsset(publicId: String): Result<Unit>
}

