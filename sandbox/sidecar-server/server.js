import http from 'node:http';
import { WebSocketServer } from 'ws';
import {
  PORT,
  LISTEN_HOST,
  SIDECAR_MODE,
  ALLOWED_COMMANDS,
  GRACEFUL_TIMEOUT_MS,
  DETACH_TTL_MS,
  OUTPUT_BUFFER_MAX_BYTES,
} from './config.js';
import { sendJson } from './lib/http.js';
import { sessions } from './lib/session.js';
import { healthRoutes } from './routes/health.js';
import { sessionRoutes } from './routes/sessions.js';
import { fileRoutes } from './routes/files.js';
import { execRoutes } from './routes/exec.js';
import { handleTerminalUpgrade } from './ws/terminal.js';
import { handleCliUpgrade } from './ws/cli.js';

// ---------------------------------------------------------------------------
// HTTP 路由表
// ---------------------------------------------------------------------------

const allRoutes = [
  ...healthRoutes,
  ...sessionRoutes,
  ...fileRoutes,
  ...execRoutes,
];

// ---------------------------------------------------------------------------
// HTTP 服务器
// ---------------------------------------------------------------------------

const server = http.createServer(async (req, res) => {
  for (const route of allRoutes) {
    if (req.method === route.method && route.match(req.url)) {
      await route.handler(req, res);
      return;
    }
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

// ---------------------------------------------------------------------------
// WebSocket 服务器
// ---------------------------------------------------------------------------

const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  if (url.pathname === '/terminal') {
    handleTerminalUpgrade(wss, req, socket, head, url);
    return;
  }

  // 默认 CLI 端点
  handleCliUpgrade(wss, req, socket, head, url);
});

// ---------------------------------------------------------------------------
// SIGTERM 信号处理 - 优雅关闭
// ---------------------------------------------------------------------------

function killSessionProcess(session) {
  return new Promise((resolve) => {
    if (!session.process || session.process.killed) {
      resolve();
      return;
    }
    session.process.kill('SIGTERM');
    const killTimer = setTimeout(() => {
      if (session.process && !session.process.killed) {
        console.log(
          `[session:${session.id}] force killing process during shutdown`,
        );
        session.process.kill('SIGKILL');
      }
    }, GRACEFUL_TIMEOUT_MS);
    session.process.on('exit', () => {
      clearTimeout(killTimer);
      resolve();
    });
  });
}

async function gracefulShutdown() {
  console.log('Received SIGTERM, shutting down gracefully...');

  // 清除所有 detach 定时器，kill 所有进程
  const killPromises = [];
  for (const session of sessions.values()) {
    if (session.detachTimer) {
      clearTimeout(session.detachTimer);
      session.detachTimer = null;
    }
    session.outputBuffer.clear();
    killPromises.push(killSessionProcess(session));
  }

  // 关闭所有 WS 连接（attached 和 detached 都处理）
  for (const session of sessions.values()) {
    if (session.ws && session.ws.readyState === 1 /* OPEN */) {
      session.ws.close(1001, 'Server shutting down');
    }
  }

  await Promise.all(killPromises);

  wss.close();

  server.close(() => {
    console.log('Server closed, exiting.');
    process.exit(0);
  });

  setTimeout(() => {
    console.error('Forced exit after shutdown timeout');
    process.exit(1);
  }, GRACEFUL_TIMEOUT_MS + 1000).unref();
}

process.on('SIGTERM', gracefulShutdown);

// ---------------------------------------------------------------------------
// 启动服务器
// ---------------------------------------------------------------------------

export function startServer() {
  server.listen(PORT, LISTEN_HOST, () => {
    console.log(
      `Sidecar server listening on ${LISTEN_HOST}:${PORT} (mode: ${SIDECAR_MODE})`,
    );
    console.log(
      `Allowed commands: [${Array.from(ALLOWED_COMMANDS).join(', ')}]`,
    );
    console.log(
      `Graceful timeout: ${GRACEFUL_TIMEOUT_MS}ms, Detach TTL: ${DETACH_TTL_MS}ms, Buffer max: ${OUTPUT_BUFFER_MAX_BYTES} bytes`,
    );
  });
}
