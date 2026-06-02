package com.loklok.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loklok.sdk.ChatClient
import com.loklok.sdk.Channel
import com.loklok.sdk.model.ChatEvent
import com.loklok.sdk.model.ConnectionState
import com.loklok.sdk.model.Message
import com.loklok.sdk.model.MessageStatus
import kotlinx.coroutines.launch

// Point this at your deployed Worker, or 10.0.2.2:8787 for the emulator -> localhost.
private const val BASE_URL = "http://10.0.2.2:8787"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
private fun App() {
    var session by remember { mutableStateOf<Triple<ChatClient, Channel, String>?>(null) }
    when (val s = session) {
        null -> SetupScreen { name, room ->
            val userId = "u_${name.lowercase()}"
            val client = ChatClient.connect(BASE_URL, DevToken.mint(userId, name), userId, name)
            session = Triple(client, client.channel(room), userId)
        }
        else -> ChatScreen(channel = s.second, selfUserId = s.third)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(onJoin: (name: String, room: String) -> Unit) {
    var name by remember { mutableStateOf("Ahmed") }
    var room by remember { mutableStateOf("general") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Loklok", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Your name") })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(room, { room = it }, label = { Text("Room") })
        Spacer(Modifier.height(24.dp))
        Button(onClick = { if (name.isNotBlank() && room.isNotBlank()) onJoin(name, room) }) {
            Text("Join")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(channel: Channel, selfUserId: String) {
    val messages by channel.messages.collectAsStateWithLifecycle()
    val connection by channel.connectionState.collectAsStateWithLifecycle()
    var typingUser by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(channel) {
        channel.events.collect { e ->
            if (e is ChatEvent.TypingChanged && e.userId != selfUserId) {
                typingUser = if (e.isTyping) e.userId else null
            }
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Loklok") }, actions = {
                AssistChip(onClick = {}, label = { Text(connection.label()) })
                Spacer(Modifier.width(8.dp))
            })
        },
        bottomBar = {
            Column {
                typingUser?.let {
                    Text("  $it is typing…", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            scope.launch { channel.setTyping(it.isNotEmpty()) }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            draft = ""
                            scope.launch { channel.send(text); channel.setTyping(false) }
                        }
                    }) { Text("Send") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { msg -> MessageBubble(msg, msg.userId == selfUserId) }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
                .widthIn(max = 280.dp),
        ) {
            if (!mine) Text(msg.name, style = MaterialTheme.typography.labelMedium)
            Text(msg.text, style = MaterialTheme.typography.bodyLarge)
            if (mine && msg.status == MessageStatus.SENDING) {
                Text("sending…", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun ConnectionState.label() = when (this) {
    ConnectionState.IDLE -> "idle"
    ConnectionState.CONNECTING -> "connecting"
    ConnectionState.CONNECTED -> "online"
    ConnectionState.RECONNECTING -> "reconnecting"
    ConnectionState.CLOSED -> "closed"
}
