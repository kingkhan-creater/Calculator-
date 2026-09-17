package com.example.integration.cloudinary

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class CloudinaryServiceImpl(
    private val context: Context? = null,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : CloudinaryServiceContract {

    override suspend fun uploadMedia(
        mediaId: String,
        fileBytes: ByteArray,
        mimeType: String,
        fileName: String
    ): Result<CloudinaryUploadResult> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("User must be authenticated to upload to cloud."))

            val uid = currentUser.uid
            if (uid.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Invalid user UID."))
            }

            val isVideo = mimeType.startsWith("video/")
            val extension = if (isVideo) "mp4" else "jpg"
            val actualFileName = if (fileName.isNotBlank()) fileName else "media_$mediaId.$extension"
            val folder = "users/$uid/vault_media"
            val publicId = "users/$uid/vault_media/$mediaId"

            // 1. Check local Cloudinary configuration
            var config = context?.let { CloudinaryConfigManager.getConfig(it) } ?: CloudinaryConfig()

            // 2. If not configured locally, attempt to read from Firestore (system_config/cloudinary)
            if (!config.isConfigured) {
                try {
                    val remoteDoc = firestore.collection("system_config").document("cloudinary").get().await()
                    if (remoteDoc.exists()) {
                        config = CloudinaryConfig(
                            cloudName = remoteDoc.getString("cloudName") ?: "",
                            uploadPreset = remoteDoc.getString("uploadPreset") ?: "",
                            apiKey = remoteDoc.getString("apiKey") ?: "",
                            apiSecret = remoteDoc.getString("apiSecret") ?: ""
                        )
                    }
                } catch (_: Exception) {}
            }

            // Path A: Unsigned Upload Preset (Zero backend / Cloud Functions required!)
            if (config.cloudName.isNotBlank() && config.uploadPreset.isNotBlank()) {
                val url = "https://api.cloudinary.com/v1_1/${config.cloudName}/auto/upload"
                val requestBodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", actualFileName, fileBytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                    .addFormDataPart("upload_preset", config.uploadPreset)
                    .addFormDataPart("folder", folder)
                    .addFormDataPart("public_id", mediaId)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyBuilder.build())
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBodyString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(responseBodyString)
                        errObj.optJSONObject("error")?.optString("message") ?: responseBodyString
                    } catch (_: Exception) { responseBodyString }
                    return@withContext Result.failure(IOException("Cloudinary upload failed ($errorMsg)"))
                }

                val jsonObject = JSONObject(responseBodyString)
                val resPublicId = jsonObject.optString("public_id", publicId)
                val secureUrl = jsonObject.optString("secure_url", "")
                val assetType = jsonObject.optString("resource_type", "auto")
                val bytes = jsonObject.optLong("bytes", fileBytes.size.toLong())

                return@withContext Result.success(
                    CloudinaryUploadResult(
                        publicId = resPublicId,
                        secureUrl = secureUrl,
                        assetType = assetType,
                        bytes = bytes
                    )
                )
            }

            // Path B: Local Signed Upload (using API Key + API Secret)
            if (config.cloudName.isNotBlank() && config.apiKey.isNotBlank() && config.apiSecret.isNotBlank()) {
                val timestamp = (System.currentTimeMillis() / 1000).toString()
                // Parameters sorted alphabetically for Cloudinary signature: folder, public_id, timestamp
                val paramsToSign = "folder=$folder&public_id=$publicId&timestamp=$timestamp${config.apiSecret}"
                val signature = CloudinaryConfigManager.generateSha1(paramsToSign)

                val url = "https://api.cloudinary.com/v1_1/${config.cloudName}/auto/upload"
                val requestBodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", actualFileName, fileBytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                    .addFormDataPart("api_key", config.apiKey)
                    .addFormDataPart("timestamp", timestamp)
                    .addFormDataPart("signature", signature)
                    .addFormDataPart("folder", folder)
                    .addFormDataPart("public_id", publicId)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyBuilder.build())
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBodyString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(responseBodyString)
                        errObj.optJSONObject("error")?.optString("message") ?: responseBodyString
                    } catch (_: Exception) { responseBodyString }
                    return@withContext Result.failure(IOException("Cloudinary signed upload failed: $errorMsg"))
                }

                val jsonObject = JSONObject(responseBodyString)
                val resPublicId = jsonObject.optString("public_id", publicId)
                val secureUrl = jsonObject.optString("secure_url", "")
                val assetType = jsonObject.optString("resource_type", "auto")
                val bytes = jsonObject.optLong("bytes", fileBytes.size.toLong())

                return@withContext Result.success(
                    CloudinaryUploadResult(
                        publicId = resPublicId,
                        secureUrl = secureUrl,
                        assetType = assetType,
                        bytes = bytes
                    )
                )
            }

            // Path C: Try Firebase Cloud Functions fallback
            try {
                val data = hashMapOf("mediaId" to mediaId)
                val funcResult = functions
                    .getHttpsCallable("generateCloudinaryUploadSignature")
                    .call(data)
                    .await()

                val resultMap = funcResult.data as? Map<*, *>
                if (resultMap != null) {
                    val signature = resultMap["signature"] as? String ?: ""
                    val timestamp = resultMap["timestamp"]?.toString() ?: ""
                    val apiKey = resultMap["apiKey"] as? String ?: ""
                    val cloudName = resultMap["cloudName"] as? String ?: ""
                    val remoteFolder = resultMap["folder"] as? String ?: folder
                    val remotePublicId = resultMap["publicId"] as? String ?: publicId

                    if (signature.isNotBlank() && timestamp.isNotBlank() && apiKey.isNotBlank() && cloudName.isNotBlank()) {
                        val url = "https://api.cloudinary.com/v1_1/$cloudName/auto/upload"
                        val requestBodyBuilder = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", actualFileName, fileBytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                            .addFormDataPart("api_key", apiKey)
                            .addFormDataPart("timestamp", timestamp)
                            .addFormDataPart("signature", signature)
                            .addFormDataPart("folder", remoteFolder)
                            .addFormDataPart("public_id", remotePublicId)

                        val request = Request.Builder().url(url).post(requestBodyBuilder.build()).build()
                        val response = okHttpClient.newCall(request).execute()
                        val responseBodyString = response.body?.string() ?: ""

                        if (response.isSuccessful) {
                            val jsonObject = JSONObject(responseBodyString)
                            return@withContext Result.success(
                                CloudinaryUploadResult(
                                    publicId = jsonObject.optString("public_id", remotePublicId),
                                    secureUrl = jsonObject.optString("secure_url", ""),
                                    assetType = jsonObject.optString("resource_type", "auto"),
                                    bytes = jsonObject.optLong("bytes", fileBytes.size.toLong())
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            Result.failure(
                IllegalStateException(
                    "Cloudinary is not configured. Please tap 'Cloudinary Settings' on the Cloud Backup screen to enter your Cloud Name and Upload Preset (or API Key & Secret)."
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadEncryptedMedia(
        mediaId: String,
        encryptedFileBytes: ByteArray,
        mimeType: String
    ): Result<CloudinaryUploadResult> {
        return uploadMedia(mediaId, encryptedFileBytes, mimeType, "media_$mediaId.enc")
    }

    override suspend fun deleteAsset(publicId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("User must be authenticated."))

            val uid = currentUser.uid
            if (!publicId.startsWith("users/$uid/")) {
                return@withContext Result.failure(SecurityException("Unauthorized asset deletion path."))
            }

            val data = hashMapOf("publicId" to publicId)
            functions
                .getHttpsCallable("deleteCloudinaryAsset")
                .call(data)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
