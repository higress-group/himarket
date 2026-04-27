import { Form, Modal, Select, message } from 'antd';
import { useCallback, useState } from 'react';

import { apiProductApi, nacosApi, mcpServerApi } from '@/lib/api';
import { getGatewayTypeLabel } from '@/lib/constant';
import type {
  ApiProduct,
  LinkedService,
  ApiListItem,
  NacosMCPItem,
  NacosAgentItem,
  RestAPIItem,
  APIGAIMCPItem,
  AIGatewayAgentItem,
  AIGatewayModelItem,
  AdpAIGatewayModelItem,
  ApsaraGatewayModelItem,
} from '@/types/api-product';
import type { Gateway, NacosInstance } from '@/types/gateway';

import { useApiList } from '../hooks/useApiList';
import { useGateways } from '../hooks/useGateways';
import { useNacosInstances } from '../hooks/useNacosInstances';

interface LinkApiModalProps {
  apiProduct: ApiProduct;
  linkedService: LinkedService | null;
  open: boolean;
  onCancel: () => void;
  onOk: () => void;
}

export function LinkApiModal({
  apiProduct,
  linkedService,
  onCancel,
  onOk,
  open,
}: LinkApiModalProps) {
  const [form] = Form.useForm();
  const [sourceType, setSourceType] = useState<'GATEWAY' | 'NACOS'>('GATEWAY');
  const [selectedGateway, setSelectedGateway] = useState<Gateway | null>(null);
  const [selectedNacos, setSelectedNacos] = useState<NacosInstance | null>(null);
  const [nacosNamespaces, setNacosNamespaces] = useState<
    Array<{ namespaceId: string; namespaceName: string }>
  >([]);
  const [selectedNamespace, setSelectedNamespace] = useState<string | null>(null);

  const { gateways, loading: gatewayLoading } = useGateways(apiProduct.type);
  const { instances: nacosInstances, loading: nacosLoading } = useNacosInstances();
  const {
    apiList,
    clear,
    fetchByGateway,
    fetchByNacos,
    loading: apiLoading,
  } = useApiList(apiProduct.type);

  const handleSourceTypeChange = (value: 'GATEWAY' | 'NACOS') => {
    setSourceType(value);
    setSelectedGateway(null);
    setSelectedNacos(null);
    setSelectedNamespace(null);
    setNacosNamespaces([]);
    clear();
    form.setFieldsValue({
      apiId: undefined,
      gatewayId: undefined,
      nacosId: undefined,
    });
  };

  const handleGatewayChange = useCallback(
    async (gatewayId: string) => {
      const gateway = gateways.find((g) => g.gatewayId === gatewayId);
      setSelectedGateway(gateway || null);
      if (!gateway) return;
      await fetchByGateway(gateway);
    },
    [gateways, fetchByGateway],
  );

  const handleNacosChange = useCallback(
    async (nacosId: string) => {
      const nacos = nacosInstances.find((n) => n.nacosId === nacosId);
      setSelectedNacos(nacos || null);
      setSelectedNamespace(null);
      clear();
      setNacosNamespaces([]);
      if (!nacos) return;

      try {
        const nsRes = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 });
        const namespaces = ((nsRes.data?.content || []) as Record<string, unknown>[]).map((ns) => ({
          namespaceDesc: ns.namespaceDesc,
          namespaceId: ns.namespaceId,
          namespaceName: ns.namespaceName || ns.namespaceId,
        }));
        setNacosNamespaces(namespaces as Array<{ namespaceId: string; namespaceName: string }>);
      } catch (e) {
        console.error('获取命名空间失败', e);
      }
    },
    [nacosInstances, clear],
  );

  const handleNamespaceChange = useCallback(
    async (namespaceId: string) => {
      setSelectedNamespace(namespaceId);
      if (!selectedNacos) return;
      await fetchByNacos(selectedNacos, namespaceId);
    },
    [selectedNacos, fetchByNacos],
  );

  const handleModalOk = () => {
    form.validateFields().then(async (values) => {
      const { apiId, gatewayId, nacosId, sourceType: st } = values;
      const selectedApi = apiList.find((item) => {
        if ('apiId' in item) {
          if ('mcpRouteId' in item) {
            return item.mcpRouteId === apiId;
          } else {
            return item.apiId === apiId;
          }
        } else if ('mcpServerName' in item) {
          return item.mcpServerName === apiId;
        } else if ('agentApiId' in item || 'agentApiName' in item) {
          return item.agentApiId === apiId || item.agentApiName === apiId;
        } else if ('modelApiId' in item || 'modelApiName' in item) {
          return item.modelApiId === apiId || item.modelApiName === apiId;
        } else if ('modelRouteName' in item && item.fromGatewayType === 'HIGRESS') {
          return item.modelRouteName === apiId;
        } else if ('agentName' in item) {
          return item.agentName === apiId;
        }
        return false;
      });

      if (apiProduct.type === 'MCP_SERVER' && selectedApi) {
        try {
          const mcpServerName = (selectedApi as Record<string, unknown>).mcpServerName || apiId;
          await mcpServerApi.saveMeta({
            connectionConfig: '{}',
            displayName: apiProduct.name,
            gatewayId: st === 'GATEWAY' ? gatewayId : undefined,
            mcpName: mcpServerName,
            nacosId: st === 'NACOS' ? nacosId : undefined,
            origin: st,
            productId: apiProduct.productId,
            protocolType: 'sse',
            publishStatus: 'DRAFT',
            refConfig: JSON.stringify(
              st === 'NACOS'
                ? { ...selectedApi, namespaceId: selectedNamespace || 'public' }
                : selectedApi,
            ),
            visibility: 'PUBLIC',
          });
          message.success('MCP 配置导入成功');
          onOk();
        } catch {
          message.error('MCP 配置导入失败');
        }
        return;
      }

      const newService: LinkedService = {
        adpAIGatewayRefConfig:
          selectedApi &&
          'fromGatewayType' in selectedApi &&
          selectedApi.fromGatewayType === 'ADP_AI_GATEWAY'
            ? apiProduct.type === 'MODEL_API'
              ? ({
                  fromGatewayType: 'ADP_AI_GATEWAY' as const,
                  modelApiId: (selectedApi as Record<string, unknown>).modelApiId,
                  modelApiName: (selectedApi as Record<string, unknown>).modelApiName,
                } as AdpAIGatewayModelItem)
              : (selectedApi as APIGAIMCPItem)
            : undefined,
        apigRefConfig:
          selectedApi &&
          ('apiId' in selectedApi ||
            'agentApiId' in selectedApi ||
            'agentApiName' in selectedApi ||
            'modelApiId' in selectedApi ||
            'modelApiName' in selectedApi) &&
          (!('fromGatewayType' in selectedApi) ||
            (selectedApi.fromGatewayType !== 'HIGRESS' &&
              selectedApi.fromGatewayType !== 'ADP_AI_GATEWAY' &&
              selectedApi.fromGatewayType !== 'APSARA_GATEWAY'))
            ? (selectedApi as RestAPIItem | APIGAIMCPItem | AIGatewayAgentItem | AIGatewayModelItem)
            : undefined,
        apsaraGatewayRefConfig:
          selectedApi &&
          'fromGatewayType' in selectedApi &&
          selectedApi.fromGatewayType === 'APSARA_GATEWAY'
            ? apiProduct.type === 'MODEL_API'
              ? ({
                  fromGatewayType: 'APSARA_GATEWAY' as const,
                  modelApiId: (selectedApi as Record<string, unknown>).modelApiId,
                  modelApiName: (selectedApi as Record<string, unknown>).modelApiName,
                } as ApsaraGatewayModelItem)
              : (selectedApi as APIGAIMCPItem)
            : undefined,
        gatewayId: st === 'GATEWAY' ? gatewayId : undefined,
        higressRefConfig:
          selectedApi &&
          'fromGatewayType' in selectedApi &&
          selectedApi.fromGatewayType === 'HIGRESS'
            ? apiProduct.type === 'MODEL_API'
              ? {
                  fromGatewayType: 'HIGRESS' as const,
                  modelRouteName: (selectedApi as Record<string, unknown>).modelRouteName as
                    | string
                    | undefined,
                }
              : {
                  fromGatewayType: 'HIGRESS' as const,
                  mcpServerName: (selectedApi as Record<string, unknown>).mcpServerName as
                    | string
                    | undefined,
                }
            : undefined,
        nacosId: st === 'NACOS' ? nacosId : undefined,
        nacosRefConfig:
          st === 'NACOS' &&
          selectedApi &&
          'fromGatewayType' in selectedApi &&
          selectedApi.fromGatewayType === 'NACOS'
            ? ({
                ...(selectedApi as unknown as Record<string, unknown>),
                namespaceId: selectedNamespace || 'public',
              } as NacosMCPItem | NacosAgentItem)
            : undefined,
        productId: apiProduct.productId,
        sourceType: st,
      };
      apiProductApi
        .createApiProductRef(apiProduct.productId, newService)
        .then(async () => {
          message.success('关联成功');
          onOk();
        })
        .catch(() => {
          message.error('关联失败');
        });
    });
  };

  const handleModalCancel = () => {
    form.resetFields();
    setSelectedGateway(null);
    setSelectedNacos(null);
    clear();
    setSourceType('GATEWAY');
    onCancel();
  };

  return (
    <Modal
      cancelText="取消"
      okText="关联"
      onCancel={handleModalCancel}
      onOk={handleModalOk}
      open={open}
      title={
        linkedService
          ? apiProduct.type === 'MCP_SERVER'
            ? '重新关联MCP Server'
            : '重新关联API'
          : apiProduct.type === 'MCP_SERVER'
            ? '关联MCP Server'
            : '关联新API'
      }
      width={600}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          initialValue="GATEWAY"
          label="来源类型"
          name="sourceType"
          rules={[{ message: '请选择来源类型', required: true }]}
        >
          <Select onChange={handleSourceTypeChange} placeholder="请选择来源类型">
            <Select.Option value="GATEWAY">网关</Select.Option>
            <Select.Option
              disabled={apiProduct.type === 'REST_API' || apiProduct.type === 'MODEL_API'}
              value="NACOS"
            >
              Nacos
            </Select.Option>
          </Select>
        </Form.Item>

        {sourceType === 'GATEWAY' && (
          <Form.Item
            label="网关实例"
            name="gatewayId"
            rules={[{ message: '请选择网关', required: true }]}
          >
            <Select
              filterOption={(input, option) =>
                (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
              }
              loading={gatewayLoading}
              onChange={handleGatewayChange}
              optionLabelProp="label"
              placeholder="请选择网关实例"
              showSearch
            >
              {gateways
                .filter((gateway) => {
                  if (apiProduct.type === 'AGENT_API') {
                    return gateway.gatewayType === 'APIG_AI';
                  }
                  if (apiProduct.type === 'MODEL_API') {
                    return (
                      gateway.gatewayType === 'APIG_AI' ||
                      gateway.gatewayType === 'HIGRESS' ||
                      gateway.gatewayType === 'ADP_AI_GATEWAY' ||
                      gateway.gatewayType === 'APSARA_GATEWAY'
                    );
                  }
                  return true;
                })
                .map((gateway) => (
                  <Select.Option
                    key={gateway.gatewayId}
                    label={gateway.gatewayName}
                    value={gateway.gatewayId}
                  >
                    <div>
                      <div className="font-medium">{gateway.gatewayName}</div>
                      <div className="text-sm text-gray-500">
                        {gateway.gatewayId} -{' '}
                        {getGatewayTypeLabel(
                          gateway.gatewayType as
                            | 'HIGRESS'
                            | 'APIG_AI'
                            | 'ADP_AI_GATEWAY'
                            | 'APSARA_GATEWAY'
                            | 'APIG_API',
                        )}
                      </div>
                    </div>
                  </Select.Option>
                ))}
            </Select>
          </Form.Item>
        )}

        {sourceType === 'NACOS' && (
          <Form.Item
            label="Nacos实例"
            name="nacosId"
            rules={[{ message: '请选择Nacos实例', required: true }]}
          >
            <Select
              filterOption={(input, option) =>
                (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
              }
              loading={nacosLoading}
              onChange={handleNacosChange}
              optionLabelProp="label"
              placeholder="请选择Nacos实例"
              showSearch
            >
              {nacosInstances.map((nacos) => (
                <Select.Option key={nacos.nacosId} label={nacos.nacosName} value={nacos.nacosId}>
                  <div>
                    <div className="font-medium">{nacos.nacosName}</div>
                    <div className="text-sm text-gray-500">{nacos.serverUrl}</div>
                  </div>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}

        {sourceType === 'NACOS' && selectedNacos && (
          <Form.Item
            label="命名空间"
            name="namespaceId"
            rules={[{ message: '请选择命名空间', required: true }]}
          >
            <Select
              filterOption={(input, option) =>
                (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
              }
              loading={apiLoading && nacosNamespaces.length === 0}
              onChange={handleNamespaceChange}
              optionLabelProp="label"
              placeholder="请选择命名空间"
              showSearch
            >
              {nacosNamespaces.map((ns: { namespaceId: string; namespaceName: string }) => (
                <Select.Option key={ns.namespaceId} label={ns.namespaceName} value={ns.namespaceId}>
                  <div>
                    <div className="font-medium">{ns.namespaceName}</div>
                    <div className="text-sm text-gray-500">{ns.namespaceId}</div>
                  </div>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}

        {(selectedGateway || (selectedNacos && selectedNamespace)) && (
          <Form.Item
            label={
              apiProduct.type === 'REST_API'
                ? '选择REST API'
                : apiProduct.type === 'AGENT_API'
                  ? '选择Agent API'
                  : apiProduct.type === 'MODEL_API'
                    ? '选择Model API'
                    : '选择MCP Server'
            }
            name="apiId"
            rules={[
              {
                message:
                  apiProduct.type === 'REST_API'
                    ? '请选择REST API'
                    : apiProduct.type === 'AGENT_API'
                      ? '请选择Agent API'
                      : apiProduct.type === 'MODEL_API'
                        ? '请选择Model API'
                        : '请选择MCP Server',
                required: true,
              },
            ]}
          >
            <Select
              filterOption={(input, option) =>
                (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
              }
              loading={apiLoading}
              optionLabelProp="label"
              placeholder={
                apiProduct.type === 'REST_API'
                  ? '请选择REST API'
                  : apiProduct.type === 'AGENT_API'
                    ? '请选择Agent API'
                    : apiProduct.type === 'MODEL_API'
                      ? '请选择Model API'
                      : '请选择MCP Server'
              }
              showSearch
            >
              {apiList.map((api: ApiListItem) => {
                let key: string, value: string, displayName: string;
                if (apiProduct.type === 'REST_API') {
                  key = api.apiId || '';
                  value = api.apiId || '';
                  displayName = api.apiName || '';
                } else if (apiProduct.type === 'AGENT_API') {
                  if ('agentName' in api) {
                    key = api.agentName || '';
                    value = api.agentName || '';
                    displayName = api.agentName || '';
                  } else {
                    key = api.agentApiId || api.agentApiName || '';
                    value = api.agentApiId || api.agentApiName || '';
                    displayName = api.agentApiName || '';
                  }
                } else if (apiProduct.type === 'MODEL_API') {
                  if (api.fromGatewayType === 'HIGRESS') {
                    key = api.modelRouteName || '';
                    value = api.modelRouteName || '';
                    displayName = api.modelRouteName || '';
                  } else {
                    key = api.modelApiId || api.modelApiName || '';
                    value = api.modelApiId || api.modelApiName || '';
                    displayName = api.modelApiName || '';
                  }
                } else {
                  key = String(api.mcpRouteId || api.mcpServerName || api.name || '');
                  value = String(api.mcpRouteId || api.mcpServerName || api.name || '');
                  displayName = String(api.mcpServerName || api.name || '');
                }

                return (
                  <Select.Option key={key || ''} label={displayName || ''} value={value || ''}>
                    <div>
                      <div className="font-medium">{displayName}</div>
                      <div className="text-sm text-gray-500">
                        {(api.type as string) || ''} - {api.description || key || ''}
                      </div>
                    </div>
                  </Select.Option>
                );
              })}
            </Select>
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}
