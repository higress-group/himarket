import { useEffect, useState } from "react";
import type { ICategory } from "../lib/apis";
import APIs from "../lib/apis";

function useCategories(params: { type: string, addAll?: boolean }) {
  const [data, setData] = useState<ICategory[]>([]);
  const [loading, setLoading] = useState(false);

  const get = () => {
    setLoading(true);
    APIs.getCategoriesByProductType({ productType: params.type })
      .then(res => {
        if (res.data?.content) {
          if (params.addAll) {
            setData([
              {
                categoryId: "all",
                name: "全部",
                description: "",
                createAt: "",
                updatedAt: "",
              },
              ...res.data.content
            ])
          }
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

export default useCategories;