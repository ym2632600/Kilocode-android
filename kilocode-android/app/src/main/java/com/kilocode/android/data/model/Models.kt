package com.kilocode.android.data.model

import com.google.gson.annotations.SerializedName

data class Session(
    val id: String,
    val directory: String,
    val title: String,
    val time: SessionTime,
    val parentID: String? = null,
    val version: String? = null,
    val pinned: Boolean = false,
)

data class SessionTime(
    val created: Long,
    val updated: Long,
)

data class Message(
    val id: String,
    val sessionID: String,
    val role: String,
    val time: MessageTime,
    val model: ModelInfo? = null,
    val agent: String? = null,
    val error: MessageError? = null,
    val parentID: String? = null,
    val modelID: String? = null,
    val providerID: String? = null,
    val mode: String? = null,
    val cost: Double = 0.0,
    val tokens: TokenUsage? = null,
)

data class MessageTime(
    val created: Long,
    val completed: Long? = null,
)

data class ModelInfo(
    val providerID: String,
    val modelID: String,
)

data class TokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: CacheUsage? = null,
)

data class CacheUsage(
    val read: Long = 0,
    val write: Long = 0,
)

data class MessageError(
    val name: String,
    val data: Map<String, Any>? = null,
)

data class Part(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val state: ToolState? = null,
    val callID: String? = null,
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
)

data class ToolState(
    val status: String,
    val input: Map<String, Any>? = null,
    val output: String? = null,
    val error: String? = null,
    val title: String? = null,
    val metadata: Map<String, Any>? = null,
    val time: ToolTime? = null,
)

data class ToolTime(
    val start: Long,
    val end: Long? = null,
)

data class Provider(
    val id: String,
    val name: String,
    val models: Map<String, Model> = emptyMap(),
)

data class Model(
    val id: String,
    val name: String,
    val releaseDate: String? = null,
    val attachment: Boolean = false,
    val reasoning: Boolean = false,
    val temperature: Boolean = true,
    val toolCall: Boolean = true,
    val cost: ModelCost? = null,
    val limit: ModelLimit? = null,
)

data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheRead: Double = 0.0,
    val cacheWrite: Double = 0.0,
)

data class ModelLimit(
    val context: Long = 0,
    val output: Long = 0,
)

data class Project(
    val id: String,
    val name: String,
    val path: String,
)

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode>? = null,
)

data class Config(
    val providers: Map<String, ConfigProvider> = emptyMap(),
    val models: List<String> = emptyList(),
)

data class ConfigProvider(
    val id: String,
    val name: String,
    val apiKey: String? = null,
)

data class SessionStatus(
    val sessionID: String,
    val status: String,
)

data class PromptRequest(
    val messageID: String,
    val parts: List<PromptPart>,
)

data class PromptPart(
    val type: String,
    val text: String? = null,
)

data class ServerEvent(
    val type: String,
    val properties: Map<String, Any>? = null,
)

data class McpServer(
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)
