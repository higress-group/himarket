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
import { useTranslation } from 'react-i18next';

import request from '../../lib/request';
import { ProductTypeMap } from '../../lib/statusUtils';
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
  const { t } = useTranslation(['consumer', 'common']);
  const [productModalVisible, setProductModalVisible] = useState(false);
  const [filteredProducts, setFilteredProducts] = useState<Product[]>([]);
  const [productLoading, setProductLoading] = useState(false);
  const [subscribeLoading, setSubscribeLoading] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<string>('');
  const [subscriptionSearch, setSubscriptionSearch] = useState({ productName: '', status: '' });
  const [searchInput, setSearchInput] = useState('');

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchInput(e.target.value);
  };

  const handleSearch = () => {
    const newSearch = { ...subscriptionSearch, productName: searchInput };
    setSubscriptionSearch(newSearch);
    onSubscriptionsChange({
      productName: searchInput,
      status: newSearch.status,
    });
  };

  const handleClearSearch = () => {
    const emptySearch = { productName: '', status: '' };
    setSubscriptionSearch(emptySearch);
    onSubscriptionsChange({ productName: '', status: '' });
  };

  const filterProducts = (allProducts: Product[]) => {
    const subscribedProductIds = subscriptions.map((sub) => sub.productId);

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
        const filtered = filterProducts(allProducts);
        setFilteredProducts(filtered);
      }
    } catch (error) {
      console.error('Failed to fetch products:', error);
      // message.error('Failed to fetch products');
    } finally {
      setProductLoading(false);
    }
  };

  const handleSubscribeProducts = async () => {
    if (!selectedProduct) {
      message.warning(t('subscription.selectProductWarning'));
      return;
    }

    setSubscribeLoading(true);
    try {
      await request.post(`/consumers/${consumerId}/subscriptions`, { productId: selectedProduct });
      message.success(t('subscription.subscribeSuccess'));
      setProductModalVisible(false);
      setSelectedProduct('');
      onSubscriptionsChange();
    } catch (error) {
      console.error('Subscribe failed:', error);
      // message.error('Subscribe failed');
    } finally {
      setSubscribeLoading(false);
    }
  };

  const handleUnsubscribe = async (productId: string) => {
    try {
      await request.delete(`/consumers/${consumerId}/subscriptions/${productId}`);
      message.success(t('subscription.unsubscribeSuccess'));
      onSubscriptionsChange();
    } catch (error) {
      console.error('Unsubscribe failed:', error);
      // message.error('Unsubscribe failed');
    }
  };

  const subscriptionColumns = [
    {
      dataIndex: 'productName',
      key: 'productName',
      render: (productName: Product['productName']) => productName || '-',
      title: t('subscription.productName'),
    },
    {
      dataIndex: 'productType',
      key: 'productType',
      render: (productType: Product['productType']) => {
        return ProductTypeMap[productType] || productType || '-';
      },
      title: t('subscription.productType'),
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
            <span className="text-gray-900">
              {status === 'APPROVED'
                ? t('subscription.approved')
                : status === 'PENDING'
                  ? t('subscription.pending')
                  : status}
            </span>
          </div>
        );
      },
      title: t('subscription.status'),
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => (date ? formatDateTime(date) : '-'),
      title: t('subscription.subscribedAt'),
    },
    {
      key: 'action',
      render: (record: Subscription) => (
        <Popconfirm
          onConfirm={() => handleUnsubscribe(record.productId)}
          title={t('subscription.unsubscribeConfirm')}
        >
          <Button className="rounded-lg" icon={<DeleteOutlined className="text-red-500" />} />
        </Popconfirm>
      ),
      title: t('subscription.action'),
    },
  ];

  const safeSubscriptions = Array.isArray(subscriptions) ? subscriptions : [];

  return (
    <>
      <div className="bg-white">
        <div className="mb-4 flex justify-between">
          <div className="flex items-center gap-4">
            <Button
              className="rounded-lg"
              icon={<PlusOutlined />}
              onClick={openProductModal}
              type="primary"
            >
              {t('subscription.subscribe')}
            </Button>
            <Input
              allowClear
              className="w-80 rounded-lg"
              onChange={handleSearchChange}
              onClear={handleClearSearch}
              onPressEnter={handleSearch}
              placeholder={t('subscription.searchPlaceholder')}
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
            locale={{ emptyText: t('subscription.empty') }}
            pagination={false}
            rowKey={(record) => record.productId}
            size="small"
          />
        </div>
      </div>

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
              {t('common:cancel')}
            </Button>
            <Button
              disabled={!selectedProduct}
              loading={subscribeLoading}
              onClick={handleSubscribeProducts}
              type="primary"
            >
              {t('subscription.confirmSubscribe')}
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
        title={t('subscription.selectTitle')}
        width={500}
      >
        <div>
          <div className="text-sm text-gray-700 mb-3 font-medium">
            {t('subscription.selectLabel')}
          </div>
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
            notFoundContent={productLoading ? t('common:loading') : t('subscription.noProducts')}
            onChange={setSelectedProduct}
            placeholder={t('subscription.selectPlaceholder')}
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
