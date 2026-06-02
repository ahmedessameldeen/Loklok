# Loklok Wire Protocol v1

The contract between the server (Cloudflare Durable Object) and every SDK. JSON frames over a
single WebSocket per room. This document is the source of truth; `server/src/protocol.ts` and the
KMP `model/` package both mirror it.

## Connection

```
GET wss://<host>/room/<roomId>/ws?token=<jwt>
```

- One WebSocket per room.
- `token` is an HS256 JWT carrying `{ sub: userId, name: displayName, exp }`. The Worker verifies
  it before upgrading. Auth can also be sent as a first `auth` frame, but the query param is the
  default path used by the SDKs.

## Sequence numbers (`seq`)

Every persisted message gets a strictly increasing `seq`, assigned by the room's Durable Object.
`seq` is the backbone of missed-message sync:

- The client remembers the highest `seq` it has applied (`lastSeq`).
- On (re)connect the client sends `sync { lastSeq }`.
- The server replies with `history` containing every message where `seq > lastSeq`.

This makes reconnection lossless without timestamps or guesswork.

## Frame envelope

Every frame is `{ "type": <string>, ... }`. Unknown `type` values must be ignored (forward-compat).

## Client → Server

| type | fields | meaning |
|------|--------|---------|
| `auth` | `token` | optional explicit auth (alternative to query param) |
| `send` | `clientMsgId` (string, client-generated UUID), `text` | send a message |
| `typing` | `isTyping` (bool) | local typing state changed |
| `sync` | `lastSeq` (int) | request everything newer than `lastSeq` |

## Server → Client

| type | fields | meaning |
|------|--------|---------|
| `ready` | `userId`, `roomId` | connection accepted, auth ok |
| `ack` | `clientMsgId`, `id`, `seq`, `ts` | the `send` was persisted; reconcile the optimistic message |
| `message` | `id`, `seq`, `userId`, `name`, `text`, `ts`, `clientMsgId?` | a message (own or others') |
| `history` | `messages: Message[]` | ordered list with `seq > lastSeq`, ascending |
| `presence` | `userId`, `online` (bool) | a member connected/disconnected |
| `typing` | `userId`, `isTyping` (bool) | a member's typing state |
| `error` | `code`, `message` | recoverable protocol/auth error |

### `Message` object

```jsonc
{
  "id": "srv_01H...",       // server id (ULID-ish)
  "seq": 42,                // monotonic per room
  "userId": "u_123",
  "name": "Ahmed",
  "text": "hello",
  "ts": 1733070000000,      // server epoch ms
  "clientMsgId": "c_abc"    // echoed so the sender can dedupe its optimistic copy
}
```

## Delivery & dedupe rules (client responsibility)

1. On `send`, insert an **optimistic** message locally keyed by `clientMsgId` (status `sending`).
2. On `ack`, upgrade that optimistic message to `sent`, fill in `id`/`seq`/`ts`.
3. On `message`, if `clientMsgId` matches an existing optimistic entry, reconcile rather than append.
4. Always store messages keyed/ordered by `seq`. Apply `history` by skipping any `seq <= lastSeq`.

## Error codes

| code | meaning |
|------|---------|
| `auth_failed` | token missing/invalid/expired — client should not auto-retry without a new token |
| `bad_frame` | malformed JSON or missing required field |
| `internal` | server error — client may retry with backoff |
