package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_folders")
data class VaultFolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
