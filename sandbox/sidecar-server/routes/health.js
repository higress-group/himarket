import { sendJson } from '../lib/http.js';
import { sessions } from '../lib/session.js';

// ---------------------------------------------------------------------------
// GET /health — 健康检查
// ---------------------------------------------------------------------------

export const healthRoutes = [
  {
    method: 'GET',
    match: (url) => url === '/health',
    handler: async (_req, res) => {
      const allSessions = Array.from(sessions.values());
      const attached = allSessions.filter(
        (s) => s.state === 'attached',
      ).length;
      const detached = allSessions.filter(
        (s) => s.state === 'detached',
      ).length;
      const processes = allSessions.filter(
        (s) => s.process !== null && !s.process.killed,
      ).length;
      const bufferBytes = allSessions.reduce(
        (sum, s) => sum + s.outputBuffer.totalBytes,
        0,
      );
      sendJson(res, 200, {
        status: 'ok',
        sessions: { total: sessions.size, attached, detached },
        processes,
        bufferBytes,
      });
    },
  },
];
