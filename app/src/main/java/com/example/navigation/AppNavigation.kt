package com.example.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.VaultAppContainer
import com.example.core.admin.AdminAnnouncement
import com.example.core.auth.AuthState
import com.example.core.updates.AppUpdateState
import com.example.feature.calculator.CalculatorScreen
import com.example.feature.calculator.CalculatorViewModel
import com.example.feature.media.VaultMediaViewModel
import com.example.feature.vault.VaultScreen
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.InAppAnnouncementDialog
import kotlinx.coroutines.launch

object AppDestinations {
    const val CALCULATOR = "calculator"
    const val VAULT = "vault"
    const val ADMIN = "admin"
    const val AUTH = "auth"
    const val SETTINGS = "settings"
    const val CLOUD_BACKUP = "cloud_backup"
    const val RECORDING = "recording"
    const val PREMIUM = "premium"
}

@Composable
fun AppNavigation(
    appContainer: VaultAppContainer,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isPremiumUser by appContainer.entitlementManager.isPremium.collectAsStateWithLifecycle(initialValue = false)
    val authState by appContainer.firebaseAuthRepository.authState.collectAsStateWithLifecycle()
    val currentUserId = (authState as? AuthState.Authenticated)?.uid
    val currentUserEmail = (authState as? AuthState.Authenticated)?.email

    var activeUpdateResult by remember { mutableStateOf<AppUpdateState.UpdateAvailable?>(null) }
    var activeAnnouncement by remember { mutableStateOf<AdminAnnouncement?>(null) }

    // On App Startup: Check for App Updates and Unread Broadcast Announcements
    LaunchedEffect(currentUserId, isPremiumUser) {
        // 1. Check for App Updates
        val updateRes = appContainer.appUpdateManager.checkForUpdates()
        if (updateRes is AppUpdateState.UpdateAvailable) {
            activeUpdateResult = updateRes
        }

        // 2. Check for Broadcast Announcements
        val announcementsRes = appContainer.adminRepository.getActiveAnnouncementsForUser(
            userId = currentUserId,
            isPremium = isPremiumUser
        )
        announcementsRes.onSuccess { list ->
            val unseen = appContainer.announcementDismissManager.filterUnseenAnnouncements(list)
            if (unseen.isNotEmpty()) {
                activeAnnouncement = unseen.first()
            }
        }
    }

    val onManualCheckUpdates: () -> Unit = {
        coroutineScope.launch {
            Toast.makeText(context, "Checking for latest app updates...", Toast.LENGTH_SHORT).show()
            val res = appContainer.appUpdateManager.checkForUpdates()
            when (res) {
                is AppUpdateState.UpdateAvailable -> {
                    activeUpdateResult = res
                }
                is AppUpdateState.UpToDate -> {
                    Toast.makeText(
                        context,
                        "App is up to date (v${res.currentVersionName}).",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is AppUpdateState.Error -> {
                    Toast.makeText(
                        context,
                        "Unable to check for updates: ${res.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                AppUpdateState.Checking -> {}
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = AppDestinations.CALCULATOR,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestinations.CALCULATOR) {
                val calculatorViewModel: CalculatorViewModel = viewModel(
                    factory = CalculatorViewModel.Factory(
                        calculatorEngine = appContainer.calculatorEngine,
                        checkPinSetupUseCase = appContainer.checkPinSetupUseCase,
                        verifyPinUseCase = appContainer.verifyPinUseCase,
                        setupPinUseCase = appContainer.setupPinUseCase
                    )
                )

                val context = androidx.compose.ui.platform.LocalContext.current
                CalculatorScreen(
                    viewModel = calculatorViewModel,
                    onNavigateToVault = {
                        com.example.feature.recording.VolumeButtonRecordingManager.onVaultUnlocked(context)
                        navController.navigate(AppDestinations.VAULT)
                    }
                )
            }

            composable(AppDestinations.VAULT) {
                val vaultMediaViewModel: VaultMediaViewModel = viewModel(
                    factory = VaultMediaViewModel.Factory(
                        mediaRepository = appContainer.mediaRepository,
                        checkPinSetupUseCase = appContainer.checkPinSetupUseCase,
                        changePinUseCase = appContainer.changePinUseCase
                    )
                )

                VaultScreen(
                    viewModel = vaultMediaViewModel,
                    onLockVault = {
                        navController.popBackStack(AppDestinations.CALCULATOR, inclusive = false)
                    },
                    onNavigateToSettings = {
                        navController.navigate(AppDestinations.SETTINGS)
                    },
                    onNavigateToRecording = {
                        navController.navigate(AppDestinations.RECORDING)
                    }
                )
            }

            composable(AppDestinations.RECORDING) {
                val recordingViewModel: com.example.feature.recording.RecordingViewModel = viewModel(
                    factory = com.example.feature.recording.RecordingViewModel.Factory(
                        mediaDao = appContainer.database.mediaDao(),
                        storageManager = appContainer.storageManager,
                        fileEncryptor = appContainer.fileEncryptor
                    )
                )

                com.example.feature.recording.RecordingScreen(
                    viewModel = recordingViewModel,
                    activeFolderId = null,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AppDestinations.SETTINGS) {
                com.example.feature.settings.SettingsScreen(
                    isPremium = isPremiumUser,
                    currentUserEmail = currentUserEmail,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToAuth = {
                        navController.navigate(AppDestinations.AUTH)
                    },
                    onNavigateToCloudBackup = {
                        navController.navigate(AppDestinations.CLOUD_BACKUP)
                    },
                    onNavigateToPremium = {
                        navController.navigate(AppDestinations.PREMIUM)
                    },
                    onNavigateToAdmin = {
                        navController.navigate(AppDestinations.ADMIN)
                    },
                    onCheckUpdateClick = onManualCheckUpdates,
                    securityRepository = appContainer.securityRepository,
                    onChangePinRequested = {
                        navController.navigate(AppDestinations.VAULT)
                    }
                )
            }

            composable(AppDestinations.CLOUD_BACKUP) {
                val cloudBackupViewModel: com.example.feature.backup.CloudBackupViewModel = viewModel(
                    factory = com.example.feature.backup.CloudBackupViewModel.Factory(
                        context = context,
                        authRepository = appContainer.firebaseAuthRepository,
                        firestoreRepository = appContainer.firestoreVaultRepository,
                        mediaRepository = appContainer.mediaRepository,
                        storageManager = appContainer.storageManager,
                        cloudinaryService = appContainer.cloudinaryService,
                        entitlementManager = appContainer.entitlementManager
                    )
                )

                com.example.feature.backup.CloudBackupScreen(
                    viewModel = cloudBackupViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToAuth = {
                        navController.navigate(AppDestinations.AUTH)
                    },
                    onNavigateToPremium = {
                        navController.navigate(AppDestinations.PREMIUM)
                    }
                )
            }

            composable(AppDestinations.PREMIUM) {
                val premiumViewModel: com.example.feature.premium.PremiumViewModel = viewModel(
                    factory = com.example.feature.premium.PremiumViewModel.Factory(
                        billingManager = appContainer.billingManager,
                        entitlementManager = appContainer.entitlementManager
                    )
                )

                com.example.feature.premium.PremiumScreen(
                    viewModel = premiumViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AppDestinations.AUTH) {
                val authViewModel: com.example.feature.auth.AuthViewModel = viewModel(
                    factory = com.example.feature.auth.AuthViewModel.Factory(
                        authRepository = appContainer.firebaseAuthRepository,
                        firestoreRepository = appContainer.firestoreVaultRepository
                    )
                )

                com.example.feature.auth.AuthScreen(
                    viewModel = authViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AppDestinations.ADMIN) {
                val adminViewModel: com.example.feature.admin.AdminViewModel = viewModel(
                    factory = com.example.feature.admin.AdminViewModel.Factory(
                        adminRepository = appContainer.adminRepository,
                        entitlementManager = appContainer.entitlementManager
                    )
                )

                com.example.feature.admin.AdminDashboardScreen(
                    viewModel = adminViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        // Global In-App Update Dialog
        activeUpdateResult?.let { update ->
            AppUpdateDialog(
                config = update.config,
                isMandatory = update.isMandatory,
                currentVersionName = update.currentVersionName,
                onUpdateClick = {
                    appContainer.appUpdateManager.openApkDownloadLink(context, update.config.apkUrl)
                },
                onDismiss = {
                    activeUpdateResult = null
                }
            )
        }

        // Global One-Time In-App Announcement Dialog
        activeAnnouncement?.let { announcement ->
            InAppAnnouncementDialog(
                announcement = announcement,
                onDismissAndNeverShowAgain = {
                    coroutineScope.launch {
                        appContainer.announcementDismissManager.markAsDismissed(announcement.id)
                        activeAnnouncement = null
                    }
                }
            )
        }
    }
}


