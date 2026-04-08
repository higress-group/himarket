import { clsx, type ClassValue } from 'clsx';
import * as yaml from 'js-yaml';
import { twMerge } from 'tailwind-merge';

import type { LinkedService } from '@/types/api-product';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Token 相关函数
export const getToken = (): string | null => {
  return localStorage.getItem('access_token');
};

export const removeToken = (): void => {
  localStorage.removeItem('access_token');
  localStorage.removeItem('userInfo');
};

export const isAuthenticated = (): boolean => {
  return !!getToken();
};

export const getStatusBadgeVariant = (status: string) => {
  return status === 'PENDING' ? 'orange' : status === 'READY' ? 'blue' : 'green';
};

export const getServiceName = (linkedServiceParam: LinkedService | null | undefined) => {
  if (!linkedServiceParam) {
    return null;
  }

  if (linkedServiceParam.sourceType === 'NACOS') {
    const cfg = linkedServiceParam.nacosRefConfig;
    if (!cfg) return 'Nacos服务';
    if ('mcpServerName' in cfg) return cfg.mcpServerName;
    if ('agentName' in cfg) return cfg.agentName;
    return 'Nacos服务';
  }
  if (linkedServiceParam.apigRefConfig) {
    const cfg = linkedServiceParam.apigRefConfig;
    if ('apiName' in cfg) return cfg.apiName;
    if ('mcpServerName' in cfg) return cfg.mcpServerName;
    if ('agentApiName' in cfg) return cfg.agentApiName;
    if ('modelApiName' in cfg) return cfg.modelApiName;
    return null;
  }
  if (linkedServiceParam.higressRefConfig) {
    const cfg = linkedServiceParam.higressRefConfig;
    return cfg.mcpServerName || cfg.modelRouteName || null;
  }
  if (linkedServiceParam.adpAIGatewayRefConfig) {
    const cfg = linkedServiceParam.adpAIGatewayRefConfig;
    if ('mcpServerName' in cfg) return cfg.mcpServerName;
    if ('modelApiName' in cfg) return cfg.modelApiName;
    return null;
  }
  if (linkedServiceParam.apsaraGatewayRefConfig) {
    const cfg = linkedServiceParam.apsaraGatewayRefConfig;
    if ('mcpServerName' in cfg) return cfg.mcpServerName;
    if ('modelApiName' in cfg) return cfg.modelApiName;
    return null;
  }
  return null;
};

/**
 * 格式化日期时间，显示完整的时间点包括小时、分钟、秒
 * @param dateString 日期字符串或Date对象
 * @returns 格式化后的日期时间字符串 (YYYY-MM-DD HH:mm:ss)
 */
export const formatDateTime = (dateString: string | Date): string => {
  try {
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString;
    if (isNaN(date.getTime())) {
      return String(dateString);
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  } catch {
    return String(dateString);
  }
};

/**
 * 格式化日期，只显示年月日
 * @param dateString 日期字符串或Date对象
 * @returns 格式化后的日期字符串 (YYYY-MM-DD)
 */
export const formatDate = (dateString: string | Date): string => {
  try {
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString;
    if (isNaN(date.getTime())) {
      return String(dateString);
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
  } catch {
    return String(dateString);
  }
};

// 类型映射
export const ProductTypeMap: Record<string, string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

// OpenAPI 规范解析相关类型和函数
export interface OpenAPIEndpoint {
  path: string;
  method: string;
  operationId?: string;
  summary?: string;
  description?: string;
  parameters?: Array<{
    name: string;
    in: string;
    description?: string;
    required?: boolean;
    schema?: Record<string, unknown>;
  }>;
  requestBody?: {
    description?: string;
    content?: Record<string, unknown>;
    required?: boolean;
  };
  responses?: Record<
    string,
    {
      description: string;
      content?: Record<string, unknown>;
    }
  >;
  tags?: string[];
}

export interface ParsedOpenAPI {
  info?: {
    title?: string;
    version?: string;
    description?: string;
  };
  servers?: Array<{
    url: string;
    description?: string;
  }>;
  endpoints: OpenAPIEndpoint[];
}

/** OpenAPI path item 下的 operation 对象（宽松结构，用于解析） */
interface LooseOpenAPIOperation {
  description?: string;
  operationId?: string;
  parameters?: OpenAPIEndpoint['parameters'];
  requestBody?: OpenAPIEndpoint['requestBody'];
  responses?: OpenAPIEndpoint['responses'];
  summary?: string;
  tags?: string[];
}

/**
 * 解析OpenAPI规范
 * @param spec OpenAPI规范字符串（YAML或JSON格式）
 * @returns 解析后的OpenAPI对象
 */
export const parseOpenAPISpec = (spec: string): ParsedOpenAPI | null => {
  try {
    let openApiDoc: unknown;

    // 尝试解析YAML格式
    try {
      openApiDoc = yaml.load(spec);
    } catch {
      // 如果YAML解析失败，尝试JSON格式
      openApiDoc = JSON.parse(spec);
    }

    if (!openApiDoc || typeof openApiDoc !== 'object' || !('paths' in openApiDoc)) {
      return null;
    }

    const doc = openApiDoc as Record<string, unknown>;
    const paths = doc.paths as Record<string, Record<string, unknown>> | undefined;

    if (!paths) {
      return null;
    }

    const endpoints: OpenAPIEndpoint[] = [];

    // 解析路径和方法
    Object.entries(paths).forEach(([path, pathItem]) => {
      if (pathItem && typeof pathItem === 'object') {
        // HTTP方法
        const methods = ['get', 'post', 'put', 'delete', 'patch', 'head', 'options', 'trace'];

        methods.forEach((method) => {
          const raw = pathItem[method];
          if (raw && typeof raw === 'object') {
            const operation = raw as LooseOpenAPIOperation;
            endpoints.push({
              description: operation.description,
              method: method.toUpperCase(),
              operationId: operation.operationId,
              parameters: operation.parameters,
              path,
              requestBody: operation.requestBody,
              responses: operation.responses,
              summary: operation.summary,
              tags: operation.tags,
            });
          }
        });
      }
    });

    return {
      endpoints,
      info: doc.info as ParsedOpenAPI['info'],
      servers: doc.servers as ParsedOpenAPI['servers'],
    };
  } catch (error) {
    console.error('OpenAPI规范解析失败:', error);
    return null;
  }
};

/**
 * 获取HTTP方法的颜色标签
 * @param method HTTP方法
 * @returns 对应的颜色类名
 */
export const getMethodColor = (method: string): string => {
  const colors: Record<string, string> = {
    DELETE: 'red',
    GET: 'green',
    HEAD: 'gray',
    OPTIONS: 'gray',
    PATCH: 'purple',
    POST: 'blue',
    PUT: 'orange',
    TRACE: 'gray',
  };
  return colors[method.toUpperCase()] || 'gray';
};

/**
 * 复制文本到剪贴板
 * @param text 要复制的文本
 */
export const copyToClipboard = async (text: string): Promise<void> => {
  try {
    // 优先尝试现代 Clipboard API
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return;
    }
  } catch (_error) {
    // 现代API失败，继续降级处理
    console.warn('Modern clipboard API failed, falling back to execCommand');
  }

  try {
    // 降级到传统方法
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  } catch (error) {
    console.error('All copy methods failed:', error);
    throw error;
  }
};

/**
 * 格式化域名和端口为完整的 host 字符串
 * @param domain - 域名
 * @param port - 端口号（可选）
 * @param protocol - 协议（http/https）
 * @returns 格式化后的 host 字符串
 *
 * 规则：
 * - 如果 port 为 null/undefined，只返回 domain
 * - 如果 port 是默认端口（http:80, https:443），只返回 domain
 * - 其他情况返回 domain:port
 */
export function formatDomainWithPort(
  domain: string,
  port: number | null | undefined,
  protocol: string,
): string {
  if (!port) return domain;
  if (protocol === 'http' && port === 80) return domain;
  if (protocol === 'https' && port === 443) return domain;
  return `${domain}:${port}`;
}
