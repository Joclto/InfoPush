package com.infopush.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.infopush.app.data.api.ApiClient
import com.infopush.app.data.repository.MessageRepository
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.service.PushService
import com.infopush.app.ui.navigation.NavGraph
import com.infopush.app.ui.theme.InfoPushTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var messageRepo: MessageRepository
    private var isLoggedIn by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, we proceed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepo = SettingsRepository(applicationContext)
        messageRepo = MessageRepository(applicationContext)

        // 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 检查登录状态
        lifecycleScope.launch {
            isLoggedIn = settingsRepo.isLoggedIn()
            if (isLoggedIn) {
                val serverUrl = settingsRepo.getServerUrl()
                ApiClient.setBaseUrl(serverUrl)
                requestBatteryOptimizationExemption()
                startPushService()
            }
        }

        setContent {
            InfoPushTheme {
                NavGraph(
                    isLoggedIn = isLoggedIn,
                    settingsRepo = settingsRepo,
                    messageRepo = messageRepo,
                    onLoginSuccess = {
                        isLoggedIn = true
                        requestBatteryOptimizationExemption()
                        startPushService()
                    },
                    onLogout = {
                        isLoggedIn = false
                    }
                )
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun startPushService() {
        val intent = Intent(this, PushService::class.java)
        startForegroundService(intent)
    }
}
