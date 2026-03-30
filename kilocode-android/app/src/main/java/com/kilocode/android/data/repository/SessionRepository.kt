package com.kilocode.android.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kilocode.android.data.api.ApiClient
import com.kilocode.android.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.sse.EventSource
import java.util.UUID

class SessionRepository(private val apiClient: ApiClient) {

    private val gson = Gson()
    private var eventSource: EventSource? = null

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _parts = MutableStateFlow<Map<String, List<Part>>>(emptyMap())
    val parts: StateFlow<Map<String, List<Part>>> = _parts.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun loadSessions() {
        try {
            _isLoading.value = true
            _error.value = null
            val response = apiClient.api.listSessions()
            if (response.isSuccessful) {
                _sessions.value = response.body() ?: emptyList()
            } else {
                _error.value = "Failed to load sessions: ${response.code()}"
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error loading sessions", e)
            _error.value = "Connection error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun createSession(directory: String): Session? {
        return try {
            _isLoading.value = true
            val response = apiClient.api.createSession(mapOf("directory" to directory))
            if (response.isSuccessful) {
                val session = response.body()
                if (session != null) {
                    _sessions.value = listOf(session) + _sessions.value
                    _currentSession.value = session
                    loadMessages(session.id)
                }
                session
            } else {
                _error.value = "Failed to create session: ${response.code()}"
                null
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error creating session", e)
            _error.value = "Connection error: ${e.message}"
            null
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun selectSession(sessionId: String) {
        try {
            val response = apiClient.api.getSession(sessionId)
            if (response.isSuccessful) {
                _currentSession.value = response.body()
                loadMessages(sessionId)
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error selecting session", e)
            _error.value = "Connection error: ${e.message}"
        }
    }

    suspend fun loadMessages(sessionId: String) {
        try {
            val response = apiClient.api.listMessages(sessionId)
            if (response.isSuccessful) {
                val msgs = response.body() ?: emptyList()
                _messages.value = msgs
                withContext(Dispatchers.IO) {
                    msgs.map { message ->
                        async { loadParts(sessionId, message.id) }
                    }.awaitAll()
                }
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error loading messages", e)
        }
    }

    private suspend fun loadParts(sessionId: String, messageId: String) {
        try {
            val response = apiClient.api.listParts(sessionId, messageId)
            if (response.isSuccessful) {
                val messageParts = response.body() ?: emptyList()
                _parts.value = _parts.value + (messageId to messageParts)
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error loading parts", e)
        }
    }

    suspend fun sendPrompt(sessionId: String, text: String): Boolean {
        return try {
            _isLoading.value = true
            val request = mapOf(
                "messageID" to generateMessageId(),
                "parts" to listOf(mapOf("type" to "text", "text" to text))
            )
            val response = apiClient.api.sendPrompt(sessionId, request)
            if (response.isSuccessful) {
                loadMessages(sessionId)
                true
            } else {
                _error.value = "Failed to send prompt: ${response.code()}"
                false
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error sending prompt", e)
            _error.value = "Connection error: ${e.message}"
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun abortSession(sessionId: String) {
        try {
            apiClient.api.abortSession(sessionId)
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error aborting session", e)
        }
    }

    suspend fun deleteSession(sessionId: String) {
        try {
            val response = apiClient.api.deleteSession(sessionId)
            if (response.isSuccessful) {
                _sessions.value = _sessions.value.filter { it.id != sessionId }
                if (_currentSession.value?.id == sessionId) {
                    _currentSession.value = null
                    _messages.value = emptyList()
                    _parts.value = emptyMap()
                }
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error deleting session", e)
        }
    }

    fun connectSse(sessionId: String) {
        disconnectSse()
        val baseUrl = apiClient.baseUrl.removeSuffix("/")
        eventSource = apiClient.createEventSource(
            "$baseUrl/session/$sessionId/events",
            object : okhttp3.sse.EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    handleSseEvent(type, data)
                }

                override fun onClosed(eventSource: EventSource) {
                    _isConnected.value = false
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?,
                ) {
                    _isConnected.value = false
                    Log.e("SessionRepo", "SSE failed: ${t?.message}")
                }
            }
        )
        _isConnected.value = true
    }

    private fun handleSseEvent(type: String?, data: String) {
        try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val event: Map<String, Any> = gson.fromJson(data, mapType)
            val properties = event["properties"] as? Map<String, Any> ?: return

            when (type) {
                "message.updated" -> {
                    val info = properties["info"] as? Map<String, Any> ?: return
                    val messageJson = gson.toJson(info)
                    val message = gson.fromJson(messageJson, Message::class.java)
                    val current = _messages.value.toMutableList()
                    val index = current.indexOfFirst { it.id == message.id }
                    if (index >= 0) {
                        current[index] = message
                    } else {
                        current.add(message)
                    }
                    _messages.value = current
                }
                "message.removed" -> {
                    val messageID = properties["messageID"] as? String ?: return
                    _messages.value = _messages.value.filter { it.id != messageID }
                }
                "part.updated" -> {
                    val partData = properties["part"] as? Map<String, Any> ?: return
                    val partJson = gson.toJson(partData)
                    val part = gson.fromJson(partJson, Part::class.java)
                    val currentParts = _parts.value.toMutableMap()
                    val messageParts = currentParts[part.messageID]?.toMutableList() ?: mutableListOf()
                    val index = messageParts.indexOfFirst { it.id == part.id }
                    if (index >= 0) {
                        messageParts[index] = part
                    } else {
                        messageParts.add(part)
                    }
                    currentParts[part.messageID] = messageParts
                    _parts.value = currentParts
                }
            }
        } catch (e: Exception) {
            Log.e("SessionRepo", "Error handling SSE event", e)
        }
    }

    fun disconnectSse() {
        eventSource?.cancel()
        eventSource = null
        _isConnected.value = false
    }

    private fun generateMessageId(): String {
        return "msg_${UUID.randomUUID()}"
    }

    fun clearError() {
        _error.value = null
    }
}
