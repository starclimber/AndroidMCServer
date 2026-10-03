package dev.tinymcserver.app.core.plugin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.tinymcserver.app.core.storage.Paths
import java.io.File
import java.io.FileOutputStream
import dev.tinymcserver.app.core.i18n.t

/**
 * 从系统文件选择器导入本地的插件 jar 到实例的 plugins/ 目录。
 *
 * 与 PluginScreen（从 Modrinth 下载）互补：这里是离线 / 自备插件包的入口。
 */
object PluginImporter {

    /**
     * 把 [uri] 指向的文件复制进 `<实例目录>/plugins/`。
     * 非 .jar 结尾会自动补后缀；同名文件不覆盖，改为加序号。
     *
     * @return 实际落地的文件名
     */
    fun importTo(ctx: Context, instanceId: String, uri: Uri): String {
        val dir = File(Paths.instanceDir(ctx, instanceId), "plugins").apply { mkdirs() }

        var name = displayName(ctx, uri)?.takeIf { it.isNotBlank() } ?: "plugin.jar"
        if (!name.lowercase().endsWith(".jar")) name += ".jar"

        val target = uniqueFile(dir, name)
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            FileOutputStream(target).use { outs -> ins.copyTo(outs) }
        } ?: throw RuntimeException(t("无法读取所选文件"))

        if (target.length() == 0L) {
            target.delete()
            throw RuntimeException(t("文件为空"))
        }
        return target.name
    }

    /** 取所选文件的显示名（SAF 的 DISPLAY_NAME 列）。 */
    private fun displayName(ctx: Context, uri: Uri): String? {
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    /** 同名时自动加 -1 / -2 … 避免覆盖已有插件。 */
    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val base = name.removeSuffix(".jar")
        var i = 1
        var f = File(dir, "$base-$i.jar")
        while (f.exists()) {
            i++
            f = File(dir, "$base-$i.jar")
        }
        return f
    }
}
