package com.loklok.sdk

import com.loklok.sdk.model.ChatEvent
import com.loklok.sdk.model.ConnectionState
import com.loklok.sdk.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Swift-friendly facade over [Channel].
 *
 * Kotlin `Flow` and `suspend` functions are awkward to consume from Swift, so this wrapper
 * exposes plain callbacks (returning a [Cancellable]) and fire-and-forget actions. All
 * callbacks are delivered on the main dispatcher, ready to drive SwiftUI state.
 *
 * ```swift
 * let chat = ChatClientKt.iosChannel(client, roomId: "general")
 * let sub = chat.observeMessages { messages in self.messages = messages }
 * chat.send(text: "hello")
 * ```
 */
class IosChannel internal constructor(private val channel: Channel) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun observeMessages(onChange: (List<Message>) -> Unit): Cancellable =
        Cancellable(scope.launch { channel.messages.collect { onChange(it) } })

    fun observeConnection(onChange: (ConnectionState) -> Unit): Cancellable =
        Cancellable(scope.launch { channel.connectionState.collect { onChange(it) } })

    fun observeEvents(onEvent: (ChatEvent) -> Unit): Cancellable =
        Cancellable(scope.launch { channel.events.collect { onEvent(it) } })

    fun send(text: String) { scope.launch { channel.send(text) } }

    fun setTyping(isTyping: Boolean) { scope.launch { channel.setTyping(isTyping) } }

    fun close() {
        scope.launch { channel.close() }
        scope.cancel()
    }
}

/** Handle to stop an observation. Call [cancel] in SwiftUI `.onDisappear`. */
class Cancellable internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/** Open a Swift-friendly channel for [roomId]. */
fun ChatClient.iosChannel(roomId: String): IosChannel = IosChannel(channel(roomId))
