package dev.tinymcserver.app.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 预留：开机后在用户主动打开应用时再恢复实例，此处不做自动启动。
        }
    }
}
