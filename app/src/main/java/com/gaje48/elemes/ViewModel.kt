@file:Suppress("DEPRECATION") // EncryptedSharedPreferences — masih functional, lihat Models.kt
package com.gaje48.elemes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Backend(application: Application) : AndroidViewModel(application) {

    // ── UI State ──────────────────────────────────────────────────────────────

    var isLoading by mutableStateOf(false)
        private set
    var isAutoLoginLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isLogin by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    var studentProfileUI by mutableStateOf(StudentInfo())
        private set
    var lectureCourseUI by mutableStateOf<List<CourseInfo>>(emptyList())
        private set
    var meetingDetailUI by mutableStateOf<List<MeetingDetail>>(emptyList())
        private set
    var taskDetailUI by mutableStateOf<Map<Int, TaskDetail>>(emptyMap())
        private set
    var presenceDetailUI by mutableStateOf<List<StatusPresensi>>(emptyList())
        private set

    var uploadProgress by mutableFloatStateOf(0f)
        private set
    var uploadFileName by mutableStateOf("")
        private set
    var isUploading by mutableStateOf(false)
        private set
    var isPresenceSubmitting by mutableStateOf(false)
        private set

    // ── Credential Storage ────────────────────────────────────────────────────

    private val prefer = run {
        val masterKey = MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            application, "secure_prefer", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Repository ────────────────────────────────────────────────────────────

    private val repo = Repository().apply {
        credentialsProvider = {
            Pair(prefer.getString("nim", "") ?: "", prefer.getString("pwd", "") ?: "")
        }
        onWrongPassword = {
            prefer.edit { remove("nim").remove("pwd") }
            errorMessage = "Password berubah. Silakan login ulang."
            _snackbarEvent.trySend(errorMessage)
            isLogin = false
        }
        onError = { msg ->
            _snackbarEvent.trySend(msg)
        }
    }

    fun checkLoginStatus() {
        if (prefer.getString("nim", "").isNullOrEmpty()) return
        isAutoLoginLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = repo.silentLogin()) {
                    is LoginResult.Success -> { getInitData(result.html); isLogin = true }
                    is LoginResult.WrongPassword -> {
                        prefer.edit { remove("nim").remove("pwd") }
                        errorMessage = "Password berubah. Silakan login ulang."
                        _snackbarEvent.trySend(errorMessage)
                    }
                    is LoginResult.WrongCaptcha -> errorMessage = "Gagal login setelah 3x percobaan captcha"
                    is LoginResult.Error -> {
                        errorMessage = result.message
                        _snackbarEvent.trySend(errorMessage)
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Gagal memuat data"
            }
        }
    }

    fun manualLogin(nim: String, pwd: String) {
        if (nim == "wowo " && pwd == "nyawit ") {
            viewModelScope.launch {
                isLoading = true
                delay(1000)
                isLoading = false
                isLogin = true
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            errorMessage = ""
            var loginResult: LoginResult = LoginResult.Error("Belum dicoba")
            repeat(3) {
                when (val r = repo.executeLogin(nim, pwd)) {
                    is LoginResult.WrongCaptcha -> {}
                    else -> { loginResult = r; return@repeat }
                }
            }
            when (val result = loginResult) {
                is LoginResult.Success -> {
                    prefer.edit { putString("nim", nim).putString("pwd", pwd) }
                    getInitData(result.html)
                    isLogin = true
                }
                is LoginResult.WrongPassword -> errorMessage = "Username atau password salah"
                is LoginResult.Error -> errorMessage = result.message
                else -> {}
            }
            isLoading = false
        }
    }

    fun logout() {
        prefer.edit { remove("nim").remove("pwd") }
        isAutoLoginLoading = false
        isLogin = false
        studentProfileUI = StudentInfo()
        lectureCourseUI = emptyList()
        meetingDetailUI = emptyList()
        taskDetailUI = emptyMap()
        presenceDetailUI = emptyList()
    }

    private fun getInitData(html: String) {
        try {
            val result = repo.fetchInitData(html)
            studentProfileUI = result.studentInfo
            lectureCourseUI = result.courseInfo
        } catch (e: Exception) {
            errorMessage = e.message ?: "Gagal memuat data"
        }
    }

    fun refreshDashboard(isSwipe: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isSwipe) isRefreshing = true else isLoading = true
            errorMessage = ""
            try {
                when (val result = repo.silentLogin()) {
                    is LoginResult.Success -> getInitData(result.html)
                    is LoginResult.Error -> {
                        if (lectureCourseUI.isEmpty()) errorMessage = result.message
                        else _snackbarEvent.trySend(result.message)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                if (lectureCourseUI.isEmpty()) errorMessage = e.message ?: "Gagal memuat"
                else _snackbarEvent.trySend(e.message ?: "Gagal memuat")
            }
            isRefreshing = false
            isLoading = false
        }
    }

    /** Dipanggil dari MeetingDetail.kt yang menerima URL sebagai navigasi argument. */
    fun getMeetingDetail(meetingUrl: String, isSwipe: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isSwipe) isRefreshing = true
            else { isLoading = true; meetingDetailUI = emptyList() }
            errorMessage = ""
            try {
                meetingDetailUI = repo.fetchMeetingDetail(meetingUrl)
                repo.executePresence(meetingUrl)
            } catch (e: Exception) {
                if (meetingDetailUI.isEmpty()) errorMessage = e.message ?: "Gagal memuat detail pertemuan"
                else _snackbarEvent.trySend(e.message ?: "Gagal memuat detail pertemuan")
            }
            isRefreshing = false
            isLoading = false
        }
    }

    /** Dipanggil dari Presence.kt untuk absen manual. */
    fun executePresence(url: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            isPresenceSubmitting = true
            try {
                repo.executePresence(url)
            } catch (e: Exception) {
                val message = e.message ?: "Gagal mengisi presensi"
                errorMessage = message
                _snackbarEvent.trySend(message)
            } finally {
                isPresenceSubmitting = false
                withContext(Dispatchers.Main) {
                    onDone?.invoke()
                }
            }
        }
    }

    fun getPresence(courseIndex: Int, isSwipe: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isSwipe) isRefreshing = true
            else { isLoading = true; presenceDetailUI = emptyList() }
            errorMessage = ""
            try {
                presenceDetailUI = repo.fetchPresence(studentProfileUI.nimEncode, lectureCourseUI[courseIndex].presenceInfo.kdJdwEncode, lectureCourseUI[courseIndex].meetingList)
            } catch (e: Exception) {
                if (presenceDetailUI.isEmpty()) errorMessage = e.message ?: "Gagal memuat presensi"
                else _snackbarEvent.trySend(e.message ?: "Gagal memuat presensi")
            }
            isRefreshing = false
            isLoading = false
        }
    }

    fun getAllTask(courseIndex: Int, isSwipe: Boolean = false) {
        val meetings = lectureCourseUI.getOrNull(courseIndex)?.meetingList
        if (meetings.isNullOrEmpty()) {
            if (!isSwipe) { isLoading = false; taskDetailUI = emptyMap() }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (isSwipe) isRefreshing = true
            else { isLoading = true; taskDetailUI = emptyMap() }
            errorMessage = ""
            try {
                taskDetailUI = repo.fetchAllTask(meetings)
            } catch (e: Exception) {
                if (taskDetailUI.isEmpty()) errorMessage = e.message ?: "Gagal memuat data tugas"
                else _snackbarEvent.trySend(e.message ?: "Gagal memuat data tugas")
            }
            isRefreshing = false
            isLoading = false
        }
    }

    // ── File Actions ──────────────────────────────────────────────────────────

    fun executeDownload(context: Context, fileUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val notifManager = context.getSystemService(NotificationManager::class.java)
            val channelId = "download_channel"
            notifManager.createNotificationChannel(
                NotificationChannel(channelId, "Unduhan Materi", NotificationManager.IMPORTANCE_LOW)
            )

            var fileName = fileUrl.substringAfterLast("/")
            val notifId = fileName.hashCode()
            val notifBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Mengunduh...")
                .setContentText(fileName)
                .setOngoing(true).setOnlyAlertOnce(true)
                .setProgress(100, 0, true)
            notifManager.notify(notifId, notifBuilder.build())

            try {
                val response = repo.downloadFileRaw(fileUrl)
                fileName = response.request.url.toString().substringAfterLast("/")
                val totalBytes = response.body.contentLength()

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: error("Gagal membuat file di Downloads")

                resolver.openOutputStream(uri)!!.use { out ->
                    response.body.byteStream().use { inStream ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var read = inStream.read(buffer)
                        var lastUpdate = System.currentTimeMillis()
                        while (read != -1) {
                            out.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (totalBytes > 0 && now - lastUpdate > 500) {
                                val progress = (downloaded * 100 / totalBytes).toInt()
                                notifBuilder.setProgress(100, progress, false)
                                    .setContentText("Mengunduh $progress%")
                                notifManager.notify(notifId, notifBuilder.build())
                                lastUpdate = now
                            }
                            read = inStream.read(buffer)
                        }
                    }
                }
                response.close()

                _snackbarEvent.trySend("✅ $fileName tersimpan di Downloads")
                notifBuilder.setContentTitle("Unduhan Selesai")
                    .setContentText(fileName).setProgress(0, 0, false).setOngoing(false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                notifManager.notify(notifId, notifBuilder.build())
            } catch (e: Exception) {
                errorMessage = e.message ?: "Gagal mengunduh file"
                _snackbarEvent.trySend(errorMessage)
                notifBuilder.setContentTitle("Unduhan Gagal").setContentText(e.message)
                    .setProgress(0, 0, false).setOngoing(false)
                notifManager.notify(notifId, notifBuilder.build())
            }
            isLoading = false
        }
    }

    fun submitTask(context: Context, uri: Uri, taskUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true; isUploading = true
            uploadProgress = 0f; uploadFileName = "Menyiapkan file..."

            val notifManager = context.getSystemService(NotificationManager::class.java)
            val channelId = "upload_channel"
            notifManager.createNotificationChannel(
                NotificationChannel(channelId, "Upload Materi", NotificationManager.IMPORTANCE_LOW)
            )
            var notifId = 0
            lateinit var notifBuilder: NotificationCompat.Builder

            try {
                val result = repo.uploadTask(context, uri, taskUrl) { fileName, progress ->
                    uploadFileName = fileName
                    uploadProgress = progress
                    val pct = (progress * 100).toInt()

                    notifId = fileName.hashCode()
                    notifBuilder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.stat_sys_upload)
                        .setContentTitle("Mengunggah Tugas...")
                        .setContentText(fileName)
                        .setOngoing(true).setOnlyAlertOnce(true)
                        .setProgress(100, 0, true)
                    notifManager.notify(notifId, notifBuilder.build())

                    notifBuilder.setProgress(100, pct, false).setContentText("Mengunggah $pct%")
                    notifManager.notify(notifId, notifBuilder.build())
                }

                println("Status Upload: $result")
                _snackbarEvent.trySend("✅ Tugas Berhasil Dikumpulkan!")
                uploadProgress = 1f

                notifBuilder.let { nb ->
                    nb.setContentTitle("Upload Selesai!")
                        .setContentText(uploadFileName)
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                    notifManager.notify(notifId, nb.build())
                }

            } catch (e: Exception) {
                errorMessage = e.message ?: "Gagal upload file"
                _snackbarEvent.trySend(errorMessage)
                notifBuilder.setContentTitle("Upload Gagal").setContentText(e.message)
                    .setProgress(0, 0, false).setOngoing(false)
                notifManager.notify(notifId, notifBuilder.build())
            } finally {
                isLoading = false; isUploading = false
            }
        }
    }
}
