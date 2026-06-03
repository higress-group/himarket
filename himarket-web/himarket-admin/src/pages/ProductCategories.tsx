import { PlusOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { useRef } from 'react';

import { AdminPageHeader } from '@/components/common';
import CategoryTable from '@/components/product-category/CategoryTable';
import type { CategoryTableRef } from '@/components/product-category/CategoryTable';
import { useLocale } from '@/contexts/LocaleContext';

export default function ProductCategories() {
  const tableRef = useRef<CategoryTableRef>(null);
  const { t } = useLocale();

  return (
    <div className="space-y-6">
      <AdminPageHeader
        actions={
          <Button
            icon={<PlusOutlined />}
            onClick={() => tableRef.current?.handleCreate()}
            type="primary"
          >
            {t('page.categories.create')}
          </Button>
        }
        description={t('page.categories.description')}
        title={t('page.categories.title')}
      />

      <CategoryTable ref={tableRef} />
    </div>
  );
}
