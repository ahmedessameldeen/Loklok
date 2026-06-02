// Minimal HS256 JWT verify/sign using WebCrypto (available in Workers & Node 18+).
// Not a full JWT library — just enough for Loklok's auth: { sub, name, exp }.

import type { SessionAuth } from "./protocol";

function b64urlToBytes(s: string): Uint8Array {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToB64url(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"],
  );
}

export interface LoklokClaims {
  sub: string; // userId
  name: string; // display name
  exp: number; // epoch seconds
}

/** Verify an HS256 JWT and return the session identity, or null if invalid/expired. */
export async function verifyToken(token: string, secret: string): Promise<SessionAuth | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [h, p, s] = parts;
  try {
    const key = await hmacKey(secret);
    const ok = await crypto.subtle.verify(
      "HMAC",
      key,
      b64urlToBytes(s),
      new TextEncoder().encode(`${h}.${p}`),
    );
    if (!ok) return null;
    const claims = JSON.parse(new TextDecoder().decode(b64urlToBytes(p))) as LoklokClaims;
    if (!claims.sub || typeof claims.exp !== "number") return null;
    if (Date.now() / 1000 > claims.exp) return null;
    return { userId: claims.sub, name: claims.name ?? claims.sub };
  } catch {
    return null;
  }
}

/** Sign a token. Used only by the dev token script — clients never sign their own. */
export async function signToken(claims: LoklokClaims, secret: string): Promise<string> {
  const enc = (o: unknown) => bytesToB64url(new TextEncoder().encode(JSON.stringify(o)));
  const head = enc({ alg: "HS256", typ: "JWT" });
  const body = enc(claims);
  const key = await hmacKey(secret);
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${head}.${body}`));
  return `${head}.${body}.${bytesToB64url(new Uint8Array(sig))}`;
}
