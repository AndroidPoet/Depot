package dev.androidpoet.depot.repo

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RepoClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetchBytes(url: String, limit: Long): ByteArray {
        val target = File.createTempFile("depot", ".part")
        try {
            download(url, target, limit) {}
            return target.readBytes()
        } finally {
            target.delete()
        }
    }

    suspend fun download(url: String, target: File, limit: Long, onProgress: (Long) -> Unit) {
        val context = currentCoroutineContext()
        val request = Request.Builder().url(url).header("User-Agent", "Depot").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("$url answered HTTP ${response.code}")
            val body = response.body ?: throw IOException("$url answered with no body")
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        context.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > limit) throw IntegrityException("$url is larger than the expected $limit bytes")
                        output.write(buffer, 0, read)
                        onProgress(total)
                    }
                }
            }
        }
    }
}
