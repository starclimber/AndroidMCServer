package dev.tinymcserver.app.core.runtime

import android.content.Context
import android.content.res.AssetManager
import android.system.Os
import dev.tinymcserver.app.core.storage.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 从 APK assets 中解压内置 JRE 到私有目录。
 * assets 结构：jre/jre{major}/{universal.tar.xz, bin-arm64.tar.xz}
 * 两个归档叠加合并即为完整的 JRE Home。
 */
object JreManager {

    /**
     * 内置 JRE 大版本。
     * 自 1.0.13 起移除 JRE 8 —— Android 平台缺少可直接获取的独立 JRE 8 发行版，
     * 而 MC 1.16 及更早才需要它（现代 Paper/Purpur/Folia 均在 1.17+）。
     */
    val BUNDLED = listOf(17, 21, 25)

    /** 0755 */
    private const val MODE_EXEC = 493

    private fun assetPath(major: Int, part: String) = "jre/jre$major/$part"

    /** 注意：AssetManager.list() 只能列举目录，对文件会返回空；这里用 open() 判断文件是否存在 */
    fun assetFileExists(ctx: Context, path: String): Boolean = runCatching {
        ctx.assets.open(path, AssetManager.ACCESS_STREAMING).close()
        true
    }.getOrDefault(false)

    /** 实际随 APK 打包、可用的 JRE 列表 */
    fun bundledMajors(ctx: Context): List<Int> = BUNDLED.filter { major ->
        assetFileExists(ctx, assetPath(major, "universal.tar.xz")) ||
            assetFileExists(ctx, assetPath(major, "bin-arm64.tar.xz"))
    }

    fun isInstalled(ctx: Context, major: Int): Boolean = Paths.jreJava(ctx, major).exists()

    /**
     * 启动前自愈：若 bin/java 不可执行（例如被 copy 丢失执行位），重新 chmod。
     * @return 是否可用
     */
    fun ensureExecutable(ctx: Context, major: Int): Boolean {
        val home = Paths.jreHome(ctx, major)
        val java = Paths.jreJava(ctx, major)
        if (!java.exists()) return false
        if (java.canExecute()) return true
        chmodAll(home)
        return java.canExecute()
    }

    /** 已安装 JRE 的版本描述（读取 release 文件） */
    fun versionLabel(ctx: Context, major: Int): String {
        val rel = File(Paths.jreHome(ctx, major), "release")
        if (rel.exists()) {
            rel.readText().lineSequence().firstOrNull { it.startsWith("JAVA_VERSION") }
                ?.let { return it.substringAfter('"').substringBefore('"') }
        }
        return "JRE $major"
    }

    /** 已安装 JRE 的占用空间 */
    fun installedSize(ctx: Context, major: Int): Long {
        val home = Paths.jreHome(ctx, major)
        if (!home.exists()) return 0
        var total = 0L
        home.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    /**
     * 解压安装。onProgress(阶段名, 已完成归档数, 总归档数)
     */
    suspend fun install(
        ctx: Context,
        major: Int,
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val home = Paths.jreHome(ctx, major)
        if (Paths.jreJava(ctx, major).exists()) return@withContext

        val parts = listOf("universal.tar.xz", "bin-arm64.tar.xz")
            .filter { assetFileExists(ctx, assetPath(major, it)) }
        if (parts.isEmpty()) throw RuntimeException("assets 中缺少 JRE $major 归档")

        // 先解压到临时目录，成功后替换，避免半成品被误判为已安装
        val staging = File(ctx.cacheDir, "jre_install_$major")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            parts.forEachIndexed { idx, part ->
                onProgress(part, idx, parts.size)
                ctx.assets.open(assetPath(major, part), AssetManager.ACCESS_STREAMING).use { ins ->
                    extractTarXz(ins, staging)
                }
            }
            // 移动到最终目录
            home.deleteRecursively()
            home.parentFile?.mkdirs()
            if (!staging.renameTo(home)) {
                staging.copyRecursively(home, overwrite = true)
                staging.deleteRecursively()
            }
            // 关键：在最终目录上设置可执行权限（copyRecursively 会丢失执行位）
            chmodAll(home)
            File(Paths.jreRoot(ctx), "jre$major.version").writeText(major.toString())
            onProgress("done", parts.size, parts.size)
        } catch (e: Exception) {
            staging.deleteRecursively()
            home.deleteRecursively()
            throw e
        }
    }

    suspend fun uninstall(ctx: Context, major: Int) = withContext(Dispatchers.IO) {
        Paths.jreHome(ctx, major).deleteRecursively()
    }

    /** 给 bin/ 下所有文件与关键本地工具设置 0755（用 Os.chmod，比 File.setExecutable 可靠） */
    fun chmodAll(home: File) {
        // 目录也需要执行(搜索)权限
        runCatching { Os.chmod(home.absolutePath, MODE_EXEC) }
        val bin = File(home, "bin")
        if (bin.isDirectory) {
            runCatching { Os.chmod(bin.absolutePath, MODE_EXEC) }
            bin.listFiles()?.forEach { chmod(it) }
        }
        listOf("lib/jexec", "lib/jspawnhelper").forEach { chmod(File(home, it)) }
    }

    private fun chmod(f: File) {
        if (!f.exists()) return
        runCatching { Os.chmod(f.absolutePath, MODE_EXEC) }
    }

    private fun extractTarXz(input: InputStream, dest: File) {
        BufferedInputStream(input, 1 shl 16).use { bis ->
            XZInputStream(bis).use { xz ->
                TarArchiveInputStream(xz).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val name = entry.name.removePrefix("./")
                        if (name.isNotBlank()) {
                            val out = File(dest, name)
                            when {
                                entry.isDirectory -> out.mkdirs()
                                entry.isSymbolicLink -> {
                                    runCatching {
                                        out.parentFile?.mkdirs()
                                        out.delete()
                                        Os.symlink(entry.linkName, out.absolutePath)
                                    }
                                }
                                else -> {
                                    out.parentFile?.mkdirs()
                                    FileOutputStream(out).use { fo -> tar.copyTo(fo) }
                                }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }
}
