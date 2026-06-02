// Loklok Worker — entry point.
// Routes:
//   GET /                      -> health text
//   GET /room/:roomId/ws?token -> verify JWT, forward the WS upgrade to the room's DO
//
// The Worker is the only caller of the DO. It verifies the JWT here and passes the
// trusted identity to the DO via X-Loklok-* headers.

import { RoomDO } from "./RoomDO";
import { verifyToken } from "./jwt";

export { RoomDO };

interface Env {
  ROOMS: DurableObjectNamespace;
  LOKLOK_JWT_SECRET: string;
}

const ROOM_RE = /^\/room\/([^/]+)\/ws$/;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/") {
      return new Response("loklok ok", { headers: { "content-type": "text/plain" } });
    }

    const match = url.pathname.match(ROOM_RE);
    if (match) {
      if (request.headers.get("Upgrade") !== "websocket") {
        return new Response("expected websocket", { status: 426 });
      }
      const roomId = decodeURIComponent(match[1]);
      const token = url.searchParams.get("token") ?? "";
      const auth = await verifyToken(token, env.LOKLOK_JWT_SECRET);
      if (!auth) {
        return new Response("auth_failed", { status: 401 });
      }

      // Route to the DO instance for this room (idFromName is stable per room id).
      const id = env.ROOMS.idFromName(roomId);
      const stub = env.ROOMS.get(id);

      const fwd = new Request(`https://do/?room=${encodeURIComponent(roomId)}`, request);
      fwd.headers.set("X-Loklok-User", auth.userId);
      fwd.headers.set("X-Loklok-Name", auth.name);
      return stub.fetch(fwd);
    }

    return new Response("not found", { status: 404 });
  },
};
