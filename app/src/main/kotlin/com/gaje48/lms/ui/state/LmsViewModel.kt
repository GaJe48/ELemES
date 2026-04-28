package com.gaje48.lms.ui.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.CourseInfo
import com.gaje48.lms.model.LmsUiState
import com.gaje48.lms.model.LoadMode
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LmsViewModel(
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LmsUiState())
    val uiState = _uiState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private fun updateState(transform: LmsUiState.() -> LmsUiState) {
        _uiState.update(transform)
    }

    private fun setError(message: String) {
        updateState { copy(errorMessage = message) }
    }

    private fun emitSnackbar(message: String) {
        _snackbarEvent.trySend(message)
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            val credentials = lmsRepository.savedCredential()
                ?: run {
                    updateState { copy(isSplashReady = true) }
                    return@launch
                }

            updateState { copy(isSplashReady = true, isAutoLoginLoading = true) }

            val (username, password) = credentials
            lmsRepository.login(username, password)
                .onSuccess {
                    updateState {
                        copy(
                            isLogin = true,
                            studentInfo = it.studentInfo,
                            allCourseInfo = it.courses,
                            allPresenceInfo = it.presences
                        )
                    }
                }
                .onFailure {
                    setError(it.toString())
                    updateState { copy(isAutoLoginLoading = false) }
                }
        }
    }


    fun manualLogin(nim: String, pwd: String) {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            lmsRepository.login(nim, pwd)
                .onSuccess {
                    updateState {
                        copy(
                            isLogin = true,
                            studentInfo = it.studentInfo,
                            allCourseInfo = it.courses,
                            allPresenceInfo = it.presences
                        )
                    }
                }
                .onFailure {
                    setError(it.toString())
                }

            updateState { copy(isLoading = false) }
        }
    }

    fun logout() {
        viewModelScope.launch { lmsRepository.clearCredential() }
        _uiState.value = LmsUiState()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            updateState { copy(isRefreshing = true) }

            lmsRepository.fetchDashboardData()
                .onSuccess {
                    updateState {
                        copy(
                            studentInfo = it.studentInfo,
                            allCourseInfo = it.courses,
                            allPresenceInfo = it.presences
                        )
                    }
                }
                .onFailure {
                    val message = it.toString()
                    if (uiState.value.allCourseInfo.isEmpty()) setError(message)
                    else emitSnackbar(message)
                }

            updateState { copy(isRefreshing = false) }
        }
    }

    fun loadMeetingDetail(meetingUrl: String, loadMode: LoadMode = LoadMode.LOADING) {
        viewModelScope.launch {
            when (loadMode) {
                LoadMode.REFRESH -> updateState { copy(isRefreshing = true, errorMessage = null) }
                LoadMode.LOADING -> updateState {
                    copy(isLoading = true, errorMessage = null, allMeetingContent = emptyList())
                }
            }

            lmsRepository.fetchMeetingDetail(meetingUrl)
                .onSuccess { updateState { copy(allMeetingContent = it) } }
                .onFailure {
                    val message = it.toString()
                    if (uiState.value.allMeetingContent.isEmpty()) setError(message)
                    else emitSnackbar(message)
                }

            updateState { copy(isRefreshing = false, isLoading = false) }
        }
    }

    fun submitPresence(courses: CourseInfo, url: String) {
        val presence = uiState.value.allPresenceInfo

        viewModelScope.launch {
            updateState { copy(isPresenceSubmitting = true) }

            lmsRepository.executePresence(url).onFailure { emitSnackbar(it.toString()) }

            lmsRepository.fetchPresenceDetail(
                courses.allMeeting,
                presence[courses.courseCode] ?: emptyList()
            )
                .onSuccess { updateState { copy(allPresenceStatus = it) } }
                .onFailure { emitSnackbar(it.toString()) }

            updateState { copy(isPresenceSubmitting = false) }
        }
    }

    fun loadPresence(courses: CourseInfo, loadMode: LoadMode = LoadMode.LOADING) {
        val presence = uiState.value.allPresenceInfo

        viewModelScope.launch {
            when (loadMode) {
                LoadMode.REFRESH -> updateState { copy(isRefreshing = true, errorMessage = null) }
                LoadMode.LOADING -> updateState {
                    copy(isLoading = true, errorMessage = null, allPresenceStatus = emptyList())
                }
            }

            lmsRepository.fetchPresenceDetail(
                courses.allMeeting,
                presence[courses.courseCode] ?: emptyList()
            )
                .onSuccess { updateState { copy(allPresenceStatus = it) } }
                .onFailure {
                    val message = it.toString()
                    if (uiState.value.allPresenceStatus.isEmpty()) setError(message)
                    else emitSnackbar(message)
                }

            updateState { copy(isRefreshing = false, isLoading = false) }
        }
    }

    fun loadTasks(courses: CourseInfo, loadMode: LoadMode = LoadMode.LOADING) {
        viewModelScope.launch {
            when (loadMode) {
                LoadMode.REFRESH -> updateState { copy(isRefreshing = true, errorMessage = null) }
                LoadMode.LOADING -> updateState {
                    copy(isLoading = true, errorMessage = null, allTaskInfo = emptyMap())
                }
            }

            lmsRepository.fetchTasks(courses.allMeeting)
                .onSuccess { updateState { copy(allTaskInfo = it) } }
                .onFailure {
                    val message = it.toString()
                    if (uiState.value.allTaskInfo.isEmpty()) setError(message)
                    else emitSnackbar(message)
                }

            updateState { copy(isRefreshing = false, isLoading = false) }
        }
    }

    fun submitTask(uri: Uri, taskUrl: String) {
        val notifId = System.currentTimeMillis().toInt()
        lateinit var currentFileName: String

        viewModelScope.launch {
            notificationHelper.showUploadStarted(notifId)

            lmsRepository.uploadTask(uri, taskUrl) { fileName, progress ->
                currentFileName = fileName
                notificationHelper.showUploadProgress(notifId, fileName, progress)
            }
            .onSuccess {
                notificationHelper.showUploadSuccess(notifId, currentFileName)
            }
            .onFailure { e ->
                val message = e.message ?: "Gagal upload file"
                notificationHelper.showUploadFailure(notifId, message)
                emitSnackbar(message)
            }
        }
    }

    fun downloadFile(fileUrl: String) {
        val notifId = System.currentTimeMillis().toInt()
        lateinit var currentFileName: String

        viewModelScope.launch {
            notificationHelper.showDownloadStarted(notifId)

            lmsRepository.downloadFile(fileUrl) { fileName, progress ->
                currentFileName = fileName
                notificationHelper.showDownloadProgress(notifId, fileName, progress)
            }
            .onSuccess {
                notificationHelper.showDownloadSuccess(notifId, currentFileName)
            }
            .onFailure { e ->
                val message = e.message ?: "Gagal mengunduh file"
                notificationHelper.showDownloadFailure(notifId, message)
                emitSnackbar(message)
            }
        }
    }
}
