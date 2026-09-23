package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_media",
    foreignKeys = [
        ForeignKey(
            entity = VaultFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("folderId"),
        Index("isDeleted"),
        Index("mediaType")
    ]
)
data class VaultMediaEntity(
    @PrimaryKey
    val id: String,
    val folderId: String? = null,
    val fileName: String,
    val mimeType: String,
    val mediaType: String, // "PHOTO" or "VIDEO"
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val relativePath: String,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    // Shadow Archive: when user "permanently deletes", we keep a record for 15 days
    // Physical device file is erased but cloud record is preserved for premium recovery
    val isArchivedBySystem: Boolean = false,
    val archivedAt: Long? = null
)
