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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.runtime.mutableFloatStateOf
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink

class Backend(application: Application) : AndroidViewModel(application) {
    var isLoading by mutableStateOf(false)
        private set
    var isAutoLoginLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isLogin by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf(String())
        private set
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()
    var studentProfileUI by mutableStateOf(StudentProfile())
        private set
    var lectureCourseUI by mutableStateOf<List<LectureCourse>>(emptyList())
        private set
    var meetingDetailUI by mutableStateOf<List<MeetingDetail>>(emptyList())
        private set
    var taskDetailUI by mutableStateOf<Map<Int, TaskDetail>>(emptyMap())
        private set
    var presenceDetailUI by mutableStateOf<List<String>>(emptyList())
        private set

    var uploadProgress by mutableFloatStateOf(0f)
        private set
    var uploadFileName by mutableStateOf(String())
        private set
    var isUploading by mutableStateOf(false)
        private set


    private val prefer = application.getSharedPreferences("prefer", Context.MODE_PRIVATE)
    private var isDemo = false
    private val cachePresence = mutableListOf<String>()
    private var nimCachePresence = String()
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
            val html = response.peekBody(70).string()

            if (!request.url.toString().contains("login_new") && html.contains("Login | LMS UNINDRA")) {
                println("🚨 SATPAM: Cookie Basi! Menahan request ke ${request.url}...")
                response.close()

                when (val loginResult = runBlocking { silentLogin() }) {
                    is LoginResult.Success -> {
                        println("🚨 SATPAM: Silent Login Sukses! Mengulang request asli...")
                        response = chain.proceed(request)
                    }
                    is LoginResult.WrongPassword -> {
                        println("🚨 SATPAM: Password Salah! Hapus kredensial, paksa login ulang.")
                        prefer.edit { remove("nim").remove("pwd") }
                        errorMessage = "Password berubah. Silakan login ulang."
                        _snackbarEvent.trySend(errorMessage)
                        isLogin = false
                    }
                    is LoginResult.Error -> {
                        println("🚨 SATPAM: Error jaringan — ${loginResult.message}")
                        errorMessage = loginResult.message
                        _snackbarEvent.trySend(errorMessage)
                    }
                    else -> {}
                }
            }
            response
        }
        .build()

    private suspend fun executeLogin(nim: String, pwd: String): LoginResult {
        try {
            val reqPayload = Request.Builder().url("https://lms.unindra.ac.id/login_new")
                .header("Skip-Interceptor", "true").get().build()
            val htmlPayload = Jsoup.parse(webClient.newCall(reqPayload).execute().body.string())

            val tCsrf = htmlPayload.select("input[name=csrf_token]").`val`() ?: ""

            var randomName = ""; var randomValue = ""
            for (input in htmlPayload.select("input[type=hidden]")) {
                val n = input.attr("name")
                if (n.length == 32) {
                    randomName = n; randomValue = input.`val`(); break
                }
            }
            val reqCaptcha = Request.Builder().url("https://lms.unindra.ac.id/kapca")
                .header("Skip-Interceptor", "true").get().build()
            val bytes = webClient.newCall(reqCaptcha).execute().body.bytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return LoginResult.Error("Gagal decode gambar")

            val aiAnswer = solveCaptcha(bitmap)
            if (aiAnswer.isEmpty()) return LoginResult.WrongCaptcha

            val formLogin = FormBody.Builder()
                .add("csrf_token", tCsrf).add(randomName, randomValue)
                .add("username", nim).add("pswd", pwd).add("kapca", aiAnswer)
                .build()

            val reqLogin = Request.Builder().url("https://lms.unindra.ac.id/login_new")
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

    fun checkLoginStatus() {
        if (prefer.getString("nim", "").isNullOrEmpty()) return

        isAutoLoginLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = silentLogin()) {
                is LoginResult.Success -> {
                    getInitData(result.html)
                    isLogin = true
                }
                is LoginResult.WrongPassword -> {
                    prefer.edit { remove("nim").remove("pwd") }
                    errorMessage = "Password berubah. Silakan login ulang."
                    _snackbarEvent.trySend(errorMessage)
                }
                is LoginResult.Error -> {
                    errorMessage = result.message
                    _snackbarEvent.trySend(errorMessage)
                }
                else -> {}
            }
        }
    }

    fun manualLogin(nim: String, pwd: String) {
        if (nim == "wowo " && pwd == "nyawit ") {
            viewModelScope.launch {
                isLoading = true
                delay(1000)

                studentProfileUI = StudentProfile(
                    studentName = "Prabowo-chan",
                    npm = "1234567890",
                    studyProgram = "Teknik Yapping S1",
                    classCode = "Z2X",
                    studentPhoto = ""
                )

                lectureCourseUI = listOf(
                    LectureCourse(
                        courseCode = "AK47",
                        courseName = "My Bini Guweh (MBG)",
                        day = "Senin",
                        clock = "00:00 - 23:59",
                        room = "Planet Namek",
                        lecturerName = "Raiden Shotgun",
                        lecturerHp = "08123456789",
                        lecturerPhoto = "",
                        "",
                        meetingList = List(10) {"apa"}
                    ),
                    LectureCourse(
                        courseCode = "WW3",
                        courseName = "19Jt Lapangan Sawit",
                        day = "Rabu",
                        clock = "13:00 - 15:30",
                        room = "AK Enfield",
                        lecturerName = "Mas Gibran",
                        lecturerHp = "08987654321",
                        lecturerPhoto = "",
                        "",
                        meetingList = listOf("link1", "link2")
                    ),
                    LectureCourse(
                        courseCode = "TIF-303",
                        courseName = "Metodologi Copy-Paste Prompt",
                        day = "Setiap Hari",
                        clock = "23:59 - 00:00",
                        room = "Warnet Terdekat",
                        lecturerName = "Lord ChatGPT, S.AI",
                        lecturerHp = "Chat Tidak Dibaca",
                        lecturerPhoto = "",
                        "",
                        meetingList = listOf()
                    ),
                    LectureCourse(
                        courseCode = "FF99",
                        courseName = "Manajemen Kemarahan Publik",
                        day = "Selasa",
                        clock = "19:00 - 21:00",
                        room = "Gedung DPR",
                        lecturerName = "Lord Rangga",
                        lecturerHp = "085566778899",
                        lecturerPhoto = "",
                        "",
                        meetingList = listOf("link_tatanan_dunia", "link_empire")
                    )
                )
                isDemo = true
                isLogin = true
                isLoading = false
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            errorMessage = ""

            for (attempt in 1..3) {
                when (val result = executeLogin(nim, pwd)) {
                    is LoginResult.Success -> {
                        prefer.edit { putString("nim", nim).putString("pwd", pwd) }
                        getInitData(result.html)
                        isLogin = true
                        break
                    }
                    is LoginResult.WrongPassword -> {
                        errorMessage = "NIM atau Password salah!"
                        _snackbarEvent.trySend(errorMessage)
                        break
                    }
                    is LoginResult.WrongCaptcha -> {
                        if (attempt == 3) {
                            errorMessage = "Gagal login. AI meleset membaca Captcha 3 kali."
                            _snackbarEvent.trySend(errorMessage)
                        }
                    }
                    is LoginResult.Error -> {
                        errorMessage = result.message
                        _snackbarEvent.trySend(errorMessage)
                        break
                    }
                }
            }
        }
    }

    fun logout() {
        prefer.edit { remove("nim").remove("pwd") }
        isAutoLoginLoading = false
        isLoading = false
        isLogin = false
        isDemo = false
    }

    fun refreshDashboard() {
        if (isDemo) {
            viewModelScope.launch {
                isRefreshing = true
                delay(1000)
                isRefreshing = false
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            errorMessage = ""

            when (val result = silentLogin()) {
                is LoginResult.Success -> { getInitData(result.html) }
                is LoginResult.WrongPassword -> {
                    prefer.edit { remove("nim").remove("pwd") }
                    errorMessage = "Password berubah. Silakan login ulang."
                    _snackbarEvent.trySend(errorMessage)
                    isLogin = false
                }
                is LoginResult.Error -> {
                    if (lectureCourseUI.isEmpty()) errorMessage = result.message
                    else _snackbarEvent.trySend(result.message)
                }
                else -> {}
            }

            isRefreshing = false
        }
    }

    private suspend fun silentLogin(): LoginResult {
        val savedNim = prefer.getString("nim", "") ?: ""
        val savedPwd = prefer.getString("pwd", "") ?: ""

        repeat(3) {
            when (val result = executeLogin(savedNim, savedPwd)) {
                is LoginResult.Success -> return result
                is LoginResult.WrongCaptcha -> {} // retry
                else -> return result
            }
        }
        return LoginResult.Error("Gagal login setelah 3x percobaan captcha")
    }

    private suspend fun solveCaptcha(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val cleanText = visionText.text.replace(" ", "")
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

    private fun getInitData(htmlDashboard: String) {
        val document = Jsoup.parse(htmlDashboard)
        try {
            val rawName = document.selectFirst("div.pull-left.info p")?.text() ?: ""
            val studentName = rawName.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            val npm = document.selectFirst("li.user-body strong")?.text() ?: ""
            val studyProgram = document.selectFirst("span.Badge-info")?.text() ?: ""
            val classCode = document.selectFirst("span.pull-right.text-bold.badge")?.text() ?: ""
            val studentPhoto = "https://lms.unindra.ac.id/lms_publik/images/users/thumbs/$npm.png"

            studentProfileUI = StudentProfile(studentName, npm, studyProgram, classCode, studentPhoto)

            if (document.select("div.box-widget").isEmpty()) return

            val meetingDict = mutableMapOf<String, List<String>>()
            for (tree in document.select("li.treeview")) {
                val titleSidebar = tree.selectFirst("span")?.text() ?: continue
                val parts = titleSidebar.split(" ")

                val scheduleKey = "${parts[0]} ${parts[1]}"

                val meetingList = mutableListOf<String>()
                for (li in tree.select("ul li")) {
                    val link = li.selectFirst("a")?.attr("href") ?: ""
                    meetingList.add(link)
                }
                meetingDict[scheduleKey] = meetingList
            }

            val scrapeClass = mutableListOf<LectureCourse>()
            for (element in document.select("div.box-widget")) {
                val rawLecturer = element.selectFirst("h3.widget-user-username")?.text() ?: ""
                val lecturerName = rawLecturer.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                val lecturerHp = element.selectFirst("h5.widget-user-desc")?.text()?.replace("HP :", "")?.trim() ?: ""
                val lecturerPhoto = element.selectFirst("img")?.attr("src") ?: ""

                val rawCourse = element.selectFirst("span.header_badeg")?.text() ?: ""
                val splitCourse = rawCourse.split(" -")
                val courseCode = splitCourse[0]
                val courseName = when (splitCourse[1]) {
                    "Arsitektur dan Organisasi Komput" -> "Arsitektur dan Organisasi Komputer"
                    else -> splitCourse[1].trimEnd(' ', '*', '#', ')')
                }

                val rawDetail = element.selectFirst("span.text-green")?.text() ?: ""
                val detailParts = rawDetail.split("|").map { it.trim() }
                val room = detailParts[1].replace("Ruang: ", "")
                val rawTime = detailParts[2].replace("Waktu: ", "").split(", ")
                val day = rawTime[0]
                val clock = rawTime[1]

                val meetingList = meetingDict["$day $clock"] ?: emptyList()

                scrapeClass.add(LectureCourse(courseCode, courseName, day, clock, room, lecturerName, lecturerHp, lecturerPhoto, "", meetingList))
            }

            val req = Request.Builder().url("https://lms.unindra.ac.id/presensi").get().build()
            val res = webClient.newCall(req).execute()
            val html = res.body.string()
            val parser = Jsoup.parse(html)

            val payload = parser.select("td[onclick*=rps_mhs]")
            nimCachePresence = payload.attr("onclick").split("'")[3]

            val presensiMap = mutableMapOf<String, String>()

            // Ambil semua baris di dalam tbody tabel
            val rows = parser.select("table.table-striped tbody tr")

            rows.forEach { row ->
                val cols = row.select("td")

                // Memastikan baris punya format kolom yang benar
                if (cols.size >= 10) {
                    val courseCode = cols[1].text().trim()

                    // Ambil persentase dari kolom Hadir (index 9)
                    val rawPersen = cols[9].selectFirst("span.badge")?.text()?.trim() ?: "0%"
                    // Cek jika teksnya cuma "%" atau kosong, ubah jadi "0%"
                    val persenHadir = if (rawPersen == "%" || rawPersen.isEmpty()) "0%" else rawPersen
                    presensiMap[courseCode] = persenHadir

                    // Ekstrak cachePresence dan nimCachePresence
                    val onClickAttr = cols[2].attr("onclick")
                    if (onClickAttr.contains("rps_mhs")) {
                        val parts = onClickAttr.split("'")
                        if (parts.size >= 4) {
                            cachePresence.add(parts[1]) // kdJdw
                        }
                    }
                }
            }

            // Langsung mapping persentase ke list scrapeClass
            // Catatan: Pastikan di data class LectureCourse sudah ada parameter untuk menampung ini
            // (misalnya bernama `persenHadir`), lalu kita gunakan fungsi `.copy()`
            lectureCourseUI = scrapeClass.map { course ->
                val absen = presensiMap[course.courseCode] ?: "0%"
                course.copy(persen = absen)
            }

        } catch (e: Exception) {
            errorMessage = e.message ?: "Gagal memuat dashboard"
        }
    }

    fun executePresence(urlPertemuan: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reqPage = Request.Builder().url(urlPertemuan).get().build()
                val resPage = webClient.newCall(reqPage).execute()
                val htmlPage = resPage.body.string()

                val document = Jsoup.parse(htmlPage)
                val downloadLink = document.selectFirst("a[href*=force_download]")?.attr("href")

                if (downloadLink != null) {
                    val reqDownload = Request.Builder().url(downloadLink).get().build()
                    val resDownload = webClient.newCall(reqDownload).execute()
                    resDownload.close()
                }
            } catch (e: Exception) {
                println("❌ Error saat mencoba absen siluman: ${e.message}")
            }
        }
    }

    fun getMeetingDetail(meetingUrl: String, isSwipe: Boolean = false) {
        if (isDemo) {
            viewModelScope.launch {
                if (isSwipe) isRefreshing = true
                else {
                    isLoading = true
                    meetingDetailUI = emptyList()
                }
                delay(1000)

                meetingDetailUI = when (meetingUrl) {
                    "link1" -> listOf(MeetingDetail("a","Slide_Pertemuan_1.pdf", "dummy_url"))
                    "link2" -> listOf(MeetingDetail("a","Slide_Pertemuan_2.pdf", "dummy_url"))
                    "link_ctrl_c" -> listOf(
                        MeetingDetail("a","Cheatsheet_Prompt_AI.txt", "dummy_url"),
                        MeetingDetail("a","Trik_Bypass_Plagiarisme.pdf", "dummy_url")
                    )
                    "link_tatanan_dunia" -> listOf(MeetingDetail("a","Dokumen_Deklarasi_Sunda_Empire.pdf", "dummy_url"))
                    "link_empire" -> listOf(MeetingDetail("a","Peta_Wilayah_Kekuasaan_Sunda_Empire.pdf", "dummy_url"))
                    else -> listOf(
                        MeetingDetail("a","Materi_Random_Copas_Dari_Google.pdf", "dummy_url"),
                        MeetingDetail("a","Presentasi_Hasil_Joki.pptx", "dummy_url")
                    )
                }
                isRefreshing = false
                isLoading = false
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (isSwipe) isRefreshing = true
            else {
                isLoading = true
                meetingDetailUI = emptyList()
            }

            errorMessage = ""
            try {
                val req = Request.Builder().url(meetingUrl).get().build()
                val res = webClient.newCall(req).execute()
                val html = res.body.string()
                val document = Jsoup.parse(html)
                val rows = document.select("tbody tr")
                val urlRegex = """(https?://[^\s'"]+)""".toRegex()

                val results = mutableListOf<MeetingDetail>()

                for (row in rows) {
                    val divs = row.select("div.col-md-4")
                    if (divs.isEmpty()) continue

                    val div1 = divs.getOrNull(0)
                    val div2 = divs.getOrNull(1)

                    val fullClass = div1?.selectFirst("a i")?.className() ?: ""
                    val jenis = fullClass.split(" ").find { it.startsWith("fa-") } ?: "fa-file-o"

                    val rawHtmlDiv1 = div1?.html() ?: ""
                    var link = urlRegex.find(rawHtmlDiv1)?.value ?: "Link tidak ditemukan"

                    if (link.contains("member_url")) {
                        val requ = Request.Builder().url(link).get().build()
                        val resp = webClient.newCall(requ).execute()
                        val realLink = resp.body.string()

                        if (realLink.isNotEmpty()) {
                            link = realLink
                        }
                    }

                    var deskripsi = div2?.text()?.trim()?.substringBeforeLast(".") ?: ""
                    if (deskripsi.isEmpty()) {
                        deskripsi = div1?.text()?.trim() ?: "Tidak ada deskripsi"
                    }

                    results.add(MeetingDetail(jenis, deskripsi, link))
                }

                meetingDetailUI = results
            } catch (e: Exception) {
                println(e.message)
                if (meetingDetailUI.isEmpty()) errorMessage = e.message ?: "Gagal memuat detail pertemuan"
                else _snackbarEvent.trySend(e.message ?: "Gagal memuat detail pertemuan")

            }
            isRefreshing = false
            isLoading = false
        }
    }

    fun executeDownload(context: Context, fileUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "download_channel"
            val notifId = fileUrl.hashCode()

            val channel = NotificationChannel(channelId, "Download Materi", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)

            val notifBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Menyiapkan Download...")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, 0, true)

            notificationManager.notify(notifId, notifBuilder.build())

            try {
                val req = Request.Builder().url(fileUrl).get().build()
                val res = webClient.newCall(req).execute()

                val fileName = res.request.url.toString().substringAfterLast("/")

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LMS")
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        val inputStream = res.body.byteStream()
                        val totalBytes = res.body.contentLength()

                        val buffer = ByteArray(8 * 1024)
                        var downloadedBytes = 0L
                        var bytesRead = inputStream.read(buffer)

                        var lastUpdateTime = System.currentTimeMillis()

                        notifBuilder.setContentTitle(fileName)

                        while (bytesRead != -1) {
                            outputStream?.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (totalBytes > 0L && currentTime - lastUpdateTime > 500) {

                                val progress = (downloadedBytes * 100 / totalBytes).toInt()

                                notifBuilder.setProgress(100, progress, false)
                                    .setContentText("Selesai $progress%")

                                notificationManager.notify(notifId, notifBuilder.build())
                                lastUpdateTime = currentTime
                            }
                            bytesRead = inputStream.read(buffer)
                        }
                    }
                }

                notifBuilder.setContentText("Download Selesai!")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)

                notificationManager.notify(notifId, notifBuilder.build())

            } catch (e: Exception) {
                notifBuilder.setContentTitle("Download Gagal")
                    .setContentText(e.message)
                    .setProgress(0, 0, false)
                    .setOngoing(false)

                notificationManager.notify(notifId, notifBuilder.build())
            }
        }
    }

    fun getPresence(courseIndex: Int, isSwipe: Boolean = false) {
        if (isDemo) {
            viewModelScope.launch {
                if (isSwipe) isRefreshing = true
                else {
                    isLoading = true
                    presenceDetailUI = emptyList()
                }
                delay(1000)

                when (courseIndex) {
                    0 -> presenceDetailUI = listOf("link1", "link2", "link3", "dump", "", "dump", "", "dump")
                    1 -> presenceDetailUI = List(8) { "dump" }
                }
                isRefreshing = false
                isLoading = false
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (isSwipe) isRefreshing = true
            else {
                isLoading = true
                presenceDetailUI = emptyList()
            }
            errorMessage = ""
            try {
                val kdJdw = cachePresence[courseIndex]

                val payload = FormBody.Builder().add("kd_jdw", kdJdw).add("nim", nimCachePresence).build()
                val req = Request.Builder().url("https://lms.unindra.ac.id/presensi/rekap_presensi_mhs").post(payload).build()
                val res = webClient.newCall(req).execute()
                val html = res.body.string()
                val parser = Jsoup.parse(html)

                val barisMahasiswa = parser.selectFirst("table.table-bordered tbody tr")

                if (barisMahasiswa != null) {
                    val semuaKolom = barisMahasiswa.select("td")
                    val jumlahKolom = semuaKolom.size

                    val listStatusHadir = mutableListOf<Boolean>()
                    val indexPertemuanTerakhir = jumlahKolom - 2

                    for (i in 3..indexPertemuanTerakhir) {
                        val iconHadir = semuaKolom[i].selectFirst("i.fa-calendar-check-o")
                        listStatusHadir.add(iconHadir != null)
                    }

                    val tempPresenceList = mutableListOf<String>()
                    listStatusHadir.forEachIndexed { index, isHadir ->
                        var presenceUrl = ""
                        if (!isHadir) {
                            val meetingUrl = lectureCourseUI[courseIndex].meetingList[index]
                            val meetReq = Request.Builder().url(meetingUrl).get().build()
                            val meetRes = webClient.newCall(meetReq).execute()
                            val meetHtml = meetRes.body.string()
                            val meetParser = Jsoup.parse(meetHtml)
                            presenceUrl = meetParser.selectFirst("a[href*=force_download]")?.attr("href") ?: ""
                        }
                        tempPresenceList.add(presenceUrl)
                    }
                    presenceDetailUI = tempPresenceList
                }
            } catch (e: Exception) {
                if (presenceDetailUI.isEmpty()) errorMessage = e.message ?: "Gagal memuat data presensi"
                else _snackbarEvent.trySend(e.message ?: "Gagal memuat data presensi")
            }
            isRefreshing = false
            isLoading = false
        }
    }

    fun getAllTask(courseIndex: Int, isSwipe: Boolean = false) {
        if (isDemo) {
            viewModelScope.launch {
                if (isSwipe) isRefreshing = true
                else {
                    isLoading = true
                    taskDetailUI = emptyMap()
                }
                delay(1000)

                when (courseIndex) {
                    0 -> {
                        taskDetailUI = mapOf(
                            3 to TaskDetail(
                                taskUrl = "link_rekening_orang_dalam",
                                message = "Proyek Pengadaan Gorden Rumah Dinas: Anggaran 22 Miliar. Spesifikasi: Harus bisa menghalau sinar matahari dan nyinyiran netizen.",
                                taskFile = "rab_fiktif_final_v2.xlsx",
                                deadline = "10 Okt 2026 23:59:00",
                                viewUrl = "",
                                status = "active"
                            ),
                            4 to TaskDetail(
                                taskUrl = "dummy",
                                message = "Simulasi Menghadapi Hacker: Jika data bocor, cukup ganti password menjadi 'admin123' atau salahkan faktor cuaca.",
                                taskFile = "sop_ngeles_nasional.pdf",
                                deadline = "15 Okt 2026 10:00:00",
                                viewUrl = "link_data_dijual_di_breachforums",
                                status = "submitted"
                            ),
                            6 to TaskDetail(
                                taskUrl = "link_pendaftaran_anak_emas",
                                message = "Workshop Optimalisasi 'Orang Dalam': Cara cepat naik jabatan tanpa perlu kompetensi, cukup modal koneksi dan hobi main golf.",
                                taskFile = "",
                                deadline = "30 Okt 2026 15:00:00",
                                viewUrl = "",
                                status = "active"
                            )
                        )
                    }
                }
                isRefreshing = false
                isLoading = false
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (isSwipe) isRefreshing = true
            else {
                isLoading = true
                taskDetailUI = emptyMap()
            }
            errorMessage = ""

            try {
                val temp = mutableMapOf<Int, TaskDetail>()
                lectureCourseUI[courseIndex].meetingList.forEachIndexed { index, meetingUrl ->
                    val req = Request.Builder().url(meetingUrl).get().build()
                    val res = webClient.newCall(req).execute()
                    val html = res.body.string()
                    val parser = Jsoup.parse(html)

                    val taskUrl = parser.selectFirst("a[href*=member_tugas]")?.attr("href")
                        ?: return@forEachIndexed

                    val taskReq = Request.Builder().url(taskUrl).get().build()
                    val taskRes = webClient.newCall(taskReq).execute()
                    val taskHtml = taskRes.body.string()
                    val taskParser = Jsoup.parse(taskHtml)

                    val message = taskParser.selectFirst("div[style*=padding-left]")?.text()?.trim() ?: ""

                    val docx = taskParser.selectFirst("a[href*=force_download]")?.attr("href")

                    val pdf = taskParser.selectFirst("div.callout-white-default")
                        ?.selectFirst("a[onclick*=lihat_pdf]")?.attr("onclick")
                        ?.substringAfter("'")?.substringBefore("'")
                    val pdfUrl = "https://lms.unindra.ac.id/media_public/lihat_pdf/$pdf"

                    val pict = taskParser.selectFirst("a[onclick*=lihat_gambar]")?.attr("onclick")
                        ?.substringAfter("'")?.substringBefore("'")
                    val pictUrl = "https://lms.unindra.ac.id/media_public/lihat_gambar/$pict"

                    val taskFile = if (pdf != null) pdfUrl else if (pict != null) pictUrl else docx ?: ""

                    val table = taskParser.select("table.table-bordered tr")
                    var deadline = String()
                    for (element in table) {
                        val header = element.selectFirst("th")?.text()
                        if (header != "Akhir Submit") continue
                        deadline = element.selectFirst("td")?.text() ?: ""
                        break
                    }

                    val element = taskParser.selectFirst("div.callout-white-warning")
                        ?.selectFirst("a[onclick*=lihat_pdf]")?.attr("onclick")
                        ?.substringAfter("'")?.substringBefore("'")
                    val viewUrl = if (element != null) "https://lms.unindra.ac.id/media_public/lihat_pdf/$element" else ""

                    val status = when {
                        taskHtml.contains("Sudah Submit") -> "submitted"
                        taskHtml.contains("Waktu Submit sudah berakhir") -> "expired"
                        else -> "active"
                    }

                    temp[index] = TaskDetail(taskUrl, message, taskFile, deadline, viewUrl, status)
                }
                taskDetailUI = temp
            } catch (e: Exception) {
                if (taskDetailUI.isEmpty()) errorMessage = e.message ?: "Gagal memuat data tugas"
                else _snackbarEvent.trySend(e.message ?: "Gagal memuat data tugas")
            }
            isRefreshing = false
            isLoading = false
        }
    }

    fun submitTask(context: Context, uri: Uri, taskUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            isUploading = true
            uploadProgress = 0f
            uploadFileName = "Menyiapkan file..."

            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            
            var fileName = "Tugas"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "upload_channel"
            var notifId = fileName.hashCode()
            var notifBuilder: NotificationCompat.Builder? = null
            
            try {
                if (cursor == null || !cursor.moveToFirst() || cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) < 0 ||cursor.getColumnIndex(OpenableColumns.SIZE) < 0) {
                    _snackbarEvent.trySend("gagal mendapatkan informasi file")
                    return@launch
                }

                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                fileName = cursor.getString(nameIndex)
                uploadFileName = fileName
                notifId = fileName.hashCode()
                
                val channel = NotificationChannel(channelId, "Upload Materi", NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)

                notifBuilder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setContentTitle("Mengunggah Tugas...")
                    .setContentText(fileName)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, 0, true)

                notificationManager.notify(notifId, notifBuilder.build())

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val fileSize = cursor.getLong(sizeIndex)
                if (fileSize > 20 * 1024 * 1024) {
                    _snackbarEvent.trySend("ukuran file melebihi 20MB")
                    
                    notifBuilder.setContentTitle("Upload Gagal")
                        .setContentText("Ukuran file melebihi 20MB")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                    notificationManager.notify(notifId, notifBuilder.build())
                    
                    return@launch
                }

                val mimeType = contentResolver.getType(uri)!!
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

                val uriRequestBody = object : RequestBody() {
                    override fun contentType() = null

                    override fun contentLength() = fileSize

                    override fun writeTo(sink: BufferedSink) {
                        contentResolver.openInputStream(uri)!!.use { inputStream ->
                            val buffer = ByteArray(8 * 1024)
                            var uploadedBytes = 0L
                            var bytesRead = inputStream.read(buffer)
                            var lastUpdateTime = System.currentTimeMillis()

                            while (bytesRead != -1) {
                                sink.write(buffer, 0, bytesRead)
                                uploadedBytes += bytesRead

                                val currentTime = System.currentTimeMillis()
                                if (fileSize > 0L && currentTime - lastUpdateTime > 500) {
                                    val progress = (uploadedBytes * 100 / fileSize).toInt()

                                    notifBuilder.setProgress(100, progress, false)
                                        .setContentText("Mengunggah $progress%")

                                    notificationManager.notify(notifId, notifBuilder.build())
                                    
                                    uploadProgress = progress / 100f
                                    lastUpdateTime = currentTime
                                }
                                bytesRead = inputStream.read(buffer)
                            }
                        }
                    }
                }

                val reqForm = Request.Builder().url(taskUrl).get().build()
                val resForm = webClient.newCall(reqForm).execute()
                val htmlForm = resForm.body.string()
                val parserForm = Jsoup.parse(htmlForm)

                val idTugas = parserForm.selectFirst("input[name=h_id_tugas]")?.attr("value")
                val hKode = parserForm.selectFirst("input[name=h_kode]")?.attr("value")
                val idAktifitas = parserForm.selectFirst("input[name=h_id_aktifitas]")?.attr("value")
                if (idTugas == null || hKode == null || idAktifitas == null) {
                    _snackbarEvent.trySend("payload tidak ditemukan")
                    
                    notifBuilder.setContentTitle("Upload Gagal")
                        .setContentText("Payload tidak ditemukan")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                    notificationManager.notify(notifId, notifBuilder.build())
                    
                    return@launch
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("h_id_tugas", idTugas)
                    .addFormDataPart("h_kode", hKode)
                    .addFormDataPart("h_id_aktifitas", idAktifitas)
                    .addFormDataPart("myfile", "upload.$extension", uriRequestBody)
                    .build()

                val uploadReq = Request.Builder()
                    .url("https://lms.unindra.ac.id/member_tugas/mhs_upload_file_proses")
                    .post(requestBody)
                    .build()

                val uploadRes = webClient.newCall(uploadReq).execute()

                val balasanServer = uploadRes.body.string()
                println("Status Upload: $balasanServer")
                
                _snackbarEvent.trySend("Tugas Berhasil Dikumpulkan!")
                
                uploadProgress = 1f
                notifBuilder.setContentTitle("Upload Selesai!")
                    .setContentText(fileName)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)

                notificationManager.notify(notifId, notifBuilder.build())

            } catch (e: Exception) {
                errorMessage = e.message ?: "Gagal upload file"
                _snackbarEvent.trySend(errorMessage)
                
                notifBuilder?.setContentTitle("Upload Gagal")
                    ?.setContentText(e.message)
                    ?.setProgress(0, 0, false)
                    ?.setOngoing(false)
                
                if (notifBuilder != null) {
                    notificationManager.notify(notifId, notifBuilder.build())
                }
            } finally {
                cursor?.close()
                isLoading = false
                isUploading = false
            }

        }
    }
}

sealed class LoginResult {
    data class Success(val html: String) : LoginResult()
    object WrongPassword : LoginResult()
    object WrongCaptcha : LoginResult()
    data class Error(val message: String) : LoginResult()
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
    val persen: String, val meetingList: List<String>
)
data class MeetingDetail(val type: String, val desc: String, val url: String)
data class TaskDetail(
    val taskUrl: String,
    val message: String,
    val taskFile: String,
    val deadline: String,
    val viewUrl: String,
    val status: String
)