import { ArrowLeftOutlined } from '@ant-design/icons';
import { Alert } from 'antd';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation('common');

  if (loading) {
    return (
      <Layout>
        <div className="mx-auto w-full max-w-[1480px] py-5 sm:py-7">
          <DetailSkeleton />
        </div>
      </Layout>
    );
  }

  if (error) {
    return (
      <Layout>
        <div className="p-8">
          <Alert description={error} message={t('errorTitle')} showIcon type="error" />
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="mx-auto w-full max-w-[1480px] py-5 sm:py-7">
        <div className="mb-5">
          <button
            className="
              mb-4 inline-flex h-9 items-center gap-2 rounded-[10px] px-3
              text-sm font-medium text-gray-600 transition-all duration-200
              hover:bg-white/80 hover:text-gray-950 hover:shadow-sm
              focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-colorPrimary/30
              active:translate-y-px
            "
            onClick={onBack || (() => navigate(-1))}
          >
            <ArrowLeftOutlined className="text-xs" />
            <span>{t('back')}</span>
          </button>

          {headerProps && <ProductHeader {...headerProps} />}
        </div>

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_390px] xl:items-start">
          <div className="order-2 min-w-0 xl:order-1">{leftContent}</div>
          <div className="order-1 min-w-0 xl:sticky xl:top-24 xl:order-2 xl:self-start">
            {rightContent}
          </div>
        </div>
      </div>
    </Layout>
  );
}
