import {
  CheckOutlined,
  UndoOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  EditOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { Button, Space, message, Modal, Tooltip } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { DataTable } from '@/components/common/DataTable';
import { SubscriptionListModal } from '@/components/subscription/SubscriptionListModal';
import { useLocale } from '@/contexts/LocaleContext';
import { portalApi } from '@/lib/api';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { Portal, Developer, Consumer } from '@/types';

interface PortalDevelopersProps {
  portal: Portal;
}

export function PortalDevelopers({ portal }: PortalDevelopersProps) {
  const { t } = useLocale();
  const [developers, setDevelopers] = useState<Developer[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number, _range: [number, number]) => t('common.totalItems', { total }),
    total: 0,
  });

  // Consumer相关状态
  const [consumers, setConsumers] = useState<Consumer[]>([]);
  const [consumerModalVisible, setConsumerModalVisible] = useState(false);
  const [currentDeveloper, setCurrentDeveloper] = useState<Developer | null>(null);
  const [consumerSearchName, setConsumerSearchName] = useState('');
  const [consumerPagination, setConsumerPagination] = useState({
    current: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number, _range: [number, number]) => t('common.totalItems', { total }),
    total: 0,
  });

  // 订阅列表相关状态
  const [subscriptionModalVisible, setSubscriptionModalVisible] = useState(false);
  const [currentConsumer, setCurrentConsumer] = useState<Consumer | null>(null);

  const { current: page, pageSize } = pagination;

  const fetchDevelopers = useCallback(() => {
    portalApi
      .getDeveloperList(portal.portalId, {
        page,
        size: pageSize,
      })
      .then((res) => {
        setDevelopers(res.data.content);
        setPagination((prev) => ({
          ...prev,
          total: res.data.totalElements || 0,
        }));
      });
  }, [page, pageSize, portal.portalId]);

  useEffect(() => {
    fetchDevelopers();
  }, [fetchDevelopers]);

  const handleUpdateDeveloperStatus = (developerId: string, status: string) => {
    portalApi
      .updateDeveloperStatus(portal.portalId, developerId, status)
      .then(() => {
        if (status === 'PENDING') {
          message.success(t('portal.developers.revokeSuccess'));
        } else {
          message.success(t('portal.developers.approveSuccess'));
        }
        fetchDevelopers();
      })
      .catch(() => {
        message.error(t('portal.developers.approveFailed'));
      });
  };

  const handleTableChange = (page: number, size?: number) => {
    setPagination((prev) => ({
      ...prev,
      current: page,
      pageSize: size ?? prev.pageSize,
    }));
  };

  const handleDeleteDeveloper = (developerId: string, username: string) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('portal.developers.deleteConfirm', { name: username }),
      icon: <ExclamationCircleOutlined />,
      okText: t('common.confirmDelete'),
      okType: 'danger',
      onOk() {
        portalApi
          .deleteDeveloper(developerId)
          .then(() => {
            message.success(t('common.deleteSuccess'));
            fetchDevelopers();
          })
          .catch(() => {
            message.error(t('portal.developers.deleteFailed'));
          });
      },
      title: t('common.confirmDelete'),
    });
  };

  // Consumer相关函数
  const handleViewConsumers = (developer: Developer) => {
    setCurrentDeveloper(developer);
    setConsumerModalVisible(true);
    setConsumerSearchName('');
    setConsumerPagination((prev) => ({ ...prev, current: 1 }));
    fetchConsumers(developer.developerId, 1, consumerPagination.pageSize, '');
  };

  const fetchConsumers = (developerId: string, page: number, size: number, name?: string) => {
    portalApi.getConsumerList(portal.portalId, developerId, { name, page, size }).then((res) => {
      setConsumers(res.data.content || []);
      setConsumerPagination((prev) => ({
        ...prev,
        total: res.data.totalElements || 0,
      }));
    });
  };

  const handleConsumerTableChange = (page: number, size?: number) => {
    if (currentDeveloper) {
      setConsumerPagination((prev) => ({
        ...prev,
        current: page,
        pageSize: size ?? prev.pageSize,
      }));
      fetchConsumers(
        currentDeveloper.developerId,
        page,
        size ?? consumerPagination.pageSize,
        consumerSearchName || undefined,
      );
    }
  };

  const handleConsumerSearch = () => {
    if (currentDeveloper) {
      setConsumerPagination((prev) => ({ ...prev, current: 1 }));
      fetchConsumers(
        currentDeveloper.developerId,
        1,
        consumerPagination.pageSize,
        consumerSearchName || undefined,
      );
    }
  };

  // 查看订阅列表
  const handleViewSubscriptions = (consumer: Consumer) => {
    setCurrentConsumer(consumer);
    setSubscriptionModalVisible(true);
  };

  // 关闭订阅列表模态框
  const handleSubscriptionModalCancel = () => {
    setSubscriptionModalVisible(false);
    setCurrentConsumer(null);
  };

  const columns = [
    {
      dataIndex: 'username',
      fixed: 'left' as const,
      key: 'username',
      render: (username: string, record: Developer) => (
        <div className="ml-2">
          <Tooltip placement="topLeft" title={username}>
            <button
              className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
              onClick={() => handleViewConsumers(record)}
              type="button"
            >
              {username}
            </button>
          </Tooltip>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.developerId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.developerId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('portal.developers.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <div className="flex items-center">
          {status === 'APPROVED' ? (
            <>
              <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">{t('portal.developers.approved')}</span>
            </>
          ) : (
            <>
              <ClockCircleOutlined className="text-orange-500 mr-2" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">{t('portal.developers.pending')}</span>
            </>
          )}
        </div>
      ),
      title: t('common.status'),
      width: 120,
    },

    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: t('product.overview.createAt'),
      width: 160,
    },

    {
      fixed: 'right' as const,
      key: 'action',
      render: (_: unknown, record: Developer) => (
        <Space size="middle">
          {record.status === 'APPROVED' ? (
            <Button
              icon={<UndoOutlined />}
              onClick={() => handleUpdateDeveloperStatus(record.developerId, 'PENDING')}
              type="link"
            >
              {t('portal.developers.revoke')}
            </Button>
          ) : (
            <Button
              icon={<CheckOutlined />}
              onClick={() => handleUpdateDeveloperStatus(record.developerId, 'APPROVED')}
              type="link"
            >
              {t('portal.developers.approve')}
            </Button>
          )}
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteDeveloper(record.developerId, record.username)}
            type="link"
          >
            {t('common.delete')}
          </Button>
        </Space>
      ),
      title: t('common.operation'),
      width: 180,
    },
  ];

  // Consumer表格列定义
  const consumerColumns = [
    {
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: Consumer) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{name}</div>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.consumerId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.consumerId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('portal.developers.consumerNameAndId'),
      width: 280,
    },
    {
      dataIndex: 'description',
      ellipsis: true,
      key: 'description',
      title: t('common.description'),
      width: 200,
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: t('product.overview.createAt'),
      width: 150,
    },
    {
      key: 'action',
      render: (_: unknown, record: Consumer) => (
        <Button
          className="text-blue-600 hover:text-blue-700 hover:bg-blue-50 !px-2 text-xs"
          icon={<EditOutlined />}
          onClick={() => handleViewSubscriptions(record)}
          type="text"
        >
          {t('portal.developers.manageSubscriptions')}
        </Button>
      ),
      title: t('common.operation'),
      width: 120,
    },
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">{t('portal.developers.title')}</h1>
          <p className="text-gray-600">{t('portal.developers.description')}</p>
        </div>
      </div>

      <DataTable<Developer>
        columns={columns}
        dataSource={developers}
        pagination={{
          current: pagination.current,
          onChange: handleTableChange,
          pageSize: pagination.pageSize,
          total: pagination.total,
        }}
        rowKey="developerId"
      />

      {/* Consumer弹窗 */}
      <Modal
        destroyOnClose
        footer={null}
        onCancel={() => setConsumerModalVisible(false)}
        open={consumerModalVisible}
        title={t('portal.developers.viewConsumer', { name: currentDeveloper?.username || '' })}
        width={1000}
      >
        <DataTable<Consumer>
          columns={consumerColumns}
          dataSource={consumers}
          pagination={{
            current: consumerPagination.current,
            onChange: handleConsumerTableChange,
            pageSize: consumerPagination.pageSize,
            total: consumerPagination.total,
          }}
          rowKey="consumerId"
          search={{
            onChange: (value) => {
              setConsumerSearchName(value);
              if (!value) {
                handleConsumerSearch();
              }
            },
            onSearch: handleConsumerSearch,
            placeholder: t('portal.developers.searchConsumer'),
            value: consumerSearchName,
          }}
        />
      </Modal>

      {/* 订阅列表弹窗 */}
      {currentConsumer && (
        <SubscriptionListModal
          consumerId={currentConsumer.consumerId}
          consumerName={currentConsumer.name}
          onCancel={handleSubscriptionModalCancel}
          visible={subscriptionModalVisible}
        />
      )}
    </div>
  );
}
