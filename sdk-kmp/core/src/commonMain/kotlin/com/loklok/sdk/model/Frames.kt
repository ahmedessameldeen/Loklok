package com.loklok.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Wire frames — the JSON contract with the server. See docs/protocol.md.
 * The "type" field is the polymorphic discriminator on both directions.
 */

@Serializable
data class WireMessage(
    val id: String,
    val seq: Long,
    val userId: String,
    val name: String,
    val text: String,
    val ts: Long,
    val clientMsgId: String? = null,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ClientFrame {
    @Serializable @SerialName("send")
    data class Send(val clientMsgId: String, val text: String) : ClientFrame

    @Serializable @SerialName("typing")
    data class Typing(val isTyping: Boolean) : ClientFrame

    @Serializable @SerialName("sync")
    data class Sync(val lastSeq: Long) : ClientFrame
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ServerFrame {
    @Serializable @SerialName("ready")
    data class Ready(val userId: String, val roomId: String) : ServerFrame

    @Serializable @SerialName("ack")
    data class Ack(val clientMsgId: String, val id: String, val seq: Long, val ts: Long) : ServerFrame

    @Serializable @SerialName("message")
    data class Msg(
        val id: String,
        val seq: Long,
        val userId: String,
        val name: String,
        val text: String,
        val ts: Long,
        val clientMsgId: String? = null,
    ) : ServerFrame

    @Serializable @SerialName("history")
    data class History(val messages: List<WireMessage>) : ServerFrame

    @Serializable @SerialName("presence")
    data class PresenceFrame(val userId: String, val online: Boolean) : ServerFrame

    @Serializable @SerialName("typing")
    data class TypingFrame(val userId: String, val isTyping: Boolean) : ServerFrame

    @Serializable @SerialName("error")
    data class ErrorFrame(val code: String, val message: String) : ServerFrame
}

/** Shared JSON config: ignore unknown frame types for forward compatibility. */
val LoklokJson: Json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    encodeDefaults = true
}
