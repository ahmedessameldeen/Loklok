package com.loklok.sdk

import com.loklok.sdk.transport.ChatTransport
import com.loklok.sdk.transport.KtorChatTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Entry point to the Loklok SDK. One client per signed-in user; open a [Channel] per room.
 *
 * ```
 * val client = ChatClient.connect("https://loklok.example.workers.dev", token, userId, name)
 * val channel = client.channel("room-123")
 * channel.send("hello")
 * channel.messages          // StateFlow<List<Message>>
 * channel.events            // presence / typing / errors
 * ```
 */
class ChatClient internal constructor(
    private val baseUrl: String,
    private val token: String,
    private val userId: String,
    private val name: String,
    private val transport: ChatTransport,
    private val clock: () -> Long,
    private val newId: () -> String,
) {
    /** Open (and start connecting to) a room. The returned [Channel] reconnects automatically. */
    fun channel(roomId: String): Channel {
        val wsBase = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val url = "$wsBase/room/$roomId/ws?token=$token"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return Channel(roomId, url, userId, name, transport, scope, clock, newId).also { it.start() }
    }

    companion object {
        /**
         * Connect with the default platform transport (OkHttp on Android, Darwin on iOS).
         * [baseUrl] is the HTTP origin of the Worker, e.g. `https://loklok.<acct>.workers.dev`.
         */
        fun connect(baseUrl: String, token: String, userId: String, name: String): ChatClient =
            ChatClient(
                baseUrl = baseUrl,
                token = token,
                userId = userId,
                name = name,
                transport = KtorChatTransport.create(platformHttpClient()),
                clock = ::currentTimeMillis,
                newId = ::randomId,
            )

        /** Testing/advanced: inject a custom transport and deterministic clock/id generators. */
        fun withTransport(
            baseUrl: String,
            token: String,
            userId: String,
            name: String,
            transport: ChatTransport,
            clock: () -> Long,
            newId: () -> String,
        ): ChatClient = ChatClient(baseUrl, token, userId, name, transport, clock, newId)
    }
}
