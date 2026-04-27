import type { IMcpMeta } from '@/lib/apis';

/**
 * 判断 MCP 是否有可用 endpoint
 *
 * 优先检查 endpointUrl（平台内部部署的 MCP），
 * 其次检查 resolvedConfig（外部接入的 GATEWAY / NACOS / DIRECT 类型 MCP）。
 */
export function hasAvailableEndpoint(meta: IMcpMeta | null | undefined): boolean {
  if (meta?.endpointUrl) return true;

  if (meta?.resolvedConfig) {
    try {
      const parsed = JSON.parse(meta.resolvedConfig);
      const servers = Object.values(parsed.mcpServers || {}) as Array<{ url?: string }>;
      return servers.some((s) => s?.url);
    } catch {
      /* ignore invalid JSON */
    }
  }

  return false;
}
