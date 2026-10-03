package dev.tinymcserver.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tinymcserver.app.core.model.ServerInstance
import dev.tinymcserver.app.core.net.Http
import dev.tinymcserver.app.core.plugin.PluginSearch
import dev.tinymcserver.app.core.model.ServerType
import dev.tinymcserver.app.core.runtime.JreManager
import dev.tinymcserver.app.core.server.ServerInstaller
import dev.tinymcserver.app.core.server.ServerProvider
import dev.tinymcserver.app.core.service.DownloadNotifier
import dev.tinymcserver.app.core.storage.EulaManager
import dev.tinymcserver.app.core.storage.InstanceStore
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.core.storage.ServerProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import dev.tinymcserver.app.core.i18n.t

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()

    private val _instances = MutableStateFlow<List<ServerInstance>>(emptyList())
    val instances: StateFlow<List<ServerInstance>> = _instances.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val message = _message.asSharedFlow()

    private val _progress = MutableStateFlow<Pair<Float, String>?>(null)
    val progress: StateFlow<Pair<Float, String>?> = _progress.asStateFlow()

    private val versionCache = HashMap<String, List<String>>()

    init { refresh() }

    fun refresh() {
        _instances.value = InstanceStore.load(ctx)
    }

    fun toast(msg: String) { _message.tryEmit(msg) }

    fun deleteInstance(id: String) {
        InstanceStore.remove(ctx, id)
        Paths.instanceDir(ctx, id).deleteRecursively()
        refresh()
        toast(t("已删除实例"))
    }

    fun saveInstance(inst: ServerInstance) {
        InstanceStore.update(ctx, inst)
        refresh()
    }

    /** 创建实例目录 + 写配置 + 写 EULA（用户已同意时） */
    fun createInstance(inst: ServerInstance, acceptEula: Boolean): ServerInstance {
        val dir = Paths.instanceDir(ctx, inst.id)
        dir.mkdirs()
        File(dir, "plugins").mkdirs()
        File(dir, "backups").mkdirs()
        File(dir, "tmp").mkdirs()
        ServerProperties.applyConfig(File(dir, "server.properties"), inst.config)
        if (acceptEula) EulaManager.accept(dir)
        InstanceStore.add(ctx, inst)
        refresh()
        return inst
    }

    /**
     * 拉取版本列表。
     *
     * 注意：**只缓存非空结果** —— 旧实现会把一次失败 / 空响应缓存下来，
     * 导致「列表空白」在本次进程内一直无法恢复。
     *
     * @param force 忽略缓存重新请求（界面上的「重试」用它）
     */
    suspend fun loadVersions(type: ServerType, force: Boolean = false): List<String> {
        if (!force) versionCache[type.key]?.takeIf { it.isNotEmpty() }?.let { return it }
        val list = withContext(Dispatchers.IO) { ServerProvider.listVersions(type) }
        if (list.isNotEmpty()) versionCache[type.key] = list
        return list
    }

    fun clearVersionCache() { versionCache.clear() }

    fun installJar(inst: ServerInstance, onDone: (Boolean) -> Unit = {}) {
        val title = t("下载 %s %s", inst.config.type.display, inst.config.mcVersion)
        viewModelScope.launch {
            val taskId = DownloadNotifier.start(ctx, title)
            try {
                ServerInstaller.install(ctx, inst, isCancelled = { DownloadNotifier.isCancelled(taskId) }) { p, s ->
                    _progress.value = (p to s)
                    DownloadNotifier.progress(ctx, taskId, title, p, s)
                }
                _progress.value = null
                DownloadNotifier.finish(ctx, taskId)
                toast(t("服务端 jar 下载完成"))
                onDone(true)
            } catch (e: CancellationException) {
                _progress.value = null
                DownloadNotifier.finish(ctx, taskId)
                cleanupPartial(inst)
                toast(t("已取消下载"))
                onDone(false)
            } catch (e: Exception) {
                _progress.value = null
                DownloadNotifier.finish(ctx, taskId)
                toast(t("下载失败：%s", e.message))
                onDone(false)
            }
        }
    }

    fun installJre(major: Int, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                JreManager.install(ctx, major) { part, idx, total ->
                    _progress.value = ((idx.toFloat() / total) to t("解压 %s", part))
                }
                _progress.value = null
                toast(t("JRE %s 安装完成", major))
                onDone(true)
            } catch (e: Exception) {
                _progress.value = null
                toast(t("JRE 安装失败：%s", e.message))
                onDone(false)
            }
        }
    }

    /** 一键准备：先解压匹配的 JRE，再从官方源下载服务端 jar */
    fun prepareInstance(inst: ServerInstance, onDone: (Boolean) -> Unit = {}) {
        val title = t("准备 %s", inst.config.name)
        viewModelScope.launch {
            val taskId = DownloadNotifier.start(ctx, title)
            try {
                if (!JreManager.isInstalled(ctx, inst.config.jreMajor)) {
                    _progress.value = (0f to t("解压 JRE %s…", inst.config.jreMajor))
                    DownloadNotifier.progress(ctx, taskId, title, 0f, t("解压 JRE %s…", inst.config.jreMajor))
                    JreManager.install(ctx, inst.config.jreMajor) { part, idx, total ->
                        _progress.value = ((idx.toFloat() / total) to t("解压 %s", part))
                        DownloadNotifier.progress(ctx, taskId, title, idx.toFloat() / total, t("解压 %s", part))
                    }
                }
                _progress.value = (0.02f to t("解析下载地址…"))
                ServerInstaller.install(ctx, inst, isCancelled = { DownloadNotifier.isCancelled(taskId) }) { p, s ->
                    _progress.value = (p to s)
                    DownloadNotifier.progress(ctx, taskId, title, p, s)
                }
                _progress.value = null
                DownloadNotifier.finish(ctx, taskId)
                toast(t("准备完成，可以启动了"))
                onDone(true)
            } catch (e: CancellationException) {
                _progress.value = null
                DownloadNotifier.finish(ctx, taskId)
                cleanupPartial(inst)
                toast(t("已取消"))
                onDone(false)
            } catch (e: Exception) {
                _progress.value = null
                DownloadNotifier.finish(ctx, taskId)
                toast(t("准备失败：%s", e.message))
                onDone(false)
            }
        }
    }

    /** 取消当前下载（界面上的取消按钮 / 通知里的取消都走这里） */
    fun cancelDownload() {
        DownloadNotifier.requestCancelLatest()
    }

    /** 取消后清掉半成品 jar，避免被误判为「已下载」 */
    private fun cleanupPartial(inst: ServerInstance) {
        runCatching { File(Paths.instanceDir(ctx, inst.id), inst.jarFile).delete() }
    }

    /**
     * 下载 Modrinth 插件到实例的 plugins/ 目录。
     * 带通知进度、取消与**完整性校验**（按上游声明的字节数），
     * 半截文件会被删除并报错，避免服务端报 "Failed to open plugin jar"。
     */
    fun downloadPlugin(
        inst: ServerInstance,
        projectId: String,
        loader: String,
        title: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        val jobTitle = t("下载插件 %s", title)
        viewModelScope.launch {
            val taskId = DownloadNotifier.start(ctx, jobTitle)
            try {
                val d = withContext(Dispatchers.IO) {
                    PluginSearch.pickDownload(projectId, inst.config.mcVersion, loader)
                } ?: throw RuntimeException(t("Modrinth 上没有与该 MC 版本兼容的文件"))
                if (d.size > 0 && d.size < 4096) {
                    throw RuntimeException(t("上游文件异常（仅 %s 字节）", d.size))
                }
                val dir = File(Paths.instanceDir(ctx, inst.id), "plugins")
                dir.mkdirs()
                val out = File(dir, d.filename)
                // 先删掉同名旧文件，避免覆盖过程中断留下半截 jar
                runCatching { if (out.exists()) out.delete() }
                withContext(Dispatchers.IO) {
                    Http.download(
                        url = d.url,
                        dest = out,
                        isCancelled = { DownloadNotifier.isCancelled(taskId) },
                        expectedSize = d.size,
                    ) { read, total ->
                        val p = if (total > 0) read.toFloat() / total else 0f
                        val totalTxt = if (total > 0) "${total / 1024}KB" else "?"
                        DownloadNotifier.progress(
                            ctx, taskId, jobTitle, p,
                            t("下载中 %sKB / %s", read / 1024, totalTxt),
                        )
                    }
                }
                DownloadNotifier.finish(ctx, taskId)
                toast(t("已下载 %s（重启服务端后生效）", d.filename))
                onDone(true)
            } catch (e: CancellationException) {
                DownloadNotifier.finish(ctx, taskId)
                toast(t("已取消下载"))
                onDone(false)
            } catch (e: Exception) {
                DownloadNotifier.finish(ctx, taskId)
                toast(t("插件下载失败：%s", e.message))
                onDone(false)
            }
        }
    }

    fun bundledJres(): List<Int> = JreManager.bundledMajors(ctx)
    fun installedJres(): List<Int> = JreManager.BUNDLED.filter { JreManager.isInstalled(ctx, it) }
    fun jreInstalled(major: Int) = JreManager.isInstalled(ctx, major)
    fun jreVersionLabel(major: Int) = JreManager.versionLabel(ctx, major)
    fun jreSize(major: Int) = JreManager.installedSize(ctx, major)
}
