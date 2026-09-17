package com.example.core.model

data class VaultSessionState(
    val isUnlocked: Boolean = false,
    val isPinConfigured: Boolean = false,
    val lastUnlockedTimestamp: Long = 0L
)
