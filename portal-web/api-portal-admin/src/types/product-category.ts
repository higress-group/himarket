export interface ProductCategory {
  id: number;
  categoryId: string;
  code: string;
  name: string;
  description: string;
}

export interface CreateProductCategoryParam {
  code: string;
  name: string;
  description?: string;
}

export interface ProductCategoryPage {
  content: ProductCategory[];
  number: number;
  size: number;
  totalElements: number;
}