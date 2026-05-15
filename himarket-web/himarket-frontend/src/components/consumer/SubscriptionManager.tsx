import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  DeleteOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { Button, message, Modal, Table, Popconfirm, Select, Input } from 'antd';
import { useState } from 'react';

import request from '../../lib/request';
import { getSubscriptionStatusText, ProductTypeMap } from '../../lib/statusUtils';
import { modelStyles } from '../../lib/styles';
import { formatDateTime } from '../../lib/utils';

import type { ISubscription } from '../../lib/apis';
import type { ApiResponse, Product } from '../../types';
import type { Subscription } from '../../types/consumer';

interface SubscriptionManagerProps {
  consumerId: string;
  subscriptions: ISubscription[];
  onSubscriptionsChange: (searchParams?: { productName: string; status: string }) => void;
  onRefresh: () => void;
  loading?: boolean;
}

export function SubscriptionManager({
  consumerId,
  loading = false,
  onRefresh,
  onSubscriptionsChange,
  subscriptions,
}: SubscriptionManagerProps) {
  const [productModalVisible, setProductModalVisible] = useState(false);
  const [filteredProducts, setFilteredProducts] = useState<Product[]>([]);
  const [productLoading, setProductLoading] = useState(false);
  const [subscribeLoading, setSubscribeLoading] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<string>('');
  const [subscriptionSearch, setSubscriptionSearch] = useState({ productName: '', status: '' });
  const [searchInput, setSearchInput] = useState(''); // 输入框的值

  // 处理搜索输入变化 - 只更新输入框值，不触发搜索
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchInput(e.target.value);
  };

  // 执行搜索
  const handleSearch = () => {
    const newSearch = { ...subscriptionSearch, productName: searchInput };
    setSubscriptionSearch(newSearch);
    onSubscriptionsChange({
      productName: searchInput,
      status: newSearch.status,
    });
  };

  // 清除搜索条件
  const handleClearSearch = () => {
    const emptySearch = { productName: '', status: '' };
    setSubscriptionSearch(emptySearch);
    onSubscriptionsChange({ productName: '', status: '' });
  };

  // 过滤产品：移除已订阅的产品
  const filterProducts = (allProducts: Product[]) => {
    // 获取已订阅的产品ID列表
    const subscribedProductIds = subscriptions.map((sub) => sub.productId);

    // 过滤掉已订阅的产品
    return allProducts.filter((product) => !subscribedProductIds.includes(product.productId));
  };

  const openProductModal = async () => {
    setProductModalVisible(true);
    setProductLoading(true);
    try {
      const response: ApiResponse<{ content: Product[] }> = await request.get(
        '/products?page=0&size=100',
      );
      if (response?.code === 'SUCCESS' && response?.data) {
        const allProducts = response.data.content || [];
        // 初始化时过滤掉已订阅的产品
        const filtered = filterProducts(allProducts);
        setFilteredProducts(filtered);
      }
    } catch (error) {
      console.error('获取产品列表失败:', error);
      // message.error('获取产品列表失败');
    } finally {
      setProductLoading(false);
    }
  };

  const handleSubscribeProducts = async () => {
    if (!selectedProduct) {
      message.warning('请选择要订阅的产品');
      return;
    }

    setSubscribeLoading(true);
    try {
      await request.post(`/consumers/${consumerId}/subscriptions`, { productId: selectedProduct });
      message.success('订阅成功');
      setProductModalVisible(false);
      setSelectedProduct('');
      onSubscriptionsChange();
    } catch (error) {
      console.error('订阅失败:', error);
      // message.error('订阅失败');
    } finally {
      setSubscribeLoading(false);
    }
  };

  const handleUnsubscribe = async (productId: string) => {
    try {
      await request.delete(`/consumers/${consumerId}/subscriptions/${productId}`);
      message.success('取消订阅成功');
      onSubscriptionsChange();
    } catch (error) {
      console.error('取消订阅失败:', error);
      // message.error('取消订阅失败');
    }
  };

  const subscriptionColumns = [
    {
      dataIndex: 'productName',
      key: 'productName',
      render: (productName: Product['productName']) => productName || '-',
      title: '产品名称',
    },
    {
      dataIndex: 'productType',
      key: 'productType',
      render: (productType: Product['productType']) => {
        return ProductTypeMap[productType] || productType || '-';
      },
      title: '产品类型',
    },
    {
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const isApproved = status === 'APPROVED';
        return (
          <div className="flex items-center">
            {isApproved ? (
              <CheckCircleFilled className="mr-2 text-green-500" style={{ fontSize: '10px' }} />
            ) : (
              <ClockCircleOutlined className="mr-2 text-orange-500" style={{ fontSize: '10px' }} />
            )}
            <span className="text-gray-900">{getSubscriptionStatusText(status)}</span>
          </div>
        );
      },
      title: '订阅状态',
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => (date ? formatDateTime(date) : '-'),
      title: '订阅时间',
    },
    {
      key: 'action',
      render: (record: Subscription) => (
        <Popconfirm
          onConfirm={() => handleUnsubscribe(record.productId)}
          title="确定要取消订阅吗？"
        >
          <Button className="rounded-lg" icon={<DeleteOutlined className="text-red-500" />} />
        </Popconfirm>
      ),
      title: '操作',
    },
  ];

  // 确保 subscriptions 始终是数组
  const safeSubscriptions = Array.isArray(subscriptions) ? subscriptions : [];

  return (
    <>
      <div className="bg-white">
        {/* 搜索框和订阅按钮在同一行 */}
        <div className="mb-4 flex justify-between">
          <div className="flex items-center gap-4">
            <Button
              className="rounded-lg"
              icon={<PlusOutlined />}
              onClick={openProductModal}
              type="primary"
            >
              订阅
            </Button>
            <Input
              allowClear
              className="w-80 rounded-lg"
              onChange={handleSearchChange}
              onClear={handleClearSearch}
              onPressEnter={handleSearch}
              placeholder="请输入产品名称进行搜索"
              prefix={<SearchOutlined className="text-gray-400" />}
              style={{
                backdropFilter: 'blur(10px)',
                backgroundColor: 'rgba(255, 255, 255, 0.6)',
              }}
              value={searchInput}
            />
          </div>
          <Button className="rounded-lg" icon={<ReloadOutlined />} onClick={onRefresh} />
        </div>
        <div className="overflow-hidden rounded-lg border border-[#e5e5e5]">
          <Table
            columns={subscriptionColumns}
            dataSource={safeSubscriptions}
            loading={loading}
            locale={{ emptyText: '暂无订阅记录，请点击上方按钮进行订阅' }}
            pagination={false}
            rowKey={(record) => record.productId}
            size="small"
          />
        </div>
      </div>

      {/* 产品选择弹窗 */}
      <Modal
        footer={
          <div className="flex justify-end space-x-2">
            <Button
              disabled={subscribeLoading}
              onClick={() => {
                if (!subscribeLoading) {
                  setProductModalVisible(false);
                  setSelectedProduct('');
                }
              }}
            >
              取消
            </Button>
            <Button
              disabled={!selectedProduct}
              loading={subscribeLoading}
              onClick={handleSubscribeProducts}
              type="primary"
            >
              确定订阅
            </Button>
          </div>
        }
        onCancel={() => {
          if (!subscribeLoading) {
            setProductModalVisible(false);
            setSelectedProduct('');
          }
        }}
        open={productModalVisible}
        styles={modelStyles}
        title="订阅产品"
        width={500}
      >
        <div>
          <div className="text-sm text-gray-700 mb-3 font-medium">选择要订阅的产品：</div>
          <Select
            filterOption={(input, option) => {
              const product = filteredProducts.find((p) => p.productId === option?.value);
              if (!product) return false;

              const searchText = input.toLowerCase();
              return (
                product.name?.toLowerCase().includes(searchText) ||
                product.description?.toLowerCase().includes(searchText)
              );
            }}
            loading={productLoading}
            notFoundContent={productLoading ? '加载中...' : '暂无可订阅的产品'}
            onChange={setSelectedProduct}
            placeholder="请输入产品名称进行搜索或直接选择"
            showSearch={true}
            style={{ width: '100%' }}
            value={selectedProduct}
          >
            {filteredProducts.map((product) => (
              <Select.Option key={product.productId} value={product.productId}>
                {product.name}
              </Select.Option>
            ))}
          </Select>
        </div>
      </Modal>
    </>
  );
}
