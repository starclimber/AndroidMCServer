package dev.tinymcserver.app.core.server

import android.content.Context
import dev.tinymcserver.app.core.model.InstanceState
import dev.tinymcserver.app.core.model.InstanceRuntime
import dev.tinymcserver.app.core.model.ServerInstance
import dev.tinymcserver.app.core.runtime.JreManager
import dev.tinymcserver.app.core.storage.EulaManager
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.core.storage.ServerProperties
import dev.tinymcserver.app.core.util.ProcessUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap

/** 全局启停入口：按实例 id 管理运行期控制器 */
object ServerManager {

    private val controllers = ConcurrentHashMap<String, RuntimeController>()

    fun controller(ctx: Context, instance: ServerInstance): RuntimeController {
        val c = controllers[instance.id] ?: RuntimeController(ctx.applicationContext, instance)
        c.instance = instance
        controllers[instance.id] = c
        return c
    }

    fun controllerOrNull(id: String): RuntimeController? = controllers[id]

    fun all(): List<RuntimeController> = controllers.values.toList()

    fun runningCount(): Int =
        controllers.values.count { it.state.value.state == InstanceState.RUNNING }

    fun stopAll() { controllers.values.forEach { it.requestStop() } }
}

/**
 * 单个实例的运行期控制器：组装命令、启动进程、读写 stdio、日志解析、自动重启、内存采样。
 */
class RuntimeController(
    private val appCtx: Context,
    @Volatile var instance: ServerInstance,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(InstanceRuntime(maxPlayers = instance.config.maxPlayers))
    val state: StateFlow<InstanceRuntime> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val ring = ArrayDeque<String>()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var memJob: Job? = null
    private var userStop = false
    private var restartCount = 0

    val dir: File get() = Paths.instanceDir(appCtx, instance.id)
    val jar: File get() = File(dir, instance.jarFile)
    val propertiesFile: File get() = File(dir, "server.properties")

    // ---------------- 公共操作 ----------------

    fun start(restart: Boolean = false) {
        if (_state.value.state == InstanceState.RUNNING ||
            _state.value.state == InstanceState.STARTING
        ) return
        userStop = false
        scope.launch { doStart(restart) }
    }

    fun requestStop() {
        userStop = true
        val w = writer
        if (w != null && _state.value.state == InstanceState.RUNNING) {
            _state.value = _state.value.copy(state = InstanceState.STOPPING)
            runCatching { sendCommand("stop") }
            scope.launch {
                delay(60_000)
                if (_state.value.state != InstanceState.STOPPED) {
                    appendLocal("停止超时，强制结束进程")
                    process?.destroy()
                }
            }
        } else {
            process?.destroy()
            _state.value = _state.value.copy(state = InstanceState.STOPPED)
        }
    }

    fun forceKill() {
        userStop = true
        process?.destroy()
        _state.value = _state.value.copy(state = InstanceState.STOPPED)
    }

    fun sendCommand(cmd: String) {
        val w = writer ?: run { appendLocal("进程未运行"); return }
        scope.launch {
            runCatching {
                w.write(cmd)
                w.newLine()
                w.flush()
                appendLocal("> $cmd")
            }.onFailure { appendLocal("命令发送失败: ${it.message}") }
        }
    }

    fun appendLocal(line: String) = appendLine("[Tiny] $line")

    // ---------------- 内部 ----------------

    private suspend fun doStart(restart: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (!jar.exists()) {
                appendLine("[Tiny] 未找到 server.jar，请先在实例中下载服务端")
                _state.value = _state.value.copy(state = InstanceState.CRASHED)
                return@withContext
            }
            if (!EulaManager.isAccepted(dir)) {
                appendLine("[Tiny] 尚未同意 Mojang EULA，无法启动")
                _state.value = _state.value.copy(state = InstanceState.CRASHED)
                return@withContext
            }
            val major = instance.config.jreMajor
            val jreHome = Paths.jreHome(appCtx, major)
            if (!Paths.jreJava(appCtx, major).exists()) {
                appendLine("[Tiny] 未安装 JRE $major，请到「设置 → JRE 管理」安装")
                _state.value = _state.value.copy(state = InstanceState.CRASHED)
                return@withContext
            }
            // 自愈：确保 bin/java 有可执行权限（否则 exec 报 error=13 Permission denied）
            if (!JreManager.ensureExecutable(appCtx, major)) {
                appendLine("[Tiny] JRE $major 的 bin/java 不可执行，自动修复失败，请到设置里「重装」该 JRE")
                _state.value = _state.value.copy(state = InstanceState.CRASHED)
                return@withContext
            }

            ServerProperties.applyConfig(propertiesFile, instance.config)
            File(dir, "tmp").mkdirs()

            _state.value = _state.value.copy(
                state = InstanceState.STARTING,
                startedAt = System.currentTimeMillis(),
            )
            appendLine("[Tiny] 使用 JRE $major 启动：${instance.config.type.display} ${instance.config.mcVersion}")

            val args = LaunchArgs.build(jreHome, instance.config, dir, jar)
            appendLine("[Tiny] 启动参数: " + args.drop(1).joinToString(" "))
            val pb = ProcessBuilder(args)
                .directory(dir)
                .redirectErrorStream(true)
            runCatching {
                val env = pb.environment()
                val home = jreHome.absolutePath
                env["JAVA_HOME"] = home
                env["PATH"] = "$home/bin:" + (env["PATH"] ?: "/system/bin")
                // 覆盖各版本 JDK 的库布局：JDK 8 的 libjli.so 位于 lib/aarch64/jli，
                // JDK 9~13 位于 lib/jli，JDK 14+ 位于 lib 与 lib/<arch>/server。
                // 缺了 lib/<arch>/jli 会导致 JDK 8 直接报 "library libjli.so not found"。
                env["LD_LIBRARY_PATH"] =
                    "$home/lib:$home/lib/jli:$home/lib/server:$home/lib/aarch64:" +
                        "$home/lib/aarch64/jli:$home/lib/aarch64/server:" +
                        (env["LD_LIBRARY_PATH"] ?: "")
                // 对齐 FCL 的做法：HOME / TMPDIR 指向可写目录。
                // Android 上默认 HOME=/data、TMPDIR 走 /tmp 都不一定可写，
                // 而 JVM 与其原生依赖（含 JIT 相关的内存/临时文件逻辑）会用到它们。
                env["HOME"] = dir.absolutePath
                env["TMPDIR"] = File(dir, "tmp").absolutePath
            }
            val proc = pb.start()
            process = proc
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
            val pid = ProcessUtil.findPid(jar.absolutePath)
            appendLine("[Tiny] 进程已启动" + if (pid > 0) " (pid=$pid)" else "")
            _state.value = _state.value.copy(state = InstanceState.RUNNING, pid = pid)

            startMemorySampler(jar.absolutePath)

            val reader = proc.inputStream.bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                handleLine(line)
            }
            val code = proc.waitFor()
            memJob?.cancel()
            appendLine(
                "[Tiny] 进程退出，返回码 $code" +
                    if (code != 0) "（${CrashDiagnostics.explain(code)}）" else ""
            )
            if (!userStop && code != 0) {
                diagnose(dir, pid)
                appendLocal(
                    "排查建议：① 把「诊断 → 启动参数」的 GC 预设切到「兼容（SerialGC）」再试；" +
                        "② 降低 Xmx（先试 2048）；③ 换一个 JRE 版本（如 25 / 17）试。"
                )
            }
            _state.value = _state.value.copy(
                state = if (userStop) InstanceState.STOPPED else InstanceState.CRASHED,
                exitCode = code,
                players = 0,
                pid = -1,
            )
            maybeAutoRestart(code)
        } catch (e: Exception) {
            appendLine("[Tiny] 启动失败：${e.message}")
            _state.value = _state.value.copy(state = InstanceState.CRASHED)
            maybeAutoRestart(-1)
        }
    }

    /**
     * 异常退出后收集崩溃现场。Android 上 java 子进程被原生层 abort 时，
     * App 只能拿到退出码，真正的死因在 hs_err_pid*.log 与 logcat 的 crash buffer 里。
     */
    private suspend fun diagnose(dir: File, pid: Long) {
        appendLocal("进程异常退出，开始收集崩溃现场…")
        _state.value = _state.value.copy(crashSummary = "")
        val sb = StringBuilder()

        val hs = withContext(Dispatchers.IO) {
            runCatching { CrashDiagnostics.readHsErr(dir) }.getOrNull()
        }
        if (hs != null) {
            appendLocal("找到 JVM 崩溃报告 ${hs.first.name}：")
            hs.second.lineSequence().forEach { appendLine("[崩溃] $it") }
            sb.append("=== JVM 崩溃报告 ").append(hs.first.name).append(" ===\n")
                .append(hs.second).append('\n')
        } else {
            appendLocal("没有生成 hs_err_pid*.log —— 崩溃发生在 JVM 之外的原生层（如 bionic 分配器 / 动态链接器）。")
            sb.append("未生成 hs_err 报告（崩溃点在 JVM 之外的原生层）\n")
        }

        val lg = withTimeoutOrNull(8_000) {
            withContext(Dispatchers.IO) {
                runCatching { CrashDiagnostics.captureLogcat() }.getOrDefault(emptyList())
            }
        } ?: emptyList()
        if (lg.isEmpty()) {
            appendLocal("logcat 未取到内容（部分系统限制读取，可忽略）")
        } else {
            appendLocal("logcat 崩溃片段（${lg.size} 行）：")
            lg.forEach { appendLine("[logcat] $it") }
            sb.append("\n=== logcat ===\n").append(lg.joinToString("\n"))
        }

        _state.value = _state.value.copy(crashSummary = sb.toString())
        appendLocal("诊断信息已生成，点底部「诊断」查看或复制。")
    }

    private fun maybeAutoRestart(exitCode: Int) {
        if (userStop || !instance.config.autoRestart) return
        if (restartCount >= 5) {
            appendLine("[Tiny] 自动重启已达上限(5)，停止。")
            return
        }
        restartCount++
        appendLocal("将在 10 秒后自动重启（第 $restartCount 次）")
        scope.launch {
            delay(10_000)
            if (!userStop) start(restart = true)
        }
    }

    private fun handleLine(line: String) {
        appendLine(line)
        LogParser.playersOnline(line)?.let { (n, max) ->
            _state.value = _state.value.copy(players = n, maxPlayers = max)
        }
        when {
            LogParser.isOom(line) -> appendLocal("检测到内存溢出(OOM)，建议提高 Xmx 或降低视距")
            LogParser.isReady(line) -> appendLocal("服务端已就绪")
        }
        if (_state.value.state == InstanceState.STARTING && LogParser.isReady(line)) {
            _state.value = _state.value.copy(state = InstanceState.RUNNING)
        }
    }

    private fun appendLine(line: String) {
        ring.addLast(line)
        while (ring.size > 3000) ring.removeFirst()
        _log.value = ring.toList()
        _state.value = _state.value.copy(lastLine = line)
    }

    private fun startMemorySampler(jarPath: String) {
        memJob?.cancel()
        memJob = scope.launch {
            while (true) {
                val pid = ProcessUtil.findPid(jarPath)
                if (pid > 0) {
                    _state.value = _state.value.copy(
                        pid = pid,
                        memoryUsedMb = ProcessUtil.readVmRss(pid),
                        memoryMaxMb = instance.config.xmxMb.toLong(),
                    )
                } else {
                    _state.value = _state.value.copy(memoryMaxMb = instance.config.xmxMb.toLong())
                }
                delay(5000)
            }
        }
    }
}
