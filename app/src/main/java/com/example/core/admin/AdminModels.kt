package com.example.core.admin

enum class UserRole {
    NORMAL_USER,
    ADMIN
}

enum class AccountStatus {
    ACTIVE,
    PENDING_VERIFICATION,
    FLAGGED_SECURITY,
    SUSPENDED,
    INACTIVE
}

data class AdminUser(
    val adminId: String,
    val email: String,
    val role: UserRole = UserRole.ADMIN,
    val permissions: Set<AdminPermission> = setOf(
        AdminPermission.VIEW_DASHBOARD_METRICS,
        AdminPermission.VIEW_USER_MANAGEMENT,
        AdminPermission.VIEW_SUBSCRIPTION_MONITORING,
        AdminPermission.VIEW_BACKUP_MONITORING,
        AdminPermission.VIEW_SECURITY_AUDIT_LOGS,
        AdminPermission.MANAGE_USER_STATUS
    ),
    val lastLoginTimestamp: Long = 0L,
    val hasServerCustomClaim: Boolean = true
)

enum class AdminPermission {
    VIEW_DASHBOARD_METRICS,
    VIEW_USER_MANAGEMENT,
    VIEW_SUBSCRIPTION_MONITORING,
    VIEW_BACKUP_MONITORING,
    VIEW_SECURITY_AUDIT_LOGS,
    VIEW_ABUSE_REPORTS,
    MANAGE_USER_STATUS
}

data class AdminDashboardMetrics(
    val totalUsers: Long = 0,
    val activeUsers: Long = 0,
    val premiumUsers: Long = 0,
    val freeUsers: Long = 0,
    val activeSubscriptions: Long = 0,
    val pendingSubscriptions: Long = 0,
    val canceledActiveSubscriptions: Long = 0,
    val expiredSubscriptions: Long = 0,
    val totalCloudMediaCount: Long = 0,
    val totalCloudStorageUsageBytes: Long = 0L,
    val backupSuccessCount: Long = 0,
    val backupFailureCount: Long = 0,
    val restoreSuccessCount: Long = 0,
    val restoreFailureCount: Long = 0,
    val recordingUsageCount: Long = 0,
    val securityAbuseEventCount: Long = 0,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

data class AdminUserSummary(
    val uid: String, // Masked or sanitized identifier for display, e.g., usr_***4f1a
    val rawUid: String,
    val displayName: String?,
    val emailMasked: String?,
    val createdAtEpochMs: Long,
    val lastActivityEpochMs: Long,
    val isPremium: Boolean,
    val subscriptionState: String,
    val accountStatus: AccountStatus,
    val cloudMediaCount: Int,
    val cloudStorageBytes: Long,
    val isCloudBackupEnabled: Boolean
)

data class AdminUserDetail(
    val uid: String,
    val rawUid: String = "",
    val emailMasked: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val createdAtEpochMs: Long,
    val lastActivityEpochMs: Long,
    val isPremium: Boolean,
    val planName: String,
    val subscriptionStatus: String,
    val subscriptionProductId: String?,
    val subscriptionExpiryEpochMs: Long?,
    val cloudMediaCount: Int,
    val cloudStorageBytes: Long,
    val customStorageLimitBytes: Long? = null,
    val totalBackupAttempts: Int,
    val successfulBackups: Int,
    val failedBackups: Int,
    val totalRestoreAttempts: Int,
    val successfulRestores: Int,
    val failedRestores: Int,
    val lastBackupEpochMs: Long?,
    val lastRestoreEpochMs: Long?,
    val accountStatus: AccountStatus,
    val recentSecurityEvents: List<SecurityAuditEvent> = emptyList()
)

data class SubscriptionMonitoringItem(
    val subscriptionId: String,
    val userUidMasked: String,
    val productId: String,
    val planName: String,
    val billingStatus: String, // ACTIVE, PENDING, CANCELED_ACTIVE, EXPIRED
    val purchaseTimeEpochMs: Long,
    val expiryTimeEpochMs: Long?,
    val isAutoRenewing: Boolean,
    val isServerVerified: Boolean
)

data class BackupMonitoringMetrics(
    val totalBackupAttempts: Long = 0,
    val successfulBackups: Long = 0,
    val failedBackups: Long = 0,
    val totalRestoreAttempts: Long = 0,
    val successfulRestores: Long = 0,
    val failedRestores: Long = 0,
    val lastBackupEpochMs: Long? = null,
    val lastRestoreEpochMs: Long? = null,
    val totalStorageBytes: Long = 0,
    val failureCategories: Map<String, Long> = emptyMap()
)

data class SecurityAuditEvent(
    val eventId: String,
    val eventType: SecurityEventType,
    val timestamp: Long,
    val affectedUserIdMasked: String,
    val ipAddressMasked: String,
    val severity: SecuritySeverity,
    val sourceComponent: String,
    val sanitizedMessage: String
)

enum class SecurityEventType {
    FAILED_PIN_ATTEMPT,
    BRUTE_FORCE_LOCKOUT,
    SUSPICIOUS_SIGN_IN,
    INVALID_AUTHORIZATION_ATTEMPT,
    BACKEND_PERMISSION_DENIED,
    BILLING_VERIFICATION_FAILURE,
    BACKUP_INTEGRITY_FAILURE,
    RESTORE_INTEGRITY_FAILURE,
    RATE_LIMIT_EXCEEDED,
    TOKEN_VALIDATION_FAILURE
}

enum class SecuritySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class AdminAuditLogEntry(
    val logId: String,
    val adminUid: String,
    val action: String,
    val targetUserUidMasked: String?,
    val timestamp: Long,
    val safeMetadata: Map<String, String> = emptyMap()
)

data class AbuseSecurityReport(
    val reportId: String,
    val reporterIdMasked: String,
    val reportType: String,
    val description: String,
    val timestamp: Long,
    val status: String
)

data class AppUpdateConfig(
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val isMandatory: Boolean = false,
    val minSupportedVersionCode: Int = 1,
    val publishedAtEpochMs: Long = System.currentTimeMillis()
)

enum class AnnouncementType {
    INFO,
    UPDATE,
    WARNING,
    PROMOTION,
    SECURITY_ALERT
}

enum class TargetAudience {
    ALL_USERS,
    FREE_ONLY,
    PREMIUM_ONLY,
    SPECIFIC_USER
}

data class AdminUserMediaItem(
    val mediaId: String,
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val folderId: String? = null,
    val cloudinarySecureUrl: String? = null,
    val backupStatus: String = "SUCCESS",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class AdminAnnouncement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: AnnouncementType = AnnouncementType.INFO,
    val targetAudience: TargetAudience = TargetAudience.ALL_USERS,
    val targetUserId: String? = null,
    val actionLabel: String? = null,
    val actionUrl: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isDismissible: Boolean = true
)

