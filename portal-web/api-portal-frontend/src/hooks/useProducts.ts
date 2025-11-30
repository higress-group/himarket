import { useEffect, useState } from "react";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";

function useProducts(params: { type: string, categoryIds?: string[] }) {
  const [data, setData] = useState<IProductDetail[]>([]);
  const [loading, setLoading] = useState(false);

  const get = () => {
    setLoading(true);
    APIs.getProducts({ type: params.type, categoryIds: params.categoryIds })
      .then(res => {
        if (res.data?.content) {
          setData(res.data.content)
        }
      }).finally(() => setLoading(false));
  }

  useEffect(() => {
    get();
  }, []);

  return {
    data,
    loading,
    get
  }

}

export default useProducts;