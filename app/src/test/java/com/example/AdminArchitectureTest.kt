package com.example

import com.example.core.admin.AccountStatus
import com.example.core.admin.AdminPermission
import com.example.core.admin.UserRole
import com.example.data.admin.AdminRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdminArchitectureTest {

    private lateinit var adminRepository: AdminRepositoryImpl

    @Before
    fun setup() {
        adminRepository = AdminRepositoryImpl()
    }

    @Test
    fun `unauthenticated access to admin operations is strictly rejected`() = runTest {
        val metricsResult = adminRepository.getDashboardMetrics()
        assertTrue(metricsResult.isFailure)
        assertTrue(metricsResult.exceptionOrNull() is SecurityException)

        val usersResult = adminRepository.getUsers()
        assertTrue(usersResult.isFailure)
        assertTrue(usersResult.exceptionOrNull() is SecurityException)

        val subscriptionsResult = adminRepository.getSubscriptionMonitoringList()
        assertTrue(subscriptionsResult.isFailure)
        assertTrue(subscriptionsResult.exceptionOrNull() is SecurityException)

        val backupsResult = adminRepository.getBackupMonitoringMetrics()
        assertTrue(backupsResult.isFailure)
        assertTrue(backupsResult.exceptionOrNull() is SecurityException)

        val securityEventsResult = adminRepository.getSecurityEvents()
        assertTrue(securityEventsResult.isFailure)
        assertTrue(securityEventsResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `server verified admin authentication grants role and permission access`() = runTest {
        val authResult = adminRepository.verifyAdminRoleWithToken("valid_admin_token_from_server")
        assertTrue(authResult.isSuccess)
        val admin = authResult.getOrNull()
        assertNotNull(admin)
        assertEquals(UserRole.ADMIN, admin?.role)
        assertTrue(admin!!.permissions.contains(AdminPermission.VIEW_DASHBOARD_METRICS))
        assertTrue(admin.permissions.contains(AdminPermission.VIEW_USER_MANAGEMENT))
        assertTrue(admin.permissions.contains(AdminPermission.VIEW_SUBSCRIPTION_MONITORING))
        assertTrue(admin.permissions.contains(AdminPermission.VIEW_BACKUP_MONITORING))
        assertTrue(admin.permissions.contains(AdminPermission.VIEW_SECURITY_AUDIT_LOGS))

        // Query metrics
        val metricsResult = adminRepository.getDashboardMetrics()
        assertTrue(metricsResult.isSuccess)
        val metrics = metricsResult.getOrNull()
        assertNotNull(metrics)
        assertTrue(metrics!!.totalUsers > 0)
        assertTrue(metrics.totalCloudStorageUsageBytes > 0)
        assertTrue(metrics.activeSubscriptions > 0)
    }

    @Test
    fun `user summaries and activity logs do not expose sensitive raw credentials or files`() = runTest {
        adminRepository.verifyAdminRoleWithToken("valid_token")

        val usersResult = adminRepository.getUsers()
        assertTrue(usersResult.isSuccess)
        val users = usersResult.getOrNull().orEmpty()

        // Ensure user IDs and emails are masked and no raw secrets or private URLs exist
        users.forEach { item ->
            assertTrue("User ID must be masked for privacy", item.uid.contains("***"))
            item.emailMasked?.let {
                assertTrue("Email must be masked", it.contains("***"))
            }
        }

        val logsResult = adminRepository.getSecurityEvents()
        assertTrue(logsResult.isSuccess)
        val logs = logsResult.getOrNull()!!
        assertTrue(logs.isNotEmpty())
        logs.forEach { log ->
            assertTrue("User ID in logs must be masked", log.affectedUserIdMasked.contains("***"))
            assertTrue("IP Address in logs must be masked", log.ipAddressMasked.contains("***"))
        }
    }

    @Test
    fun `subscription and backup telemetry returns authoritative data`() = runTest {
        adminRepository.verifyAdminRoleWithToken("valid_token")

        val subResult = adminRepository.getSubscriptionMonitoringList()
        assertTrue(subResult.isSuccess)
        val subs = subResult.getOrNull()!!
        assertTrue(subs.isNotEmpty())
        assertTrue(subs.any { it.billingStatus == "ACTIVE" })

        val backupResult = adminRepository.getBackupMonitoringMetrics()
        assertTrue(backupResult.isSuccess)
        val backupMetrics = backupResult.getOrNull()!!
        assertTrue(backupMetrics.totalBackupAttempts > 0)
        assertTrue(backupMetrics.successfulBackups > 0)
        assertTrue(backupMetrics.failureCategories.isNotEmpty())
    }

    @Test
    fun `user status update records immutable audit log`() = runTest {
        adminRepository.verifyAdminRoleWithToken("valid_token")

        val updateResult = adminRepository.updateUserAccountStatus(
            userId = "usr_sample_123",
            newStatus = AccountStatus.FLAGGED_SECURITY,
            reason = "Automated test update"
        )
        assertTrue(updateResult.isSuccess)

        val auditLogsResult = adminRepository.getAdminAuditLogs()
        assertTrue(auditLogsResult.isSuccess)
        val logs = auditLogsResult.getOrNull()!!
        assertTrue(logs.isNotEmpty())
    }
}
