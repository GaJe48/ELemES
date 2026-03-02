package com.example.lmsunindra

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jsoup.Jsoup
import androidx.core.content.edit
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay

class Backend(application: Application) : AndroidViewModel(application) {
    private val prefer = application.getSharedPreferences("prefer", Context.MODE_PRIVATE)

    var isLoading by mutableStateOf(false)
        private set
    var isLogin by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set
    var studentProfileUI by mutableStateOf(StudentProfile())
        private set
    var lectureCourseUI by mutableStateOf<List<LectureCourse>>(emptyList())
        private set
    var meetingDetailUI by mutableStateOf(MeetingDetail())
        private set
    var taskDetailUI by mutableStateOf<TaskDetail?>(null)
        private set

    private val baseRequest: Request.Builder
        get() = Request.Builder()
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")

    private val webClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val cookieStore = mutableListOf<Cookie>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore.clear()
                cookieStore.addAll(cookies)
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore
        })
        .addInterceptor { chain ->
            val request = chain.request()

            if (request.header("Skip-Interceptor") == "true") {
                val cleanRequest = request.newBuilder().removeHeader("Skip-Interceptor").build()
                return@addInterceptor chain.proceed(cleanRequest)
            }

            var response = chain.proceed(request)
            val html = response.peekBody(Long.MAX_VALUE).string()

            if (!request.url.toString().contains("login_new") && html.contains("name=\"csrf_token\"")) {
                println("🚨 SATPAM: Cookie Basi! Menahan request ke ${request.url}...")
                response.close()

                val htmlDashboardBaru = runBlocking { silentLogin() }

                if (htmlDashboardBaru.isNotEmpty()) {
                    println("🚨 SATPAM: Silent Login Sukses! Mengulang request asli...")
                    response = chain.proceed(request)
                } else {
                    println("🚨 SATPAM: Auto-Relogin gagal.")
                    isLogin = false
                }
            }
            response
        }
        .build()

    sealed class LoginResult {
        data class Success(val html: String) : LoginResult()
        object WrongPassword : LoginResult()
        object WrongCaptcha : LoginResult()
        data class Error(val message: String) : LoginResult()
    }

    private suspend fun executeLogin(nim: String, pwd: String): LoginResult {
        try {
            val reqPayload = baseRequest.url("https://lms.unindra.ac.id/login_new")
                .header("Skip-Interceptor", "true").build()
            val htmlPayload = Jsoup.parse(webClient.newCall(reqPayload).execute().body.string())

            val tCsrf = htmlPayload.select("input[name=csrf_token]").`val`() ?: ""

            var randomName = ""; var randomValue = ""
            for (input in htmlPayload.select("input[type=hidden]")) {
                val n = input.attr("name")
                if (n.length == 32) {
                    randomName = n; randomValue = input.`val`(); break
                }
            }

            val reqCaptcha = baseRequest.url("https://lms.unindra.ac.id/kapca")
                .header("Skip-Interceptor", "true").build()
            val bytes = webClient.newCall(reqCaptcha).execute().body.bytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return LoginResult.Error("Gagal decode gambar")

            val aiAnswer = solveCaptcha(bitmap)
            if (aiAnswer.isEmpty()) return LoginResult.WrongCaptcha

            val formLogin = FormBody.Builder()
                .add("csrf_token", tCsrf).add(randomName, randomValue)
                .add("username", nim).add("pswd", pwd).add("kapca", aiAnswer)
                .build()

            val reqLogin = baseRequest.url("https://lms.unindra.ac.id/login_new")
                .header("Skip-Interceptor", "true")
                .post(formLogin).build()

            val htmlLogin = webClient.newCall(reqLogin).execute().body.string()

            return if (htmlLogin.contains("<title>Member")) {
                LoginResult.Success(htmlLogin)
            } else if (htmlLogin.contains("Username atau password salah")) {
                LoginResult.WrongPassword
            } else if (htmlLogin.contains("Jawaban Captcha Salah")) {
                LoginResult.WrongCaptcha
            } else {
                LoginResult.Error("Gangguan server Unindra.")
            }
        } catch (e: Exception) {
            return LoginResult.Error(e.message ?: "Error jaringan tidak diketahui")
        }
    }

    init {
        if (!prefer.getString("nim", "").isNullOrEmpty()) {
            isLoading = true

            viewModelScope.launch(Dispatchers.IO) {
                val htmlDashboard = silentLogin()
                if (htmlDashboard.isNotEmpty()) {
                    scrapeDashboard(htmlDashboard)
                    isLogin = true
                }
                delay(50)
                isLoading = false
            }
        }
    }

    fun loginManual(nim: String, pwd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            errorMessage = ""

            for (attempt in 1..3) {
                println("🚀 Login Manual Percobaan $attempt...")
                when (val result = executeLogin(nim, pwd)) {
                    is LoginResult.Success -> {
                        println("✅ LOGIN BERHASIL!")
                        prefer.edit { putString("nim", nim).putString("pwd", pwd) }
                        scrapeDashboard(result.html)
                        isLogin = true
                        break
                    }
                    is LoginResult.WrongPassword -> {
                        println("❌ Password Salah.")
                        errorMessage = "NIM atau Password salah!"
                        break
                    }
                    is LoginResult.WrongCaptcha -> {
                        println("⚠️ AI Salah Captcha. Mengulang secara instan...")
                        if (attempt == 3) errorMessage = "Gagal login. AI meleset membaca Captcha 3 kali."
                    }
                    is LoginResult.Error -> {
                        println("❌ Error: ${result.message}")
                        errorMessage = result.message
                        break
                    }
                }
            }
            delay(50)
            isLoading = false
        }
    }

    private suspend fun silentLogin(): String {
        val savedNim = prefer.getString("nim", "") ?: ""
        val savedPwd = prefer.getString("pwd", "") ?: ""

        for (attempt in 1..3) {
            println("🕵️ Silent Login Percobaan $attempt...")
            when (val result = executeLogin(savedNim, savedPwd)) {
                is LoginResult.Success -> {
                    println("✅ SILENT LOGIN BERHASIL!")
                    return result.html
                }
                is LoginResult.WrongCaptcha -> {
                    println("⚠️ AI Salah Captcha. Mencoba mengendus lagi...")
                }
                else -> return ""
            }
        }
        return ""
    }

    private suspend fun solveCaptcha(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val cleanText = visionText.text.replace(" ", "").trim()
                var result = cleanText
                try {
                    val mathString = cleanText.replace(Regex("[^0-9+]"), "")
                    val matchResult = Regex("(\\d+)([+])(\\d+)").find(mathString)
                    if (matchResult != null) {
                        result = (matchResult.groupValues[1].toInt() + matchResult.groupValues[3].toInt()).toString()
                    }
                } catch (e: Exception) {println(e)}

                if (continuation.isActive) continuation.resume(result)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume("")
            }
    }

    private fun scrapeDashboard(htmlDashboard: String) {
        val document = Jsoup.parse(htmlDashboard)
        try {
            val rawName = document.selectFirst("div.pull-left.info p")?.text() ?: "Name not found"
            val studentName = rawName.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            val npm = document.selectFirst("li.user-body strong")?.text() ?: ""
            val studyProgram = document.selectFirst("span.Badge-info")?.text() ?: ""
            val classCode = document.selectFirst("span.pull-right.text-bold.badge")?.text()?.trim() ?: ""
            val studentPhoto = "https://lms.unindra.ac.id/lms_publik/images/users/thumbs/$npm.png"

            studentProfileUI = StudentProfile(studentName, npm, studyProgram, classCode, studentPhoto)

            val meetingDict = mutableMapOf<String, List<String>>()
            for (tree in document.select("li.treeview")) {
                val titleSidebar = tree.selectFirst("span")?.text()?.trim() ?: continue
                val parts = titleSidebar.split(" ")
                if (parts.size >= 2) {
                    val scheduleKey = "${parts[0]} ${parts[1]}"

                    val meetingList = mutableListOf<String>()
                    for (li in tree.select("ul li")) {
                        val link = li.selectFirst("a")?.attr("href") ?: ""
                        meetingList.add(link)
                    }
                    meetingDict[scheduleKey] = meetingList
                }
            }

            val scrapeClass = mutableListOf<LectureCourse>()
            for (element in document.select("div.box-widget")) {
                val lecturerName = element.selectFirst("h3.widget-user-username")?.text()?.trim() ?: ""
                val rawHp = element.selectFirst("h5.widget-user-desc")?.text()?.replace("HP :", "")?.trim() ?: ""
                val lecturerHp = rawHp.ifEmpty { "Not available" }
                val lecturerPhoto = element.selectFirst("img")?.attr("src") ?: ""

                val rawCourse = element.selectFirst("span.header_badeg")?.text()?.trim() ?: ""
                val splitCourse = rawCourse.split("-", limit = 2)
                val courseCode = if (splitCourse.isNotEmpty()) splitCourse[0].trim() else ""
                val courseName = if (splitCourse.size > 1) splitCourse[1].trim() else rawCourse

                val rawDetail = element.selectFirst("span.text-green")?.text()?.trim() ?: ""
                val detailParts = rawDetail.split("|").map { it.trim() }
                val room = detailParts.getOrNull(1)?.replace("Ruang:", "")?.trim() ?: ""
                val rawTime = detailParts.getOrNull(2)?.replace("Waktu:", "")?.trim() ?: ""
                val day = if (rawTime.contains(",")) rawTime.split(",")[0].trim() else ""
                val clock = if (rawTime.contains(",")) rawTime.split(",")[1].trim() else rawTime

                val meetingList = meetingDict["$day $clock"] ?: emptyList()

                scrapeClass.add(LectureCourse(courseCode, courseName, day, clock, room, lecturerName, lecturerHp, lecturerPhoto, meetingList))
            }

            lectureCourseUI = scrapeClass
            println("✅ Berhasil mengekstrak ${lectureCourseUI.size} kelas!")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun autoAbsenPertemuan(urlPertemuan: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("🕵️ Memasuki halaman pertemuan: $urlPertemuan")

                val reqPage = baseRequest.url(urlPertemuan).build()
                val resPage = webClient.newCall(reqPage).execute()
                val htmlPage = resPage.body.string()

                val document = Jsoup.parse(htmlPage)
                val downloadLink = document.selectFirst("a[href*=/pertemuan/force_download/]")?.attr("href")

                if (!downloadLink.isNullOrEmpty()) {
                    println("🎯 Target kunci ditemukan! Mengeksekusi GET ke: $downloadLink")

                    val reqDownload = baseRequest.url(downloadLink).build()
                    val resDownload = webClient.newCall(reqDownload).execute()

                    // 🚨 TRIK SILUMAN: Langsung tutup koneksinya!
                    // Server udah keburu nyatet kita absen, tapi kuota internet kita selamat!
                    resDownload.close()

                    println("✅ AUTO-ABSEN SUKSES! (File tidak didownload, hanya trigger server)")
                } else {
                    println("⚠️ Tidak ada file materi di pertemuan ini. Mungkin dosen belum upload?")
                }

            } catch (e: Exception) {
                println("❌ Error saat mencoba absen siluman: ${e.message}")
            }
        }
    }

    fun getMeetingDetail(meetingUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val req = baseRequest.url(meetingUrl).build()
            val res = webClient.newCall(req).execute()
            val html = res.body.string()
            val parser = Jsoup.parse(html)

            val meetingFileList = mutableSetOf<String>()
            for (element in parser.select("a[href*=force_download]")) {
                meetingFileList.add(element.attr("href"))
            }

            val gMeetRedirect = parser.selectFirst("a[onclick*=kelas_gmeet]")?.attr("onclick")
                ?.substringAfter("'")?.substringBefore("'") ?: ""
            val gMeetUrl = if (gMeetRedirect.isNotEmpty()) {
                val gMeetRedirectReq = baseRequest.url(gMeetRedirect).build()
                val gMeetRedirectRes = webClient.newCall(gMeetRedirectReq).execute()

                gMeetRedirectRes.body.string()
            } else ""

            val taskUrl = parser.selectFirst("a[href*=member_tugas]")?.attr("href")?: ""

            meetingDetailUI = MeetingDetail(taskUrl, gMeetUrl, meetingFileList.toList())
        }
    }

    fun getTaskDetail(taskUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val req = baseRequest.url(taskUrl).build()
            val res = webClient.newCall(req).execute()
            val html = res.body.string()
            val parser = Jsoup.parse(html)

            val message = parser.selectFirst("h4[class*=attachment-heading]")?.text() ?: ""
            val taskFile = parser.selectFirst("a[href*=force_download]")?.attr("href") ?: ""
            val taskSubmit = parser.selectFirst("a[href*=submit_tugas]")?.attr("href") ?: ""

            taskDetailUI = TaskDetail(message, taskFile, taskSubmit)
        }
    }

    fun downloadMateri(context: Context, fileUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "download_channel"
            val notifId = fileUrl.hashCode() // Bikin ID unik biar kalau download 2 file barengan notifnya pisah

            // Bikin "Channel" (Wajib untuk Android 8.0 ke atas)
            val channel = NotificationChannel(channelId, "Download Materi", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)

            // Desain Awal Notifikasi (Mode Loading Mutar)
            val notifBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download) // Icon panah bawah bawaan Android
                .setContentTitle("Menyiapkan Download...")
                .setOngoing(true) // Bikin notif nggak bisa di-swipe (dibuang) sama user
                .setOnlyAlertOnce(true) // Biar HP nggak bunyi "Ting!" berkali-kali pas progres naik
                .setProgress(100, 0, true) // True = loading muter-muter tanpa angka

            notificationManager.notify(notifId, notifBuilder.build())

            try {
                println("📥 Memulai proses download dari: $fileUrl")

                val req = baseRequest.url(fileUrl).build()
                val res = webClient.newCall(req).execute()

                val fileName = res.request.url.toString().substringAfterLast("/")

                // 3. SIAPKAN TEMPAT DI FOLDER "DOWNLOADS" HP (Standar Android Modern / Scoped Storage)
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LMS")
                }

                // Minta Android buatin file kosongnya
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        val inputStream = res.body.byteStream()
                        val totalBytes = res.body.contentLength()

                        val buffer = ByteArray(8 * 1024)
                        var downloadedBytes = 0L
                        var bytesRead = inputStream.read(buffer)

                        // 🔥 TRIK RAHASIA: Timer biar HP nggak meledak!
                        var lastUpdateTime = System.currentTimeMillis()

                        notifBuilder.setContentTitle(fileName) // Ubah judul notif jadi nama file

                        while (bytesRead != -1) {
                            outputStream?.write(buffer, 0, bytesRead) // Tuang ke HP
                            downloadedBytes += bytesRead

                            // UPDATE NOTIFIKASI (Maksimal tiap 0.5 detik sekali)
                            val currentTime = System.currentTimeMillis()
                            if (totalBytes > 0L && currentTime - lastUpdateTime > 500) {

                                val progress = (downloadedBytes * 100 / totalBytes).toInt()

                                notifBuilder.setProgress(100, progress, false)
                                    .setContentText("Selesai $progress%")

                                notificationManager.notify(notifId, notifBuilder.build())
                                lastUpdateTime = currentTime

                            }

                            bytesRead = inputStream.read(buffer) // Sendok lagi...
                        }

                    }
                }

                // 3. UBAH NOTIFIKASI JADI "SUKSES"
                notifBuilder.setContentText("Download Selesai!")
                    .setProgress(0, 0, false) // Hilangkan progress bar
                    .setOngoing(false) // Boleh di-swipe/dibuang user
                    .setSmallIcon(android.R.drawable.stat_sys_download_done) // Ganti icon centang

                notificationManager.notify(notifId, notifBuilder.build())

            } catch (e: Exception) {
                // 4. UBAH NOTIFIKASI JADI "GAGAL"
                notifBuilder.setContentTitle("Download Gagal")
                    .setContentText(e.message)
                    .setProgress(0, 0, false)
                    .setOngoing(false)

                notificationManager.notify(notifId, notifBuilder.build())
            }
        }
    }
}

data class StudentProfile(
    val studentName: String = "",
    val npm: String = "",
    val studyProgram: String = "",
    val classCode: String = "",
    val studentPhoto: String = ""
)
data class LectureCourse(
    val courseCode: String, val courseName: String,
    val day: String, val clock: String, val room: String,
    val lecturerName: String, val lecturerHp: String, val lecturerPhoto: String,
    val meetingList: List<String>
)
data class MeetingDetail(
    val taskUrl: String = "",
    val gMeetUrl: String = "",
    val meetingFileList: List<String> = emptyList()
)
data class TaskDetail(
    val message: String,
    val taskFile: String,
    val taskSubmit: String
)