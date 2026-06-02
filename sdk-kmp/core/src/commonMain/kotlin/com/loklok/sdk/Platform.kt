package com.loklok.sdk

import io.ktor.client.HttpClient

/** Wall-clock milliseconds — used for optimistic message timestamps. */
internal expect fun currentTimeMillis(): Long

/** A unique client message id for optimistic-send reconciliation. */
internal expect fun randomId(): String

/** Platform HTTP engine (OkHttp on Android, Darwin on iOS) with no plugins installed. */
internal expect fun platformHttpClient(): HttpClient
