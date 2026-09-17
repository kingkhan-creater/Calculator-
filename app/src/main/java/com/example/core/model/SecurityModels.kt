package com.example.core.model

sealed interface SecurityResult {
    data object Success : SecurityResult
    data class Error(val message: String) : SecurityResult
}

data class StoredCredential(
    val salt: ByteArray,
    val pinHash: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StoredCredential
        if (!salt.contentEquals(other.salt)) return false
        if (!pinHash.contentEquals(other.pinHash)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + pinHash.contentHashCode()
        return result
    }
}
