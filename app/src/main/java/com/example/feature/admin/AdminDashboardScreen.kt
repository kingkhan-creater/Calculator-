package com.example.feature.admin

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.admin.AbuseSecurityReport
import com.example.core.admin.AccountStatus
import com.example.core.admin.AdminAuditLogEntry
import com.example.core.admin.AdminDashboardMetrics
import com.example.core.admin.AdminUserDetail
import com.example.core.admin.AdminUserMediaItem
import com.example.core.admin.AdminUserSummary
import com.example.core.admin.BackupMonitoringMetrics
import com.example.core.admin.SecurityAuditEvent
import com.example.core.admin.SecurityEventType
import com.example.core.admin.SecuritySeverity
import com.example.core.admin.SubscriptionMonitoringItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Protect Admin Console from recording and recent snapshots
    DisposableEffect(context) {
        var ctx = context
        while (ctx is ContextWrapper && ctx !is Activity) {
            ctx = ctx.baseContext
        }
        val activity = ctx as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onClearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onClearSuccess()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("admin_dashboard_scaffold"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Admin Shield",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Admin Console",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.isAuthenticatedAdmin) "Server Verified (${uiState.currentAdmin?.email})" else "Authorization Required",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isAuthenticatedAdmin) {
                        IconButton(
                            onClick = { viewModel.loadAdminData() },
                            modifier = Modifier.testTag("admin_refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isAuthenticatedAdmin) {
                AdminAuthPrompt(
                    isLoading = uiState.isLoading,
                    onVerifyClaim = { viewModel.verifyAdminAuthorization(forceRefresh = true) },
                    onAuthenticateToken = { token -> viewModel.authenticateWithAdminToken(token) }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Admin Quick Privilege Controls (Test App in Free Mode & Make Admin Premium)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Admin Access Controls (${uiState.currentAdmin?.email ?: "king.khan648k@gmail.com"})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = when {
                                        uiState.isAdminTestModeFree -> "Active Mode: Simulated FREE Tier (Testing)"
                                        uiState.isAdminSelfPremiumActive -> "Active Mode: Admin VIP PREMIUM Granted"
                                        else -> "Active Mode: Standard Admin Session"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.toggleAdminTestAsFree(!uiState.isAdminTestModeFree) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isAdminTestModeFree) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("admin_toggle_free_test_btn")
                                ) {
                                    Text(
                                        text = if (uiState.isAdminTestModeFree) "Stop Free Test" else "Test Free",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.isAdminTestModeFree) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Button(
                                    onClick = { viewModel.toggleAdminSelfPremium(!uiState.isAdminSelfPremiumActive) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isAdminSelfPremiumActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("admin_toggle_self_premium_btn")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isAdminSelfPremiumActive) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (uiState.isAdminSelfPremiumActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.isAdminSelfPremiumActive) "Premium ON" else "Make Premium",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.isAdminSelfPremiumActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Modern Tab Bar
                    ScrollableTabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        AdminTab.entries.forEach { tab ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = { viewModel.onTabSelected(tab) },
                                text = {
                                    Text(
                                        when (tab) {
                                            AdminTab.METRICS -> "Metrics"
                                            AdminTab.APP_UPDATES -> "App Updates"
                                            AdminTab.ANNOUNCEMENTS -> "Announcements"
                                            AdminTab.USERS -> "Users"
                                            AdminTab.SUBSCRIPTIONS -> "Subscriptions"
                                            AdminTab.BACKUP_MONITOR -> "Backups"
                                            AdminTab.SECURITY_EVENTS -> "Security"
                                            AdminTab.AUDIT_LOGS -> "Audit Logs"
                                            AdminTab.ABUSE_REPORTS -> "Abuse"
                                        }
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            AdminTab.METRICS -> Icons.Default.Assessment
                                            AdminTab.APP_UPDATES -> Icons.Default.SystemUpdate
                                            AdminTab.ANNOUNCEMENTS -> Icons.Default.Campaign
                                            AdminTab.USERS -> Icons.Default.Group
                                            AdminTab.SUBSCRIPTIONS -> Icons.Default.Payments
                                            AdminTab.BACKUP_MONITOR -> Icons.Default.CloudDone
                                            AdminTab.SECURITY_EVENTS -> Icons.Default.Security
                                            AdminTab.AUDIT_LOGS -> Icons.Default.History
                                            AdminTab.ABUSE_REPORTS -> Icons.Default.Report
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Content View
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (uiState.selectedTab) {
                            AdminTab.METRICS -> uiState.metrics?.let { metrics ->
                                AdminMetricsView(metrics)
                            } ?: LoadingSpinner()

                            AdminTab.APP_UPDATES -> AdminAppUpdatesView(
                                currentConfig = uiState.appUpdateConfig,
                                onPublishUpdate = { vCode, vName, url, notes, isMandatory, minCode ->
                                    viewModel.publishAppUpdate(vCode, vName, url, notes, isMandatory, minCode)
                                }
                            )

                            AdminTab.ANNOUNCEMENTS -> AdminAnnouncementsView(
                                announcements = uiState.announcementsList,
                                onCreateAnnouncement = { title, msg, type, aud, targetUid, actLabel, actUrl ->
                                    viewModel.createAnnouncement(title, msg, type, aud, targetUid, actLabel, actUrl)
                                },
                                onDeleteAnnouncement = { id ->
                                    viewModel.deleteAnnouncement(id)
                                }
                            )

                            AdminTab.USERS -> AdminUsersListView(
                                users = uiState.usersList,
                                searchQuery = uiState.userSearchQuery,
                                onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                                onUserClicked = { user -> viewModel.loadUserDetail(user.rawUid) },
                                onToggleUserPremium = { user, makePremium ->
                                    viewModel.updateUserPremiumStatus(user.rawUid.ifBlank { user.uid }, makePremium)
                                }
                            )

                            AdminTab.SUBSCRIPTIONS -> AdminSubscriptionsView(
                                subscriptions = uiState.subscriptionsList
                            )

                            AdminTab.BACKUP_MONITOR -> uiState.backupMetrics?.let { bMetrics ->
                                AdminBackupMonitorView(bMetrics)
                            } ?: LoadingSpinner()

                            AdminTab.SECURITY_EVENTS -> AdminSecurityEventsView(
                                events = uiState.securityEvents,
                                currentSeverity = uiState.securityFilterSeverity,
                                onSelectSeverity = { sev -> viewModel.loadSecurityEvents(sev) }
                            )

                            AdminTab.AUDIT_LOGS -> AdminAuditLogsView(
                                logs = uiState.auditLogs
                            )

                            AdminTab.ABUSE_REPORTS -> AdminAbuseReportsView(
                                reports = uiState.abuseReports
                            )
                        }
                    }
                }

                // User Detail Sheet
                uiState.selectedUserDetail?.let { detail ->
                    val targetUid = detail.rawUid.ifBlank { detail.uid }
                    AdminUserDetailSheet(
                        userDetail = detail,
                        userMediaFiles = uiState.userMediaFiles,
                        isUserMediaLoading = uiState.isUserMediaLoading,
                        onDismiss = { viewModel.dismissUserDetail() },
                        onTogglePremium = { makePremium ->
                            viewModel.updateUserPremiumStatus(targetUid, makePremium)
                        },
                        onUpdateStatus = { newStatus, reason ->
                            viewModel.updateUserAccountStatus(targetUid, newStatus, reason)
                        },
                        onUpdateStorageLimit = { limitBytes ->
                            viewModel.updateUserStorageLimit(targetUid, limitBytes)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AdminAuthPrompt(
    isLoading: Boolean,
    onVerifyClaim: () -> Unit,
    onAuthenticateToken: (String) -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    var useTokenFallback by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Administrator Authorization",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Administrative access is strictly restricted to the authorized app owner (king.khan648k@gmail.com) for subscription monitoring, security audit, and system abuse prevention.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerifyClaim,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_verify_claim_btn")
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Authorize Administrator (king.khan648k@gmail.com)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { useTokenFallback = !useTokenFallback },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (useTokenFallback) "Hide Manual Verification" else "Manual Token Verification")
        }

        if (useTokenFallback) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Server Authorization Token / Claim Key") },
                modifier = Modifier.fillMaxWidth().testTag("admin_token_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onAuthenticateToken(tokenInput) },
                enabled = !isLoading && tokenInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("admin_auth_submit")
            ) {
                Text("Verify Token")
            }
        }
    }
}

@Composable
private fun AdminMetricsView(metrics: AdminDashboardMetrics) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_metrics_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "System Overview & Aggregate Health",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Users Overview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Total Users",
                    value = "${metrics.totalUsers}",
                    icon = Icons.Default.Person,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Active Users",
                    value = "${metrics.activeUsers}",
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Premium Users",
                    value = "${metrics.premiumUsers}",
                    icon = Icons.Default.Payments,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Subscriptions Breakdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Subscriptions Health",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem("Active Subscriptions", "${metrics.activeSubscriptions}", MaterialTheme.colorScheme.primary)
                        MetricItem("Pending Purchases", "${metrics.pendingSubscriptions}", MaterialTheme.colorScheme.tertiary)
                        MetricItem("Canceled (Active)", "${metrics.canceledActiveSubscriptions}", Color(0xFFE65100))
                        MetricItem("Expired", "${metrics.expiredSubscriptions}", MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Backup & Restore Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloud Backup & Restore Telemetry",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem("Backup Success", "${metrics.backupSuccessCount}", Color(0xFF2E7D32))
                        MetricItem("Backup Failures", "${metrics.backupFailureCount}", MaterialTheme.colorScheme.error)
                        MetricItem("Restore Success", "${metrics.restoreSuccessCount}", Color(0xFF2E7D32))
                        MetricItem("Restore Failures", "${metrics.restoreFailureCount}", MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Cloud Storage & Media Items Card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val storageGb = metrics.totalCloudStorageUsageBytes / (1024L * 1024L * 1024L)
                MetricCard(
                    title = "Cloud Storage",
                    value = "$storageGb GB",
                    icon = Icons.Default.Storage,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Cloud Media",
                    value = "${metrics.totalCloudMediaCount}",
                    icon = Icons.Default.PhotoLibrary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Recordings Used",
                    value = "${metrics.recordingUsageCount}",
                    icon = Icons.Default.Mic,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Security Events Alert Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Security Events (${metrics.securityAbuseEventCount} Total)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Failed authentications, rate limits, and permission denials captured.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun AdminUsersListView(
    users: List<AdminUserSummary>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onUserClicked: (AdminUserSummary) -> Unit,
    onToggleUserPremium: (AdminUserSummary, Boolean) -> Unit
) {
    val filteredUsers = users.filter {
        searchQuery.isBlank() ||
                it.uid.contains(searchQuery, ignoreCase = true) ||
                (it.displayName?.contains(searchQuery, ignoreCase = true) == true) ||
                (it.emailMasked?.contains(searchQuery, ignoreCase = true) == true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("admin_user_search_input"),
            placeholder = { Text("Search by masked UID or display name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        if (filteredUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No users matching \"$searchQuery\"" else "No registered users in system",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Real accounts will appear here automatically when users register.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("admin_users_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            items(filteredUsers) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUserClicked(user) }
                        .testTag("admin_user_item_${user.uid}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName ?: user.uid,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "UID: ${user.uid} • ${user.emailMasked ?: "No email"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            StatusBadge(user.accountStatus)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (user.isPremium) "Plan: PREMIUM" else "Plan: FREE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (user.isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val storageMb = user.cloudStorageBytes / (1024 * 1024)
                                Text(
                                    text = "Media: ${user.cloudMediaCount} ($storageMb MB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick toggle button on the card
                            OutlinedButton(
                                onClick = { onToggleUserPremium(user, !user.isPremium) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("admin_quick_toggle_premium_${user.uid}")
                            ) {
                                Icon(
                                    imageVector = if (user.isPremium) Icons.Default.Close else Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (user.isPremium) "Make Free" else "Make Premium",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun StatusBadge(status: AccountStatus) {
    val (bgColor, textColor, label) = when (status) {
        AccountStatus.ACTIVE -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "ACTIVE")
        AccountStatus.PENDING_VERIFICATION -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "PENDING")
        AccountStatus.FLAGGED_SECURITY -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "FLAGGED")
        AccountStatus.SUSPENDED -> Triple(Color(0xFFFFCDD2), Color(0xFFB71C1C), "SUSPENDED")
        AccountStatus.INACTIVE -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), "INACTIVE")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminUserDetailSheet(
    userDetail: AdminUserDetail,
    userMediaFiles: List<AdminUserMediaItem>,
    isUserMediaLoading: Boolean,
    onDismiss: () -> Unit,
    onTogglePremium: (Boolean) -> Unit,
    onUpdateStatus: (AccountStatus, String) -> Unit,
    onUpdateStorageLimit: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showStatusDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var customStorageGbInput by remember { mutableStateOf("") }
    var selectedNewStatus by remember { mutableStateOf(userDetail.accountStatus) }
    var statusReasonInput by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("admin_user_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = userDetail.displayName ?: "User Administration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "UID: ${userDetail.uid}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                StatusBadge(userDetail.accountStatus)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Account Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow("Email", userDetail.emailMasked ?: "None (Guest)")
            DetailRow("Email Verified", if (userDetail.isEmailVerified) "Yes" else "No")
            DetailRow("Created At", dateFormat.format(Date(userDetail.createdAtEpochMs)))
            DetailRow("Last Activity", dateFormat.format(Date(userDetail.lastActivityEpochMs)))

            Spacer(modifier = Modifier.height(16.dp))
            Text("Subscription Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow("Current Plan", userDetail.planName)
            DetailRow("Subscription Status", userDetail.subscriptionStatus)
            userDetail.subscriptionProductId?.let { DetailRow("Product ID", it) }
            userDetail.subscriptionExpiryEpochMs?.let { DetailRow("Expiry Date", dateFormat.format(Date(it))) }

            Spacer(modifier = Modifier.height(12.dp))
            // Admin Grant/Revoke Premium Button
            Button(
                onClick = { onTogglePremium(!userDetail.isPremium) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (userDetail.isPremium) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (userDetail.isPremium) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth().testTag("admin_toggle_user_premium_btn")
            ) {
                Icon(
                    imageVector = if (userDetail.isPremium) Icons.Default.Close else Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (userDetail.isPremium) "Revoke Premium (Set to Free)" else "Grant Premium Plan (Make VIP)")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Cloud Backup & Storage Allocation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val storageMb = userDetail.cloudStorageBytes / (1024 * 1024)
            DetailRow("Cloud Media Count", "${userDetail.cloudMediaCount} items")
            DetailRow("Storage Consumed", "$storageMb MB")
            val currentLimitDisplay = userDetail.customStorageLimitBytes?.let {
                val gb = it / (1024L * 1024L * 1024L)
                val mb = it / (1024L * 1024L)
                if (gb >= 1) "$gb GB (Admin Custom)" else "$mb MB (Admin Custom)"
            } ?: if (userDetail.isPremium) "50 GB (Default Premium)" else "100 MB (Default Free)"
            DetailRow("Allocated Limit", currentLimitDisplay)
            DetailRow("Backup Success / Total", "${userDetail.successfulBackups} / ${userDetail.totalBackupAttempts}")
            DetailRow("Restore Success / Total", "${userDetail.successfulRestores} / ${userDetail.totalRestoreAttempts}")

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val currentGb = userDetail.customStorageLimitBytes?.let { it / (1024L * 1024L * 1024L) }
                    customStorageGbInput = currentGb?.toString() ?: if (userDetail.isPremium) "50" else "1"
                    showStorageDialog = true
                },
                modifier = Modifier.fillMaxWidth().testTag("admin_allocate_storage_btn")
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allocate Cloud Storage Limit")
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showStatusDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("admin_change_user_status_btn")
            ) {
                Text("Update Account Status")
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // User Uploaded Files Section (Admin live file viewer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "User Files & Cloud Uploads (${userMediaFiles.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isUserMediaLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (userMediaFiles.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isUserMediaLoading) "Loading user files from cloud..." else "No files or cloud media found for this user.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    userMediaFiles.forEach { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("admin_user_file_${file.mediaId}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            file.mediaType.startsWith("video") -> Icons.Default.VideoFile
                                            file.mediaType.startsWith("audio") -> Icons.Default.Mic
                                            file.mediaType.startsWith("image") -> Icons.Default.PhotoLibrary
                                            else -> Icons.Default.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = file.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val kb = file.sizeBytes / 1024
                                        val sizeStr = if (kb >= 1024) "${kb / 1024} MB" else "$kb KB"
                                        Text(
                                            text = "${file.mediaType} • $sizeStr • ${file.backupStatus}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                file.cloudinarySecureUrl?.let { url ->
                                    IconButton(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.testTag("admin_view_file_link_${file.mediaId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Open file",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Allocate Cloud Storage") },
            text = {
                Column {
                    Text(
                        "Set custom cloud storage limit for user ${userDetail.uid}. Works for both Free and Premium users.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1", "5", "10", "50", "100").forEach { presetGb ->
                            OutlinedButton(
                                onClick = { customStorageGbInput = presetGb },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("${presetGb}G", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customStorageGbInput,
                        onValueChange = { customStorageGbInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Custom Storage Limit in Gigabytes (GB)") },
                        placeholder = { Text("e.g. 25") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_storage_gb_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val gb = customStorageGbInput.toLongOrNull() ?: 1L
                        val bytes = gb * 1024L * 1024L * 1024L
                        onUpdateStorageLimit(bytes)
                        showStorageDialog = false
                    },
                    modifier = Modifier.testTag("admin_storage_confirm_btn")
                ) {
                    Text("Save Storage Limit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Update User Account Status") },
            text = {
                Column {
                    Text("Select new account status for ${userDetail.uid}:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    AccountStatus.entries.forEach { status ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedNewStatus = status }
                                .padding(vertical = 4.dp)
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedNewStatus == status,
                                onClick = { selectedNewStatus = status }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(status.name)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = statusReasonInput,
                        onValueChange = { statusReasonInput = it },
                        label = { Text("Reason for status change") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateStatus(selectedNewStatus, statusReasonInput.ifBlank { "Administrative action" })
                        showStatusDialog = false
                    }
                ) {
                    Text("Apply Status")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdminSubscriptionsView(subscriptions: List<SubscriptionMonitoringItem>) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_subscriptions_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Subscription Monitoring & Verification",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(subscriptions) { sub ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sub.planName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        SubscriptionStatusBadge(sub.billingStatus)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    DetailRow("User UID", sub.userUidMasked)
                    DetailRow("Product ID", sub.productId)
                    DetailRow("Purchased On", dateFormat.format(Date(sub.purchaseTimeEpochMs)))
                    sub.expiryTimeEpochMs?.let {
                        DetailRow("Renewal/Expiry", dateFormat.format(Date(it)))
                    }
                    DetailRow("Auto-Renewing", if (sub.isAutoRenewing) "Yes" else "No")
                    DetailRow("Server Verified", if (sub.isServerVerified) "Authoritative Verified" else "Pending Verification")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionStatusBadge(status: String) {
    val (bgColor, textColor) = when (status) {
        "ACTIVE" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "CANCELED_ACTIVE" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "PENDING" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "EXPIRED" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Pair(Color(0xFFEEEEEE), Color(0xFF616161))
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = status,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AdminBackupMonitorView(metrics: BackupMonitoringMetrics) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_backup_monitor_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Cloud Backup & Restore Telemetry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Backup Stats Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup Telemetry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Total Backup Attempts", "${metrics.totalBackupAttempts}")
                    DetailRow("Successful Backups", "${metrics.successfulBackups}")
                    DetailRow("Failed Backups", "${metrics.failedBackups}")
                    metrics.lastBackupEpochMs?.let {
                        DetailRow("Last Backup Timestamp", dateFormat.format(Date(it)))
                    }
                }
            }
        }

        // Restore Stats Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Telemetry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Total Restore Attempts", "${metrics.totalRestoreAttempts}")
                    DetailRow("Successful Restores", "${metrics.successfulRestores}")
                    DetailRow("Failed Restores", "${metrics.failedRestores}")
                    metrics.lastRestoreEpochMs?.let {
                        DetailRow("Last Restore Timestamp", dateFormat.format(Date(it)))
                    }
                }
            }
        }

        // Failure Categories
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Error Categories & Diagnostic Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    metrics.failureCategories.forEach { (cat, count) ->
                        DetailRow(cat, "$count occurrences")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSecurityEventsView(
    events: List<SecurityAuditEvent>,
    currentSeverity: SecuritySeverity?,
    onSelectSeverity: (SecuritySeverity?) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().testTag("admin_security_events_view")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentSeverity == null,
                onClick = { onSelectSeverity(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = currentSeverity == SecuritySeverity.CRITICAL,
                onClick = { onSelectSeverity(SecuritySeverity.CRITICAL) },
                label = { Text("Critical") }
            )
            FilterChip(
                selected = currentSeverity == SecuritySeverity.HIGH,
                onClick = { onSelectSeverity(SecuritySeverity.HIGH) },
                label = { Text("High") }
            )
            FilterChip(
                selected = currentSeverity == SecuritySeverity.MEDIUM,
                onClick = { onSelectSeverity(SecuritySeverity.MEDIUM) },
                label = { Text("Medium") }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = event.eventType.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (event.severity) {
                                    SecuritySeverity.CRITICAL, SecuritySeverity.HIGH -> MaterialTheme.colorScheme.error
                                    SecuritySeverity.MEDIUM -> Color(0xFFE65100)
                                    SecuritySeverity.LOW -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(
                                text = dateFormat.format(Date(event.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = event.sanitizedMessage, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Component: ${event.sourceComponent} • User: ${event.affectedUserIdMasked} • IP: ${event.ipAddressMasked}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAuditLogsView(logs: List<AdminAuditLogEntry>) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_audit_logs_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Immutable Admin Action Audit Trail",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(logs) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = log.action,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = dateFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Admin: ${log.adminUid}" + (log.targetUserUidMasked?.let { " • Target: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    if (log.safeMetadata.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.safeMetadata.entries.joinToString(separator = " | ") { "${it.key}: ${it.value}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAbuseReportsView(reports: List<AbuseSecurityReport>) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_abuse_reports_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "System Abuse & Security Incident Reports",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(reports) { rep ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = rep.reportType,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rep.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = rep.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reporter: ${rep.reporterIdMasked} • ${dateFormat.format(Date(rep.timestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
