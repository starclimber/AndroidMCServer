package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.core.storage.ServerProperties
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.LabeledField
import java.io.File
import dev.tinymcserver.app.core.i18n.t

private val TEXT_EXT = setOf(
    "properties", "yml", "yaml", "json", "txt", "log", "conf", "toml", "cfg", "mcmeta", "md", "sh", "ini"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) {
        Text(t("实例不存在"), Modifier.padding(16.dp)); return
    }
    val root = Paths.instanceDir(ctx, id)
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("文件 · %s", inst.config.name)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                listOf(t("服务器属性"), t("原始编辑"), t("文件浏览")).forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> PropertiesEditor(File(root, "server.properties")) { refresh++ }
                1 -> RawEditor(File(root, "server.properties")) { refresh++ }
                else -> FileBrowser(root, vm)
            }
        }
    }
}

/** 「服务器属性」页：键名显示为「中文名 · 原键」，常见键附取值提示 */
@Composable
private fun PropertiesEditor(file: File, onSaved: () -> Unit) {
    var map by remember { mutableStateOf(LinkedHashMap<String, String>()) }
    LaunchedEffect(file.path) {
        map = if (file.exists()) ServerProperties.parse(file.readText()) else LinkedHashMap()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(
            t("共 %s 项。左边中文是含义，括号里是 server.properties 里的原始键名。", map.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        map.keys.toList().forEach { k ->
            val cn = ServerProperties.displayName(k)
            val title = if (cn == k) k else "$cn（$k）"
            LabeledField(title, map[k] ?: "", { map[k] = it })
            ServerProperties.hint(k)?.let { h ->
                Text(
                    "　$h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }
        Button(
            onClick = { ServerProperties.write(file, map); onSaved() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) { Text(t("保存 server.properties")) }
    }
}

@Composable
private fun RawEditor(file: File, onSaved: () -> Unit) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(file.path) { text = if (file.exists()) file.readText() else "" }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { file.writeText(text); onSaved() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(t("保存")) }
    }
}

@Composable
private fun FileBrowser(root: File, vm: AppViewModel) {
    var rel by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<File?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val current = if (rel.isEmpty()) root else File(root, rel)
    val entries = remember(rel, tick) {
        current.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // 面包屑
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (rel.isEmpty()) t("🏠 服务端根目录") else "📁 ${rel.replace("/", " › ")}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
                if (rel.isNotEmpty()) {
                    TextButton(onClick = { rel = rel.substringBeforeLast('/', "") }) { Text(t("上一级")) }
                }
            }
        }

        val dirs = entries.count { it.isDirectory }
        val files = entries.size - dirs
        Text(
            t("共 %s 项（%s 个文件夹 · %s 个文件）", entries.size, dirs, files),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (entries.isEmpty()) {
                Text(t("（空目录）"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entries.forEachIndexed { idx, f ->
                val isDir = f.isDirectory
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (isDir) rel = f.relativeTo(root).path
                            else if (isText(f)) editing = f
                            else vm.toast(t("非文本文件，无法编辑"))
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = if (isDir) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            f.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isDir) FontWeight.Medium else FontWeight.Normal,
                        )
                        if (!isDir) {
                            Text(
                                "${humanSize(f.length())} · ${if (isText(f)) "可编辑" else "二进制"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isDir) {
                        Icon(
                            Icons.Filled.NavigateNext, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (idx < entries.lastIndex) HorizontalDivider()
            }
            tick // 触发重组
        }
    }

    editing?.let { f ->
        var text by remember(f.path) { mutableStateOf(runCatching { f.readText() }.getOrDefault("")) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(f.name, maxLines = 1) },
            text = {
                Column {
                    Text(
                        t("路径：%s · %s", f.relativeTo(root).path, humanSize(f.length())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().height(320.dp).padding(top = 6.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { f.writeText(text); editing = null; tick++; vm.toast(t("已保存")) }) {
                    Text(t("保存"))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(t("取消")) } },
        )
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
    else -> String.format("%.2f GB", bytes / 1073741824.0)
}

private fun isText(f: File): Boolean {
    val ext = f.extension.lowercase()
    return ext in TEXT_EXT && f.length() < 4L * 1024 * 1024
}
