import { PlusOutlined } from '@ant-design/icons';
import { Button, Table, message, Modal, Tabs } from 'antd';
import { useState, useEffect, useCallback } from 'react';

import EditGatewayModal from '@/components/console/EditGatewayModal';
import GatewayTypeSelector from '@/components/console/GatewayTypeSelector';
import ImportGatewayModal from '@/components/console/ImportGatewayModal';
import ImportHigressModal from '@/components/console/ImportHigressModal';
import { gatewayApi } from '@/lib/api';
import { formatDateTime } from '@/lib/utils';
import type { Gateway, GatewayType } from '@/types';

import type { ColumnsType } from 'antd/es/table';

export default function Consoles() {
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [typeSelectorVisible, setTypeSelectorVisible] = useState(false);
  const [importVisible, setImportVisible] = useState(false);
  const [higressImportVisible, setHigressImportVisible] = useState(false);
  const [selectedGatewayType, setSelectedGatewayType] = useState<GatewayType>('APIG_API');
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<GatewayType>('HIGRESS');
  const [editVisible, setEditVisible] = useState(false);
  const [editingGateway, setEditingGateway] = useState<Gateway | null>(null);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchGatewaysByType = useCallback(async (gatewayType: GatewayType, page = 1, size = 10) => {
    setLoading(true);
    try {
      const res = await gatewayApi.getGateways({ gatewayType, page, size });
      setGateways(res.data?.content || []);
      setPagination({
        current: page,
        pageSize: size,
        total: res.data?.totalElements || 0,
      });
    } catch (_error) {
      // message.error('获取网关列表失败')
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchGatewaysByType(activeTab, 1, 10);
  }, [fetchGatewaysByType, activeTab]);

  // 处理导入成功
  const handleImportSuccess = () => {
    fetchGatewaysByType(activeTab, pagination.current, pagination.pageSize);
  };

  // 处理网关类型选择
  const handleGatewayTypeSelect = (type: GatewayType) => {
    setSelectedGatewayType(type);
    setTypeSelectorVisible(false);
    if (type === 'HIGRESS') {
      setHigressImportVisible(true);
    } else {
      setImportVisible(true);
    }
  };

  // 处理分页变化
  const handlePaginationChange = (page: number, pageSize: number) => {
    fetchGatewaysByType(activeTab, page, pageSize);
  };

  // 处理Tab切换
  const handleTabChange = (tabKey: string) => {
    const gatewayType = tabKey as GatewayType;
    setActiveTab(gatewayType);
    // Tab切换时重置到第一页
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleEditGateway = (gateway: Gateway) => {
    setEditingGateway(gateway);
    setEditVisible(true);
  };

  const handleDeleteGateway = async (gatewayId: string) => {
    Modal.confirm({
      content: '确定要删除该网关吗？',
      onOk: async () => {
        try {
          await gatewayApi.deleteGateway(gatewayId);
          message.success('删除成功');
          fetchGatewaysByType(activeTab, pagination.current, pagination.pageSize);
        } catch (_error) {
          // message.error('删除失败')
        }
      },
      title: '确认删除',
    });
  };

  // APIG 网关的列定义
  const apigColumns: ColumnsType<Gateway> = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <div className="text-xs text-gray-500 truncate">{record.gatewayId}</div>
        </div>
      ),
      title: '网关名称/ID',
      width: 280,
    },
    {
      dataIndex: 'region',
      key: 'region',
      render: (_text: unknown, record: Gateway) => {
        return record.apigConfig?.region || '-';
      },
      title: '区域',
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
    },
    {
      key: 'action',
      render: (_text: unknown, record: Gateway) => (
        <>
          <Button onClick={() => handleEditGateway(record)} type="link">
            编辑
          </Button>
          <Button danger onClick={() => handleDeleteGateway(record.gatewayId)} type="link">
            删除
          </Button>
        </>
      ),
      title: '操作',
    },
  ];

  // 专有云 AI 网关的列定义
  const adpAiColumns: ColumnsType<Gateway> = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <div className="text-xs text-gray-500 truncate">{record.gatewayId}</div>
        </div>
      ),
      title: '网关名称/ID',
      width: 280,
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
    },
    {
      key: 'action',
      render: (_text: unknown, record: Gateway) => (
        <>
          <Button onClick={() => handleEditGateway(record)} type="link">
            编辑
          </Button>
          <Button danger onClick={() => handleDeleteGateway(record.gatewayId)} type="link">
            删除
          </Button>
        </>
      ),
      title: '操作',
    },
  ];

  // 飞天企业版 AI 网关的列定义
  const apsaraGatewayColumns: ColumnsType<Gateway> = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <div className="text-xs text-gray-500 truncate">{record.gatewayId}</div>
        </div>
      ),
      title: '网关名称/ID',
      width: 280,
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
    },
    {
      key: 'action',
      render: (_text: unknown, record: Gateway) => (
        <>
          <Button onClick={() => handleEditGateway(record)} type="link">
            编辑
          </Button>
          <Button danger onClick={() => handleDeleteGateway(record.gatewayId)} type="link">
            删除
          </Button>
        </>
      ),
      title: '操作',
    },
  ];

  // Higress 网关的列定义
  const higressColumns: ColumnsType<Gateway> = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <div className="text-xs text-gray-500 truncate">{record.gatewayId}</div>
        </div>
      ),
      title: '网关名称/ID',
      width: 280,
    },
    {
      dataIndex: 'address',
      key: 'address',
      render: (_text: unknown, record: Gateway) => {
        return record.higressConfig?.address || '-';
      },
      title: 'Console地址',
    },
    {
      dataIndex: 'gatewayAddress',
      key: 'gatewayAddress',
      render: (_text: unknown, record: Gateway) => {
        return record.higressConfig?.gatewayAddress || '-';
      },
      title: 'Gateway地址',
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
    },
    {
      key: 'action',
      render: (_text: unknown, record: Gateway) => (
        <>
          <Button onClick={() => handleEditGateway(record)} type="link">
            编辑
          </Button>
          <Button danger onClick={() => handleDeleteGateway(record.gatewayId)} type="link">
            删除
          </Button>
        </>
      ),
      title: '操作',
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">网关实例</h1>
          <p className="text-gray-500 mt-2">管理和配置您的网关实例</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={() => setTypeSelectorVisible(true)} type="primary">
          导入网关实例
        </Button>
      </div>

      <Tabs
        activeKey={activeTab}
        items={[
          {
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 pl-4">
                  <h3 className="text-lg font-medium text-gray-900">Higress 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">Higress 云原生网关</p>
                </div>
                <Table
                  columns={higressColumns}
                  dataSource={gateways}
                  loading={loading}
                  pagination={{
                    current: pagination.current,
                    onChange: handlePaginationChange,
                    onShowSizeChange: handlePaginationChange,
                    pageSize: pagination.pageSize,
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    total: pagination.total,
                  }}
                  rowKey="gatewayId"
                />
              </div>
            ),
            key: 'HIGRESS',
            label: 'Higress 网关',
          },
          {
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 pl-4">
                  <h3 className="text-lg font-medium text-gray-900">API 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">阿里云 API 网关服务</p>
                </div>
                <Table
                  columns={apigColumns}
                  dataSource={gateways}
                  loading={loading}
                  pagination={{
                    current: pagination.current,
                    onChange: handlePaginationChange,
                    onShowSizeChange: handlePaginationChange,
                    pageSize: pagination.pageSize,
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    total: pagination.total,
                  }}
                  rowKey="gatewayId"
                />
              </div>
            ),
            key: 'APIG_API',
            label: 'API 网关',
          },
          {
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 pl-4">
                  <h3 className="text-lg font-medium text-gray-900">AI 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">阿里云 AI 网关服务</p>
                </div>
                <Table
                  columns={apigColumns}
                  dataSource={gateways}
                  loading={loading}
                  pagination={{
                    current: pagination.current,
                    onChange: handlePaginationChange,
                    onShowSizeChange: handlePaginationChange,
                    pageSize: pagination.pageSize,
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    total: pagination.total,
                  }}
                  rowKey="gatewayId"
                />
              </div>
            ),
            key: 'APIG_AI',
            label: 'AI 网关',
          },
          {
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 pl-4">
                  <h3 className="text-lg font-medium text-gray-900">AI 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">专有云 AI 网关服务</p>
                </div>
                <Table
                  columns={adpAiColumns}
                  dataSource={gateways}
                  loading={loading}
                  pagination={{
                    current: pagination.current,
                    onChange: handlePaginationChange,
                    onShowSizeChange: handlePaginationChange,
                    pageSize: pagination.pageSize,
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    total: pagination.total,
                  }}
                  rowKey="gatewayId"
                />
              </div>
            ),
            key: 'ADP_AI_GATEWAY',
            label: '专有云 AI 网关',
          },
          {
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 pl-4">
                  <h3 className="text-lg font-medium text-gray-900">飞天企业版 AI 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">阿里云飞天企业版 AI 网关服务</p>
                </div>
                <Table
                  columns={apsaraGatewayColumns}
                  dataSource={gateways}
                  loading={loading}
                  pagination={{
                    current: pagination.current,
                    onChange: handlePaginationChange,
                    onShowSizeChange: handlePaginationChange,
                    pageSize: pagination.pageSize,
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    total: pagination.total,
                  }}
                  rowKey="gatewayId"
                />
              </div>
            ),
            key: 'APSARA_GATEWAY',
            label: '飞天企业版 AI 网关',
          },
        ]}
        onChange={handleTabChange}
      />

      <ImportGatewayModal
        gatewayType={
          selectedGatewayType as 'APIG_API' | 'APIG_AI' | 'ADP_AI_GATEWAY' | 'APSARA_GATEWAY'
        }
        onCancel={() => setImportVisible(false)}
        onSuccess={handleImportSuccess}
        visible={importVisible}
      />

      <ImportHigressModal
        onCancel={() => setHigressImportVisible(false)}
        onSuccess={handleImportSuccess}
        visible={higressImportVisible}
      />

      <EditGatewayModal
        gateway={editingGateway}
        onCancel={() => {
          setEditVisible(false);
          setEditingGateway(null);
        }}
        onSuccess={() => {
          setEditVisible(false);
          setEditingGateway(null);
          fetchGatewaysByType(activeTab, pagination.current, pagination.pageSize);
        }}
        visible={editVisible}
      />

      <GatewayTypeSelector
        onCancel={() => setTypeSelectorVisible(false)}
        onSelect={handleGatewayTypeSelect}
        visible={typeSelectorVisible}
      />
    </div>
  );
}
