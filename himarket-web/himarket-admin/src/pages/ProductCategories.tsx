import { PlusOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { useRef } from 'react';

import CategoryTable from '@/components/product-category/CategoryTable';
import type { CategoryTableRef } from '@/components/product-category/CategoryTable';

export default function ProductCategories() {
  const tableRef = useRef<CategoryTableRef>(null);

  return (
    <div className="space-y-6">
      {/* 页面头部 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Categories</h1>
          <p className="text-gray-500 mt-2">管理产品分类，帮助用户更好地发现和组织API产品</p>
        </div>
        <Button
          icon={<PlusOutlined />}
          onClick={() => tableRef.current?.handleCreate()}
          type="primary"
        >
          创建 Category
        </Button>
      </div>

      {/* 分类表格 */}
      <CategoryTable ref={tableRef} />
    </div>
  );
}
