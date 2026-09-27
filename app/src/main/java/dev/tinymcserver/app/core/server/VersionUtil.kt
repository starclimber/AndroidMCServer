package dev.tinymcserver.app.core.server

import dev.tinymcserver.app.core.model.JreVersion

/** MC 版本比较与 JRE 匹配 */
object VersionUtil {

    /** 将形如 "1.21.4" / "26.2" 的版本解析为可比较的数字串 */
    fun parse(v: String): List<Int> {
        val core = v.substringBefore('-').substringBefore('+')
        return core.split('.').mapNotNull { it.toIntOrNull() }
    }

    /** a > b 返回正；相等 0；小于 负数 */
    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        val n = maxOf(x.size, y.size)
        for (i in 0 until n) {
            val xi = x.getOrElse(i) { 0 }
            val yi = y.getOrElse(i) { 0 }
            if (xi != yi) return xi - yi
        }
        return 0
    }

    /** 是否稳定发行版（排除快照/预发布） */
    fun isStable(v: String): Boolean =
        !v.contains("-pre") && !v.contains("-rc") && !v.contains("snapshot") &&
            !v.startsWith("b") && v.firstOrNull()?.isDigit() == true

    /**
     * 按 MC 版本推荐 JRE；**返回 null 表示当前内置 JRE 不支持该版本**：
     *  - 26.x 及以后（新编号） -> 25
     *  - 1.20.5 ~ 1.21.x        -> 21
     *  - 1.17 ~ 1.20.4          -> 17
     *  - 1.16 及更早            -> null（需要 Java 8，自 1.0.13 起不再内置 JRE 8）
     */
    fun recommendJre(mcVersion: String): JreVersion? {
        val p = parse(mcVersion)
        if (p.isEmpty()) return JreVersion.JRE21
        val major = p[0]
        if (major >= 26) return JreVersion.JRE25
        val minor = p.getOrElse(1) { 0 }
        val patch = p.getOrElse(2) { 0 }
        return when {
            major > 1 -> JreVersion.JRE25
            minor >= 21 -> JreVersion.JRE21
            minor == 20 && patch >= 5 -> JreVersion.JRE21
            minor >= 17 -> JreVersion.JRE17
            else -> null
        }
    }

    /** 该 MC 版本能否用当前内置的 JRE 运行（版本尚未选择时按“可以”处理） */
    fun isSupported(mcVersion: String): Boolean =
        mcVersion.isBlank() || recommendJre(mcVersion) != null
}
