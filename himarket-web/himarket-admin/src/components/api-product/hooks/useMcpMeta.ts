import { useCallback, useState } from 'react';

import { mcpServerApi } from '@/lib/api';
import type { McpMetaItem } from '@/types/api-product';

export function useMcpMeta() {
  const [metaList, setMetaList] = useState<McpMetaItem[]>([]);

  const fetch = useCallback(async (productId: string) => {
    try {
      const res = await mcpServerApi.listMetaByProduct(productId);
      setMetaList(res.data || []);
    } catch {
      setMetaList([]);
    }
  }, []);

  return { fetch, metaList };
}
