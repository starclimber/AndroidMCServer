package dev.tinymcserver.app.core.backup

import android.content.Context
import dev.tinymcserver.app.core.storage.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import dev.tinymcserver.app.core.i18n.t

/** 实例备份 / 恢复（zip） */
object BackupManager {

    private val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 备份实例（排除 backups 自身），仅世界 或 全量 */
    suspend fun backup(
        ctx: Context,
        id: String,
        worldOnly: Boolean,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val dir = Paths.instanceDir(ctx, id)
        val outDir = Paths.backupsDir(ctx, id)
        val name = "${if (worldOnly) "world" else "full"}_${fmt.format(Date())}.zip"
        val out = File(outDir, name)

        val roots = if (worldOnly) {
            listOf("world", "world_nether", "world_the_end", "world_end")
                .map { File(dir, it) }.filter { it.exists() }
        } else {
            dir.listFiles()?.filter { it.name != "backups" }?.toList() ?: emptyList()
        }

        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            roots.forEach { r ->
                zipInto(zos, r, r.name) { p, msg -> onProgress(p, msg) }
            }
        }
        onProgress(1f, t("已生成 %s", out.name))
        out
    }

    private fun zipInto(
        zos: ZipOutputStream,
        file: File,
        base: String,
        progress: (Float, String) -> Unit,
    ) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$base/"))
                zos.closeEntry()
            }
            children.forEach { zipInto(zos, it, "$base/${it.name}", progress) }
        } else {
            progress(0f, file.name)
            zos.putNextEntry(ZipEntry(base))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun list(ctx: Context, id: String): List<File> =
        Paths.backupsDir(ctx, id).listFiles()?.sortedByDescending { it.name } ?: emptyList()

    suspend fun restore(
        ctx: Context,
        id: String,
        zip: File,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val dir = Paths.instanceDir(ctx, id)
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                val target = File(dir, e.name)
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zis.copyTo(it) }
                    onProgress(0f, e.name)
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        onProgress(1f, t("恢复完成"))
    }

    fun delete(zip: File): Boolean = zip.delete()
}
