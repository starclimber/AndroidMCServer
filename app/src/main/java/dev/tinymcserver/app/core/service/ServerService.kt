package dev.tinymcserver.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.tinymcserver.app.MainActivity
import dev.tinymcserver.app.R
import dev.tinymcserver.app.core.model.InstanceState
import dev.tinymcserver.app.core.server.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.tinymcserver.app.core.i18n.t

/** 服务端运行期间的前台服务，持有 WakeLock + WifiLock 保活 */
class ServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val CHANNEL_ID = "server_status"
        const val NOTIF_ID = 1001
        const val ACTION_STOP_ALL = "dev.tinymcserver.app.STOP_ALL"

        fun start(ctx: Context) {
            val i = Intent(ctx, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            ServerManager.stopAll()
        }
        // 前台服务类型：本应用为了能 exec 私有目录里的 JRE，targetSdk 固定为 28（< 29），
        // 系统不要求 startForeground 传类型 —— 因此也**不受 Android 15+
        // 对 dataSync / mediaProcessing 类型「24 小时内最多 6 小时」的限制**
        // （超时会回调 onTimeout()，不自行 stopSelf 就抛 RemoteServiceException）。
        //
        // 这里显式走「不传类型」的老路径，而不是按运行时读到的 targetSdk 去「自动」判断，
        // 行为完全确定，不受任何系统自动替换影响。
        startForeground(NOTIF_ID, buildNotification())
        if (loopJob == null) loopJob = scope.launch { supervise() }
        return START_STICKY
    }

    private suspend fun supervise() {
        var emptyStreak = 0
        while (true) {
            val running = ServerManager.all().filter {
                it.state.value.state == InstanceState.RUNNING ||
                    it.state.value.state == InstanceState.STARTING
            }
            if (running.isEmpty()) {
                // 宽限期：启动瞬间控制器可能还没进入 STARTING，避免被立刻停掉
                emptyStreak++
                if (emptyStreak >= 5) {
                    stopSelf()
                    return
                }
            } else {
                emptyStreak = 0
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification())
            }
            delay(2000)
        }
    }

    private fun buildNotification(): Notification {
        val running = ServerManager.all().filter {
            it.state.value.state == InstanceState.RUNNING
        }
        val title = if (running.isEmpty()) "Tiny MC Server" else t("服务端运行中 (%s)", running.size)
        val text = running.joinToString("  ·  ") { c ->
            val s = c.state.value
            val cfg = c.instance.config
            "${cfg.name} | ${cfg.type.display} ${cfg.mcVersion} | :${cfg.port} | " +
                t("玩家 %s/%s | 内存 %s/%sMB", s.players, s.maxPlayers, s.memoryUsedMb, s.memoryMaxMb)
        }.ifEmpty { t("正在初始化…") }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, t("停止全部"), stop)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notif_channel_desc) }
        nm.createNotificationChannel(ch)
    }

    private fun acquireLocks() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TinyMC::Server").apply {
            setReferenceCounted(false)
            acquire()
        }
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        // 坑（Android 14 / API 34 起，官方文档明确写了）：
        //   WIFI_MODE_FULL_HIGH_PERF 被系统「自动替换」为 WIFI_MODE_FULL_LOW_LATENCY，
        //   而 LOW_LATENCY 限制是「仅在亮屏、且应用处于前台时生效」——
        //   也就是**灭屏后 WiFi 锁直接失效**。而 Minecraft 服务端恰恰是
        //   灭屏后最需要保持联网（玩家还要连进来、区块还要同步）。
        //
        // 因此 API 34+ 主动退回 WIFI_MODE_FULL：它不随屏幕状态失效，
        // 不是「高性能」，但能真正把 WiFi 芯片从休眠里拉住。
        @Suppress("DEPRECATION")
        val wifiMode = if (Build.VERSION.SDK_INT >= 34)
            WifiManager.WIFI_MODE_FULL
        else
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(wifiMode, "TinyMC::Wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        super.onDestroy()
    }
}
