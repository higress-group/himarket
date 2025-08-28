import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"
 
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
} 


// Token 相关函数
export const getToken = (): string | null => {
  return localStorage.getItem('token')
}

export const removeToken = (): void => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}

export const isAuthenticated = (): boolean => {
  return !!getToken()
}

export const getStatusBadgeVariant = (status: string) => {
  return status === "PENDING" ? "orange" : status === "READY" ? "blue" : "green"
}

export const getServiceName = (linkedServiceParam: any) => {  
  
  if (linkedServiceParam?.sourceType === 'NACOS') {
    return linkedServiceParam.nacosRefConfig?.mcpServerName||'Nacos MCP服务'
  }    
  if (linkedServiceParam?.apigRefConfig) {
    if ('apiName' in linkedServiceParam.apigRefConfig && linkedServiceParam.apigRefConfig.apiName) {
      return linkedServiceParam.apigRefConfig.apiName
    }
    if ('mcpServerName' in linkedServiceParam.apigRefConfig && linkedServiceParam.apigRefConfig.mcpServerName) {
      return linkedServiceParam.apigRefConfig.mcpServerName
    }
  }
  if (linkedServiceParam?.higressRefConfig) {
    return linkedServiceParam.higressRefConfig.mcpServerName
  }
  return '未知服务'
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

// 类型映射
export const ProductTypeMap: Record<string, string> = {
  REST_API: 'REST API',
  MCP_SERVER: 'MCP Server',
};