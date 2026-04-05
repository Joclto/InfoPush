package com.infopush.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.service.PushService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InfoPushApp : Application() {
    companion object {
        const val CHANNEL_PUSH = "push_messages"
        const val CHANNEL_SERVICE = "push_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startPushServiceIfLoggedIn()
    }

    private fun startPushServiceIfLoggedIn() {
        CoroutineScope(Dispatchers.IO).launch {
            val settingsRepo = SettingsRepository(this@InfoPushApp)
            if (settingsRepo.isLoggedIn()) {
                val intent = Intent(this@InfoPushApp, PushService::class.java)
                startForegroundService(intent)
            }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // 获取或创建推送消息通道
        val pushChannel = manager.getNotificationChannel(CHANNEL_PUSH)
        if (pushChannel == null) {
            // 如果通道不存在，创建新通道
            val newChannel = NotificationChannel(
                CHANNEL_PUSH,
                "消息推送",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "接收推送消息通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            manager.createNotificationChannel(newChannel)
        } else {
            // 如果通道已存在，更新声音设置
            pushChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            manager.createNotificationChannel(pushChannel)
        }

        // 创建服务通道
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "推送服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持推送服务运行"
        }
        manager.createNotificationChannel(serviceChannel)
    }
}
