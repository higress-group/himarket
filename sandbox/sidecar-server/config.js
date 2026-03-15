// ---------------------------------------------------------------------------
// 配置 - 从环境变量读取
// ---------------------------------------------------------------------------

export const PORT = parseInt(process.env.SIDECAR_PORT, 10) || 8080;

export const ALLOWED_COMMANDS = new Set(
  (process.env.ALLOWED_COMMANDS || '')
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean),
);

export const GRACEFUL_TIMEOUT_MS =
  parseInt(process.env.GRACEFUL_TIMEOUT_MS, 10) || 5000;

export const SIDECAR_MODE = process.env.SIDECAR_MODE || 'k8s';

export const DETACH_TTL_MS =
  parseInt(process.env.DETACH_TTL_MS, 10) || 300000; // 5 min

export const OUTPUT_BUFFER_MAX_BYTES =
  parseInt(process.env.OUTPUT_BUFFER_MAX_BYTES, 10) || 1048576; // 1 MB

export const ZOMBIE_TTL_MS = 60000; // CLI 退出后 session 保留 60 秒供 replay

// WORKSPACE_ROOT 仅用于兜底默认值（如 /files/extract 未指定 cwd 时），
// 不再作为所有文件操作的路径基准。
// 参考 OpenSandbox execd 设计：文件操作接收绝对路径，由调用方负责构建。
export const WORKSPACE_ROOT = process.env.WORKSPACE_ROOT || '/workspace';

export const LISTEN_HOST = SIDECAR_MODE === 'local' ? '127.0.0.1' : '0.0.0.0';
