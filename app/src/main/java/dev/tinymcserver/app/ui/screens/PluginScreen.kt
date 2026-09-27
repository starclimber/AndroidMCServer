package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.model.ServerType
import dev.tinymcserver.app.core.plugin.PluginSearch
import dev.tinymcserver.app.ui.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) { Text("实例不存在", Modifier.padding(16.dp)); return }

    val loader = when (inst.config.type) {
        ServerType.PAPER -> "paper"
        ServerType.PURPUR -> "purpur"
        ServerType.FOLIA -> "folia"
        ServerType.VANILLA -> ""
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PluginSearch.Hit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    // 正在下载的插件 projectId；已完成的用 done 标记
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            if (inst.config.type == ServerType.VANILLA) {
                Text("Vanilla 不支持插件，请使用 Paper/Purpur/Folia。",
                    color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Text(
                "来源 Modrinth · 目标 ${inst.config.type.display} ${inst.config.mcVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("搜索插件") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    if (query.isBlank()) return@Button
                    busy = true
                    scope.launch {
                        runCatching {
                            val r = PluginSearch.search(query, inst.config.mcVersion, loader)
                            r
                        }.onSuccess { results = it }
                            .onFailure { vm.toast("搜索失败：${it.message}") }
                        busy = false
                    }
                }) { Icon(Icons.Filled.Search, contentDescription = null) }
            }
            if (busy) Text("搜索中…", style = MaterialTheme.typography.bodySmall)
            if (!busy && results.isEmpty()) {
                Text(
                    "输入关键词后点搜索。插件来自 Modrinth，安装后需重启服务端生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.projectId }) { hit ->
                    val isDownloading = downloadingId == hit.projectId
                    val isDone = doneIds.contains(hit.projectId)
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(hit.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    hit.description.take(120),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text("下载 ${hit.downloads}",
                                    style = MaterialTheme.typography.labelSmall)
                                if (isDownloading) {
                                    Text(
                                        "下载中…（可在通知栏查看进度或取消）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            when {
                                isDownloading -> CircularProgressIndicator(
                                    Modifier.size(24.dp), strokeWidth = 2.dp,
                                )
                                isDone -> Icon(
                                    Icons.Filled.Check, contentDescription = "已下载",
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
                                        if (ok) doneIds = doneIds + hit.projectId
                                    }
                                }) { Icon(Icons.Filled.Download, contentDescription = "下载") }
                            }
                        }
                    }
                }
            }
        }
    }
}
