import api from './api';
import type { 
  ProductCategory, 
  ProductCategoryPage, 
  CreateProductCategoryParam, 
  UpdateProductCategoryParam, 
  QueryProductCategoryParam 
} from '@/types/product-category';

// 创建产品类别
export const createProductCategory = async (data: CreateProductCategoryParam): Promise<{ data: ProductCategory }> => {
  return await api.post('/product-categories', data);
};

// 获取所有产品类别（无分页）
export const getProductCategories = async (): Promise<{ data: { content: ProductCategory[] } }> => {
  return await api.get('/product-categories?page=0&size=1000');
};

// 分页获取产品类别
export const getProductCategoriesByPage = async (
  page: number = 0, 
  size: number = 10, 
  params?: QueryProductCategoryParam
): Promise<{ data: ProductCategoryPage }> => {
  const queryParams = new URLSearchParams();
  queryParams.append('page', page.toString());
  queryParams.append('size', size.toString());
  
  if (params?.name) {
    queryParams.append('name', params.name);
  }
  
  return await api.get(`/product-categories?${queryParams.toString()}`);
};

// 获取单个产品类别详情
export const getProductCategory = async (categoryId: string): Promise<{ data: ProductCategory }> => {
  return await api.get(`/product-categories/${categoryId}`);
};

// 更新产品类别
export const updateProductCategory = async (
  categoryId: string, 
  data: UpdateProductCategoryParam
): Promise<{ data: ProductCategory }> => {
  return await api.put(`/product-categories/${categoryId}`, data);
};

// 删除产品类别
export const deleteProductCategory = async (categoryId: string): Promise<void> => {
  return await api.delete(`/product-categories/${categoryId}`);
};

// 从类别中移除产品
export const unbindProductsFromCategory = async (categoryId: string, productIds: string[]): Promise<void> => {
  return await api.delete(`/product-categories/${categoryId}/products`, { data: productIds });
};

// 向类别中添加产品
export const bindProductsToCategory = async (categoryId: string, productIds: string[]): Promise<void> => {
  return await api.post(`/product-categories/${categoryId}/products`, productIds);
};
