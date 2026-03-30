package com.kilocode.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilocode.android.data.api.ApiClient
import com.kilocode.android.data.repository.SessionRepository
import com.kilocode.android.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    serverUrl: String,
    onSessionClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val apiClient = remember { ApiClient.getInstance(serverUrl) }
    val repository = remember { SessionRepository(apiClient) }
    val scope = rememberCoroutineScope()

    val sessions by repository.sessions.collectAsState()
    val isLoading by repository.isLoading.collectAsState()
    val error by repository.error.collectAsState()

    LaunchedEffect(Unit) {
        repository.loadSessions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kilo Code")
                        StatusChip(
                            text = if (error == null) "Connected" else "Disconnected",
                            isOnline = error == null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val session = repository.createSession("/")
                        if (session != null) {
                            onSessionClick(session.id)
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Session",
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                isLoading && sessions.isEmpty() -> {
                    LoadingIndicator(message = "Loading sessions...")
                }
                error != null && sessions.isEmpty() -> {
                    ErrorCard(
                        message = error ?: "Unknown error",
                        onRetry = {
                            scope.launch {
                                repository.clearError()
                                repository.loadSessions()
                            }
                        },
                    )
                }
                else -> {
                    SessionList(
                        sessions = sessions,
                        onSessionClick = onSessionClick,
                        onNewSession = {
                            scope.launch {
                                val session = repository.createSession("/")
                                if (session != null) {
                                    onSessionClick(session.id)
                                }
                            }
                        },
                        onDeleteSession = { sessionId ->
                            scope.launch {
                                repository.deleteSession(sessionId)
                            }
                        },
                    )
                }
            }
        }
    }
}
