import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, message, Modal, Tooltip } from 'antd';
import { useState, useEffect, useCallback, useRef } from 'react';

import { AdminPageHeader } from '@/components/common';
import { DataTable } from '@/components/common/DataTable';
import EditGatewayModal from '@/components/console/EditGatewayModal';
import GatewayTypeSelector from '@/components/console/GatewayTypeSelector';
import ImportGatewayModal from '@/components/console/ImportGatewayModal';
import ImportHigressModal from '@/components/console/ImportHigressModal';
import { useLocale } from '@/contexts/LocaleContext';
import { gatewayApi } from '@/lib/api';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { Gateway, GatewayType } from '@/types';

import type { TableProps } from 'antd';

export default function Consoles() {
  const { t } = useLocale();
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
  const lastAutoFetchKeyRef = useRef('');

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
    const key = `${activeTab}-1-10`;
    if (lastAutoFetchKeyRef.current === key) {
      return;
    }
    lastAutoFetchKeyRef.current = key;
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
  const handlePaginationChange = (page: number, pageSize?: number) => {
    fetchGatewaysByType(activeTab, page, pageSize ?? 10);
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
      cancelText: t('common.cancel'),
      content: t('page.gateway.confirmDelete'),
      okText: t('common.confirmDelete'),
      okType: 'danger',
      onOk: async () => {
        try {
          await gatewayApi.deleteGateway(gatewayId);
          message.success(t('common.deleteSuccess'));
          fetchGatewaysByType(activeTab, pagination.current, pagination.pageSize);
        } catch (_error) {
          // message.error('删除失败')
        }
      },
      title: t('common.confirmDelete'),
    });
  };

  const actionColumn: NonNullable<TableProps<Gateway>['columns']>[number] = {
    key: 'action',
    render: (_text: unknown, record: Gateway) => (
      <div className="flex items-center gap-1">
        <Button
          className="text-blue-600 hover:text-blue-700 hover:bg-blue-50 !px-2 text-xs"
          icon={<EditOutlined />}
          onClick={() => handleEditGateway(record)}
          type="text"
        >
          {t('common.edit')}
        </Button>
        <Button
          className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleDeleteGateway(record.gatewayId)}
          type="text"
        >
          {t('common.delete')}
        </Button>
      </div>
    ),
    title: t('common.operation'),
    width: 160,
  };

  // APIG 网关的列定义
  const apigColumns: TableProps<Gateway>['columns'] = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.gatewayId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.gatewayId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.gateway.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'region',
      key: 'region',
      render: (_text: unknown, record: Gateway) => {
        return record.apigConfig?.region || '-';
      },
      title: t('page.gateway.region'),
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: t('product.overview.createAt'),
    },
    actionColumn,
  ];

  // 专有云 AI 网关的列定义
  const adpAiColumns: TableProps<Gateway>['columns'] = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.gatewayId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.gatewayId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.gateway.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: t('product.overview.createAt'),
    },
    actionColumn,
  ];

  // 飞天企业版 AI 网关的列定义
  const apsaraGatewayColumns: TableProps<Gateway>['columns'] = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.gatewayId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.gatewayId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.gateway.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: t('product.overview.createAt'),
    },
    actionColumn,
  ];

  // Higress 网关的列定义
  const higressColumns: TableProps<Gateway>['columns'] = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: Gateway) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.gatewayName}</div>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.gatewayId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.gatewayId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.gateway.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'address',
      key: 'address',
      render: (_text: unknown, record: Gateway) => {
        return record.higressConfig?.address || '-';
      },
      title: t('page.gateway.consoleAddress'),
    },
    {
      dataIndex: 'gatewayAddress',
      key: 'gatewayAddress',
      render: (_text: unknown, record: Gateway) => {
        return record.higressConfig?.gatewayAddress || '-';
      },
      title: t('page.gateway.gatewayAddress'),
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: t('product.overview.createAt'),
    },
    actionColumn,
  ];

  const dataTablePagination = {
    current: pagination.current,
    onChange: handlePaginationChange,
    pageSize: pagination.pageSize,
    total: pagination.total,
  };

  const fallbackGatewayType = {
    columns: higressColumns,
    description: t('page.gateway.higressDescription'),
    key: 'HIGRESS' as const,
    label: t('page.gateway.higress'),
    title: t('page.gateway.higress'),
  };
  const gatewayTypes = [
    fallbackGatewayType,
    {
      columns: apigColumns,
      description: t('page.gateway.apiGatewayDescription'),
      key: 'APIG_API' as const,
      label: t('page.gateway.apiGateway'),
      title: t('page.gateway.apiGateway'),
    },
    {
      columns: apigColumns,
      description: t('page.gateway.aiGatewayDescription'),
      key: 'APIG_AI' as const,
      label: t('page.gateway.aiGateway'),
      title: t('page.gateway.aiGateway'),
    },
    {
      columns: adpAiColumns,
      description: t('page.gateway.privateAiGatewayDescription'),
      key: 'ADP_AI_GATEWAY' as const,
      label: t('page.gateway.privateAiGateway'),
      title: t('page.gateway.privateAiGateway'),
    },
    {
      columns: apsaraGatewayColumns,
      description: t('page.gateway.apsaraGatewayDescription'),
      key: 'APSARA_GATEWAY' as const,
      label: t('page.gateway.apsaraGateway'),
      title: t('page.gateway.apsaraGateway'),
    },
  ];
  const activeGatewayType =
    gatewayTypes.find((item) => item.key === activeTab) ?? fallbackGatewayType;

  return (
    <div className="space-y-6">
      <AdminPageHeader
        actions={
          <Button
            icon={<PlusOutlined />}
            onClick={() => setTypeSelectorVisible(true)}
            type="primary"
          >
            {t('page.gateway.import')}
          </Button>
        }
        description={t('page.gateway.description')}
        title={t('page.gateway.title')}
      />

      <div className="space-y-6">
        <nav className="flex flex-wrap gap-1 rounded-lg border border-gray-200 bg-gray-50 p-1">
          {gatewayTypes.map((type) => (
            <button
              aria-current={activeTab === type.key ? 'page' : undefined}
              className={`h-9 whitespace-nowrap rounded-md px-3 text-sm font-medium transition-colors ${
                activeTab === type.key
                  ? 'bg-white text-gray-950 shadow-sm'
                  : 'text-gray-500 hover:bg-white/70 hover:text-gray-800'
              }`}
              key={type.key}
              onClick={() => handleTabChange(type.key)}
            >
              {type.label}
            </button>
          ))}
        </nav>

        <div>
          <div className="px-1 pb-3">
            <h3 className="text-lg font-medium text-gray-900">{activeGatewayType.title}</h3>
            <p className="mt-1 text-sm text-gray-500">{activeGatewayType.description}</p>
          </div>
          <DataTable<Gateway>
            columns={activeGatewayType.columns}
            dataSource={gateways}
            loading={loading}
            pagination={dataTablePagination}
            rowKey="gatewayId"
          />
        </div>
      </div>

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
