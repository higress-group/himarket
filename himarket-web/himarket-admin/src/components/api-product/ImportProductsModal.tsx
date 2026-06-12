import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Modal, Form, Select, Table, message, Space, Input, Button } from 'antd';
import { useState, useEffect } from 'react';

import { ImportResultModal, type ImportResultFailure } from '@/components/common/ImportResultModal';
import { useLocale } from '@/contexts/LocaleContext';
import { airegistryApi, apiProductApi, gatewayApi, nacosApi } from '@/lib/api';
import type { AiRegistryInstance, Gateway } from '@/types/gateway';

import type { TableColumnsType } from 'antd';

interface ImportProductsModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  productType: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API' | 'AGENT_SKILL';
  importSource?: ImportSource;
}

interface ServiceItem {
  key: string;
  name: string;
  description?: string;
  // Gateway fields
  apiId?: string;
  mcpServerId?: string;
  mcpRouteId?: string;
  agentApiId?: string;
  modelApiId?: string;
  modelRouteName?: string;
  // Nacos fields
  mcpServerName?: string;
  agentName?: string;
  skillName?: string;
  namespaceId?: string;
}

interface ImportFailure {
  resourceName?: string;
  errorMessage?: string;
}

interface ImportResult {
  successCount?: number;
  failures?: ImportFailure[];
}

interface ImportResultState {
  selectedCount: number;
  successCount: number;
  failures: ImportResultFailure[];
}

type ImportSource = 'GATEWAY' | 'NACOS' | 'AIREGISTRY';
type SourceType = 'API_GATEWAY' | 'HIGRESS' | 'AI_GATEWAY' | 'NACOS' | 'AIREGISTRY';

const PRODUCT_TYPE_LABELS: Record<string, string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

export default function ImportProductsModal({
  importSource,
  onCancel,
  onSuccess,
  productType,
  visible,
}: ImportProductsModalProps) {
  const { t } = useLocale();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [servicesLoading, setServicesLoading] = useState(false);
  const [sourceType, setSourceType] = useState<SourceType>('HIGRESS');
  const [apiGateways, setApiGateways] = useState<Gateway[]>([]);
  const [higressGateways, setHigressGateways] = useState<Gateway[]>([]);
  const [aiGateways, setAiGateways] = useState<Gateway[]>([]);
  const [nacosInstances, setNacosInstances] = useState<
    Array<{ nacosId: string; nacosName: string }>
  >([]);
  const [aiRegistryInstances, setAiRegistryInstances] = useState<AiRegistryInstance[]>([]);
  const [namespaces, setNamespaces] = useState<unknown[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [selectedServiceKeys, setSelectedServiceKeys] = useState<string[]>([]);
  const [importResult, setImportResult] = useState<ImportResultState | null>(null);
  const [searchText, setSearchText] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [tablePageSize, setTablePageSize] = useState<number>(10);

  const pageSize = 500;
  const sourceTypeLabels: Record<SourceType, string> = {
    AI_GATEWAY: t('product.import.source.aiGateway'),
    AIREGISTRY: t('product.import.source.airegistry'),
    API_GATEWAY: t('product.import.source.apiGateway'),
    HIGRESS: t('product.import.source.higress'),
    NACOS: t('product.import.source.nacos'),
  };

  const supportsApiGateway = productType === 'REST_API' && importSource !== 'NACOS';

  const supportsHigress =
    importSource !== 'NACOS' && (productType === 'MCP_SERVER' || productType === 'MODEL_API');

  const supportsAIGateway =
    importSource !== 'NACOS' &&
    (productType === 'MODEL_API' || productType === 'MCP_SERVER' || productType === 'AGENT_API');

  const supportsNacos =
    importSource !== 'GATEWAY' &&
    (productType === 'MCP_SERVER' || productType === 'AGENT_API' || productType === 'AGENT_SKILL');

  const supportsAiRegistry = importSource !== 'GATEWAY' && productType === 'AGENT_SKILL';

  const gatewaySourceTypes: SourceType[] = [
    ...(supportsApiGateway ? (['API_GATEWAY'] as const) : []),
    ...(supportsHigress ? (['HIGRESS'] as const) : []),
    ...(supportsAIGateway ? (['AI_GATEWAY'] as const) : []),
  ];

  const sourceTypeOptions: SourceType[] =
    importSource === 'GATEWAY'
      ? gatewaySourceTypes
      : importSource === 'NACOS'
        ? ['NACOS']
        : importSource === 'AIREGISTRY'
          ? ['AIREGISTRY']
          : [
              ...gatewaySourceTypes,
              ...(supportsNacos ? (['NACOS'] as const) : []),
              ...(supportsAiRegistry ? (['AIREGISTRY'] as const) : []),
            ];

  const showSourceTypeSelector =
    !importSource || (importSource === 'GATEWAY' && gatewaySourceTypes.length > 1);

  const productTypeLabel = PRODUCT_TYPE_LABELS[productType] ?? productType;

  const modalTitle =
    importSource === 'GATEWAY'
      ? t('product.import.gatewaySourceTitle', { type: productTypeLabel })
      : importSource === 'NACOS'
        ? t('product.import.nacosSourceTitle', { type: productTypeLabel })
        : importSource === 'AIREGISTRY'
          ? t('product.import.airegistrySourceTitle', { type: productTypeLabel })
          : t('product.import.title', { type: productTypeLabel });

  const getDefaultSourceType = () => sourceTypeOptions[0] ?? 'API_GATEWAY';

  const filteredServices = services.filter((service) => {
    if (!searchText) return true;
    const lowerSearch = searchText.toLowerCase();
    return (
      service.name.toLowerCase().includes(lowerSearch) ||
      (service.description && service.description.toLowerCase().includes(lowerSearch))
    );
  });

  useEffect(() => {
    setCurrentPage(1);
  }, [searchText]);

  const handleSelectAll = () => {
    const allKeys = filteredServices.map((s) => s.key);
    setSelectedServiceKeys(allKeys);
  };

  const handleDeselectAll = () => {
    setSelectedServiceKeys([]);
  };

  const isGatewaySource =
    sourceType === 'API_GATEWAY' || sourceType === 'HIGRESS' || sourceType === 'AI_GATEWAY';

  const resetForm = () => {
    form.resetFields();
    const defaultSourceType = getDefaultSourceType();
    setSourceType(defaultSourceType);
    form.setFieldValue('sourceType', defaultSourceType);
    setServices([]);
    setSelectedServiceKeys([]);
    setNamespaces([]);
    setSearchText('');
    setCurrentPage(1);
  };

  const fetchGateways = async () => {
    try {
      const res = await gatewayApi.getGateways({ page: 1, size: 100 });
      const allGateways = res.data?.content || [];
      const apiGws = allGateways.filter((gw: Gateway) => gw.gatewayType === 'APIG_API');
      const higress = allGateways.filter((gw: Gateway) => gw.gatewayType === 'HIGRESS');
      const aiGws = allGateways.filter(
        (gw: Gateway) =>
          gw.gatewayType === 'APIG_AI' ||
          gw.gatewayType === 'ADP_AI_GATEWAY' ||
          gw.gatewayType === 'APSARA_GATEWAY',
      );
      setApiGateways(apiGws);
      setHigressGateways(higress);
      setAiGateways(aiGws);
    } catch (_error) {
      message.error(t('product.import.fetchGatewaysFailed'));
    }
  };

  const fetchNacosInstances = async () => {
    try {
      const res = await nacosApi.getNacos({ page: 1, size: 100 });
      const instances = res.data?.content || [];
      setNacosInstances(instances);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(
        t('product.import.fetchNacosFailed', {
          message: err.response?.data?.message || err.message || t('common.unknown'),
        }),
      );
      setNacosInstances([]);
    }
  };

  const fetchAiRegistryInstances = async () => {
    try {
      const res = await airegistryApi.list({ page: 1, size: 100 });
      setAiRegistryInstances(res.data?.content || []);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(
        t('product.import.fetchAiRegistryFailed', {
          message: err.response?.data?.message || err.message || t('common.unknown'),
        }),
      );
      setAiRegistryInstances([]);
    }
  };

  const fetchNamespaces = async (nacosId: string) => {
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 100 });
      const nsContent = (res.data?.content || []) as unknown[];
      setNamespaces(nsContent);
      const publicNs = nsContent.find((ns: unknown) => {
        const n = ns as { namespaceId?: string };
        return n.namespaceId === 'public';
      });
      if (publicNs) {
        form.setFieldValue('namespaceId', 'public');
        fetchServices({ nacosId, namespaceId: 'public' });
      }
    } catch (_error) {
      message.error(t('product.import.fetchNamespacesFailed'));
      setNamespaces([]);
    }
  };

  const fetchServices = async (
    overrides: Partial<{
      airegistryId: string;
      gatewayId: string;
      nacosId: string;
      namespaceId: string;
    }> = {},
  ) => {
    const values = { ...form.getFieldsValue(), ...overrides };

    if (isGatewaySource && !values.gatewayId) {
      message.warning(t('product.import.selectGatewayFirst'));
      return;
    }

    if (sourceType === 'NACOS' && (!values.nacosId || !values.namespaceId)) {
      message.warning(t('product.import.selectNacosNamespaceFirst'));
      return;
    }

    if (sourceType === 'AIREGISTRY' && (!values.airegistryId || !values.namespaceId)) {
      message.warning(t('product.import.selectAiRegistryNamespaceFirst'));
      return;
    }

    setServicesLoading(true);
    setCurrentPage(1);
    try {
      let res: unknown;

      if (isGatewaySource) {
        switch (productType) {
          case 'REST_API':
            res = await gatewayApi.getGatewayRestApis(values.gatewayId, {
              page: 1,
              size: pageSize,
            });
            {
              const responseData = res as { data?: { content?: unknown[] } };
              const items = (responseData.data?.content || []).map((item: unknown) => {
                const it = item as Record<string, string>;
                return {
                  apiId: it.apiId,
                  description: it.description,
                  key: it.apiId || '',
                  name: it.apiName || '',
                };
              });
              setServices(items);
            }
            break;
          case 'MCP_SERVER':
            res = await gatewayApi.getGatewayMcpServers(values.gatewayId, {
              page: 1,
              size: pageSize,
            });
            {
              const responseData = res as { data?: { content?: unknown[] } };
              const items = (responseData.data?.content || []).map((item: unknown) => {
                const it = item as Record<string, string>;
                return {
                  description: it.description,
                  key: it.mcpServerId || it.mcpServerName || '',
                  mcpRouteId: it.mcpRouteId,
                  mcpServerId: it.mcpServerId,
                  mcpServerName: it.mcpServerName,
                  name: it.mcpServerName || '',
                };
              });
              setServices(items);
            }
            break;
          case 'AGENT_API':
            res = await gatewayApi.getGatewayAgentApis(values.gatewayId, {
              page: 1,
              size: pageSize,
            });
            {
              const responseData = res as { data?: { content?: unknown[] } };
              const items = (responseData.data?.content || []).map((item: unknown) => {
                const it = item as Record<string, string>;
                return {
                  agentApiId: it.agentApiId,
                  description: it.description,
                  key: it.agentApiId || '',
                  name: it.agentApiName || '',
                };
              });
              setServices(items);
            }
            break;
          case 'MODEL_API':
            res = await gatewayApi.getGatewayModelApis(values.gatewayId, {
              page: 1,
              size: pageSize,
            });
            {
              const responseData = res as { data?: { content?: unknown[] } };
              const items = (responseData.data?.content || []).map((item: unknown) => {
                const it = item as Record<string, string>;
                return {
                  description: it.description,
                  key: it.modelApiId || it.modelRouteName || '',
                  modelApiId: it.modelApiId,
                  modelRouteName: it.modelRouteName,
                  name: it.modelApiName || it.modelRouteName || it.name || '',
                };
              });
              setServices(items);
            }
            break;
          default:
            message.error(t('product.import.unsupportedGatewayImport'));
            setServices([]);
        }
      } else if (sourceType === 'NACOS') {
        if (productType === 'MCP_SERVER') {
          res = await nacosApi.getNacosMcpServers(values.nacosId, {
            namespaceId: values.namespaceId,
            page: 1,
            size: pageSize,
          });
          {
            const responseData = res as { data?: { content?: unknown[] } };
            const items = (responseData.data?.content || []).map((item: unknown) => {
              const it = item as Record<string, string>;
              return {
                description: it.description,
                key: it.mcpServerName || '',
                mcpServerName: it.mcpServerName,
                name: it.mcpServerName || '',
                namespaceId: values.namespaceId,
              };
            });
            setServices(items);
          }
        } else if (productType === 'AGENT_API') {
          res = await nacosApi.getNacosAgents(values.nacosId, {
            namespaceId: values.namespaceId,
            page: 1,
            size: pageSize,
          });
          {
            const responseData = res as { data?: { content?: unknown[] } };
            const items = (responseData.data?.content || []).map((item: unknown) => {
              const it = item as Record<string, string>;
              return {
                agentName: it.agentName,
                description: it.description,
                key: it.agentName || '',
                name: it.agentName || '',
                namespaceId: values.namespaceId,
              };
            });
            setServices(items);
          }
        } else if (productType === 'AGENT_SKILL') {
          res = await nacosApi.getNacosSkills(values.nacosId, {
            namespaceId: values.namespaceId,
            page: 1,
            size: pageSize,
          });
          {
            const responseData = res as { data?: { content?: unknown[] } };
            const items = (responseData.data?.content || []).map((item: unknown) => {
              const it = item as Record<string, string>;
              return {
                description: it.description,
                key: it.name || '',
                name: it.name || '',
                namespaceId: values.namespaceId,
                skillName: it.name,
              };
            });
            setServices(items);
          }
        }
      } else if (sourceType === 'AIREGISTRY' && productType === 'AGENT_SKILL') {
        res = await airegistryApi.getSkills(values.airegistryId, {
          namespaceId: values.namespaceId,
          page: 1,
          size: pageSize,
        });
        {
          const responseData = res as { data?: { content?: unknown[] } };
          const items = (responseData.data?.content || []).map((item: unknown) => {
            const it = item as Record<string, string>;
            return {
              description: it.description,
              key: it.name || '',
              name: it.name || '',
              namespaceId: values.namespaceId,
              skillName: it.name,
            };
          });
          setServices(items);
        }
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(err.response?.data?.message || t('product.import.fetchResourcesFailed'));
      setServices([]);
    } finally {
      setServicesLoading(false);
    }
  };

  useEffect(() => {
    if (visible) {
      resetForm();
      if (importSource !== 'NACOS') {
        fetchGateways();
      }
      if (!importSource || importSource === 'NACOS') {
        fetchNacosInstances();
      }
      if (importSource !== 'GATEWAY' && supportsAiRegistry) {
        fetchAiRegistryInstances();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, productType, importSource]);

  const handleSourceTypeChange = (value: SourceType) => {
    setSourceType(value);
    form.resetFields(['gatewayId', 'nacosId', 'airegistryId', 'namespaceId']);
    setServices([]);
    setSelectedServiceKeys([]);
    setNamespaces([]);
    setSearchText('');
    setCurrentPage(1);
  };

  const handleAiRegistryChange = (airegistryId: string) => {
    const instance = aiRegistryInstances.find((item) => item.airegistryId === airegistryId);
    const namespaceId = instance?.namespaceId;
    form.setFieldValue('namespaceId', namespaceId);
    setServices([]);
    setSelectedServiceKeys([]);
    setSearchText('');
    setCurrentPage(1);
    if (namespaceId) {
      fetchServices({ airegistryId, namespaceId });
    }
  };

  const handleNacosChange = (nacosId: string) => {
    form.setFieldValue('namespaceId', undefined);
    setServices([]);
    setSelectedServiceKeys([]);
    setSearchText('');
    setCurrentPage(1);
    fetchNamespaces(nacosId);
  };

  const getResourceId = (service: ServiceItem) =>
    service.apiId ||
    service.mcpServerId ||
    service.mcpRouteId ||
    service.agentApiId ||
    service.modelApiId ||
    service.modelRouteName ||
    service.mcpServerName ||
    service.agentName ||
    service.skillName ||
    service.name;

  const handleImport = async () => {
    try {
      await form.validateFields();

      if (selectedServiceKeys.length === 0) {
        message.warning(t('product.import.selectAtLeastOneResource'));
        return;
      }

      const values = form.getFieldsValue();
      const selectedServices = services.filter((s) => selectedServiceKeys.includes(s.key));

      setLoading(true);
      const requestSource: ImportSource =
        sourceType === 'NACOS' ? 'NACOS' : sourceType === 'AIREGISTRY' ? 'AIREGISTRY' : 'GATEWAY';
      const res = await apiProductApi.importProducts({
        items: selectedServices.map((service) => ({
          description: service.description,
          resourceId: getResourceId(service),
          resourceName: service.name,
        })),
        productType,
        source: requestSource,
        sourceConfig:
          requestSource === 'NACOS'
            ? { instanceId: values.nacosId, namespace: values.namespaceId }
            : requestSource === 'AIREGISTRY'
              ? { instanceId: values.airegistryId, namespace: values.namespaceId }
              : { instanceId: values.gatewayId },
      });

      const result = (res as { data?: ImportResult }).data ?? (res as ImportResult);
      const failures = result.failures ?? [];

      if (failures.length > 0) {
        setImportResult({
          failures,
          selectedCount: selectedServices.length,
          successCount: result.successCount ?? 0,
        });

        if ((result.successCount ?? 0) > 0) {
          onSuccess();
        }
      } else if ((result.successCount ?? 0) > 0) {
        message.success(t('product.import.successCount', { count: result.successCount ?? 0 }));
        onSuccess();
        resetForm();
      } else {
        message.warning(t('product.import.emptySuccess'));
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(err.response?.data?.message || t('product.import.failed'));
    } finally {
      setLoading(false);
    }
  };

  const columns: TableColumnsType<ServiceItem> = [
    {
      dataIndex: 'name',
      key: 'name',
      title: t('product.import.resourceName'),
    },
  ];

  const handleCancel = () => {
    resetForm();
    onCancel();
  };

  return (
    <>
      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={loading}
        destroyOnClose
        okText={t('action.import')}
        onCancel={handleCancel}
        onOk={handleImport}
        open={visible}
        title={modalTitle}
        width={600}
      >
        <Form className="mt-4" form={form} layout="vertical">
          {showSourceTypeSelector && (
            <Form.Item
              initialValue={sourceType}
              label={
                importSource === 'GATEWAY'
                  ? t('product.import.gatewayType')
                  : t('product.import.dataSource')
              }
              name="sourceType"
              rules={[{ message: t('product.import.selectDataSource'), required: true }]}
            >
              <Select
                onChange={handleSourceTypeChange}
                placeholder={t('product.import.selectDataSource')}
              >
                {sourceTypeOptions.map((type) => (
                  <Select.Option key={type} value={type}>
                    {sourceTypeLabels[type]}
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          {sourceType === 'API_GATEWAY' && (
            <Form.Item
              label={t('product.import.selectGateway')}
              name="gatewayId"
              rules={[{ message: t('product.import.selectGateway'), required: true }]}
            >
              <Select
                onChange={(gatewayId) => fetchServices({ gatewayId })}
                optionFilterProp="children"
                placeholder={t('product.import.selectGateway')}
                showSearch
              >
                {apiGateways.map((gw) => (
                  <Select.Option key={gw.gatewayId} value={gw.gatewayId}>
                    {gw.gatewayName}
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          {sourceType === 'HIGRESS' && (
            <Form.Item
              label={t('product.import.selectHigressGateway')}
              name="gatewayId"
              rules={[{ message: t('product.import.selectHigressGateway'), required: true }]}
            >
              <Select
                onChange={(gatewayId) => fetchServices({ gatewayId })}
                optionFilterProp="children"
                placeholder={t('product.import.selectHigressGateway')}
                showSearch
              >
                {higressGateways.map((gw) => (
                  <Select.Option key={gw.gatewayId} value={gw.gatewayId}>
                    {gw.gatewayName}
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          {sourceType === 'AI_GATEWAY' && (
            <Form.Item
              label={t('product.import.selectAiGateway')}
              name="gatewayId"
              rules={[{ message: t('product.import.selectAiGateway'), required: true }]}
            >
              <Select
                onChange={(gatewayId) => fetchServices({ gatewayId })}
                optionFilterProp="children"
                placeholder={t('product.import.selectAiGateway')}
                showSearch
              >
                {aiGateways.map((gw) => (
                  <Select.Option key={gw.gatewayId} value={gw.gatewayId}>
                    {gw.gatewayName}
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          {sourceType === 'NACOS' && (
            <>
              <Form.Item
                label={t('product.import.selectNacos')}
                name="nacosId"
                rules={[{ message: t('product.import.selectNacos'), required: true }]}
              >
                <Select
                  notFoundContent={t('product.import.noNacos')}
                  onChange={handleNacosChange}
                  optionFilterProp="children"
                  placeholder={
                    nacosInstances.length === 0
                      ? t('product.import.noNacosCreateFirst')
                      : t('product.import.selectNacos')
                  }
                  showSearch
                >
                  {nacosInstances.map((nacos) => (
                    <Select.Option key={nacos.nacosId} value={nacos.nacosId}>
                      {nacos.nacosName}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>

              <Form.Item
                label={t('product.import.selectNamespace')}
                name="namespaceId"
                rules={[{ message: t('product.import.selectNamespace'), required: true }]}
              >
                <Select
                  onChange={(namespaceId) => fetchServices({ namespaceId })}
                  optionFilterProp="children"
                  placeholder={t('product.import.selectNamespace')}
                  showSearch
                >
                  {namespaces.map((ns: unknown) => {
                    const n = ns as { namespaceId: string; namespaceName?: string };
                    return (
                      <Select.Option key={n.namespaceId} value={n.namespaceId}>
                        {n.namespaceName || n.namespaceId}
                      </Select.Option>
                    );
                  })}
                </Select>
              </Form.Item>
            </>
          )}

          {sourceType === 'AIREGISTRY' && (
            <>
              <Form.Item
                label={t('product.import.selectAiRegistry')}
                name="airegistryId"
                rules={[{ message: t('product.import.selectAiRegistry'), required: true }]}
              >
                <Select
                  notFoundContent={t('product.import.noAiRegistry')}
                  onChange={handleAiRegistryChange}
                  optionFilterProp="children"
                  placeholder={
                    aiRegistryInstances.length === 0
                      ? t('product.import.noAiRegistryCreateFirst')
                      : t('product.import.selectAiRegistry')
                  }
                  showSearch
                >
                  {aiRegistryInstances.map((item) => (
                    <Select.Option key={item.airegistryId} value={item.airegistryId}>
                      {item.name}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>

              <Form.Item
                label={t('product.import.namespaceId')}
                name="namespaceId"
                rules={[{ message: t('product.import.namespaceIdRequired'), required: true }]}
              >
                <Input
                  onBlur={() => fetchServices()}
                  placeholder={t('product.import.namespaceIdPlaceholder')}
                />
              </Form.Item>
            </>
          )}
        </Form>

        <div className="mt-4">
          <div className="flex justify-between items-center mb-2">
            <Space>
              <Input
                allowClear
                onChange={(e) => setSearchText(e.target.value)}
                placeholder={t('product.import.searchResourceName')}
                prefix={<SearchOutlined />}
                style={{ width: 160 }}
                value={searchText}
              />
              <Button icon={<ReloadOutlined />} onClick={() => fetchServices()} size="small" />
            </Space>
            <div className="flex items-center gap-2">
              <Button onClick={handleSelectAll} size="small">
                {t('common.selectAll')}
              </Button>
              <Button onClick={handleDeselectAll} size="small">
                {t('common.clear')}
              </Button>
              <span
                className="text-sm text-gray-500"
                style={{ display: 'inline-block', minWidth: 72, textAlign: 'right' }}
              >
                {selectedServiceKeys.length} / {filteredServices.length}
              </span>
            </div>
          </div>
          <Table
            columns={columns}
            dataSource={filteredServices}
            loading={servicesLoading}
            locale={{
              emptyText: searchText
                ? t('product.import.noMatchedResource')
                : t('product.import.noImportableResource'),
            }}
            pagination={{
              current: currentPage,
              onChange: (page, newPageSize) => {
                if (newPageSize !== tablePageSize) {
                  setCurrentPage(1);
                  setTablePageSize(newPageSize);
                } else {
                  setCurrentPage(page);
                }
              },
              pageSize: tablePageSize,
              pageSizeOptions: ['3', '20', '50', '100'],
              showSizeChanger: true,
              showTotal: (total) => t('product.import.totalCount', { total }),
            }}
            rowSelection={{
              onChange: (keys) => setSelectedServiceKeys(keys as string[]),
              selectedRowKeys: selectedServiceKeys,
            }}
            scroll={{ y: 300 }}
          />
        </div>
      </Modal>

      <ImportResultModal
        failures={importResult?.failures ?? []}
        onClose={() => setImportResult(null)}
        open={!!importResult}
        selectedCount={importResult?.selectedCount ?? 0}
        successCount={importResult?.successCount ?? 0}
      />
    </>
  );
}
