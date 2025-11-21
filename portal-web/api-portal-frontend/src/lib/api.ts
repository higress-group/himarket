import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { message } from 'antd';

const api: AxiosInstance = axios.create({
  baseURL: (import.meta as any).env.VITE_API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})


// 请求拦截器
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 优先使用临时 token（开发环境），临时 token 已包含 Bearer 前缀
    const tempToken = (import.meta as any).env.VITE_TEMP_AUTH_TOKEN;
    const accessToken = localStorage.getItem('access_token');

    if (config.headers) {
      if (tempToken) {
        // 临时 token 直接使用（已包含 Bearer 前缀）
        config.headers.Authorization = tempToken;
      } else if (accessToken) {
        // 生产环境 token 需要添加 Bearer 前缀
        config.headers.Authorization = `Bearer ${accessToken}`;
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  (error) => {
    const status = error.response?.status;
    switch (status) {
      case 401:
        message.error('未登录或登录已过期，请重新登录');
        // 清除token信息
        localStorage.removeItem('access_token');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
        break;
      case 403:
        message.error('无权限访问该资源，请重新登录');
        // 清除token信息
        localStorage.removeItem('access_token');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
        break;
      case 404:
        message.error('请求的资源不存在');
        break;
      case 500:
        message.error('服务器异常，请稍后再试');
        break;
      default:
        message.error(error.response?.data?.message || '请求发生错误');
    }
    return Promise.reject(error);
  }
)

// Consumer相关API
export interface QueryConsumerParam {
  name?: string;
  email?: string;
  status?: string;
  company?: string;
}

export interface Pageable {
  page: number;
  size: number;
}

export function getConsumers(param: QueryConsumerParam, pageable: Pageable) {
  return api.get('/consumers', {
    params: {
      ...param,
      page: pageable.page,
      size: pageable.size,
    },
  });
}

export function deleteConsumer(consumerId: string) {
  return api.delete(`/consumers/${consumerId}`);
}

export function createConsumer(data: any) {
  return api.post('/consumers', data);
}

// 申请订阅API产品
export function subscribeProduct(consumerId: string, productId: string) {
  return api.post(`/consumers/${consumerId}/subscriptions`, {
    productId: productId
  });
}

// 获取某个consumer的订阅列表
export function getConsumerSubscriptions(consumerId: string, searchParams?: { productName?: string; status?: string }) {
  return api.get(`/consumers/${consumerId}/subscriptions`, {
    params: {
      page: 1,
      size: 100,
      ...searchParams
    }
  });
}

// 取消订阅
export function unsubscribeProduct(consumerId: string, productId: string) {
  return api.delete(`/consumers/${consumerId}/subscriptions/${productId}`);
}

// 查询产品的订阅详情（使用新的后端接口）
export async function getProductSubscriptions(productId: string, params?: {
  status?: string;
  consumerName?: string;
  page?: number;
  size?: number;
}) {
  const searchParams = new URLSearchParams();
  if (params?.status) searchParams.append('status', params.status);
  if (params?.consumerName) searchParams.append('consumerName', params.consumerName);
  if (params?.page !== undefined) searchParams.append('page', params.page.toString());
  if (params?.size !== undefined) searchParams.append('size', params.size.toString());
  
  const url = `/products/${productId}/subscriptions${searchParams.toString() ? '?' + searchParams.toString() : ''}`;
  return api.get(url);
}

// 查询当前开发者对某个产品的订阅状态
export async function getProductSubscriptionStatus(productId: string) {
  try {
    // 使用新接口获取产品的所有订阅（不过滤状态，只要有申请就算已订阅）
    const response = await getProductSubscriptions(productId, { size: 100 });
    const subscriptions = response.data.content || [];
    
    // 转换为原有格式以保持兼容性
    const subscribedConsumers = subscriptions.map((sub: any) => ({
      consumer: {
        consumerId: sub.consumerId,
        name: sub.consumerName
      },
      subscription: sub,
      subscribed: true
    }));
    
    return {
      hasSubscription: subscribedConsumers.length > 0,
      subscribedConsumers: subscribedConsumers,
      allConsumers: [], // 延迟加载，在申请订阅时才获取
      // 新增：返回完整的订阅数据供管理弹窗使用
      fullSubscriptionData: {
        content: subscriptions,
        totalElements: response.data.totalElements || subscriptions.length,
        totalPages: response.data.totalPages || 1
      }
    };
  } catch (error) {
    console.error('Failed to get product subscription status:', error);
    throw error;
  }
}


// OIDC相关接口定义 - 对接OidcController
export interface IdpResult {
  provider: string;
  name: string;
}

export interface AuthResult {
  data: {
    access_token: string;
  }
}

// 获取OIDC提供商列表 - 对接 /developers/oidc/providers
export function getOidcProviders(): Promise<IdpResult[]> {
  return api.get('/developers/oidc/providers');
}

// OIDC回调处理 - 对接 /developers/oidc/callback
export function handleOidcCallback(code: string, state: string): Promise<AuthResult> {
  return api.get('/developers/oidc/callback', {
    params: { code, state }
  });
}

// Developer相关接口
// 开发者登出 - 对接 /developers/logout
export function developerLogout(): Promise<void> {
  return api.post('/developers/logout');
}

// API调用方法封装
export const categoryApi = {
  // 获取指定产品类型下的类别列表
  getCategoriesByProductType: (productType: string) => {
    return api.get(`/product-categories`, {
      params: {
        productType,
        size: 1000
      }
    })
  }
}

// ============ 聊天相关 API ============

// 分类信息接口
export interface Category {
  categoryId: string;
  name: string;
  description: string;
  icon: {
    type: "URL" | "BASE64";
    value: string;
  } | null;
  createAt: string;
  updatedAt: string;
}

// 产品信息接口
export interface Product {
  productId: string;
  name: string;
  description: string;
  status: string;
  enableConsumerAuth: boolean;
  type: string;
  document: string | null;
  icon: any | null;
  categories: string[];
  autoApprove: boolean | null;
  createAt: string;
  updatedAt: string;
  modelConfig?: {
    modelAPIConfig: {
      modelCategory: string;
      aiProtocols: string[];
      routes: any[];
      services: any[] | null;
    };
  };
  enabled: boolean;
}

// 会话相关接口
export interface Session {
  sessionId: string;
  name: string;
  productIds: string[];
  talkType: string;
  status: string;
  createAt: string;
  updateAt: string;
}

// 聊天消息接口
export interface ChatMessage {
  conversationId: string;
  questionId: string;
  answerIndex: number;
  question: string;
  attachments?: any[];
  stream?: boolean;
  needMemory?: boolean;
  enableThinking?: boolean;
  searchType?: string;
}

// 历史对话接口
export interface Conversation {
  conversationId: string;
  questions: Question[];
}

export interface Question {
  questionId: string;
  content: string;
  attachments: any[];
  answers: Answer[];
}

export interface Answer {
  results: AnswerResult[];
}

export interface AnswerResult {
  answerId: string;
  productId: string;
  content: string;
}

// 获取模型列表
export function getProducts(params: {
  type: string;
  categoryIds?: string[];
  page?: number;
  size?: number;
}) {
  return api.get('/products', {
    params: {
      type: params.type,
      categoryIds: params.categoryIds,
      page: params.page || 0,
      size: params.size || 100,
    },
  });
}

// 创建会话
export function createSession(data: {
  talkType: string;
  name: string;
  productIds: string[];
}) {
  return api.post('/sessions', data);
}

// 获取会话列表
export function getSessions(params?: { page?: number; size?: number }) {
  return api.get('/sessions', {
    params: {
      page: params?.page || 0,
      size: params?.size || 20,
    },
  });
}

// 更新会话名称
export function updateSession(sessionId: string, data: { name: string }) {
  return api.put(`/sessions/${sessionId}`, data);
}

// 发送聊天消息（流式）
export function sendChatMessage(sessionId: string, message: ChatMessage) {
  return api.post(`/sessions/${sessionId}/chats`, message);
}

// 发送聊天消息（流式，返回完整 URL 用于 SSE）
export function getChatMessageStreamUrl(sessionId: string): string {
  const baseURL = (api.defaults.baseURL || '') as string;
  return `${baseURL}/sessions/${sessionId}/chats`;
}

// 获取历史聊天记录
export function getConversations(sessionId: string) {
  return api.get(`/sessions/${sessionId}/conversations`);
}

export default api 