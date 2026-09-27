package dev.tinymcserver.app.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收下载通知里的「取消」动作（携带 taskId，只取消对应任务） */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadNotifier.ACTION_CANCEL) {
            DownloadNotifier.requestCancel(intent.getIntExtra(DownloadNotifier.EXTRA_TASK_ID, -1))
        }
    }
}
