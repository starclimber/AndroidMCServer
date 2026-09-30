package dev.tinymcserver.app.core.runtime

import android.content.Context
import dev.tinymcserver.app.core.server.CrashDiagnostics
import dev.tinymcserver.app.core.storage.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import dev.tinymcserver.app.core.i18n.t

/**
 * JRE 自检 + 深度诊断。
 *
 * ①②③ 分档验证 JVM 本身；④/深诊 用内置探测 jar（assets/runtime/selftest.jar）在该 JRE 里
 * 复刻 Paperclip 的启动路径，每步「先打印再执行」，被原生层 abort 时最后一条 `--> ` 就是崩溃点。
 *
 * 已知实测结论（HONOR PPG-AN00 / Android 17 / API 37）：
 *   · JVM 本身没问题（-version / 大堆 / SerialGC 都正常）；
 *   · GC、压缩指针、LD_LIBRARY_PATH 都不是元凶；
 *   · **关掉 JIT（-Xint）后完全稳定，开着 JIT 必崩** → 问题在 JIT 编译链。
 * 因此「深诊」矩阵以编译策略为主轴。
 */
object JreSelfTest {

    data class Step(val cmd: String, val exit: Int, val output: String)

    data class ProbeCmd(
        val label: String,
        val jvmArgs: List<String>,
        val mode: String,
        val useLdPath: Boolean = true,
    )

    private const val PROBE_ASSET = "runtime/selftest.jar"

    /**
     * 深诊矩阵（13 组）。围绕 JIT 主轴的对照实验，
     * 并用「无网络」「纯解释」两组做基线。
     */
    fun series(): List<ProbeCmd> = listOf(
        ProbeCmd(t("基线：单次 HTTPS（默认 JIT）"), emptyList(), "net1"),
        ProbeCmd(t("隔离网络：无网络模式（默认 JIT）"), emptyList(), "native1"),
        ProbeCmd(t("基线：单次 HTTPS + 纯解释(-Xint)"), listOf("-Xint"), "net1"),
        ProbeCmd(t("看清崩溃时机：-XX:+PrintCompilation"), listOf("-XX:+PrintCompilation"), "net1"),
        ProbeCmd(t("小代码缓存（-XX:ReservedCodeCacheSize=16m）"), listOf("-XX:ReservedCodeCacheSize=16m"), "net3"),
        ProbeCmd(t("关 CDS（-Xshare:off）"), listOf("-Xshare:off"), "net3"),
        ProbeCmd(t("关压缩类指针（-XX:-UseCompressedClassPointers）"), listOf("-XX:-UseCompressedClassPointers"), "net3"),
        ProbeCmd(t("PerfDisableSharedMem + 5 次 HTTPS"), listOf("-XX:+PerfDisableSharedMem"), "net3"),
        ProbeCmd(t("只用 C2（-XX:-TieredCompilation）"), listOf("-XX:-TieredCompilation"), "net3"),
        ProbeCmd(t("关后台编译（-XX:-BackgroundCompilation）"), listOf("-XX:-BackgroundCompilation"), "net3"),
        ProbeCmd(t("单编译线程（-XX:CICompilerCount=1）"), listOf("-XX:CICompilerCount=1"), "net3"),
        ProbeCmd(t("复现：只 C1（TieredStopAtLevel=1）"), listOf("-XX:TieredStopAtLevel=1"), "net1"),
        ProbeCmd(
            t("厨房水槽组合（若这组能跑就有得用）"),
            listOf(
                "-XX:+PerfDisableSharedMem", "-XX:-TieredCompilation",
                "-XX:-BackgroundCompilation", "-XX:CICompilerCount=1",
                "-XX:ReservedCodeCacheSize=64m", "-Xshare:off",
                "-XX:-UseCompressedClassPointers",
            ),
            "net3",
        ),
        ProbeCmd(t("复刻 paperclip：下载 vanilla 服务端 jar"), emptyList(), "dl"),
    )

    // ---------------- ①②③④ ----------------

    fun runAll(ctx: Context, major: Int, xmsMb: Int, xmxMb: Int, mcVersion: String): String {
        val home = Paths.jreHome(ctx, major)
        val java = File(home, "bin/java")
        val sb = StringBuilder()
        sb.append(t("JRE %s 自检\n", major))
        sb.append(t("路径: ")).append(home.absolutePath).append('\n')
        sb.append(t("bin/java 存在: ")).append(java.exists())
            .append(t("   可执行: ")).append(java.canExecute()).append("\n")
        sb.append(t("设备: ")).append(deviceInfo()).append("\n\n")

        val steps = listOf(
            t("① 只启动 JVM") to listOf("-version"),
            t("② 用实例的堆参数启动 (-Xms%sM -Xmx%sM)", xmsMb, xmxMb) to
                listOf("-Xms${xmsMb}M", "-Xmx${xmxMb}M", "-version"),
            t("③ 兼容档启动 (SerialGC + 关压缩指针)") to
                listOf("-XX:+UseSerialGC", "-XX:-UseCompressedOops", "-version"),
        )
        for ((title, extra) in steps) {
            val s = run(home, extra)
            sb.append("=== ").append(title).append(" ===\n")
            sb.append("cmd: java ").append(extra.joinToString(" ")).append('\n')
            sb.append(t("退出码: ")).append(s.exit).append("   (")
                .append(if (s.exit == 0) t("正常") else explain(s.exit)).append(")\n")
            sb.append(s.output.trim().ifBlank { t("(无输出)") }).append("\n\n")
        }

        if (major >= 17) {
            val p = runProbeJar(ctx, major, mcVersion) { }
            sb.append(t("=== ④ 深度探测 jar（复刻 paperclip 启动路径）===\n"))
            sb.append("cmd: java -jar selftest.jar all ").append(mcVersion).append('\n')
            sb.append(t("退出码: ")).append(p.exit).append("   (")
                .append(if (p.exit == 0) t("正常") else explain(p.exit)).append(")\n")
            sb.append(p.output.trim().ifBlank { t("(无输出)") }).append("\n\n")
            sb.append(hsErrSection(ctx, major))
        } else {
            sb.append(t("=== ④ 深度探测 jar ===\nJRE %s 太旧（需 17+），跳过。\n\n", major))
        }
        return sb.toString()
    }

    // ---------------- 深诊 ----------------

    suspend fun runSeries(
        ctx: Context,
        major: Int,
        mcVersion: String,
        onProgress: (String) -> Unit,
    ): String {
        val jar = ensureProbeJar(ctx) ?: return t("APK 内缺少 %s", PROBE_ASSET)
        val home = Paths.jreHome(ctx, major)
        val cmds = series()
        val sb = StringBuilder()
        sb.append(t("JRE %s 深诊矩阵（共 %s 组）\n", major, cmds.size))
        sb.append(t("设备: ")).append(deviceInfo()).append('\n')
        sb.append(t("目标 MC 版本: ")).append(mcVersion).append('\n')
        sb.append(t("探测 jar: ")).append(jar.absolutePath)
            .append("  ").append(jar.length()).append(t(" 字节\n\n"))

        cmds.forEachIndexed { i, c ->
            onProgress(t("深诊进行中 %s/%s：%s", i + 1, cmds.size, c.label))
            val before = hsErrFiles(ctx, major).map { it.absolutePath }.toSet()
            val args = ArrayList<String>()
            args += c.jvmArgs
            args += "-Djava.io.tmpdir=${probeTmp(ctx).absolutePath}"
            args += "-Duser.home=${probeTmp(ctx).absolutePath}"
            args += "-XX:ErrorFile=${probeTmp(ctx).absolutePath}/hs_err_probe_%p.log"
            args += "-jar"
            args += jar.absolutePath
            args += c.mode
            if (mcVersion.isNotBlank()) args += mcVersion
            val s = run(home, args, timeoutSec = 180, useLdPath = c.useLdPath)

            sb.append("=== [").append(i + 1).append('/').append(cmds.size).append("] ")
                .append(c.label).append(" ===\n")
            sb.append("cmd: java ").append(c.jvmArgs.joinToString(" ")).append(' ')
                .append("-jar selftest.jar ").append(c.mode)
                .append(if (c.useLdPath) "" else t("   [无 LD_LIBRARY_PATH]")).append('\n')
            sb.append(t("退出码: ")).append(s.exit).append("   (")
                .append(if (s.exit == 0) t("正常") else explain(s.exit)).append(")\n")
            sb.append(s.output.trim().ifBlank { t("(无输出)") }).append("\n\n")

            // 这一组是否新产生了 JVM 崩溃报告？
            val after = hsErrFiles(ctx, major)
            val fresh = after.filter { it.absolutePath !in before }
            for (f in fresh) {
                val parsed = runCatching { CrashDiagnostics.readHsErr(f.parentFile) }.getOrNull()
                sb.append(t(">>> 本组新生成 JVM 崩溃报告: ")).append(f.name).append('\n')
                sb.append(parsed?.second?.trim().orEmpty().take(3000)).append("\n\n")
                runCatching { f.delete() }
            }

            // 失败时抓一段 logcat：重点看有没有 SELinux 拒绝（avc: denied）/ execmem / tagged pointer
            if (s.exit != 0) {
                onProgress(t("深诊 %s/%s 失败，正在抓 logcat…", i + 1, cmds.size))
                val lg = withTimeoutOrNull(10_000) {
                    withContext(Dispatchers.IO) {
                        runCatching { CrashDiagnostics.captureLogcat(50) }.getOrDefault(emptyList())
                    }
                } ?: emptyList()
                if (lg.isNotEmpty()) {
                    sb.append(t(">>> 本组 logcat 片段（找 avc: denied / execmem / tagged）：\n"))
                    sb.append(lg.joinToString("\n")).append("\n\n")
                }
            }
        }
        sb.append(t("读法：\n"))
        sb.append(t("  · 某档编译策略退出码=0 → 该档可用，可直接在「参数」里选它启动；\n"))
        sb.append(t("  · 只有 -Xint 正常 → JIT 编译链与设备不兼容，需要用其它 JRE 来源；\n"))
        sb.append(t("  · 出现 hs_err_pid*.log → 会附在对应组下面，那是 JVM 自己写的原因。\n"))
        return sb.toString()
    }

    // ---------------- 内部 ----------------

    /** 探测 jar 每次都用 APK 里的最新版本覆盖缓存（避免旧 jar 被复用） */
    private fun ensureProbeJar(ctx: Context): File? {
        val jar = File(ctx.cacheDir, "pmcs-selftest.jar")
        val ok = runCatching {
            val ins = ctx.assets.open(PROBE_ASSET)
            val bytes = ins.use { it.readBytes() }
            ins.close()
            if (bytes.isEmpty()) false
            else {
                if (jar.exists() && jar.length() == bytes.size.toLong()) {
                    true // 大小一致视为已是最新（探测 jar 极小，改版必定改大小）
                } else {
                    jar.outputStream().use { it.write(bytes) }
                    true
                }
            }
        }.getOrDefault(false)
        return if (ok && jar.exists() && jar.length() > 0L) jar else null
    }

    private fun probeTmp(ctx: Context): File =
        File(ctx.cacheDir, "probe").apply { runCatching { mkdirs() } }

    /** 可能写 hs_err 的目录：探测用临时目录、JRE 主目录、实例根目录 */
    private fun hsErrFiles(ctx: Context, major: Int): List<File> {
        val dirs = listOf(
            probeTmp(ctx),
            Paths.jreHome(ctx, major),
            Paths.instancesRoot(ctx),
            ctx.cacheDir,
        )
        val out = ArrayList<File>()
        for (d in dirs) {
            (d.listFiles() ?: emptyArray()).forEach { f ->
                if (f.isFile && f.name.startsWith("hs_err_pid") && f.name.endsWith(".log")) out.add(f)
            }
        }
        return out.sortedByDescending { it.lastModified() }
    }

    private fun hsErrSection(ctx: Context, major: Int): String {
        val files = hsErrFiles(ctx, major)
        if (files.isEmpty()) return t("（未发现 hs_err_pid*.log）\n")
        val sb = StringBuilder()
        sb.append(t(">>> 发现 %s 份 JVM 崩溃报告，最新一份内容：\n", files.size))
        sb.append(runCatching { CrashDiagnostics.readHsErr(files.first().parentFile) }
            .getOrNull()?.second?.trim().orEmpty().take(3000)).append("\n\n")
        return sb.toString()
    }

    fun runProbeJar(
        ctx: Context,
        major: Int,
        mcVersion: String,
        onProgress: (String) -> Unit,
    ): Step {
        val jar = ensureProbeJar(ctx) ?: return Step("java -jar selftest.jar", -1, t("缺少 %s", PROBE_ASSET))
        onProgress(t("探测 jar 运行中（含下载 vanilla 服务端 jar，可能需 1-3 分钟）…"))
        val args = mutableListOf(
            "-Djava.io.tmpdir=${probeTmp(ctx).absolutePath}",
            "-XX:ErrorFile=${probeTmp(ctx).absolutePath}/hs_err_probe_%p.log",
            "-jar", jar.absolutePath, "all",
        )
        if (mcVersion.isNotBlank()) args += mcVersion
        return run(Paths.jreHome(ctx, major), args, timeoutSec = 300)
    }

    private fun deviceInfo(): String = android.os.Build.BRAND + " " + android.os.Build.MODEL +
        "  Android " + android.os.Build.VERSION.RELEASE +
        " (API " + android.os.Build.VERSION.SDK_INT + ")"

    fun run(
        home: File,
        args: List<String>,
        timeoutSec: Long = 30,
        useLdPath: Boolean = true,
    ): Step {
        val java = File(home, "bin/java")
        val cmdLine = "java " + args.joinToString(" ")
        if (!java.exists()) return Step(cmdLine, -1, t("bin/java 不存在"))
        val dump = runCatching { File.createTempFile("jretest", ".txt") }.getOrNull()
            ?: return Step(cmdLine, -1, t("无法创建临时文件"))

        var exit = -1
        var extra = ""
        runCatching {
            val pb = ProcessBuilder(listOf(java.absolutePath) + args)
                .directory(home)
                .redirectErrorStream(true)
                .redirectOutput(dump)
            val env = pb.environment()
            val h = home.absolutePath
            env["JAVA_HOME"] = h
            env["PATH"] = "$h/bin:" + (env["PATH"] ?: "/system/bin")
            if (useLdPath) env["LD_LIBRARY_PATH"] =
                "$h/lib:$h/lib/jli:$h/lib/server:$h/lib/aarch64:$h/lib/aarch64/jli:$h/lib/aarch64/server"
            // 与正式启动 / FCL 对齐：HOME、TMPDIR 指向可写目录
            env["HOME"] = h
            env["TMPDIR"] = h
            val p = pb.start()
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroy()
                extra = t("[超时 %ss，已强制终止]", timeoutSec)
            }
            exit = runCatching { p.exitValue() }.getOrDefault(-1)
        }.onFailure { extra = t("启动失败: %s", it.message) }

        val txt = runCatching { dump.readText() }.getOrDefault("")
        runCatching { dump.delete() }
        return Step(cmdLine, exit, (extra + "\n" + txt).trim())
    }

    private fun explain(code: Int): String = when (code) {
        134 -> t("SIGABRT 原生中止")
        137 -> t("SIGKILL 被系统杀死")
        139 -> t("SIGSEGV 段错误")
        1 -> t("JVM 报错退出")
        else -> t("异常")
    }
}


