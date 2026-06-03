import { describe, expect, it } from 'vitest';

import { countOpenApiEndpoints, getProductCardTags } from '../productCardTags';

import type { IProductDetail } from '../../apis/product';
import type { TFunction } from 'i18next';

const translations: Record<string, string> = {
  apiDocs: 'API docs',
  endpointCount: '{{count}} endpoints',
  endpointCountSingle: '{{count}} endpoint',
  imageGeneration: 'Image',
  multiModal: 'Multi-modal',
  openAiCompatible: 'OpenAI-compatible',
  restEndpoint: 'REST endpoint',
  textGeneration: 'Text',
  webSearch: 'Web search',
};

const t = ((key: string, options?: { count?: number }) => {
  const value = translations[key] ?? key;
  return options?.count === undefined ? value : value.replace('{{count}}', String(options.count));
}) as TFunction;

function product(overrides: Partial<IProductDetail>): IProductDetail {
  return {
    apiConfig: { meta: { source: '', type: '' }, spec: '' },
    autoApprove: null,
    categories: [],
    createAt: '',
    description: '',
    document: null,
    enableConsumerAuth: false,
    enabled: true,
    name: '',
    productId: '',
    status: '',
    type: 'MODEL_API',
    updatedAt: '',
    ...overrides,
  } as IProductDetail;
}

describe('productCardTags', () => {
  it('为 Model 生成场景和模型能力标签', () => {
    const tags = getProductCardTags(
      product({
        feature: {
          modelFeature: {
            enableMultiModal: true,
            model: 'qwen-max',
            webSearch: true,
          },
        },
        modelConfig: {
          modelAPIConfig: {
            aiProtocols: ['OpenAI/v1'],
            modelCategory: 'TEXT',
            routes: [],
            services: [],
          },
        },
        type: 'MODEL_API',
      }),
      t,
    );

    expect(tags).toEqual(['Text', 'Multi-modal', 'Web search']);
  });

  it('为 MCP 双协议生成两个协议标签', () => {
    const tags = getProductCardTags(
      product({
        mcpConfig: {
          mcpServerConfig: {
            domains: [],
            path: '',
            rawConfig: {},
            transportMode: '',
          },
          mcpServerName: '',
          meta: {
            createFromType: '',
            protocol: 'dualHttp',
            source: '',
          },
          tools: '',
        },
        type: 'MCP_SERVER',
      }),
      t,
    );

    expect(tags).toEqual(['SSE', 'Streamable HTTP']);
  });

  it('Agent 只展示协议标签', () => {
    const tags = getProductCardTags(
      product({
        agentConfig: {
          agentAPIConfig: {
            agentProtocols: ['a2a', 'OPENAI_COMPATIBLE'],
          },
        },
        type: 'AGENT_API',
      }),
      t,
    );

    expect(tags).toEqual(['A2A', 'OpenAI-compatible']);
  });

  it('统计 OpenAPI JSON endpoint 数量', () => {
    const spec = JSON.stringify({
      paths: {
        '/pets': { get: {}, parameters: [], post: {} },
        '/pets/{id}': { delete: {}, get: {} },
      },
    });

    expect(countOpenApiEndpoints(spec)).toBe(4);
  });

  it('REST API 展示 endpoint 数量标签', () => {
    const tags = getProductCardTags(
      product({
        apiConfig: {
          meta: { source: '', type: '' },
          spec: `
openapi: 3.0.0
paths:
  /orders:
    get: {}
    post: {}
`,
        },
        type: 'REST_API',
      }),
      t,
    );

    expect(tags).toEqual(['2 endpoints']);
  });
});
