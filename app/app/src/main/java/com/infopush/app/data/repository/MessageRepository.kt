package com.infopush.app.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.infopush.app.data.api.ApiClient
import com.infopush.app.data.db.AppDatabase
import com.infopush.app.data.model.MessageEntity
import com.infopush.app.data.model.toEntity
import android.util.Log
import kotlinx.coroutines.flow.Flow

class MessageRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).messageDao()

    fun getAllMessages(): Flow<List<MessageEntity>> = dao.getAllMessages()

    fun getPagedMessages(): Flow<PagingData<MessageEntity>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false)
    ) {
        dao.getPagedMessages()
    }.flow

    suspend fun insertMessage(message: MessageEntity) = dao.insertMessage(message)

    suspend fun getMessageById(id: String): MessageEntity? = dao.getMessageById(id)

    suspend fun markAsRead(id: String, accessToken: String?) {
        dao.markAsRead(id)
        if (accessToken != null) {
            try {
                Log.d("MessageRepository", "Marking message $id as read on server")
                ApiClient.getApi().markRead("Bearer $accessToken", id)
                Log.d("MessageRepository", "Mark message $id as read on server success")
            } catch (e: Exception) {
                Log.e("MessageRepository", "Mark message $id as read on server failed", e)
                // 本地已更新，服务端同步失败不影响用户体验
            }
        }
    }

    suspend fun markAllAsRead(accessToken: String?) {
        dao.markAllAsRead()
        if (accessToken != null) {
            try {
                Log.d("MessageRepository", "Marking all as read on server")
                ApiClient.getApi().markAllRead("Bearer $accessToken")
                Log.d("MessageRepository", "Mark all as read on server success")
            } catch (e: Exception) {
                Log.e("MessageRepository", "Mark all as read on server failed", e)
                // 本地已更新，服务端同步失败不影响用户体验
            }
        }
    }

    suspend fun syncFromServer(accessToken: String, page: Int = 1, pageSize: Int = 50) {
        val response = ApiClient.getApi().getMessages("Bearer $accessToken", page, pageSize)
        val entities = response.messages.map { msg ->
            val local = dao.getMessageById(msg.id)
            // 若本地已标记已读，保留本地状态，不被服务端覆盖
            msg.toEntity().copy(isRead = local?.isRead ?: msg.is_read)
        }
        dao.insertMessages(entities)
    }

    suspend fun clearAll() = dao.deleteAll()
}

