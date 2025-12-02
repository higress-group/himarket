/**
 * 消费者（Consumer）相关接口
 */

import request, { type RespI } from "../request";

// ============ 类型定义 ============

export interface IConsumer {
  consumerId: string;
  name: string;
  description: string;
  email?: string;
  status?: string;
  company?: string;
  createAt?: string;
  updatedAt?: string;
}

export interface ISubscription {
  subscriptionId: string;
  consumerId: string;
  consumerName: string;
  productId: string;
  productName: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createAt: string;
  updatedAt: string;
}

interface QueryConsumerParams {
  name?: string;
  email?: string;
  status?: string;
  company?: string;
  page?: number;
  size?: number;
}

interface GetConsumersResp {
  content: IConsumer[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

interface CreateConsumerData {
  name: string;
  description: string;
}

interface GetSubscriptionsParams {
  productName?: string;
  status?: string;
  page?: number;
  size?: number;
}

interface GetSubscriptionsResp {
  content: ISubscription[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

interface SubscribeProductData {
  productId: string;
}

// ============ API 函数 ============

/**
 * 获取消费者列表
 */
export function getConsumers(params: QueryConsumerParams) {
  return request.get<RespI<GetConsumersResp>, RespI<GetConsumersResp>>(
    '/consumers',
    {
      params: {
        name: params.name,
        email: params.email,
        status: params.status,
        company: params.company,
        page: params.page || 0,
        size: params.size || 20,
      },
    }
  );
}

/**
 * 获取消费者详情
 */

export function getConsumer(params: { id: string }) {
  return request.get<RespI<IConsumer>, RespI<IConsumer>>(
    `/consumers/${params.id}`
  );
}

/**
 * 创建消费者
 */
export function createConsumer(data: CreateConsumerData) {
  return request.post<RespI<IConsumer>, RespI<IConsumer>>(
    '/consumers',
    data
  );
}

/**
 * 删除消费者
 */
export function deleteConsumer(consumerId: string) {
  return request.delete<RespI<void>, RespI<void>>(
    `/consumers/${consumerId}`
  );
}

/**
 * 获取某个消费者的订阅列表
 */
export function getConsumerSubscriptions(
  consumerId: string,
  params?: GetSubscriptionsParams
) {
  return request.get<RespI<GetSubscriptionsResp>, RespI<GetSubscriptionsResp>>(
    `/consumers/${consumerId}/subscriptions`,
    {
      params: {
        productName: params?.productName,
        status: params?.status,
        page: params?.page || 0,
        size: params?.size || 100,
      },
    }
  );
}

/**
 * 申请订阅产品
 */
export function subscribeProduct(consumerId: string, productId: string) {
  const data: SubscribeProductData = { productId };
  return request.post<RespI<ISubscription>, RespI<ISubscription>>(
    `/consumers/${consumerId}/subscriptions`,
    data
  );
}

/**
 * 取消订阅产品
 */
export function unsubscribeProduct(consumerId: string, productId: string) {
  return request.delete<RespI<void>, RespI<void>>(
    `/consumers/${consumerId}/subscriptions/${productId}`
  );
}

/**
 * 获取产品的订阅列表
 */
export function getProductSubscriptions(
  productId: string,
  params?: {
    status?: string;
    consumerName?: string;
    page?: number;
    size?: number;
  }
) {
  return request.get<RespI<GetSubscriptionsResp>, RespI<GetSubscriptionsResp>>(
    `/products/${productId}/subscriptions`,
    {
      params: {
        status: params?.status,
        consumerName: params?.consumerName,
        page: params?.page || 0,
        size: params?.size || 20,
      },
    }
  );
}

/**
 * 查询当前开发者对某个产品的订阅状态
 * 包含完整的订阅数据供管理弹窗使用
 */
export async function getProductSubscriptionStatus(productId: string) {
  try {
    const response = await getProductSubscriptions(productId, { size: 100 });
    const subscriptions = response.data.content || [];

    // 转换为原有格式以保持兼容性
    const subscribedConsumers = subscriptions.map((sub) => ({
      consumer: {
        consumerId: sub.consumerId,
        name: sub.consumerName,
      },
      subscription: sub,
      subscribed: true,
    }));

    return {
      hasSubscription: subscribedConsumers.length > 0,
      subscribedConsumers: subscribedConsumers,
      allConsumers: [], // 延迟加载，在申请订阅时才获取
      // 新增：返回完整的订阅数据供管理弹窗使用
      fullSubscriptionData: {
        content: subscriptions,
        totalElements: response.data.totalElements || subscriptions.length,
        totalPages: response.data.totalPages || 1,
      },
    };
  } catch (error) {
    console.error('Failed to get product subscription status:', error);
    throw error;
  }
}


/**
 * 获取默认消费者
 */
export interface IGetPrimaryConsumerResp {
  consumerId: string;
  name: string;
  description: string;
  isPrimary: true,
  createAt: string;
}
export function getPrimaryConsumer() {
  return request<RespI<IGetPrimaryConsumerResp>, RespI<IGetPrimaryConsumerResp>>(
    "/consumers/primary"
  );
}

/**
 * 更新默认消费者
 */

export function putPrimaryConsumer(id: string) {
  return request.put<RespI<unknown>, RespI<unknown>>(`/consumers/${id}/primary`)
}