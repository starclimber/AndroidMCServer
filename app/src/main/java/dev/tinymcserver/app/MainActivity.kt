package dev.tinymcserver.app

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.tinymcserver.app.core.storage.Settings
import dev.tinymcserver.app.ui.AppNav
import dev.tinymcserver.app.ui.theme.TinyTheme

class MainActivity : ComponentActivity() {

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响运行，仅通知不可见 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边显示：内容绘制到状态栏与手势导航条（小横条）之后，
        // 由 Compose 侧 Scaffold 的 window insets 负责避让，保证不被系统栏遮挡。
        enableEdgeToEdge()
        applyWindowAppearance()
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
        applyWindowAppearance()
    }

    /**
     * 系统栏外观：
     *  1. **状态栏保留显示**，但背景透明 —— 各屏幕的 TopAppBar 会延伸到状态栏之后，
     *     标题等内容仍在 inset 之内，因此不会被挖孔摄像头遮挡；
     *  2. 关闭系统栏的「自动对比度底衬」。
     *
     * 关于第 2 点：themes.xml 里的 android:enforceNavigationBarContrast 只对
     * targetSdk >= 29 的应用生效；本应用为了能 exec 私有目录里的 JRE 固定用
     * targetSdk = 28，系统会忽略那个 theme 属性，于是给透明导航栏上的
     * 手势导航条（小横条）垫了一层白色背景。这里改用运行时 API 强制关闭。
     */
    private fun applyWindowAppearance() {
        runCatching {
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.statusBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
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
