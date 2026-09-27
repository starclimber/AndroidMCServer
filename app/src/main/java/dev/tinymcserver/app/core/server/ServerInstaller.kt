package dev.tinymcserver.app.core.server

import android.content.Context
import dev.tinymcserver.app.core.model.ServerInstance
import dev.tinymcserver.app.core.net.Http
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.core.storage.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 服务端 jar 下载 */
object ServerInstaller {

    suspend fun install(
        ctx: Context,
        instance: ServerInstance,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        Settings.attach(ctx)
        val dir = Paths.instanceDir(ctx, instance.id)
        val jar = File(dir, instance.jarFile)
        onProgress(0f, "解析下载地址…")
        val raw = ServerProvider.serverJarUrl(instance.config.type, instance.config.mcVersion)
        val url = withMirror(raw)
        onProgress(0.02f, "下载 ${instance.config.type.display} ${instance.config.mcVersion}")
        Http.download(url, jar, isCancelled) { read, total ->
            val p = if (total > 0) read.toFloat() / total else 0f
            onProgress(p, "下载中 ${read / 1048576}MB / ${if (total > 0) "${total / 1048576}MB" else "?"}")
        }
        onProgress(1f, "完成")
        jar
    }

    /** 若设置了镜像/代理前缀，则拼接（留空使用官方源） */
    private fun withMirror(url: String): String {
        val m = Settings.mirrorBase
        return if (m.isBlank()) url else m.trimEnd('/') + "/" + url
    }
}
