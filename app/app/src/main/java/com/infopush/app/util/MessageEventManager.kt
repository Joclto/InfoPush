package com.infopush.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object MessageEventManager {
    private const val TAG = "MessageEventManager"
    private val _newMessageEvent = MutableSharedFlow<Unit>(replay = 1)
    val newMessageEvent: SharedFlow<Unit> = _newMessageEvent.asSharedFlow()

    fun notifyNewMessage() {
        val result = _newMessageEvent.tryEmit(Unit)
        Log.d(TAG, "notifyNewMessage called, emit result: $result")
    }
}