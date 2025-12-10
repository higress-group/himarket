import request from "./request";

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
  return request.get('/consumers', {
    params: {
      ...param,
      page: pageable.page,
      size: pageable.size,
    },
  });
}

export function deleteConsumer(consumerId: string) {
  return request.delete(`/consumers/${consumerId}`);
}

export function createConsumer(data: {name: string; description: string}) {
  return request.post('/consumers', data);
}

// 申请订阅API产品
export function subscribeProduct(consumerId: string, productId: string) {
  return request.post(`/consumers/${consumerId}/subscriptions`, {
    productId: productId
  });
}

// 获取某个consumer的订阅列表
export function getConsumerSubscriptions(consumerId: string, searchParams?: { productName?: string; status?: string }) {
  return request.get(`/consumers/${consumerId}/subscriptions`, {
    params: {
      page: 1,
      size: 100,
      ...searchParams
    }
  });
}

// 取消订阅
export function unsubscribeProduct(consumerId: string, productId: string) {
  return request.delete(`/consumers/${consumerId}/subscriptions/${productId}`);
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
  return request.get(url);
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
  return request.get('/developers/oidc/providers');
}

// OIDC回调处理 - 对接 /developers/oidc/callback
export function handleOidcCallback(code: string, state: string): Promise<AuthResult> {
  return request.get('/developers/oidc/callback', {
    params: { code, state }
  });
}

// Developer相关接口
// 开发者登出 - 对接 /developers/logout
export function developerLogout(): Promise<void> {
  return request.post('/developers/logout');
}

// API调用方法封装
export const categoryApi = {
  // 获取指定产品类型下的类别列表
  getCategoriesByProductType: (productType: string) => {
    return request.get(`/product-categories`, {
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


export default request;