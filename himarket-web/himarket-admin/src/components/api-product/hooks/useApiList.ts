import { useCallback, useState } from 'react';

import { gatewayApi, nacosApi } from '@/lib/api';
import type { ApiListItem } from '@/types/api-product';
import type { ApiProduct } from '@/types/api-product';
import type { Gateway, NacosInstance } from '@/types/gateway';

export function useApiList(productType: ApiProduct['type']) {
  const [apiList, setApiList] = useState<ApiListItem[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchByGateway = useCallback(
    async (gateway: Gateway) => {
      setLoading(true);
      try {
        let result: ApiListItem[] = [];
        const gatewayId = gateway.gatewayId;
        const gType = gateway.gatewayType;

        if (gType === 'APIG_API') {
          const res = await gatewayApi.getGatewayRestApis(gatewayId, {});
          result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
            apiId: api.apiId,
            apiName: api.apiName,
            type: 'REST API',
          }));
        } else if (gType === 'HIGRESS') {
          if (productType === 'MODEL_API') {
            const res = await gatewayApi.getGatewayModelApis(gatewayId, { page: 1, size: 1000 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              fromGatewayType: 'HIGRESS' as const,
              modelRouteName: api.modelRouteName,
              type: 'Model API',
            }));
          } else {
            const res = await gatewayApi.getGatewayMcpServers(gatewayId, { page: 1, size: 1000 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              fromGatewayType: 'HIGRESS' as const,
              mcpServerName: api.mcpServerName,
              type: 'MCP Server',
            }));
          }
        } else if (gType === 'APIG_AI') {
          if (productType === 'AGENT_API') {
            const res = await gatewayApi.getGatewayAgentApis(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              agentApiId: api.agentApiId,
              agentApiName: api.agentApiName,
              fromGatewayType: 'APIG_AI' as const,
              type: 'Agent API',
            }));
          } else if (productType === 'MODEL_API') {
            const res = await gatewayApi.getGatewayModelApis(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              fromGatewayType: 'APIG_AI' as const,
              modelApiId: api.modelApiId,
              modelApiName: api.modelApiName,
              type: 'Model API',
            }));
          } else {
            const res = await gatewayApi.getGatewayMcpServers(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              apiId: api.apiId,
              fromGatewayType: 'APIG_AI' as const,
              mcpRouteId: api.mcpRouteId,
              mcpServerId: api.mcpServerId,
              mcpServerName: api.mcpServerName,
              type: 'MCP Server',
            }));
          }
        } else if (gType === 'ADP_AI_GATEWAY') {
          if (productType === 'MODEL_API') {
            const res = await gatewayApi.getGatewayModelApis(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              fromGatewayType: 'ADP_AI_GATEWAY' as const,
              modelApiId: api.modelApiId,
              modelApiName: api.modelApiName,
              type: 'Model API',
            }));
          } else {
            const res = await gatewayApi.getGatewayMcpServers(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              fromGatewayType: 'ADP_AI_GATEWAY' as const,
              mcpRouteId: api.mcpRouteId,
              mcpServerId: api.mcpServerId,
              mcpServerName: api.mcpServerName || api.name,
              type: 'MCP Server',
            }));
          }
        } else if (gType === 'APSARA_GATEWAY') {
          if (productType === 'AGENT_API') {
            const res = await gatewayApi.getGatewayAgentApis(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              agentApiId: api.agentApiId,
              agentApiName: api.agentApiName,
              fromGatewayType: 'APSARA_GATEWAY' as const,
              type: 'Agent API',
            }));
          } else if (productType === 'MODEL_API') {
            const res = await gatewayApi.getGatewayModelApis(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              fromGatewayType: 'APSARA_GATEWAY' as const,
              modelApiId: api.modelApiId,
              modelApiName: api.modelApiName,
              type: 'Model API',
            }));
          } else {
            const res = await gatewayApi.getGatewayMcpServers(gatewayId, { page: 1, size: 500 });
            result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
              fromGatewayType: 'APSARA_GATEWAY' as const,
              mcpRouteId: api.mcpRouteId,
              mcpServerId: api.mcpServerId,
              mcpServerName: api.mcpServerName || api.name,
              type: 'MCP Server',
            }));
          }
        }
        setApiList(result);
      } catch (_error) {
        setApiList([]);
      } finally {
        setLoading(false);
      }
    },
    [productType],
  );

  const fetchByNacos = useCallback(
    async (nacos: NacosInstance, namespaceId: string) => {
      setLoading(true);
      try {
        let result: ApiListItem[] = [];
        if (productType === 'AGENT_API') {
          const res = await nacosApi.getNacosAgents(nacos.nacosId, {
            namespaceId,
            page: 1,
            size: 1000,
          });
          result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
            agentName: api.agentName,
            description: api.description,
            fromGatewayType: 'NACOS' as const,
            type: `Agent API (${namespaceId})`,
          }));
        } else if (productType === 'MCP_SERVER') {
          const res = await nacosApi.getNacosMcpServers(nacos.nacosId, {
            namespaceId,
            page: 1,
            size: 1000,
          });
          result = (res.data?.content || []).map((api: Record<string, unknown>) => ({
            fromGatewayType: 'NACOS' as const,
            mcpServerName: api.mcpServerName,
            type: `MCP Server (${namespaceId})`,
          }));
        }
        setApiList(result);
      } catch (_error) {
        setApiList([]);
      } finally {
        setLoading(false);
      }
    },
    [productType],
  );

  const clear = useCallback(() => {
    setApiList([]);
  }, []);

  return { apiList, clear, fetchByGateway, fetchByNacos, loading };
}
