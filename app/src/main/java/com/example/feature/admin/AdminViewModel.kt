package com.example.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.admin.AbuseSecurityReport
import com.example.core.admin.AccountStatus
import com.example.core.admin.AdminAnnouncement
import com.example.core.admin.AdminAuditLogEntry
import com.example.core.admin.AdminDashboardMetrics
import com.example.core.admin.AdminPermission
import com.example.core.admin.AdminRepository
import com.example.core.admin.AdminUser
import com.example.core.admin.AdminUserDetail
import com.example.core.admin.AdminUserMediaItem
import com.example.core.admin.AdminUserSummary
import com.example.core.admin.AnnouncementType
import com.example.core.admin.AppUpdateConfig
import com.example.core.admin.BackupMonitoringMetrics
import com.example.core.admin.SecurityAuditEvent
import com.example.core.admin.SecuritySeverity
import com.example.core.admin.SubscriptionMonitoringItem
import com.example.core.admin.TargetAudience
import com.example.core.subscription.EntitlementManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminTab {
    METRICS,
    APP_UPDATES,
    ANNOUNCEMENTS,
    USERS,
    SUBSCRIPTIONS,
    BACKUP_MONITOR,
    SECURITY_EVENTS,
    AUDIT_LOGS,
    ABUSE_REPORTS
}

data class AdminUiState(
    val isAuthenticatedAdmin: Boolean = false,
    val currentAdmin: AdminUser? = null,
    val isLoading: Boolean = false,
    val selectedTab: AdminTab = AdminTab.METRICS,
    val metrics: AdminDashboardMetrics? = null,
    val appUpdateConfig: AppUpdateConfig? = null,
    val announcementsList: List<AdminAnnouncement> = emptyList(),
    val usersList: List<AdminUserSummary> = emptyList(),
    val selectedUserDetail: AdminUserDetail? = null,
    val isUserDetailLoading: Boolean = false,
    val userMediaFiles: List<AdminUserMediaItem> = emptyList(),
    val isUserMediaLoading: Boolean = false,
    val isAdminTestModeFree: Boolean = false,
    val isAdminSelfPremiumActive: Boolean = false,
    val subscriptionsList: List<SubscriptionMonitoringItem> = emptyList(),
    val backupMetrics: BackupMonitoringMetrics? = null,
    val securityEvents: List<SecurityAuditEvent> = emptyList(),
    val securityFilterSeverity: SecuritySeverity? = null,
    val auditLogs: List<AdminAuditLogEntry> = emptyList(),
    val abuseReports: List<AbuseSecurityReport> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val userSearchQuery: String = ""
)


class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val entitlementManager: EntitlementManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        // Collect current admin session
        viewModelScope.launch {
            adminRepository.currentAdmin.collect { admin ->
                _uiState.update {
                    it.copy(
                        isAuthenticatedAdmin = admin != null,
                        currentAdmin = admin
                    )
                }
                if (admin != null) {
                    loadAdminData()
                }
            }
        }
    }

    fun verifyAdminAuthorization(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = adminRepository.verifyAdminAuthorization(forceRefresh)
            result.onSuccess { admin ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentAdmin = admin,
                        isAuthenticatedAdmin = true,
                        successMessage = "Admin identity verified via server claim."
                    )
                }
                loadAdminData()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Admin verification failed."
                    )
                }
            }
        }
    }

    fun authenticateWithAdminToken(token: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = adminRepository.verifyAdminRoleWithToken(token)
            result.onSuccess { admin ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentAdmin = admin,
                        isAuthenticatedAdmin = true,
                        successMessage = "Authorized session established."
                    )
                }
                loadAdminData()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun onTabSelected(tab: AdminTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            AdminTab.METRICS -> loadDashboardMetrics()
            AdminTab.APP_UPDATES -> loadAppUpdateConfig()
            AdminTab.ANNOUNCEMENTS -> loadAnnouncements()
            AdminTab.USERS -> loadUsers()
            AdminTab.SUBSCRIPTIONS -> loadSubscriptions()
            AdminTab.BACKUP_MONITOR -> loadBackupMetrics()
            AdminTab.SECURITY_EVENTS -> loadSecurityEvents()
            AdminTab.AUDIT_LOGS -> loadAuditLogs()
            AdminTab.ABUSE_REPORTS -> loadAbuseReports()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(userSearchQuery = query) }
    }

    fun loadAdminData() {
        loadDashboardMetrics()
        loadAppUpdateConfig()
        loadAnnouncements()
        loadUsers()
        loadSubscriptions()
        loadBackupMetrics()
        loadSecurityEvents()
        loadAuditLogs()
        loadAbuseReports()
    }

    fun loadAppUpdateConfig() {
        viewModelScope.launch {
            val result = adminRepository.getAppUpdateConfig()
            _uiState.update {
                it.copy(
                    appUpdateConfig = result.getOrNull(),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun publishAppUpdate(
        versionCode: Int,
        versionName: String,
        apkUrl: String,
        releaseNotes: String,
        isMandatory: Boolean,
        minSupportedVersionCode: Int = 1
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val config = AppUpdateConfig(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                releaseNotes = releaseNotes,
                isMandatory = isMandatory,
                minSupportedVersionCode = minSupportedVersionCode,
                publishedAtEpochMs = System.currentTimeMillis()
            )
            val result = adminRepository.publishAppUpdate(config)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        appUpdateConfig = config,
                        successMessage = "App version v$versionName (Code: $versionCode) published successfully!"
                    )
                }
                loadAuditLogs()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to publish update: ${error.message}"
                    )
                }
            }
        }
    }

    fun loadAnnouncements() {
        viewModelScope.launch {
            val result = adminRepository.getAnnouncements()
            _uiState.update {
                it.copy(
                    announcementsList = result.getOrDefault(emptyList()),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun createAnnouncement(
        title: String,
        message: String,
        type: AnnouncementType,
        targetAudience: TargetAudience,
        targetUserId: String? = null,
        actionLabel: String? = null,
        actionUrl: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val announcement = AdminAnnouncement(
                title = title,
                message = message,
                type = type,
                targetAudience = targetAudience,
                targetUserId = targetUserId?.ifBlank { null },
                actionLabel = actionLabel?.ifBlank { null },
                actionUrl = actionUrl?.ifBlank { null },
                createdAtEpochMs = System.currentTimeMillis()
            )
            val result = adminRepository.createAnnouncement(announcement)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Notification broadcast sent successfully!"
                    )
                }
                loadAnnouncements()
                loadAuditLogs()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to broadcast notification: ${error.message}"
                    )
                }
            }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = adminRepository.deleteAnnouncement(announcementId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Announcement removed from system."
                    )
                }
                loadAnnouncements()
                loadAuditLogs()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to delete announcement: ${error.message}"
                    )
                }
            }
        }
    }


    fun loadDashboardMetrics() {
        viewModelScope.launch {
            val result = adminRepository.getDashboardMetrics()
            _uiState.update {
                it.copy(
                    metrics = result.getOrNull(),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            val result = adminRepository.getUsers(pageSize = 50)
            _uiState.update {
                it.copy(
                    usersList = result.getOrDefault(emptyList()),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun loadUserDetail(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUserDetailLoading = true, userMediaFiles = emptyList()) }
            val result = adminRepository.getUserDetail(userId)
            _uiState.update {
                it.copy(
                    isUserDetailLoading = false,
                    selectedUserDetail = result.getOrNull(),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
            // Automatically fetch media files for this user
            loadUserMediaFiles(userId)
        }
    }

    fun loadUserMediaFiles(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUserMediaLoading = true) }
            val result = adminRepository.getUserMediaFiles(userId)
            _uiState.update {
                it.copy(
                    isUserMediaLoading = false,
                    userMediaFiles = result.getOrDefault(emptyList())
                )
            }
        }
    }

    fun dismissUserDetail() {
        _uiState.update { it.copy(selectedUserDetail = null, userMediaFiles = emptyList()) }
    }

    fun toggleAdminSelfPremium(enable: Boolean) {
        entitlementManager?.setAdminOverrideMode(if (enable) true else null)
        _uiState.update {
            it.copy(
                isAdminSelfPremiumActive = enable,
                isAdminTestModeFree = false,
                successMessage = if (enable) "Admin account switched to Lifetime Premium (Full Access)" else "Admin account reset to normal status"
            )
        }
    }

    fun toggleAdminTestAsFree(enable: Boolean) {
        entitlementManager?.setAdminOverrideMode(if (enable) false else null)
        _uiState.update {
            it.copy(
                isAdminTestModeFree = enable,
                isAdminSelfPremiumActive = false,
                successMessage = if (enable) "Testing Mode: App running strictly in FREE mode" else "Testing Mode disabled"
            )
        }
    }

    fun updateUserPremiumStatus(userId: String, isPremium: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = adminRepository.updateUserPremiumStatus(userId, isPremium)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = if (isPremium) "Granted Premium status to user ($userId)" else "Revoked Premium status for user ($userId)"
                    )
                }
                loadUsers()
                if (_uiState.value.selectedUserDetail?.rawUid == userId || _uiState.value.selectedUserDetail?.uid == userId) {
                    loadUserDetail(userId)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to update user premium status: ${error.message}"
                    )
                }
            }
        }
    }

    fun updateUserAccountStatus(userId: String, newStatus: AccountStatus, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = adminRepository.updateUserAccountStatus(userId, newStatus, reason)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "User status updated to ${newStatus.name}"
                    )
                }
                loadUsers()
                if (_uiState.value.selectedUserDetail?.uid == userId) {
                    loadUserDetail(userId)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to update status: ${error.message}"
                    )
                }
            }
        }
    }

    fun updateUserStorageLimit(userId: String, storageLimitBytes: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = adminRepository.updateUserStorageLimit(userId, storageLimitBytes)
            result.onSuccess {
                val gb = storageLimitBytes / (1024L * 1024L * 1024L)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Allocated $gb GB storage limit to user."
                    )
                }
                loadUsers()
                if (_uiState.value.selectedUserDetail?.uid == userId) {
                    loadUserDetail(userId)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to update storage limit: ${error.message}"
                    )
                }
            }
        }
    }

    fun loadSubscriptions() {
        viewModelScope.launch {
            val result = adminRepository.getSubscriptionMonitoringList()
            _uiState.update {
                it.copy(
                    subscriptionsList = result.getOrDefault(emptyList()),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun loadBackupMetrics() {
        viewModelScope.launch {
            val result = adminRepository.getBackupMonitoringMetrics()
            _uiState.update {
                it.copy(
                    backupMetrics = result.getOrNull(),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun loadSecurityEvents(severity: SecuritySeverity? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(securityFilterSeverity = severity) }
            val result = adminRepository.getSecurityEvents(limit = 50, filterSeverity = severity)
            _uiState.update {
                it.copy(
                    securityEvents = result.getOrDefault(emptyList()),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun loadAuditLogs() {
        viewModelScope.launch {
            val result = adminRepository.getAdminAuditLogs(limit = 50)
            _uiState.update {
                it.copy(
                    auditLogs = result.getOrDefault(emptyList()),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun loadAbuseReports() {
        viewModelScope.launch {
            val result = adminRepository.getAbuseReports()
            _uiState.update {
                it.copy(
                    abuseReports = result.getOrDefault(emptyList()),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else it.errorMessage
                )
            }
        }
    }

    fun onClearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onClearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    class Factory(
        private val adminRepository: AdminRepository,
        private val entitlementManager: EntitlementManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
                return AdminViewModel(adminRepository, entitlementManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
