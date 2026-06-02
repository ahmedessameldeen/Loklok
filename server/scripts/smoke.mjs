// End-to-end smoke test against a running `wrangler dev` (default http://localhost:8787).
//   node scripts/smoke.mjs [wsBase]
// Verifies: message fan-out, ack, presence, and reconnect missed-message sync.

import WebSocket from "ws";
import { createHmac } from "node:crypto";

const HTTP_BASE = process.argv[2] ?? "http://localhost:8787";
const WS_BASE = HTTP_BASE.replace(/^http/, "ws");
const SECRET = "dev-secret-change-me";
const ROOM = "smoke-" + Math.random().toString(36).slice(2, 8);

const b64url = (b) =>
  Buffer.from(b).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
function token(sub, name) {
  const h = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const exp = Math.floor(Date.now() / 1000) + 3600;
  const p = b64url(JSON.stringify({ sub, name, exp }));
  const s = b64url(createHmac("sha256", SECRET).update(`${h}.${p}`).digest());
  return `${h}.${p}.${s}`;
}
const url = (sub, name) => `${WS_BASE}/room/${ROOM}/ws?token=${token(sub, name)}`;

function open(sub, name) {
  const ws = new WebSocket(url(sub, name));
  ws.frames = [];
  ws.on("message", (d) => ws.frames.push(JSON.parse(d.toString())));
  return new Promise((res, rej) => {
    ws.on("open", () => res(ws));
    ws.on("error", rej);
  });
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const has = (ws, pred) => ws.frames.some(pred);

let failures = 0;
function check(label, ok) {
  console.log(`${ok ? "PASS" : "FAIL"}  ${label}`);
  if (!ok) failures++;
}

const main = async () => {
  console.log(`room=${ROOM} base=${WS_BASE}\n`);

  const alice = await open("u_alice", "Alice");
  const bob = await open("u_bob", "Bob");
  await sleep(300);

  check("alice got ready", has(alice, (f) => f.type === "ready" && f.userId === "u_alice"));
  check("bob sees alice presence online", has(bob, (f) => f.type === "presence" && f.userId === "u_alice" && f.online));

  // Alice sends a message.
  alice.send(JSON.stringify({ type: "send", clientMsgId: "c1", text: "hello bob" }));
  await sleep(300);
  check("alice got ack for c1", has(alice, (f) => f.type === "ack" && f.clientMsgId === "c1" && f.seq >= 1));
  check("bob received the message", has(bob, (f) => f.type === "message" && f.text === "hello bob"));

  // Typing indicator.
  alice.send(JSON.stringify({ type: "typing", isTyping: true }));
  await sleep(200);
  check("bob sees alice typing", has(bob, (f) => f.type === "typing" && f.userId === "u_alice" && f.isTyping));

  // Bob disconnects, Alice sends two more, Bob reconnects and syncs.
  bob.close();
  await sleep(300);
  alice.send(JSON.stringify({ type: "send", clientMsgId: "c2", text: "you there?" }));
  alice.send(JSON.stringify({ type: "send", clientMsgId: "c3", text: "ping" }));
  await sleep(300);

  const bob2 = await open("u_bob", "Bob");
  await sleep(200);
  bob2.send(JSON.stringify({ type: "sync", lastSeq: 1 })); // saw only the first message
  await sleep(300);
  const history = bob2.frames.find((f) => f.type === "history");
  check("bob received history on resync", !!history);
  check(
    "history contains the 2 missed messages",
    !!history && history.messages.length === 2 &&
      history.messages.map((m) => m.text).join(",") === "you there?,ping",
  );
  check("history is ordered by seq", !!history && history.messages[0].seq < history.messages[1].seq);

  alice.close();
  bob2.close();
  await sleep(100);

  console.log(`\n${failures === 0 ? "ALL PASSED" : failures + " FAILED"}`);
  process.exit(failures === 0 ? 0 : 1);
};

main().catch((e) => {
  console.error("smoke error:", e.message);
  process.exit(1);
});
