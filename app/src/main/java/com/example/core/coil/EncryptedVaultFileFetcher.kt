package com.example.core.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.core.security.VaultFileEncryptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Custom Coil Fetcher that streams private vault files directly from disk without CPU overhead,
 * while transparently decrypting any older legacy AES-256-GCM encrypted media files in memory.
 */
class EncryptedVaultFileFetcher(
    private val file: File,
    private val options: Options,
    private val fileEncryptor: VaultFileEncryptor
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val bytes = if (fileEncryptor.isEncrypted(file)) {
            fileEncryptor.decryptFileToBytes(file)
        } else {
            file.readBytes()
        }
        val buffer = Buffer().write(bytes)
        SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(
        private val fileEncryptor: VaultFileEncryptor
    ) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (fileEncryptor.isVaultOrEncryptedFile(data)) {
                return EncryptedVaultFileFetcher(data, options, fileEncryptor)
            }
            return null
        }
    }
}

