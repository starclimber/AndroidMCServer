package dev.tinymcserver.app.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.tinymcserver.app.MainActivity
import dev.tinymcserver.app.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import dev.tinymcserver.app.core.i18n.t

/**
 * 下载 / 解压进度通知：带进度条与「取消」按钮。
 *
 * **支持多任务并发**：每个任务分配独立的通知 ID 与取消标记，互不干扰
 * （此前用固定 ID 与全局标记，同时下两个插件会互相顶掉通知、取消一个会连带另一个）。
 *
 * 取消链路：通知的「取消」→ DownloadCancelReceiver（广播，携带 taskId）
 * → 标记该 taskId 已取消 → 下载循环轮询 [isCancelled] 后抛 CancellationException 终止。
 */
object DownloadNotifier {

    const val CHANNEL_ID = "download_progress"
    const val ACTION_CANCEL = "dev.tinymcserver.app.DOWNLOAD_CANCEL"
    const val EXTRA_TASK_ID = "task_id"

    private val counter = AtomicInteger(2000)
    private val cancelledTasks = ConcurrentHashMap.newKeySet<Int>()
    private val activeTasks = ConcurrentHashMap.newKeySet<Int>()
    private val lastTaskId = AtomicInteger(0)

    /** 开始一个任务，返回其 taskId（用于进度上报、取消判断与结束） */
    fun start(ctx: Context, title: String): Int {
        val taskId = counter.incrementAndGet()
        cancelledTasks.remove(taskId)
        activeTasks.add(taskId)
        lastTaskId.set(taskId)
        ensureChannel(ctx)
        post(ctx, taskId, title, t("准备中…"), 0, true)
        return taskId
    }

    /** 由 DownloadCancelReceiver 调用：只取消指定任务 */
    fun requestCancel(taskId: Int) {
        if (taskId > 0) cancelledTasks.add(taskId)
    }

    /** 取消最近启动的任务（界面上只有一个进度弹窗，对应最近的任务） */
    fun requestCancelLatest() {
        requestCancel(lastTaskId.get())
    }

    /** 指定任务是否已被取消（下载循环轮询它） */
    fun isCancelled(taskId: Int): Boolean = cancelledTasks.contains(taskId)

    fun progress(ctx: Context, taskId: Int, title: String, fraction: Float, text: String) {
        if (isCancelled(taskId) || !activeTasks.contains(taskId)) return
        post(ctx, taskId, title, text, (fraction * 100).toInt().coerceIn(0, 100), false)
    }

    /** 结束指定任务（成功 / 失败 / 取消都调用） */
    fun finish(ctx: Context, taskId: Int) {
        activeTasks.remove(taskId)
        cancelledTasks.remove(taskId)
        runCatching { NotificationManagerCompat.from(ctx).cancel(taskId) }
    }

    /** 退出 App 前清理所有下载通知（可选） */
    fun finishAll(ctx: Context) {
        activeTasks.forEach { id -> runCatching { NotificationManagerCompat.from(ctx).cancel(id) } }
        activeTasks.clear()
        cancelledTasks.clear()
    }

    private fun post(
        ctx: Context,
        taskId: Int,
        title: String,
        text: String,
        pct: Int,
        indeterminate: Boolean,
    ) {
        if (!canNotify(ctx)) return
        runCatching {
            val open = PendingIntent.getActivity(
                ctx, taskId, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val cancel = PendingIntent.getBroadcast(
                ctx, taskId,
                Intent(ctx, DownloadCancelReceiver::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_TASK_ID, taskId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_server)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(open)
                .setProgress(100, pct, indeterminate)
                .addAction(0, t("取消"), cancel)
                .build()
            NotificationManagerCompat.from(ctx).notify(taskId, n)
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, t("下载进度"), NotificationManager.IMPORTANCE_LOW)
                        .apply { description = t("服务端 / 插件的下载与 JRE 解压进度") },
                )
            }
        }
    }

    private fun canNotify(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
}
