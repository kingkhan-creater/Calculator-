package com.example.core.admin

import kotlinx.coroutines.flow.StateFlow

interface AdminRepository {
    val currentAdmin: StateFlow<AdminUser?>

    suspend fun verifyAdminAuthorization(forceRefresh: Boolean = false): Result<AdminUser>
    suspend fun verifyAdminRoleWithToken(idToken: String): Result<AdminUser>

    suspend fun getDashboardMetrics(): Result<AdminDashboardMetrics>

    // Users & Pagination
    suspend fun getUsers(pageSize: Int = 20, lastUserId: String? = null): Result<List<AdminUserSummary>>
    suspend fun getUserDetail(userId: String): Result<AdminUserDetail>
    suspend fun updateUserAccountStatus(userId: String, newStatus: AccountStatus, reason: String): Result<Unit>
    suspend fun updateUserStorageLimit(userId: String, storageLimitBytes: Long): Result<Unit>
    suspend fun updateUserPremiumStatus(userId: String, isPremium: Boolean): Result<Unit>
    suspend fun getUserMediaFiles(userId: String): Result<List<AdminUserMediaItem>>

    // Subscriptions
    suspend fun getSubscriptionMonitoringList(pageSize: Int = 20): Result<List<SubscriptionMonitoringItem>>

    // Backup & Restore
    suspend fun getBackupMonitoringMetrics(): Result<BackupMonitoringMetrics>

    // Security & Abuse
    suspend fun getSecurityEvents(limit: Int = 50, filterSeverity: SecuritySeverity? = null): Result<List<SecurityAuditEvent>>
    suspend fun reportSecurityEvent(event: SecurityAuditEvent): Result<Unit>
    suspend fun getAbuseReports(): Result<List<AbuseSecurityReport>>

    // Audit Logs
    suspend fun getAdminAuditLogs(limit: Int = 50): Result<List<AdminAuditLogEntry>>
    suspend fun recordAdminAuditLog(action: String, targetUserUid: String?, metadata: Map<String, String>): Result<Unit>

    // App Updates & Version Control
    suspend fun getAppUpdateConfig(): Result<AppUpdateConfig>
    suspend fun publishAppUpdate(config: AppUpdateConfig): Result<Unit>

    // In-App Broadcast Announcements
    suspend fun getAnnouncements(): Result<List<AdminAnnouncement>>
    suspend fun createAnnouncement(announcement: AdminAnnouncement): Result<Unit>
    suspend fun deleteAnnouncement(announcementId: String): Result<Unit>
    suspend fun getActiveAnnouncementsForUser(userId: String?, isPremium: Boolean): Result<List<AdminAnnouncement>>
}

