package com.loklok.sdk.model

/** Delivery state of a message in the local store. */
enum class MessageStatus { SENDING, SENT }

/**
 * A chat message as seen by the UI. `seq` is the server-assigned monotonic order key
 * (0 while still optimistic/SENDING). `clientMsgId` links an optimistic message to its
 * server ack so we can reconcile instead of duplicating.
 */
data class Message(
    val id: String,
    val seq: Long,
    val userId: String,
    val name: String,
    val text: String,
    val ts: Long,
    val clientMsgId: String?,
    val status: MessageStatus,
)

data class Presence(val userId: String, val online: Boolean)

/** Events surfaced to consumers beyond the message list. */
sealed interface ChatEvent {
    data class PresenceChanged(val userId: String, val online: Boolean) : ChatEvent
    data class TypingChanged(val userId: String, val isTyping: Boolean) : ChatEvent
    data class ErrorReceived(val code: String, val message: String) : ChatEvent
}

/** Connection lifecycle, exposed so UIs can show "connecting…" / "offline" banners. */
enum class ConnectionState { IDLE, CONNECTING, CONNECTED, RECONNECTING, CLOSED }
