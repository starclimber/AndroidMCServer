package dev.tinymcserver.app

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.tinymcserver.app.core.storage.Settings
import dev.tinymcserver.app.ui.AppNav
import dev.tinymcserver.app.ui.theme.TinyTheme

class MainActivity : ComponentActivity() {

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响运行，仅通知不可见 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.attach(this)
        requestNotifPermissionIfNeeded()
        setContent {
            TinyTheme {
                AppNav()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRecentsVisibility()
    }

    /**
     * 按设置动态控制本任务是否出现在系统「最近任务」列表中。
     * 使用 ActivityManager.AppTask#setExcludeFromRecents（API 21+），可在运行时随时开关。
     */
    fun applyRecentsVisibility() {
        runCatching {
            val am = getSystemService(ActivityManager::class.java) ?: return
            val hide = Settings.hideFromRecents
            am.appTasks.firstOrNull()?.setExcludeFromRecents(hide)
        }
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
