import { ArrowLeftOutlined } from '@ant-design/icons';
import { Alert } from 'antd';
import { useNavigate } from 'react-router-dom';

import { Layout } from './Layout';
import { DetailSkeleton } from './loading';
import { ProductHeader } from './ProductHeader';

import type { ProductHeaderHandle } from './ProductHeader';
import type { IProductIcon, IMCPConfig, IAgentConfig } from '../lib/apis/typing';
import type { ReactNode, Ref } from 'react';

export interface ProductDetailHeaderProps {
  name: string;
  description: string;
  icon?: IProductIcon;
  defaultIcon?: string;
  mcpConfig?: IMCPConfig;
  agentConfig?: IAgentConfig;
  updatedAt?: string;
  productType?: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API' | 'AGENT_SKILL';
  subscribable?: boolean;
  ref?: Ref<ProductHeaderHandle>;
  onSubscriptionStatusChange?: (subscribed: boolean) => void;
}

export interface ProductDetailLayoutProps {
  leftContent: ReactNode;
  rightContent: ReactNode;
  headerProps?: ProductDetailHeaderProps;
  loading?: boolean;
  error?: string;
  onBack?: () => void;
}

export function ProductDetailLayout({
  error,
  headerProps,
  leftContent,
  loading,
  onBack,
  rightContent,
}: ProductDetailLayoutProps) {
  const navigate = useNavigate();

  if (loading) {
    return (
      <Layout>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <DetailSkeleton />
        </div>
      </Layout>
    );
  }

  if (error) {
    return (
      <Layout>
        <div className="p-8">
          <Alert description={error} message="错误" showIcon type="error" />
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      {/* 头部 */}
      <div className="mb-8">
        {/* 返回按钮 */}
        <button
          className="
            flex items-center gap-2 mb-4 px-4 py-2 rounded-[10px]
            text-gray-600 hover:text-colorPrimary
            hover:bg-colorPrimaryBgHover
            transition-all duration-200
          "
          onClick={onBack || (() => navigate(-1))}
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </button>

        {headerProps && <ProductHeader {...headerProps} />}
      </div>

      {/* 主要内容区域 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,65fr)_minmax(0,35fr)]">
        {/* 左侧内容 - 65% */}
        <div className="order-2 min-w-0 lg:order-1">{leftContent}</div>
        {/* 右侧内容 - 35% */}
        <div className="order-1 min-w-0 lg:order-2">{rightContent}</div>
      </div>
    </Layout>
  );
}
