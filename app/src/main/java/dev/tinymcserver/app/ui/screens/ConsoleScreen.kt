package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.model.GcPreset
import dev.tinymcserver.app.core.model.InstanceState
import dev.tinymcserver.app.core.model.JitPreset
import dev.tinymcserver.app.core.net.NetUtil
import dev.tinymcserver.app.core.runtime.JreSelfTest
import dev.tinymcserver.app.core.server.LaunchArgs
import dev.tinymcserver.app.core.server.LogParser
import dev.tinymcserver.app.core.server.ServerManager
import dev.tinymcserver.app.core.service.ServerService
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 控制台。布局铁律（用户要求）：
 *   日志是「可滚动的列表」，占据中间全部剩余空间；
 *   命令输入 + 功能按钮组成控制栏，**钉死在屏幕最底部**，
 *   无论日志多少都不会被顶出屏幕。
 *
 * 实现注意：`Modifier.weight(1f)` 只对 Column 的**直接子节点**生效。
 * 日志外面套了 SelectionContainer，所以 weight 必须写在 SelectionContainer 上，
 * 不能写在里面的 LazyColumn 上（写错就会导致日志吃掉整屏、把控制栏顶出去）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("实例不存在") }
        return
    }
    val controller = remember(id) { ServerManager.controller(ctx, inst) }
    val st by controller.state.collectAsState()
    val log by controller.log.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var showDigest by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var showArgs by remember { mutableStateOf(false) }
    var selfTestText by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var seriesText by remember { mutableStateOf<String?>(null) }
    var seriesProg by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val jarFile = remember(tick, id) { File(Paths.instanceDir(ctx, id), inst.jarFile) }
    val jarReady = jarFile.exists()
    val jreReady = remember(tick, id) { vm.jreInstalled(inst.config.jreMajor) }

    // 只有用户本来就停在底部时才自动跟随，避免翻看历史时被强行拽走
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(log.size) {
        if (log.isNotEmpty() && atBottom) listState.scrollToItem(log.size - 1)
    }
    LaunchedEffect(Unit) { vm.refresh() }

    val running = st.state == InstanceState.RUNNING || st.state == InstanceState.STARTING
    val ready = jarReady && jreReady
    val ip = NetUtil.localIpv4()
    val crashed = st.exitCode != null && st.exitCode != 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(inst.config.name) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (running) {
                            controller.requestStop()
                        } else if (!ready) {
                            vm.toast("请先完成准备：安装 JRE 与下载服务端")
                        } else {
                            ServerService.start(ctx)
                            controller.start()
                        }
                    }) {
                        Icon(
                            if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Text(if (running) "停止" else "启动")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            // ---------- 顶部：状态 + 告警（固定） ----------
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(
                    "状态：${st.state.name}   端口 ${inst.config.port}   " +
                        "玩家 ${st.players}/${st.maxPlayers}   内存 ${st.memoryUsedMb}/${st.memoryMaxMb}MB",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "局域网连接：${ip ?: "未连接"} : ${inst.config.port}   ·   " +
                        "${inst.config.type.display} ${inst.config.mcVersion}   ·   JRE ${inst.config.jreMajor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (seriesProg.isNotEmpty()) {
                    Text(
                        seriesProg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            if (crashed) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "上次启动异常退出（返回码 ${st.exitCode}）",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "点「诊断」看崩溃现场、点「自检」判断 JVM 能否在这台设备上起来。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                        )
                    }
                }
            }

            if (!ready) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("开始前需要准备两样东西", style = MaterialTheme.typography.titleSmall)
                        ReadyRow(
                            ok = jreReady,
                            text = if (jreReady) "JRE ${inst.config.jreMajor} 已就绪"
                            else "JRE ${inst.config.jreMajor} 未解压",
                            action = "安装 JRE",
                            onClick = { vm.installJre(inst.config.jreMajor) { tick++ } },
                        )
                        ReadyRow(
                            ok = jarReady,
                            text = if (jarReady) "服务端 ${inst.jarFile} 已就绪"
                            else "服务端未下载（${inst.config.type.display} ${inst.config.mcVersion}）",
                            action = "下载服务端",
                            onClick = { vm.installJar(inst) { tick++ } },
                        )
                        Button(
                            onClick = { vm.prepareInstance(inst) { tick++ } },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("一键准备（JRE + 服务端）") }
                    }
                }
            }

            // ---------- 中部：日志区（weight 挂在 SelectionContainer 上！） ----------
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0F12))
                        .padding(6.dp),
                ) {
                    items(log) { line ->
                        Text(
                            line,
                            color = logColor(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ---------- 底部：控制栏（固定在屏幕最底部） ----------
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!atBottom) {
                            AssistChip(
                                onClick = { scope.launch { listState.scrollToItem(log.size - 1) } },
                                label = { Text("↓ 最新") },
                            )
                        }
                        listOf("stop", "save-all", "reload confirm", "whitelist list",
                            "list", "spark tps", "say hello").forEach { c ->
                            AssistChip(onClick = { controller.sendCommand(c) }, label = { Text(c) })
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { nav.navigate("files/$id") }) { Text("文件") }
                        OutlinedButton(onClick = { nav.navigate("players/$id") }) { Text("玩家") }
                        OutlinedButton(onClick = { nav.navigate("backups/$id") }) { Text("备份") }
                        OutlinedButton(onClick = { nav.navigate("plugins/$id") }) { Text("插件") }
                        OutlinedButton(onClick = { showArgs = true }) { Text("参数") }
                        OutlinedButton(onClick = { showDiag = true }) { Text("诊断") }
                        OutlinedButton(
                            onClick = {
                                testing = true
                                scope.launch {
                                    selfTestText = withContext(Dispatchers.IO) {
                                        runCatching {
                                            JreSelfTest.runAll(
                                                ctx, inst.config.jreMajor,
                                                inst.config.xmsMb, inst.config.xmxMb,
                                                inst.config.mcVersion,
                                            )
                                        }.getOrElse { "自检失败: ${it.message}" }
                                    }
                                    testing = false
                                }
                            },
                        ) { Text(if (testing) "自检中…" else "自检") }
                        OutlinedButton(
                            enabled = seriesProg.isEmpty(),
                            onClick = {
                                seriesProg = "准备深诊…"
                                scope.launch {
                                    val report = withContext(Dispatchers.IO) {
                                        runCatching {
                                            JreSelfTest.runSeries(
                                                ctx, inst.config.jreMajor, inst.config.mcVersion,
                                            ) { msg -> seriesProg = msg }
                                        }.getOrElse { "深诊失败: ${it.message}" }
                                    }
                                    seriesProg = ""
                                    seriesText = report
                                }
                            },
                        ) { Text("深诊") }
                        OutlinedButton(onClick = { showDigest = true }) { Text("错误摘要") }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入命令，如 op Steve") },
                            singleLine = true,
                        )
                        IconButton(onClick = {
                            if (input.isNotBlank()) {
                                controller.sendCommand(input.trim())
                                input = ""
                            }
                        }) { Icon(Icons.Filled.Send, contentDescription = "发送") }
                    }
                }
            }
        }
    }

    if (showDigest) {
        AlertDialog(
            onDismissRequest = { showDigest = false },
            title = { Text("错误摘要") },
            text = {
                SelectionContainer {
                    Text(
                        LogParser.errorDigest(log),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showDigest = false }) { Text("关闭") } },
        )
    }

    selfTestText?.let { text ->
        AlertDialog(
            onDismissRequest = { selfTestText = null },
            title = { Text("JRE 自检结果") },
            text = {
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    vm.toast("已复制自检结果")
                }) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { selfTestText = null }) { Text("关闭") } },
        )
    }

    seriesText?.let { text ->
        AlertDialog(
            onDismissRequest = { seriesText = null },
            title = { Text("深诊结果") },
            text = {
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    vm.toast("已复制深诊结果")
                }) { Text("复制全部") }
            },
            dismissButton = { TextButton(onClick = { seriesText = null }) { Text("关闭") } },
        )
    }

    if (showDiag) {
        val tail = log.takeLast(80).joinToString("\n")
        val body = buildString {
            append(st.crashSummary.ifBlank {
                "暂无崩溃记录（只有进程异常退出时才会自动生成）。\n\n"
            })
            if (tail.isNotBlank()) {
                append("\n=== 控制台最后 80 行 ===\n").append(tail)
            }
            if (st.crashSummary.isBlank() && tail.isBlank()) {
                append("\n把这里的内容复制发我，就能定位到具体原因。")
            }
        }
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = { Text("崩溃诊断") },
            text = {
                SelectionContainer {
                    Text(
                        body,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(body))
                    vm.toast("已复制诊断信息")
                }) { Text("复制全部") }
            },
            dismissButton = { TextButton(onClick = { showDiag = false }) { Text("关闭") } },
        )
    }

    if (showArgs) {
        ArgsDialog(inst, vm, ctx, onClose = { showArgs = false; tick++ })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArgsDialog(
    inst: dev.tinymcserver.app.core.model.ServerInstance,
    vm: AppViewModel,
    ctx: android.content.Context,
    onClose: () -> Unit,
) {
    val cfg = inst.config
    var preset by remember { mutableStateOf(cfg.preset) }
    var extra by remember { mutableStateOf(cfg.extraJvmArgs) }
    var xms by remember { mutableStateOf(cfg.xmsMb.toString()) }
    var xmx by remember { mutableStateOf(cfg.xmxMb.toString()) }
    var jit by remember { mutableStateOf(cfg.jit) }
    var autoRestart by remember { mutableStateOf(cfg.autoRestart) }

    val dir = Paths.instanceDir(ctx, inst.id)
    val jreHome = Paths.jreHome(ctx, cfg.jreMajor)
    val preview = remember(preset, jit, extra, xms, xmx) {
        runCatching {
            LaunchArgs.preview(
                cfg.copy(
                    gcPreset = preset.key,
                    jitPreset = jit.key,
                    extraJvmArgs = extra,
                    xmsMb = xms.toIntOrNull() ?: cfg.xmsMb,
                    xmxMb = xmx.toIntOrNull() ?: cfg.xmxMb,
                ),
                jreHome, dir,
            ).replace(jreHome.absolutePath, "…/jre${cfg.jreMajor}")
        }.getOrDefault("")
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("启动参数") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("GC / JVM 预设", style = MaterialTheme.typography.titleSmall)
                GcPreset.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = preset == p, onClick = { preset = p })
                        Column(Modifier.weight(1f)) {
                            Text(p.display, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                p.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "编译（JIT）预设 · 崩溃时从这里往下换",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                JitPreset.entries.forEach { j ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = jit == j, onClick = { jit = j })
                        Column(Modifier.weight(1f)) {
                            Text(j.display, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                j.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = xms,
                        onValueChange = { s -> xms = s.filter { it.isDigit() } },
                        label = { Text("Xms (MB)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = xmx,
                        onValueChange = { s -> xmx = s.filter { it.isDigit() } },
                        label = { Text("Xmx (MB)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("额外 JVM 参数（空格分隔）") },
                    placeholder = { Text("-XX:+UseSerialGC -XX:-UseCompressedOops") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoRestart, onCheckedChange = { autoRestart = it })
                    Spacer(Modifier.width(8.dp))
                    Text("崩溃后自动重启（最多 5 次）", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(10.dp))
                Text("实际命令行", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text(
                        preview.ifBlank { "（无法预览）" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveInstance(
                    inst.copy(
                        config = cfg.copy(
                            gcPreset = preset.key,
                            jitPreset = jit.key,
                            extraJvmArgs = extra.trim(),
                            xmsMb = xms.toIntOrNull() ?: cfg.xmsMb,
                            xmxMb = xmx.toIntOrNull() ?: cfg.xmxMb,
                            autoRestart = autoRestart,
                        )
                    )
                )
                vm.toast("已保存，下次启动生效")
                onClose()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
}

@Composable
private fun ReadyRow(ok: Boolean, text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (ok) "✅ " else "⏳ ") + text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        if (!ok) OutlinedButton(onClick = onClick) { Text(action) }
    }
}

private fun logColor(line: String): Color = when (LogParser.severity(line)) {
    LogParser.Severity.ERROR -> Color(0xFFFF6B6B)
    LogParser.Severity.WARN -> Color(0xFFFFD166)
    LogParser.Severity.INFO -> Color(0xFFB0BEC5)
    LogParser.Severity.PLAIN -> Color(0xFF90A4AE)
}
