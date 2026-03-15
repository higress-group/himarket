import {
  DETACH_TTL_MS,
  ZOMBIE_TTL_MS,
  GRACEFUL_TIMEOUT_MS,
} from '../config.js';

// ---------------------------------------------------------------------------
// Session registry — CLI 进程生命周期与 WebSocket 解耦。
//
// 每个 session 可处于以下状态之一：
//   attached   — 有 WS 连接，CLI 输出实时转发
//   detached   — WS 断开，CLI 继续运行，输出写入 outputBuffer
//   destroying — 正在清理（TTL 超时 / 手动销毁）
// ---------------------------------------------------------------------------

/** @type {Map<string, object>} */
export const sessions = new Map();

// ---------------------------------------------------------------------------
// Session 生命周期管理
// ---------------------------------------------------------------------------

/**
 * 将 CLI stdout/stderr 输出路由到当前 WS 或 outputBuffer。
 */
export function routeOutput(session, chunk) {
  const str = chunk.toString();
  if (
    session.state === 'attached' &&
    session.ws &&
    session.ws.readyState === 1 /* OPEN */
  ) {
    session.ws.send(str);
  } else if (session.state === 'detached') {
    session.outputBuffer.push(str);
  }
  // destroying 状态丢弃
}

/**
 * 将 WS 消息路由到 CLI stdin。
 */
export function routeInput(session, data) {
  if (
    session.process &&
    session.process.stdin &&
    !session.process.stdin.destroyed
  ) {
    const msg = data.toString();
    session.process.stdin.write(msg.endsWith('\n') ? msg : msg + '\n');
  }
  session.lastActivityAt = new Date();
}

/**
 * Attach WS 到 session（绑定事件、replay 缓冲）。
 * 调用前已确保 session.ws = ws, session.state = 'attached'。
 */
export function attachWs(session, ws) {
  // 绑定 WS → stdin
  ws.on('message', (data) => routeInput(session, data));
  ws.on('close', () => detachSession(session));
  ws.on('error', (err) => {
    console.error(`[session:${session.id}] WebSocket error:`, err.message);
  });
}

/**
 * Detach: WS 断开，CLI 继续运行，启动 TTL 定时器。
 */
export function detachSession(session) {
  if (session.state === 'destroying') return;
  console.log(`[session:${session.id}] detached, TTL=${DETACH_TTL_MS}ms`);
  session.ws = null;
  session.state = 'detached';
  session.detachTimer = setTimeout(
    () => destroySession(session.id, 'TTL expired'),
    DETACH_TTL_MS,
  );
}

/**
 * 销毁 session：kill 进程，清理资源，从 Map 删除。
 */
export function destroySession(sessionId, reason) {
  const session = sessions.get(sessionId);
  if (!session) return;
  if (session.state === 'destroying') return;
  session.state = 'destroying';
  console.log(
    `[session:${sessionId}] destroying (reason: ${reason || 'unknown'})`,
  );

  if (session.detachTimer) {
    clearTimeout(session.detachTimer);
    session.detachTimer = null;
  }

  if (session.ws && session.ws.readyState === 1 /* OPEN */) {
    session.ws.close(1001, reason || 'Session destroyed');
  }
  session.ws = null;

  const proc = session.process;
  if (proc && !proc.killed) {
    proc.kill('SIGTERM');
    const killTimer = setTimeout(() => {
      if (!proc.killed) {
        console.log(`[session:${sessionId}] force killing process`);
        proc.kill('SIGKILL');
      }
    }, GRACEFUL_TIMEOUT_MS);
    proc.on('exit', () => clearTimeout(killTimer));
  }

  session.outputBuffer.clear();
  sessions.delete(sessionId);
}

/**
 * CLI 进程退出处理。
 */
export function handleProcessExit(session, code, signal) {
  console.log(
    `[session:${session.id}] process exited - code=${code} signal=${signal}`,
  );
  session.process = null;

  const exitMsg = JSON.stringify({
    type: 'process_exited',
    code,
    signal: signal || null,
  });

  if (
    session.state === 'attached' &&
    session.ws &&
    session.ws.readyState === 1
  ) {
    session.ws.send(exitMsg);
    session.ws.close(1000, `Process exited with code ${code}`);
    sessions.delete(session.id);
  } else if (session.state === 'detached') {
    // 进程已退出，将退出信息写入缓冲，短 TTL 保留供 replay
    session.outputBuffer.push(exitMsg);
    if (session.detachTimer) {
      clearTimeout(session.detachTimer);
    }
    session.detachTimer = setTimeout(
      () => destroySession(session.id, 'Zombie TTL expired'),
      ZOMBIE_TTL_MS,
    );
    console.log(
      `[session:${session.id}] process exited while detached, zombie TTL=${ZOMBIE_TTL_MS}ms`,
    );
  } else {
    sessions.delete(session.id);
  }
}

/**
 * CLI 进程错误处理。
 */
export function handleProcessError(session, err) {
  console.error(`[session:${session.id}] process error:`, err.message);
  if (
    session.state === 'attached' &&
    session.ws &&
    session.ws.readyState === 1
  ) {
    session.ws.send(
      JSON.stringify({ error: `Process error: ${err.message}` }),
    );
    session.ws.close(4500, 'Process error');
  }
  sessions.delete(session.id);
}

/**
 * 序列化 session 为 JSON-safe 对象（用于 HTTP 管理端点）。
 */
export function serializeSession(session) {
  return {
    id: session.id,
    state: session.state,
    command: session.command,
    args: session.args,
    cwd: session.cwd,
    createdAt: session.createdAt.toISOString(),
    lastActivityAt: session.lastActivityAt.toISOString(),
    processAlive: !!(session.process && !session.process.killed),
    bufferBytes: session.outputBuffer.totalBytes,
  };
}
