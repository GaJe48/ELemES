package com.gaje48.lms.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.gaje48.lms.model.FileSource
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageDataSource(context: Context) {
    private val resolver = context.contentResolver

    suspend fun saveToDownloads(
        response: HttpResponse,
        onProgress: (fileName: String, progress: Float) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val fileName = response.request.url.segments.last()
        val totalBytes = response.contentLength() ?: -1L
        val channel = response.bodyAsChannel()

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/elemes")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("Gagal membuat file download")

        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                val buffer = ByteArray(8 * 1024)
                var downloaded = 0L

                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (totalBytes > 0) {
                        onProgress(fileName, downloaded.toFloat() / totalBytes)
                    }
                }
            } ?: error("Gagal menulis file")

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        .onFailure { e ->
            resolver.delete(uri, null, null)
            throw e
        }
    }

    suspend fun openFileStream(uri: Uri): FileSource = withContext(Dispatchers.IO) {
        val cursor = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        ) ?: error("Gagal mendapatkan informasi file")

        val (fileName, fileSize) = cursor.use { c ->
            if (!c.moveToFirst()) error("Gagal membaca metadata file")

            c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) to
                c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
        }

        val stream = resolver.openInputStream(uri) ?: error("File tidak valid")

        FileSource(fileName, fileSize, stream)
    }
}
