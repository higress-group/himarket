import { Modal, Table, Badge, message, Button, Popconfirm } from 'antd';
import axios from 'axios';
import { useEffect, useState } from 'react';

import { portalApi } from '@/lib/api';
import { formatDateTime, ProductTypeMap } from '@/lib/utils';
import type { Subscription } from '@/types/subscription';

import type { TablePaginationConfig } from 'antd/es/table/interface';

interface SubscriptionListModalProps {
  visible: boolean;
  consumerId: string;
  consumerName: string;
  onCancel: () => void;
}

export function SubscriptionListModal({
  consumerId,
  consumerName,
  onCancel,
  visible,
}: SubscriptionListModalProps) {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
    total: 0,
  });

  useEffect(() => {
    if (visible && consumerId) {
      fetchSubscriptions();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, consumerId, pagination.current, pagination.pageSize]);

  const fetchSubscriptions = () => {
    setLoading(true);
    portalApi
      .getConsumerSubscriptions(consumerId, {
        page: pagination.current,
        size: pagination.pageSize,
      })
      .then((res) => {
        setSubscriptions(res.data.content || []);
        setPagination((prev) => ({
          ...prev,
          total: res.data.totalElements || 0,
        }));
      })
      .catch(() => {
        message.error('获取订阅列表失败');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const handleTableChange = (paginationInfo: TablePaginationConfig) => {
    setPagination((prev) => ({
      ...prev,
      current: paginationInfo.current ?? prev.current,
      pageSize: paginationInfo.pageSize ?? prev.pageSize,
    }));
  };

  const handleApproveSubscription = async (subscription: Subscription) => {
    setActionLoading(`${subscription.consumerId}-${subscription.productId}-approve`);
    try {
      await portalApi.approveSubscription(subscription.consumerId, subscription.productId);
      message.success('审批通过成功');
      fetchSubscriptions(); // 重新获取数据
    } catch (error: unknown) {
      const errorMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message || error.message
        : error instanceof Error
          ? error.message
          : '审批失败';
      message.error(`审批失败: ${errorMessage}`);
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteSubscription = async (subscription: Subscription) => {
    setActionLoading(`${subscription.consumerId}-${subscription.productId}-delete`);
    try {
      await portalApi.deleteSubscription(subscription.consumerId, subscription.productId);
      message.success('删除订阅成功');
      fetchSubscriptions(); // 重新获取数据
    } catch (error: unknown) {
      const errorMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message || error.message
        : error instanceof Error
          ? error.message
          : '删除订阅失败';
      message.error(`删除订阅失败: ${errorMessage}`);
    } finally {
      setActionLoading(null);
    }
  };

  const columns = [
    {
      dataIndex: 'productName',
      key: 'productName',
      render: (productName: string) => (
        <div>
          <div className="font-medium">{productName || '未知产品'}</div>
        </div>
      ),
      title: '产品名称',
    },
    {
      dataIndex: 'productType',
      key: 'productType',
      render: (productType: string) => (
        <Badge
          color={
            productType === 'REST_API'
              ? 'blue'
              : productType === 'AGENT_API'
                ? 'orange'
                : productType === 'MODEL_API'
                  ? 'yellow'
                  : 'purple'
          }
          text={ProductTypeMap[productType] || productType}
        />
      ),
      title: '产品类型',
    },
    {
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge
          status={status === 'APPROVED' ? 'success' : 'processing'}
          text={status === 'APPROVED' ? '已通过' : '待审批'}
        />
      ),
      title: '订阅状态',
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '订阅时间',
    },
    {
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (date: string) => formatDateTime(date),
      title: '更新时间',
    },
    {
      key: 'action',
      render: (_: unknown, record: Subscription) => {
        const loadingKey = `${record.consumerId}-${record.productId}`;
        const isApproving = actionLoading === `${loadingKey}-approve`;
        const isDeleting = actionLoading === `${loadingKey}-delete`;

        if (record.status === 'PENDING') {
          return (
            <Button
              loading={isApproving}
              onClick={() => handleApproveSubscription(record)}
              size="small"
              type="primary"
            >
              审批通过
            </Button>
          );
        } else if (record.status === 'APPROVED') {
          return (
            <Popconfirm
              cancelText="取消"
              description="删除后将无法恢复"
              okText="确定"
              onConfirm={() => handleDeleteSubscription(record)}
              title="确定要删除这个订阅吗？"
            >
              <Button danger loading={isDeleting} size="small" type="default">
                删除订阅
              </Button>
            </Popconfirm>
          );
        }
        return null;
      },
      title: '操作',
      width: 120,
    },
  ];

  const pendingCount = subscriptions.filter((s) => s.status === 'PENDING').length;
  const approvedCount = subscriptions.filter((s) => s.status === 'APPROVED').length;

  return (
    <Modal
      destroyOnClose
      footer={null}
      onCancel={onCancel}
      open={visible}
      title={
        <div>
          <div className="text-lg font-semibold">订阅列表 - {consumerName}</div>
          <div className="text-sm text-gray-500 mt-1">
            待审批: <Badge count={pendingCount} style={{ backgroundColor: '#faad14' }} /> | 已通过:{' '}
            <Badge count={approvedCount} style={{ backgroundColor: '#52c41a' }} />
          </div>
        </div>
      }
      width={1000}
    >
      <Table
        columns={columns}
        dataSource={subscriptions}
        loading={loading}
        locale={{
          emptyText: '暂无订阅记录',
        }}
        onChange={handleTableChange}
        pagination={pagination}
        rowKey="subscriptionId"
      />
    </Modal>
  );
}
