/**
 * 产品分类相关接口
 */

import request, { type RespI } from "../request";
import type { IProductIcon } from "./typing";

// ============ 类型定义 ============

export interface ICategory {
  categoryId: string;
  name: string;
  description: string;
  icon?: IProductIcon;
  createAt: string;
  updatedAt: string;
}

interface GetCategoriesParams {
  productType: string;
  page?: number;
  size?: number;
}

interface GetCategoriesResp {
  content: ICategory[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// ============ API 函数 ============

/**
 * 获取指定产品类型下的分类列表
 */
export function getCategoriesByProductType(params: GetCategoriesParams) {
  return request.get<RespI<GetCategoriesResp>, RespI<GetCategoriesResp>>(
    '/product-categories',
    {
      params: {
        productType: params.productType,
        page: params.page || 0,
        size: params.size || 1000,
      },
    }
  );
}
