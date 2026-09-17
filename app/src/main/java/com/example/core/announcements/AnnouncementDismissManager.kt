package com.example.core.announcements

import android.content.Context
import android.content.SharedPreferences
import com.example.core.admin.AdminAnnouncement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages the permanent dismissal of in-app admin announcements.
 * 
 * Strict User Intent: Once a notification/announcement is seen and dismissed by the user,
 * its unique ID is permanently stored locally (and persisted across app restarts).
 * It will NEVER be shown to that user again.
 */
class AnnouncementDismissManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "announcement_dismiss_store"
        private const val KEY_DISMISSED_IDS = "dismissed_announcement_ids"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _dismissedIds = MutableStateFlow<Set<String>>(loadDismissedIds())
    val dismissedIds: StateFlow<Set<String>> = _dismissedIds.asStateFlow()

    private fun loadDismissedIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_DISMISSED_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun isDismissed(announcementId: String): Boolean {
        return _dismissedIds.value.contains(announcementId)
    }

    suspend fun markAsDismissed(announcementId: String) = withContext(Dispatchers.IO) {
        val updated = _dismissedIds.value.toMutableSet().apply {
            add(announcementId)
        }
        sharedPreferences.edit().putStringSet(KEY_DISMISSED_IDS, updated).apply()
        _dismissedIds.value = updated
    }

    fun filterUnseenAnnouncements(announcements: List<AdminAnnouncement>): List<AdminAnnouncement> {
        val dismissed = _dismissedIds.value
        return announcements.filterNot { dismissed.contains(it.id) }
    }
}
