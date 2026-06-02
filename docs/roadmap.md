# Roadmap

Loklok's first milestone is deliberately scoped to **KMP core + Android + iOS** with a real
deployed backend. The features below are intentionally deferred. They are listed here both to show
the scoping judgment and because the [wire protocol](protocol.md) was designed so each can be added
**without breaking the existing contract** (new frame `type`s are ignored by older clients).

## Additional platform SDKs
- **Flutter plugin** — a thin method-channel/FFI bridge to the KMP core.
- **React Native module** — a native module bridging to the same core.
- **KMP consumer artifact** — publish `core` to Maven Central so other KMP apps depend on it directly.

## Messaging features
- **Read receipts** — add `read { upToSeq }` (C→S) and a `read` broadcast (S→C). `seq` already gives
  a total order to mark against.
- **Media / file attachments** — upload to R2, send a `message` with an attachment descriptor.
- **Message editing & deletion** — `edit` / `delete` frames keyed by message `id`.
- **Reactions & threads** — additive frames referencing a parent message `id`.
- **Message search** — index persisted messages (the DO already stores them in SQLite).

## Infrastructure
- **Push notifications** — fan out to APNs/FCM for offline members (a queue + token registry).
- **Channel/room management** — create/list/join APIs, membership and roles.
- **Moderation & rate limiting** — per-user send limits, content filtering hooks.
- **Horizontal fan-out** — for very large rooms, shard or relay between Durable Objects.

## Hardening
- **End-to-end encryption** for private rooms.
- **Token refresh** flow and short-lived tokens.
- **Backpressure** on the outbound queue and a capped local history window.

## Publishing
- Android: GitHub Packages → Maven Central.
- iOS: distribute the XCFramework via a tagged Swift Package / CocoaPods spec.
