package com.example.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.core.admin.AdminConstants
import com.example.feature.recording.RecordingForegroundService
import com.example.feature.recording.VolumeButtonRecordingManager
import com.example.feature.recording.VolumeKeyAction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToCloudBackup: () -> Unit,
    onChangePinRequested: () -> Unit,
    isPremium: Boolean = false,
    currentUserEmail: String? = null,
    onNavigateToPremium: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onCheckUpdateClick: () -> Unit = {},
    securityRepository: com.example.core.security.SecurityRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember {
        context.getSharedPreferences("vault_security_storage", Context.MODE_PRIVATE)
    }

    var isPanicPinEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("vault_panic_pin_enabled", false))
    }
    var isPanicPinConfigured by remember {
        mutableStateOf(sharedPrefs.getBoolean("vault_panic_pin_is_set", false))
    }
    var showPanicPinDialog by remember { mutableStateOf(false) }

    var isPersistentNotificationEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("persistent_recording_notification", false))
    }

    var isExternalPlayerPreferred by remember {
        mutableStateOf(sharedPrefs.getBoolean("use_external_video_player", false))
    }

    var isVolumeControlsEnabled by remember {
        mutableStateOf(VolumeButtonRecordingManager.isEnabled(context))
    }
    var volumeUpAction by remember {
        mutableStateOf(VolumeButtonRecordingManager.getVolumeUpAction(context))
    }
    var volumeDownAction by remember {
        mutableStateOf(VolumeButtonRecordingManager.getVolumeDownAction(context))
    }
    var showVolumeUpDialog by remember { mutableStateOf(false) }
    var showVolumeDownDialog by remember { mutableStateOf(false) }
    var showSetupGuideDialog by remember { mutableStateOf(false) }

    var isDismissNotificationOnVolumeStop by remember {
        mutableStateOf(VolumeButtonRecordingManager.isDismissNotificationOnVolumeStop(context))
    }
    var isAccessibilityEnabled by remember {
        mutableStateOf(VolumeButtonRecordingManager.isAccessibilityServiceEnabled(context))
    }
    var isFloatingFlashlightEnabled by remember {
        mutableStateOf(com.example.feature.recording.FlashlightFloatingOverlayManager.isFloatingButtonEnabled(context))
    }
    var isControlsLocked by remember {
        mutableStateOf(com.example.feature.recording.FlashlightFloatingOverlayManager.isControlsLocked(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = VolumeButtonRecordingManager.isAccessibilityServiceEnabled(context)
                isControlsLocked = com.example.feature.recording.FlashlightFloatingOverlayManager.isControlsLocked(context)
                isFloatingFlashlightEnabled = com.example.feature.recording.FlashlightFloatingOverlayManager.isFloatingButtonEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showPanicPinDialog) {
        com.example.feature.vault.PanicPinDialog(
            isAlreadyConfigured = isPanicPinConfigured,
            onSavePanicPin = { masterPin, newPanicPin ->
                coroutineScope.launch {
                    val secRepo = securityRepository
                    if (secRepo != null) {
                        val isMasterValid = secRepo.verifyPin(masterPin)
                        if (isMasterValid) {
                            val res = if (isPanicPinConfigured) {
                                secRepo.changePanicPin(masterPin, newPanicPin)
                            } else {
                                secRepo.setupPanicPin(newPanicPin)
                            }
                            if (res is com.example.core.model.SecurityResult.Success) {
                                secRepo.setPanicPinEnabled(true)
                                isPanicPinEnabled = true
                                isPanicPinConfigured = true
                                showPanicPinDialog = false
                                Toast.makeText(context, "Panic PIN saved successfully!", Toast.LENGTH_SHORT).show()
                            } else if (res is com.example.core.model.SecurityResult.Error) {
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save Panic PIN", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Master PIN is incorrect", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { showPanicPinDialog = false }
        )
    }

    if (showVolumeUpDialog) {
        AlertDialog(
            onDismissRequest = { showVolumeUpDialog = false },
            title = {
                Text(
                    text = "Configure Volume UP Button",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeKeyAction.entries.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    volumeUpAction = action
                                    VolumeButtonRecordingManager.setVolumeUpAction(context, action)
                                    showVolumeUpDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = volumeUpAction == action,
                                onClick = {
                                    volumeUpAction = action
                                    VolumeButtonRecordingManager.setVolumeUpAction(context, action)
                                    showVolumeUpDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = action.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVolumeUpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showVolumeDownDialog) {
        AlertDialog(
            onDismissRequest = { showVolumeDownDialog = false },
            title = {
                Text(
                    text = "Configure Volume DOWN Button",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeKeyAction.entries.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    volumeDownAction = action
                                    VolumeButtonRecordingManager.setVolumeDownAction(context, action)
                                    showVolumeDownDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = volumeDownAction == action,
                                onClick = {
                                    volumeDownAction = action
                                    VolumeButtonRecordingManager.setVolumeDownAction(context, action)
                                    showVolumeDownDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = action.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVolumeDownDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Background Recording Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "To allow volume buttons to trigger recording when the app is closed or the screen is off, configure these 3 phone settings:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Accessibility Service (Required)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Tap 'Open Accessibility Settings' below.\n• Look for 'Installed apps' or 'Downloaded services'.\n• Turn ON 'Stealth Volume Recording'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { VolumeButtonRecordingManager.openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open Accessibility Settings")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "2. Battery Optimization (Unrestricted)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Tap 'Open Battery Settings' below.\n• Set Calculator Vault to 'Unrestricted' or 'Don't Optimize' so Android does not stop the background service.\n• Xiaomi/Vivo/Oppo: Also allow 'Autostart'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { VolumeButtonRecordingManager.openBatteryOptimizationSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open Battery Settings")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "3. Camera & Microphone Permissions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Ensure Camera and Microphone permissions are allowed at all times so video recording can start without showing permission dialogs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { VolumeButtonRecordingManager.openAppDetailsSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open App Permissions")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "★ Pro Tip (Lock in Recent Apps):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Open your phone's Recent Apps (multitasking screen), find Calculator Vault, and tap the Lock icon or swipe down to keep it in memory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSetupGuideDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Vault Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. ACCOUNT
            // ==========================================
            SettingsSectionHeader(title = "Account")
            SettingsGroupCard {
                SettingsCard(
                    icon = Icons.Default.Person,
                    title = "Account & Cloud Sync",
                    subtitle = if (!currentUserEmail.isNullOrBlank()) currentUserEmail else "Sign in with Google to protect and sync your cloud vault",
                    testTag = "settings_item_account",
                    onClick = onNavigateToAuth,
                    trailingBadge = if (!currentUserEmail.isNullOrBlank()) "Signed In" else null
                )
            }

            // ==========================================
            // 2. SECURITY
            // ==========================================
            SettingsSectionHeader(title = "Security")
            SettingsGroupCard {
                SettingsCard(
                    icon = Icons.Default.Key,
                    title = "Change Vault PIN",
                    subtitle = "Update your secret calculator unlock code",
                    testTag = "settings_item_change_pin",
                    onClick = onChangePinRequested
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsToggleCard(
                    icon = Icons.Default.Shield,
                    title = "Decoy Panic PIN",
                    subtitle = if (isPanicPinEnabled)
                        "Active • Entering Panic PIN opens an empty decoy vault"
                    else
                        "Disabled • Configure a secret PIN to show an empty decoy vault under duress",
                    checked = isPanicPinEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked && !isPanicPinConfigured) {
                            showPanicPinDialog = true
                        } else {
                            isPanicPinEnabled = isChecked
                            securityRepository?.setPanicPinEnabled(isChecked)
                        }
                    },
                    testTag = "settings_toggle_panic_pin"
                )

                if (isPanicPinEnabled || isPanicPinConfigured) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsCard(
                        icon = Icons.Default.Password,
                        title = if (isPanicPinConfigured) "Change Decoy PIN Code" else "Set Decoy PIN Code",
                        subtitle = "Configure the secret PIN that opens an empty decoy vault",
                        testTag = "settings_item_configure_panic_pin",
                        onClick = { showPanicPinDialog = true }
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsCard(
                    icon = Icons.Default.Lock,
                    title = "Screenshot Protection",
                    subtitle = "Prevents screenshots and app switcher previews of private vault files",
                    testTag = "settings_item_security",
                    onClick = {},
                    trailingBadge = "Protected"
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsCard(
                    icon = Icons.Default.Security,
                    title = "Private Encrypted Storage",
                    subtitle = "All media files are safely encrypted locally and hidden from your phone gallery",
                    testTag = "settings_item_app_info",
                    onClick = {},
                    trailingBadge = "AES-256"
                )
            }

            // ==========================================
            // 3. RECORDING
            // ==========================================
            SettingsSectionHeader(title = "Recording")
            SettingsGroupCard {
                SettingsToggleCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Quick Record Notification",
                    subtitle = "Control recording directly from the notification bar without opening the app",
                    checked = isPersistentNotificationEnabled,
                    onCheckedChange = { isEnabled ->
                        isPersistentNotificationEnabled = isEnabled
                        sharedPrefs.edit().putBoolean("persistent_recording_notification", isEnabled).apply()
                        val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
                            action = if (isEnabled) RecordingForegroundService.ACTION_START_STANDBY else RecordingForegroundService.ACTION_STOP_STANDBY
                        }
                        if (isEnabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } else {
                            context.startService(serviceIntent)
                        }
                    },
                    testTag = "settings_toggle_persistent_notification"
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsToggleCard(
                    icon = Icons.Default.Tune,
                    title = "Volume Button Recording",
                    subtitle = "Press phone hardware volume buttons to secretly start or stop video recording",
                    checked = isVolumeControlsEnabled,
                    onCheckedChange = { isEnabled ->
                        isVolumeControlsEnabled = isEnabled
                        VolumeButtonRecordingManager.setEnabled(context, isEnabled)
                    },
                    testTag = "settings_toggle_volume_controls"
                )

                if (isVolumeControlsEnabled) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsCard(
                        icon = Icons.Default.VolumeUp,
                        title = "Volume UP Button",
                        subtitle = "${volumeUpAction.title} • ${volumeUpAction.description}",
                        onClick = { showVolumeUpDialog = true },
                        testTag = "settings_volume_up_action"
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsCard(
                        icon = Icons.Default.VolumeDown,
                        title = "Volume DOWN Button",
                        subtitle = "${volumeDownAction.title} • ${volumeDownAction.description}",
                        onClick = { showVolumeDownDialog = true },
                        testTag = "settings_volume_down_action"
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsToggleCard(
                        icon = Icons.Default.NotificationsOff,
                        title = "Hide Notification on Stop",
                        subtitle = "Immediately dismiss the recording notification when stopped using volume buttons",
                        checked = isDismissNotificationOnVolumeStop,
                        onCheckedChange = { isDismiss ->
                            isDismissNotificationOnVolumeStop = isDismiss
                            VolumeButtonRecordingManager.setDismissNotificationOnVolumeStop(context, isDismiss)
                        },
                        testTag = "settings_toggle_volume_stop_notification"
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsToggleCard(
                        icon = Icons.Default.FlashlightOn,
                        title = "Floating Torch Shortcut",
                        subtitle = "Display a floating torch icon on screen that works as a real flashlight for disguise",
                        checked = isFloatingFlashlightEnabled,
                        onCheckedChange = { isEnabled ->
                            isFloatingFlashlightEnabled = isEnabled
                            com.example.feature.recording.FlashlightFloatingOverlayManager.setFloatingButtonEnabled(context, isEnabled)
                        },
                        testTag = "settings_toggle_floating_flashlight"
                    )
                }
            }

            // Sub-cards for Recording Status if volume controls enabled
            if (isVolumeControlsEnabled) {
                // Background Service Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAccessibilityEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccessibilityEnabled) "Background Service: Ready" else "Background Service: Setup Needed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isAccessibilityEnabled) "Ready" else "Action Needed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isAccessibilityEnabled) {
                                "✓ Volume buttons can trigger recording even when the app is closed or the screen is off."
                            } else {
                                "To allow volume buttons to trigger recording while the app is closed, enable the Accessibility Service."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showSetupGuideDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Instructions", style = MaterialTheme.typography.labelMedium)
                            }
                            if (!isAccessibilityEnabled) {
                                Button(
                                    onClick = { VolumeButtonRecordingManager.openAccessibilitySettings(context) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Settings", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Locked Controls or Floating Flashlight Active Banner
                if (isControlsLocked) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Controls Hidden & Locked",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Volume DOWN was long-pressed. The flashlight icon has been removed from screen and recording buttons are locked for security.\n\nEnter your Master PIN in Calculator to unlock the Vault and restore controls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    com.example.feature.recording.VolumeButtonRecordingManager.onVaultUnlocked(context)
                                    isControlsLocked = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Restore Controls Now")
                            }
                        }
                    }
                } else if (isFloatingFlashlightEnabled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Torch Camouflage Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Tap icon: Toggles phone Flashlight ON/OFF (acts as a real torch).\n• Long-press Volume DOWN: Instantly removes icon and locks controls.\n• Restore controls: Open Vault with your Master PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    com.example.feature.recording.FlashlightFloatingOverlayManager.show(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Show / Position Torch Button")
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. BACKUP & CLOUD
            // ==========================================
            SettingsSectionHeader(title = "Backup & Cloud")
            SettingsGroupCard {
                SettingsCard(
                    icon = Icons.Default.CloudSync,
                    title = "Cloud Backup & Restore",
                    subtitle = "Safely backup or restore your encrypted vault photos and videos",
                    testTag = "settings_item_cloud_backup",
                    onClick = onNavigateToCloudBackup
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsCard(
                    icon = Icons.Default.Star,
                    title = if (isPremium) "Premium Membership" else "Upgrade to Premium",
                    subtitle = if (isPremium) "Active • Full cloud backup and 15-day recovery privileges unlocked" else "Unlock automatic cloud backup, cross-device sync & 15-day recovery",
                    testTag = "settings_item_premium",
                    onClick = onNavigateToPremium,
                    trailingBadge = if (isPremium) "VIP Active" else "Upgrade"
                )
            }

            // ==========================================
            // 5. PLAYBACK
            // ==========================================
            SettingsSectionHeader(title = "Playback")
            SettingsGroupCard {
                SettingsToggleCard(
                    icon = Icons.Default.PlayCircle,
                    title = "External Video Player",
                    subtitle = "Play videos using external apps like VLC or MX Player instead of built-in player",
                    checked = isExternalPlayerPreferred,
                    onCheckedChange = { isEnabled ->
                        isExternalPlayerPreferred = isEnabled
                        sharedPrefs.edit().putBoolean("use_external_video_player", isEnabled).apply()
                    },
                    testTag = "settings_toggle_external_player"
                )
            }

            // ==========================================
            // 6. APP & INFORMATION
            // ==========================================
            SettingsSectionHeader(title = "App & Information")
            SettingsGroupCard {
                SettingsCard(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = "Version ${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE}) • Tap to verify live releases",
                    testTag = "settings_item_check_updates",
                    onClick = onCheckUpdateClick,
                    trailingBadge = "v${com.example.BuildConfig.VERSION_NAME}"
                )

                if (AdminConstants.isAdminEmail(currentUserEmail)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsCard(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Admin Console",
                        subtitle = "Master dashboard for user management, updates, broadcasts and privileges",
                        testTag = "settings_item_admin",
                        onClick = onNavigateToAdmin,
                        trailingBadge = "Admin"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("${testTag}_switch")
        )
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    trailingBadge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (trailingBadge != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = trailingBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}
