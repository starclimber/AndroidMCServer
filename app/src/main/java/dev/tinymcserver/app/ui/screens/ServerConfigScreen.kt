package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.core.storage.ServerProperties
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.LabeledField
import java.io.File
import java.util.LinkedHashMap
import dev.tinymcserver.app.core.i18n.t

/**
 * 「配置」页：专门编辑 server.properties。
 *   Tab 1「服务器属性」—— 键名本地化 + 逐项编辑；
 *   Tab 2「原始编辑」  —— 直接改原始文本。
 * 文件浏览已独立到「文件」页，这里不再混放。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) {
        Text(t("实例不存在"), Modifier.padding(16.dp))
        return
    }
    val root = Paths.instanceDir(ctx, id)
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("配置 · %s", inst.config.name)) },
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
                listOf(t("服务器属性"), t("原始编辑")).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> PropertiesEditor(File(root, "server.properties")) { refresh++ }
                else -> RawEditor(File(root, "server.properties")) { refresh++ }
            }
        }
    }
}

/**
 * 「服务器属性」页。
 *
 * 键名显示规则：本地化名称 + （原始键名）。
 * 只有当本地化名称含非 ASCII 字符（中文 / 法文 / 西文 / 俄文）时才附原始键名 ——
 * 英文界面的名称本身已是纯 ASCII 英文，再挂一个英文键名纯属重复。
 */
@Composable
private fun PropertiesEditor(file: File, onSaved: () -> Unit) {
    var map by remember { mutableStateOf(LinkedHashMap<String, String>()) }
    LaunchedEffect(file.path) {
        map = if (file.exists()) ServerProperties.parse(file.readText()) else LinkedHashMap()
    }
    // 是否需要在括号里附原始键名（按实际显示名判断，而不是按语言硬编码）
    fun needsRawKey(key: String): Boolean {
        val cn = ServerProperties.displayName(key)
        return cn != key && cn.any { it.code > 0x7F }
    }
    val showRawKey = map.keys.any { needsRawKey(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(
            if (showRawKey) {
                t("共 %s 项。左边中文是含义，括号里是 server.properties 里的原始键名。", map.size)
            } else {
                t("共 %s 项。", map.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        map.keys.toList().forEach { k ->
            val title = if (needsRawKey(k)) ServerProperties.displayName(k) + "（" + k + "）"
            else ServerProperties.displayName(k)
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
