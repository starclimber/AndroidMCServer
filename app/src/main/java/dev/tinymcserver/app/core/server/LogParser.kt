package dev.tinymcserver.app.core.server

import dev.tinymcserver.app.core.i18n.t

/** 控制台输出按行解析 */
object LogParser {

    enum class Severity { INFO, WARN, ERROR, PLAIN }

    private val playersRe =
        Regex("There are (\\d+) of a max of (\\d+) players online")
    private val joinRe = Regex("]: ([A-Za-z0-9_]{1,16}) joined the game")
    private val leaveRe = Regex("]: ([A-Za-z0-9_]{1,16}) left the game")

    /** 返回 (在线人数, 上限) */
    fun playersOnline(line: String): Pair<Int, Int>? {
        val m = playersRe.find(line) ?: return null
        return (m.groupValues[1].toIntOrNull() ?: 0) to (m.groupValues[2].toIntOrNull() ?: 0)
    }

    fun join(line: String): String? = joinRe.find(line)?.groupValues?.get(1)
    fun leave(line: String): String? = leaveRe.find(line)?.groupValues?.get(1)

    /** 服务端就绪 */
    fun isReady(line: String): Boolean =
        line.contains("Done (") && line.contains("For help, type")

    /** 崩溃 / OOM / 异常 */
    fun isOom(line: String): Boolean =
        line.contains("OutOfMemoryError") || line.contains("java.lang.OutOfMemoryError")

    fun isError(line: String): Boolean =
        line.contains("ERROR") || line.contains("FATAL") ||
            line.contains("Exception") || line.contains("Caused by:") ||
            line.contains("SEVERE")

    fun severity(line: String): Severity = when {
        isOom(line) -> Severity.ERROR
        isError(line) -> Severity.ERROR
        line.contains("WARN") || line.contains("WARNING") -> Severity.WARN
        line.contains("INFO") -> Severity.INFO
        else -> Severity.PLAIN
    }

    /** 截取最近 N 行的错误摘要 */
    fun errorDigest(lines: List<String>, n: Int = 40): String {
        val errs = lines.filter { isError(it) }
        val tail = lines.takeLast(n)
        return buildString {
            appendLine(t("=== 错误摘要 (共 %s 条异常/错误行) ===", errs.size))
            if (errs.isEmpty()) appendLine(t("未检测到明显异常行。"))
            tail.forEach { appendLine(it) }
        }
    }
}
