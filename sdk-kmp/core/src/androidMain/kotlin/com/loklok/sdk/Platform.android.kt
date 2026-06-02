package com.loklok.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun randomId(): String = "c_" + UUID.randomUUID().toString()

internal actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp)
