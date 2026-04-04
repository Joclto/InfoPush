package com.infopush.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_PUSH_TOKEN = stringPreferencesKey("push_token")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_NOTIFICATION_SOUND = stringPreferencesKey("notification_sound")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: "http://10.0.2.2:8000"
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCESS_TOKEN]
    }

    val pushToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PUSH_TOKEN]
    }

    val username: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USERNAME]
    }

    val notificationSound: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_SOUND] ?: "default"
    }

    suspend fun getServerUrl(): String = serverUrl.first()
    suspend fun getAccessToken(): String? = accessToken.first()
    suspend fun getPushToken(): String? = pushToken.first()

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun saveLoginInfo(accessToken: String, pushToken: String, username: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_PUSH_TOKEN] = pushToken
            prefs[KEY_USERNAME] = username
        }
    }

    suspend fun clearLoginInfo() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_PUSH_TOKEN)
            prefs.remove(KEY_USERNAME)
        }
    }

    suspend fun setNotificationSound(soundUri: String) {
        context.dataStore.edit { it[KEY_NOTIFICATION_SOUND] = soundUri }
    }

    suspend fun isLoggedIn(): Boolean = getAccessToken() != null
}
