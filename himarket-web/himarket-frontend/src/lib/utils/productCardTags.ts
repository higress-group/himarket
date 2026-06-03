import * as yaml from 'js-yaml';

import type { IProductDetail } from '../apis/product';
import type { TFunction } from 'i18next';

const HTTP_METHODS = new Set(['delete', 'get', 'head', 'options', 'patch', 'post', 'put', 'trace']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseOpenApiSpec(spec?: string): unknown {
  if (!spec) return undefined;

  try {
    return JSON.parse(spec) as unknown;
  } catch {
    try {
      return yaml.load(spec);
    } catch {
      return undefined;
    }
  }
}

export function countOpenApiEndpoints(spec?: string): number {
  const openApiDoc = parseOpenApiSpec(spec);
  if (!isRecord(openApiDoc) || !isRecord(openApiDoc.paths)) {
    return 0;
  }

  return Object.values(openApiDoc.paths).reduce<number>((total, pathItem) => {
    if (!isRecord(pathItem)) return total;
    return (
      total +
      Object.keys(pathItem).filter((method) => HTTP_METHODS.has(method.toLowerCase())).length
    );
  }, 0);
}

function normalizeLabel(value?: string | null) {
  if (!value) return undefined;
  return value
    .replace(/_/g, '/')
    .replace(/OPENAI/gi, 'OpenAI')
    .replace(/\bA2A\b/gi, 'A2A')
    .replace(/\bHTTP\b/gi, 'HTTP');
}

function compactTags(tags: Array<string | undefined>, limit = 2) {
  return Array.from(new Set(tags.filter((tag): tag is string => Boolean(tag)))).slice(0, limit);
}

function getModelCategoryLabel(category: string | undefined, t: TFunction) {
  switch (category?.toUpperCase()) {
    case 'TEXT':
      return t('textGeneration');
    case 'IMAGE':
      return t('imageGeneration');
    case 'VIDEO':
      return t('videoGeneration');
    case 'AUDIO':
      return t('audioGeneration');
    case 'EMBEDDING':
      return t('embedding');
    case 'RERANK':
      return t('rerank');
    default:
      return normalizeLabel(category);
  }
}

function getProtocolLabel(value: string | null | undefined, t: TFunction) {
  if (!value) return undefined;

  const normalized = value.trim();
  const upperValue = normalized.toUpperCase();

  if (upperValue === 'A2A') return 'A2A';
  if (upperValue.includes('OPENAI') && upperValue.includes('COMPATIBLE')) {
    return t('openAiCompatible');
  }
  if (upperValue.includes('OPENAI')) return normalizeLabel(normalized);
  return normalizeLabel(normalized);
}

function getMcpProtocolTags(product: IProductDetail) {
  const protocol =
    product.mcpConfig?.protocol ||
    product.mcpConfig?.meta?.protocol ||
    product.mcpConfig?.mcpServerConfig?.transportMode;
  const normalized = protocol
    ?.trim()
    .toLowerCase()
    .replace(/[_\s-]/g, '');

  if (!normalized) return [];
  if (normalized.includes('dual')) return ['SSE', 'Streamable HTTP'];
  if (normalized.includes('streamable') || normalized.includes('http')) {
    return ['Streamable HTTP'];
  }
  if (normalized.includes('sse')) return ['SSE'];
  if (normalized.includes('stdio')) return ['STDIO'];
  return compactTags([normalizeLabel(protocol)]);
}

function getEndpointCountLabel(count: number, t: TFunction) {
  if (count === 1) return t('endpointCountSingle', { count });
  return t('endpointCount', { count });
}

export function getProductCardTags(product: IProductDetail, t: TFunction) {
  switch (product.type) {
    case 'MODEL_API': {
      const modelFeature = product.feature?.modelFeature;
      return compactTags(
        [
          getModelCategoryLabel(product.modelConfig?.modelAPIConfig?.modelCategory, t),
          modelFeature?.enableMultiModal ? t('multiModal') : undefined,
          modelFeature?.webSearch ? t('webSearch') : undefined,
        ],
        3,
      );
    }
    case 'MCP_SERVER':
      return compactTags(getMcpProtocolTags(product));
    case 'AGENT_API':
      return compactTags(
        product.agentConfig?.agentAPIConfig?.agentProtocols?.map((protocol) =>
          getProtocolLabel(protocol, t),
        ) || [],
        3,
      );
    case 'REST_API': {
      const endpointCount = countOpenApiEndpoints(product.apiConfig?.spec);
      return compactTags([
        endpointCount > 0 ? getEndpointCountLabel(endpointCount, t) : t('restEndpoint'),
      ]);
    }
    default:
      return compactTags([t('apiDocs')]);
  }
}
