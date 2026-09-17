package com.example.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.SecurityResult
import com.example.core.model.VaultSessionState
import com.example.domain.usecase.ChangePinUseCase
import com.example.domain.usecase.CheckPinSetupUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VaultViewModel(
    private val checkPinSetupUseCase: CheckPinSetupUseCase,
    private val changePinUseCase: ChangePinUseCase
) : ViewModel() {

    private val _sessionState = MutableStateFlow(
        VaultSessionState(
            isUnlocked = true,
            isPinConfigured = true,
            lastUnlockedTimestamp = System.currentTimeMillis()
        )
    )
    val sessionState: StateFlow<VaultSessionState> = _sessionState.asStateFlow()

    private val _lockVaultEvent = MutableSharedFlow<Unit>()
    val lockVaultEvent: SharedFlow<Unit> = _lockVaultEvent.asSharedFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    private val _isChangePinDialogVisible = MutableStateFlow(false)
    val isChangePinDialogVisible: StateFlow<Boolean> = _isChangePinDialogVisible.asStateFlow()

    fun onLockVaultClick() {
        _sessionState.update { it.copy(isUnlocked = false) }
        viewModelScope.launch {
            _lockVaultEvent.emit(Unit)
        }
    }

    fun onAppBackgrounded() {
        // Auto-lock when leaving foreground
        _sessionState.update { it.copy(isUnlocked = false) }
        viewModelScope.launch {
            _lockVaultEvent.emit(Unit)
        }
    }

    fun onOpenChangePinDialog() {
        _isChangePinDialogVisible.value = true
    }

    fun onDismissChangePinDialog() {
        _isChangePinDialogVisible.value = false
    }

    fun onChangePin(currentPin: String, newPin: String, confirmPin: String) {
        if (newPin != confirmPin) {
            viewModelScope.launch {
                _messageEvent.emit("New PIN and confirmation do not match.")
            }
            return
        }

        if (newPin.length < 4 || newPin.length > 12) {
            viewModelScope.launch {
                _messageEvent.emit("New PIN must be between 4 and 12 digits.")
            }
            return
        }

        viewModelScope.launch {
            when (val result = changePinUseCase(currentPin, newPin)) {
                is SecurityResult.Success -> {
                    _isChangePinDialogVisible.value = false
                    _messageEvent.emit("PIN successfully updated!")
                }
                is SecurityResult.Error -> {
                    _messageEvent.emit(result.message)
                }
            }
        }
    }

    class Factory(
        private val checkPinSetupUseCase: CheckPinSetupUseCase,
        private val changePinUseCase: ChangePinUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VaultViewModel(
                checkPinSetupUseCase,
                changePinUseCase
            ) as T
        }
    }
}
