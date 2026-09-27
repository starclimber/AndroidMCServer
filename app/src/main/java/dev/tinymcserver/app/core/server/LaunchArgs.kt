package dev.tinymcserver.app.core.server

import dev.tinymcserver.app.core.model.GcPreset
import dev.tinymcserver.app.core.model.InstanceConfig
import dev.tinymcserver.app.core.model.ServerType
import java.io.File

/**
 * 组装 `java -jar server.jar nogui` 的完整命令。
 *
 * 手机端（Android/bionic）与桌面 Linux 差异较大，这里刻意保持参数克制：
 *  - 不用 `-server`（aarch64 只有 Server VM，写了也是空操作）
 *  - 不写 hsperfdata（Android 上 /tmp 常不可写）
 *  - 强制把 JVM 崩溃报告落到实例目录，方便 App 直接读出来给用户看
 *  - GC 策略交给「预设」，出问题时用户可一键切换，无需重新安装
 */
object LaunchArgs {

    fun build(jreHome: File, cfg: InstanceConfig, instanceDir: File, jar: File): List<String> {
        val java = File(jreHome, "bin/java").absolutePath
        val tmp = File(instanceDir, "tmp").apply { runCatching { mkdirs() } }
        val args = mutableListOf<String>()

        args += java
        args += "-Xms${cfg.xmsMb}M"
        args += "-Xmx${cfg.xmxMb}M"

        // ---------- Android 平台适配 ----------
        args += "-Dfile.encoding=UTF-8"
        args += "-Djava.awt.headless=true"
        args += "-Djava.io.tmpdir=${tmp.absolutePath}"
        // FCL 同款：把 user.home 指到可写目录（Android 上默认是 /data，不可写）
        args += "-Duser.home=${instanceDir.absolutePath}"
        // 关闭共享内存的性能计数器文件（/tmp/hsperfdata_*），Android 上既不可写也没意义
        args += "-XX:+PerfDisableSharedMem"
        // 关键：崩溃报告直接写进实例目录，否则默认落在工作目录/未知位置
        args += "-XX:ErrorFile=${File(instanceDir, "hs_err_%p.log").absolutePath}"

        // ---------- GC 预设 ----------
        when (cfg.preset) {
            GcPreset.PERFORMANCE -> {
                args += "-XX:+UseG1GC"
                args += "-XX:+ParallelRefProcEnabled"
                args += "-XX:MaxGCPauseMillis=200"
                args += "-XX:+UnlockExperimentalVMOptions"
                args += "-XX:+DisableExplicitGC"
                args += "-XX:G1NewSizePercent=30"
                args += "-XX:G1MaxNewSizePercent=40"
                args += "-XX:G1HeapRegionSize=8M"
                args += "-XX:G1ReservePercent=20"
                args += "-XX:G1HeapWastePercent=5"
                args += "-XX:InitiatingHeapOccupancyPercent=15"
                args += "-XX:SurvivorRatio=32"
                args += "-XX:MaxTenuringThreshold=1"
            }
            GcPreset.COMPAT -> {
                // 最保守的一档：单线程 GC + 关闭压缩指针 + 关闭显式 GC
                args += "-XX:+UseSerialGC"
                args += "-XX:-UseCompressedOops"
                args += "-XX:+DisableExplicitGC"
            }
            GcPreset.CUSTOM -> {
                // 完全交给额外参数
            }
            GcPreset.CONSERVATIVE -> {
                args += "-XX:+UseG1GC"
                args += "-XX:MaxGCPauseMillis=200"
                args += "-XX:+DisableExplicitGC"
            }
        }

        // Folia：用 ActiveProcessorCount 限制可用核数（Folia 依此确定区域线程数），
        // 避免在手机上全核拉满导致过热降频。
        // ---------- 编译（JIT）预设 ----------
        // Android 上这套 JRE 的 JIT 会触发原生层 abort，因此留出从激进到保守的档位
        args += cfg.jit.args

        if (cfg.type == ServerType.FOLIA && cfg.foliaRegionThreads > 0) {
            args += "-XX:ActiveProcessorCount=${cfg.foliaRegionThreads}"
        }

        if (cfg.extraJvmArgs.isNotBlank()) {
            args += cfg.extraJvmArgs.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        }

        args += "-jar"
        args += jar.absolutePath
        args += "nogui"
        return args
    }

    /** 给 UI 展示用的可读命令行（缩短 JRE 路径） */
    fun preview(cfg: InstanceConfig, jreHome: File, instanceDir: File): String {
        val jar = File(instanceDir, "server.jar")
        return build(jreHome, cfg, instanceDir, jar).joinToString(" ")
    }
}
