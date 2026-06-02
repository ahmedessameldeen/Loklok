// Loklok wire protocol v1 — see docs/protocol.md (source of truth).
// Mirrored by the KMP model/ package. Keep all three in sync.

export interface ChatMessage {
  id: string;
  seq: number;
  userId: string;
  name: string;
  text: string;
  ts: number; // epoch ms
  clientMsgId?: string;
}

// ---- Client -> Server ----
export type ClientFrame =
  | { type: "auth"; token: string }
  | { type: "send"; clientMsgId: string; text: string }
  | { type: "typing"; isTyping: boolean }
  | { type: "sync"; lastSeq: number };

// ---- Server -> Client ----
export type ServerFrame =
  | { type: "ready"; userId: string; roomId: string }
  | { type: "ack"; clientMsgId: string; id: string; seq: number; ts: number }
  | { type: "message"; } & ChatMessage
  | { type: "history"; messages: ChatMessage[] }
  | { type: "presence"; userId: string; online: boolean }
  | { type: "typing"; userId: string; isTyping: boolean }
  | { type: "error"; code: ErrorCode; message: string };

export type ErrorCode = "auth_failed" | "bad_frame" | "internal";

// Identity attached to each socket after the JWT is verified.
export interface SessionAuth {
  userId: string;
  name: string;
}
