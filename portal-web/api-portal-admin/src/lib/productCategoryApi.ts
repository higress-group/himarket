import api from './api';
import type { ProductCategory, ProductCategoryPage } from '@/types/product-category';

// 创建产品类别
export const createProductCategory = async (data: any): Promise<any> => {
  return await api.post('/product-categories', data);
};

// 获取所有产品类别
export const getProductCategories = async (): Promise<{ data: ProductCategory[] }> => {
  return await api.get('/product-categories');
};

// 分页获取产品类别
export const getProductCategoriesByPage = async (page: number, size: number): Promise<{ data: ProductCategoryPage }> => {
  return await api.get(`/product-categories/page?page=${page}&size=${size}`);
};

// 更新产品类别
export const updateProductCategory = async (categoryId: string, data: any): Promise<any> => {
  return await api.put(`/product-categories/${categoryId}`, data);
};

// 删除产品类别
export const deleteProductCategory = async (categoryId: string): Promise<any> => {
  return await api.delete(`/product-categories/${categoryId}`);
};