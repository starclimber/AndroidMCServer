package dev.tinymcserver.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.model.ServerType
import dev.tinymcserver.app.core.net.Http
import dev.tinymcserver.app.core.plugin.PluginImporter
import dev.tinymcserver.app.core.plugin.PluginSearch
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import dev.tinymcserver.app.core.i18n.t

/**
 * 「插件」页：与本应用插件相关的全部入口，按来源分成 4 个 Tab
 *   Tab1 本地导入 · Tab2 链接下载 · Tab3 在线搜索(Modrinth) · Tab4 已安装
 * 布局方式与「配置」页（server.properties）保持一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginImportScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) {
        Text(t("实例不存在"), Modifier.padding(16.dp))
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableStateOf(0) }

    // —— 本地 / 链接 ——
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    // —— 在线搜索 ——
    val loader = when (inst.config.type) {
        ServerType.PAPER -> "paper"
        ServerType.PURPUR -> "purpur"
        ServerType.FOLIA -> "folia"
        ServerType.VANILLA -> ""
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PluginSearch.Hit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val pluginsDir = remember(tick, id) { File(Paths.instanceDir(ctx, id), "plugins") }
    val installed = remember(tick, id) {
        pluginsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = ""
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { PluginImporter.importTo(ctx, id, uri) }
            }.onSuccess {
                status = t("已导入：%s", it)
                vm.toast(t("插件已导入：%s（重启服务端后生效）", it))
            }.onFailure {
                status = t("导入失败：%s", it.message)
            }
            busy = false
            tick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("插件")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                listOf(t("本地导入"), t("链接下载"), t("在线搜索"), t("已安装")).forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Text(
                                label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            when (tab) {
                // ---------------- Tab1 本地导入 ----------------
                0 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("从本地文件导入"))
                    FieldCard {
                        Text(
                            t("选择手机里的插件 jar（.jar），会复制到本实例的 plugins 目录。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) { Text(t("选择 jar 文件")) }
                        if (status.isNotEmpty()) {
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                // ---------------- Tab2 链接下载 ----------------
                1 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("从链接下载"))
                    FieldCard {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(t("插件直链（.jar）")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val link = url.trim()
                                if (!link.startsWith("http://") && !link.startsWith("https://")) {
                                    status = t("请输入以 http(s) 开头的链接")
                                    return@Button
                                }
                                busy = true
                                status = t("下载中…")
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val dir = File(Paths.instanceDir(ctx, id), "plugins").apply { mkdirs() }
                                            var name = link.substringAfterLast('/').substringBefore('?')
                                            if (name.isBlank()) name = "plugin"
                                            if (!name.lowercase().endsWith(".jar")) name += ".jar"
                                            val dest = uniqueFile(dir, name)
                                            Http.download(link, dest)
                                            dest.name
                                        }
                                    }
                                    busy = false
                                    tick++
                                    res.onSuccess {
                                        status = t("已下载：%s", it)
                                        vm.toast(t("插件已下载：%s（重启服务端后生效）", it))
                                    }.onFailure {
                                        status = t("下载失败：%s", it.message)
                                    }
                                }
                            },
                            enabled = !busy && url.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) { Text(if (busy) t("下载中…") else t("下载")) }
                        if (status.isNotEmpty()) {
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                // ---------------- Tab3 在线搜索 ----------------
                2 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("在线搜索安装"))
                    if (inst.config.type == ServerType.VANILLA) {
                        FieldCard {
                            Text(
                                t("Vanilla 不支持插件，请使用 Paper/Purpur/Folia。"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        FieldCard {
                            Text(
                                t("来源 Modrinth · 目标 %s %s", inst.config.type.display, inst.config.mcVersion),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = query, onValueChange = { query = it },
                                    label = { Text(t("搜索插件")) }, singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(onClick = {
                                    if (query.isBlank()) return@Button
                                    searching = true
                                    scope.launch {
                                        runCatching {
                                            PluginSearch.search(query, inst.config.mcVersion, loader)
                                        }.onSuccess { results = it; searched = true }
                                            .onFailure { vm.toast(t("搜索失败：%s", it.message)) }
                                        searching = false
                                    }
                                }) { Icon(Icons.Filled.Search, contentDescription = t("搜索")) }
                            }
                            if (searching) {
                                Text(
                                    t("搜索中…"),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            if (!searching && !searched) {
                                Text(
                                    t("输入关键词后点搜索。安装后需重启服务端生效。"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        results.forEach { hit ->
                            val isDownloading = downloadingId == hit.projectId
                            val isDone = doneIds.contains(hit.projectId)
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(hit.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            hit.description.take(110),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            t("下载 %s", hit.downloads),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    when {
                                        isDownloading -> CircularProgressIndicator(
                                            Modifier.size(22.dp), strokeWidth = 2.dp,
                                        )
                                        isDone -> Icon(
                                            Icons.Filled.Check,
                                            contentDescription = t("已下载"),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        else -> IconButton(onClick = {
                                            downloadingId = hit.projectId
                                            vm.downloadPlugin(
                                                inst = inst,
                                                projectId = hit.projectId,
                                                loader = loader,
                                                title = hit.title,
                                            ) { ok ->
                                                downloadingId = null
                                                if (ok) {
                                                    doneIds = doneIds + hit.projectId
                                                    tick++
                                                }
                                            }
                                        }) { Icon(Icons.Filled.Download, contentDescription = t("下载")) }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- Tab4 已安装 ----------------
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("已安装插件"))
                    FieldCard {
                        if (installed.isEmpty()) {
                            Text(
                                t("（暂无插件）"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        installed.forEach { f ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        humanSize(f.length()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = {
                                    runCatching { f.delete() }
                                    tick++
                                    vm.toast(t("已删除 %s", f.name))
                                }) { Text(t("删除")) }
                            }
                        }
                    }
                    Text(
                        t("提示：插件改动需要重启服务端才会生效。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

private fun uniqueFile(dir: File, name: String): File {
    val first = File(dir, name)
    if (!first.exists()) return first
    val base = name.removeSuffix(".jar")
    var i = 1
    var f = File(dir, "$base-$i.jar")
    while (f.exists()) {
        i++
        f = File(dir, "$base-$i.jar")
    }
    return f
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1048576.0)
}
