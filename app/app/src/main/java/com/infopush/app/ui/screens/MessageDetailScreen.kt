package com.infopush.app.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.infopush.app.data.model.MessageEntity
import com.infopush.app.data.repository.MessageRepository
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.util.DeepLinkHelper

import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    messageId: String,
    messageRepo: MessageRepository,
    settingsRepo: SettingsRepository,
    onBack: () -> Unit
) {
    var message by remember { mutableStateOf<MessageEntity?>(null) }
    val context = LocalContext.current

    LaunchedEffect(messageId) {
        message = messageRepo.getMessageById(messageId)
        val token = settingsRepo.getAccessToken()
        message?.let { messageRepo.markAsRead(it.id, token) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(message?.title ?: "消息详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        val msg = message
        if (msg == null) {
            Text("加载中...", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 标题
            Text(
                text = msg.title,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = msg.createdAt.take(19).replace("T", " "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 内容
            when (msg.contentType) {
                "markdown" -> {
                    MarkdownText(
                        markdown = msg.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "html" -> {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                                loadDataWithBaseURL(null, msg.content, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    )
                }
                else -> {
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // URL 跳转按钮
            if (!msg.url.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { DeepLinkHelper.openUrl(context, msg.url!!) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("打开链接")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = msg.url!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
