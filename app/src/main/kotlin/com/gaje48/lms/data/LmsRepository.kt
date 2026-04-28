package com.gaje48.lms.data

import android.net.Uri
import com.gaje48.lms.model.AccountProblemException
import com.gaje48.lms.model.SessionExpiredException

class LmsRepository(
    private val internetDataSource: InternetDataSource,
    private val storageDataSource: StorageDataSource,
    private val localDataSource: LocalDataSource
) {
    suspend fun savedCredential() = localDataSource.getCredentials()

    suspend fun clearCredential() = localDataSource.clearCredentials()

    private suspend fun <T> withAutoReLogin(block: suspend () -> T): T {
        return try {
            block()
        } catch (_: SessionExpiredException) {
            val (username, password) = localDataSource.getCredentials()
                ?: error("Username atau password tidak ditemukan")

            internetDataSource.loginStatus(username, password)
            block()
        } catch (_: AccountProblemException) {
            localDataSource.clearCredentials()
            error("Password berubah")
        }
    }

    suspend fun login(nim: String, pwd: String) = runCatching {
        internetDataSource.loginStatus(nim, pwd)
        localDataSource.saveCredentials(nim, pwd)
        internetDataSource.getDashboardData()
    }

    suspend fun fetchDashboardData() = runCatching {
        withAutoReLogin { internetDataSource.getDashboardData() }
    }

    suspend fun fetchMeetingDetail(meetingUrl: String) = runCatching {
        withAutoReLogin { internetDataSource.getAllMeetingContent(meetingUrl) }
    }

    suspend fun fetchPresenceDetail(allMeeting: Map<Int, String>, allPresenceInfo: List<Boolean>) = runCatching {
        withAutoReLogin { internetDataSource.getAllPresenceStatus(allMeeting, allPresenceInfo) }
    }

    suspend fun fetchTasks(courseMeetings: Map<Int, String>) = runCatching {
        withAutoReLogin { internetDataSource.getAllTask(courseMeetings) }
    }

    suspend fun executePresence(fileUrl: String) = runCatching {
        withAutoReLogin { internetDataSource.executePresence(fileUrl) }
    }

    suspend fun downloadFile(
        fileUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit
    ) = runCatching {
        withAutoReLogin {
            val response = internetDataSource.downloadFile(fileUrl)
            if (response.status.value !in 200..299) {
                error("File tidak ada di server (${response.status.value})")
            }

            storageDataSource.saveToDownloads(response, onProgress)
        }
    }

    suspend fun uploadTask(
        uri: Uri,
        taskUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit
    ) = runCatching {
        withAutoReLogin {
            val fileSource = storageDataSource.openFileStream(uri)
            if (fileSource.size > 20 * 1024 * 1024) error("Ukuran file melebihi 20MB")

            fileSource.stream.use {
                internetDataSource.uploadTask(
                    fileSource.name,
                    fileSource.size,
                    it,
                    taskUrl,
                    onProgress
                )
            }
        }
    }
}
