import React, { useState, useEffect } from 'react';
import { Card, Spin, Alert, Button, Space } from 'antd';
import { ReloadOutlined, DashboardOutlined } from '@ant-design/icons';
import { apiProductApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';

interface ApiProductDashboardProps {
  apiProduct: ApiProduct;
}

export const ApiProductDashboard: React.FC<ApiProductDashboardProps> = ({ apiProduct }) => {
  const [dashboardUrl, setDashboardUrl] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');

  // 获取Dashboard URL
  const fetchDashboardUrl = async () => {
    if (!apiProduct.productId) return;
    
    setLoading(true);
    setError('');
    
    try {
      // 直接调用产品的dashboard接口获取监控面板URL
      const response = await apiProductApi.getProductDashboard(apiProduct.productId);
      setDashboardUrl(response.data);
    } catch (err: any) {
      setError(err.response?.data?.message || '获取监控面板失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (apiProduct.productId) {
      fetchDashboardUrl();
    }
  }, [apiProduct.productId]);

  const handleRefresh = () => {
    fetchDashboardUrl();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spin size="large" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <Alert
          message="获取监控面板失败"
          description={error}
          type="error"
          showIcon
          action={
            <Button size="small" onClick={handleRefresh}>
              重试
            </Button>
          }
        />
      </div>
    );
  }

  if (!dashboardUrl) {
    return (
      <div className="p-6">
        <Alert
          message="暂无监控数据"
          description="该产品尚未配置监控面板，请联系管理员配置。"
          type="info"
          showIcon
        />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* 标题和操作 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold flex items-center gap-2">
            <DashboardOutlined className="text-blue-500" />
            Dashboard 监控面板
          </h2>
          <p className="text-gray-500 mt-2">
            实时监控 {apiProduct.name} 的API调用情况和性能指标
          </p>
        </div>
        <Space>
          <Button 
            icon={<ReloadOutlined />} 
            onClick={handleRefresh}
            loading={loading}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* Dashboard嵌入区域 */}
      <Card title="监控面板" className="w-full">
        <div className="w-full h-[600px] border rounded-lg overflow-hidden">
          {dashboardUrl ? (
            <iframe
              src={dashboardUrl}
              title={`${apiProduct.name} Dashboard`}
              className="w-full h-full border-0"
              sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
            />
          ) : (
            <div className="flex items-center justify-center h-full text-gray-500">
              加载监控面板中...
            </div>
          )}
        </div>
      </Card>


    </div>
  );
};
