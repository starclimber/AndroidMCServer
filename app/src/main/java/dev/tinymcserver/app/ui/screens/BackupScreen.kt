package dev.tinymcserver.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.backup.BackupManager
import dev.tinymcserver.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import dev.tinymcserver.app.core.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) { Text(t("实例不存在"), Modifier.padding(16.dp)); return }

    var tick by remember { mutableIntStateOf(0) }
    var exportTarget by remember { mutableStateOf<File?>(null) }
    val backups = remember(tick) { BackupManager.list(ctx, id) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val src = exportTarget
        if (uri != null && src != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            src.inputStream().use { it.copyTo(out) }
                        }
                    }
                    vm.toast(t("已导出到所选位置"))
                }.onFailure { vm.toast(t("导出失败：%s", it.message)) }
            }
        }
        exportTarget = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("备份")) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement =
                androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                BackupManager.backup(ctx, id, worldOnly = false) { p, m ->
                                    vm.toast(m)
                                }
                                tick++; vm.toast(t("全量备份完成"))
                            }.onFailure { vm.toast(t("备份失败：%s", it.message)) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(t("全量备份")) }
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                BackupManager.backup(ctx, id, worldOnly = true)
                                tick++; vm.toast(t("世界备份完成"))
                            }.onFailure { vm.toast(t("备份失败：%s", it.message)) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(t("仅世界")) }
            }
            Text(
                t("备份保存在实例目录 backups/ 下，可导出到外置存储。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(backups, key = { it.path }) { zip ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${zip.name}\n${"%.1f".format(zip.length() / 1048576.0)} MB",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        BackupManager.restore(ctx, id, zip)
                                        vm.toast(t("已从备份恢复"))
                                    }.onFailure { vm.toast(t("恢复失败：%s", it.message)) }
                                }
                            }) { Icon(Icons.Filled.Restore, contentDescription = t("恢复")) }
                            IconButton(onClick = {
                                exportTarget = zip
                                exportLauncher.launch(zip.name)
                            }) { Icon(Icons.Filled.Download, contentDescription = t("导出")) }
                            IconButton(onClick = {
                                BackupManager.delete(zip); tick++
                            }) { Icon(Icons.Filled.Delete, contentDescription = t("删除")) }
                        }
                    }
                }
            }
        }
    }
}
