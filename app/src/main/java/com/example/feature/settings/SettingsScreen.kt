package com.example.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.core.admin.AdminConstants
import com.example.feature.recording.RecordingForegroundService
import com.example.feature.recording.VolumeButtonRecordingManager
import com.example.feature.recording.VolumeKeyAction

import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Password
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.widget.Toast

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
                    text = "Closed App Recording Setup",
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
                        text = "App close hone ke baad bhi volume buttons se recording kaam kare, uske liye phone mein ye 3 settings enable karein:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Accessibility Service (Zaroori)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Niche 'Open Accessibility Settings' dabayein.\n• 'Installed apps' ya 'Downloaded services' mein ja kar 'Stealth Volume Recording' ON karein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { VolumeButtonRecordingManager.openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Accessibility Settings")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "2. Battery Optimization (Don't optimize)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Niche 'Open Battery Settings' dabayein.\n• Calculator Vault ki battery 'Unrestricted' ya 'Don't optimize' par set karein taakay Android background service ko kill na kare.\n• Xiaomi/Vivo/Oppo: 'Autostart' ko bhi Allow karein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { VolumeButtonRecordingManager.openBatteryOptimizationSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Battery Settings")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
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
                                text = "• Camera aur Microphone permissions allow honi chahiye taakay bina kisi pop-up ke stealth video record ho saky.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { VolumeButtonRecordingManager.openAppDetailsSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open App Permissions")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "★ Pro Tip (Lock in Recent Tasks):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Phone ki Recent Apps (multitasking screen) kholein aur Calculator Vault app ko swipe down ya lock icon daba kar Lock kar dein taakay phone cleaner isay close na kare.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSetupGuideDialog = false }) {
                    Text("Got It (Theek Hai)")
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vault Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Quick Recording & Persistent Notification
            SettingsSectionHeader(title = "Recording & Notifications")
            SettingsToggleCard(
                icon = Icons.Default.NotificationsActive,
                title = "Persistent Quick Recording Bar",
                subtitle = "Keep a permanent recording notification on your phone to start, pause, and stop recordings anytime without opening the app",
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

            // Hardware Volume Button Recording Controls
            SettingsSectionHeader(title = "Hardware Volume Button Controls")
            SettingsToggleCard(
                icon = Icons.Default.Tune,
                title = "Volume Buttons Recording Shortcuts",
                subtitle = "Control video recording discreetly using phone hardware volume keys without touching the screen",
                checked = isVolumeControlsEnabled,
                onCheckedChange = { isEnabled ->
                    isVolumeControlsEnabled = isEnabled
                    VolumeButtonRecordingManager.setEnabled(context, isEnabled)
                },
                testTag = "settings_toggle_volume_controls"
            )

            if (isVolumeControlsEnabled) {
                SettingsCard(
                    icon = Icons.Default.VolumeUp,
                    title = "Volume UP Button",
                    subtitle = "${volumeUpAction.title} (${volumeUpAction.description})",
                    onClick = { showVolumeUpDialog = true },
                    testTag = "settings_volume_up_action"
                )

                SettingsCard(
                    icon = Icons.Default.VolumeDown,
                    title = "Volume DOWN Button",
                    subtitle = "${volumeDownAction.title} (${volumeDownAction.description})",
                    onClick = { showVolumeDownDialog = true },
                    testTag = "settings_volume_down_action"
                )

                SettingsToggleCard(
                    icon = Icons.Default.NotificationsOff,
                    title = "Dismiss Notification on Volume Stop",
                    subtitle = "When stopped using volume buttons, immediately clear and dismiss the recording notification (video remains safely encrypted in Vault)",
                    checked = isDismissNotificationOnVolumeStop,
                    onCheckedChange = { isDismiss ->
                        isDismissNotificationOnVolumeStop = isDismiss
                        VolumeButtonRecordingManager.setDismissNotificationOnVolumeStop(context, isDismiss)
                    },
                    testTag = "settings_toggle_volume_stop_notification"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAccessibilityEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccessibilityEnabled) "Background Service Active (Ready)" else "Closed App Recording Setup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isAccessibilityEnabled) {
                                "✓ Volume buttons will trigger recording even when this app is completely closed or running in the background."
                            } else {
                                "App close hone ke baad bhi volume buttons se recording ke liye phone ki Accessibility aur Battery Optimization settings enable karein."
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
                                modifier = Modifier.weight(1f)
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Turn ON", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Default Behavior: Press Volume Up to Start recording (or Pause/Resume when recording). Press Volume Down to Stop and save directly to Vault. You can customize each button above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Flashlight Floating Camouflage Button
                SettingsToggleCard(
                    icon = Icons.Default.FlashlightOn,
                    title = "Flashlight Camouflage Floating Icon",
                    subtitle = "Show a floating flashlight icon on screen. Tapping turns real flashlight ON/OFF. Long-pressing Volume DOWN instantly removes it and locks controls until Vault is opened",
                    checked = isFloatingFlashlightEnabled,
                    onCheckedChange = { isEnabled ->
                        isFloatingFlashlightEnabled = isEnabled
                        com.example.feature.recording.FlashlightFloatingOverlayManager.setFloatingButtonEnabled(context, isEnabled)
                    },
                    testTag = "settings_toggle_floating_flashlight"
                )

                if (isControlsLocked) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        )
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
                                text = "Volume DOWN was long-pressed. The flashlight icon has been removed from screen and recording buttons are locked for security.\n\nEnter your Master PIN in Calculator to unlock the Vault and automatically restore everything.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    com.example.feature.recording.VolumeButtonRecordingManager.onVaultUnlocked(context)
                                    isControlsLocked = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restore Controls & Flashlight Now")
                            }
                        }
                    }
                } else if (isFloatingFlashlightEnabled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
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
                                    text = "Flashlight Camouflage Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Touch icon: Toggles phone Flashlight ON/OFF (acts as a genuine torch).\n• Long-press Volume DOWN: Instantly removes icon and locks controls.\n• Restore controls: Open Vault with your Master PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    com.example.feature.recording.FlashlightFloatingOverlayManager.show(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Show / Position Flashlight Button")
                            }
                        }
                    }
                }
            }

            // 2. Video Playback
            SettingsSectionHeader(title = "Video Playback")
            SettingsToggleCard(
                icon = Icons.Default.PlayCircle,
                title = "Direct External Video Player",
                subtitle = "Open videos directly in VLC / MX Player instead of built-in hardware player",
                checked = isExternalPlayerPreferred,
                onCheckedChange = { isEnabled ->
                    isExternalPlayerPreferred = isEnabled
                    sharedPrefs.edit().putBoolean("use_external_video_player", isEnabled).apply()
                },
                testTag = "settings_toggle_external_player"
            )

            // 3. Account Section
            SettingsSectionHeader(title = "Account & Cloud Backup")
            SettingsCard(
                icon = Icons.Default.Person,
                title = "Account & Cloud Sync",
                subtitle = "Optional Firebase login for secure Cloudinary cloud backup",
                testTag = "settings_item_account",
                onClick = onNavigateToAuth
            )
            SettingsCard(
                icon = Icons.Default.CloudSync,
                title = "Cloud Backup & Restore",
                subtitle = "Manually backup and restore encrypted vault media via Cloudinary",
                testTag = "settings_item_cloud_backup",
                onClick = onNavigateToCloudBackup
            )

            // 4. Premium Section
            SettingsSectionHeader(title = "Premium Subscription")
            SettingsCard(
                icon = Icons.Default.Star,
                title = if (isPremium) "Premium Active" else "Upgrade to Premium",
                subtitle = if (isPremium) "Your subscription is active with full cloud backup & restore privileges" else "Cloud backup, cross-device sync, 4K video storage & zero ads",
                testTag = "settings_item_premium",
                onClick = onNavigateToPremium
            )

            // 5. Administration Section (Exclusively visible to king.khan648k@gmail.com)
            if (AdminConstants.isAdminEmail(currentUserEmail)) {
                SettingsSectionHeader(title = "System Administration")
                SettingsCard(
                    icon = Icons.Default.AdminPanelSettings,
                    title = "Admin Console (${AdminConstants.ADMIN_EMAIL})",
                    subtitle = "Master dashboard for updates, broadcasts, user files & membership privileges",
                    testTag = "settings_item_admin",
                    onClick = onNavigateToAdmin
                )
            }

            // 6. Version & Updates Section
            SettingsSectionHeader(title = "App Updates & Information")
            SettingsCard(
                icon = Icons.Default.SystemUpdate,
                title = "Check for App Updates",
                subtitle = "Installed: v${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE}) • Tap to verify live releases",
                testTag = "settings_item_check_updates",
                onClick = onCheckUpdateClick
            )

            // 7. Security Section
            SettingsSectionHeader(title = "Security & Double PIN (Panic Mode)")
            SettingsCard(
                icon = Icons.Default.Key,
                title = "Change Vault Master PIN",
                subtitle = "Update your 4-digit secret calculator unlock code",
                testTag = "settings_item_change_pin",
                onClick = onChangePinRequested
            )

            SettingsToggleCard(
                icon = Icons.Default.Shield,
                title = "Panic / Decoy PIN (Double PIN)",
                subtitle = if (isPanicPinEnabled) 
                    "Enabled • Entering Panic PIN opens a completely empty decoy vault"
                else 
                    "Disabled • Tap to enable duress decoy mode",
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
                SettingsCard(
                    icon = Icons.Default.Password,
                    title = if (isPanicPinConfigured) "Change Panic PIN Code" else "Set Panic PIN Code",
                    subtitle = "Configure the secret PIN that unlocks an empty decoy vault safely",
                    testTag = "settings_item_configure_panic_pin",
                    onClick = { showPanicPinDialog = true }
                )
            }

            SettingsCard(
                icon = Icons.Default.Lock,
                title = "Auto-Lock & FLAG_SECURE",
                subtitle = "Active: Prevents app screenshots & locks on exit",
                testTag = "settings_item_security",
                onClick = {}
            )
            SettingsCard(
                icon = Icons.Default.Security,
                title = "Local AES-256 Storage",
                subtitle = "Local Room database with private AES-GCM media encryption (hidden from Gallery)",
                testTag = "settings_item_app_info",
                onClick = {}
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
