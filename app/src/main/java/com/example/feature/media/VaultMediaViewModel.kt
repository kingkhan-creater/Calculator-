package com.example.feature.media

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.SecurityResult
import com.example.domain.usecase.ChangePinUseCase
import com.example.domain.usecase.CheckPinSetupUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VaultTab {
    ALL,
    PHOTOS,
    VIDEOS,
    FOLDERS,
    TRASH
}

data class VaultMediaUiState(
    val selectedTab: VaultTab = VaultTab.ALL,
    val activeFolderId: String? = null, // If inside a specific folder view
    val activeFolderName: String? = null,
    val mediaItems: List<VaultMediaItem> = emptyList(),
    val folders: List<VaultFolder> = emptyList(),
    val trashItems: List<VaultMediaItem> = emptyList(),
    val trashCount: Int = 0,
    val selectedMediaIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val previewMediaItem: VaultMediaItem? = null,
    val isCreateFolderDialogOpen: Boolean = false,
    val isMoveToFolderDialogOpen: Boolean = false,
    val isChangePinDialogOpen: Boolean = false,
    val isDeleteOriginalsPromptVisible: Boolean = false,
    val pendingOriginalsUris: List<Uri> = emptyList(),
    val deleteOriginalsCount: Int = 0,
    val isExporting: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

sealed interface VaultMediaUiEvent {
    data class ShareExportUris(val uris: List<Uri>, val mimeType: String) : VaultMediaUiEvent
    data class RequestDeleteGalleryUris(val uris: List<Uri>) : VaultMediaUiEvent
    data class ShowToast(val message: String) : VaultMediaUiEvent
}

class VaultMediaViewModel(
    private val mediaRepository: VaultMediaRepository,
    val checkPinSetupUseCase: CheckPinSetupUseCase,
    val changePinUseCase: ChangePinUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultMediaUiState())
    val uiState: StateFlow<VaultMediaUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<VaultMediaUiEvent>()
    val events: SharedFlow<VaultMediaUiEvent> = _events.asSharedFlow()

    init {
        observeData()
    }

    private fun observeData() {
        // Observe Folders
        mediaRepository.getAllFolders()
            .onEach { folderList ->
                _uiState.update { state ->
                    val updatedFolderName = if (state.activeFolderId != null) {
                        folderList.find { it.id == state.activeFolderId }?.name ?: state.activeFolderName
                    } else null
                    state.copy(
                        folders = folderList,
                        activeFolderName = updatedFolderName
                    )
                }
            }
            .launchIn(viewModelScope)

        // Observe Trash count
        mediaRepository.getTrashCount()
            .onEach { count ->
                _uiState.update { it.copy(trashCount = count) }
            }
            .launchIn(viewModelScope)

        // Observe Media items reactively
        combine(
            mediaRepository.getAllActiveMedia(),
            mediaRepository.getTrashMedia()
        ) { allMedia, trash ->
            _uiState.update { state ->
                val filtered = when (state.selectedTab) {
                    VaultTab.ALL -> {
                        if (state.activeFolderId != null) {
                            allMedia.filter { it.folderId == state.activeFolderId }
                        } else {
                            allMedia
                        }
                    }
                    VaultTab.PHOTOS -> allMedia.filter { it.mediaType == VaultMediaType.PHOTO }
                    VaultTab.VIDEOS -> allMedia.filter { it.mediaType == VaultMediaType.VIDEO }
                    VaultTab.FOLDERS -> {
                        if (state.activeFolderId != null) {
                            allMedia.filter { it.folderId == state.activeFolderId }
                        } else {
                            emptyList()
                        }
                    }
                    VaultTab.TRASH -> trash
                }

                // Also update preview item if open
                val updatedPreview = state.previewMediaItem?.let { prev ->
                    allMedia.find { it.id == prev.id } ?: trash.find { it.id == prev.id }
                }

                state.copy(
                    mediaItems = filtered,
                    trashItems = trash,
                    previewMediaItem = updatedPreview
                )
            }
        }.launchIn(viewModelScope)
    }

    fun onTabSelected(tab: VaultTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                activeFolderId = if (tab != VaultTab.FOLDERS && tab != VaultTab.ALL) null else it.activeFolderId,
                activeFolderName = if (tab != VaultTab.FOLDERS && tab != VaultTab.ALL) null else it.activeFolderName,
                selectedMediaIds = emptySet(),
                isSelectionMode = false
            )
        }
        refreshFilteredList()
    }

    fun onOpenFolder(folder: VaultFolder) {
        _uiState.update {
            it.copy(
                activeFolderId = folder.id,
                activeFolderName = folder.name,
                selectedMediaIds = emptySet(),
                isSelectionMode = false
            )
        }
        refreshFilteredList()
    }

    fun onExitFolder() {
        _uiState.update {
            it.copy(
                activeFolderId = null,
                activeFolderName = null,
                selectedMediaIds = emptySet(),
                isSelectionMode = false
            )
        }
        refreshFilteredList()
    }

    private fun refreshFilteredList() {
        viewModelScope.launch {
            val state = _uiState.value
            // Triggers state refresh through active flow observers
        }
    }

    fun onMediaItemClick(item: VaultMediaItem) {
        val state = _uiState.value
        if (state.isSelectionMode) {
            toggleMediaSelection(item.id)
        } else {
            _uiState.update { it.copy(previewMediaItem = item) }
        }
    }

    fun onMediaItemLongClick(item: VaultMediaItem) {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedMediaIds = it.selectedMediaIds + item.id
            )
        }
    }

    fun toggleMediaSelection(mediaId: String) {
        _uiState.update { state ->
            val updated = if (state.selectedMediaIds.contains(mediaId)) {
                state.selectedMediaIds - mediaId
            } else {
                state.selectedMediaIds + mediaId
            }
            state.copy(
                selectedMediaIds = updated,
                isSelectionMode = updated.isNotEmpty()
            )
        }
    }

    fun onSelectAll() {
        _uiState.update { state ->
            val currentIds = state.mediaItems.map { it.id }.toSet()
            state.copy(
                isSelectionMode = true,
                selectedMediaIds = currentIds
            )
        }
    }

    fun onClearSelection() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedMediaIds = emptySet()
            )
        }
    }

    fun onDismissPreview() {
        _uiState.update { it.copy(previewMediaItem = null) }
    }

    fun onImportMediaSelected(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentFolderId = _uiState.value.activeFolderId
            val result = mediaRepository.importMediaList(uris, currentFolderId)
            _uiState.update { state ->
                val importedCount = result.getOrNull()?.size ?: 0
                state.copy(
                    isLoading = false,
                    isDeleteOriginalsPromptVisible = result.isSuccess && importedCount > 0,
                    pendingOriginalsUris = if (result.isSuccess && importedCount > 0) uris else emptyList(),
                    deleteOriginalsCount = importedCount,
                    successMessage = if (result.isSuccess) "Imported $importedCount item(s) safely" else null,
                    errorMessage = if (result.isFailure) "Failed to import media: ${result.exceptionOrNull()?.localizedMessage}" else null
                )
            }
        }
    }

    fun onDismissDeleteOriginalsPrompt() {
        _uiState.update {
            it.copy(
                isDeleteOriginalsPromptVisible = false,
                pendingOriginalsUris = emptyList(),
                deleteOriginalsCount = 0
            )
        }
    }

    fun onConfirmDeleteOriginals() {
        val uris = _uiState.value.pendingOriginalsUris
        _uiState.update {
            it.copy(
                isDeleteOriginalsPromptVisible = false,
                pendingOriginalsUris = emptyList(),
                deleteOriginalsCount = 0
            )
        }
        if (uris.isNotEmpty()) {
            viewModelScope.launch {
                _events.emit(VaultMediaUiEvent.RequestDeleteGalleryUris(uris))
            }
        }
    }

    fun onOriginalsDeletionCompleted(success: Boolean) {
        _uiState.update {
            it.copy(
                successMessage = if (success) "Original files removed from Gallery. Device space freed!" else "Original files kept in Gallery."
            )
        }
    }

    fun createGalleryDeleteIntentSender(uris: List<Uri>): IntentSender? {
        return mediaRepository.createGalleryDeleteIntentSender(uris)
    }

    suspend fun deleteGalleryOriginalsDirectly(uris: List<Uri>): Int {
        return mediaRepository.deleteGalleryOriginalsDirectly(uris)
    }

    fun onCreateFolderRequested() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = true) }
    }

    fun onDismissCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = false) }
    }

    fun onCreateFolder(folderName: String) {
        if (folderName.isBlank()) return
        viewModelScope.launch {
            val result = mediaRepository.createFolder(folderName.trim())
            _uiState.update { state ->
                state.copy(
                    isCreateFolderDialogOpen = false,
                    successMessage = if (result.isSuccess) "Folder created" else null,
                    errorMessage = if (result.isFailure) "Failed to create folder" else null
                )
            }
        }
    }

    fun onDeleteFolder(folderId: String) {
        viewModelScope.launch {
            val result = mediaRepository.deleteFolder(folderId)
            _uiState.update { state ->
                state.copy(
                    activeFolderId = if (state.activeFolderId == folderId) null else state.activeFolderId,
                    activeFolderName = if (state.activeFolderId == folderId) null else state.activeFolderName,
                    successMessage = if (result.isSuccess) "Folder removed" else null
                )
            }
        }
    }

    fun onOpenMoveToFolderDialog() {
        _uiState.update { it.copy(isMoveToFolderDialogOpen = true) }
    }

    fun onDismissMoveToFolderDialog() {
        _uiState.update { it.copy(isMoveToFolderDialogOpen = false) }
    }

    fun onMoveSelectedToFolder(targetFolderId: String?) {
        val selectedIds = _uiState.value.selectedMediaIds.toList()
        val previewItem = _uiState.value.previewMediaItem
        val idsToMove = if (selectedIds.isNotEmpty()) selectedIds else listOfNotNull(previewItem?.id)

        if (idsToMove.isEmpty()) return

        viewModelScope.launch {
            val result = mediaRepository.moveMediaToFolder(idsToMove, targetFolderId)
            _uiState.update { state ->
                state.copy(
                    isMoveToFolderDialogOpen = false,
                    selectedMediaIds = emptySet(),
                    isSelectionMode = false,
                    successMessage = if (result.isSuccess) "Moved ${idsToMove.size} item(s)" else null
                )
            }
        }
    }

    fun onDeleteSelectedMedia() {
        val selectedIds = _uiState.value.selectedMediaIds.toList()
        val previewItem = _uiState.value.previewMediaItem
        val idsToDelete = if (selectedIds.isNotEmpty()) selectedIds else listOfNotNull(previewItem?.id)

        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            val result = mediaRepository.moveToTrash(idsToDelete)
            _uiState.update { state ->
                state.copy(
                    selectedMediaIds = emptySet(),
                    isSelectionMode = false,
                    previewMediaItem = null,
                    successMessage = if (result.isSuccess) "Moved ${idsToDelete.size} item(s) to Trash" else null
                )
            }
        }
    }

    fun onRestoreSelectedMedia() {
        val selectedIds = _uiState.value.selectedMediaIds.toList()
        val previewItem = _uiState.value.previewMediaItem
        val idsToRestore = if (selectedIds.isNotEmpty()) selectedIds else listOfNotNull(previewItem?.id)

        if (idsToRestore.isEmpty()) return

        viewModelScope.launch {
            val result = mediaRepository.restoreFromTrash(idsToRestore)
            _uiState.update { state ->
                state.copy(
                    selectedMediaIds = emptySet(),
                    isSelectionMode = false,
                    previewMediaItem = null,
                    successMessage = if (result.isSuccess) "Restored ${idsToRestore.size} item(s)" else null
                )
            }
        }
    }

    fun onPermanentlyDeleteSelected() {
        val selectedIds = _uiState.value.selectedMediaIds.toList()
        val previewItem = _uiState.value.previewMediaItem
        val idsToDelete = if (selectedIds.isNotEmpty()) selectedIds else listOfNotNull(previewItem?.id)

        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            val result = mediaRepository.permanentlyDelete(idsToDelete)
            _uiState.update { state ->
                state.copy(
                    selectedMediaIds = emptySet(),
                    isSelectionMode = false,
                    previewMediaItem = null,
                    successMessage = if (result.isSuccess) "Permanently deleted ${idsToDelete.size} item(s)" else null
                )
            }
        }
    }

    fun onEmptyTrash() {
        viewModelScope.launch {
            val trashIds = _uiState.value.trashItems.map { it.id }
            if (trashIds.isEmpty()) return@launch
            val result = mediaRepository.permanentlyDelete(trashIds)
            _uiState.update { state ->
                state.copy(
                    selectedMediaIds = emptySet(),
                    isSelectionMode = false,
                    successMessage = if (result.isSuccess) "Trash emptied" else null
                )
            }
        }
    }

    fun onExportToGallerySelected() {
        if (_uiState.value.isExporting) return
        val selectedIds = _uiState.value.selectedMediaIds.toList()
        val previewItem = _uiState.value.previewMediaItem
        val idsToExport = if (selectedIds.isNotEmpty()) selectedIds else listOfNotNull(previewItem?.id)

        if (idsToExport.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val result = mediaRepository.exportToGallery(idsToExport)
            _uiState.update { it.copy(isExporting = false) }

            result.fold(
                onSuccess = { summary ->
                    val message = when {
                        summary.successCount == 0 && summary.failedCount > 0 ->
                            "Failed to export ${summary.failedCount} item(s) to Gallery"
                        summary.failedCount > 0 ->
                            "Moved ${summary.successCount} item(s) to Gallery (${summary.failedCount} failed)"
                        summary.successCount == 1 ->
                            "Moved to Gallery (Pictures/Vault)"
                        else ->
                            "${summary.successCount} item(s) moved to Gallery"
                    }
                    _uiState.update {
                        it.copy(
                            isSelectionMode = false,
                            selectedMediaIds = emptySet(),
                            previewMediaItem = null,
                            successMessage = message
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = "Export to Gallery failed: ${error.message ?: "Unknown error"}")
                    }
                }
            )
        }
    }

    fun onShareSelected() {
        val selectedIds = _uiState.value.selectedMediaIds.toList()
        val previewItem = _uiState.value.previewMediaItem
        val idsToExport = if (selectedIds.isNotEmpty()) selectedIds else listOfNotNull(previewItem?.id)

        if (idsToExport.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val result = mediaRepository.prepareExport(idsToExport)
            _uiState.update { it.copy(isExporting = false) }

            if (result.isSuccess) {
                val uris = result.getOrNull().orEmpty()
                if (uris.isNotEmpty()) {
                    val mimeType = if (uris.size == 1) {
                        val singleItem = _uiState.value.mediaItems.find { it.id == idsToExport.first() }
                            ?: _uiState.value.trashItems.find { it.id == idsToExport.first() }
                        singleItem?.mimeType ?: "*/*"
                    } else {
                        "*/*"
                    }
                    _events.emit(VaultMediaUiEvent.ShareExportUris(uris, mimeType))
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to prepare files for sharing") }
            }
        }
    }

    fun onExportSelected() {
        onExportToGallerySelected()
    }

    fun onOpenChangePinDialog() {
        _uiState.update { it.copy(isChangePinDialogOpen = true) }
    }

    fun onDismissChangePinDialog() {
        _uiState.update { it.copy(isChangePinDialogOpen = false) }
    }

    fun onChangePin(currentPin: String, newPin: String) {
        viewModelScope.launch {
            when (val result = changePinUseCase(currentPin, newPin)) {
                is SecurityResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isChangePinDialogOpen = false,
                            successMessage = "PIN successfully changed"
                        )
                    }
                }
                is SecurityResult.Error -> {
                    _uiState.update {
                        it.copy(
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun onClearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    class Factory(
        private val mediaRepository: VaultMediaRepository,
        private val checkPinSetupUseCase: CheckPinSetupUseCase,
        private val changePinUseCase: ChangePinUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VaultMediaViewModel(
                mediaRepository = mediaRepository,
                checkPinSetupUseCase = checkPinSetupUseCase,
                changePinUseCase = changePinUseCase
            ) as T
        }
    }
}
