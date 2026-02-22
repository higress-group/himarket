'use strict';

const http = require('http');
const { WebSocketServer } = require('ws');
const { spawn } = require('child_process');
const crypto = require('crypto');

// ---------------------------------------------------------------------------
// 配置 - 从环境变量读取
// ---------------------------------------------------------------------------
const PORT = parseInt(process.env.SIDECAR_PORT, 10) || 8080;
const ALLOWED_COMMANDS = new Set(
  (process.env.ALLOWED_COMMANDS || '')
    .split(',')
    .map(c => c.trim())
    .filter(Boolean)
);
const GRACEFUL_TIMEOUT_MS = parseInt(process.env.GRACEFUL_TIMEOUT_MS, 10) || 5000;

// ---------------------------------------------------------------------------
// 服务器状态
// ---------------------------------------------------------------------------

/** @type {Map<string, {id: string, ws: import('ws'), process: import('child_process').ChildProcess|null, command: string, args: string[], createdAt: Date}>} */
const sessions = new Map();

// ---------------------------------------------------------------------------
// HTTP 服务器
// ---------------------------------------------------------------------------
const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    const activeProcesses = Array.from(sessions.values()).filter(s => s.process !== null).length;
    const body = JSON.stringify({
      status: 'ok',
      connections: sessions.size,
      processes: activeProcesses,
    });
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(body);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

// ---------------------------------------------------------------------------
// WebSocket 服务器
// ---------------------------------------------------------------------------
const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  // 先完成 WebSocket 升级，再在回调中做校验
  wss.handleUpgrade(req, socket, head, (ws) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const command = url.searchParams.get('command');
    const argsRaw = url.searchParams.get('args') || '';
    const args = argsRaw ? argsRaw.split(' ').filter(Boolean) : [];

    // 校验 command 参数 - 通过 WebSocket 关闭帧返回错误
    if (!command) {
      ws.close(4400, 'Missing command parameter');
      return;
    }

    if (!ALLOWED_COMMANDS.has(command)) {
      ws.close(4403, `Command not allowed: ${command}`);
      return;
    }

    const sessionId = crypto.randomUUID();
    console.log(`[session:${sessionId}] connected - command=${command} args=[${args.join(', ')}]`);

    // 启动 CLI 子进程
    let cliProcess;
    try {
      cliProcess = spawn(command, args, {
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch (err) {
      console.error(`[session:${sessionId}] spawn failed:`, err.message);
      ws.send(JSON.stringify({ error: `Failed to start process: ${err.message}` }));
      ws.close(4500, 'Process spawn failed');
      return;
    }

    const session = {
      id: sessionId,
      ws,
      process: cliProcess,
      command,
      args,
      createdAt: new Date(),
    };
    sessions.set(sessionId, session);

    // --- 双向桥接 ---

    // WebSocket 消息 → CLI stdin
    ws.on('message', (data) => {
      if (cliProcess.stdin && !cliProcess.stdin.destroyed) {
        // JSON-RPC 消息以换行符分隔，确保每条消息后有 \n
        const msg = data.toString();
        cliProcess.stdin.write(msg.endsWith('\n') ? msg : msg + '\n');
      }
    });

    // CLI stdout → WebSocket 消息
    cliProcess.stdout.on('data', (chunk) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(chunk.toString());
      }
    });

    // CLI stderr → WebSocket 消息
    cliProcess.stderr.on('data', (chunk) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(chunk.toString());
      }
    });

    // --- 生命周期管理 ---

    // CLI 进程 spawn 错误（如命令不存在）
    cliProcess.on('error', (err) => {
      console.error(`[session:${sessionId}] process error:`, err.message);
      if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify({ error: `Process error: ${err.message}` }));
        ws.close(4500, 'Process error');
      }
      sessions.delete(sessionId);
    });

    // CLI 进程退出 → 关闭 WebSocket
    cliProcess.on('close', (code, signal) => {
      console.log(`[session:${sessionId}] process exited - code=${code} signal=${signal}`);
      if (ws.readyState === ws.OPEN) {
        ws.close(1000, `Process exited with code ${code}`);
      }
      session.process = null;
      sessions.delete(sessionId);
    });

    // WebSocket 关闭 → 终止 CLI 进程
    ws.on('close', () => {
      console.log(`[session:${sessionId}] disconnected`);
      if (session.process && !session.process.killed) {
        // 先 SIGTERM，超时后 SIGKILL
        session.process.kill('SIGTERM');
        const killTimer = setTimeout(() => {
          if (session.process && !session.process.killed) {
            console.log(`[session:${sessionId}] force killing process`);
            session.process.kill('SIGKILL');
          }
        }, GRACEFUL_TIMEOUT_MS);
        // 进程退出后清除定时器
        session.process.on('exit', () => clearTimeout(killTimer));
      }
      sessions.delete(sessionId);
    });

    ws.on('error', (err) => {
      console.error(`[session:${sessionId}] WebSocket error:`, err.message);
    });

    wss.emit('connection', ws, req);
  });
});

// ---------------------------------------------------------------------------
// SIGTERM 信号处理 - 优雅关闭（Requirements 7.2）
// ---------------------------------------------------------------------------

/**
 * 终止单个会话中的 CLI 子进程，先 SIGTERM 再 SIGKILL。
 * 返回一个 Promise，在进程退出后 resolve。
 */
function killSessionProcess(session) {
  return new Promise((resolve) => {
    if (!session.process || session.process.killed) {
      resolve();
      return;
    }
    session.process.kill('SIGTERM');
    const killTimer = setTimeout(() => {
      if (session.process && !session.process.killed) {
        console.log(`[session:${session.id}] force killing process during shutdown`);
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

  // 1. 终止所有活跃的 CLI 子进程
  const killPromises = [];
  for (const session of sessions.values()) {
    killPromises.push(killSessionProcess(session));
  }

  // 2. 关闭所有 WebSocket 连接
  for (const session of sessions.values()) {
    if (session.ws.readyState === session.ws.OPEN) {
      session.ws.close(1001, 'Server shutting down');
    }
  }

  // 等待所有子进程退出
  await Promise.all(killPromises);

  // 3. 关闭 WebSocket 服务器（停止接受新连接）
  wss.close();

  // 4. 关闭 HTTP 服务器
  server.close(() => {
    console.log('Server closed, exiting.');
    process.exit(0);
  });

  // 安全兜底：如果 server.close 回调未触发，强制退出
  setTimeout(() => {
    console.error('Forced exit after shutdown timeout');
    process.exit(1);
  }, GRACEFUL_TIMEOUT_MS + 1000).unref();
}

process.on('SIGTERM', gracefulShutdown);

// ---------------------------------------------------------------------------
// 启动服务器
// ---------------------------------------------------------------------------
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Sidecar server listening on 0.0.0.0:${PORT}`);
  console.log(`Allowed commands: [${Array.from(ALLOWED_COMMANDS).join(', ')}]`);
  console.log(`Graceful timeout: ${GRACEFUL_TIMEOUT_MS}ms`);
});

// ---------------------------------------------------------------------------
// 导出供测试使用
// ---------------------------------------------------------------------------
module.exports = { server, wss, sessions, ALLOWED_COMMANDS, GRACEFUL_TIMEOUT_MS, PORT, gracefulShutdown };
