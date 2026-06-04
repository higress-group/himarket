import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import APIs from '../lib/apis';

import type { ICategory } from '../lib/apis';

function useCategories(params: { type: string; addAll?: boolean }) {
  const { t } = useTranslation('square');
  const [data, setData] = useState<ICategory[]>([]);
  const [loading, setLoading] = useState(false);

  const get = React.useCallback(() => {
    setLoading(true);
    APIs.getCategoriesByProductType({ productType: params.type })
      .then((res) => {
        if (res.data?.content) {
          if (params.addAll) {
            setData([
              {
                categoryId: 'all',
                createAt: '',
                description: '',
                name: t('allCategory'),
                updatedAt: '',
              },
              ...res.data.content,
            ]);
          }
        }
      })
      .finally(() => setLoading(false));
  }, [params.type, params.addAll, t]);

  useEffect(() => {
    get();
  }, [get]);

  return {
    data,
    get,
    loading,
  };
}

export default useCategories;
