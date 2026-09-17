package com.example.domain.usecase

import com.example.core.model.SecurityResult
import com.example.core.security.SecurityRepository

class ChangePinUseCase(
    private val securityRepository: SecurityRepository
) {
    suspend operator fun invoke(currentPin: String, newPin: String): SecurityResult {
        return securityRepository.changePin(currentPin, newPin)
    }
}
