package com.loklok.sdk.transport

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Real transport backed by Ktor's multiplatform WebSocket client.
 * The platform engine (OkHttp on Android, Darwin on iOS) is injected via [engineClient].
 */
class KtorChatTransport(
    private val client: HttpClient,
) : ChatTransport {

    override suspend fun connect(url: String): TransportSession {
        val session = client.webSocketSession(url)
        return object : TransportSession {
            override val incoming: Flow<String> = flow {
                session.incoming.consumeEach { frame ->
                    if (frame is Frame.Text) emit(frame.readText())
                }
            }
            override suspend fun send(text: String) = session.send(Frame.Text(text))
            override suspend fun close() = session.close()
        }
    }

    companion object {
        /** Build a transport with the WebSockets plugin installed on [engine]. */
        fun create(engine: HttpClient): KtorChatTransport =
            KtorChatTransport(engine.config { install(WebSockets) })
    }
}
