import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Modal, Form, Select, Table, message, Space, Input, Button } from 'antd';
import { useState, useEffect } from 'react';

import { ImportResultModal, type ImportResultFailure } from '@/components/common/ImportResultModal';
import { apiProductApi, gatewayApi, nacosApi } from '@/lib/api';
import type { Gateway } from '@/types/gateway';

import type { TableColumnsType } from 'antd';

interface ImportProductsModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  productType: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API';
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

type ImportSource = 'GATEWAY' | 'NACOS';
type SourceType = 'API_GATEWAY' | 'HIGRESS' | 'AI_GATEWAY' | 'NACOS';

const PRODUCT_TYPE_LABELS: Record<string, string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

const SOURCE_TYPE_LABELS: Record<SourceType, string> = {
  AI_GATEWAY: 'AI网关',
  API_GATEWAY: '网关',
  HIGRESS: 'Higress网关',
  NACOS: 'Nacos',
};

export default function ImportProductsModal({
  importSource,
  onCancel,
  onSuccess,
  productType,
  visible,
}: ImportProductsModalProps) {
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
  const [namespaces, setNamespaces] = useState<unknown[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [selectedServiceKeys, setSelectedServiceKeys] = useState<string[]>([]);
  const [importResult, setImportResult] = useState<ImportResultState | null>(null);
  const [searchText, setSearchText] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [tablePageSize, setTablePageSize] = useState<number>(10);

  const pageSize = 500;

  const supportsApiGateway = productType === 'REST_API' && importSource !== 'NACOS';

  const supportsHigress =
    importSource !== 'NACOS' && (productType === 'MCP_SERVER' || productType === 'MODEL_API');

  const supportsAIGateway =
    importSource !== 'NACOS' &&
    (productType === 'MODEL_API' || productType === 'MCP_SERVER' || productType === 'AGENT_API');

  const supportsNacos =
    importSource !== 'GATEWAY' && (productType === 'MCP_SERVER' || productType === 'AGENT_API');

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
        : [...gatewaySourceTypes, ...(supportsNacos ? (['NACOS'] as const) : [])];

  const showSourceTypeSelector =
    !importSource || (importSource === 'GATEWAY' && gatewaySourceTypes.length > 1);

  const modalTitle =
    importSource === 'GATEWAY'
      ? `从网关导入 ${PRODUCT_TYPE_LABELS[productType]}`
      : importSource === 'NACOS'
        ? `从 Nacos 导入 ${PRODUCT_TYPE_LABELS[productType]}`
        : `导入 ${PRODUCT_TYPE_LABELS[productType]}`;

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
      message.error('获取网关列表失败');
    }
  };

  const fetchNacosInstances = async () => {
    try {
      const res = await nacosApi.getNacos({ page: 1, size: 100 });
      const instances = res.data?.content || [];
      setNacosInstances(instances);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(`获取 Nacos 列表失败: ${err.response?.data?.message || err.message}`);
      setNacosInstances([]);
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
      }
    } catch (_error) {
      message.error('获取命名空间列表失败');
      setNamespaces([]);
    }
  };

  const fetchServices = async () => {
    const values = form.getFieldsValue();

    if (isGatewaySource && !values.gatewayId) {
      message.warning('请先选择网关实例');
      return;
    }

    if (sourceType === 'NACOS' && (!values.nacosId || !values.namespaceId)) {
      message.warning('请先选择 Nacos 实例和命名空间');
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
            message.error('该产品类型不支持从 Gateway 导入');
            setServices([]);
        }
      } else {
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
        }
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(err.response?.data?.message || '获取资源列表失败');
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
      if (importSource !== 'GATEWAY') {
        fetchNacosInstances();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, productType, importSource]);

  const handleSourceTypeChange = (value: SourceType) => {
    setSourceType(value);
    form.resetFields(['gatewayId', 'nacosId', 'namespaceId']);
    setServices([]);
    setSelectedServiceKeys([]);
    setNamespaces([]);
    setSearchText('');
    setCurrentPage(1);
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
    service.name;

  const handleImport = async () => {
    try {
      await form.validateFields();

      if (selectedServiceKeys.length === 0) {
        message.warning('请至少选择一个资源');
        return;
      }

      const values = form.getFieldsValue();
      const selectedServices = services.filter((s) => selectedServiceKeys.includes(s.key));

      setLoading(true);
      const requestSource: ImportSource = sourceType === 'NACOS' ? 'NACOS' : 'GATEWAY';
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
        message.success(`成功导入 ${result.successCount} 个产品`);
        onSuccess();
        resetForm();
      } else {
        message.warning('没有成功导入任何产品');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(err.response?.data?.message || '导入失败');
    } finally {
      setLoading(false);
    }
  };

  const columns: TableColumnsType<ServiceItem> = [
    {
      dataIndex: 'name',
      key: 'name',
      title: '资源名称',
    },
  ];

  const handleCancel = () => {
    resetForm();
    onCancel();
  };

  return (
    <>
      <Modal
        cancelText="取消"
        confirmLoading={loading}
        destroyOnClose
        okText="导入"
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
              label={importSource === 'GATEWAY' ? '网关类型' : '数据源'}
              name="sourceType"
              rules={[{ message: '请选择数据源', required: true }]}
            >
              <Select onChange={handleSourceTypeChange} placeholder="请选择数据源">
                {sourceTypeOptions.map((type) => (
                  <Select.Option key={type} value={type}>
                    {SOURCE_TYPE_LABELS[type]}
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          {sourceType === 'API_GATEWAY' && (
            <Form.Item
              label="选择网关"
              name="gatewayId"
              rules={[{ message: '请选择网关', required: true }]}
            >
              <Select
                onChange={fetchServices}
                optionFilterProp="children"
                placeholder="请选择网关"
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
              label="选择Higress网关"
              name="gatewayId"
              rules={[{ message: '请选择Higress网关', required: true }]}
            >
              <Select
                onChange={fetchServices}
                optionFilterProp="children"
                placeholder="请选择Higress网关"
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
              label="选择AI网关"
              name="gatewayId"
              rules={[{ message: '请选择AI网关', required: true }]}
            >
              <Select
                onChange={fetchServices}
                optionFilterProp="children"
                placeholder="请选择AI网关"
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
                label="选择Nacos"
                name="nacosId"
                rules={[{ message: '请选择Nacos', required: true }]}
              >
                <Select
                  notFoundContent="暂无Nacos"
                  onChange={handleNacosChange}
                  optionFilterProp="children"
                  placeholder={
                    nacosInstances.length === 0 ? '暂无Nacos，请先在系统中添加' : '请选择Nacos'
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
                label="选择命名空间"
                name="namespaceId"
                rules={[{ message: '请选择命名空间', required: true }]}
              >
                <Select
                  onChange={fetchServices}
                  optionFilterProp="children"
                  placeholder="请选择命名空间"
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
        </Form>

        <div className="mt-4">
          <div className="flex justify-between items-center mb-2">
            <Space>
              <Input
                allowClear
                onChange={(e) => setSearchText(e.target.value)}
                placeholder="搜索资源名称"
                prefix={<SearchOutlined />}
                style={{ width: 160 }}
                value={searchText}
              />
              <Button icon={<ReloadOutlined />} onClick={fetchServices} size="small" />
            </Space>
            <div className="flex items-center gap-2">
              <Button onClick={handleSelectAll} size="small">
                全选
              </Button>
              <Button onClick={handleDeselectAll} size="small">
                清空
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
              emptyText: searchText ? '没有匹配的资源' : '暂无可导入的资源，请先选择数据源',
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
              showTotal: (total) => `共 ${total} 个`,
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
