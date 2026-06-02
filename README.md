# Loklok

A small, real-time chat **SDK** — like a miniature [Stream](https://getstream.io) — built to be
dropped into mobile apps. One shared core written in **Kotlin Multiplatform**, shipped as native
SDKs for **Android** and **iOS**, backed by a chat server I deployed on **Cloudflare Workers +
Durable Objects**. Runs entirely on free tiers.

> The point isn't "a chat screen" — it's the **SDK design**: one core, a clean cross-platform API,
> lossless reconnection, and a documented wire protocol that real apps integrate in ~10 lines.

---

## What it does

- **Real-time messaging** over a single WebSocket per room
- **Optimistic sends** with server `ack` reconciliation (no duplicates)
- **Lossless reconnection** — every message carries a monotonic `seq`; on reconnect the client
  replays only what it missed via `sync(lastSeq)`
- **Offline send queue** — messages typed while disconnected flush automatically on reconnect
- **Presence** (online/offline, with a snapshot for late joiners) and **typing indicators**
- **JWT auth** — the server verifies an HS256 token before upgrading the socket

## Architecture

```
                 Cloudflare Worker  (routes /room/:id/ws, verifies JWT)
                          │  upgrades the WebSocket → the room's Durable Object
                          ▼
              Durable Object (one per room)
              ├─ WebSocket Hibernation API  (idle rooms cost nothing)
              ├─ SQLite: messages with monotonic seq
              └─ in-memory: presence + typing
                          ▲   one WebSocket per room
        ┌─────────────────┴─────────────────┐
        │      KMP shared core (Kotlin)      │   ← all the logic lives here, once
        │  ChatClient · Channel · MessageStore │
        │  Ktor WS transport · reconnect/backoff │
        ├──────────────────┬─────────────────┤
        │  Android  (.aar)  │  iOS (XCFramework)│
        │  Compose demo     │  SwiftUI demo     │
        └──────────────────┴─────────────────┘
```

## Repository layout

| Path | What |
|------|------|
| [`server/`](server) | Cloudflare Worker + Durable Object (TypeScript) |
| [`sdk-kmp/core/`](sdk-kmp/core) | The shared SDK: models, transport, store, reconnect, public API |
| [`samples/android-demo/`](samples/android-demo) | Jetpack Compose app using the SDK |
| [`samples/ios-demo/`](samples/ios-demo) | SwiftUI app using the SDK |
| [`docs/protocol.md`](docs/protocol.md) | The wire protocol — the contract between server and SDK |
| [`docs/roadmap.md`](docs/roadmap.md) | Intentionally deferred features |

## The SDK API (identical shape on every platform)

**Kotlin / Android**
```kotlin
val client = ChatClient.connect(baseUrl, token, userId, name)
val channel = client.channel("general")
channel.messages          // StateFlow<List<Message>> — bind to the UI
channel.connectionState   // StateFlow<ConnectionState>
channel.events            // Flow<ChatEvent>: presence / typing / errors
channel.send("hello")
channel.setTyping(true)
```

**Swift / iOS** (via the Swift-friendly bridge)
```swift
let client = ChatClientCompanion().connect(baseUrl: baseURL, token: token, userId: id, name: name)
let chat = ChatClientKt.iosChannel(client, roomId: "general")
let sub = chat.observeMessages { self.messages = $0 }
chat.send(text: "hello")
```

---

## Run it

### 1. Backend (Cloudflare)
```bash
cd server
npm install
npm run dev          # wrangler dev on http://localhost:8787
npm run smoke        # end-to-end test: fan-out, ack, presence, reconnect-sync
# deploy (free):  npx wrangler deploy
```

### 2. Android demo
```bash
cd sdk-kmp
./gradlew :android-demo:installDebug    # or open the folder in Android Studio
```
The emulator reaches a local `wrangler dev` at `http://10.0.2.2:8787` (already configured).

### 3. iOS demo
```bash
cd sdk-kmp && ./build-xcframework.sh    # requires Xcode
# then open samples/ios-demo in Xcode, add the LoklokKit local package, run
```
See [samples/ios-demo/README.md](samples/ios-demo/README.md) for the Xcode setup steps.

---

## What's verified

- ✅ **Backend** — `npm run smoke` passes 8/8 (message fan-out, ack, presence snapshot, typing,
  lossless reconnect-sync) against `wrangler dev`.
- ✅ **SDK core** — 8 unit tests pass (`./gradlew :core:testDebugUnitTest`): optimistic/ack dedupe,
  seq ordering, history resync merge, backoff bounds, reconnect-then-resync, offline-queue flush.
- ✅ **Android** — the SDK assembles to `core-release.aar` and the Compose demo builds to an APK.
- 📦 **iOS** — XCFramework + SwiftUI demo are scaffolded; build with Xcode via `build-xcframework.sh`.

## Tech

Kotlin Multiplatform · Ktor WebSockets · kotlinx.coroutines / serialization · Cloudflare Workers ·
Durable Objects (WebSocket Hibernation + SQLite) · Jetpack Compose · SwiftUI.

See [docs/roadmap.md](docs/roadmap.md) for what's deliberately out of scope (and why the protocol
already leaves room for it).
