import {
  CheckOutlined,
  DeleteOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Modal, message, Button, Popconfirm } from 'antd';
import axios from 'axios';
import { useEffect, useRef, useState } from 'react';

import { DataTable } from '@/components/common/DataTable';
import { portalApi } from '@/lib/api';
import { formatDateTime, ProductTypeMap } from '@/lib/utils';
import type { Subscription } from '@/types/subscription';

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
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING' | 'APPROVED'>('ALL');
  const [productNameSearch, setProductNameSearch] = useState('');
  const [stats, setStats] = useState({ all: 0, approved: 0, pending: 0 });
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
    total: 0,
  });
  const skipFetchRef = useRef(false);

  const handleFilterChange = (filter: 'ALL' | 'PENDING' | 'APPROVED') => {
    setStatusFilter(filter);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  // 首次进入 Modal 时统一刷新数据和统计
  useEffect(() => {
    if (visible && consumerId) {
      refreshAll();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, consumerId]);

  // 分页或筛选变化时只加载当前列表
  useEffect(() => {
    if (!visible || !consumerId) return;
    if (skipFetchRef.current) {
      return;
    }
    fetchSubscriptions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pagination.current, pagination.pageSize, statusFilter]);

  const fetchSubscriptions = () => {
    setLoading(true);
    const params: { page: number; size: number; status?: string; productName?: string } = {
      page: pagination.current,
      size: pagination.pageSize,
    };
    if (statusFilter !== 'ALL') {
      params.status = statusFilter;
    }
    if (productNameSearch) {
      params.productName = productNameSearch;
    }
    portalApi
      .getConsumerSubscriptions(consumerId, params)
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

  const refreshAll = () => {
    skipFetchRef.current = true;
    setLoading(true);
    const baseParams = { page: 1, size: 10 };
    const searchParams = productNameSearch ? { productName: productNameSearch } : {};
    Promise.all([
      portalApi.getConsumerSubscriptions(consumerId, { ...baseParams, ...searchParams }),
      portalApi.getConsumerSubscriptions(consumerId, {
        ...baseParams,
        status: 'PENDING',
        ...searchParams,
      }),
      portalApi.getConsumerSubscriptions(consumerId, {
        ...baseParams,
        status: 'APPROVED',
        ...searchParams,
      }),
    ])
      .then(([allRes, pendingRes, approvedRes]) => {
        setStats({
          all: allRes.data?.totalElements || 0,
          approved: approvedRes.data?.totalElements || 0,
          pending: pendingRes.data?.totalElements || 0,
        });
        const res =
          statusFilter === 'ALL' ? allRes : statusFilter === 'PENDING' ? pendingRes : approvedRes;
        setSubscriptions(res.data?.content || []);
        setPagination((prev) => ({
          ...prev,
          current: 1,
          total: res.data?.totalElements || 0,
        }));
      })
      .catch(() => {
        message.error('刷新数据失败');
      })
      .finally(() => {
        setLoading(false);
        skipFetchRef.current = false;
      });
  };

  const handleProductNameSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    refreshAll();
  };

  const handleTableChange = (page: number, size?: number) => {
    setPagination((prev) => ({
      ...prev,
      current: page,
      pageSize: size ?? prev.pageSize,
    }));
  };

  const handleApproveSubscription = async (subscription: Subscription) => {
    setActionLoading(`${subscription.consumerId}-${subscription.productId}-approve`);
    try {
      await portalApi.approveSubscription(subscription.consumerId, subscription.productId);
      message.success('审批通过成功');
      refreshAll();
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
      refreshAll();
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
        <span className="text-gray-600 text-xs">{ProductTypeMap[productType] || productType}</span>
      ),
      title: '产品类型',
    },
    {
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <div className="flex items-center">
          {status === 'APPROVED' ? (
            <>
              <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">已通过</span>
            </>
          ) : (
            <>
              <ClockCircleOutlined className="text-orange-500 mr-2" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">待审批</span>
            </>
          )}
        </div>
      ),
      title: '订阅状态',
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => (
        <span className="text-xs text-gray-500">{formatDateTime(date)}</span>
      ),
      title: '订阅时间',
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
              className="text-blue-600 hover:text-blue-700 hover:bg-blue-50 !px-2 text-xs"
              icon={<CheckOutlined />}
              loading={isApproving}
              onClick={() => handleApproveSubscription(record)}
              type="text"
            >
              审批
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
              <Button
                className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
                danger
                icon={<DeleteOutlined />}
                loading={isDeleting}
                type="text"
              >
                删除
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

  return (
    <Modal
      destroyOnClose
      footer={null}
      onCancel={onCancel}
      open={visible}
      title={<div className="text-lg font-semibold">订阅列表 - {consumerName}</div>}
      width={1000}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="inline-flex bg-gray-100 rounded-lg p-1">
          <button
            className={`px-4 py-1.5 text-sm rounded-md transition-all ${
              statusFilter === 'ALL'
                ? 'bg-white text-gray-900 shadow-sm font-medium'
                : 'text-gray-500 hover:text-gray-700'
            }`}
            onClick={() => handleFilterChange('ALL')}
          >
            全部 ({stats.all})
          </button>
          <button
            className={`px-4 py-1.5 text-sm rounded-md transition-all ${
              statusFilter === 'PENDING'
                ? 'bg-white text-gray-900 shadow-sm font-medium'
                : 'text-gray-500 hover:text-gray-700'
            }`}
            onClick={() => handleFilterChange('PENDING')}
          >
            待审批 ({stats.pending})
          </button>
          <button
            className={`px-4 py-1.5 text-sm rounded-md transition-all ${
              statusFilter === 'APPROVED'
                ? 'bg-white text-gray-900 shadow-sm font-medium'
                : 'text-gray-500 hover:text-gray-700'
            }`}
            onClick={() => handleFilterChange('APPROVED')}
          >
            已通过 ({stats.approved})
          </button>
        </div>
        <Button
          className="text-gray-500 hover:text-gray-700"
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={refreshAll}
          type="text"
        >
          刷新
        </Button>
      </div>
      <DataTable<Subscription>
        columns={columns}
        dataSource={subscriptions}
        loading={loading}
        locale={{
          emptyText: '暂无订阅记录',
        }}
        pagination={{
          current: pagination.current,
          onChange: handleTableChange,
          pageSize: pagination.pageSize,
          total: pagination.total,
        }}
        rowKey="subscriptionId"
        search={{
          onChange: (value) => {
            setProductNameSearch(value);
            if (!value) {
              handleProductNameSearch();
            }
          },
          onSearch: handleProductNameSearch,
          placeholder: '搜索产品名称',
          value: productNameSearch,
        }}
      />
    </Modal>
  );
}
