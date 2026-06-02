package com.loklok.sdk

import com.loklok.sdk.model.ChatEvent
import com.loklok.sdk.model.LoklokJson
import com.loklok.sdk.model.ClientFrame
import com.loklok.sdk.model.ConnectionState
import com.loklok.sdk.model.Message
import com.loklok.sdk.model.ServerFrame
import com.loklok.sdk.model.WireMessage
import com.loklok.sdk.store.MessageStore
import com.loklok.sdk.transport.ChatTransport
import com.loklok.sdk.transport.TransportSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * A live connection to one chat room.
 *
 * Owns the reconnect loop: it keeps a single WebSocket open, and on any drop it
 * reconnects with [backoffMillis] then replays missed messages via `sync(lastSeq)`.
 * Outbound sends made while offline are queued and flushed on reconnect, so the
 * optimistic UI stays consistent.
 *
 * All [MessageStore] mutations are guarded by [mutex] so inbound frames and outbound
 * sends never race.
 */
class Channel internal constructor(
    private val roomId: String,
    private val wsUrl: String,
    private val selfUserId: String,
    private val selfName: String,
    private val transport: ChatTransport,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val newId: () -> String,
) {
    private val store = MessageStore()
    private val mutex = Mutex()
    private val outbox = ArrayDeque<ClientFrame>()
    private var session: TransportSession? = null

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    private val _state = MutableStateFlow(ConnectionState.IDLE)

    /** Reactive, ordered, deduped message list — bind this directly to the UI. */
    val messages: StateFlow<List<Message>> get() = store.messages

    /** Presence, typing, and error events. */
    val events: Flow<ChatEvent> = _events.asSharedFlow()

    /** Connection lifecycle for "connecting…/offline" UI. */
    val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    internal fun start() {
        scope.launch { runConnectionLoop() }
    }

    /** Optimistically add the message locally and send it (or queue it if offline). */
    suspend fun send(text: String) {
        val clientMsgId = newId()
        val frame = ClientFrame.Send(clientMsgId, text)
        mutex.withLock {
            store.addOptimistic(clientMsgId, selfUserId, selfName, text, clock())
            val s = session
            if (s != null) trySend(s, frame) else outbox.addLast(frame)
        }
    }

    /** Notify the room of local typing state (best-effort, dropped if offline). */
    suspend fun setTyping(isTyping: Boolean) {
        mutex.withLock { session?.let { trySend(it, ClientFrame.Typing(isTyping)) } }
    }

    /** Tear down the connection and stop reconnecting. */
    suspend fun close() {
        mutex.withLock { session?.close() }
        _state.value = ConnectionState.CLOSED
        scope.cancel()
    }

    // ---- internals ----

    private suspend fun runConnectionLoop() {
        var attempt = 0
        while (scope.isActive) {
            _state.value = if (attempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
            try {
                val s = transport.connect(wsUrl)
                mutex.withLock {
                    session = s
                    // Replay anything we missed, then flush queued sends.
                    if (store.lastSeq > 0) trySend(s, ClientFrame.Sync(store.lastSeq))
                    while (outbox.isNotEmpty()) trySend(s, outbox.removeFirst())
                }
                _state.value = ConnectionState.CONNECTED
                attempt = 0
                s.incoming.collect { raw -> onFrame(raw) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // fall through to reconnect
            } finally {
                mutex.withLock { session = null }
            }
            if (!scope.isActive) break
            delay(backoffMillis(attempt++))
        }
    }

    private suspend fun onFrame(raw: String) {
        val frame = try {
            LoklokJson.decodeFromString(ServerFrame.serializer(), raw)
        } catch (_: Throwable) {
            return // ignore unknown / malformed frames
        }
        when (frame) {
            is ServerFrame.Ready -> {}
            is ServerFrame.Ack ->
                mutex.withLock { store.applyAck(frame.clientMsgId, frame.id, frame.seq, frame.ts) }
            is ServerFrame.Msg ->
                mutex.withLock {
                    store.applyMessage(
                        WireMessage(frame.id, frame.seq, frame.userId, frame.name, frame.text, frame.ts, frame.clientMsgId),
                    )
                }
            is ServerFrame.History ->
                mutex.withLock { store.applyHistory(frame.messages) }
            is ServerFrame.PresenceFrame ->
                _events.emit(ChatEvent.PresenceChanged(frame.userId, frame.online))
            is ServerFrame.TypingFrame ->
                _events.emit(ChatEvent.TypingChanged(frame.userId, frame.isTyping))
            is ServerFrame.ErrorFrame ->
                _events.emit(ChatEvent.ErrorReceived(frame.code, frame.message))
        }
    }

    private suspend fun trySend(s: TransportSession, frame: ClientFrame) {
        try {
            s.send(LoklokJson.encodeToString(ClientFrame.serializer(), frame))
        } catch (_: Throwable) {
            // socket died mid-send; reconnect loop will re-establish and flush
            if (frame is ClientFrame.Send) outbox.addLast(frame)
        }
    }
}
