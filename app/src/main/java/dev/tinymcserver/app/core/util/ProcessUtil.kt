package dev.tinymcserver.app.core.util

import java.io.File

/** 进程查找与内存读取（Android 无 Process.pid()，通过 /proc 定位） */
object ProcessUtil {

    /** 在 /proc 中找到 cmdline 含指定 jar 路径的进程 */
    fun findPid(jarPath: String): Long {
        val procs = File("/proc")
        val children = procs.listFiles() ?: return -1L
        for (d in children) {
            if (d.name.isEmpty() || !d.name.all { it.isDigit() }) continue
            val cmd = File(d, "cmdline")
            if (!cmd.exists()) continue
            val s = runCatching { cmd.readBytes().toString(Charsets.UTF_8) }.getOrNull() ?: continue
            if (s.contains(jarPath)) return d.name.toLongOrNull() ?: -1L
        }
        return -1L
    }

    /** 读取 VmRSS（MB） */
    fun readVmRss(pid: Long): Long = runCatching {
        val f = File("/proc/$pid/status")
        if (!f.exists()) return 0L
        f.readLines().firstOrNull { it.startsWith("VmRSS:") }
            ?.let { it.replace(Regex("[^0-9]"), "").toLongOrNull()?.div(1024) } ?: 0L
    }.getOrDefault(0L)
}
