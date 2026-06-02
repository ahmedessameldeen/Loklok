package com.loklok.sdk

import com.loklok.sdk.model.LoklokJson
import com.loklok.sdk.model.ClientFrame
import com.loklok.sdk.model.ServerFrame
import com.loklok.sdk.transport.ChatTransport
import com.loklok.sdk.transport.TransportSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel as CoChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeSession : TransportSession {
    val sent = mutableListOf<String>()
    private val inbound = CoChannel<String>(CoChannel.UNLIMITED)
    override val incoming: Flow<String> = inbound.consumeAsFlow()
    override suspend fun send(text: String) { sent.add(text) }
    override suspend fun close() { inbound.close() }
    suspend fun push(frame: ServerFrame) =
        inbound.send(LoklokJson.encodeToString(ServerFrame.serializer(), frame))
    fun drop() = inbound.close()
}

private class FakeTransport : ChatTransport {
    val sessions = mutableListOf<FakeSession>()
    override suspend fun connect(url: String): TransportSession =
        FakeSession().also { sessions.add(it) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelReconnectTest {

    @Test
    fun reconnectsAndResyncsFromLastSeq() = runTest {
        // Unconfined dispatcher: launched coroutines run eagerly to their first real
        // suspension (here, collecting the incoming flow), so the socket connects inline.
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport()
        var idN = 0
        val channel = Channel(
            roomId = "r", wsUrl = "ws://x/room/r/ws",
            selfUserId = "me", selfName = "Me",
            transport = transport, scope = scope,
            clock = { 1L }, newId = { "c${idN++}" },
        )
        channel.start()

        // First connection established eagerly.
        assertEquals(1, transport.sessions.size)
        val s1 = transport.sessions[0]

        // Server delivers a message at seq=5.
        s1.push(ServerFrame.Msg("m5", seq = 5, userId = "u", name = "U", text = "hello", ts = 5))
        assertEquals(5, channel.messages.value.single().seq)

        // Connection drops -> the loop reconnects after the backoff delay.
        s1.drop()
        advanceUntilIdle()
        assertEquals(2, transport.sessions.size, "should have reconnected")

        // On reconnect it must resync from the highest applied seq.
        val s2 = transport.sessions[1]
        val syncFrames = s2.sent.map { LoklokJson.decodeFromString(ClientFrame.serializer(), it) }
            .filterIsInstance<ClientFrame.Sync>()
        assertEquals(1, syncFrames.size, "expected exactly one sync on reconnect")
        assertEquals(5, syncFrames.first().lastSeq)
        scope.cancel()
    }

    @Test
    fun queuesSendWhileOfflineAndFlushesOnConnect() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeTransport()
        var idN = 0
        val channel = Channel(
            roomId = "r", wsUrl = "ws://x/room/r/ws",
            selfUserId = "me", selfName = "Me",
            transport = transport, scope = scope,
            clock = { 1L }, newId = { "c${idN++}" },
        )
        // Send BEFORE the connection exists — must be queued and shown optimistically.
        channel.send("queued while offline")
        assertTrue(channel.messages.value.any { it.text == "queued while offline" })

        // Now connect; the queued send must flush.
        channel.start()
        val s1 = transport.sessions[0]
        val sends = s1.sent.map { LoklokJson.decodeFromString(ClientFrame.serializer(), it) }
            .filterIsInstance<ClientFrame.Send>()
        assertEquals(1, sends.size, "queued send should flush once connected")
        assertEquals("queued while offline", sends.first().text)
        scope.cancel()
    }
}
