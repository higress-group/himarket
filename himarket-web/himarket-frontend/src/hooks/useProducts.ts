import React, { useEffect, useState } from "react";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";

function useProducts(params: { type: string, categoryIds?: string[], name?: string, needInit?: boolean, ["modelFilter.category"]?: "Image" | "TEXT" }) {
  const [data, setData] = useState<IProductDetail[]>([]);
  const [loading, setLoading] = useState(false);

  const get = React.useCallback(({ type, categoryIds, name, ["modelFilter.category"]: category }: { type: string, categoryIds?: string[], name?: string, ["modelFilter.category"]?: "Image" | "TEXT" }) => {
    setLoading(true);
    APIs.getProducts({ type: type, categoryIds: categoryIds, name, ["modelFilter.category"]: category })
      .then(res => {
        if (res.data?.content) {
          setData(res.data.content)
        }
      }).finally(() => setLoading(false));
  }, []);

  const set = setData;

  useEffect(() => {
    if (params.needInit === false) return;
    get({ type: params.type, categoryIds: params.categoryIds, ["modelFilter.category"]: params["modelFilter.category"] });
  }, [params.type, params.categoryIds, params.needInit, params["modelFilter.category"], get]);

  return {
    data,
    loading,
    get,
    set,
  }

}

export default useProducts;