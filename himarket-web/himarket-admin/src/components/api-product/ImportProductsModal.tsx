import { useState, useEffect } from 'react';
import { Modal, Form, Select, Table, message, Space, Input, Button } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { apiProductApi, gatewayApi, nacosApi } from '@/lib/api';
import type { TableColumnsType } from 'antd';
import type { Gateway } from '@/types/gateway';

interface ImportProductsModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  productType: 'REST_API' | 'MCP_SERVER' | 'AGENT_SKILL' | 'WORKER' | 'AGENT_API' | 'MODEL_API';
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
  REST_API: 'REST API',
  MCP_SERVER: 'MCP Server',
  AGENT_API: 'Agent API',
  MODEL_API: 'Model API',
  AGENT_SKILL: 'Agent Skill',
  WORKER: 'Worker',
};

const SOURCE_TYPE_LABELS: Record<SourceType, string> = {
  HIGRESS: 'Higress 网关',
  AI_GATEWAY: 'AI 网关实例',
  NACOS: 'Nacos 实例',
};

export default function ImportProductsModal({
  visible,
  onCancel,
  onSuccess,
  productType,
}: ImportProductsModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [servicesLoading, setServicesLoading] = useState(false);
  const [sourceType, setSourceType] = useState<SourceType>('HIGRESS');
  const [higressGateways, setHigressGateways] = useState<Gateway[]>([]);
  const [aiGateways, setAiGateways] = useState<Gateway[]>([]);
  const [nacosInstances, setNacosInstances] = useState<any[]>([]);
  const [namespaces, setNamespaces] = useState<any[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [selectedServiceKeys, setSelectedServiceKeys] = useState<string[]>([]);
  const [searchText, setSearchText] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [tablePageSize, setTablePageSize] = useState<number>(10);

  const pageSize = 500; // 每次从后端加载的服务数量上限

  // 判断当前产品类型支持的数据源
  // REST_API, MODEL_API: 仅支持 Gateway
  // MCP_SERVER, AGENT_API: 支持 Gateway 和 Nacos
  // AGENT_SKILL, WORKER: 不支持此界面导入
  const supportsNacos =
    productType === 'MCP_SERVER' ||
    productType === 'AGENT_API';

  const supportsGateway =
    productType === 'REST_API' ||
    productType === 'MODEL_API' ||
    productType === 'MCP_SERVER' ||
    productType === 'AGENT_API';

  // 过滤服务列表
  const filteredServices = services.filter(service => {
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
    const allKeys = filteredServices.map(s => s.key);
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
    const defaultSourceType = supportsGateway ? 'HIGRESS' : 'NACOS';
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
      const aiGws = allGateways.filter((gw: Gateway) =>
        gw.gatewayType === 'APIG_AI' ||
        gw.gatewayType === 'ADP_AI_GATEWAY' ||
        gw.gatewayType === 'APSARA_GATEWAY'
      );

      setHigressGateways(higress);
      setAiGateways(aiGws);
    } catch (error) {
      message.error('获取网关列表失败');
    }
  };

  // 加载 Nacos 列表
  const fetchNacosInstances = async () => {
    try {
      const res = await nacosApi.getNacos({ page: 0, size: 100 });
      const instances = res.data?.content || [];
      setNacosInstances(instances);
    } catch (error: any) {
      message.error(`获取 Nacos 列表失败: ${error.response?.data?.message || error.message}`);
      setNacosInstances([]);
    }
  };

  // 加载 Nacos 命名空间列表
  const fetchNamespaces = async (nacosId: string) => {
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 0, size: 100 });
      setNamespaces(res.data?.content || []);
      // 默认选择 public 命名空间
      const publicNs = res.data?.content?.find((ns: any) => ns.namespaceId === 'public');
      if (publicNs) {
        form.setFieldValue('namespaceId', 'public');
      }
    } catch (error) {
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
      let res: any;
      let loadedCount = 0;

      if (isGatewaySource) {
        // 根据产品类型调用不同的 API
        switch (productType) {
          case 'REST_API':
            res = await gatewayApi.getGatewayRestApis(values.gatewayId, { page: 0, size: pageSize });
            {
              const items = (res.data?.content || []).map((item: any) => ({
                key: item.apiId,
                name: item.apiName,
                description: item.description,
                apiId: item.apiId,
              }));
              setServices(items);
              loadedCount = items.length;
            }
            break;
          case 'MCP_SERVER':
            res = await gatewayApi.getGatewayMcpServers(values.gatewayId, { page: 0, size: pageSize });
            {
              const items = (res.data?.content || []).map((item: any) => ({
                key: item.mcpServerId || item.mcpServerName,
                name: item.mcpServerName,
                description: item.description,
                mcpServerId: item.mcpServerId,
                mcpRouteId: item.mcpRouteId,
                mcpServerName: item.mcpServerName,
              }));
              setServices(items);
              loadedCount = items.length;
            }
            break;
          case 'AGENT_API':
            res = await gatewayApi.getGatewayAgentApis(values.gatewayId, { page: 0, size: pageSize });
            {
              const items = (res.data?.content || []).map((item: any) => ({
                key: item.agentApiId,
                name: item.agentApiName,
                description: item.description,
                agentApiId: item.agentApiId,
              }));
              setServices(items);
              loadedCount = items.length;
            }
            break;
          case 'MODEL_API':
            res = await gatewayApi.getGatewayModelApis(values.gatewayId, { page: 0, size: pageSize });
            {
              const items = (res.data?.content || []).map((item: any) => ({
                key: item.modelApiId,
                name: item.modelApiName || item.name,
                description: item.description,
                modelApiId: item.modelApiId,
              }));
              setServices(items);
              loadedCount = items.length;
            }
            break;
          default:
            message.error('该产品类型不支持从 Gateway 导入');
            setServices([]);
        }
      } else {
        // Nacos 数据源
        if (productType === 'REST_API') {
          message.warning('REST API 仅支持从网关实例导入，不支持从 Nacos 导入');
          setServices([]);
        } else if (productType === 'MODEL_API') {
          message.warning('Model API 仅支持从网关实例导入，不支持从 Nacos 导入');
          setServices([]);
        } else if (productType === 'AGENT_SKILL' || productType === 'WORKER') {
          message.warning('Agent Skill 和 Worker 暂不支持通过此界面从 Nacos 导入');
          setServices([]);
        } else if (productType === 'MCP_SERVER') {
          res = await nacosApi.getNacosMcpServers(values.nacosId, {
            namespaceId: values.namespaceId,
            page: 0,
            size: pageSize,
          });
          {
            const items = (res.data?.content || []).map((item: any) => ({
              key: item.mcpServerName,
              name: item.mcpServerName,
              description: item.description,
              mcpServerName: item.mcpServerName,
              namespaceId: values.namespaceId,
            }));
            setServices(items);
            loadedCount = items.length;
          }
        } else if (productType === 'AGENT_API') {
          res = await nacosApi.getNacosAgents(values.nacosId, {
            namespaceId: values.namespaceId,
            page: 0,
            size: pageSize,
          });
          {
            const items = (res.data?.content || []).map((item: any) => ({
              key: item.agentName,
              name: item.agentName,
              description: item.description,
              agentName: item.agentName,
              namespaceId: values.namespaceId,
            }));
            setServices(items);
            loadedCount = items.length;
          }
        }
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '获取服务列表失败');
      setServices([]);
    } finally {
      setServicesLoading(false);
      // 如果达到上限，提示用户
      if (loadedCount >= pageSize) {
        message.info(`已加载 ${pageSize} 个服务，如有更多服务请使用搜索或分页功能`);
      }
    }
  };

  // 初始化数据
  useEffect(() => {
    if (visible) {
      resetForm();
      fetchGateways();
      fetchNacosInstances();
      // 根据产品类型设置默认数据源类型
      const defaultSourceType = supportsGateway ? 'HIGRESS' : 'NACOS';
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
      const selectedServices = services.filter(s => selectedServiceKeys.includes(s.key));

      setLoading(true);
      const res = await apiProductApi.importProducts({
        productType,
        sourceType: sourceType === 'NACOS' ? 'NACOS' : 'GATEWAY',
        gatewayId: isGatewaySource ? values.gatewayId : undefined,
        nacosId: sourceType === 'NACOS' ? values.nacosId : undefined,
        namespaceId: sourceType === 'NACOS' ? values.namespaceId : undefined,
        services: selectedServices.map(s => ({
          name: s.name,
          description: s.description,
          apiId: s.apiId,
          mcpServerId: s.mcpServerId,
          mcpRouteId: s.mcpRouteId,
          agentApiId: s.agentApiId,
          modelApiId: s.modelApiId,
          mcpServerName: s.mcpServerName,
          agentName: s.agentName,
          namespaceId: s.namespaceId,
        })),
      });

      const result = res.data;

      // 如果有失败的，显示详细信息
      if (result.failureCount > 0) {
        const failedResults = result.results.filter((r: any) => !r.success);

        Modal.error({
          title: `导入完成：成功 ${result.successCount} 个，失败 ${result.failureCount} 个`,
          width: 600,
          content: (
            <div className="mt-4">
              <div className="mb-2 text-gray-600">
                共选择 {result.totalCount} 个服务，成功导入 {result.successCount} 个
              </div>
              <div className="font-semibold mb-2">失败详情：</div>
              <div className="max-h-96 overflow-y-auto">
                {failedResults.map((item: any, index: number) => (
                  <div key={index} className="mb-3 p-2 bg-red-50 rounded border border-red-200">
                    <div className="font-medium text-red-700">{item.serviceName}</div>
                    <div className="text-sm text-red-600 mt-1">
                      {item.errorCode && <span className="font-mono">[{item.errorCode}] </span>}
                      {item.errorMessage || '未知错误'}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ),
          okText: '知道了',
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
    } catch (error: any) {
      message.error(error.response?.data?.message || '导入失败');
    } finally {
      setLoading(false);
    }
  };

  const columns: TableColumnsType<ServiceItem> = [
    {
      title: '服务名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      render: (text) => text || '-',
    },
  ];

  const handleCancel = () => {
    resetForm();
    onCancel();
  };

  return (
    <Modal
      title={`导入 ${PRODUCT_TYPE_LABELS[productType]}`}
      open={visible}
      onCancel={handleCancel}
      onOk={handleImport}
      confirmLoading={loading}
      width={800}
      okText="导入"
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical" className="mt-4">
        <Form.Item
          label="数据源类型"
          name="sourceType"
          initialValue={sourceType}
          rules={[{ required: true, message: '请选择数据源类型' }]}
        >
          <Select
            placeholder="请选择数据源类型"
            onChange={handleSourceTypeChange}
          >
            {supportsGateway && (
              <>
                <Select.Option value="HIGRESS">{SOURCE_TYPE_LABELS.HIGRESS}</Select.Option>
                <Select.Option value="AI_GATEWAY">{SOURCE_TYPE_LABELS.AI_GATEWAY}</Select.Option>
              </>
            )}
            {supportsNacos && (
              <Select.Option value="NACOS">{SOURCE_TYPE_LABELS.NACOS}</Select.Option>
            )}
          </Select>
        </Form.Item>

        {sourceType === 'HIGRESS' && (
          <Form.Item
            label="选择 Higress 实例"
            name="gatewayId"
            rules={[{ required: true, message: '请选择 Higress 实例' }]}
          >
            <Select
              placeholder="请选择 Higress 实例"
              onChange={fetchServices}
              showSearch
              optionFilterProp="children"
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
            rules={[{ required: true, message: '请选择 AI 网关实例' }]}
          >
            <Select
              placeholder="请选择 AI 网关实例"
              onChange={fetchServices}
              showSearch
              optionFilterProp="children"
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
              rules={[{ required: true, message: '请选择 Nacos 实例' }]}
            >
              <Select
                placeholder={nacosInstances.length === 0 ? "暂无 Nacos 实例，请先在系统中添加" : "请选择 Nacos 实例"}
                onChange={handleNacosChange}
                showSearch
                optionFilterProp="children"
                notFoundContent="暂无 Nacos 实例"
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
              rules={[{ required: true, message: '请选择命名空间' }]}
            >
              <Select
                placeholder="请选择命名空间"
                onChange={fetchServices}
                showSearch
                optionFilterProp="children"
              >
                {namespaces.map((ns: any) => (
                  <Select.Option key={ns.namespaceId} value={ns.namespaceId}>
                    {ns.namespaceName || ns.namespaceId}
                  </Select.Option>
                ))}
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
              placeholder="搜索服务名称或描述"
              prefix={<SearchOutlined />}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
            <Button size="small" onClick={handleSelectAll}>
              全选
            </Button>
            <Button size="small" onClick={handleDeselectAll}>
              清空
            </Button>
            <span className="text-sm text-gray-500">
              已选择 {selectedServiceKeys.length} / {filteredServices.length}
              {searchText && services.length !== filteredServices.length && ` (共 ${services.length} 个)`}
            </span>
          </Space>
        </div>
        <Table
          rowSelection={{
            selectedRowKeys: selectedServiceKeys,
            onChange: (keys) => setSelectedServiceKeys(keys as string[]),
          }}
          columns={columns}
          dataSource={filteredServices}
          loading={servicesLoading}
          pagination={{
            current: currentPage,
            pageSize: tablePageSize,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个服务`,
            pageSizeOptions: ['3', '20', '50', '100'],
            onChange: (page, newPageSize) => {
              // 如果 pageSize 改变了，重置到第一页
              if (newPageSize !== tablePageSize) {
                setCurrentPage(1);
                setTablePageSize(newPageSize);
              } else {
                setCurrentPage(page);
              }
            },
          }}
          scroll={{ y: 300 }}
          locale={{
            emptyText: searchText ? '没有匹配的服务' : '暂无可导入的服务，请先选择数据源',
          }}
        />
      </div>
    </Modal>
  );
}
