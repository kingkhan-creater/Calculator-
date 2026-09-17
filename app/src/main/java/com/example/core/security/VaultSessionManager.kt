package com.example.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global singleton managing current vault session state, including Decoy (Panic PIN) mode.
 */
object VaultSessionManager {
    private val _isDecoyMode = MutableStateFlow(false)
    val isDecoyMode: StateFlow<Boolean> = _isDecoyMode.asStateFlow()

    fun setDecoyMode(decoy: Boolean) {
        _isDecoyMode.value = decoy
    }

    fun resetSession() {
        _isDecoyMode.value = false
    }
}
