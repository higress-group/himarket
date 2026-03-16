import crypto from 'node:crypto';
import fsSync from 'node:fs';
import { spawn } from 'node:child_process';
import {
  ALLOWED_COMMANDS,
  WORKSPACE_ROOT,
  OUTPUT_BUFFER_MAX_BYTES,
} from '../config.js';
import { RingBuffer } from '../lib/ring-buffer.js';
import {
  sessions,
  routeOutput,
  attachWs,
  destroySession,
  handleProcessExit,
  handleProcessError,
} from '../lib/session.js';

// ---------------------------------------------------------------------------
// 默认 CLI 端点：session 管理 (新建 / attach)
// ---------------------------------------------------------------------------

export function handleCliUpgrade(wss, req, socket, head, url) {
  wss.handleUpgrade(req, socket, head, (ws) => {
    const requestedSessionId = url.searchParams.get('sessionId');

    // ========== Attach 到已有 session ==========
    if (requestedSessionId) {
      const session = sessions.get(requestedSessionId);
      if (!session) {
        ws.close(4404, 'Session not found');
        return;
      }
      if (session.state === 'destroying') {
        ws.close(4410, 'Session is being destroyed');
        return;
      }

      console.log(
        `[session:${requestedSessionId}] attach requested, current state=${session.state}`,
      );

      // 如果已有旧 WS 连接，踢掉旧连接
      if (session.ws && session.ws.readyState === 1 /* OPEN */) {
        console.log(
          `[session:${requestedSessionId}] kicking old WS connection`,
        );
        session.ws.removeAllListeners('close');
        session.ws.removeAllListeners('message');
        session.ws.close(4409, 'Replaced by new connection');
      }

      // 清除 detach 定时器
      if (session.detachTimer) {
        clearTimeout(session.detachTimer);
        session.detachTimer = null;
      }

      // 绑定新 WS
      session.ws = ws;
      session.state = 'attached';
      session.lastActivityAt = new Date();

      // 发送 session 元信息
      ws.send(
        JSON.stringify({
          type: 'session_meta',
          sessionId: session.id,
          replayed: true,
        }),
      );

      // Replay 缓冲的输出
      const { chunks, droppedBytes } = session.outputBuffer.drain();
      if (droppedBytes > 0) {
        ws.send(JSON.stringify({ type: 'buffer_truncated', droppedBytes }));
      }
      for (const chunk of chunks) {
        if (ws.readyState === 1) {
          ws.send(chunk);
        }
      }

      // 绑定 WS 事件
      attachWs(session, ws);

      console.log(
        `[session:${requestedSessionId}] attached, replayed ${chunks.length} chunks (dropped ${droppedBytes} bytes)`,
      );
      wss.emit('connection', ws, req);
      return;
    }

    // ========== 新建 session ==========
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
    console.log(
      `[session:${sessionId}] new session - command=${command} args=[${args.join(', ')}] cwd=${cliCwd}`,
    );

    let cliProcess;
    let spawnEnv;
    try {
      // 构建干净的基础环境变量（白名单）
      const BASE_ENV_KEYS = [
        'PATH',
        'HOME',
        'USER',
        'SHELL',
        'LANG',
        'LC_ALL',
        'LC_CTYPE',
        'TERM',
        'TMPDIR',
        'XDG_RUNTIME_DIR',
        'XDG_CONFIG_HOME',
        'XDG_DATA_HOME',
        'NODE_PATH',
        'NVM_DIR',
        'FNM_DIR',
      ];
      const baseEnv = {};
      for (const key of BASE_ENV_KEYS) {
        if (process.env[key] !== undefined) {
          baseEnv[key] = process.env[key];
        }
      }
      for (const [key, val] of Object.entries(process.env)) {
        if (
          key.startsWith('SIDECAR_') ||
          key.startsWith('WORKSPACE_') ||
          key.startsWith('ALLOWED_')
        ) {
          baseEnv[key] = val;
        }
      }

      // 合并通过 WebSocket URL 传入的额外环境变量
      const envRaw = url.searchParams.get('env');
      let extraEnv = {};
      if (envRaw) {
        try {
          extraEnv = JSON.parse(decodeURIComponent(envRaw));
          console.log(
            `[session:${sessionId}] injecting env vars: ${Object.keys(extraEnv).join(', ')}`,
          );
        } catch (e) {
          console.warn(
            `[session:${sessionId}] failed to parse env param: ${e.message}`,
          );
        }
      }

      spawnEnv = { ...baseEnv, ...extraEnv };

      // 确保 cwd 目录存在
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
      ws.send(
        JSON.stringify({
          error: `Failed to start process: ${err.message}`,
        }),
      );
      ws.close(4500, 'Process spawn failed');
      return;
    }

    const now = new Date();
    const session = {
      id: sessionId,
      state: 'attached',
      ws,
      process: cliProcess,
      command,
      args,
      env: spawnEnv,
      cwd: cliCwd,
      createdAt: now,
      lastActivityAt: now,
      outputBuffer: new RingBuffer(OUTPUT_BUFFER_MAX_BYTES),
      detachTimer: null,
    };
    sessions.set(sessionId, session);

    // CLI stdout/stderr -> routeOutput
    cliProcess.stdout.on('data', (chunk) => routeOutput(session, chunk));
    cliProcess.stderr.on('data', (chunk) => routeOutput(session, chunk));

    // CLI 进程退出/错误 -> 生命周期处理
    cliProcess.on('close', (code, signal) =>
      handleProcessExit(session, code, signal),
    );
    cliProcess.on('error', (err) => handleProcessError(session, err));

    // 绑定 WS 事件
    attachWs(session, ws);

    // 向客户端发送 session 元信息（客户端据此获取 sessionId 用于重连）
    ws.send(
      JSON.stringify({ type: 'session_meta', sessionId, replayed: false }),
    );

    wss.emit('connection', ws, req);
  });
}
