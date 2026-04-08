/**
 * 模型 API 路径归一化工具函数。
 *
 * 与后端 ModelEndpointResolver 保持相同的归一化规则。
 */

const PROTOCOL_CONFIG: Record<string, { versionPrefix: string; endpointSuffix: string }> = {
  anthropic: { endpointSuffix: '/messages', versionPrefix: '/v1' },
  openai: { endpointSuffix: '/chat/completions', versionPrefix: '/v1' },
};

function detectProtocol(aiProtocols: string[] | undefined): string {
  if (!aiProtocols || aiProtocols.length === 0) return 'openai';
  const first = aiProtocols[0];
  if (!first) return 'openai';
  const lower = first.toLowerCase();
  if (lower.includes('openai')) return 'openai';
  if (lower.includes('anthropic')) return 'anthropic';
  return 'unknown';
}

function stripTrailingSlash(path: string): string {
  return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path;
}

/**
 * 归一化路由路径为完整的 API 端点路径（用于 curl 示例等）。
 */
export function resolveEndpointPath(
  pathValue: string,
  pathType: string | undefined,
  aiProtocols: string[] | undefined,
): string {
  const path = stripTrailingSlash(pathValue);
  const protocol = detectProtocol(aiProtocols);
  const config = PROTOCOL_CONFIG[protocol];

  if (!config) return path;

  if (pathType?.toLowerCase() === 'prefix') {
    const base = ensureVersionPrefix(path, config);
    return base + config.endpointSuffix;
  }

  // Exact or null — path is already the full endpoint
  return path;
}

/**
 * 归一化路由路径为 SDK/CLI 使用的 baseUrl 路径部分。
 */
export function resolveBaseUrlPath(
  pathValue: string,
  pathType: string | undefined,
  aiProtocols: string[] | undefined,
): string {
  const path = stripTrailingSlash(pathValue);
  const protocol = detectProtocol(aiProtocols);
  const config = PROTOCOL_CONFIG[protocol];

  if (!config) return path;

  if (pathType?.toLowerCase() === 'prefix') {
    return ensureVersionPrefix(path, config);
  }

  // Regex or other unknown types — return as-is
  if (pathType && !['exact', 'prefix'].includes(pathType.toLowerCase())) {
    return path;
  }

  // Exact or null — strip endpoint suffix
  if (path.endsWith(config.endpointSuffix)) {
    return path.slice(0, -config.endpointSuffix.length);
  }
  return path;
}

function ensureVersionPrefix(
  path: string,
  config: { versionPrefix: string; endpointSuffix: string },
): string {
  if (path.endsWith(config.endpointSuffix)) {
    return path.slice(0, -config.endpointSuffix.length);
  }
  if (path.endsWith(config.versionPrefix)) {
    return path;
  }
  return path + config.versionPrefix;
}
