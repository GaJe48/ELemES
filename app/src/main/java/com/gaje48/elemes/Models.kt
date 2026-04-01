package com.gaje48.elemes

// ── Auth ─────────────────────────────────────────────────────────────────────
sealed class LoginResult {
    data class Success(val html: String) : LoginResult()
    data object WrongPassword : LoginResult()
    data object WrongCaptcha : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed interface StatusPresensi {
    data object SudahHadir : StatusPresensi
    data class BelumHadirAdaLink(val linkDownload: String) : StatusPresensi
    data object BelumHadirTanpaLink : StatusPresensi
}

// ── Dashboard ─────────────────────────────────────────────────────────────────
data class StudentInfo(
    val studentName: String = "",
    val npm: String = "",
    val nimEncode: String = "",
    val studyProgram: String = "",
    val classCode: String = "",
    val studentPhoto: String = ""
)

data class CourseInfo(
    val courseCode: String,
    val courseName: String,
    val day: String,
    val clock: String,
    val room: String,
    val lecturerName: String,
    val lecturerHp: String,
    val lecturerPhoto: String,
    val presenceInfo: PresenceInfo,
    val meetingList: Map<Int, String>
)

data class PresenceInfo(val jumlahPertemuan: Int, val persen: Int, val kdJdwEncode: String)

data class InitDataResult(
    val studentInfo: StudentInfo,
    val courseInfo: List<CourseInfo>
)

// ── Meeting ───────────────────────────────────────────────────────────────────
data class MeetingDetail(val type: String, val desc: String, val url: String)

// ── Task ─────────────────────────────────────────────────────────────────────
data class TaskDetail(
    val taskUrl: String,
    val message: String,
    val taskFile: String,
    val deadline: String,
    val viewUrl: String,
    val status: TaskStatus
)

enum class TaskStatus {
    ACTIVE,
    SUBMITTED,
    EXPIRED
}