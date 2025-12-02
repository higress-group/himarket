// 迁移自 portal-web/portal-frontend/src/lib/utils.ts
export function fetcher(url: string) {
  return fetch(url).then((res) => res.json());
}

export function getTokenFromCookie(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)auth_token=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
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
export function processProductSpecs<T extends { type: string; mcpSpec?: string | null; apiSpec?: string | null }>(
  product: T
): T {
  if (product.type === 'MCP_SERVER' && product.mcpSpec) {
    return {
      ...product,
      mcpSpec: unescapeNewlines(product.mcpSpec)
    };
  } else if (product.type === 'REST_API' && product.apiSpec) {
    return {
      ...product,
      apiSpec: unescapeNewlines(product.apiSpec)
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
    
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    });
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
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  } catch {
    return String(dateString);
  }
}; 

export const safeJSONParse = <T>(value: string, fallback: T): T => {
  try {
    return JSON.parse(value);
  } catch (error) {
    console.log(error);
    return fallback;
  }
}