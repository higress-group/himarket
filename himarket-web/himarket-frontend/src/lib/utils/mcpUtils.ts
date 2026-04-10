import type { IMcpMeta } from '../apis/product';

/**
 * 判断 MCP 是否有可用 endpoint
 * endpointUrl 由后端在 endpoint 部署/同步成功后写入，是判断可用性的唯一依据
 */
export function hasAvailableEndpoint(meta: IMcpMeta | null | undefined): boolean {
  return !!meta?.endpointUrl;
}
