// Dev-only: mint an HS256 JWT for testing.
//   node scripts/make-token.mjs <userId> <name> [secret]
// Defaults to the dev secret in wrangler.toml.

import { createHmac } from "node:crypto";

const userId = process.argv[2] ?? "u_alice";
const name = process.argv[3] ?? "Alice";
const secret = process.argv[4] ?? "dev-secret-change-me";

const b64url = (buf) =>
  Buffer.from(buf).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

const header = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
const exp = Math.floor(Date.now() / 1000) + 60 * 60 * 24; // 24h
const payload = b64url(JSON.stringify({ sub: userId, name, exp }));
const sig = b64url(createHmac("sha256", secret).update(`${header}.${payload}`).digest());

process.stdout.write(`${header}.${payload}.${sig}\n`);
