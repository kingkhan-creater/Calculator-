package com.example.domain.usecase

import com.example.core.security.PinVerificationResult
import com.example.core.security.SecurityRepository

class VerifyPinUseCase(
    private val securityRepository: SecurityRepository
) {
    suspend operator fun invoke(pin: String): Boolean {
        return securityRepository.verifyPin(pin)
    }

    suspend fun verifyPinType(pin: String): PinVerificationResult {
        return securityRepository.verifyPinType(pin)
    }
}
