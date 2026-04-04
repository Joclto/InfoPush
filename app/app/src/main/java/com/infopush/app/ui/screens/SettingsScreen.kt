package com.infopush.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Notifications
import com.infopush.app.data.api.ApiClient
import com.infopush.app.data.model.PushRequest
import com.infopush.app.data.repository.MessageRepository
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.service.PushService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    messageRepo: MessageRepository,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val serverUrl by settingsRepo.serverUrl.collectAsState(initial = "")
    val pushToken by settingsRepo.pushToken.collectAsState(initial = null)
    val username by settingsRepo.username.collectAsState(initial = null)
    val notificationSound by settingsRepo.notificationSound.collectAsState(initial = "default")

    // 获取声音名称
    fun getSoundName(soundUriStr: String): String {
        return if (soundUriStr == "default") {
            "系统默认"
        } else {
            try {
                val ringtone = RingtoneManager.getRingtone(context, soundUriStr.toUri())
                ringtone?.getTitle(context) ?: "未知声音"
            } catch (_: Exception) {
                "未知声音"
            }
        }
    }

    // 声音选择器 Launcher
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        uri?.let {
            scope.launch {
                settingsRepo.setNotificationSound(uri.toString())
                Toast.makeText(context, "提示音已设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 试一试状态
    var selectedTemplate by remember { mutableStateOf(TestTemplate.TEXT) }
    var isSending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 用户信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("用户信息", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("用户名: ${username ?: "未登录"}")
                    Text("服务器: $serverUrl")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 推送 Token
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("推送令牌 (Push Token)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "使用此令牌通过 API 推送消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pushToken ?: "未获取",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            pushToken?.let { token ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("push_token", token))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 推送 API 示例
                    Text("API 调用示例:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    val apiExample = """curl -X POST "$serverUrl/push/$pushToken" \
  -H "Content-Type: application/json" \
  -d '{"title":"测试","content":"Hello!"}'"""
                    Text(
                        text = apiExample,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("api_example", apiExample))
                        Toast.makeText(context, "已复制 API 示例", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制示例")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 电池优化
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("后台保活", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "关闭电池优化可以防止系统杀掉推送服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)

                    if (isIgnoring) {
                        Text("已关闭电池优化", color = MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedButton(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        }) {
                            Text("关闭电池优化")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提示音设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("提示音设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "选择接收消息时的提示音",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "当前提示音: ${getSoundName(notificationSound)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择提示音")
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    if (notificationSound == "default") null else notificationSound.toUri()
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            }
                            soundPickerLauncher.launch(intent)
                        }) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("更改")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 播放当前声音按钮
                    if (notificationSound != "default") {
                        Button(
                            onClick = {
                                try {
                                    val ringtone = RingtoneManager.getRingtone(context, notificationSound.toUri())
                                    ringtone?.play()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法播放声音", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("试听当前提示音")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 试一试
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("试一试", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "选择模板，向自己发送一条测试推送",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 模板选择
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TestTemplate.entries.forEach { template ->
                            FilterChip(
                                selected = selectedTemplate == template,
                                onClick = {
                                    selectedTemplate = template
                                    sendResult = null
                                },
                                label = { Text(template.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 预览内容
                    Text(
                        text = selectedTemplate.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 发送按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val token = pushToken ?: return@Button
                                scope.launch {
                                    isSending = true
                                    sendResult = null
                                    try {
                                        ApiClient.setBaseUrl(serverUrl)
                                        val req = selectedTemplate.toRequest()
                                        val resp = ApiClient.getApi().pushTestMessage(token, req)
                                        sendResult = if (resp.code == 200) "发送成功" else resp.msg
                                    } catch (e: Exception) {
                                        sendResult = "发送失败: ${e.message}"
                                    } finally {
                                        isSending = false
                                    }
                                }
                            },
                            enabled = !isSending && pushToken != null
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("发送测试")
                        }

                        sendResult?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it == "发送成功") MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 退出登录
            Button(
                onClick = {
                    scope.launch {
                        // 停止推送服务
                        val stopIntent = Intent(context, PushService::class.java).apply {
                            action = PushService.ACTION_STOP
                        }
                        context.startService(stopIntent)

                        // 清理数据
                        settingsRepo.clearLoginInfo()
                        messageRepo.clearAll()
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("退出登录")
            }
        }
    }
}

enum class TestTemplate(
    val label: String,
    val preview: String,
    private val title: String,
    private val content: String,
    private val contentType: String,
    private val url: String? = null
) {
    TEXT(
        label = "文本",
        preview = "标题: 测试推送\n内容: 这是一条普通文本消息，来自 InfoPush 试一试功能。",
        title = "测试推送",
        content = "这是一条普通文本消息，来自 InfoPush 试一试功能。",
        contentType = "text"
    ),
    MARKDOWN(
        label = "Markdown",
        preview = "标题: Markdown 测试\n内容: # Hello\n**粗体** _斜体_ `代码`\n- 列表项 1\n- 列表项 2",
        title = "Markdown 测试",
        content = "# Hello InfoPush\n\n**粗体文本** 和 _斜体文本_ 以及 `内联代码`\n\n- 列表项 1\n- 列表项 2\n\n> 这是一条引用",
        contentType = "markdown"
    ),
    URL(
        label = "链接",
        preview = "标题: 带链接的消息\n内容: 点击查看详情\nURL: https://github.com",
        title = "带链接的消息",
        content = "点击通知可以打开指定网址，这是 InfoPush 的链接推送功能演示。",
        contentType = "text",
        url = "https://github.com"
    );

    fun toRequest() = PushRequest(
        title = title,
        content = content,
        content_type = contentType,
        url = url
    )
}
