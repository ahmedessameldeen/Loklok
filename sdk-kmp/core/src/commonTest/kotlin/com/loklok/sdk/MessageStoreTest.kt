package com.loklok.sdk

import com.loklok.sdk.model.MessageStatus
import com.loklok.sdk.model.WireMessage
import com.loklok.sdk.store.MessageStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageStoreTest {

    private fun wire(seq: Long, id: String, text: String, cid: String? = null) =
        WireMessage(id = id, seq = seq, userId = "u", name = "U", text = text, ts = seq, clientMsgId = cid)

    @Test
    fun optimisticThenAckReconcilesWithoutDuplicate() {
        val store = MessageStore()
        store.addOptimistic("c1", "u", "Me", "hi", ts = 10)
        assertEquals(1, store.messages.value.size)
        assertEquals(MessageStatus.SENDING, store.messages.value[0].status)

        store.applyAck("c1", id = "srv_1", seq = 1, ts = 11)
        assertEquals(1, store.messages.value.size, "ack must not create a second row")
        val m = store.messages.value[0]
        assertEquals(MessageStatus.SENT, m.status)
        assertEquals(1, m.seq)
        assertEquals(1, store.lastSeq)
    }

    @Test
    fun echoedMessageDoesNotDuplicateOptimistic() {
        val store = MessageStore()
        store.addOptimistic("c1", "u", "Me", "hi", ts = 10)
        store.applyAck("c1", "srv_1", seq = 1, ts = 11)
        // Server also broadcasts the message back to the sender (same clientMsgId).
        store.applyMessage(wire(seq = 1, id = "srv_1", text = "hi", cid = "c1"))
        assertEquals(1, store.messages.value.size, "echo with known clientMsgId must reconcile")
    }

    @Test
    fun messagesAreOrderedBySeqWithOptimisticLast() {
        val store = MessageStore()
        store.applyMessage(wire(seq = 2, id = "b", text = "second"))
        store.applyMessage(wire(seq = 1, id = "a", text = "first"))
        store.addOptimistic("c9", "u", "Me", "pending", ts = 999)
        val texts = store.messages.value.map { it.text }
        assertEquals(listOf("first", "second", "pending"), texts)
    }

    @Test
    fun historySkipsAlreadySeenSeq() {
        val store = MessageStore()
        store.applyMessage(wire(seq = 3, id = "c", text = "c"))
        assertEquals(3, store.lastSeq)
        // Resync returns 2..4; 3 already applied, only 4 (and lower-but-unseen) added.
        store.applyHistory(listOf(wire(2, "b", "b"), wire(3, "c", "c"), wire(4, "d", "d")))
        val seqs = store.messages.value.map { it.seq }
        assertTrue(seqs.contains(4))
        assertEquals(4, store.lastSeq)
        // No duplicate of seq 3.
        assertEquals(1, store.messages.value.count { it.seq == 3L })
    }
}
