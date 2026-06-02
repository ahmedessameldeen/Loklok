package com.loklok.sdk

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackoffTest {

    @Test
    fun staysWithinJitterCeiling() {
        val rnd = Random(42)
        repeat(100) { attempt ->
            val d = backoffMillis(attempt, baseMs = 500, capMs = 30_000, random = rnd)
            assertTrue(d in 0..30_000, "attempt=$attempt delay=$d out of range")
        }
    }

    @Test
    fun ceilingGrowsThenCaps() {
        // With a random that always returns the max, delay == ceiling.
        val maxRandom = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextLong(until: Long) = until - 1
        }
        assertEquals(500, backoffMillis(0, 500, 30_000, maxRandom))   // 500 * 2^0
        assertEquals(1000, backoffMillis(1, 500, 30_000, maxRandom))  // 500 * 2^1
        assertEquals(2000, backoffMillis(2, 500, 30_000, maxRandom))  // 500 * 2^2
        assertEquals(30_000, backoffMillis(20, 500, 30_000, maxRandom)) // capped
    }
}
