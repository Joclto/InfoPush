package com.infopush.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.infopush.app.InfoPushApp
import com.infopush.app.MainActivity
import com.infopush.app.data.api.ApiClient
import com.infopush.app.data.db.AppDatabase
import com.infopush.app.data.model.WsMessage
import com.infopush.app.data.model.toEntity
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.util.MessageEventManager
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
        const val ACTION_STOP = "com.infopush.STOP"

        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val wsMessageAdapter = moshi.adapter(WsMessage::class.java)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = 1000L
    private val maxReconnectDelay = 60000L
    private var isConnecting = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createServiceNotification())
        registerNetworkCallback()
        acquireWakeLock()
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        releaseWakeLock()
        disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "InfoPush::WebSocket"
            ).apply {
                acquire(10 * 60 * 1000L) // 10分钟超时，会自动续期
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                if (webSocket == null && !isConnecting) {
                    Log.i(TAG, "Network restored, reconnecting...")
                    scope.launch { connect() }
                }
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
            }
        }
        
        cm.registerNetworkCallback(networkRequest, networkCallback!!)
        Log.d(TAG, "Network callback registered")
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(it)
            Log.d(TAG, "Network callback unregistered")
        }
        networkCallback = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, webSocket=$webSocket, isConnecting=$isConnecting")
        when (intent?.action) {
            ACTION_STOP -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // 只在未连接且未正在连接时启动连接
        if (webSocket == null && !isConnecting) {
            scope.launch { connect() }
        }
        return START_STICKY
    }

    private suspend fun connect() {
        if (isConnecting || webSocket != null) {
            Log.d(TAG, "Already connected or connecting, skip")
            return
        }
        
        isConnecting = true
        try {
            val settings = SettingsRepository(applicationContext)
            val pushToken = settings.getPushToken() ?: return
            val serverUrl = settings.getServerUrl()

            ApiClient.setBaseUrl(serverUrl)
            val wsUrl = ApiClient.getWsUrl(pushToken)

            Log.i(TAG, "Connecting to WebSocket: $wsUrl")

            val request = Request.Builder().url(wsUrl).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket connected")
                    isConnecting = false
                    reconnectDelay = 1000L
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "WebSocket message received: ${text.take(100)}...")
                    scope.launch {
                        handleMessage(text)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closing: $code $reason")
                    webSocket.close(1000, null)
                    if (code == 4001) {
                        reconnectJob?.cancel()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed: $code $reason")
                    isConnecting = false
                    this@PushService.webSocket = null
                    if (code != 4001) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    isConnecting = false
                    this@PushService.webSocket = null
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Connect error: ${e.message}")
            isConnecting = false
        }
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
                    Log.d(TAG, "Processing message: id=${data.id}, title=${data.title}")
                    val entity = data.toEntity()

                    // 存入本地数据库
                    val dao = AppDatabase.getInstance(applicationContext).messageDao()
                    dao.insertMessage(entity)
                    Log.d(TAG, "Message saved to database: ${entity.id}")

                    // 通知UI刷新
                    MessageEventManager.notifyNewMessage()
                    Log.d(TAG, "New message event emitted")

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
            Log.w(TAG, "Attempting to reconnect in ${reconnectDelay}ms...")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(maxReconnectDelay)
            connect()
        }
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        isConnecting = false
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
