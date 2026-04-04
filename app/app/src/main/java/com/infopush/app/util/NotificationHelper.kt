package com.infopush.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
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

                // 总是使用系统默认通知声音
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                Log.d(TAG, "Notification sound setting: '$soundUriStr'")
                Log.d(TAG, "Original sound URI: $soundUriStr")
                Log.d(TAG, "Converted sound URI: $soundUri")

                // 获取通知管理器
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // 获取或创建通知通道
                val channel = manager.getNotificationChannel(InfoPushApp.CHANNEL_PUSH)
              if (channel == null) {
                  // 如果通道不存在，创建新通道
                  val newChannel = NotificationChannel(
                      InfoPushApp.CHANNEL_PUSH,
                      "消息推送",
                      NotificationManager.IMPORTANCE_HIGH
                  ).apply {
                      description = "接收推送消息通知"
                      enableVibration(true)
                      vibrationPattern = longArrayOf(0, 300, 200, 300)
                  }
                  manager.createNotificationChannel(newChannel)
              }

              val builder = NotificationCompat.Builder(context, InfoPushApp.CHANNEL_PUSH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(message.title)
                .setContentText(message.content.take(100))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 300, 200, 300))
                .setSound(soundUri)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)

            Log.d(TAG, "Notification built with sound: $soundUri")

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

                manager.notify(notificationId.getAndIncrement(), builder.build())
            }
        }
    }

  }
