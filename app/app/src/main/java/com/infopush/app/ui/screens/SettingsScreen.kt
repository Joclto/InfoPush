package com.infopush.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.core.net.toUri
import com.infopush.app.data.api.ApiClient
import com.infopush.app.data.model.PushRequest
import com.infopush.app.data.repository.MessageRepository
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.service.PushService
import kotlinx.coroutines.launch

// 显示 Toast 消息的辅助函数
private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

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

    // 试一试状态
    var selectedTemplate by remember { mutableStateOf(TestTemplate.TEXT) }
    var isSending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }

    // 可编辑的标题和内容
    var editTitle by remember { mutableStateOf(selectedTemplate.title) }
    var editContent by remember { mutableStateOf(selectedTemplate.content) }
    var editUrl by remember { mutableStateOf(selectedTemplate.url ?: "") }

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
                                showToast(context, "已复制")
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
                        showToast(context, "已复制 API 示例")
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

                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)

                    if (isIgnoring) {
                        Text("已关闭电池优化", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            "关闭电池优化可以防止系统杀掉推送服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        }) {
                            Text("关闭电池优化")
                        }
                    }

                    // OPPO/一加等国产手机额外设置提示
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "部分手机（如OPPO/一加/小米/华为）需要额外设置：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• 设置 → 应用管理 → 找到本应用 → 允许自启动\n" +
                        "• 设置 → 应用管理 → 找到本应用 → 允许后台活动\n" +
                        "• 设置 → 电池 → 找到本应用 → 允许后台运行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        context.startActivity(intent)
                    }) {
                        Text("打开应用设置")
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
                        "Android 8.0+ 需要在系统设置中更改通知声音",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开系统通知设置")
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
                                    editTitle = template.title
                                    editContent = template.content
                                    editUrl = template.url ?: ""
                                    sendResult = null
                                },
                                label = { Text(template.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Spacer(modifier = Modifier.height(8.dp))

                    // 可编辑的标题
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 可编辑的内容
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("内容") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )

                    // 如果是链接模板，显示可编辑的链接
                    if (selectedTemplate == TestTemplate.URL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editUrl,
                            onValueChange = { editUrl = it },
                            label = { Text("链接（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

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
                                        val req = selectedTemplate.toRequest().copy(
                                            title = editTitle.ifEmpty { selectedTemplate.title },
                                            content = editContent.ifEmpty { selectedTemplate.content },
                                            url = if (selectedTemplate == TestTemplate.URL) editUrl else null
                                        )
                                        val resp = ApiClient.getApi().pushTestMessage(token, req)
                                        sendResult = if (resp.code == 200) "发送成功" else resp.msg
                                    } catch (e: Exception) {
                                        sendResult = "发送失败: ${e.message}"
                                    } finally {
                                        isSending = false
                                    }
                                }
                            },
                            enabled = !isSending && pushToken != null && (editTitle.isNotBlank() || editContent.isNotBlank())
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
    val title: String,
    val content: String,
    val contentType: String,
    val url: String? = null
) {
    TEXT(
        label = "文本",
        title = "测试推送",
        content = "这是一条普通文本消息，来自 InfoPush 试一试功能。",
        contentType = "text"
    ),
    MARKDOWN(
        label = "Markdown",
        title = "Markdown 测试",
        content = "# Hello InfoPush\n\n**粗体文本** 和 _斜体文本_ 以及 `内联代码`\n\n- 列表项 1\n- 列表项 2\n\n> 这是一条引用",
        contentType = "markdown"
    ),
    URL(
        label = "链接",
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
