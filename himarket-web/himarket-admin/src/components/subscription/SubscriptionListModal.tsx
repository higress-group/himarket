import { Modal, Table, Badge, message, Button, Popconfirm } from 'antd';
import { useEffect, useState } from 'react';
import { Subscription } from '@/types/subscription';
import { portalApi } from '@/lib/api';
import { formatDateTime, ProductTypeMap } from '@/lib/utils';

interface SubscriptionListModalProps {
  visible: boolean;
  consumerId: string;
  consumerName: string;
  onCancel: () => void;
}

export function SubscriptionListModal({
  visible,
  consumerId,
  consumerName,
  onCancel
}: SubscriptionListModalProps) {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条`
  });

  useEffect(() => {
    if (visible && consumerId) {
      fetchSubscriptions();
    }
  }, [visible, consumerId, pagination.current, pagination.pageSize]);

  const fetchSubscriptions = () => {
    setLoading(true);
    portalApi.getConsumerSubscriptions(consumerId, {
      page: pagination.current - 1, // 后端从0开始
      size: pagination.pageSize
    }).then((res) => {
      setSubscriptions(res.data.content || []);
      setPagination(prev => ({
        ...prev,
        total: res.data.totalElements || 0
      }));
    }).catch(() => {
      message.error('获取订阅列表失败');
    }).finally(() => {
      setLoading(false);
    });
  };

  const handleTableChange = (paginationInfo: any) => {
    setPagination(prev => ({
      ...prev,
      current: paginationInfo.current,
      pageSize: paginationInfo.pageSize
    }));
  };

  const handleApproveSubscription = async (subscription: Subscription) => {
    setActionLoading(`${subscription.consumerId}-${subscription.productId}-approve`);
    try {
      await portalApi.approveSubscription(subscription.consumerId, subscription.productId);
      message.success('审批通过成功');
      fetchSubscriptions(); // 重新获取数据
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || error.message || '审批失败';
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
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || error.message || '删除订阅失败';
      message.error(`删除订阅失败: ${errorMessage}`);
    } finally {
      setActionLoading(null);
    }
  };

  const columns = [
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName',
      render: (productName: string) => (
        <div>
          <div className="font-medium">{productName || '未知产品'}</div>
        </div>
      )
    },
    {
      title: '产品类型',
      dataIndex: 'productType',
      key: 'productType',
      render: (productType: string) => (
        <Badge 
          color={productType === 'REST_API' ? 'blue' : 
                 productType === 'AGENT_API' ? 'orange' : 
                 productType === 'MODEL_API' ? 'yellow' : 'purple'} 
          text={ProductTypeMap[productType] || productType} 
        />
      )
    },
    {
      title: '订阅状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge 
          status={status === 'APPROVED' ? 'success' : 'processing'} 
          text={status === 'APPROVED' ? '已通过' : '待审批'} 
        />
      )
    },
    {
      title: '订阅时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date)
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (date: string) => formatDateTime(date)
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: Subscription) => {
        const loadingKey = `${record.consumerId}-${record.productId}`;
        const isApproving = actionLoading === `${loadingKey}-approve`;
        const isDeleting = actionLoading === `${loadingKey}-delete`;
        
        if (record.status === 'PENDING') {
          return (
            <Button
              type="primary"
              size="small"
              loading={isApproving}
              onClick={() => handleApproveSubscription(record)}
            >
              审批通过
            </Button>
          );
        } else if (record.status === 'APPROVED') {
          return (
            <Popconfirm
              title="确定要删除这个订阅吗？"
              description="删除后将无法恢复"
              onConfirm={() => handleDeleteSubscription(record)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="default"
                size="small"
                danger
                loading={isDeleting}
              >
                删除订阅
              </Button>
            </Popconfirm>
          );
        }
        return null;
      }
    }
  ];

  const pendingCount = subscriptions.filter(s => s.status === 'PENDING').length;
  const approvedCount = subscriptions.filter(s => s.status === 'APPROVED').length;

  return (
    <Modal
      title={
        <div>
          <div className="text-lg font-semibold">订阅列表 - {consumerName}</div>
          <div className="text-sm text-gray-500 mt-1">
            待审批: <Badge count={pendingCount} style={{ backgroundColor: '#faad14' }} /> | 
            已通过: <Badge count={approvedCount} style={{ backgroundColor: '#52c41a' }} />
          </div>
        </div>
      }
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={1000}
      destroyOnClose
    >
      <Table
        columns={columns}
        dataSource={subscriptions}
        rowKey="subscriptionId"
        loading={loading}
        pagination={pagination}
        onChange={handleTableChange}
        locale={{
          emptyText: '暂无订阅记录'
        }}
      />
    </Modal>
  );
}



