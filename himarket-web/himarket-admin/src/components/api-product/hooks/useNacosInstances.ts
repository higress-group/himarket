import { useCallback, useState } from 'react';

import { nacosApi } from '@/lib/api';
import type { NacosInstance } from '@/types/gateway';

export function useNacosInstances() {
  const [instances, setInstances] = useState<NacosInstance[]>([]);
  const [loading, setLoading] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const res = await nacosApi.getNacos({
        page: 1,
        size: 1000,
      });
      setInstances(res.data?.content || []);
    } catch (_error) {
      console.error('获取Nacos实例列表失败:', _error);
    } finally {
      setLoading(false);
    }
  }, []);

  return { fetch, instances, loading };
}
