'use strict';

const http = require('http');
const fs = require('fs/promises');
const path = require('path');
const { WebSocketServer } = require('ws');
const { spawn } = require('child_process');
const crypto = require('crypto');
let pty;
try {
  pty = require('node-pty');
} catch {
  // node-pty 可选依赖，未安装时 /terminal 端点不可用
  pty = null;
}

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
const SIDECAR_MODE = process.env.SIDECAR_MODE || 'k8s';

// WORKSPACE_ROOT 仅用于兜底默认值（如 /files/extract 未指定 cwd 时），
// 不再作为所有文件操作的路径基准。
// 参考 OpenSandbox execd 设计：文件操作接收绝对路径，由调用方负责构建。
const WORKSPACE_ROOT = process.env.WORKSPACE_ROOT || '/workspace';

// ---------------------------------------------------------------------------
// 路径安全校验
// ---------------------------------------------------------------------------

/**
 * 解析并校验路径。
 *
 * 设计参考 OpenSandbox execd：接受绝对路径，由调用方负责构建正确路径。
 * 安全边界由容器本身提供（沙箱隔离），不在应用层做路径限制。
 *
 * 对于相对路径，以 WORKSPACE_ROOT 为基准解析（向后兼容）。
 */
function resolvePath(inputPath) {
  if (path.isAbsolute(inputPath)) {
    return path.resolve(inputPath);
  }
  // 向后兼容：相对路径以 WORKSPACE_ROOT 为基准
  return path.resolve(WORKSPACE_ROOT, inputPath);
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
  // 设计参考 OpenSandbox execd：所有路径参数接受绝对路径，
  // 由调用方（后端 Java 服务）负责构建正确的用户隔离路径。

  // POST /files/write — 写入文件（支持绝对路径）
  if (req.method === 'POST' && req.url === '/files/write') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: filePath, content } = body;
    if (!filePath || content === undefined || content === null) {
      return sendJson(res, 400, { success: false, error: '缺少 path 或 content 参数' });
    }
    try {
      const fullPath = resolvePath(filePath);
      await fs.mkdir(path.dirname(fullPath), { recursive: true });
      await fs.writeFile(fullPath, content, 'utf-8');
      sendJson(res, 200, { success: true });
    } catch (err) {
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // POST /files/read — 读取文件（支持绝对路径）
  // 支持 encoding 参数：'base64' 返回 base64 编码（用于二进制文件），默认 'utf-8'
  if (req.method === 'POST' && req.url === '/files/read') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: filePath, encoding: reqEncoding } = body;
    if (!filePath) {
      return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
    }
    try {
      const fullPath = resolvePath(filePath);
      if (reqEncoding === 'base64') {
        const buffer = await fs.readFile(fullPath);
        sendJson(res, 200, { content: buffer.toString('base64'), encoding: 'base64' });
      } else {
        const content = await fs.readFile(fullPath, 'utf-8');
        sendJson(res, 200, { content, encoding: 'utf-8' });
      }
    } catch (err) {
      const status = err.code === 'ENOENT' ? 404 : 500;
      sendJson(res, status, { success: false, error: err.message });
    }
    return;
  }

  // GET /files/download?path=xxx — 下载文件原始二进制流
  // 兼容 OpenSandbox execd 的 /files/download 端点设计，
  // 直接返回文件原始字节，不经过 JSON/base64 编码，适用于二进制文件下载。
  if (req.method === 'GET' && (req.url === '/files/download' || req.url.startsWith('/files/download?'))) {
    const reqUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const filePath = reqUrl.searchParams.get('path');
    if (!filePath) {
      return sendJson(res, 400, { success: false, error: "missing query parameter 'path'" });
    }
    try {
      const fullPath = resolvePath(filePath);
      const stat = await fs.stat(fullPath);
      if (!stat.isFile()) {
        return sendJson(res, 400, { success: false, error: '指定路径不是文件' });
      }
      const fileName = path.basename(fullPath);
      res.writeHead(200, {
        'Content-Type': 'application/octet-stream',
        'Content-Disposition': `attachment; filename="${fileName}"`,
        'Content-Length': stat.size,
      });
      const { createReadStream } = require('fs');
      createReadStream(fullPath).pipe(res);
    } catch (err) {
      const status = err.code === 'ENOENT' ? 404 : 500;
      sendJson(res, status, { success: false, error: err.message });
    }
    return;
  }

  // POST /files/mkdir — 创建目录（支持绝对路径）
  if (req.method === 'POST' && req.url === '/files/mkdir') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: dirPath } = body;
    if (!dirPath) {
      return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
    }
    try {
      const fullPath = resolvePath(dirPath);
      await fs.mkdir(fullPath, { recursive: true });
      sendJson(res, 200, { success: true });
    } catch (err) {
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // POST /files/extract — 接收 tar.gz 二进制流，解压到指定目录
  // 支持 query 参数 ?cwd=/workspace/dev-xxx 指定解压目标目录
  // 未指定时降级为 WORKSPACE_ROOT（向后兼容）
  if (req.method === 'POST' && (req.url === '/files/extract' || req.url.startsWith('/files/extract?'))) {
    const reqUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const extractCwd = reqUrl.searchParams.get('cwd') || WORKSPACE_ROOT;

    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', async () => {
      try {
        const buffer = Buffer.concat(chunks);
        if (buffer.length === 0) {
          return sendJson(res, 400, { success: false, error: '请求体为空' });
        }

        // 确保目标目录存在
        const fsSync = require('fs');
        if (!fsSync.existsSync(extractCwd)) {
          fsSync.mkdirSync(extractCwd, { recursive: true });
        }

        // 写入临时文件，用 tar 命令解压
        const tmpFile = path.join(extractCwd, '.tmp-extract-' + Date.now() + '.tar.gz');
        await fs.writeFile(tmpFile, buffer);
        try {
          const { execSync } = require('child_process');
          execSync(`tar xzf "${tmpFile}" 2>&1`, {
            cwd: extractCwd,
            timeout: 30000,
            maxBuffer: 1024 * 1024,
          });
          // 统计解压的文件数
          let fileCount = 0;
          try {
            const listOutput = execSync(`tar tzf "${tmpFile}" 2>/dev/null`, {
              cwd: extractCwd,
              timeout: 10000,
              maxBuffer: 1024 * 1024,
            }).toString().trim();
            fileCount = listOutput ? listOutput.split('\n').filter(l => !l.endsWith('/')).length : 0;
          } catch { fileCount = -1; }
          sendJson(res, 200, { success: true, fileCount });
        } finally {
          await fs.unlink(tmpFile).catch(() => {});
        }
      } catch (err) {
        sendJson(res, 500, { success: false, error: err.message });
      }
    });
    req.on('error', (err) => {
      sendJson(res, 500, { success: false, error: err.message });
    });
    return;
  }

  // POST /files/list — 列出目录内容（支持绝对路径）
  if (req.method === 'POST' && req.url === '/files/list') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: dirPath, depth } = body;
    if (!dirPath) {
      return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
    }
    const maxDepth = (typeof depth === 'number' && depth > 0) ? depth : 3;
    try {
      const fullPath = resolvePath(dirPath);
      const stat = await fs.stat(fullPath);
      if (!stat.isDirectory()) {
        return sendJson(res, 400, { success: false, error: '指定路径不是目录' });
      }

      async function buildTree(currentPath, currentDepth) {
        const entries = await fs.readdir(currentPath, { withFileTypes: true });
        const result = [];
        for (const entry of entries) {
          if (entry.isFile()) {
            result.push({ name: entry.name, type: 'file' });
          } else if (entry.isDirectory()) {
            const node = { name: entry.name, type: 'dir', children: [] };
            if (currentDepth < maxDepth) {
              node.children = await buildTree(path.join(currentPath, entry.name), currentDepth + 1);
            }
            result.push(node);
          }
        }
        return result;
      }

      const tree = await buildTree(fullPath, 1);
      sendJson(res, 200, tree);
    } catch (err) {
      if (err.code === 'ENOENT') {
        return sendJson(res, 404, { success: false, error: '路径不存在: ' + dirPath });
      }
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // POST /files/changes — 查询文件变更（支持绝对路径）
  if (req.method === 'POST' && req.url === '/files/changes') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { cwd: cwdPath, since } = body;
    if (!cwdPath) {
      return sendJson(res, 400, { success: false, error: '缺少 cwd 参数' });
    }
    const sinceMs = (typeof since === 'number' && since > 0) ? since : 0;
    const MAX_DEPTH = 10;
    const SKIP_DIRS = new Set(['node_modules', '.git', '.next', 'dist', '__pycache__', '.cache']);
    try {
      const fullPath = resolvePath(cwdPath);
      const changes = [];

      async function scan(dir, depth) {
        if (depth > MAX_DEPTH) return;
        let entries;
        try { entries = await fs.readdir(dir, { withFileTypes: true }); } catch { return; }
        for (const entry of entries) {
          const entryPath = path.join(dir, entry.name);
          if (entry.isDirectory()) {
            if (!SKIP_DIRS.has(entry.name)) {
              await scan(entryPath, depth + 1);
            }
          } else if (entry.isFile()) {
            try {
              const stat = await fs.stat(entryPath);
              if (stat.mtimeMs > sinceMs) {
                changes.push({
                  path: path.relative(fullPath, entryPath),
                  mtimeMs: stat.mtimeMs,
                  size: stat.size,
                  ext: path.extname(entry.name),
                });
              }
            } catch { /* skip inaccessible files */ }
          }
        }
      }

      await scan(fullPath, 0);
      sendJson(res, 200, { changes });
    } catch (err) {
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // POST /exec — 在沙箱内执行命令并返回结果
  // 请求体: {"command": "nacos-cli", "args": ["skill-get", "skill1", "--config", "..."]}
  // 响应体: {"exitCode": 0, "stdout": "...", "stderr": "..."}
  if (req.method === 'POST' && req.url === '/exec') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { error: '无效的 JSON 请求体' }); }
    const { command, args: cmdArgs, cwd: execCwd, timeout: execTimeout } = body;
    if (!command) {
      return sendJson(res, 400, { error: '缺少 command 参数' });
    }
    const spawnArgs = Array.isArray(cmdArgs) ? cmdArgs : [];
    const spawnCwd = execCwd ? resolvePath(execCwd) : WORKSPACE_ROOT;
    const timeoutMs = (typeof execTimeout === 'number' && execTimeout > 0) ? execTimeout : 120000;

    try {
      const { execFile } = require('child_process');
      const result = await new Promise((resolve, reject) => {
        const proc = execFile(command, spawnArgs, {
          cwd: spawnCwd,
          timeout: timeoutMs,
          maxBuffer: 10 * 1024 * 1024,
          env: { ...process.env },
        }, (err, stdout, stderr) => {
          if (err && err.killed) {
            // 超时被 kill
            resolve({ exitCode: 124, stdout: stdout || '', stderr: (stderr || '') + '\nProcess timed out' });
          } else if (err && err.code === 'ENOENT') {
            resolve({ exitCode: 127, stdout: '', stderr: `Command not found: ${command}` });
          } else if (err) {
            resolve({ exitCode: err.code || 1, stdout: stdout || '', stderr: stderr || err.message });
          } else {
            resolve({ exitCode: 0, stdout: stdout || '', stderr: stderr || '' });
          }
        });
      });
      sendJson(res, 200, result);
    } catch (err) {
      sendJson(res, 500, { error: err.message });
    }
    return;
  }

  // POST /files/exists — 检查文件是否存在（支持绝对路径）
  if (req.method === 'POST' && req.url === '/files/exists') {
    let body;
    try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
    const { path: filePath } = body;
    if (!filePath) {
      return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
    }
    try {
      const fullPath = resolvePath(filePath);
      const stat = await fs.stat(fullPath);
      sendJson(res, 200, { exists: true, isFile: stat.isFile(), isDirectory: stat.isDirectory() });
    } catch (err) {
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
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  // --- /terminal 端点：交互式 PTY shell ---
  if (url.pathname === '/terminal') {
    if (!pty) {
      socket.write('HTTP/1.1 501 Not Implemented\r\n\r\n');
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => {
      const cols = parseInt(url.searchParams.get('cols'), 10) || 80;
      const rows = parseInt(url.searchParams.get('rows'), 10) || 24;
      const cwd = url.searchParams.get('cwd') || WORKSPACE_ROOT;
      const sessionId = crypto.randomUUID();

      console.log(`[terminal:${sessionId}] connected - cols=${cols} rows=${rows} cwd=${cwd}`);

      // 确保 cwd 目录存在，避免 shell 因目录不存在而退出
      try {
        const fsSync = require('fs');
        if (!fsSync.existsSync(cwd)) {
          fsSync.mkdirSync(cwd, { recursive: true });
          console.log(`[terminal:${sessionId}] created missing cwd: ${cwd}`);
        }
      } catch (mkdirErr) {
        console.error(`[terminal:${sessionId}] failed to create cwd:`, mkdirErr.message);
      }

      let shell;
      try {
        shell = pty.spawn('/bin/sh', ['-l'], {
          name: 'xterm-256color',
          cols,
          rows,
          cwd,
          env: { ...process.env, TERM: 'xterm-256color' },
        });
      } catch (err) {
        console.error(`[terminal:${sessionId}] pty spawn failed:`, err.message);
        ws.close(4500, 'PTY spawn failed');
        return;
      }

      const session = {
        id: sessionId,
        ws,
        process: shell,
        command: '/bin/sh',
        args: ['-l'],
        createdAt: new Date(),
      };
      sessions.set(sessionId, session);

      // PTY stdout → WebSocket (binary)
      shell.onData((data) => {
        if (ws.readyState === ws.OPEN) {
          ws.send(data);
        }
      });

      shell.onExit(({ exitCode, signal }) => {
        console.log(`[terminal:${sessionId}] shell exited - code=${exitCode} signal=${signal}`);
        if (ws.readyState === ws.OPEN) {
          ws.close(1000, `Shell exited with code ${exitCode}`);
        }
        session.process = null;
        sessions.delete(sessionId);
      });

      // WebSocket → PTY stdin / resize
      ws.on('message', (data) => {
        const msg = data.toString();
        if (msg.startsWith('{')) {
          try {
            const parsed = JSON.parse(msg);
            if (parsed.type === 'resize' && parsed.cols && parsed.rows) {
              shell.resize(parsed.cols, parsed.rows);
              return;
            }
          } catch { /* 不是 JSON，当作普通输入 */ }
        }
        shell.write(msg);
      });

      ws.on('close', () => {
        console.log(`[terminal:${sessionId}] disconnected`);
        if (session.process) {
          shell.kill();
        }
        sessions.delete(sessionId);
      });

      ws.on('error', (err) => {
        console.error(`[terminal:${sessionId}] WebSocket error:`, err.message);
      });
    });
    return;
  }

  // --- 默认 CLI 端点：spawn CLI 子进程 ---
  wss.handleUpgrade(req, socket, head, (ws) => {
    const command = url.searchParams.get('command');
    const argsRaw = url.searchParams.get('args') || '';
    const args = argsRaw ? argsRaw.split(' ').filter(Boolean) : [];

    if (!command) {
      ws.close(4400, 'Missing command parameter');
      return;
    }

    if (!ALLOWED_COMMANDS.has(command)) {
      ws.close(4403, `Command not allowed: ${command}`);
      return;
    }

    const sessionId = crypto.randomUUID();
    const cliCwd = url.searchParams.get('cwd') || WORKSPACE_ROOT;
    console.log(`[session:${sessionId}] connected - command=${command} args=[${args.join(', ')}] cwd=${cliCwd}`);

    let cliProcess;
    try {
      // 构建干净的基础环境变量（白名单）
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
      for (const [key, val] of Object.entries(process.env)) {
        if (key.startsWith('SIDECAR_') || key.startsWith('WORKSPACE_') || key.startsWith('ALLOWED_')) {
          baseEnv[key] = val;
        }
      }

      // 合并通过 WebSocket URL 传入的额外环境变量
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

      // 确保 cwd 目录存在
      const fsSync = require('fs');
      if (!fsSync.existsSync(cliCwd)) {
        fsSync.mkdirSync(cliCwd, { recursive: true });
        console.log(`[session:${sessionId}] created missing cwd: ${cliCwd}`);
      }

      cliProcess = spawn(command, args, {
        stdio: ['pipe', 'pipe', 'pipe'],
        cwd: cliCwd,
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

    ws.on('message', (data) => {
      if (cliProcess.stdin && !cliProcess.stdin.destroyed) {
        const msg = data.toString();
        cliProcess.stdin.write(msg.endsWith('\n') ? msg : msg + '\n');
      }
    });

    cliProcess.stdout.on('data', (chunk) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(chunk.toString());
      }
    });

    cliProcess.stderr.on('data', (chunk) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(chunk.toString());
      }
    });

    cliProcess.on('error', (err) => {
      console.error(`[session:${sessionId}] process error:`, err.message);
      if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify({ error: `Process error: ${err.message}` }));
        ws.close(4500, 'Process error');
      }
      sessions.delete(sessionId);
    });

    cliProcess.on('close', (code, signal) => {
      console.log(`[session:${sessionId}] process exited - code=${code} signal=${signal}`);
      if (ws.readyState === ws.OPEN) {
        ws.close(1000, `Process exited with code ${code}`);
      }
      session.process = null;
      sessions.delete(sessionId);
    });

    ws.on('close', () => {
      console.log(`[session:${sessionId}] disconnected`);
      if (session.process && !session.process.killed) {
        session.process.kill('SIGTERM');
        const killTimer = setTimeout(() => {
          if (session.process && !session.process.killed) {
            console.log(`[session:${sessionId}] force killing process`);
            session.process.kill('SIGKILL');
          }
        }, GRACEFUL_TIMEOUT_MS);
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

  const killPromises = [];
  for (const session of sessions.values()) {
    killPromises.push(killSessionProcess(session));
  }

  for (const session of sessions.values()) {
    if (session.ws.readyState === session.ws.OPEN) {
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
const LISTEN_HOST = SIDECAR_MODE === 'local' ? '127.0.0.1' : '0.0.0.0';
server.listen(PORT, LISTEN_HOST, () => {
  console.log(`Sidecar server listening on ${LISTEN_HOST}:${PORT} (mode: ${SIDECAR_MODE})`);
  console.log(`Allowed commands: [${Array.from(ALLOWED_COMMANDS).join(', ')}]`);
  console.log(`Graceful timeout: ${GRACEFUL_TIMEOUT_MS}ms`);
});

// ---------------------------------------------------------------------------
// 导出供测试使用
// ---------------------------------------------------------------------------
module.exports = { server, wss, sessions, ALLOWED_COMMANDS, GRACEFUL_TIMEOUT_MS, PORT, WORKSPACE_ROOT, SIDECAR_MODE, LISTEN_HOST, gracefulShutdown, resolvePath, parseJsonBody, sendJson };
