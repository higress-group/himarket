import crypto from 'node:crypto';
import fsSync from 'node:fs';
import { WORKSPACE_ROOT, OUTPUT_BUFFER_MAX_BYTES } from '../config.js';
import { RingBuffer } from '../lib/ring-buffer.js';
import { sessions, destroySession } from '../lib/session.js';

// ---------------------------------------------------------------------------
// node-pty 可选导入
// ---------------------------------------------------------------------------
let pty = null;
try {
  pty = await import('node-pty');
  // 某些 ESM 包装会将默认导出放在 .default 中
  if (pty.default && typeof pty.default.spawn === 'function') {
    pty = pty.default;
  }
} catch {
  // node-pty 可选依赖，未安装时 /terminal 端点不可用
}

export const isPtyAvailable = pty !== null;

// ---------------------------------------------------------------------------
// /terminal 端点：交互式 PTY shell
// ---------------------------------------------------------------------------

export function handleTerminalUpgrade(wss, req, socket, head, url) {
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

    console.log(
      `[terminal:${sessionId}] connected - cols=${cols} rows=${rows} cwd=${cwd}`,
    );

    // 确保 cwd 目录存在，避免 shell 因目录不存在而退出
    try {
      if (!fsSync.existsSync(cwd)) {
        fsSync.mkdirSync(cwd, { recursive: true });
        console.log(`[terminal:${sessionId}] created missing cwd: ${cwd}`);
      }
    } catch (mkdirErr) {
      console.error(
        `[terminal:${sessionId}] failed to create cwd:`,
        mkdirErr.message,
      );
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
      console.error(
        `[terminal:${sessionId}] pty spawn failed:`,
        err.message,
      );
      ws.close(4500, 'PTY spawn failed');
      return;
    }

    const now = new Date();
    const session = {
      id: sessionId,
      state: 'attached',
      ws,
      process: shell,
      command: '/bin/sh',
      args: ['-l'],
      cwd,
      createdAt: now,
      lastActivityAt: now,
      outputBuffer: new RingBuffer(OUTPUT_BUFFER_MAX_BYTES),
      detachTimer: null,
    };
    sessions.set(sessionId, session);

    // PTY stdout -> WebSocket (binary)
    shell.onData((data) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(data);
      }
    });

    shell.onExit(({ exitCode, signal }) => {
      console.log(
        `[terminal:${sessionId}] shell exited - code=${exitCode} signal=${signal}`,
      );
      if (ws.readyState === ws.OPEN) {
        ws.close(1000, `Shell exited with code ${exitCode}`);
      }
      session.process = null;
      destroySession(sessionId, `Shell exited with code ${exitCode}`);
    });

    // WebSocket -> PTY stdin / resize
    ws.on('message', (data) => {
      const msg = data.toString();
      if (msg.startsWith('{')) {
        try {
          const parsed = JSON.parse(msg);
          if (parsed.type === 'resize' && parsed.cols && parsed.rows) {
            shell.resize(parsed.cols, parsed.rows);
            return;
          }
          if (parsed.type === 'heartbeat') {
            // 心跳消息，忽略不转发给 pty
            return;
          }
        } catch {
          /* 不是 JSON，当作普通输入 */
        }
      }
      shell.write(msg);
    });

    ws.on('close', () => {
      console.log(`[terminal:${sessionId}] disconnected`);
      if (session.process) {
        shell.kill();
      }
      destroySession(sessionId, 'WebSocket closed');
    });

    ws.on('error', (err) => {
      console.error(
        `[terminal:${sessionId}] WebSocket error:`,
        err.message,
      );
    });
  });
}
