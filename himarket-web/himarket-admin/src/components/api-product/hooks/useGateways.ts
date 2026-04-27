import { useCallback, useState } from 'react';

import { gatewayApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';
import type { Gateway } from '@/types/gateway';

export function useGateways(productType: ApiProduct['type']) {
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [loading, setLoading] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const res = await gatewayApi.getGateways({
        page: 1,
        size: 1000,
      });
      let result: Gateway[] = [];
      const all = res.data?.content || [];
      switch (productType) {
        case 'REST_API':
          result = all.filter((item: Gateway) => item.gatewayType === 'APIG_API');
          break;
        case 'AGENT_API':
          result = all.filter((item: Gateway) => item.gatewayType === 'APIG_AI');
          break;
        case 'MODEL_API':
          result = all.filter(
            (item: Gateway) =>
              item.gatewayType === 'APIG_AI' ||
              item.gatewayType === 'HIGRESS' ||
              item.gatewayType === 'ADP_AI_GATEWAY' ||
              item.gatewayType === 'APSARA_GATEWAY',
          );
          break;
        case 'AGENT_SKILL':
          result = [];
          break;
        default:
          result = all.filter(
            (item: Gateway) =>
              item.gatewayType === 'HIGRESS' ||
              item.gatewayType === 'APIG_AI' ||
              item.gatewayType === 'ADP_AI_GATEWAY' ||
              item.gatewayType === 'APSARA_GATEWAY',
          );
      }
      setGateways(result);
    } catch (_error) {
      console.error('获取网关列表失败:', _error);
    } finally {
      setLoading(false);
    }
  }, [productType]);

  return { fetch, gateways, loading };
}
