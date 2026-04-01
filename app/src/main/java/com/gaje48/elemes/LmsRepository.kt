package com.gaje48.elemes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.jsoup.nodes.Element
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.jsoup.Jsoup

class Repository {
    lateinit var onWrongPassword: (() -> Unit)
    lateinit var onError: ((String) -> Unit)
    lateinit var credentialsProvider: (() -> Pair<String, String>)

    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.clear(); cookieStore.addAll(cookies)
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore
    }

    private val loginClient = OkHttpClient.Builder().cookieJar(cookieJar).build()
    private val webClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val request = chain.request()
            val originalResponse = chain.proceed(request)
            val html = originalResponse.peekBody(70).string()

            if (!request.url.toString().contains("login_new") && html.contains("Login | LMS UNINDRA")) {
                originalResponse.close()

                when (val result = runBlocking { silentLogin() }) {
                    is LoginResult.Success -> {
                        return@addInterceptor chain.proceed(request)
                    }
                    is LoginResult.WrongPassword -> {
                        onWrongPassword.invoke()
                        error("Autologin gagal: Password salah")
                    }
                    is LoginResult.WrongCaptcha -> {
                        onError.invoke("Gagal login setelah 3x percobaan captcha")
                        error("Autologin gagal: Limit captcha tercapai")

                    }
                    is LoginResult.Error -> {
                        onError.invoke(result.message)
                        error("Autologin error: ${result.message}")
                    }
                }

            }
            originalResponse
        }
        .build()

    suspend fun silentLogin(): LoginResult {
        val (nim, pwd) = credentialsProvider.invoke()
        repeat(3) {
            when (val result = executeLogin(nim, pwd)) {
                is LoginResult.Success -> return LoginResult.Success(result.html)
                is LoginResult.WrongCaptcha -> {}
                else -> return result
            }
        }
        return LoginResult.WrongCaptcha
    }

    suspend fun executeLogin(nim: String, pwd: String): LoginResult {
        return try {
            val htmlPayload = Jsoup.parse(
                loginClient.newCall(Request.Builder().url("https://lms.unindra.ac.id/login_new").get().build())
                    .execute().use { it.body.string() }
            )

            val tCsrf = htmlPayload.selectFirst("input[name=csrf_token]")!!.`val`()

            lateinit var randomName: String
            lateinit var randomValue: String
            for (input in htmlPayload.select("input[type=hidden]")) {
                val n = input.attr("name")
                if (n.length == 32) { randomName = n; randomValue = input.`val`(); break }
            }

            val bitmap = loginClient.newCall(Request.Builder().url("https://lms.unindra.ac.id/kapca").get().build())
                .execute().use { response ->
                    response.body.byteStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }

            val aiAnswer = solveCaptcha(bitmap) ?: error("captcha gagal dipecahkan")

            val formLogin = FormBody.Builder()
                .add("csrf_token", tCsrf).add(randomName, randomValue)
                .add("username", nim).add("pswd", pwd).add("kapca", aiAnswer)
                .build()
            val htmlLogin = loginClient.newCall(
                Request.Builder().url("https://lms.unindra.ac.id/login_new").post(formLogin).build()
            ).execute().use { it.body.string() }

            when {
                htmlLogin.contains("<title>Member") -> LoginResult.Success(htmlLogin)
                htmlLogin.contains("Username atau password salah") -> LoginResult.WrongPassword
                htmlLogin.contains("Jawaban Captcha Salah") -> LoginResult.WrongCaptcha
                else -> LoginResult.Error("Gangguan server Unindra.")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Error jaringan tidak diketahui")
        }
    }

    private suspend fun solveCaptcha(bitmap: Bitmap): String? = try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

        val cleanText = visionText.text.replace(" ", "")

        val mathMatch = Regex("(\\d+)\\+(\\d+)").find(cleanText) ?: return null

        val (num1, num2) = mathMatch.destructured
        (num1.toInt() + num2.toInt()).toString()
    } catch (_: Exception) {
        null
    }

    fun fetchInitData(dashboardHtml: String): InitDataResult {
        val dashboardParser = Jsoup.parse(dashboardHtml)

        val presenceHtml = webClient.newCall(
            Request.Builder().url("https://lms.unindra.ac.id/presensi").get().build()
        ).execute().use { it.body.string() }
        val presenceParser = Jsoup.parse(presenceHtml)

        val rawName = dashboardParser.selectFirst("div.pull-left.info p")!!.text()
        val studentName = rawName.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val npm = dashboardParser.selectFirst("li.user-body strong")!!.text()
        val studentInfo = StudentInfo(
            studentName = studentName,
            npm = npm,
            nimEncode = presenceParser.selectFirst("td[onclick*=absensi_mhs]")!!
                .attr("onclick").split("'")[3],
            studyProgram = dashboardParser.selectFirst("span.Badge-info")!!.text(),
            classCode = dashboardParser.selectFirst("span.pull-right.text-bold.badge")!!.text(),
            studentPhoto = "https://lms.unindra.ac.id/lms_publik/images/users/thumbs/$npm.png"
        )

        if (dashboardParser.select("div.box-widget").isEmpty()) {
            return InitDataResult(studentInfo, emptyList())
        }

        val presensiMap = mutableMapOf<String, PresenceInfo>()

        presenceParser.select("table.table-striped tbody tr").forEach { row ->
            val cols = row.select("td")

            val courseCode = cols[1].text().trim()

            val jumlahPertemuan = cols[8].ownText().trim().substringBefore("/").toInt()

            val rawPersen = cols[9].selectFirst("span.badge")!!.text()
            val finalPersenInt = rawPersen.replace("%", "").toIntOrNull() ?: 0

            val presenceCode = cols[2].attr("onclick").split("'")[1]

            presensiMap[courseCode] = PresenceInfo(jumlahPertemuan, finalPersenInt, presenceCode)
        }

        val meetingDict = dashboardParser.select("li.treeview").associate { tree ->
            val parts = tree.selectFirst("span")!!.text().split(" ")

            val meetings = mutableMapOf<Int, String>()
            tree.select("ul li a").forEach { aTag ->
                val title = aTag.text()
                val url = aTag.attr("href")

                val meetingNumber = Regex("(\\d+)").find(title)!!.value.toInt()

                meetings[meetingNumber - 1] = url
            }

            "${parts[0]} ${parts[1]}" to meetings
        }

        val courses = dashboardParser.select("div.box-widget").map { el ->
            val rawLecturer = el.selectFirst("h3.widget-user-username")!!.text()
            val lecturerName = rawLecturer.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

            val rawCourse = el.selectFirst("span.header_badeg")!!.text().split(" -")
            val courseCode = rawCourse[0]

            val courseName = when (val parsedName = rawCourse[1]) {
                "Arsitektur dan Organisasi Komput" -> "Arsitektur dan Organisasi Komputer"
                else -> parsedName.trimEnd(' ', '*', '#', ')')
            }

            val detailParts = el.selectFirst("span.text-green")!!.text().split("|").map { it.trim() }
            val rawTime = detailParts[2].replace("Waktu: ", "").split(", ")
            val day = rawTime[0]
            val clock = rawTime[1]

            CourseInfo(
                courseCode = courseCode,
                courseName = courseName,
                day = day,
                clock = clock,
                room = detailParts[1].replace("Ruang: ", ""),
                lecturerName = lecturerName,
                lecturerHp = el.selectFirst("h5.widget-user-desc")!!.text()
                    .replace("HP :", "").trim().ifEmpty { "Nomor HP tidak tersedia" },
                lecturerPhoto = el.selectFirst("img")!!.attr("src"),
                presenceInfo = presensiMap[courseCode]!!,
                meetingList = meetingDict["$day $clock"]!!
            )
        }

        return InitDataResult(studentInfo, courses)
    }

    fun fetchMeetingDetail(url: String): List<MeetingDetail> {
        val html = webClient.newCall(Request.Builder().url(url).get().build())
            .execute().use { it.body.string() }
        val document = Jsoup.parse(html)
        val urlRegex = """(https?://[^\s'"]+)""".toRegex()
        val results = mutableListOf<MeetingDetail>()

        for (row in document.select("tbody tr")) {
            val divs = row.select("div.col-md-4")
            val div1 = divs[0]
            val div2 = divs[1]

            val jenis = div1.selectFirst("a i")!!.className().split(" ")
                .find { it.startsWith("fa-") }!!

            val link = urlRegex.find(div1.html())!!.value
            val realLink = if (link.contains("member_url")) webClient.newCall(Request.Builder().url(link).get().build())
                .execute().use { it.body.string() }
            else link

            val deskripsi = div2.text().trim().substringBeforeLast(".")
                .ifEmpty { div1.text().trim() }

            results.add(MeetingDetail(jenis, deskripsi, realLink))
        }
        return results
    }

    suspend fun fetchPresence(nimEncode: String, kdJdwEncode: String, meetings: Map<Int, String>): List<StatusPresensi> {
        val payload = FormBody.Builder().add("kd_jdw", kdJdwEncode).add("nim", nimEncode).build()
        val html = webClient.newCall(
            Request.Builder().url("https://lms.unindra.ac.id/presensi/rekap_presensi_mhs").post(payload).build()
        ).execute().use { it.body.string() }
        val parser = Jsoup.parse(html)

        val barisMahasiswa = parser.selectFirst("table.table-bordered tbody tr") ?: return emptyList()

        val cols = barisMahasiswa.select("td")
        val listStatusHadir = (3..<cols.size - 1).map { i ->
            cols[i].selectFirst("i.fa-calendar-check-o") != null
        }

        println(listStatusHadir)

        return coroutineScope {
            listStatusHadir.mapIndexed { index, isHadir ->
                async(Dispatchers.IO) {
                    if (isHadir) return@async StatusPresensi.SudahHadir

                    val meetUrl = meetings[index] ?: return@async StatusPresensi.BelumHadirTanpaLink

                    val meetHtml = webClient.newCall(Request.Builder().url(meetUrl).get().build())
                        .execute().use { it.body.string() }
                    val link = Jsoup.parse(meetHtml).selectFirst("a[href*=force_download]")?.attr("href")
                        ?: return@async StatusPresensi.BelumHadirTanpaLink
                    StatusPresensi.BelumHadirAdaLink(link)
                }
            }.awaitAll()
        }
    }

    suspend fun fetchAllTask(meetings: Map<Int, String>): Map<Int, TaskDetail> = coroutineScope {

        fun extractFileUrl(container: Element): String {
            val pdfId = container.selectFirst("a[onclick*=lihat_pdf]")?.attr("onclick")
                ?.substringAfter("'", "")?.substringBefore("'")

            val pictId = container.selectFirst("a[onclick*=lihat_gambar]")?.attr("onclick")
                ?.substringAfter("'", "")?.substringBefore("'")

            val others = container.selectFirst("a[href*=force_download]")?.attr("href")

            return when {
                pdfId != null -> "https://lms.unindra.ac.id/media_public/lihat_pdf/$pdfId"
                pictId != null -> "https://lms.unindra.ac.id/media_public/lihat_gambar/$pictId"
                else -> others ?: ""
            }
        }

        meetings.toList().map { (index, meetingUrl) ->
            async(Dispatchers.IO) {
                runCatching {
                    val html = webClient.newCall(Request.Builder().url(meetingUrl).get().build())
                        .execute().use { it.body.string() }
                    val taskUrl = Jsoup.parse(html).selectFirst("a[href*=member_tugas]")?.attr("href")
                        ?: return@runCatching null

                    val taskHtml = webClient.newCall(Request.Builder().url(taskUrl).get().build())
                        .execute().use { it.body.string() }
                    val taskParser = Jsoup.parse(taskHtml)

                    val message = taskParser.selectFirst("div[style*=padding-left]")?.text() ?: ""

                    val taskFile = extractFileUrl(taskParser.selectFirst("div.callout-white-default")!!)

                    lateinit var deadline: String
                    for (row in taskParser.select("table.table-bordered tr")) {
                        if (row.selectFirst("th")!!.text() == "Akhir Submit")
                        { deadline = row.selectFirst("td")!!.text(); break }
                    }

                    val viewUrl = extractFileUrl(taskParser.selectFirst("div.callout-white-warning")!!)

                    val status: TaskStatus = when {
                        taskHtml.contains("Sudah Submit") -> TaskStatus.SUBMITTED
                        taskHtml.contains("Waktu Submit sudah berakhir") -> TaskStatus.EXPIRED
                        else -> TaskStatus.ACTIVE
                    }

                    index to TaskDetail(taskUrl, message, taskFile, deadline, viewUrl, status)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().toMap()
    }

    fun executePresence(fileUrl: String) {
        webClient.newCall(Request.Builder().url(fileUrl).get().build()).execute().close()
    }

    fun downloadFileRaw(fileUrl: String): Response = webClient.newCall(Request.Builder().url(fileUrl).get().build()).execute()

    fun uploadTask(
        context: Context,
        uri: Uri,
        taskUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit
    ): String {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        ) ?: error("Gagal mendapatkan informasi file")

        return cursor.use { c ->
            if (!c.moveToFirst()) error("Gagal membaca metadata file")
            val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex < 0 || sizeIndex < 0) error("Metadata file tidak valid")

            val fileName = c.getString(nameIndex)
            val fileSize = c.getLong(sizeIndex)
            if (fileSize > 20 * 1024 * 1024) error("Ukuran file melebihi 20MB")

            val mimeType = contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

            val uriBody = object : RequestBody() {
                override fun contentType() = null
                override fun contentLength() = fileSize
                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var uploaded = 0L
                        var read = input.read(buffer)
                        var lastUpdate = System.currentTimeMillis()
                        while (read != -1) {
                            sink.write(buffer, 0, read)
                            uploaded += read
                            val now = System.currentTimeMillis()
                            if (fileSize > 0 && now - lastUpdate > 500) {
                                onProgress(fileName, uploaded.toFloat() / fileSize)
                                lastUpdate = now
                            }
                            read = input.read(buffer)
                        }
                    } ?: error("file tidak valid")
                }
            }

            val htmlForm = webClient.newCall(Request.Builder().url(taskUrl).get().build())
                .execute().use { it.body.string() }
            val parserForm = Jsoup.parse(htmlForm)
            val idTugas = parserForm.selectFirst("input[name=h_id_tugas]")?.attr("value")
                ?: error("Payload form tidak ditemukan")
            val hKode = parserForm.selectFirst("input[name=h_kode]")!!.attr("value")
            val idAktifitas = parserForm.selectFirst("input[name=h_id_aktifitas]")!!.attr("value")

            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("h_id_tugas", idTugas)
                .addFormDataPart("h_kode", hKode)
                .addFormDataPart("h_id_aktifitas", idAktifitas)
                .addFormDataPart("myfile", "upload.$extension", uriBody)
                .build()

            webClient.newCall(
                Request.Builder()
                    .url("https://lms.unindra.ac.id/member_tugas/mhs_upload_file_proses")
                    .post(requestBody).build()
            ).execute().use { it.body.string() }
        }
    }
}
