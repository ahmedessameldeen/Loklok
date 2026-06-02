package com.loklok.sdk.transport

import kotlinx.coroutines.flow.Flow

/**
 * Transport abstraction over a single WebSocket connection attempt.
 * Kept as an interface so the reconnection logic and store can be unit-tested
 * against a fake transport without a real network. See [KtorChatTransport].
 */
interface ChatTransport {
    /**
     * Open a connection to [url]. Suspends until the socket is open, then returns a
     * live [TransportSession]. Throws if the connection cannot be established.
     */
    suspend fun connect(url: String): TransportSession
}

/** A single open connection. [incoming] completes when the socket closes (locally or remotely). */
interface TransportSession {
    val incoming: Flow<String>
    suspend fun send(text: String)
    suspend fun close()
}
