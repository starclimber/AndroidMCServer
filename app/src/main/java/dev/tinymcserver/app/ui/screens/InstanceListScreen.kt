package dev.tinymcserver.app.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.model.InstanceState
import dev.tinymcserver.app.core.model.ServerInstance
import dev.tinymcserver.app.core.server.ServerManager
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.ui.AppViewModel
import java.io.File
import dev.tinymcserver.app.core.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceListScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val instances by vm.instances.collectAsState()
    var pendingDelete by remember { mutableStateOf<ServerInstance?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiny MC Server") },
                actions = {
                    IconButton(onClick = { nav.navigate("skinforge") }) {
                        Icon(Icons.Filled.Face, contentDescription = t("皮肤工坊"))
                    }
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = t("设置"))
                    }
                    IconButton(onClick = { nav.navigate("about") }) {
                        Icon(Icons.Filled.Info, contentDescription = t("关于"))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("wizard") }) {
                Icon(Icons.Filled.Add, contentDescription = t("新建实例"))
            }
        },
    ) { pad ->
        if (instances.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("还没有服务器实例"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(t("点右下角 + 创建你的第一个手机服务端"), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(instances, key = { it.id }) { inst ->
                    InstanceCard(
                        ctx = ctx,
                        inst = inst,
                        onOpen = { nav.navigate("console/${inst.id}") },
                        onEdit = { nav.navigate("wizard/${inst.id}") },
                        onFiles = { nav.navigate("files/${inst.id}") },
                        onConfig = { nav.navigate("config/${inst.id}") },
                        onDelete = { pendingDelete = inst },
                    )
                }
            }
        }
    }

    pendingDelete?.let { inst ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(t("删除实例")) },
            text = { Text(t("将删除「%s」及其世界、配置与备份，不可恢复。", inst.config.name)) },
            confirmButton = {
                TextButton(onClick = {
                    ServerManager.controllerOrNull(inst.id)?.forceKill()
                    vm.deleteInstance(inst.id)
                    pendingDelete = null
                }) { Text(t("删除")) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(t("取消")) } },
        )
    }
}

@Composable
private fun InstanceCard(
    ctx: Context,
    inst: ServerInstance,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFiles: () -> Unit,
    onConfig: () -> Unit,
    onDelete: () -> Unit,
) {
    val controller = remember(inst.id) { ServerManager.controller(ctx, inst) }
    val st by controller.state.collectAsState()
    val (label, color) = when (st.state) {
        InstanceState.RUNNING -> t("运行中") to Color(0xFF66BB6A)
        InstanceState.STARTING -> t("启动中") to Color(0xFFFFA726)
        InstanceState.STOPPING -> t("停止中") to Color(0xFFFFA726)
        InstanceState.CRASHED -> t("已崩溃") to Color(0xFFEF5350)
        InstanceState.STOPPED -> t("已停止") to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(inst.config.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${inst.config.type.display} ${inst.config.mcVersion.ifBlank { "(未选版本)" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(onClick = {}, label = { Text(label, color = color) })
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("端口 %s · JRE %s · ", inst.config.port, inst.config.jreMajor) +
                        t("%s/%s 人 · %s/%sMB", st.players, st.maxPlayers, st.memoryUsedMb, st.memoryMaxMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onOpen) { Icon(Icons.Filled.Terminal, t("控制台")) }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, t("编辑")) }
                IconButton(onClick = onFiles) { Icon(Icons.Filled.Folder, t("文件")) }
                IconButton(onClick = onConfig) { Icon(Icons.Filled.Settings, t("配置")) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, t("删除")) }
            }
        }
    }
}
