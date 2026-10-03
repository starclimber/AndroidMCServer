package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.model.InstanceConfig
import dev.tinymcserver.app.core.model.ServerInstance
import dev.tinymcserver.app.core.model.ServerType
import dev.tinymcserver.app.core.server.VersionUtil
import dev.tinymcserver.app.core.storage.EulaManager
import dev.tinymcserver.app.core.storage.InstanceStore
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.IntStepper
import dev.tinymcserver.app.ui.components.LabeledField
import dev.tinymcserver.app.ui.components.SectionTitle
import dev.tinymcserver.app.core.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWizardScreen(vm: AppViewModel, nav: NavController, editId: String?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val existing = remember(editId) { vm.instances.value.firstOrNull { it.id == editId } }
    val base: InstanceConfig = existing?.config ?: InstanceStore.defaultConfig()

    var name by remember { mutableStateOf(base.name) }
    var type by remember { mutableStateOf(base.type) }
    var mcVersion by remember { mutableStateOf(base.mcVersion) }
    var jreManual by remember { mutableStateOf(base.jreMajor) }
    var jreAutoOverride by remember { mutableStateOf(false) }
    var xms by remember { mutableStateOf(base.xmsMb) }
    var xmx by remember { mutableStateOf(base.xmxMb) }
    var port by remember { mutableStateOf(base.port) }
    var online by remember { mutableStateOf(base.onlineMode) }
    var difficulty by remember { mutableStateOf(base.difficulty) }
    var viewDistance by remember { mutableStateOf(base.viewDistance) }
    var simDistance by remember { mutableStateOf(base.simulationDistance) }
    var maxPlayers by remember { mutableStateOf(base.maxPlayers) }
    var whitelist by remember { mutableStateOf(base.whitelist) }
    var motd by remember { mutableStateOf(base.motd) }
    var autoRestart by remember { mutableStateOf(base.autoRestart) }
    var foliaThreads by remember { mutableStateOf(base.foliaRegionThreads) }

    var versions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingVersions by remember { mutableStateOf(false) }
    var versionError by remember { mutableStateOf<String?>(null) }
    var versionReload by remember { mutableStateOf(0) }
    var showEula by remember { mutableStateOf(false) }
    var eulaAccepted by remember { mutableStateOf(editId != null && EulaManager.isAccepted(Paths.instanceDir(ctx, editId))) }

    val autoJre = if (mcVersion.isBlank()) base.jreMajor else (VersionUtil.recommendJre(mcVersion)?.major ?: 0)
    val jreSupported = VersionUtil.isSupported(mcVersion)
    val effectiveJre = (if (jreAutoOverride) jreManual else autoJre).takeIf { it > 0 } ?: 21

    // 版本列表加载：失败时记录原因供界面展示；「重试」会强制绕过缓存重新请求。
    LaunchedEffect(type, versionReload) {
        loadingVersions = true
        versionError = null
        val res = runCatching { vm.loadVersions(type, force = versionReload > 0) }
        versions = res.getOrDefault(emptyList())
        versionError = res.exceptionOrNull()?.message
        loadingVersions = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) t("新建服务器") else t("编辑服务器")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SectionTitle(t("基础"))
            FieldCard {
                LabeledField(t("名称"), name, { name = it })
                DropField(t("服务端类型"), type.display, ServerType.entries.map { it.display }) { sel ->
                    type = ServerType.entries.first { it.display == sel }
                    mcVersion = ""
                }
                DropField(
                    t("MC 版本") + if (loadingVersions) t("（加载中…）") else t("（%s 个）", versions.size),
                    mcVersion.ifBlank { t("点击选择") },
                    versions.take(200),
                ) { mcVersion = it }
                if (!loadingVersions && versions.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (versionError ?: t("加载失败")) + " ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { versionReload++ }) { Text(t("重试")) }
                    }
                }
                DropField(
                    "JRE" + when {
                        !jreAutoOverride && autoJre > 0 -> t("（自动匹配：JRE %s）", autoJre)
                        !jreSupported -> t("（该版本无可用 JRE）")
                        else -> ""
                    },
                    "JRE $effectiveJre",
                    listOf(t("自动匹配 (JRE %s)", effectiveJre), "JRE 17", "JRE 21", "JRE 25"),
                ) { sel ->
                    if (sel.startsWith(t("自动"))) {
                        jreAutoOverride = false
                    } else {
                        jreAutoOverride = true
                        jreManual = sel.removePrefix("JRE ").trim().toIntOrNull() ?: effectiveJre
                    }
                }
                if (!jreSupported) {
                    Text(
                        t("⚠️ MC 1.16 及更早需要 Java 8，本版本已不再内置 JRE 8（Android 上没有干净的独立来源）；请选择 1.17 及以上版本。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SectionTitle(t("性能与内存"))
            FieldCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        xms = 1024; xmx = 2048; viewDistance = 6; simDistance = 4
                    }) { Text(t("低配")) }
                    OutlinedButton(onClick = {
                        xms = 2048; xmx = 4096; viewDistance = 8; simDistance = 6
                    }) { Text(t("均衡")) }
                    OutlinedButton(onClick = {
                        xms = 4096; xmx = 6144; viewDistance = 10; simDistance = 8
                    }) { Text(t("高性能")) }
                }
                IntStepper(t("初始内存 Xms"), xms, { xms = it }, 512, 16384, 512, " MB")
                IntStepper(t("最大内存 Xmx"), xmx, { xmx = it }, 512, 16384, 512, " MB")
                Text(
                    t("建议 2GB 起步、4–6GB 舒适；上限受设备可用 RAM 限制。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IntStepper(t("视距"), viewDistance, { viewDistance = it }, 3, 16)
                IntStepper(t("模拟距离"), simDistance, { simDistance = it }, 3, 12)
            }

            SectionTitle(t("网络与规则"))
            FieldCard {
                IntStepper(t("端口"), port, { port = it }, 1024, 65535)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("正版验证 online-mode"), Modifier.weight(1f))
                    Switch(checked = online, onCheckedChange = { online = it })
                }
                if (!online) {
                    Text(
                        t("关闭后为离线模式，仅建议局域网自用；公网会被冒充。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                DropField(t("难度"), difficulty, listOf("peaceful", "easy", "normal", "hard")) {
                    difficulty = it
                }
                IntStepper(t("最大玩家"), maxPlayers, { maxPlayers = it }, 1, 200)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("开启白名单"), Modifier.weight(1f))
                    Switch(checked = whitelist, onCheckedChange = { whitelist = it })
                }
                LabeledField("MOTD", motd, { motd = it })
            }

            SectionTitle(t("运行"))
            FieldCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("崩溃自动重启"), Modifier.weight(1f))
                    Switch(checked = autoRestart, onCheckedChange = { autoRestart = it })
                }
                if (type == ServerType.FOLIA) {
                    IntStepper(
                        t("Folia 区域线程上限"), foliaThreads, { foliaThreads = it }, -1, 16,
                        display = { if (it < 0) t("自动") else "$it" },
                    )
                    Text(
                        t("Folia 不支持多数 Bukkit/Spigot 插件，安装前请确认兼容性。区域线程按手机核心数保守设置，") +
                            t("不要全核拉满。「自动」= 由服务端按 CPU 核心数决定。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SectionTitle(t("合规"))
            FieldCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = eulaAccepted, onCheckedChange = { eulaAccepted = it })
                    Text(t("我已阅读并同意 Minecraft EULA"), Modifier.weight(1f))
                }
                TextButton(onClick = { showEula = true }) { Text(t("查看 EULA 摘要")) }
            }

            Button(
                enabled = jreSupported,
                onClick = {
                    if (!eulaAccepted) {
                        vm.toast(t("请先同意 Mojang EULA"))
                        return@Button
                    }
                    val cfg = InstanceConfig(
                        name = name.ifBlank { t("未命名服务器") },
                        typeKey = type.key,
                        mcVersion = mcVersion,
                        jreMajor = effectiveJre,
                        xmsMb = xms, xmxMb = xmx, port = port,
                        onlineMode = online, difficulty = difficulty,
                        viewDistance = viewDistance, simulationDistance = simDistance,
                        maxPlayers = maxPlayers, whitelist = whitelist, motd = motd,
                        autoRestart = autoRestart, foliaRegionThreads = foliaThreads,
                    )
                    if (existing == null) {
                        val inst = ServerInstance(
                            id = InstanceStore.newId(),
                            config = cfg,
                            createdAt = System.currentTimeMillis(),
                        )
                        vm.createInstance(inst, acceptEula = true)
                        // 创建即下载：先解压匹配的 JRE，再从官方源下载服务端 jar
                        vm.prepareInstance(inst) { ok ->
                            vm.toast(
                                if (ok) t("创建完成，可以启动了")
                                else t("实例已创建，下载未完成，可到控制台重试")
                            )
                            nav.navigate("console/${inst.id}") {
                                popUpTo("list") { inclusive = false }
                            }
                        }
                        return@Button
                    } else {
                        vm.saveInstance(existing.copy(config = cfg))
                        if (eulaAccepted) EulaManager.accept(Paths.instanceDir(ctx, existing.id))
                        vm.toast(t("已保存"))
                    }
                    if (nav.previousBackStackEntry != null) nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) { Text(if (editId == null) t("创建并下载服务端") else t("保存")) }
        }
    }

    if (showEula) {
        AlertDialog(
            onDismissRequest = { showEula = false },
            title = { Text("Minecraft EULA") },
            text = {
                Text(
                    t("使用 Minecraft 服务端软件需同意 Mojang 的最终用户许可协议（EULA）。") +
                        t("本应用不内置任何 Mojang 二进制，仅作为下载与启动器；所有服务端 jar 均从官方或授权源获取。") +
                        t("详情见 https://aka.ms/MinecraftEULA")
                )
            },
            confirmButton = { TextButton(onClick = { showEula = false }) { Text(t("知道了")) } },
        )
    }
}

@Composable
private fun DropField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(value, Modifier.weight(1f), maxLines = 1,
                    fontWeight = FontWeight.Normal)
                Text("▾")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { onSelect(opt); expanded = false },
                    )
                }
            }
        }
    }
}
