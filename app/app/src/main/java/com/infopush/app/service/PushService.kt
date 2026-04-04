package com.infopush.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.infopush.app.InfoPushApp
import com.infopush.app.MainActivity
import com.infopush.app.data.api.ApiClient
import com.infopush.app.data.db.AppDatabase
import com.infopush.app.data.model.WsMessage
import com.infopush.app.data.model.toEntity
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.util.NotificationHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class PushService : Service() {

    companion object {
        private const val TAG = "PushService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.infopush.START"
        const val ACTION_STOP = "com.infopush.STOP"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = 1000L // ms
    private val maxReconnectDelay = 60000L

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val wsMessageAdapter = moshi.adapter(WsMessage::class.java)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (webSocket == null && reconnectJob?.isActive != true) {
            scope.launch { connect() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connect() {
        val settings = SettingsRepository(applicationContext)
        val pushToken = settings.getPushToken() ?: return
        val serverUrl = settings.getServerUrl()

        ApiClient.setBaseUrl(serverUrl)
        val wsUrl = ApiClient.getWsUrl(pushToken)

        Log.i(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                reconnectDelay = 1000L // reset
            }

            override fun onMessage(ws: WebSocket, text: String) {
                scope.launch {
                    handleMessage(text)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
                if (code == 4001) {
                    reconnectJob?.cancel() // invalid token — do not reconnect
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                if (code != 4001) { // 4001 = invalid token, don't reconnect
                    scheduleReconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private suspend fun handleMessage(text: String) {
        try {
            val msg = wsMessageAdapter.fromJson(text) ?: return

            when (msg.type) {
                "ping" -> {
                    webSocket?.send("""{"type":"pong"}""")
                }
                "message" -> {
                    val data = msg.data ?: return
                    val entity = data.toEntity()

                    // 存入本地数据库
                    val dao = AppDatabase.getInstance(applicationContext).messageDao()
                    dao.insertMessage(entity)

                    // 显示通知
                    NotificationHelper.showMessageNotification(applicationContext, entity)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handle message error: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.i(TAG, "Reconnecting in ${reconnectDelay}ms...")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(maxReconnectDelay)
            connect()
        }
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Service stopped")
        webSocket = null
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, InfoPushApp.CHANNEL_SERVICE)
            .setContentTitle("InfoPush")
            .setContentText("消息推送服务运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
