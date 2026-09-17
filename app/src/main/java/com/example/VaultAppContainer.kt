package com.example

import android.content.Context
import com.example.core.security.CryptoManager
import com.example.core.security.SecurityRepository
import com.example.data.SecurityRepositoryImpl
import com.example.data.VaultMediaRepositoryImpl
import com.example.data.VaultPreferencesDataSource
import com.example.data.local.VaultDatabase
import com.example.data.storage.VaultStorageManager
import com.example.domain.calculator.CalculatorEngine
import com.example.domain.usecase.ChangePinUseCase
import com.example.domain.usecase.CheckPinSetupUseCase
import com.example.domain.usecase.SetupPinUseCase
import com.example.domain.usecase.VerifyPinUseCase
import com.example.feature.media.VaultMediaRepository

class VaultAppContainer(private val context: Context) {

    val fileEncryptor: com.example.core.security.VaultFileEncryptor by lazy {
        com.example.core.security.VaultFileEncryptor()
    }

    val database: VaultDatabase by lazy {
        VaultDatabase.getInstance(context)
    }

    val storageManager: VaultStorageManager by lazy {
        VaultStorageManager(context, fileEncryptor)
    }

    val mediaRepository: VaultMediaRepository by lazy {
        VaultMediaRepositoryImpl(
            mediaDao = database.mediaDao(),
            folderDao = database.folderDao(),
            storageManager = storageManager
        )
    }

    val cryptoManager: CryptoManager by lazy {
        CryptoManager()
    }

    val vaultPreferencesDataSource: VaultPreferencesDataSource by lazy {
        VaultPreferencesDataSource(context, cryptoManager)
    }

    val securityRepository: SecurityRepository by lazy {
        SecurityRepositoryImpl(vaultPreferencesDataSource, cryptoManager)
    }

    val calculatorEngine: CalculatorEngine by lazy {
        CalculatorEngine()
    }

    val checkPinSetupUseCase: CheckPinSetupUseCase by lazy {
        CheckPinSetupUseCase(securityRepository)
    }

    val verifyPinUseCase: VerifyPinUseCase by lazy {
        VerifyPinUseCase(securityRepository)
    }

    val setupPinUseCase: SetupPinUseCase by lazy {
        SetupPinUseCase(securityRepository)
    }

    val changePinUseCase: ChangePinUseCase by lazy {
        ChangePinUseCase(securityRepository)
    }

    val serverSubscriptionValidator: com.example.core.subscription.ServerSubscriptionValidator by lazy {
        com.example.data.subscription.RemoteServerSubscriptionValidator()
    }

    val billingManager: com.example.core.billing.BillingManager by lazy {
        com.example.data.billing.PlayBillingManager(context)
    }

    val entitlementManager: com.example.core.subscription.EntitlementManager by lazy {
        com.example.data.subscription.EntitlementManagerImpl(serverSubscriptionValidator)
    }

    val adminRepository: com.example.core.admin.AdminRepository by lazy {
        com.example.data.admin.AdminRepositoryImpl()
    }

    val firebaseAuthRepository: com.example.core.auth.FirebaseAuthRepository by lazy {
        com.example.data.auth.FirebaseAuthRepositoryImpl()
    }

    val firestoreVaultRepository: com.example.core.firestore.FirestoreVaultRepository by lazy {
        com.example.data.firestore.FirestoreVaultRepositoryImpl()
    }

    val cloudinaryService: com.example.integration.cloudinary.CloudinaryServiceContract by lazy {
        com.example.integration.cloudinary.CloudinaryServiceImpl()
    }

    val announcementDismissManager: com.example.core.announcements.AnnouncementDismissManager by lazy {
        com.example.core.announcements.AnnouncementDismissManager(context)
    }

    val appUpdateManager: com.example.core.updates.AppUpdateManager by lazy {
        com.example.core.updates.AppUpdateManager(adminRepository)
    }
}


