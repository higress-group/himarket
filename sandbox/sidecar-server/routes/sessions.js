import { sendJson } from '../lib/http.js';
import {
  sessions,
  serializeSession,
  destroySession,
} from '../lib/session.js';

// ---------------------------------------------------------------------------
// Session 管理端点
// ---------------------------------------------------------------------------

export const sessionRoutes = [
  // GET /sessions — 列出所有 CLI session
  {
    method: 'GET',
    match: (url) => url === '/sessions',
    handler: async (_req, res) => {
      const list = Array.from(sessions.values()).map(serializeSession);
      sendJson(res, 200, list);
    },
  },

  // GET /sessions/:id — 查询单个 session
  {
    method: 'GET',
    match: (url) =>
      url.startsWith('/sessions/') &&
      !url.includes('/', '/sessions/'.length),
    handler: async (req, res) => {
      const id = req.url.slice('/sessions/'.length);
      const session = sessions.get(id);
      if (!session) {
        return sendJson(res, 404, { error: 'Session not found' });
      }
      sendJson(res, 200, serializeSession(session));
    },
  },

  // DELETE /sessions/:id — 强制销毁 session
  {
    method: 'DELETE',
    match: (url) =>
      url.startsWith('/sessions/') &&
      !url.includes('/', '/sessions/'.length),
    handler: async (req, res) => {
      const id = req.url.slice('/sessions/'.length);
      const session = sessions.get(id);
      if (!session) {
        return sendJson(res, 404, { error: 'Session not found' });
      }
      destroySession(id, 'Manual DELETE');
      sendJson(res, 200, { success: true });
    },
  },
];
