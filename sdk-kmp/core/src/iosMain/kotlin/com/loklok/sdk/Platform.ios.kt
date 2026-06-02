package com.loklok.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

internal actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

internal actual fun randomId(): String = "c_" + NSUUID().UUIDString()

internal actual fun platformHttpClient(): HttpClient = HttpClient(Darwin)
