export interface ProductIcon {
  type: 'URL' | 'BASE64';
  value: string;
}

export interface ProductCategory {
  id?: number;
  categoryId: string;
  name: string;
  description?: string;
  icon?: ProductIcon;
  createAt?: string;
  updatedAt?: string;
}

export interface CreateProductCategoryParam {
  name: string;
  description?: string;
  icon?: ProductIcon;
}

export interface UpdateProductCategoryParam {
  name?: string;
  description?: string;
  icon?: ProductIcon;
}

export interface QueryProductCategoryParam {
  name?: string;
}

export interface ProductCategoryPage {
  content: ProductCategory[];
  number: number;
  size: number;
  totalElements: number;
}