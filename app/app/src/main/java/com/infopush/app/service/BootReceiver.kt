package com.infopush.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.infopush.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        const val ACTION_RESTART = "com.infopush.RESTART_SERVICE"
        const val ACTION_KEEP_ALIVE = "com.infopush.KEEP_ALIVE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Received action: ${intent?.action}")
        
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_RESTART,
            ACTION_KEEP_ALIVE,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val loggedIn = SettingsRepository(context).isLoggedIn()
                        if (loggedIn) {
                            Log.d(TAG, "Starting PushService")
                            val serviceIntent = Intent(context, PushService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting service: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}