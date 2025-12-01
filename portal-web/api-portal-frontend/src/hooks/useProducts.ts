import { useEffect, useState } from "react";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";

function useProducts(params: { type: string, categoryIds?: string[], name?: string, needInit?: boolean }) {
  const [data, setData] = useState<IProductDetail[]>([]);
  const [loading, setLoading] = useState(false);

  const get = ({ type, categoryIds, name }: { type: string, categoryIds?: string[], name?: string }) => {
    setLoading(true);
    APIs.getProducts({ type: type, categoryIds: categoryIds, name })
      .then(res => {
        if (res.data?.content) {
          setData(res.data.content)
        }
      }).finally(() => setLoading(false));
  }

  const set = setData;

  useEffect(() => {
    if (params.needInit === false) return;
    get({ type: params.type, categoryIds: params.categoryIds });
  }, [params.type, params.categoryIds, params.needInit]);

  return {
    data,
    loading,
    get,
    set,
  }

}

export default useProducts;