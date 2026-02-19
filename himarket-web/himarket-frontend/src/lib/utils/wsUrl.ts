/**
 * WebSocket URL 构建工具函数
 *
 * 统一三个页面（HiWork/Quest、HiCoding/Coding、HiCli）的 WebSocket URL 构建逻辑，
 * 确保 provider、runtime 等查询参数一致地附加到 WebSocket URL 中。
 */

export interface WsUrlParams {
  /** CLI provider key */
  provider?: string;
  /** 运行时类型 (local | k8s) */
  runtime?: string;
  /** 认证 token */
  token?: string;
  /** 工作目录（HiCli 专用） */
  cwd?: string;
  /** 沙箱模式 (user | session) */
  sandboxMode?: string;
}

/**
 * 构建 ACP WebSocket URL，附加 provider、runtime 等查询参数。
 *
 * @param basePath WebSocket 基础路径，默认 "/ws/acp"
 * @param params   查询参数
 * @param origin   协议+主机，默认从 window.location 推导
 */
export function buildAcpWsUrl(
  params: WsUrlParams,
  basePath = "/ws/acp",
  origin?: string,
): string {
  const resolvedOrigin =
    origin ??
    `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}`;
  const base = `${resolvedOrigin}${basePath}`;

  const searchParams = new URLSearchParams();
  if (params.token) searchParams.set("token", params.token);
  if (params.provider) searchParams.set("provider", params.provider);
  if (params.cwd) searchParams.set("cwd", encodeURIComponent(params.cwd));
  if (params.runtime) searchParams.set("runtime", params.runtime);
  if (params.sandboxMode) searchParams.set("sandboxMode", params.sandboxMode);

  const qs = searchParams.toString();
  return qs ? `${base}?${qs}` : base;
}
