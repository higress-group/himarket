import * as yaml from 'js-yaml';

// 迁移自 portal-web/portal-frontend/src/lib/utils.ts
export function fetcher(url: string) {
  return fetch(url).then((res) => res.json());
}

export function getTokenFromCookie(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)auth_token=([^;]*)/);
  return match && match[1] ? decodeURIComponent(match[1]) : null;
}

/**
 * 处理字符串中的换行符转义
 * 将 \\n 转换为 \n
 */
export function unescapeNewlines(str: string): string {
  return str.replace(/\\n/g, '\n');
}

/**
 * 处理产品数据中的 mcpSpec 和 apiSpec 换行符转义
 */
export function processProductSpecs<
  T extends { type: string; mcpSpec?: string | null; apiSpec?: string | null },
>(product: T): T {
  if (product.type === 'MCP_SERVER' && product.mcpSpec) {
    return {
      ...product,
      mcpSpec: unescapeNewlines(product.mcpSpec),
    };
  } else if (product.type === 'REST_API' && product.apiSpec) {
    return {
      ...product,
      apiSpec: unescapeNewlines(product.apiSpec),
    };
  }
  return product;
}

/**
 * 格式化日期时间，显示完整的时间点包括小时、分钟、秒
 * @param dateString 日期字符串或Date对象
 * @returns 格式化后的日期时间字符串
 */
export const formatDateTime = (dateString: string | Date): string => {
  try {
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString;
    if (isNaN(date.getTime())) {
      return String(dateString);
    }

    // 格式化为 YYYY-MM-DD HH:mm:ss
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
 * @returns 格式化后的日期字符串
 */
export const formatDate = (dateString: string | Date): string => {
  try {
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString;
    if (isNaN(date.getTime())) {
      return String(dateString);
    }

    return date.toLocaleDateString('zh-CN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return String(dateString);
  }
};

export const safeJSONParse = <T>(value: string, fallback: T): T => {
  try {
    return JSON.parse(value);
  } catch (error) {
    console.warn(error);
    return fallback;
  }
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
  components?: {
    schemas?: Record<string, Record<string, unknown>>;
  };
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

interface LooseOpenAPIOperation {
  description?: string;
  operationId?: string;
  parameters?: OpenAPIEndpoint['parameters'];
  requestBody?: OpenAPIEndpoint['requestBody'];
  responses?: OpenAPIEndpoint['responses'];
  summary?: string;
  tags?: string[];
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

export const parseOpenAPISpec = (spec: string): ParsedOpenAPI | null => {
  try {
    let openApiDoc: unknown;

    try {
      openApiDoc = yaml.load(spec);
    } catch {
      openApiDoc = JSON.parse(spec);
    }

    if (!openApiDoc || typeof openApiDoc !== 'object' || !('paths' in openApiDoc)) {
      return null;
    }

    const doc = openApiDoc as Record<string, unknown>;
    const paths = isPlainRecord(doc.paths)
      ? (doc.paths as Record<string, Record<string, unknown>>)
      : undefined;
    const components = isPlainRecord(doc.components) ? doc.components : undefined;
    const schemas =
      components && isPlainRecord(components.schemas)
        ? (components.schemas as Record<string, Record<string, unknown>>)
        : undefined;

    if (!paths) {
      return null;
    }

    const endpoints: OpenAPIEndpoint[] = [];
    const methods = ['get', 'post', 'put', 'delete', 'patch', 'head', 'options', 'trace'];

    Object.entries(paths).forEach(([path, pathItem]) => {
      if (!isPlainRecord(pathItem)) return;

      methods.forEach((method) => {
        const raw = pathItem[method];
        if (!isPlainRecord(raw)) return;

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
      });
    });

    return {
      components: schemas ? { schemas } : undefined,
      endpoints,
      info: isPlainRecord(doc.info) ? (doc.info as ParsedOpenAPI['info']) : undefined,
      servers: Array.isArray(doc.servers) ? (doc.servers as ParsedOpenAPI['servers']) : undefined,
    };
  } catch (error) {
    console.error('OpenAPI规范解析失败:', error);
    return null;
  }
};

export function copyToClipboard(text: string) {
  // 返回一个 Promise 对象
  return new Promise((resolve, reject) => {
    // 检查是否支持 Clipboard API 且处于安全上下文
    if (
      navigator.clipboard &&
      typeof navigator.clipboard.writeText === 'function' &&
      window.isSecureContext
    ) {
      // 使用 Clipboard API 写入剪切板
      navigator.clipboard
        .writeText(text)
        .then(resolve, reject)
        .catch(() => {
          // 如果 Clipboard API 失败，fallback 到 textarea
          fallbackCopy(text, resolve, reject);
        });
    } else {
      // 非安全环境下或不支持 Clipboard API 的浏览器的回退方法
      fallbackCopy(text, resolve, reject);
    }
  });
}

function fallbackCopy(
  text: string,
  resolve: (value: boolean) => void,
  reject: (reason?: unknown) => void,
) {
  const textArea = document.createElement('textarea');
  textArea.value = text;

  // 避免出现滚动条
  textArea.style.position = 'fixed';
  textArea.style.top = '0';
  textArea.style.left = '0';
  textArea.style.width = '2em';
  textArea.style.height = '2em';
  textArea.style.padding = '0';
  textArea.style.border = 'none';
  textArea.style.outline = 'none';
  textArea.style.boxShadow = 'none';
  textArea.style.background = 'transparent';

  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();

  try {
    const successful = document.execCommand('copy');
    if (successful) {
      resolve(true);
    } else {
      reject(new Error('Copy command failed'));
    }
  } catch (err) {
    reject(err); // 如果执行失败，调用 reject
  } finally {
    document.body.removeChild(textArea);
  }
}

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
