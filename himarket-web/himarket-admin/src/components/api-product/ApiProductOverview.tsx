import {
  ApiOutlined,
  GlobalOutlined,
  TeamOutlined,
  EditOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import { Card, Button, message } from 'antd';
import { useState, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { StatusIndicator } from '@/components/common';
import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi } from '@/lib/api';
import { getProductCategories } from '@/lib/productCategoryApi';
import { getServiceName, formatDateTime, copyToClipboard } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';
import type { LinkedService } from '@/types/api-product';
import type { ProductCategory } from '@/types/product-category';

import type { ReactNode } from 'react';

interface ApiProductOverviewProps {
  apiProduct: ApiProduct;
  linkedService: LinkedService | null;
  onEdit: () => void;
}

function getProductTypeLabel(type: ApiProduct['type']) {
  switch (type) {
    case 'REST_API':
      return 'REST API';
    case 'AGENT_API':
      return 'Agent API';
    case 'MODEL_API':
      return 'Model API';
    case 'AGENT_SKILL':
      return 'Agent Skill';
    case 'WORKER':
      return 'Worker';
    default:
      return 'MCP Server';
  }
}

function renderProductStatus(status: ApiProduct['status'], t: ReturnType<typeof useLocale>['t']) {
  if (status === 'PENDING') {
    return <StatusIndicator tone="warning">{t('product.overview.statusPending')}</StatusIndicator>;
  }
  if (status === 'READY') {
    return (
      <StatusIndicator icon="clock" tone="info">
        {t('product.overview.statusReady')}
      </StatusIndicator>
    );
  }
  return <StatusIndicator tone="success">{t('product.overview.statusPublished')}</StatusIndicator>;
}

function InfoItem({
  children,
  label,
  wide,
}: {
  children: ReactNode;
  label: string;
  wide?: boolean;
}) {
  return (
    <div className={wide ? 'md:col-span-2' : undefined}>
      <dt className="text-xs font-medium text-gray-500">{label}</dt>
      <dd className="mt-1 min-w-0 text-sm leading-6 text-gray-900">{children}</dd>
    </div>
  );
}

function MetricCard({
  icon,
  label,
  onClick,
  value,
}: {
  icon: ReactNode;
  label: string;
  onClick?: () => void;
  value: ReactNode;
}) {
  const content = (
    <>
      <div className="text-sm text-gray-500">{label}</div>
      <div className="mt-3 flex items-end gap-2">
        <span className="text-xl text-blue-500">{icon}</span>
        <span className="min-w-0 truncate text-2xl font-semibold leading-none text-blue-600">
          {value}
        </span>
      </div>
    </>
  );

  if (onClick) {
    return (
      <button
        className="rounded-lg border border-gray-200 bg-white p-4 text-left shadow-sm transition-all duration-150 hover:border-blue-200 hover:shadow-md"
        onClick={onClick}
        type="button"
      >
        {content}
      </button>
    );
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4 text-left shadow-sm transition-all duration-150">
      {content}
    </div>
  );
}

export function ApiProductOverview({ apiProduct, linkedService, onEdit }: ApiProductOverviewProps) {
  const { t } = useLocale();
  const [portalCount, setPortalCount] = useState(0);
  const [subscriberCount, setSubscriberCount] = useState(0);
  const [productCategories, setProductCategories] = useState<ProductCategory[]>([]);

  const navigate = useNavigate();
  const location = useLocation();

  const { productId, type } = apiProduct;
  const lastFetchedKeyRef = useRef<string>('');

  useEffect(() => {
    if (!productId) {
      return;
    }

    const key = `${productId}-${type}`;
    if (lastFetchedKeyRef.current === key) {
      return;
    }
    lastFetchedKeyRef.current = key;

    const fetchPublishedPortals = async () => {
      try {
        const res = await apiProductApi.getApiProductPublications(productId);
        setPortalCount(res.data.content?.length || 0);
      } catch {
        // ignore
      }
    };

    const fetchSubscriberCount = async () => {
      try {
        const res = await apiProductApi.getProductSubscriptions(productId, {
          page: 1,
          size: 1,
        });
        setSubscriberCount(res.data.totalElements || 0);
      } catch {
        // ignore
      }
    };

    const fetchProductCategories = async () => {
      try {
        const res = await apiProductApi.getProductCategories(productId);
        const categoriesData = res.data as unknown;

        if (!Array.isArray(categoriesData) || categoriesData.length === 0) {
          setProductCategories([]);
          return;
        }

        const allCategoriesRes = await getProductCategories();
        const allCategories = allCategoriesRes.data.content || allCategoriesRes.data || [];

        const categoriesWithNames = categoriesData.map((category: unknown) => {
          const cat = category as { categoryId?: string; id?: string };
          const categoryId = cat.categoryId || cat.id;
          const fullCategoryInfo = allCategories.find(
            (c: ProductCategory) => c.categoryId === categoryId || c.id === categoryId,
          );
          return fullCategoryInfo || (cat as ProductCategory);
        });

        setProductCategories(categoriesWithNames);
      } catch (_error) {
        console.error('获取产品类别失败:', _error);
        setProductCategories([]);
      }
    };

    fetchPublishedPortals();
    fetchProductCategories();
    fetchSubscriberCount();
  }, [productId, type]);

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">{t('product.overview.title')}</h1>
        <p className="text-gray-600">{t('product.overview.description')}</p>
      </div>

      {/* 基本信息 */}
      <Card
        className="rounded-lg border border-gray-200 shadow-sm"
        extra={
          <Button icon={<EditOutlined />} onClick={onEdit} type="primary">
            {t('product.overview.edit')}
          </Button>
        }
        title={t('product.overview.basicInfo')}
      >
        <dl className="grid gap-x-8 gap-y-5 md:grid-cols-2">
          <InfoItem label={t('product.overview.name')}>{apiProduct.name}</InfoItem>
          <InfoItem label={t('product.overview.id')}>
            <div className="flex min-w-0 items-center gap-2">
              <span className="truncate text-gray-700">{apiProduct.productId}</span>
              <CopyOutlined
                className="text-gray-400 hover:text-blue-600 cursor-pointer transition-colors ml-1"
                onClick={async () => {
                  try {
                    await copyToClipboard(apiProduct.productId);
                    message.success(t('product.overview.copied'));
                  } catch {
                    message.error(t('common.copyFailed'));
                  }
                }}
                style={{ fontSize: '12px' }}
              />
            </div>
          </InfoItem>

          <InfoItem label={t('product.overview.type')}>
            {getProductTypeLabel(apiProduct.type)}
          </InfoItem>
          <InfoItem label={t('product.overview.status')}>
            {renderProductStatus(apiProduct.status, t)}
          </InfoItem>

          {apiProduct.type !== 'AGENT_SKILL' && apiProduct.type !== 'WORKER' ? (
            <>
              <InfoItem label={t('product.overview.autoApproveSubscription')}>
                <StatusIndicator tone={apiProduct.autoApprove === true ? 'success' : 'neutral'}>
                  {apiProduct.autoApprove === true
                    ? t('product.overview.enabled')
                    : t('product.overview.disabled')}
                </StatusIndicator>
              </InfoItem>
              <InfoItem label={t('product.overview.createAt')}>
                {formatDateTime(apiProduct.createAt)}
              </InfoItem>
            </>
          ) : (
            <InfoItem label={t('product.overview.createAt')}>
              {formatDateTime(apiProduct.createAt)}
            </InfoItem>
          )}

          <InfoItem label={t('product.overview.category')} wide={apiProduct.type !== 'MODEL_API'}>
            <div className="flex flex-wrap gap-x-2 gap-y-1">
              {productCategories && productCategories.length > 0 ? (
                <>
                  {productCategories.map((category, index) => (
                    <span key={category.categoryId}>
                      <span
                        className="text-gray-900 hover:text-blue-600 cursor-pointer hover:underline transition-colors"
                        onClick={() => navigate(`/product-categories/${category.categoryId}`)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            navigate(`/product-categories/${category.categoryId}`);
                          }
                        }}
                        role="button"
                        tabIndex={0}
                      >
                        {category.name}
                      </span>
                      {index < productCategories.length - 1 && (
                        <span className="text-gray-400 mx-2">|</span>
                      )}
                    </span>
                  ))}
                </>
              ) : (
                <span className="text-gray-400">-</span>
              )}
            </div>
          </InfoItem>

          {apiProduct.type === 'MODEL_API' && (
            <InfoItem label={t('product.overview.modelParams')}>
              {apiProduct.feature?.modelFeature ? (
                <span>
                  {[
                    apiProduct.feature.modelFeature.model,
                    apiProduct.feature.modelFeature.maxTokens &&
                      `${apiProduct.feature.modelFeature.maxTokens} tokens`,
                    apiProduct.feature.modelFeature.temperature !== null &&
                      apiProduct.feature.modelFeature.temperature !== undefined &&
                      `temperature ${apiProduct.feature.modelFeature.temperature}`,
                    apiProduct.feature.modelFeature.webSearch && t('product.overview.webSearch'),
                    apiProduct.feature.modelFeature.enableThinking &&
                      t('product.overview.thinking'),
                    apiProduct.feature.modelFeature.enableMultiModal &&
                      t('product.overview.multimodal'),
                  ]
                    .filter(Boolean)
                    .map((param, index, array) => (
                      <span key={index}>
                        <span className="text-gray-900">{param}</span>
                        {index < array.length - 1 && <span className="text-gray-400 mx-2">|</span>}
                      </span>
                    ))}
                </span>
              ) : (
                <span className="text-gray-400">-</span>
              )}
            </InfoItem>
          )}

          {apiProduct.description && (
            <InfoItem label={t('product.overview.detail')} wide>
              <span className="text-gray-700">{apiProduct.description}</span>
            </InfoItem>
          )}
        </dl>
      </Card>

      {/* 统计数据 - AGENT_SKILL 不展示 */}
      {apiProduct.type !== 'AGENT_SKILL' && (
        <div className="grid gap-4 md:grid-cols-3">
          <MetricCard
            icon={<GlobalOutlined />}
            label={t('product.overview.publishedPortals')}
            onClick={() => {
              navigate(`/api-products/${apiProduct.productId}?tab=portal`, {
                state: location.state,
              });
            }}
            value={portalCount}
          />
          <MetricCard
            icon={<ApiOutlined />}
            label={t('product.overview.linkedApi')}
            onClick={() => {
              navigate(`/api-products/${apiProduct.productId}?tab=link-api`, {
                state: location.state,
              });
            }}
            value={getServiceName(linkedService) || t('product.overview.unlinked')}
          />
          <MetricCard
            icon={<TeamOutlined />}
            label={t('product.overview.subscribers')}
            value={subscriberCount}
          />
        </div>
      )}
    </div>
  );
}
