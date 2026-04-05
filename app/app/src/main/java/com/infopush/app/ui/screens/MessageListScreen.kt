package com.infopush.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import android.util.Log
import com.infopush.app.data.model.MessageEntity
import com.infopush.app.data.repository.MessageRepository
import com.infopush.app.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private fun showShortToast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

private sealed class SyncStatus {
    object Idle : SyncStatus()
    object Loading : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListScreen(
    messageRepo: MessageRepository,
    settingsRepo: SettingsRepository,
    onMessageClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val lazyMessages = messageRepo.getPagedMessages().collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    var syncStatus by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }

    suspend fun doSync(token: String) {
        syncStatus = SyncStatus.Loading
        try {
            messageRepo.syncFromServer(token)
            syncStatus = SyncStatus.Success
        } catch (e: Exception) {
            syncStatus = SyncStatus.Error(e.message ?: "未知错误")
        }
        delay(2000)
        syncStatus = SyncStatus.Idle
    }

    // Sync on first load
    LaunchedEffect(Unit) {
        val token = settingsRepo.getAccessToken() ?: return@LaunchedEffect
        doSync(token)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("InfoPush") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val token = settingsRepo.getAccessToken() ?: return@launch
                            try {
                                Log.d("MessageListScreen", "Marking all as read")
                                messageRepo.markAllAsRead(token)
                                showShortToast(context, "全部已读")
                            } catch (e: Exception) {
                                Log.e("MessageListScreen", "Mark all as read failed", e)
                                showShortToast(context, "标记失败: ${e.message}")
                            }
                        }
                    }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "全部已读")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val token = settingsRepo.getAccessToken() ?: return@launch
                                doSync(token)
                            }
                        },
                        enabled = syncStatus !is SyncStatus.Loading
                    ) {
                        if (syncStatus is SyncStatus.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 同步状态提示条
            AnimatedVisibility(
                visible = syncStatus !is SyncStatus.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val (text, color) = when (val s = syncStatus) {
                    is SyncStatus.Loading -> "正在同步..." to MaterialTheme.colorScheme.surfaceVariant
                    is SyncStatus.Success -> "同步成功" to MaterialTheme.colorScheme.surfaceVariant
                    is SyncStatus.Error -> "同步失败: ${s.message}" to MaterialTheme.colorScheme.errorContainer
                    else -> "" to MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (syncStatus) {
                            is SyncStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            when {
                lazyMessages.loadState.refresh is LoadState.Loading && lazyMessages.itemCount == 0 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                lazyMessages.itemCount == 0 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无消息",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            count = lazyMessages.itemCount,
                            key = lazyMessages.itemKey { it.id }
                        ) { index ->
                            val message = lazyMessages[index] ?: return@items
                            MessageCard(message = message, onClick = { onMessageClick(message.id) })
                        }

                        if (lazyMessages.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: MessageEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isRead)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!message.isRead) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = "未读",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!message.url.isNullOrBlank()) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = "包含链接",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.content.take(100),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatDateTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatDateTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: DateTimeParseException) {
        isoString.take(19).replace("T", " ")
    }
}
