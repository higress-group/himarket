import { Form, Modal, Select, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi, nacosApi } from '@/lib/api';
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
  const { t } = useLocale();
  const [form] = Form.useForm();
  const [sourceType, setSourceType] = useState<'GATEWAY' | 'NACOS'>('GATEWAY');
  const [selectedGateway, setSelectedGateway] = useState<Gateway | null>(null);
  const [selectedNacos, setSelectedNacos] = useState<NacosInstance | null>(null);
  const [nacosNamespaces, setNacosNamespaces] = useState<
    Array<{ namespaceId: string; namespaceName: string }>
  >([]);
  const [selectedNamespace, setSelectedNamespace] = useState<string | null>(null);

  const { fetch: fetchGateways, gateways, loading: gatewayLoading } = useGateways(apiProduct.type);
  const {
    fetch: fetchNacosInstances,
    instances: nacosInstances,
    loading: nacosLoading,
  } = useNacosInstances();
  const {
    apiList,
    clear,
    fetchByGateway,
    fetchByNacos,
    loading: apiLoading,
  } = useApiList(apiProduct.type);

  useEffect(() => {
    if (open) {
      fetchGateways();
      fetchNacosInstances();
      form.resetFields();
      setSelectedGateway(null);
      setSelectedNacos(null);
      setSelectedNamespace(null);
      setNacosNamespaces([]);
      setSourceType('GATEWAY');
      clear();
    }
  }, [open, fetchGateways, fetchNacosInstances, form, clear]);

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

  const getGatewayTypeDisplay = (gatewayType: string) => {
    switch (gatewayType) {
      case 'APIG_API':
        return t('product.linkApi.apiGateway');
      case 'APIG_AI':
        return t('product.linkApi.apiGateway');
      case 'HIGRESS':
        return t('product.linkApi.higressGateway');
      case 'ADP_AI_GATEWAY':
        return t('product.linkApi.privateAiGateway');
      case 'APSARA_GATEWAY':
        return t('product.linkApi.apsaraGateway');
      default:
        return gatewayType;
    }
  };

  const getApiSelectLabel = () => {
    if (apiProduct.type === 'REST_API') {
      return t('product.linkApi.selectRestApi');
    }
    if (apiProduct.type === 'AGENT_API') {
      return t('product.linkApi.selectAgentApi');
    }
    if (apiProduct.type === 'MODEL_API') {
      return t('product.linkApi.selectModelApi');
    }
    return t('product.linkApi.selectMcpServer');
  };

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
          message.success(t('product.linkApi.linkSuccess'));
          onOk();
        })
        .catch(() => {
          message.error(t('product.linkApi.linkFailed'));
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
      cancelText={t('common.cancel')}
      okText={t('product.linkApi.linkApi')}
      onCancel={handleModalCancel}
      onOk={handleModalOk}
      open={open}
      title={
        linkedService
          ? apiProduct.type === 'MCP_SERVER'
            ? t('product.linkApi.relinkMcp')
            : t('product.linkApi.relinkApi')
          : apiProduct.type === 'MCP_SERVER'
            ? t('product.linkApi.linkMcp')
            : t('product.linkApi.linkNewApi')
      }
      width={600}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          initialValue="GATEWAY"
          label={t('product.linkApi.sourceType')}
          name="sourceType"
          rules={[{ message: t('product.linkApi.selectSourceType'), required: true }]}
        >
          <Select
            onChange={handleSourceTypeChange}
            placeholder={t('product.linkApi.selectSourceType')}
          >
            <Select.Option value="GATEWAY">{t('product.linkApi.gateway')}</Select.Option>
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
            label={t('product.linkApi.gatewayInstance')}
            name="gatewayId"
            rules={[{ message: t('product.linkApi.selectGateway'), required: true }]}
          >
            <Select
              filterOption={(input, option) =>
                (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
              }
              loading={gatewayLoading}
              onChange={handleGatewayChange}
              optionLabelProp="label"
              placeholder={t('product.linkApi.selectGatewayInstance')}
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
                        {gateway.gatewayId} - {getGatewayTypeDisplay(gateway.gatewayType)}
                      </div>
                    </div>
                  </Select.Option>
                ))}
            </Select>
          </Form.Item>
        )}

        {sourceType === 'NACOS' && (
          <Form.Item
            label={t('product.linkApi.nacosInstance')}
            name="nacosId"
            rules={[{ message: t('product.linkApi.selectNacosInstance'), required: true }]}
          >
            <Select
              filterOption={(input, option) =>
                (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
              }
              loading={nacosLoading}
              onChange={handleNacosChange}
              optionLabelProp="label"
              placeholder={t('product.linkApi.selectNacosInstance')}
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
            label={t('product.linkApi.namespace')}
            name="namespaceId"
            rules={[{ message: t('product.linkApi.selectNamespace'), required: true }]}
          >
            <Select
              filterOption={(input, option) =>
                (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
              }
              loading={apiLoading && nacosNamespaces.length === 0}
              onChange={handleNamespaceChange}
              optionLabelProp="label"
              placeholder={t('product.linkApi.selectNamespace')}
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
            label={getApiSelectLabel()}
            name="apiId"
            rules={[
              {
                message: getApiSelectLabel(),
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
              placeholder={getApiSelectLabel()}
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
