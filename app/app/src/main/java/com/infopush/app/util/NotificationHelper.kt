package com.infopush.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.infopush.app.InfoPushApp
import com.infopush.app.MainActivity
import com.infopush.app.data.model.MessageEntity
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    private val notificationId = AtomicInteger(1000)

    fun showMessageNotification(context: Context, message: MessageEntity) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("message_id", message.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, message.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, InfoPushApp.CHANNEL_PUSH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.title)
            .setContentText(message.content.take(100))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

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
