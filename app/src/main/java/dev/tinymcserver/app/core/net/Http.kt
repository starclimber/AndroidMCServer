package dev.tinymcserver.app.core.net

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import dev.tinymcserver.app.core.i18n.t

/** 统一网络访问（含下载与进度回调） */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private const val UA = "TinyMCServer/1.0 (Android)"

    fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code} @ $url")
            return r.body?.string() ?: ""
        }
    }

    /**
     * 下载到文件。
     * @param isCancelled 每读一块前轮询；返回 true 则抛 CancellationException 终止并保留半成品供调用方清理。
     * @param expectedSize 期望字节数（>0 时校验）；不符则删除文件并抛异常，避免半截文件被当成可用资源。
     */
    fun download(
        url: String,
        dest: File,
        isCancelled: () -> Boolean = { false },
        expectedSize: Long = 0L,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code} @ $url")
            val body = r.body ?: throw RuntimeException("empty body @ $url")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            body.byteStream().use { ins ->
                FileOutputStream(dest).use { outs ->
                    val buf = ByteArray(128 * 1024)
                    var read = 0L
                    while (true) {
                        if (isCancelled()) throw CancellationException(t("下载已取消"))
                        val n = ins.read(buf)
                        if (n < 0) break
                        outs.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        }
        val expect = if (expectedSize > 0) expectedSize else -1L
        if (expect > 0 && dest.length() != expect) {
            val got = dest.length()
            runCatching { dest.delete() }
            throw RuntimeException(t("下载不完整（%s / %s 字节），已删除，请重试", got, expect))
        }
    }

    fun exists(url: String): Boolean = try {
        val req = Request.Builder().url(url).head().header("User-Agent", UA).build()
        client.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }
}
