import { SearchOutlined } from '@ant-design/icons';
import { Modal, Form, Select, Table, message, Space, Input, Button } from 'antd';
import { useState, useEffect } from 'react';

import { apiProductApi, gatewayApi, nacosApi } from '@/lib/api';
import type { Gateway } from '@/types/gateway';

import type { TableColumnsType } from 'antd';

interface ImportProductsModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  productType: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API';
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
  // Nacos fields
  mcpServerName?: string;
  agentName?: string;
  namespaceId?: string;
}

type SourceType = 'HIGRESS' | 'AI_GATEWAY' | 'NACOS';

const PRODUCT_TYPE_LABELS: Record<string, string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

const SOURCE_TYPE_LABELS: Record<SourceType, string> = {
  AI_GATEWAY: 'AI 网关实例',
  HIGRESS: 'Higress 网关',
  NACOS: 'Nacos 实例',
};

export default function ImportProductsModal({
  onCancel,
  onSuccess,
  productType,
  visible,
}: ImportProductsModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [servicesLoading, setServicesLoading] = useState(false);
  const [sourceType, setSourceType] = useState<SourceType>('HIGRESS');
  const [higressGateways, setHigressGateways] = useState<Gateway[]>([]);
  const [aiGateways, setAiGateways] = useState<Gateway[]>([]);
  const [nacosInstances, setNacosInstances] = useState<
    Array<{ nacosId: string; nacosName: string }>
  >([]);
  const [namespaces, setNamespaces] = useState<unknown[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [selectedServiceKeys, setSelectedServiceKeys] = useState<string[]>([]);
  const [searchText, setSearchText] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [tablePageSize, setTablePageSize] = useState<number>(10);

  const pageSize = 500; // 每次从后端加载的服务数量上限

  // 判断当前产品类型支持的数据源
  // Higress 网关仅支持 MCP_SERVER 批量导入
  // REST_API, MODEL_API: 仅支持 AI 网关
  // MCP_SERVER, AGENT_API: 支持 AI 网关和 Nacos
  const supportsHigress = productType === 'MCP_SERVER';

  const supportsAIGateway =
    productType === 'REST_API' ||
    productType === 'MODEL_API' ||
    productType === 'MCP_SERVER' ||
    productType === 'AGENT_API';

  // Higress 不支持时的提示文案
  const higressDisabledReason = !supportsHigress
    ? `Higress 网关暂不支持 ${PRODUCT_TYPE_LABELS[productType]} 批量导入`
    : '';

  // Nacos 不支持时的提示文案
  const nacosDisabledReason =
    productType === 'REST_API' || productType === 'MODEL_API'
      ? `Nacos 不支持 ${PRODUCT_TYPE_LABELS[productType]} 导入`
      : '';

  // 过滤服务列表
  const filteredServices = services.filter((service) => {
    if (!searchText) return true;
    const lowerSearch = searchText.toLowerCase();
    return (
      service.name.toLowerCase().includes(lowerSearch) ||
      (service.description && service.description.toLowerCase().includes(lowerSearch))
    );
  });

  // 搜索文本变化时重置分页
  useEffect(() => {
    setCurrentPage(1);
  }, [searchText]);

  // 全选/反选当前过滤结果
  const handleSelectAll = () => {
    const allKeys = filteredServices.map((s) => s.key);
    setSelectedServiceKeys(allKeys);
  };

  const handleDeselectAll = () => {
    setSelectedServiceKeys([]);
  };

  // 当前数据源类型是否为网关
  const isGatewaySource = sourceType === 'HIGRESS' || sourceType === 'AI_GATEWAY';

  // 重置表单和状态
  const resetForm = () => {
    form.resetFields();
    // 根据产品类型设置默认数据源
    const defaultSourceType = supportsHigress
      ? 'HIGRESS'
      : supportsAIGateway
        ? 'AI_GATEWAY'
        : 'NACOS';
    setSourceType(defaultSourceType);
    setServices([]);
    setSelectedServiceKeys([]);
    setNamespaces([]);
    setSearchText(''); // 重置搜索文本
    setCurrentPage(1); // 重置分页
  };

  // 加载网关列表并分类
  const fetchGateways = async () => {
    try {
      const res = await gatewayApi.getGateways({ page: 0, size: 100 });
      const allGateways = res.data?.content || [];

      // 分类网关
      const higress = allGateways.filter((gw: Gateway) => gw.gatewayType === 'HIGRESS');
      const aiGws = allGateways.filter(
        (gw: Gateway) =>
          gw.gatewayType === 'APIG_AI' ||
          gw.gatewayType === 'ADP_AI_GATEWAY' ||
          gw.gatewayType === 'APSARA_GATEWAY',
      );

      setHigressGateways(higress);
      setAiGateways(aiGws);
    } catch (_error) {
      message.error('获取网关列表失败');
    }
  };

  // 加载 Nacos 列表
  const fetchNacosInstances = async () => {
    try {
      const res = await nacosApi.getNacos({ page: 0, size: 100 });
      const instances = res.data?.content || [];
      setNacosInstances(instances);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      message.error(`获取 Nacos 列表失败: ${err.response?.data?.message || err.message}`);
      setNacosInstances([]);
    }
  };

  // 加载 Nacos 命名空间列表
  const fetchNamespaces = async (nacosId: string) => {
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 0, size: 100 });
      const nsContent = (res.data?.content || []) as unknown[];
      setNamespaces(nsContent);
      // 默认选择 public 命名空间
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

  // 加载服务列表
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
    setCurrentPage(1); // 加载新服务列表时重置分页
    try {
      let res: unknown;

      if (isGatewaySource) {
        // 根据产品类型调用不同的 API
        switch (productType) {
          case 'REST_API':
            res = await gatewayApi.getGatewayRestApis(values.gatewayId, {
              page: 0,
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
              page: 0,
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
              page: 0,
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
              page: 0,
              size: pageSize,
            });
            {
              const responseData = res as { data?: { content?: unknown[] } };
              const items = (responseData.data?.content || []).map((item: unknown) => {
                const it = item as Record<string, string>;
                return {
                  description: it.description,
                  key: it.modelApiId || '',
                  modelApiId: it.modelApiId,
                  name: it.modelApiName || it.name || '',
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
        // Nacos 数据源
        if (productType === 'MCP_SERVER') {
          res = await nacosApi.getNacosMcpServers(values.nacosId, {
            namespaceId: values.namespaceId,
            page: 0,
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
            page: 0,
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
      message.error(err.response?.data?.message || '获取服务列表失败');
      setServices([]);
    } finally {
      setServicesLoading(false);
    }
  };

  // 初始化数据
  useEffect(() => {
    if (visible) {
      resetForm();
      fetchGateways();
      fetchNacosInstances();
      // 根据产品类型设置默认数据源类型
      const defaultSourceType = supportsHigress
        ? 'HIGRESS'
        : supportsAIGateway
          ? 'AI_GATEWAY'
          : 'NACOS';
      setSourceType(defaultSourceType);
      form.setFieldValue('sourceType', defaultSourceType);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, productType]);

  // 处理数据源类型变化
  const handleSourceTypeChange = (value: SourceType) => {
    setSourceType(value);
    form.resetFields(['gatewayId', 'nacosId', 'namespaceId']);
    setServices([]);
    setSelectedServiceKeys([]);
    setNamespaces([]);
    setSearchText('');
    setCurrentPage(1);
  };

  // 处理 Nacos 实例变化
  const handleNacosChange = (nacosId: string) => {
    form.setFieldValue('namespaceId', undefined);
    setServices([]);
    setSelectedServiceKeys([]);
    setSearchText('');
    setCurrentPage(1);
    fetchNamespaces(nacosId);
  };

  // 处理导入
  const handleImport = async () => {
    try {
      await form.validateFields();

      if (selectedServiceKeys.length === 0) {
        message.warning('请至少选择一个服务');
        return;
      }

      const values = form.getFieldsValue();
      const selectedServices = services.filter((s) => selectedServiceKeys.includes(s.key));

      setLoading(true);
      const res = await apiProductApi.importProducts({
        gatewayId: isGatewaySource ? values.gatewayId : undefined,
        nacosId: sourceType === 'NACOS' ? values.nacosId : undefined,
        namespaceId: sourceType === 'NACOS' ? values.namespaceId : undefined,
        productType,
        services: selectedServices.map((s) => ({
          agentApiId: s.agentApiId,
          agentName: s.agentName,
          apiId: s.apiId,
          description: s.description,
          mcpRouteId: s.mcpRouteId,
          mcpServerId: s.mcpServerId,
          mcpServerName: s.mcpServerName,
          modelApiId: s.modelApiId,
          name: s.name,
          namespaceId: s.namespaceId,
        })),
        sourceType: sourceType === 'NACOS' ? 'NACOS' : 'GATEWAY',
      });

      const result = res.data;

      // 如果有失败的，显示详细信息
      if (result.failureCount > 0) {
        const failedResults = (result.results as unknown[]).filter(
          (r: unknown) => !(r as { success?: boolean }).success,
        );

        Modal.error({
          content: (
            <div className="mt-4">
              <div className="mb-2 text-gray-600">
                共选择 {result.totalCount} 个服务，成功导入 {result.successCount} 个
              </div>
              <div className="font-semibold mb-2">失败详情：</div>
              <div className="max-h-96 overflow-y-auto">
                {failedResults.map((item: unknown, index: number) => {
                  const it = item as {
                    serviceName?: string;
                    errorCode?: string;
                    errorMessage?: string;
                  };
                  return (
                    <div className="mb-3 p-2 bg-red-50 rounded border border-red-200" key={index}>
                      <div className="font-medium text-red-700">{it.serviceName}</div>
                      <div className="text-sm text-red-600 mt-1">
                        {it.errorCode && <span className="font-mono">[{it.errorCode}] </span>}
                        {it.errorMessage || '未知错误'}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ),
          okText: '知道了',
          title: `导入完成：成功 ${result.successCount} 个，失败 ${result.failureCount} 个`,
          width: 600,
        });

        // 如果有部分成功，刷新列表
        if (result.successCount > 0) {
          onSuccess();
        }
      } else if (result.successCount > 0) {
        // 全部成功，关闭模态窗并刷新
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
      title: '服务名称',
    },
    {
      dataIndex: 'description',
      key: 'description',
      render: (text) => text || '-',
      title: '描述',
    },
  ];

  const handleCancel = () => {
    resetForm();
    onCancel();
  };

  return (
    <Modal
      cancelText="取消"
      confirmLoading={loading}
      destroyOnClose
      okText="导入"
      onCancel={handleCancel}
      onOk={handleImport}
      open={visible}
      title={`导入 ${PRODUCT_TYPE_LABELS[productType]}`}
      width={800}
    >
      <Form className="mt-4" form={form} layout="vertical">
        <Form.Item
          initialValue={sourceType}
          label="数据源类型"
          name="sourceType"
          rules={[{ message: '请选择数据源类型', required: true }]}
        >
          <Select onChange={handleSourceTypeChange} placeholder="请选择数据源类型">
            <Select.Option disabled={!supportsHigress} value="HIGRESS">
              {SOURCE_TYPE_LABELS.HIGRESS}
              {higressDisabledReason && (
                <span className="text-gray-400 text-xs ml-1">（{higressDisabledReason}）</span>
              )}
            </Select.Option>
            {supportsAIGateway && (
              <Select.Option value="AI_GATEWAY">{SOURCE_TYPE_LABELS.AI_GATEWAY}</Select.Option>
            )}
            <Select.Option disabled={!!nacosDisabledReason} value="NACOS">
              {SOURCE_TYPE_LABELS.NACOS}
              {nacosDisabledReason && (
                <span className="text-gray-400 text-xs ml-1">（{nacosDisabledReason}）</span>
              )}
            </Select.Option>
          </Select>
        </Form.Item>

        {sourceType === 'HIGRESS' && (
          <Form.Item
            label="选择 Higress 实例"
            name="gatewayId"
            rules={[{ message: '请选择 Higress 实例', required: true }]}
          >
            <Select
              onChange={fetchServices}
              optionFilterProp="children"
              placeholder="请选择 Higress 实例"
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
            label="选择 AI 网关实例"
            name="gatewayId"
            rules={[{ message: '请选择 AI 网关实例', required: true }]}
          >
            <Select
              onChange={fetchServices}
              optionFilterProp="children"
              placeholder="请选择 AI 网关实例"
              showSearch
            >
              {aiGateways.map((gw) => (
                <Select.Option key={gw.gatewayId} value={gw.gatewayId}>
                  {gw.gatewayName} ({gw.gatewayType})
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}

        {sourceType === 'NACOS' && (
          <>
            <Form.Item
              label="选择 Nacos 实例"
              name="nacosId"
              rules={[{ message: '请选择 Nacos 实例', required: true }]}
            >
              <Select
                notFoundContent="暂无 Nacos 实例"
                onChange={handleNacosChange}
                optionFilterProp="children"
                placeholder={
                  nacosInstances.length === 0
                    ? '暂无 Nacos 实例，请先在系统中添加'
                    : '请选择 Nacos 实例'
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
          <span className="font-medium">可用服务列表</span>
          <Space>
            <Input
              allowClear
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="搜索服务名称或描述"
              prefix={<SearchOutlined />}
              style={{ width: 200 }}
              value={searchText}
            />
            <Button onClick={handleSelectAll} size="small">
              全选
            </Button>
            <Button onClick={handleDeselectAll} size="small">
              清空
            </Button>
            <span className="text-sm text-gray-500">
              已选择 {selectedServiceKeys.length} / {filteredServices.length}
              {searchText &&
                services.length !== filteredServices.length &&
                ` (共 ${services.length} 个)`}
            </span>
          </Space>
        </div>
        <Table
          columns={columns}
          dataSource={filteredServices}
          loading={servicesLoading}
          locale={{
            emptyText: searchText ? '没有匹配的服务' : '暂无可导入的服务，请先选择数据源',
          }}
          pagination={{
            current: currentPage,
            onChange: (page, newPageSize) => {
              // 如果 pageSize 改变了，重置到第一页
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
            showTotal: (total) => `共 ${total} 个服务`,
          }}
          rowSelection={{
            onChange: (keys) => setSelectedServiceKeys(keys as string[]),
            selectedRowKeys: selectedServiceKeys,
          }}
          scroll={{ y: 300 }}
        />
      </div>
    </Modal>
  );
}
