package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.runtime.JreManager
import dev.tinymcserver.app.core.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavController) {
    val ctx = LocalContext.current

    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    val versionCode = remember {
        val pi = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        pi?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
            ?: 0L
    }
    val bundled = remember {
        JreManager.bundledMajors(ctx).joinToString(" / ") { "JRE $it" }.ifBlank { t("（无）") }
    }
    val installed = remember {
        JreManager.BUNDLED.filter { JreManager.isInstalled(ctx, it) }
            .joinToString(" / ") { "JRE $it" }.ifBlank { t("（尚未解压）") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("关于")) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Text("Android MC Server", style = MaterialTheme.typography.headlineSmall)
            Text(
                t("纯手机 Minecraft 服务端启动器 · v%s（%s）", versionName, versionCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section(t("它能做什么"))
            Bullet(t("不做客户端渲染、不集成 Termux、不要求 root。"))
            Bullet(t("一键创建并启动 Vanilla / Paper / Purpur / Folia 服务端。"))
            Bullet(t("控制台实时日志、命令输入、内存/玩家/TPS 状态。"))
            Bullet(t("server.properties 可视化编辑、插件搜索下载、世界备份/导出。"))
            Bullet(t("前台服务 + WakeLock/WifiLock 保活，通知栏显示运行状态。"))

            Section(t("运行原理"))
            Bullet(t("内置 Android(bionic) 版 OpenJDK，首次使用时从 APK 解压到应用私有目录。"))
            Bullet(t("通过 ProcessBuilder 以 headless 方式执行「java -jar server.jar nogui」。"))
            Bullet(t("JRE 8/17/21/25 按 MC 版本自动匹配；本版本内置：%s。", bundled))
            Bullet(t("已解压：%s。", installed))

            Section(t("版本与 JRE 对应"))
            Bullet(t("MC 26.x 及以后 → JRE 25"))
            Bullet("MC 1.20.5 ~ 1.21.x → JRE 21")
            Bullet("MC 1.17 ~ 1.20.4 → JRE 17")
            Bullet(t("MC 1.16 及更早 → 需要 Java 8，本版本不再内置（现代服务端均要求 1.17+）"))

            Section(t("说明"))
            Bullet(t("JRE 来自 Termux 官方仓库的 OpenJDK 二进制包，按需裁剪后打包。"))
            Bullet(t("不内置任何 Mojang / Minecraft 二进制或游戏资源，仅提供下载与启动。"))
            Bullet(t("服务端 jar 均从官方或授权源获取，使用须遵守 Minecraft EULA。"))
            Bullet(t("内置 OpenJDK 遵循 GPLv2 + Classpath Exception；完整来源与修改说明见项目 NOTICE。"))

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                t("本应用为第三方工具，与 Mojang、Microsoft 无关联。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Bullet(text: String) {
    Text(
        "• $text",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
