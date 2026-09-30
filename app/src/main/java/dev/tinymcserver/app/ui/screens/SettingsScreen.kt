package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.tinymcserver.app.core.runtime.JreManager
import dev.tinymcserver.app.core.storage.Settings
import dev.tinymcserver.app.core.util.BatteryUtil
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.LabeledField
import dev.tinymcserver.app.ui.components.SectionTitle
import kotlinx.coroutines.launch
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.i18n.AppLanguage
import dev.tinymcserver.app.core.i18n.I18n
import dev.tinymcserver.app.MainActivity
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    Settings.attach(ctx)
    val scope = rememberCoroutineScope()
    var refreshFlag by remember { mutableIntStateOf(0) }
    var rcon by remember { mutableStateOf(Settings.getRconPassword(ctx)) }
    var mirror by remember { mutableStateOf(Settings.mirrorBase) }
    var ignoringBattery by remember { mutableStateOf(BatteryUtil.isIgnoring(ctx)) }
    var hideRecents by remember { mutableStateOf(Settings.hideFromRecents) }

    LaunchedEffect(refreshFlag) { ignoringBattery = BatteryUtil.isIgnoring(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("设置")) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
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
            SectionTitle(t("通用"))
            FieldCard {
                Text(t("语言"), style = MaterialTheme.typography.titleSmall)
                I18n.revision
                AppLanguage.entries.forEach { lang ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = I18n.current == lang,
                            onClick = { I18n.setLanguage(ctx, lang) },
                        )
                        Text(lang.displayName)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("从最近任务中隐藏"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            t("开启后，本应用不出现在系统「最近任务」列表中。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = hideRecents,
                        onCheckedChange = { v ->
                            hideRecents = v
                            Settings.hideFromRecents = v
                            (ctx as? MainActivity)?.applyRecentsVisibility()
                        },
                    )
                }
            }

            SectionTitle(t("JRE 管理"))
            FieldCard {
                Text(
                    t("内置 JRE 打包在 APK 中，首次使用解压到私有目录。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val bundled = vm.bundledJres()
                if (bundled.isEmpty()) {
                    Text(t("未检测到内置 JRE 归档。"), color = MaterialTheme.colorScheme.error)
                }
                bundled.forEach { major ->
                    val installed = vm.jreInstalled(major)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("JRE $major", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (installed)
                                    t("已安装 · %s · ", vm.jreVersionLabel(major)) +
                                        "${"%.0f".format(vm.jreSize(major) / 1048576.0)} MB"
                                else t("未解压"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (installed) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    JreManager.uninstall(ctx, major)
                                    refreshFlag++
                                }
                            }) { Text(t("重装")) }
                        } else {
                            Button(onClick = { vm.installJre(major) { refreshFlag++ } }) {
                                Text(t("解压安装"))
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { vm.installJre(21) { refreshFlag++ } },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text(t("一键安装 JRE 21（推荐）")) }
            }

            SectionTitle(t("保活"))
            FieldCard {
                Text(
                    if (ignoringBattery) t("已加入电池优化白名单 ✅")
                    else t("尚未加入电池优化白名单，后台可能被系统杀掉 ❌"),
                    color = if (ignoringBattery) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = {
                        runCatching { ctx.startActivity(BatteryUtil.requestIgnoreIntent(ctx)) }
                            .onFailure {
                                runCatching { ctx.startActivity(BatteryUtil.openSettingsIntent()) }
                            }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text(t("关闭电池优化 / 加入白名单")) }
                Text(
                    t("服务端运行时会启动前台服务，并持有 PARTIAL_WAKE_LOCK 与 WIFI_LOCK。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(t("下载源与镜像"))
            FieldCard {
                Text(t("默认源：PaperMC / Purpur / Folia / Mojang 官方。可填写镜像前缀。"))
                LabeledField(t("镜像前缀"), mirror, { mirror = it })
                Button(
                    onClick = { Settings.mirrorBase = mirror.trim(); vm.toast(t("已保存镜像设置")) },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text(t("保存")) }
            }

            SectionTitle(t("存储"))
            FieldCard {
                Text(t("实例默认存储在应用私有目录，无需存储权限。"))
                OutlinedButton(onClick = { vm.toast(t("请到实例「文件」页导出到外置存储")) }) {
                    Text(t("迁移到 SAF 外置存储（导出）"))
                }
            }

            SectionTitle("RCON")
            FieldCard {
                Text(t("RCON 密码使用 Android Keystore 加密存储。"))
                LabeledField(t("RCON 密码"), rcon, { rcon = it })
                Button(
                    onClick = { Settings.setRconPassword(ctx, rcon); vm.toast(t("已加密保存")) },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text(t("保存密码")) }
            }

            SectionTitle(t("关于"))
            FieldCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigate("about") }) { Text(t("关于本应用")) }
                }
            }
            Text(" ", Modifier.padding(12.dp))
        }
    }
}
