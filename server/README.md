# Loklok server

Cloudflare Worker + Durable Object. One DO instance per room holds the WebSockets (via the
Hibernation API, so idle rooms cost nothing), assigns a monotonic `seq` to each message, persists
to the DO's SQLite, and broadcasts to members.

## Files
- `src/index.ts` — Worker router: verifies the JWT, routes `/room/:id/ws` to the room's DO.
- `src/RoomDO.ts` — the Durable Object: hibernation sockets, SQLite, presence, typing, resync.
- `src/jwt.ts` — minimal HS256 verify/sign over WebCrypto.
- `src/protocol.ts` — wire types, mirrors [`../docs/protocol.md`](../docs/protocol.md).
- `scripts/make-token.mjs` — mint a dev JWT. `scripts/smoke.mjs` — end-to-end test.

## Commands
```bash
npm install
npm run dev                 # wrangler dev on :8787
npm run smoke               # 8 end-to-end checks against a running dev server
npm run token u_alice Alice # print a JWT for manual testing (wscat etc.)
npx tsc --noEmit            # type-check
npx wrangler deploy         # deploy to the free tier
```

## Production config
```bash
wrangler secret put LOKLOK_JWT_SECRET   # replace the dev secret in wrangler.toml
```
The Worker is the only caller of the DO; it verifies the token and passes the trusted identity to
the DO via `X-Loklok-*` headers.
