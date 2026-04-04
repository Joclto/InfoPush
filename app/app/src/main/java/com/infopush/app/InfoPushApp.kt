package com.infopush.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class InfoPushApp : Application() {
    companion object {
        const val CHANNEL_PUSH = "push_messages"
        const val CHANNEL_SERVICE = "push_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val pushChannel = NotificationChannel(
            CHANNEL_PUSH,
            "消息推送",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "接收推送消息通知"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "推送服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持推送服务运行"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(pushChannel)
        manager.createNotificationChannel(serviceChannel)
    }
}
