# Loklok SDK (Kotlin Multiplatform)

The shared chat core, compiled to a native Android library (`.aar`) and an iOS `XCFramework`.

## Module layout (`core/`)
```
commonMain/com/loklok/sdk/
├── ChatClient.kt          # entry point: connect(), channel()
├── Channel.kt             # reconnect loop, optimistic send, resync, offline queue
├── Backoff.kt             # exponential backoff + full jitter (pure, tested)
├── Platform.kt            # expect: clock, id, HTTP engine
├── model/
│   ├── Models.kt          # Message, Presence, ChatEvent, ConnectionState
│   └── Frames.kt          # serializable wire frames (ClientFrame / ServerFrame)
├── transport/
│   ├── ChatTransport.kt   # interface (so reconnect logic is unit-testable)
│   └── KtorChatTransport.kt
└── store/MessageStore.kt  # dedupe by clientMsgId, order by seq, history merge
androidMain/…Platform.android.kt   # OkHttp engine, java time/uuid
iosMain/…Platform.ios.kt           # Darwin engine, NSDate/NSUUID
iosMain/…IosBridge.kt              # Swift-friendly callback facade (IosChannel)
```

## Build & test
```bash
# Unit tests (run on the Android/JVM target; no device needed)
./gradlew :core:testDebugUnitTest -Ploklok.enableIos=false

# Android library artifact
./gradlew :core:assembleRelease -Ploklok.enableIos=false   # → core/build/outputs/aar

# iOS XCFramework (requires Xcode)
./build-xcframework.sh                                     # → core/build/XCFrameworks/release
```

`-Ploklok.enableIos=false` drops the iOS targets so the project builds headless / on CI without
Xcode. iOS targets are **on by default** otherwise.

## Design notes
- **One WebSocket per `Channel`.** The connection loop reconnects with [`Backoff`](core/src/commonMain/kotlin/com/loklok/sdk/Backoff.kt)
  and, once reconnected, sends `sync(lastSeq)` so the [`MessageStore`](core/src/commonMain/kotlin/com/loklok/sdk/store/MessageStore.kt)
  only receives what it missed.
- **Optimistic UI.** `send()` inserts a `SENDING` message keyed by `clientMsgId`; the server `ack`
  (and the echoed `message`) reconcile it to `SENT` without creating a duplicate.
- **Testability.** All network goes through the `ChatTransport` interface; the tests inject a fake
  transport and drive reconnection on virtual time — no sockets, no flakiness.
