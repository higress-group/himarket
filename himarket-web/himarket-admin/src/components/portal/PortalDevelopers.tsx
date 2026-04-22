import {
  EditOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  UnorderedListOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { Table, Button, Space, message, Modal } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { SubscriptionListModal } from '@/components/subscription/SubscriptionListModal';
import { portalApi } from '@/lib/api';
import { formatDateTime } from '@/lib/utils';
import type { Portal, Developer, Consumer } from '@/types';

interface PortalDevelopersProps {
  portal: Portal;
}

export function PortalDevelopers({ portal }: PortalDevelopersProps) {
  const [developers, setDevelopers] = useState<Developer[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number, _range: [number, number]) => `共 ${total} 条`,
    total: 0,
  });

  // Consumer相关状态
  const [consumers, setConsumers] = useState<Consumer[]>([]);
  const [consumerModalVisible, setConsumerModalVisible] = useState(false);
  const [currentDeveloper, setCurrentDeveloper] = useState<Developer | null>(null);
  const [consumerPagination, setConsumerPagination] = useState({
    current: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number, _range: [number, number]) => `共 ${total} 条`,
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
          message.success('取消授权成功');
        } else {
          message.success('审批成功');
        }
        fetchDevelopers();
      })
      .catch(() => {
        message.error('审批失败');
      });
  };

  const handleTableChange = (paginationInfo: { current?: number; pageSize?: number }) => {
    setPagination((prev) => ({
      ...prev,
      current: paginationInfo.current ?? prev.current,
      pageSize: paginationInfo.pageSize ?? prev.pageSize,
    }));
  };

  const handleDeleteDeveloper = (developerId: string, username: string) => {
    Modal.confirm({
      cancelText: '取消',
      content: `确定要删除开发者 "${username}" 吗？此操作不可恢复。`,
      icon: <ExclamationCircleOutlined />,
      okText: '确认删除',
      okType: 'danger',
      onOk() {
        portalApi
          .deleteDeveloper(developerId)
          .then(() => {
            message.success('删除成功');
            fetchDevelopers();
          })
          .catch(() => {
            message.error('删除失败');
          });
      },
      title: '确认删除',
    });
  };

  // Consumer相关函数
  const handleViewConsumers = (developer: Developer) => {
    setCurrentDeveloper(developer);
    setConsumerModalVisible(true);
    setConsumerPagination((prev) => ({ ...prev, current: 1 }));
    fetchConsumers(developer.developerId, 1, consumerPagination.pageSize);
  };

  const fetchConsumers = (developerId: string, page: number, size: number) => {
    portalApi.getConsumerList(portal.portalId, developerId, { page: page, size }).then((res) => {
      setConsumers(res.data.content || []);
      setConsumerPagination((prev) => ({
        ...prev,
        total: res.data.totalElements || 0,
      }));
    });
  };

  const handleConsumerTableChange = (paginationInfo: { current?: number; pageSize?: number }) => {
    if (currentDeveloper) {
      setConsumerPagination((prev) => ({
        ...prev,
        current: paginationInfo.current ?? prev.current,
        pageSize: paginationInfo.pageSize ?? prev.pageSize,
      }));
      fetchConsumers(
        currentDeveloper.developerId,
        paginationInfo.current ?? consumerPagination.current,
        paginationInfo.pageSize ?? consumerPagination.pageSize,
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
          <div className="font-medium">{username}</div>
          <div className="text-sm text-gray-500">{record.developerId}</div>
        </div>
      ),
      title: '开发者名称/ID',
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
              <span className="text-xs text-gray-900">可用</span>
            </>
          ) : (
            <>
              <ClockCircleOutlined className="text-orange-500 mr-2" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">待审核</span>
            </>
          )}
        </div>
      ),
      title: '状态',
      width: 120,
    },

    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
      width: 160,
    },

    {
      fixed: 'right' as const,
      key: 'action',
      render: (_: unknown, record: Developer) => (
        <Space size="middle">
          <Button icon={<EyeOutlined />} onClick={() => handleViewConsumers(record)} type="link">
            查看Consumer
          </Button>
          {!portal.portalSettingConfig.autoApproveDevelopers &&
            (record.status === 'APPROVED' ? (
              <Button
                icon={<EditOutlined />}
                onClick={() => handleUpdateDeveloperStatus(record.developerId, 'PENDING')}
                type="link"
              >
                取消授权
              </Button>
            ) : (
              <Button
                icon={<EditOutlined />}
                onClick={() => handleUpdateDeveloperStatus(record.developerId, 'APPROVED')}
                type="link"
              >
                审批通过
              </Button>
            ))}
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteDeveloper(record.developerId, record.username)}
            type="link"
          >
            删除
          </Button>
        </Space>
      ),
      title: '操作',
      width: 250,
    },
  ];

  // Consumer表格列定义
  const consumerColumns = [
    {
      dataIndex: 'name',
      key: 'name',
      title: 'Consumer名称',
      width: 200,
    },
    {
      dataIndex: 'consumerId',
      key: 'consumerId',
      title: 'Consumer ID',
      width: 200,
    },
    {
      dataIndex: 'description',
      ellipsis: true,
      key: 'description',
      title: '描述',
      width: 200,
    },
    // {
    //   title: '状态',
    //   dataIndex: 'status',
    //   key: 'status',
    //   width: 120,
    //   render: (status: string) => (
    //     <Badge status={status === 'APPROVED' ? 'success' : 'default'} text={status === 'APPROVED' ? '可用' : '待审核'} />
    //   )
    // },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
      width: 150,
    },
    {
      key: 'action',
      render: (_: unknown, record: Consumer) => (
        <div
          className="text-colorPrimary/80 text-colorPrimary flex items-center gap-2"
          onClick={() => handleViewSubscriptions(record)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handleViewSubscriptions(record);
            }
          }}
          role="button"
          tabIndex={0}
        >
          <UnorderedListOutlined />
          订阅列表
        </div>
      ),
      title: '操作',
      width: 120,
    },
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">开发者</h1>
          <p className="text-gray-600">管理Portal的开发者用户</p>
        </div>
      </div>

      <Table
        columns={columns}
        dataSource={developers}
        onChange={handleTableChange}
        pagination={pagination}
        rowKey="developerId"
        scroll={{
          x: 'max-content',
          y: 'calc(100vh - 400px)',
        }}
      />

      {/* Consumer弹窗 */}
      <Modal
        destroyOnClose
        footer={null}
        onCancel={() => setConsumerModalVisible(false)}
        open={consumerModalVisible}
        title={`查看Consumer - ${currentDeveloper?.username || ''}`}
        width={1000}
      >
        <Table
          columns={consumerColumns}
          dataSource={consumers}
          onChange={handleConsumerTableChange}
          pagination={consumerPagination}
          rowKey="consumerId"
          scroll={{ y: 'calc(100vh - 400px)' }}
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
