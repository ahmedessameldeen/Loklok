package com.loklok.sdk.store

import com.loklok.sdk.model.Message
import com.loklok.sdk.model.MessageStatus
import com.loklok.sdk.model.WireMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory message store implementing the client-side delivery & dedupe rules
 * from docs/protocol.md.
 *
 *  - Optimistic messages are keyed by `clientMsgId` and shown immediately (SENDING).
 *  - An `ack` upgrades that entry to SENT and fills in seq/id/ts.
 *  - An incoming `message` with a known `clientMsgId` reconciles the optimistic entry
 *    rather than appending a duplicate; messages from others are keyed by server `id`.
 *  - `history` is applied skipping anything we've already seen (`seq <= lastSeq`).
 *
 * Not internally synchronized — the owning Channel confines all calls to a single
 * coroutine, matching how the SDK drives it.
 */
class MessageStore {
    private val byKey = LinkedHashMap<String, Message>()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())

    /** Ordered, deduped view: SENT messages by `seq`, optimistic (SENDING) appended by `ts`. */
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    /** Highest server `seq` applied so far — sent to the server as `lastSeq` on resync. */
    var lastSeq: Long = 0
        private set

    fun addOptimistic(clientMsgId: String, userId: String, name: String, text: String, ts: Long) {
        byKey["cid:$clientMsgId"] = Message(
            id = clientMsgId, seq = 0, userId = userId, name = name,
            text = text, ts = ts, clientMsgId = clientMsgId, status = MessageStatus.SENDING,
        )
        publish()
    }

    fun applyAck(clientMsgId: String, id: String, seq: Long, ts: Long) {
        val existing = byKey["cid:$clientMsgId"] ?: return
        byKey["cid:$clientMsgId"] = existing.copy(id = id, seq = seq, ts = ts, status = MessageStatus.SENT)
        bumpSeq(seq)
        publish()
    }

    /** Apply an incoming message (own echo or from another user). */
    fun applyMessage(m: WireMessage) {
        if (m.seq in 1..lastSeq && byKey.values.none { it.seq == m.seq }) {
            // Already-seen older message we don't have a row for: still record to be safe.
        }
        val cid = m.clientMsgId
        val key = if (cid != null && byKey.containsKey("cid:$cid")) "cid:$cid" else "id:${m.id}"
        byKey[key] = Message(
            id = m.id, seq = m.seq, userId = m.userId, name = m.name,
            text = m.text, ts = m.ts, clientMsgId = cid, status = MessageStatus.SENT,
        )
        bumpSeq(m.seq)
        publish()
    }

    /** Apply a batch from `history`, skipping anything at or below lastSeq. */
    fun applyHistory(messages: List<WireMessage>) {
        var changed = false
        for (m in messages) {
            if (m.seq <= lastSeq && byKey.containsKey("id:${m.id}")) continue
            val cid = m.clientMsgId
            val key = if (cid != null && byKey.containsKey("cid:$cid")) "cid:$cid" else "id:${m.id}"
            byKey[key] = Message(
                id = m.id, seq = m.seq, userId = m.userId, name = m.name,
                text = m.text, ts = m.ts, clientMsgId = cid, status = MessageStatus.SENT,
            )
            bumpSeq(m.seq)
            changed = true
        }
        if (changed) publish()
    }

    private fun bumpSeq(seq: Long) {
        if (seq > lastSeq) lastSeq = seq
    }

    private fun publish() {
        // SENT (seq > 0) ordered by seq; optimistic (seq == 0) last, ordered by ts.
        _messages.value = byKey.values.sortedWith(
            compareBy({ if (it.seq == 0L) Long.MAX_VALUE else it.seq }, { it.ts }),
        )
    }
}
