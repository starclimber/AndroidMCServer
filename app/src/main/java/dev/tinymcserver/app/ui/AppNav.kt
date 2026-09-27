package dev.tinymcserver.app.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tinymcserver.app.ui.screens.AboutScreen
import dev.tinymcserver.app.ui.screens.BackupScreen
import dev.tinymcserver.app.ui.screens.ConsoleScreen
import dev.tinymcserver.app.ui.screens.CreateWizardScreen
import dev.tinymcserver.app.ui.screens.FileEditorScreen
import dev.tinymcserver.app.ui.screens.InstanceListScreen
import dev.tinymcserver.app.ui.screens.PlayerManageScreen
import dev.tinymcserver.app.ui.screens.PluginScreen
import dev.tinymcserver.app.ui.screens.SettingsScreen
import dev.tinymcserver.app.ui.screens.SkinForgeScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    val progress by vm.progress.collectAsState()

    LaunchedEffect(Unit) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        NavHost(
            navController = nav,
            startDestination = "list",
            modifier = Modifier.padding(pad),
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
            ) { e -> FileEditorScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "players/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> PlayerManageScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "backups/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> BackupScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
            composable(
                "plugins/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e -> PluginScreen(vm, nav, e.arguments?.getString("id").orEmpty()) }
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
                TextButton(onClick = { vm.cancelDownload() }) { Text("取消") }
            },
            title = { Text("正在处理") },
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
