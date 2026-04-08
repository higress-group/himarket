import React, { useEffect, useState } from 'react';

import APIs from '../lib/apis';

import type { IProductDetail } from '../lib/apis';

function useProducts(params: {
  type: string;
  categoryIds?: string[];
  name?: string;
  needInit?: boolean;
  ['modelFilter.category']?: 'Image' | 'TEXT';
}) {
  const [data, setData] = useState<IProductDetail[]>([]);
  const [loading, setLoading] = useState(false);

  const get = React.useCallback(
    ({
      categoryIds,
      ['modelFilter.category']: category,
      name,
      type,
    }: {
      type: string;
      categoryIds?: string[];
      name?: string;
      ['modelFilter.category']?: 'Image' | 'TEXT';
    }) => {
      setLoading(true);
      APIs.getProducts({
        categoryIds: categoryIds,
        ['modelFilter.category']: category,
        name,
        type: type,
      })
        .then((res) => {
          if (res.data?.content) {
            setData(res.data.content);
          }
        })
        .finally(() => setLoading(false));
    },
    [],
  );

  const set = setData;

  const needInit = params.needInit;
  const type = params.type;
  const categoryIds = params.categoryIds;
  const modelFilterCategory = params['modelFilter.category'];

  useEffect(() => {
    if (needInit === false) return;
    get({
      categoryIds,
      ['modelFilter.category']: modelFilterCategory,
      type,
    });
  }, [type, categoryIds, needInit, modelFilterCategory, get]);

  return {
    data,
    get,
    loading,
    set,
  };
}

export default useProducts;
