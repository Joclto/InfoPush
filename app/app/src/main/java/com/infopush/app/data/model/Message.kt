package com.infopush.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val contentType: String, // text, markdown, html
    val url: String?,
    val isRead: Boolean = false,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val push_token: String
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val id: String,
    val username: String,
    val push_token: String,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class MessageResponse(
    val id: String,
    val title: String,
    val content: String,
    val content_type: String,
    val url: String?,
    val is_read: Boolean,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class MessageListResponse(
    val total: Int,
    val page: Int,
    val page_size: Int,
    val messages: List<MessageResponse>
)

@JsonClass(generateAdapter = true)
data class PushRequest(
    val title: String,
    val content: String,
    val content_type: String = "text",
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class PushResponse(
    val code: Int,
    val msg: String,
    val message_id: String? = null,
    val online_devices: Int = 0
)

@JsonClass(generateAdapter = true)
data class WsMessage(
    val type: String,
    val data: WsMessageData? = null,
    val time: String? = null
)

@JsonClass(generateAdapter = true)
data class WsMessageData(
    val id: String,
    val title: String,
    val content: String,
    val content_type: String,
    val url: String?,
    val created_at: String
)

fun MessageResponse.toEntity() = MessageEntity(
    id = id,
    title = title,
    content = content,
    contentType = content_type,
    url = url,
    isRead = is_read,
    createdAt = created_at
)

fun WsMessageData.toEntity() = MessageEntity(
    id = id,
    title = title,
    content = content,
    contentType = content_type,
    url = url,
    isRead = false,
    createdAt = created_at
)
