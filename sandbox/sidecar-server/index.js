'use strict';

const http = require('http');
const fs = require('fs/promises');
const path = require('path');
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
const WORKSPACE_ROOT = process.env.WORKSPACE_ROOT || process.cwd();
const SIDECAR_MODE = process.env.SIDECAR_MODE || 'k8s';

// ---------------------------------------------------------------------------
// 路径安全校验
// ---------------------------------------------------------------------------

/**
 * 安全路径解析：确保所有文件操作限制在工作空间内。
 * 防止路径遍历攻击（如 ../../etc/passwd）。
 */
function resolveSafePath(relativePath) {
  const resolved = path.resolve(WORKSPACE_ROOT, relativePath);
  const root = path.resolve(WORKSPACE_ROOT);
  if (!resolved.startsWith(root + path.sep) && resolved !== root) {
    throw new Error('路径越界: ' + relativePath);
  }
  return resolved;
}

// ---------------------------------------------------------------------------
// 请求体解析
// ---------------------------------------------------------------------------

/**
 * 解析 JSON 请求体。返回 Promise<object>。
 */
function parseJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

// ---------------------------------------------------------------------------
// JSON 响应辅助
// ---------------------------------------------------------------------------

function sendJson(res, statusCode, data) {
  const body = JSON.stringify(data);
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(body);
}

// ---------------------------------------------------------------------------
// 服务器状态
// ---------------------------------------------------------------------------

/** @type {Map<string, {id: string, ws: import('ws'), process: import('child_process').ChildProcess|null, command: string, args: string[], createdAt: Date}>} */
const sessions = new Map();

// ---------------------------------------------------------------------------
// HTTP 服务器
// ---------------------------------------------------------------------------
const server = http.createServer(async (req, res) => {
  // --- 健康检查 ---
  if (req.method === 'GET' && req.url === '/health') {
    const activeProcesses = Array.from(sessions.values()).filter(s => s.process !== null).length;
    sendJson(res, 200, {
      status: 'ok',
      connections: sessions.size,
      processes: activeProcesses,
    });
    return;
  }

  // --- 文件操作端点 ---

  // POST /files/write
  if (req.method === 'POST' && req.url === '/files/write') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: filePath, content } = body;
    if (!filePath || content === undefined || content === null) {
      return sendJson(res, 400, { success: false, error: '缺少 path 或 content 参数' });
    }
    try {
      const fullPath = resolveSafePath(filePath);
      await fs.mkdir(path.dirname(fullPath), { recursive: true });
      await fs.writeFile(fullPath, content, 'utf-8');
      sendJson(res, 200, { success: true });
    } catch (err) {
      if (err.message.startsWith('路径越界')) {
        return sendJson(res, 403, { success: false, error: err.message });
      }
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // POST /files/read
  if (req.method === 'POST' && req.url === '/files/read') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: filePath } = body;
    if (!filePath) {
      return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
    }
    try {
      const fullPath = resolveSafePath(filePath);
      const content = await fs.readFile(fullPath, 'utf-8');
      sendJson(res, 200, { content });
    } catch (err) {
      if (err.message.startsWith('路径越界')) {
        return sendJson(res, 403, { success: false, error: err.message });
      }
      const status = err.code === 'ENOENT' ? 404 : 500;
      sendJson(res, status, { success: false, error: err.message });
    }
    return;
  }

  // POST /files/mkdir
  if (req.method === 'POST' && req.url === '/files/mkdir') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: dirPath } = body;
    if (!dirPath) {
      return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
    }
    try {
      const fullPath = resolveSafePath(dirPath);
      await fs.mkdir(fullPath, { recursive: true });
      sendJson(res, 200, { success: true });
    } catch (err) {
      if (err.message.startsWith('路径越界')) {
        return sendJson(res, 403, { success: false, error: err.message });
      }
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // POST /files/exists
  if (req.method === 'POST' && req.url === '/files/exists') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: filePath } = body;
    if (!filePath) {
      return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
    }
    try {
      const fullPath = resolveSafePath(filePath);
      const stat = await fs.stat(fullPath);
      sendJson(res, 200, { exists: true, isFile: stat.isFile(), isDirectory: stat.isDirectory() });
    } catch (err) {
      if (err.message.startsWith('路径越界')) {
        return sendJson(res, 403, { success: false, error: err.message });
      }
      if (err.code === 'ENOENT') {
        sendJson(res, 200, { exists: false, isFile: false, isDirectory: false });
      } else {
        sendJson(res, 500, { success: false, error: err.message });
      }
    }
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
      // 构建干净的基础环境变量（白名单），避免 Sidecar 进程复用时
      // 上一次会话注入的认证凭据（如 QODER_PERSONAL_ACCESS_TOKEN）残留到后续会话
      const BASE_ENV_KEYS = [
        'PATH', 'HOME', 'USER', 'SHELL', 'LANG', 'LC_ALL', 'LC_CTYPE',
        'TERM', 'TMPDIR', 'XDG_RUNTIME_DIR', 'XDG_CONFIG_HOME', 'XDG_DATA_HOME',
        'NODE_PATH', 'NVM_DIR', 'FNM_DIR',
      ];
      const baseEnv = {};
      for (const key of BASE_ENV_KEYS) {
        if (process.env[key] !== undefined) {
          baseEnv[key] = process.env[key];
        }
      }
      // 保留 SIDECAR_ 和 WORKSPACE_ 前缀的变量（Sidecar 自身配置）
      for (const [key, val] of Object.entries(process.env)) {
        if (key.startsWith('SIDECAR_') || key.startsWith('WORKSPACE_') || key.startsWith('ALLOWED_')) {
          baseEnv[key] = val;
        }
      }

      // 合并通过 WebSocket URL 传入的额外环境变量（每次会话动态传递）
      const envRaw = url.searchParams.get('env');
      let extraEnv = {};
      if (envRaw) {
        try {
          extraEnv = JSON.parse(decodeURIComponent(envRaw));
          console.log(`[session:${sessionId}] injecting env vars: ${Object.keys(extraEnv).join(', ')}`);
        } catch (e) {
          console.warn(`[session:${sessionId}] failed to parse env param: ${e.message}`);
        }
      }

      const spawnEnv = { ...baseEnv, ...extraEnv };

      cliProcess = spawn(command, args, {
        stdio: ['pipe', 'pipe', 'pipe'],
        env: spawnEnv,
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
const LISTEN_HOST = SIDECAR_MODE === 'local' ? '127.0.0.1' : '0.0.0.0';
server.listen(PORT, LISTEN_HOST, () => {
  console.log(`Sidecar server listening on ${LISTEN_HOST}:${PORT} (mode: ${SIDECAR_MODE})`);
  console.log(`Allowed commands: [${Array.from(ALLOWED_COMMANDS).join(', ')}]`);
  console.log(`Graceful timeout: ${GRACEFUL_TIMEOUT_MS}ms`);
});

// ---------------------------------------------------------------------------
// 导出供测试使用
// ---------------------------------------------------------------------------
module.exports = { server, wss, sessions, ALLOWED_COMMANDS, GRACEFUL_TIMEOUT_MS, PORT, WORKSPACE_ROOT, SIDECAR_MODE, LISTEN_HOST, gracefulShutdown, resolveSafePath, parseJsonBody, sendJson };
