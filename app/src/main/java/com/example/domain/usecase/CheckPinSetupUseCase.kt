package com.example.domain.usecase

import com.example.core.security.SecurityRepository
import kotlinx.coroutines.flow.Flow

class CheckPinSetupUseCase(
    private val securityRepository: SecurityRepository
) {
    operator fun invoke(): Flow<Boolean> {
        return securityRepository.isPinConfigured()
    }

    suspend fun isConfiguredSync(): Boolean {
        return securityRepository.checkIsPinConfiguredSync()
    }
}
