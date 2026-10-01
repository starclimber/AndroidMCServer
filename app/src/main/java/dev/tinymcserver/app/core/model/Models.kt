package dev.tinymcserver.app.core.model

import kotlinx.serialization.Serializable
import dev.tinymcserver.app.core.i18n.t

/** 服务端类型 */
enum class ServerType(val key: String, val display: String) {
    VANILLA("vanilla", "Vanilla"),
    PAPER("paper", "Paper"),
    PURPUR("purpur", "Purpur"),
    FOLIA("folia", "Folia");

    companion object {
        fun fromKey(k: String): ServerType = entries.firstOrNull { it.key == k } ?: VANILLA
    }
}

/** 内置 JRE 大版本 */
enum class JreVersion(val major: Int) {
    JRE8(8), JRE17(17), JRE21(21), JRE25(25);

    companion object {
        fun fromMajor(m: Int): JreVersion = entries.firstOrNull { it.major == m } ?: JRE21
    }
}

/**
 * JVM 垃圾回收 / 参数预设。
 * Android（bionic）上 JVM 的兼容性差异较大，所以把预设做成可选，便于逐个排查。
 */
enum class GcPreset(val key: String, private val displayZh: String, private val descZh: String) {
    CONSERVATIVE("conservative", "均衡（G1，推荐）", "G1GC + 最大暂停 200ms，去掉激进微调"),
    PERFORMANCE("performance", "高性能（G1 + Aikar）", "Aikar 调优参数，适合 4GB 以上内存"),
    COMPAT("compat", "兼容（SerialGC）", "单线程 GC 且关闭压缩指针，崩溃时的首选排查档"),
    CUSTOM("custom", "自定义", "只用下方「额外 JVM 参数」里的内容，完全自控");

    /** 显示名（随当前语言变化） */
    val display: String get() = t(displayZh)

    /** 说明（随当前语言变化） */
    val desc: String get() = t(descZh)

    companion object {
        fun fromKey(k: String): GcPreset = entries.firstOrNull { it.key == k } ?: CONSERVATIVE
    }
}

/**
 * 编译（JIT）预设。
 *
 * 实测背景：在 HONOR PPG-AN00 / Android 17 上，内置 JRE 21 跑真实负载时会被原生层
 * SIGABRT（bionic tagged-pointer 校验），而**关掉 JIT（-Xint）后完全稳定** →
 * 问题出在 JIT 编译链。这里给出几档从激进到保守的编译策略，便于逐个试出可用档。
 */
enum class JitPreset(val key: String, private val displayZh: String, private val descZh: String, val args: List<String>) {
    DEFAULT("default", "默认（C1 + C2 分层编译）", "性能最好，若崩溃则往下换", emptyList()),
    C2_ONLY("c2", "只用 C2（关分层编译）", "-XX:-TieredCompilation，性能接近默认", listOf("-XX:-TieredCompilation")),
    NO_BG("nobg", "关后台编译", "-XX:-BackgroundCompilation，编译改在业务线程同步做", listOf("-XX:-BackgroundCompilation")),
    ONE_COMPILER("one", "单编译线程", "-XX:CICompilerCount=1，排除编译线程并发问题", listOf("-XX:CICompilerCount=1")),
    INTERPRET("interpret", "纯解释执行（最稳，最慢）", "-Xint，完全不用 JIT；已实测稳定可跑", listOf("-Xint"));

    /** 显示名（随当前语言变化） */
    val display: String get() = t(displayZh)

    /** 说明（随当前语言变化） */
    val desc: String get() = t(descZh)

    companion object {
        fun fromKey(k: String): JitPreset = entries.firstOrNull { it.key == k } ?: DEFAULT
    }
}

enum class InstanceState { STOPPED, STARTING, RUNNING, STOPPING, CRASHED }

/** 实例配置（可序列化持久化） */
@Serializable
data class InstanceConfig(
    val name: String,
    val typeKey: String = "paper",
    val mcVersion: String = "",
    val jreMajor: Int = 21,
    val xmsMb: Int = 2048,
    val xmxMb: Int = 4096,
    val port: Int = 25565,
    val onlineMode: Boolean = true,
    val difficulty: String = "easy",
    val viewDistance: Int = 8,
    val simulationDistance: Int = 6,
    val maxPlayers: Int = 10,
    val whitelist: Boolean = false,
    val motd: String = "Android MC Server",
    val autoRestart: Boolean = false,
    val foliaRegionThreads: Int = -1,
    val extraJvmArgs: String = "",
    val gcPreset: String = "conservative",
    val jitPreset: String = "default",
) {
    val type: ServerType get() = ServerType.fromKey(typeKey)
    val jre: JreVersion get() = JreVersion.fromMajor(jreMajor)
    val preset: GcPreset get() = GcPreset.fromKey(gcPreset)
    val jit: JitPreset get() = JitPreset.fromKey(jitPreset)
}

/** 一个服务端实例 */
@Serializable
data class ServerInstance(
    val id: String,
    val config: InstanceConfig,
    val createdAt: Long,
    val jarFile: String = "server.jar",
    /** 若迁移到 SAF 外置存储，记录其树 URI */
    val safTreeUri: String = "",
    val safRelative: String = "",
) {
    val isExternal: Boolean get() = safTreeUri.isNotEmpty()
}

/** 运行期状态快照 */
data class InstanceRuntime(
    val state: InstanceState = InstanceState.STOPPED,
    val pid: Long = -1,
    val players: Int = 0,
    val maxPlayers: Int = 0,
    val memoryUsedMb: Long = 0,
    val memoryMaxMb: Long = 0,
    val startedAt: Long = 0,
    val lastLine: String = "",
    val exitCode: Int? = null,
    /** 异常退出后收集到的崩溃现场（hs_err 摘要 + logcat 片段） */
    val crashSummary: String = "",
)
