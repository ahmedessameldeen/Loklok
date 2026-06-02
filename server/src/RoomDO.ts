// RoomDO — one Durable Object instance per chat room.
//
// Responsibilities:
//  - Hold the room's WebSocket connections (using the Hibernation API so an idle
//    room costs nothing and survives DO eviction).
//  - Assign a monotonic `seq` to every message and persist it in DO SQLite.
//  - Broadcast messages, presence, and typing to connected members.
//  - Answer `sync { lastSeq }` with the missed `history`.
//
// See docs/protocol.md for the wire contract.

import type { ClientFrame, ServerFrame, ChatMessage, SessionAuth } from "./protocol";

interface Env {
  LOKLOK_JWT_SECRET: string;
}

// Identity we attach to each hibernatable socket so it survives eviction.
interface Attachment extends SessionAuth {
  roomId: string;
}

export class RoomDO {
  private ctx: DurableObjectState;
  private env: Env;

  constructor(ctx: DurableObjectState, env: Env) {
    this.ctx = ctx;
    this.env = env;
    this.ctx.blockConcurrencyWhile(async () => this.initSchema());
  }

  private initSchema() {
    this.ctx.storage.sql.exec(`
      CREATE TABLE IF NOT EXISTS messages (
        seq         INTEGER PRIMARY KEY AUTOINCREMENT,
        id          TEXT NOT NULL,
        userId      TEXT NOT NULL,
        name        TEXT NOT NULL,
        text        TEXT NOT NULL,
        ts          INTEGER NOT NULL,
        clientMsgId TEXT
      );
    `);
  }

  // Worker forwards the upgrade request here; identity is passed via headers it set
  // after verifying the JWT (the DO trusts the Worker — it's the only caller).
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const userId = request.headers.get("X-Loklok-User") ?? "";
    const name = request.headers.get("X-Loklok-Name") ?? userId;
    const roomId = url.searchParams.get("room") ?? "unknown";

    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("expected websocket", { status: 426 });
    }

    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];

    // Hibernation: the runtime keeps the socket alive even if this DO is evicted.
    this.ctx.acceptWebSocket(server);
    const attachment: Attachment = { userId, name, roomId };
    server.serializeAttachment(attachment);

    this.send(server, { type: "ready", userId, roomId });
    // Tell the newcomer who is already online (presence snapshot), then announce them to others.
    for (const other of this.onlineUserIds(server)) {
      this.send(server, { type: "presence", userId: other, online: true });
    }
    this.broadcastPresence(userId, true, server);

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, raw: string | ArrayBuffer): Promise<void> {
    const auth = ws.deserializeAttachment() as Attachment | null;
    if (!auth) return;

    let frame: ClientFrame;
    try {
      frame = JSON.parse(typeof raw === "string" ? raw : new TextDecoder().decode(raw));
    } catch {
      this.send(ws, { type: "error", code: "bad_frame", message: "invalid JSON" });
      return;
    }

    switch (frame.type) {
      case "send":
        this.handleSend(ws, auth, frame.clientMsgId, frame.text);
        break;
      case "typing":
        this.broadcastTyping(auth.userId, frame.isTyping, ws);
        break;
      case "sync":
        this.handleSync(ws, frame.lastSeq);
        break;
      case "auth":
        // Auth already established by the Worker at upgrade time; no-op.
        break;
      default:
        this.send(ws, { type: "error", code: "bad_frame", message: "unknown type" });
    }
  }

  async webSocketClose(ws: WebSocket): Promise<void> {
    const auth = ws.deserializeAttachment() as Attachment | null;
    if (auth) {
      // Only flip to offline if this user has no other live sockets.
      if (!this.userHasOtherSockets(auth.userId, ws)) {
        this.broadcastPresence(auth.userId, false, ws);
      }
    }
  }

  // ---- handlers ----

  private handleSend(ws: WebSocket, auth: Attachment, clientMsgId: string, text: string) {
    if (!text || typeof text !== "string") {
      this.send(ws, { type: "error", code: "bad_frame", message: "missing text" });
      return;
    }
    const id = "srv_" + crypto.randomUUID();
    const ts = Date.now();

    const cursor = this.ctx.storage.sql.exec(
      `INSERT INTO messages (id, userId, name, text, ts, clientMsgId)
       VALUES (?, ?, ?, ?, ?, ?) RETURNING seq;`,
      id, auth.userId, auth.name, text, ts, clientMsgId ?? null,
    );
    const seq = Number(cursor.one().seq);

    // Confirm to sender, then fan out to everyone (including sender, who dedupes by clientMsgId).
    this.send(ws, { type: "ack", clientMsgId, id, seq, ts });
    const msg: ServerFrame & ChatMessage = {
      type: "message", id, seq, userId: auth.userId, name: auth.name, text, ts, clientMsgId,
    };
    this.broadcast(msg);
  }

  private handleSync(ws: WebSocket, lastSeq: number) {
    const since = Number.isFinite(lastSeq) ? lastSeq : 0;
    const rows = this.ctx.storage.sql
      .exec(
        `SELECT id, seq, userId, name, text, ts, clientMsgId
         FROM messages WHERE seq > ? ORDER BY seq ASC LIMIT 500;`,
        since,
      )
      .toArray() as unknown as ChatMessage[];
    this.send(ws, { type: "history", messages: rows });
  }

  // ---- helpers ----

  /** Distinct userIds currently connected, excluding the given socket. */
  private onlineUserIds(except: WebSocket): string[] {
    const ids = new Set<string>();
    for (const s of this.ctx.getWebSockets()) {
      if (s === except) continue;
      const a = s.deserializeAttachment() as Attachment | null;
      if (a?.userId) ids.add(a.userId);
    }
    return [...ids];
  }

  private userHasOtherSockets(userId: string, except: WebSocket): boolean {
    for (const s of this.ctx.getWebSockets()) {
      if (s === except) continue;
      const a = s.deserializeAttachment() as Attachment | null;
      if (a?.userId === userId) return true;
    }
    return false;
  }

  private send(ws: WebSocket, frame: ServerFrame) {
    try {
      ws.send(JSON.stringify(frame));
    } catch {
      /* socket closing — ignore */
    }
  }

  private broadcast(frame: ServerFrame, except?: WebSocket) {
    const data = JSON.stringify(frame);
    for (const s of this.ctx.getWebSockets()) {
      if (s === except) continue;
      try {
        s.send(data);
      } catch {
        /* ignore */
      }
    }
  }

  private broadcastPresence(userId: string, online: boolean, except?: WebSocket) {
    this.broadcast({ type: "presence", userId, online }, except);
  }

  private broadcastTyping(userId: string, isTyping: boolean, except: WebSocket) {
    this.broadcast({ type: "typing", userId, isTyping }, except);
  }
}
