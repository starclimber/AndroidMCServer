package dev.tinymcserver.app.core.server

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 崩溃现场取证。
 *
 * 为什么需要它：Android 上 `java` 子进程被原生层 abort 时，App 只能拿到一个退出码（134/139…），
 * 真正的死因写在两个地方：
 *  1. JVM 自己写的 `hs_err_pid<pid>.log`（我们通过 -XX:ErrorFile 固定到实例目录，App 读得到）
 *  2. logcat 的 crash buffer —— java 子进程与 App 同 UID，所以 App 有权限读到自己这条记录
 * 两者都抓下来，用户即使看不懂，也能把内容发回来定位。
 */
object CrashDiagnostics {

    /** 退出码 → 人话 */
    fun explain(code: Int): String = when (code) {
        134 -> "SIGABRT：原生层主动中止（JVM/libc 崩溃，最常见）"
        137 -> "SIGKILL：被系统杀掉，通常是内存不足（降低 Xmx 或关掉后台应用）"
        139 -> "SIGSEGV：段错误（原生代码访问了非法内存）"
        143 -> "SIGTERM：被要求退出"
        1 -> "异常退出（参数错误或 Java 抛异常）"
        126, 127 -> "无法执行 java（权限位或文件缺失）"
        else -> "退出码 $code"
    }

    /** 读取最新的一份 hs_err_pid*.log，返回 (文件, 摘要) */
    fun readHsErr(dir: File): Pair<File, String>? {
        val f = (dir.listFiles() ?: return null)
            .filter { it.isFile && it.name.startsWith("hs_err_pid") && it.name.endsWith(".log") }
            .maxByOrNull { it.lastModified() } ?: return null

        val lines = runCatching { f.readLines() }.getOrNull() ?: return null
        val out = StringBuilder()
        var inSummary = false
        var frames = 0
        for ((i, raw) in lines.withIndex()) {
            if (i > 600) break
            val l = raw.trimEnd()
            when {
                // 崩溃原因块：# 开头的那几行
                !inSummary && l.startsWith("#") -> out.appendLine(l)
                l.startsWith("---------------") -> inSummary = true
                inSummary && (l.startsWith("Command Line:") || l.startsWith("Host:") ||
                    l.startsWith("Time:") || l.startsWith("Memory:") ||
                    l.startsWith("vm_info:") || l.startsWith("OS:")) -> out.appendLine(l)
                // 原生调用栈，取前若干帧
                frames < 12 && (l.startsWith("C  [") || l.startsWith("V  [") ||
                    l.startsWith("J ") || l.startsWith("V  ")) -> {
                    out.appendLine(l); frames++
                }
                frames < 12 && l.startsWith("Stack:") -> out.appendLine(l)
            }
        }
        val text = out.toString().trim()
        return f to (if (text.isEmpty()) lines.take(30).joinToString("\n") else text)
    }

    /** 实例目录下所有崩溃报告文件名（用于列表展示） */
    fun listReports(dir: File): List<String> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith("hs_err_pid") }
            .sortedByDescending { it.lastModified() }
            .map { it.name }

    /**
     * 抓取 logcat。应用只能看到自己 UID 的日志，而 java 是 App 的子进程（同 UID），
     * 所以原生崩溃的 "Abort message"/tombstone 摘要是读得到的。
     */
    fun captureLogcat(maxLines: Int = 80): List<String> {
        val commands = listOf(
            arrayOf("/system/bin/logcat", "-d", "-b", "crash", "-v", "brief", "-t", "150"),
            arrayOf("/system/bin/logcat", "-d", "-b", "all", "-v", "brief", "-t", "600"),
            arrayOf("/system/bin/logcat", "-d", "-v", "threadtime", "-t", "400"),
        )
        val keywords = listOf(
            "fatal", "Fatal", "FATAL", "Abort message", "signal ", "signal:",
            "DEBUG", "backtrace", "libc", "libjvm", "libart", "hs_err",
            "tinymcserver", "JNI", "art::", "Cannot ", "cannot ",
            "Error", "error", "Exception",
            // SELinux / 内存保护相关：判断是不是被系统策略拒绝（例如 execmem）
            "avc:", "denied", "execmem", "SELinux", "selinux",
            "tagged", "Pointer tag", "memtag", "MTE", "pkey",
            "SIGABRT", "SIGSEGV", "libjvm.so", "Jit", "jit",
        )
        val out = LinkedHashSet<String>()
        for (cmd in commands) {
            // 输出重定向到临时文件：避免 logcat 输出量大时写满管道导致 waitFor 死锁
            val dump = runCatching { File.createTempFile("logcat", ".txt") }.getOrNull() ?: continue
            val p = runCatching {
                ProcessBuilder(*cmd).redirectErrorStream(true).redirectOutput(dump).start()
            }.getOrNull()
            if (p == null) {
                dump.delete()
                continue
            }
            runCatching {
                if (!p.waitFor(4, TimeUnit.SECONDS)) p.destroy()
            }
            runCatching { if (p.isAlive) p.destroy() }
            val text = runCatching { dump.readText() }.getOrDefault("")
            runCatching { dump.delete() }
            text.lineSequence().forEach { raw ->
                val s = raw.trim()
                if (s.isNotEmpty() && keywords.any { s.contains(it) }) out.add(s)
            }
            if (out.size >= maxLines * 3) break
        }
        return out.toList().takeLast(maxLines)
    }
}
