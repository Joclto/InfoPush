package com.infopush.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infopush.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loggedIn = SettingsRepository(context).isLoggedIn()
                if (loggedIn) {
                    val serviceIntent = Intent(context, PushService::class.java)
                    context.startForegroundService(serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
