package com.gaje48.lms.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gaje48.lms.model.AccountProblemException
import com.gaje48.lms.model.CourseInfo
import com.gaje48.lms.model.DashboardData
import com.gaje48.lms.model.MeetingContent
import com.gaje48.lms.model.SessionExpiredException
import com.gaje48.lms.model.StatusPresensi
import com.gaje48.lms.model.StudentInfo
import com.gaje48.lms.model.TaskInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.plugin
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.InputStream


class InternetDataSource(private val ioDispatcher: CoroutineDispatcher) {
    private val webClient = HttpClient(CIO) {
        install(HttpCookies)
        install(HttpRedirect) {
            allowHttpsDowngrade = true
        }
    }.apply {
        plugin(HttpSend).intercept { request ->
            val call = execute(request)

            if (!request.url.pathSegments.contains("login_new")
                && call.response.request.url.segments.contains("login")) {
                throw SessionExpiredException()
            }

            call
        }
    }

    suspend fun loginStatus(nim: String, pwd: String) {
        val loginPageHtml = webClient.get("https://lms.unindra.ac.id/login_new").bodyAsText()
        val htmlPayload = Jsoup.parse(loginPageHtml)

        val hiddenInputs = htmlPayload.select("input[type=hidden]")
        val tCsrf = hiddenInputs[0].attr("value")
        val randomInput = hiddenInputs[1]
        val randomName = randomInput.attr("name")
        val randomValue = randomInput.attr("value")

        repeat(3) { attempt ->
            val bytes = webClient.get("https://lms.unindra.ac.id/kapca").bodyAsBytes()

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            val aiAnswer = solveCaptcha(bitmap) ?: run {
                if (attempt == 2) error("Gagal membaca captcha setelah 3x percobaan")
                return@repeat
            }

            val response = webClient.submitForm(
                url = "https://lms.unindra.ac.id/login_new",
                formParameters = parameters {
                    append("csrf_token", tCsrf)
                    append(randomName, randomValue)
                    append("username", nim)
                    append("pswd", pwd)
                    append("kapca", aiAnswer)
                }
            )

            val location = response.headers["location"]
                ?: if (attempt == 2) error("Gagal login setelah menjawab captcha 3x") else return@repeat

            when {
                location.contains("member") -> return
                location.contains("login") -> throw AccountProblemException()
                else -> error("Ada yang salah, location: $location")
            }
        }
    }

    private suspend fun solveCaptcha(bitmap: Bitmap): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

        val cleanText = visionText.text.replace(" ", "")

        val mathMatch = Regex("(\\d+)\\+(\\d+)").find(cleanText) ?: return null

        val (num1, num2) = mathMatch.destructured
        return (num1.toInt() + num2.toInt()).toString()
    }

    suspend fun getDashboardData(): DashboardData = coroutineScope {
        val presences = async { getAllPresenceInfo() }

        val dashboardHtml = webClient.get("https://lms.unindra.ac.id/member").bodyAsText()
        val courses = async { parseAllCourseInfo(dashboardHtml) }
        val studentInfo = async { parseStudentInfo(dashboardHtml) }

        DashboardData(studentInfo.await(), courses.await(), presences.await())
    }

    private fun parseStudentInfo(dashboardHtml: String): StudentInfo {
        val dashboardParser = Jsoup.parse(dashboardHtml)

        val rawName = dashboardParser.selectFirst("div.pull-left.info p")!!.text()
        val studentName = rawName.lowercase().split(" ")
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val npm = dashboardParser.selectFirst("li.user-body strong")!!.text()

        return StudentInfo(
            studentName = studentName,
            npm = npm,
            studyProgram = dashboardParser.selectFirst("span.Badge-info")!!.text(),
            classCode = dashboardParser.selectFirst("span.pull-right.text-bold.badge")!!.text(),
            studentPhoto = "https://lms.unindra.ac.id/lms_publik/images/users/thumbs/$npm.png"
        )
    }

    private fun parseAllCourseInfo(dashboardHtml: String): List<CourseInfo> {
        val dashboardParser = Jsoup.parse(dashboardHtml)

        val meetingDict = dashboardParser.select("li.treeview").associate { tree ->
            val parts = tree.selectFirst("span")!!.text().split(" ")

            val meetings = tree.select("ul li a").associate { aTag ->
                val title = aTag.text()
                val url = aTag.attr("href")

                val meetingNumber = Regex("(\\d+)").find(title)!!.value.toInt()

                meetingNumber - 1 to url
            }

            "${parts[0]} ${parts[1]}" to meetings
        }

        return dashboardParser.select("div.box-widget").map { el ->
            val rawLecturer = el.selectFirst("h3.widget-user-username")!!.text()
            val lecturerName = rawLecturer.lowercase().split(" ")
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

            val rawCourse = el.selectFirst("span.header_badeg")!!.text().split(" -")
            val courseCode = rawCourse[0]

            val courseName = when (val parsedName = rawCourse[1]) {
                "Arsitektur dan Organisasi Komput" -> "Arsitektur dan Organisasi Komputer"
                else -> parsedName.trimEnd(' ', '*', '#', ')')
            }

            val detailParts = el.selectFirst("span.text-green")!!.text()
                .split("|").map { it.trim() }
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
                allMeeting = meetingDict["$day $clock"] ?: emptyMap()
            )
        }
    }

    suspend fun getAllPresenceInfo(): Map<String, List<Boolean>> = coroutineScope {
        val presenceHtml = webClient.get("https://lms.unindra.ac.id/presensi").bodyAsText()
        val presenceParser = Jsoup.parse(presenceHtml)

        val nimId = presenceParser.selectFirst("td[onclick*=absensi_mhs]")
            ?.attr("onclick")?.split("'")[3] ?: return@coroutineScope emptyMap()

        presenceParser.select("table.table-striped tbody tr").map { row ->
            async(ioDispatcher) {
                val colPresences = row.select("td")
                val courseCode = colPresences[1].text().trim()
                val kodeJadwalId = colPresences[2].attr("onclick").split("'")[1]

                val html = webClient.submitForm(
                    url = "https://lms.unindra.ac.id/presensi/rekap_presensi_mhs",
                    formParameters = parameters {
                        append("kd_jdw", kodeJadwalId)
                        append("nim", nimId)
                    }
                ).bodyAsText()

                val parser = Jsoup.parse(html)
                val barisMahasiswa = parser.selectFirst("table.table-bordered tbody tr")
                    ?: return@async courseCode to emptyList()

                val cols = barisMahasiswa.select("td")
                val listStatusHadir = (3..<cols.size - 1).map { i ->
                    cols[i].selectFirst("i.fa-calendar-check-o") != null
                }

                courseCode to listStatusHadir
            }
        }.awaitAll().toMap()
    }

    suspend fun getAllMeetingContent(meetingUrl: String): List<MeetingContent> {
        val html = webClient.get(meetingUrl).bodyAsText()
        val document = Jsoup.parse(html)

        val urlRegex = """(https?://[^\s'"]+)""".toRegex()

        return document.select("tbody tr").map { row ->
            val divs = row.select("div.col-md-4")
            val div1 = divs[0]
            val div2 = divs[1]

            val link = urlRegex.find(div1.html())!!.value
            val realLink = if (link.contains("member_url")) webClient.get(link).bodyAsText()
            else link

            MeetingContent(
                type = div1.selectFirst("a i")!!.className().split(" ")
                    .find { it.startsWith("fa-") }!!,
                desc = div2.text().trim().substringBeforeLast(".")
                    .ifEmpty { div1.text().trim() },
                url = realLink
            )
        }
    }

    suspend fun getAllPresenceStatus(
        allMeeting: Map<Int, String>,
        allPresenceInfo: List<Boolean>
    ): List<StatusPresensi> = coroutineScope {
        allPresenceInfo.mapIndexed { index, isHadir ->
            async {
                if (isHadir) return@async StatusPresensi.SudahHadir

                val meetUrl = allMeeting[index] ?: return@async StatusPresensi.BelumHadirTanpaLink

                val meetHtml = webClient.get(meetUrl).bodyAsText()
                val link = Jsoup.parse(meetHtml).selectFirst("a[href*=force_download]")
                    ?.attr("href") ?: return@async StatusPresensi.BelumHadirTanpaLink
                StatusPresensi.BelumHadirAdaLink(link)
            }
        }.awaitAll()
    }

    suspend fun getAllTask(courseMeetings: Map<Int, String>): Map<Int, TaskInfo> = coroutineScope {
        fun extractFileUrl(container: Element): String? {
            val pdfId = container.selectFirst("a[onclick*=lihat_pdf]")?.attr("onclick")
                ?.substringAfter("'", "")?.substringBefore("'")

            val pictId = container.selectFirst("a[onclick*=lihat_gambar]")?.attr("onclick")
                ?.substringAfter("'", "")?.substringBefore("'")

            val others = container.selectFirst("a[href*=force_download]")?.attr("href")

            return when {
                pdfId != null -> "https://lms.unindra.ac.id/media_public/lihat_pdf/$pdfId"
                pictId != null -> "https://lms.unindra.ac.id/media_public/lihat_gambar/$pictId"
                else -> others
            }
        }

        courseMeetings.map { (index, meetingUrl) ->
            async {
                val html = webClient.get(meetingUrl).bodyAsText()
                val taskUrl = Jsoup.parse(html).selectFirst("a[href*=member_tugas]")
                    ?.attr("href") ?: return@async null

                val taskHtml = webClient.get(taskUrl).bodyAsText()
                val taskParser = Jsoup.parse(taskHtml)

                val message = taskParser.selectFirst("div[style*=padding-left]")?.text()

                val taskFile = extractFileUrl(
                    taskParser.selectFirst("div.callout-white-default")!!
                )

                val deadline: String = taskParser.select("table.table-bordered tr")[1]
                    .selectFirst("td")!!.text()

                val viewUrl = extractFileUrl(
                    taskParser.selectFirst("div.callout-white-warning")!!
                )

                val isSubmitted = taskHtml.contains("Sudah Submit")
                val isExpired = taskHtml.contains("Waktu Submit sudah berakhir")

                index to TaskInfo(taskUrl, message, taskFile, deadline, viewUrl, isSubmitted, isExpired)
            }
        }.awaitAll().filterNotNull().toMap()
    }

    suspend fun executePresence(fileUrl: String) = webClient.get(fileUrl)

    suspend fun downloadFile(fileUrl: String) = webClient.get(fileUrl)

    suspend fun uploadTask(
        fileName: String,
        fileSize: Long,
        stream: InputStream,
        taskUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit
    ) {
        val htmlForm = webClient.get(taskUrl).bodyAsText()
        val parserForm = Jsoup.parse(htmlForm)

        val idTugas = parserForm.selectFirst("input[name=h_id_tugas]")?.attr("value")
            ?: error("Payload form tidak ditemukan")
        val hKode = parserForm.selectFirst("input[name=h_kode]")!!.attr("value")
        val idAktifitas = parserForm.selectFirst("input[name=h_id_aktifitas]")!!.attr("value")

        webClient.post("https://lms.unindra.ac.id/member_tugas/mhs_upload_file_proses") {
            onUpload { uploaded, total ->
                total?.let { onProgress(fileName, uploaded.toFloat() / it) }
            }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("h_id_tugas", idTugas)
                        append("h_kode", hKode)
                        append("h_id_aktifitas", idAktifitas)
                        append("myfile", InputProvider { stream.asInput() }, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=$fileName")
                        })
                    }
                )
            )
        }
    }
}
