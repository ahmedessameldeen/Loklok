package com.loklok.sdk

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff with full jitter, capped. Pulled out as a pure function so the
 * reconnection policy can be unit-tested without timing.
 *
 * delay = random(0 .. min(cap, base * 2^attempt))
 */
fun backoffMillis(
    attempt: Int,
    baseMs: Long = 500,
    capMs: Long = 30_000,
    random: Random = Random.Default,
): Long {
    val exp = baseMs.toDouble() * 2.0.pow(attempt.coerceAtMost(16))
    val ceiling = min(capMs.toDouble(), exp).toLong().coerceAtLeast(1)
    return random.nextLong(ceiling + 1)
}
