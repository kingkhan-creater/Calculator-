package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.VaultMediaRepositoryImpl
import com.example.data.local.VaultDatabase
import com.example.data.local.entity.VaultFolderEntity
import com.example.data.local.entity.VaultMediaEntity
import com.example.data.storage.VaultStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultMediaRepositoryTest {

    private lateinit var database: VaultDatabase
    private lateinit var repository: VaultMediaRepositoryImpl
    private lateinit var storageManager: VaultStorageManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storageManager = VaultStorageManager(context)
        repository = VaultMediaRepositoryImpl(
            mediaDao = database.mediaDao(),
            folderDao = database.folderDao(),
            storageManager = storageManager
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create folder and retrieve folders list`() = runTest {
        val result = repository.createFolder("Vacation 2026")
        assertTrue(result.isSuccess)
        val created = result.getOrNull()
        assertNotNull(created)
        assertEquals("Vacation 2026", created?.name)

        val folders = repository.getAllFolders().first()
        assertEquals(1, folders.size)
        assertEquals("Vacation 2026", folders[0].name)
    }

    @Test
    fun `media soft delete and restore lifecycle`() = runTest {
        val mediaId = UUID.randomUUID().toString()
        val mediaEntity = VaultMediaEntity(
            id = mediaId,
            fileName = "secret_photo.jpg",
            mimeType = "image/jpeg",
            mediaType = "PHOTO",
            sizeBytes = 1024L,
            durationMs = 0L,
            relativePath = "test.jpg"
        )
        database.mediaDao().insertMedia(mediaEntity)

        // Verify it is in active media
        val activeList = repository.getAllActiveMedia().first()
        assertEquals(1, activeList.size)
        assertEquals(mediaId, activeList[0].id)

        // Soft delete (move to trash)
        val deleteResult = repository.moveToTrash(listOf(mediaId))
        assertTrue(deleteResult.isSuccess)

        val activeAfterDelete = repository.getAllActiveMedia().first()
        assertEquals(0, activeAfterDelete.size)

        val trashList = repository.getTrashMedia().first()
        assertEquals(1, trashList.size)
        assertEquals(mediaId, trashList[0].id)

        // Restore from trash
        val restoreResult = repository.restoreFromTrash(listOf(mediaId))
        assertTrue(restoreResult.isSuccess)

        val activeAfterRestore = repository.getAllActiveMedia().first()
        assertEquals(1, activeAfterRestore.size)
        val trashAfterRestore = repository.getTrashMedia().first()
        assertEquals(0, trashAfterRestore.size)
    }

    @Test
    fun `move media to folder`() = runTest {
        val folderResult = repository.createFolder("Documents")
        val folderId = folderResult.getOrNull()!!.id

        val mediaId = UUID.randomUUID().toString()
        database.mediaDao().insertMedia(
            VaultMediaEntity(
                id = mediaId,
                fileName = "doc.jpg",
                mimeType = "image/jpeg",
                mediaType = "PHOTO",
                sizeBytes = 500L,
                relativePath = "doc.jpg"
            )
        )

        // Move to folder
        val moveResult = repository.moveMediaToFolder(listOf(mediaId), folderId)
        assertTrue(moveResult.isSuccess)

        val inFolder = repository.getActiveMediaInFolder(folderId).first()
        assertEquals(1, inFolder.size)
        assertEquals(folderId, inFolder[0].folderId)
    }

    @Test
    fun `export to gallery returns success result`() = runTest {
        val mediaId = UUID.randomUUID().toString()
        database.mediaDao().insertMedia(
            VaultMediaEntity(
                id = mediaId,
                fileName = "export_test.jpg",
                mimeType = "image/jpeg",
                mediaType = "PHOTO",
                sizeBytes = 1024L,
                relativePath = "export_test.jpg"
            )
        )

        val result = repository.exportToGallery(listOf(mediaId))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `export to gallery moves file out of vault`() = runTest {
        val mediaId = UUID.randomUUID().toString()
        val testFile = storageManager.getMediaFile("export_move_test.jpg")
        testFile.parentFile?.mkdirs()
        testFile.writeText("sample content")

        database.mediaDao().insertMedia(
            VaultMediaEntity(
                id = mediaId,
                fileName = "export_move_test.jpg",
                mimeType = "image/jpeg",
                mediaType = "PHOTO",
                sizeBytes = 1024L,
                relativePath = "export_move_test.jpg"
            )
        )

        val result = repository.exportToGallery(listOf(mediaId))
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.successCount)

        // Must be removed from vault database and deleted from disk
        val activeMedia = repository.getAllActiveMedia().first()
        assertTrue(activeMedia.none { it.id == mediaId })
        assertFalse(testFile.exists())
    }
}
