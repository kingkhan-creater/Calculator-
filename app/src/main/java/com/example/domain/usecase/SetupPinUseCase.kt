package com.example.domain.usecase

import com.example.core.model.SecurityResult
import com.example.core.security.SecurityRepository

class SetupPinUseCase(
    private val securityRepository: SecurityRepository
) {
    suspend operator fun invoke(pin: String): SecurityResult {
        return securityRepository.setupPin(pin)
    }
}
