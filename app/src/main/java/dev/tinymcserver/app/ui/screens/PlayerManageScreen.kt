package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.player.PlayerManager
import dev.tinymcserver.app.core.server.ServerManager
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.core.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerManageScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) { Text(t("实例不存在"), Modifier.padding(16.dp)); return }
    val controller = remember(id) { ServerManager.controller(ctx, inst) }

    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    val tabs = listOf(t("白名单"), "OP", t("封禁"))
    val list = when (tab) {
        0 -> PlayerManager.whitelist(ctx, id)
        1 -> PlayerManager.ops(ctx, id)
        else -> PlayerManager.banned(ctx, id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("玩家管理")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                t("名单变更通过控制台命令下发（需服务端运行）。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("玩家名")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) return@Button
                    val cmd = when (tab) {
                        0 -> PlayerManager.commandForWhitelistAdd(n)
                        1 -> PlayerManager.commandForOp(n)
                        else -> PlayerManager.commandForBan(n)
                    }
                    controller.sendCommand(cmd)
                    name = ""; tick++
                }) { Text(t("添加")) }
            }
            TextButtonRefresh(tick) { tick++ }

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(list) { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.name, Modifier.weight(1f))
                        IconButton(onClick = {
                            val cmd = when (tab) {
                                0 -> PlayerManager.commandForWhitelistRemove(p.name)
                                1 -> PlayerManager.commandForDeop(p.name)
                                else -> PlayerManager.commandForPardon(p.name)
                            }
                            controller.sendCommand(cmd)
                            tick++
                        }) { Icon(Icons.Filled.Delete, contentDescription = t("移除")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonRefresh(tick: Int, onRefresh: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onRefresh,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) { Text(t("刷新名单")) }
}
