import React, { useEffect, useState } from 'react';

import APIs from '../lib/apis';

import type { ICategory } from '../lib/apis';

function useCategories(params: { type: string; addAll?: boolean }) {
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
                name: '全部',
                updatedAt: '',
              },
              ...res.data.content,
            ]);
          }
        }
      })
      .finally(() => setLoading(false));
  }, [params.type, params.addAll]);

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
