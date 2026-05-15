import {
  ApiOutlined,
  GlobalOutlined,
  TeamOutlined,
  EditOutlined,
  CheckCircleFilled,
  MinusCircleFilled,
  CopyOutlined,
  ExclamationCircleFilled,
  ClockCircleFilled,
} from '@ant-design/icons';
import { Card, Row, Col, Statistic, Button, message } from 'antd';
import { useState, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { apiProductApi } from '@/lib/api';
import { getProductCategories } from '@/lib/productCategoryApi';
import { getServiceName, formatDateTime, copyToClipboard } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';
import type { LinkedService } from '@/types/api-product';
import type { ProductCategory } from '@/types/product-category';

interface ApiProductOverviewProps {
  apiProduct: ApiProduct;
  linkedService: LinkedService | null;
  onEdit: () => void;
}

export function ApiProductOverview({ apiProduct, linkedService, onEdit }: ApiProductOverviewProps) {
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
        <h1 className="text-2xl font-bold mb-2">概览</h1>
        <p className="text-gray-600">API产品概览</p>
      </div>

      {/* 基本信息 */}
      <Card
        extra={
          <Button icon={<EditOutlined />} onClick={onEdit} type="primary">
            编辑
          </Button>
        }
        title="基本信息"
      >
        <div>
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">产品名称:</span>
            <span className="col-span-2 text-xs text-gray-900">{apiProduct.name}</span>
            <span className="text-xs text-gray-600">产品ID:</span>
            <div className="col-span-2 flex items-center gap-2">
              <span className="text-xs text-gray-700">{apiProduct.productId}</span>
              <CopyOutlined
                className="text-gray-400 hover:text-blue-600 cursor-pointer transition-colors ml-1"
                onClick={async () => {
                  try {
                    await copyToClipboard(apiProduct.productId);
                    message.success('产品ID已复制');
                  } catch {
                    message.error('复制失败，请手动复制');
                  }
                }}
                style={{ fontSize: '12px' }}
              />
            </div>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">类型:</span>
            <span className="col-span-2 text-xs text-gray-900">
              {apiProduct.type === 'REST_API'
                ? 'REST API'
                : apiProduct.type === 'AGENT_API'
                  ? 'Agent API'
                  : apiProduct.type === 'MODEL_API'
                    ? 'Model API'
                    : apiProduct.type === 'AGENT_SKILL'
                      ? 'Agent Skill'
                      : apiProduct.type === 'WORKER'
                        ? 'Worker'
                        : 'MCP Server'}
            </span>
            <span className="text-xs text-gray-600">状态:</span>
            <div className="col-span-2 flex items-center">
              {apiProduct.status === 'PENDING' ? (
                <ExclamationCircleFilled
                  className="text-yellow-500 mr-2"
                  style={{ fontSize: '10px' }}
                />
              ) : apiProduct.status === 'READY' ? (
                <ClockCircleFilled className="text-blue-500 mr-2" style={{ fontSize: '10px' }} />
              ) : (
                <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '10px' }} />
              )}
              <span className="text-xs text-gray-900">
                {apiProduct.status === 'PENDING'
                  ? '待配置'
                  : apiProduct.status === 'READY'
                    ? '待发布'
                    : '已发布'}
              </span>
            </div>
          </div>

          {apiProduct.type !== 'AGENT_SKILL' && apiProduct.type !== 'WORKER' ? (
            <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
              <span className="text-xs text-gray-600">自动审批订阅:</span>
              <div className="col-span-2 flex items-center">
                {apiProduct.autoApprove === true ? (
                  <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '10px' }} />
                ) : (
                  <MinusCircleFilled className="text-gray-400 mr-2" style={{ fontSize: '10px' }} />
                )}
                <span className="text-xs text-gray-900">
                  {apiProduct.autoApprove === true ? '已开启' : '已关闭'}
                </span>
              </div>
              <span className="text-xs text-gray-600">创建时间:</span>
              <span className="col-span-2 text-xs text-gray-700">
                {formatDateTime(apiProduct.createAt)}
              </span>
            </div>
          ) : (
            <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
              <span className="text-xs text-gray-600">创建时间:</span>
              <span className="col-span-2 text-xs text-gray-700">
                {formatDateTime(apiProduct.createAt)}
              </span>
            </div>
          )}

          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">产品类别:</span>
            <div className="col-span-2 text-xs text-gray-900">
              {productCategories && productCategories.length > 0 ? (
                <span>
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
                </span>
              ) : (
                <span className="text-gray-400">-</span>
              )}
            </div>
            {/* Feature 配置 - 仅 MODEL_API 类型显示 */}
            {apiProduct.type === 'MODEL_API' && (
              <>
                <span className="text-xs text-gray-600">模型参数:</span>
                <div className="col-span-2 text-xs text-gray-700">
                  {apiProduct.feature?.modelFeature ? (
                    <span>
                      {[
                        apiProduct.feature.modelFeature.model,
                        apiProduct.feature.modelFeature.maxTokens &&
                          `${apiProduct.feature.modelFeature.maxTokens} tokens`,
                        apiProduct.feature.modelFeature.temperature !== null &&
                          apiProduct.feature.modelFeature.temperature !== undefined &&
                          `temperature ${apiProduct.feature.modelFeature.temperature}`,
                        apiProduct.feature.modelFeature.webSearch && '联网搜索',
                        apiProduct.feature.modelFeature.enableThinking && '深度思考',
                        apiProduct.feature.modelFeature.enableMultiModal && '多模态',
                      ]
                        .filter(Boolean)
                        .map((param, index, array) => (
                          <span key={index}>
                            <span className="text-gray-900">{param}</span>
                            {index < array.length - 1 && (
                              <span className="text-gray-400 mx-2">|</span>
                            )}
                          </span>
                        ))}
                    </span>
                  ) : (
                    <span className="text-gray-400">-</span>
                  )}
                </div>
              </>
            )}
          </div>

          {apiProduct.description && (
            <div className="grid grid-cols-6 gap-8 pt-2 pb-2">
              <span className="text-xs text-gray-600">描述:</span>
              <span className="col-span-5 text-xs text-gray-700 leading-relaxed">
                {apiProduct.description}
              </span>
            </div>
          )}
        </div>
      </Card>

      {/* 统计数据 - AGENT_SKILL 不展示 */}
      {apiProduct.type !== 'AGENT_SKILL' && (
        <Row gutter={[16, 16]}>
          <Col lg={8} sm={12} xs={24}>
            <Card
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => {
                navigate(`/api-products/${apiProduct.productId}?tab=portal`, {
                  state: location.state,
                });
              }}
            >
              <Statistic
                prefix={<GlobalOutlined className="text-blue-500" />}
                title="发布的门户"
                value={portalCount}
                valueStyle={{ color: '#1677ff', fontSize: '24px' }}
              />
            </Card>
          </Col>
          <Col lg={8} sm={12} xs={24}>
            <Card
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => {
                navigate(`/api-products/${apiProduct.productId}?tab=link-api`, {
                  state: location.state,
                });
              }}
            >
              <Statistic
                prefix={<ApiOutlined className="text-blue-500" />}
                title="关联API"
                value={getServiceName(linkedService) || '未关联'}
                valueStyle={{ color: '#1677ff', fontSize: '24px' }}
              />
            </Card>
          </Col>
          <Col lg={8} sm={12} xs={24}>
            <Card className="hover:shadow-md transition-shadow">
              <Statistic
                prefix={<TeamOutlined className="text-blue-500" />}
                title="订阅用户"
                value={subscriberCount}
                valueStyle={{ color: '#1677ff', fontSize: '24px' }}
              />
            </Card>
          </Col>
        </Row>
      )}
    </div>
  );
}
