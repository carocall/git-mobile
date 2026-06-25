package com.carocall.gitmobile.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.git.ComposeGitProgressMonitor
import com.carocall.gitmobile.data.git.GitManager
import com.carocall.gitmobile.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class GitCommitUiState(
    val status: RepoStatus = RepoStatus(),
    val history: List<CommitInfo> = emptyList(),
    val historyPage: Int = 0,
    val hasMoreHistory: Boolean = true,
    val isHistoryLoading: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val commitMessage: String = "",
    val isLoading: Boolean = false,
    val loadingStatus: String = "",
    val gitProgress: GitProgress? = null,
    val remoteConfig: RemoteProfile = RemoteProfile("", "", null),
    val isInitialLoading: Boolean = true,
    val localIdentity: Pair<String, String> = "" to "",
    val errorMessage: String? = null
)

class GitCommitViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GitCommitUiState())
    val uiState: StateFlow<GitCommitUiState> = _uiState.asStateFlow()

    private val historyPageSize = 20
    private var repoRoot: File? = null

    fun init(repoRoot: File) {
        if (this.repoRoot == null) {
            this.repoRoot = repoRoot
            refresh()
        }
    }

    fun refresh() {
        val root = repoRoot ?: return
        viewModelScope.launch {
            val status = GitManager.getStatus(root)
            val history = GitManager.getHistory(root, skip = 0, limit = historyPageSize)
            val remoteConfig = GitManager.getRemoteConfig(root)
            val localIdentity = GitManager.getLocalIdentity(root)
            
            _uiState.update { state ->
                state.copy(
                    status = status,
                    history = history,
                    historyPage = 0,
                    hasMoreHistory = true,
                    remoteConfig = remoteConfig,
                    localIdentity = localIdentity,
                    isInitialLoading = false,
                    selectedFiles = if (state.selectedFiles.isEmpty() && status.allChanges.isNotEmpty()) {
                        status.allChanges.map { it.first }.toSet()
                    } else state.selectedFiles
                )
            }
        }
    }

    fun loadMoreHistory() {
        val root = repoRoot ?: return
        val currentState = _uiState.value
        if (currentState.isHistoryLoading || !currentState.hasMoreHistory) return

        _uiState.update { it.copy(isHistoryLoading = true) }
        
        viewModelScope.launch {
            val nextPage = currentState.historyPage + 1
            val newCommits = GitManager.getHistory(root, skip = nextPage * historyPageSize, limit = historyPageSize)
            _uiState.update { state ->
                if (newCommits.isEmpty()) {
                    state.copy(hasMoreHistory = false, isHistoryLoading = false)
                } else {
                    state.copy(
                        history = state.history + newCommits,
                        historyPage = nextPage,
                        isHistoryLoading = false
                    )
                }
            }
        }
    }

    fun updateCommitMessage(message: String) {
        _uiState.update { it.copy(commitMessage = message) }
    }

    fun toggleFileSelection(path: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedFiles.contains(path)) {
                state.selectedFiles - path
            } else {
                state.selectedFiles + path
            }
            state.copy(selectedFiles = newSelection)
        }
    }

    fun setAllFilesSelected(selected: Boolean) {
        _uiState.update { state ->
            val newSelection = if (selected) {
                state.status.allChanges.map { it.first }.toSet()
            } else {
                emptySet()
            }
            state.copy(selectedFiles = newSelection)
        }
    }

    fun commit(message: String, files: List<String>, authorName: String, authorEmail: String) {
        val root = repoRoot ?: return
        viewModelScope.launch {
            GitManager.commit(root, message, files, authorName, authorEmail).onSuccess {
                _uiState.update { it.copy(commitMessage = "", selectedFiles = emptySet()) }
                refresh()
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun discardChanges(paths: List<String>) {
        val root = repoRoot ?: return
        viewModelScope.launch {
            GitManager.discardChanges(root, paths).onSuccess {
                _uiState.update { it.copy(selectedFiles = it.selectedFiles - paths.toSet()) }
                refresh()
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = it.errorMessage ?: e.message) }
            }
        }
    }

    fun saveLocalIdentity(name: String, email: String) {
        val root = repoRoot ?: return
        viewModelScope.launch {
            GitManager.saveLocalIdentity(root, name, email)
            refresh()
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun performPull(context: Context, user: String, token: String) {
        val root = repoRoot ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingStatus = context.getString(R.string.pull)) }
            val monitor = ComposeGitProgressMonitor { progress -> _uiState.update { it.copy(gitProgress = progress) } }
            GitManager.pull(root, user, token, monitor).onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = context.getString(R.string.pull_failed, e.message)) }
            }
            _uiState.update { it.copy(isLoading = false, loadingStatus = "", gitProgress = null) }
        }
    }

    fun performPush(context: Context, user: String, token: String) {
        val root = repoRoot ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingStatus = context.getString(R.string.push)) }
            val monitor = ComposeGitProgressMonitor { progress -> _uiState.update { it.copy(gitProgress = progress) } }
            GitManager.push(root, user, token, monitor).onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = context.getString(R.string.push_failed, e.message)) }
            }
            _uiState.update { it.copy(isLoading = false, loadingStatus = "", gitProgress = null) }
        }
    }

    fun performSync(context: Context, user: String, token: String) {
        val root = repoRoot ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingStatus = context.getString(R.string.syncing)) }
            val monitor = ComposeGitProgressMonitor { progress -> _uiState.update { it.copy(gitProgress = progress) } }
            GitManager.sync(root, user, token, context.getString(R.string.sync_pull_failed), context.getString(R.string.push), monitor).onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = context.getString(R.string.sync_failed, e.message)) }
            }
            _uiState.update { it.copy(isLoading = false, loadingStatus = "", gitProgress = null) }
        }
    }
}
