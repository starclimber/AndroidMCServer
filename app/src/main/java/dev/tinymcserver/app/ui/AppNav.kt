package dev.tinymcserver.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tinymcserver.app.ui.screens.AboutScreen
import dev.tinymcserver.app.ui.screens.BackupScreen
import dev.tinymcserver.app.ui.screens.ConsoleScreen
import dev.tinymcserver.app.ui.screens.CreateWizardScreen
import dev.tinymcserver.app.ui.screens.FileBrowserScreen
import dev.tinymcserver.app.ui.screens.InstanceListScreen
import dev.tinymcserver.app.ui.screens.PlayerManageScreen
import dev.tinymcserver.app.ui.screens.PluginImportScreen
import dev.tinymcserver.app.ui.screens.ServerConfigScreen
import dev.tinymcserver.app.ui.screens.SettingsScreen
import dev.tinymcserver.app.ui.screens.SkinForgeScreen
import dev.tinymcserver.app.core.i18n.t

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    val progress by vm.progress.collectAsState()

    LaunchedEffect(Unit) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }

    // 兜底：万一回退栈被弹空（例如连点返回），NavHost 会变成一片空白。
    // 记录「曾经有过目的地」，一旦目的地消失就立刻补回实例列表。
    val backEntry by nav.currentBackStackEntryAsState()
    var everHadEntry by remember { mutableStateOf(false) }
    if (backEntry != null) everHadEntry = true
    LaunchedEffect(backEntry, everHadEntry) {
        if (everHadEntry && backEntry == null) {
            runCatching { nav.navigate("list") { launchSingleTop = true } }
        }
    }

    // 外层 Scaffold 只做 Snackbar 容器：contentWindowInsets 置 0，
    // 系统栏（状态栏 / 手势条）留白交给各屏幕自己的 Scaffold 处理，
    // 否则会被叠加两次，顶部和底部出现双倍留白。
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        // 关闭页面切换动画：既是工具类应用的观感偏好，
        // 也避免「返回动画尚未结束就立刻点下一个入口」导致的导航竞态（界面空白）。
        NavHost(
            navController = nav,
            startDestination = "list",
            modifier = Modifier.padding(pad),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("list") { InstanceListScreen(vm, nav) }
            composable("wizard") { CreateWizardScreen(vm, nav, null) }
            composable(
                "wizard/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> CreateWizardScreen(vm, nav, e.arguments?.getString("id")) }
            composable(
                "console/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> ConsoleScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "files/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> FileBrowserScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "config/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> ServerConfigScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "players/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> PlayerManageScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "backups/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> BackupScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "pluginimport/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> PluginImportScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable("skinforge") { SkinForgeScreen(nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("about") { AboutScreen(nav) }
        }
    }

    progress?.let { (value, label) ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { vm.cancelDownload() }) { Text(t("取消")) }
            },
            title = { Text(t("正在处理")) },
            text = {
                Column {
                    Text(label)
                    Text("", modifier = Modifier.padding(top = 4.dp))
                    LinearProgressIndicator(
                        progress = { value.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}
