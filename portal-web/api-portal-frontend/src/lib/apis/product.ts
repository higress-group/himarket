/**
 * 模型相关接口
 */

import request, { type RespI } from "../request";
import type { IAgentConfig, IAPIConfig, IMCPConfig, IModelConfig, IProductIcon } from "./typing";

export interface IProductDetail {
  productId: string;
  name: string;
  description: string;
  status: string;
  enableConsumerAuth: boolean;
  type: string;
  document: string | null;
  icon?: IProductIcon;
  categories: {
    categoryId: string;
    name: string;
    description: string;
    icon: {
      type: string,
      value: string
    },
    createAt: string;
    updatedAt: string;
  }[];
  autoApprove: boolean | null;
  createAt: string;
  updatedAt: string;
  apiConfig: IAPIConfig,
  agentConfig: IAgentConfig;
  mcpConfig: IMCPConfig;
  modelConfig?: IModelConfig;
  enabled: boolean;
}

interface GetProductsResp {
  content: IProductDetail[];
}
// 获取模型列表
export function getProducts(params: {
  type: string;
  categoryIds?: string[];
  page?: number;
  size?: number;
}) {
  const p = `type=${params.type}${params.categoryIds?.length ? `&${encodeURIComponent("categoryIds[]")}=${params.categoryIds.join(`&${encodeURIComponent("categoryIds[]")}=`)}` : ''}&page=${params.page || 0}&size=${params.size || 100}`;
  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>('/products?' + p);
}


export function getProduct(params: { id: string }) {
  return request.get<RespI<IProductDetail>, RespI<IProductDetail>>('/products/' + params.id)
}