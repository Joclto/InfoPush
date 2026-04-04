package com.infopush.app.data.api

import com.infopush.app.data.model.LoginRequest
import com.infopush.app.data.model.MessageListResponse
import com.infopush.app.data.model.PushRequest
import com.infopush.app.data.model.PushResponse
import com.infopush.app.data.model.RegisterRequest
import com.infopush.app.data.model.TokenResponse
import com.infopush.app.data.model.UserInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @GET("api/auth/me")
    suspend fun getMe(@Header("Authorization") token: String): UserInfo

    @GET("api/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): MessageListResponse

    @PUT("api/messages/read-all")
    suspend fun markAllRead(
        @Header("Authorization") token: String
    )

    @PUT("api/messages/{id}/read")
    suspend fun markRead(
        @Header("Authorization") token: String,
        @Path("id") id: String
    )

    @POST("push/{token}")
    suspend fun pushTestMessage(
        @Path("token") token: String,
        @Body request: PushRequest
    ): PushResponse
}
