import { useState } from "react";
import {
  Card,
  Button,
  message,
  Input,
  Drawer,
  Table,
  Badge,
  Popconfirm,
  Select,
} from "antd";
import {
  PlusOutlined,
} from "@ant-design/icons";
import api from "../../lib/api";
import type { Subscription } from "../../types/consumer";
import type { ApiResponse, Product } from "../../types";
import { getSubscriptionStatusText, getSubscriptionStatusColor } from "../../lib/statusUtils";
import { formatDateTime } from "../../lib/utils";

interface SubscriptionManagerProps {
  consumerId: string;
  subscriptions: Subscription[];
  onSubscriptionsChange: (searchParams?: { productName: string; status: string }) => void;
}

export function SubscriptionManager({ consumerId, subscriptions, onSubscriptionsChange }: SubscriptionManagerProps) {
  const [productDrawerVisible, setProductDrawerVisible] = useState(false);
  const [products, setProducts] = useState<Product[]>([]);
  const [productLoading, setProductLoading] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<string>('');
  const [subscriptionSearch, setSubscriptionSearch] = useState({ productName: '', status: '' as 'PENDING' | 'APPROVED' | '' });

  const openProductDrawer = async () => {
    setProductDrawerVisible(true);
    setProductLoading(true);
    try {
      const response: ApiResponse<{ content: Product[] }> = await api.get("/products?page=0&size=100");
      if (response?.code === "SUCCESS" && response?.data) {
        setProducts(response.data.content || []);
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

    try {
      await api.post(`/consumers/${consumerId}/subscriptions`, { productId: selectedProduct });
      message.success('订阅成功');
      setProductDrawerVisible(false);
      setSelectedProduct('');
      onSubscriptionsChange();
    } catch (error) {
      console.error('订阅失败:', error);
      // message.error('订阅失败');
    }
  };

  const handleUnsubscribe = async (productId: string) => {
    try {
      await api.delete(`/consumers/${consumerId}/subscriptions/${productId}`);
      message.success('取消订阅成功');
      onSubscriptionsChange();
    } catch (error) {
      console.error('取消订阅失败:', error);
      // message.error('取消订阅失败');
    }
  };



  const subscriptionColumns = [
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName',
      render: (productName: Product['productName']) => productName || '-',
    },
    {
      title: '产品类型',
      dataIndex: 'productType',
      key: 'productType',
      render: (productType: Product['productType']) => {
        const typeMap = {
          'REST_API': 'REST API',
          'HTTP_API': 'HTTP API',
          'MCP_SERVER': 'MCP Server'
        };
        return typeMap[productType as keyof typeof typeMap] || productType || '-';
      }
    },
    {
      title: '订阅状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge status={getSubscriptionStatusColor(status) as 'success' | 'processing' | 'error' | 'default' | 'warning'} text={getSubscriptionStatusText(status)} />
      ),
    },
    {
      title: '订阅时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => date ? formatDateTime(date) : '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (record: Subscription) => (
        <Popconfirm
          title="确定要取消订阅吗？"
          onConfirm={() => handleUnsubscribe(record.productId)}
        >
          <Button type="link" danger size="small">
            取消订阅
          </Button>
        </Popconfirm>
      ),
    },
  ];

  // 确保 subscriptions 始终是数组
  const safeSubscriptions = Array.isArray(subscriptions) ? subscriptions : [];

  return (
    <>
      <Card>
        <div className="mb-4 flex justify-between items-center">
          <div className="flex space-x-4">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openProductDrawer}
            >
              订阅
            </Button>
            <Input.Search
              placeholder="请输入API名称进行搜索"
              style={{ width: 300 }}
              onSearch={(value) => {
                const newSearch = { ...subscriptionSearch, productName: value };
                setSubscriptionSearch(newSearch);
                onSubscriptionsChange(newSearch);
              }}
            />
            <Select
              placeholder="订阅状态"
              style={{ width: 120 }}
              allowClear
              value={subscriptionSearch.status || undefined}
              onChange={(value) => {
                const newSearch = { ...subscriptionSearch, status: value as 'PENDING' | 'APPROVED' | '' };
                setSubscriptionSearch(newSearch);
                onSubscriptionsChange(newSearch);
              }}
            >
              <Select.Option value="PENDING">待审批</Select.Option>
              <Select.Option value="APPROVED">已通过</Select.Option>
            </Select>
          </div>
        </div>
        <Table
          columns={subscriptionColumns}
          dataSource={safeSubscriptions}
          rowKey={(record) => record.productId}
          pagination={false}
          size="small"
          locale={{ emptyText: '暂无订阅记录，请点击上方按钮进行订阅' }}
        />
      </Card>

      {/* 产品选择抽屉 */}
      <Drawer
        title="订阅"
        placement="right"
        width={600}
        open={productDrawerVisible}
        onClose={() => {
          setProductDrawerVisible(false);
          setSelectedProduct('');
        }}
        footer={
          <div className="flex justify-end space-x-2">
            <Button
              onClick={() => {
                setProductDrawerVisible(false);
                setSelectedProduct('');
              }}
            >
              取消
            </Button>
            <Button
              type="primary"
              onClick={handleSubscribeProducts}
              disabled={!selectedProduct}
            >
              确定订阅
            </Button>
          </div>
        }
      >
        <div className="mb-4">
          <div className="text-sm text-gray-600 mb-2">选择要订阅的产品：</div>
          <Select
            placeholder="请选择产品"
            style={{ width: '100%'}}
            value={selectedProduct}
            onChange={setSelectedProduct}
            loading={productLoading}
            showSearch
            // filterOption={(input, option) => {
            //   const product = option?.data as Product;
            //   return product?.name?.toLowerCase().includes(input.toLowerCase()) ||
            //          product?.description?.toLowerCase().includes(input.toLowerCase());
            // }}
            // optionFilterProp="children"
          >
            {products.map(product => (
              <Select.Option key={product.productId} value={product.productId}>
                {product.name}
              </Select.Option>
            ))}
          </Select>
        </div>
      </Drawer>
    </>
  );
} 