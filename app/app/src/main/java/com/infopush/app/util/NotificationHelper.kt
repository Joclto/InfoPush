package com.infopush.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.InfoPushApp
import com.infopush.app.MainActivity
import com.infopush.app.data.model.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    private val notificationId = AtomicInteger(1000)
    private const val TAG = "NotificationHelper"

    fun showMessageNotification(context: Context, message: MessageEntity) {
        // 在后台获取通知声音设置并显示通知
        CoroutineScope(Dispatchers.IO).launch {
            val settings = SettingsRepository(context)
            val soundUriStr = settings.notificationSound.first()

            // 在主线程显示通知
            CoroutineScope(Dispatchers.Main).launch {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("message_id", message.id)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, message.id.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val soundUri = if (soundUriStr == "default") {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                } else {
                    soundUriStr.toUri()
                }

                Log.d(TAG, "Notification sound: $soundUriStr")

                val builder = NotificationCompat.Builder(context, InfoPushApp.CHANNEL_PUSH)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(message.title)
                    .setContentText(message.content.take(100))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setVibrate(longArrayOf(0, 300, 200, 300))

                // 更新通知通道的声音设置
                updateNotificationChannelSound(context, soundUri)

                // 如果有 URL，添加打开链接的 action
                if (!message.url.isNullOrBlank()) {
                    val urlIntent = DeepLinkHelper.createIntent(context, message.url)
                    val urlPendingIntent = PendingIntent.getActivity(
                        context, message.id.hashCode() + 1, urlIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_send,
                        "打开链接",
                        urlPendingIntent
                    )
                }

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(notificationId.getAndIncrement(), builder.build())
            }
        }
    }

    private fun updateNotificationChannelSound(context: Context, soundUri: Uri) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(InfoPushApp.CHANNEL_PUSH)
        channel?.let {
            it.setSound(soundUri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            manager.createNotificationChannel(it)
            Log.d(TAG, "Updated notification channel sound")
        }
    }
}
