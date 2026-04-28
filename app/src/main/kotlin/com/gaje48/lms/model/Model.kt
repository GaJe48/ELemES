package com.gaje48.lms.model

import kotlinx.serialization.Serializable
import java.io.InputStream

data class LmsUiState(
    val isSplashReady: Boolean = false,
    val isLoading: Boolean = false,
    val isAutoLoginLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLogin: Boolean = false,
    val errorMessage: String? = null,
    val studentInfo: StudentInfo? = null,
    val allCourseInfo: List<CourseInfo> = emptyList(),
    val allPresenceInfo: Map<String, List<Boolean>> = emptyMap(),
    val allMeetingContent: List<MeetingContent> = emptyList(),
    val allTaskInfo: Map<Int, TaskInfo> = emptyMap(),
    val allPresenceStatus: List<StatusPresensi> = emptyList(),
    val uploadProgress: Float = 0f,
    val uploadFileName: String = "",
    val isUploading: Boolean = false,
    val isPresenceSubmitting: Boolean = false,
)

sealed interface StatusPresensi {
    data object SudahHadir : StatusPresensi
    data class BelumHadirAdaLink(val linkDownload: String) : StatusPresensi
    data object BelumHadirTanpaLink : StatusPresensi
}

enum class LoadMode {
    LOADING,
    REFRESH,
}

data class DashboardData(
    val studentInfo: StudentInfo,
    val courses: List<CourseInfo>,
    val presences: Map<String, List<Boolean>>
)

data class StudentInfo(
    val studentName: String,
    val npm: String,
    val studyProgram: String,
    val classCode: String,
    val studentPhoto: String
)

@Serializable
data class CourseInfo(
    val courseCode: String,
    val courseName: String,
    val day: String,
    val clock: String,
    val room: String,
    val lecturerName: String,
    val lecturerHp: String,
    val lecturerPhoto: String,
    val allMeeting: Map<Int, String>
)

data class MeetingContent(val type: String, val desc: String, val url: String)

data class TaskInfo(
    val taskUrl: String,
    val message: String?,
    val taskFile: String?,
    val deadline: String,
    val viewUrl: String?,
    val isSubmitted: Boolean,
    val isExpired: Boolean
)

data class FileSource(val name: String, val size: Long, val stream: InputStream)

class SessionExpiredException : Exception()

class AccountProblemException : Exception()
