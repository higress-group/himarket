import {
  ArrowLeftOutlined,
  EditOutlined,
  FolderOutlined,
  ExclamationCircleFilled,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  Button,
  Card,
  Skeleton,
  Empty,
  Divider,
  message,
  Modal,
  Tooltip,
  Checkbox,
  Pagination,
} from 'antd';
import { useState, useEffect, useCallback, useRef } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { DataTable } from '@/components/common/DataTable';
import { StatusIndicator } from '@/components/common/StatusIndicator';
import { ProductIconRenderer } from '@/components/icons/ProductIconRenderer';
import AddProductModal from '@/components/product-category/AddProductModal';
import CategoryFormModal from '@/components/product-category/CategoryFormModal';
import { useLocale } from '@/contexts/LocaleContext';
import { useAdminViewMode } from '@/hooks/useAdminViewMode';
import { apiProductApi } from '@/lib/api';
import { getIconString } from '@/lib/iconUtils';
import { getProductCategory, unbindProductsFromCategory } from '@/lib/productCategoryApi';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';
import type { ProductCategory } from '@/types/product-category';

import type { TableProps } from 'antd';

const API_PRODUCTS_VIEW_MODE_SETTING_KEY = 'api-products.view-mode';

function renderStatusTag(status: string, t: ReturnType<typeof useLocale>['t']) {
  if (status === 'PUBLISHED') {
    return (
      <StatusIndicator tone="success">{t('product.overview.statusPublished')}</StatusIndicator>
    );
  }
  if (status === 'READY') {
    return (
      <StatusIndicator icon="clock" tone="info">
        {t('product.overview.statusReady')}
      </StatusIndicator>
    );
  }
  if (status === 'PENDING') {
    return <StatusIndicator tone="warning">{t('product.overview.statusPending')}</StatusIndicator>;
  }
  return <span className="text-xs text-gray-700">{status}</span>;
}

function getTypeLabel(type: string) {
  switch (type) {
    case 'REST_API':
      return 'REST API';
    case 'MCP_SERVER':
      return 'MCP Server';
    case 'AGENT_API':
      return 'Agent API';
    case 'AGENT_SKILL':
      return 'Agent Skill';
    case 'MODEL_API':
      return 'Model API';
    case 'WORKER':
      return 'Worker';
    default:
      return type;
  }
}

interface CategoryProductCardProps {
  checked: boolean;
  onOpenDetail: (productId: string) => void;
  onRemove: (product: ApiProduct) => void;
  onToggleSelect: (productId: string, checked: boolean) => void;
  product: ApiProduct;
}

function CategoryProductCard({
  checked,
  onOpenDetail,
  onRemove,
  onToggleSelect,
  product,
}: CategoryProductCardProps) {
  const { t } = useLocale();

  return (
    <article
      className={`group flex min-h-[184px] cursor-pointer flex-col rounded-lg border bg-white p-4 shadow-sm transition-all duration-150 hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-md ${
        checked ? 'border-blue-300 ring-1 ring-blue-100' : 'border-gray-200'
      }`}
      onClick={() => onOpenDetail(product.productId)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onOpenDetail(product.productId);
        }
      }}
      role="button"
      tabIndex={0}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-blue-50">
            <ProductIconRenderer
              className="h-8 w-8"
              iconType={getIconString(product.icon)}
              type={product.type}
            />
          </div>
          <div className="min-w-0">
            <Tooltip placement="topLeft" title={product.name}>
              <h3 className="truncate text-sm font-semibold text-gray-900">{product.name}</h3>
            </Tooltip>
            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
              <span className="text-xs text-gray-500">{getTypeLabel(product.type)}</span>
              {renderStatusTag(product.status, t)}
            </div>
          </div>
        </div>
        <Checkbox
          checked={checked}
          onChange={(event) => onToggleSelect(product.productId, event.target.checked)}
          onClick={(event) => event.stopPropagation()}
        />
      </div>

      <Tooltip placement="topLeft" title={product.description}>
        <p className="mt-4 line-clamp-2 min-h-[40px] text-xs leading-5 text-gray-600">
          {product.description || t('common.noDescription')}
        </p>
      </Tooltip>

      <div className="mt-auto flex items-end justify-between gap-3 pt-4">
        <div className="min-w-0 text-xs text-gray-400">
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="block max-w-[180px] truncate border-none bg-transparent p-0 text-left text-xs text-gray-400 hover:text-blue-500"
              onClick={(event) => {
                event.stopPropagation();
                copyToClipboard(product.productId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                });
              }}
              type="button"
            >
              {product.productId}
            </button>
          </Tooltip>
          <span>{product.createAt ? formatDateTime(product.createAt) : '-'}</span>
        </div>
        <div className="flex shrink-0 translate-y-1 items-center gap-1 opacity-0 transition-all duration-150 group-hover:translate-y-0 group-hover:opacity-100 group-focus:translate-y-0 group-focus:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100">
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={(event) => {
              event.stopPropagation();
              onRemove(product);
            }}
            size="small"
            type="text"
          >
            {t('common.remove')}
          </Button>
        </div>
      </div>
    </article>
  );
}

export default function ProductCategoryDetail() {
  const { t } = useLocale();
  const { categoryId } = useParams<{ categoryId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const lastAutoFetchKeyRef = useRef('');
  const currentPath = `${location.pathname}${location.search}${location.hash}`;
  const { viewMode } = useAdminViewMode(API_PRODUCTS_VIEW_MODE_SETTING_KEY);

  const [category, setCategory] = useState<ProductCategory | null>(null);
  const [products, setProducts] = useState<ApiProduct[]>([]);
  const [categoryLoading, setCategoryLoading] = useState(true);
  const [productsLoading, setProductsLoading] = useState(true);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [addModalVisible, setAddModalVisible] = useState(false);
  const [selectedProductIds, setSelectedProductIds] = useState<Set<string>>(new Set());
  const [removeLoading, setRemoveLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  // 获取类别详情
  const fetchCategoryDetail = useCallback(async () => {
    if (!categoryId) return;

    try {
      setCategoryLoading(true);
      const response = await getProductCategory(categoryId);
      setCategory(response.data);
    } catch (error) {
      console.error('获取类别详情失败:', error);
      message.error(t('page.categoryDetail.fetchDetailFailed'));
    } finally {
      setCategoryLoading(false);
    }
  }, [categoryId, t]);

  // 获取该类别下的产品
  const fetchCategoryProducts = useCallback(
    async (page = 1, size = 10) => {
      if (!categoryId) return;

      try {
        setProductsLoading(true);
        const response = await apiProductApi.getApiProducts({
          categoryIds: categoryId,
          page,
          size,
        });
        setProducts(response.data.content || []);
        setPagination({
          current: response.data.number ?? page,
          pageSize: response.data.size ?? size,
          total: response.data.totalElements ?? 0,
        });
      } catch (error) {
        console.error('获取类别产品失败:', error);
        message.error(t('page.categoryDetail.fetchProductsFailed'));
      } finally {
        setProductsLoading(false);
      }
    },
    [categoryId, t],
  );

  useEffect(() => {
    if (!categoryId) {
      return;
    }

    const key = `${categoryId}-1-10`;
    if (lastAutoFetchKeyRef.current === key) {
      return;
    }
    lastAutoFetchKeyRef.current = key;
    fetchCategoryDetail();
    fetchCategoryProducts();
  }, [categoryId, fetchCategoryDetail, fetchCategoryProducts]);

  // 渲染类别图标
  const renderCategoryIcon = (category: ProductCategory, size: number = 64) => {
    if (!category.icon) {
      return (
        <div
          className="flex items-center justify-center rounded-lg bg-gradient-to-br from-gray-100 to-gray-200 shadow-sm"
          style={{ height: size, width: size }}
        >
          <FolderOutlined style={{ color: '#666', fontSize: size * 0.4 }} />
        </div>
      );
    }

    if (category.icon.type === 'URL') {
      return (
        <img
          alt={category.name}
          className="rounded-lg object-cover shadow-sm"
          onError={(e) => {
            e.currentTarget.outerHTML = `
              <div class="w-16 h-16 rounded-lg bg-gradient-to-br from-gray-100 to-gray-200 flex items-center justify-center shadow-sm">
                <svg class="w-6 h-6 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M10 4H4c-1.11 0-2 .89-2 2v12c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2h-8l-2-2z"/>
                </svg>
              </div>
            `;
          }}
          src={category.icon.value}
          style={{ height: size, width: size }}
        />
      );
    } else {
      // BASE64 类型，可能是emoji或图片
      if (category.icon.value.length <= 10 && /\p{Emoji}/u.test(category.icon.value)) {
        // 是emoji
        return (
          <div
            className="flex items-center justify-center rounded-lg bg-gradient-to-br from-blue-50 to-indigo-50 shadow-sm"
            style={{ fontSize: size * 0.4, height: size, width: size }}
          >
            {category.icon.value}
          </div>
        );
      } else {
        // 是base64图片
        return (
          <img
            alt={category.name}
            className="rounded-lg object-cover shadow-sm"
            src={category.icon.value}
            style={{ height: size, width: size }}
          />
        );
      }
    }
  };

  // 编辑成功回调
  const handleEditSuccess = () => {
    setEditModalVisible(false);
    fetchCategoryDetail();
    fetchCategoryProducts();
  };

  // 添加产品成功回调
  const handleAddSuccess = () => {
    setAddModalVisible(false);
    fetchCategoryProducts();
  };

  // 从类别中移除选中的产品
  const handleRemoveProducts = () => {
    if (selectedProductIds.size === 0) {
      message.warning(t('page.categoryDetail.selectProductsToRemove'));
      return;
    }

    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('page.categoryDetail.removeProductsConfirm', { count: selectedProductIds.size }),
      icon: <ExclamationCircleFilled />,
      okText: t('common.confirm'),
      onOk: async () => {
        if (!categoryId) return;

        try {
          setRemoveLoading(true);
          await unbindProductsFromCategory(categoryId, [...selectedProductIds]);
          message.success(t('common.removeSuccess'));
          setSelectedProductIds(new Set());
          fetchCategoryProducts(pagination.current, pagination.pageSize);
        } catch (error) {
          console.error('移除产品失败:', error);
          message.error(t('page.categoryDetail.removeFailed'));
        } finally {
          setRemoveLoading(false);
        }
      },
      title: t('common.confirmRemove'),
    });
  };

  const handleRemoveSingleProduct = useCallback(
    (product: ApiProduct) => {
      Modal.confirm({
        cancelText: t('common.cancel'),
        content: t('page.categoryDetail.removeProductConfirm', { name: product.name }),
        icon: <ExclamationCircleFilled />,
        okText: t('common.confirm'),
        onOk: async () => {
          if (!categoryId) return;
          try {
            await unbindProductsFromCategory(categoryId, [product.productId]);
            message.success(t('common.removeSuccess'));
            setSelectedProductIds((prev) => {
              const next = new Set(prev);
              next.delete(product.productId);
              return next;
            });
            fetchCategoryProducts(pagination.current, pagination.pageSize);
          } catch (error) {
            console.error('移除产品失败:', error);
            message.error(t('page.categoryDetail.removeFailed'));
          }
        },
        title: t('common.confirmRemove'),
      });
    },
    [categoryId, fetchCategoryProducts, pagination, t],
  );

  const rowSelection: TableProps<ApiProduct>['rowSelection'] = {
    onChange: (selectedRowKeys) => {
      setSelectedProductIds(new Set(selectedRowKeys as string[]));
    },
    selectedRowKeys: [...selectedProductIds],
  };

  const handleOpenProductDetail = useCallback(
    (productId: string) => {
      navigate(`/api-products/${productId}`, {
        state: { from: currentPath },
      });
    },
    [currentPath, navigate],
  );

  const handleToggleProductSelect = useCallback((productId: string, checked: boolean) => {
    setSelectedProductIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(productId);
      } else {
        next.delete(productId);
      }
      return next;
    });
  }, []);

  const columns: TableProps<ApiProduct>['columns'] = [
    {
      dataIndex: 'name',
      render: (_text: unknown, record: ApiProduct) => (
        <div className="min-w-0">
          <button
            className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
            onClick={() => handleOpenProductDetail(record.productId)}
            type="button"
          >
            {record.name}
          </button>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.productId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.productId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.categoryDetail.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'type',
      render: (type: string) => <span className="text-xs text-gray-600">{getTypeLabel(type)}</span>,
      title: t('common.type'),
      width: 120,
    },
    {
      dataIndex: 'status',
      render: (status: string) => renderStatusTag(status, t),
      title: t('common.status'),
      width: 110,
    },
    {
      dataIndex: 'description',
      ellipsis: { showTitle: false },
      render: (description: string) => (
        <span className="text-gray-600 text-xs" title={description}>
          {description || '-'}
        </span>
      ),
      title: t('common.description'),
    },
    {
      render: (_text: unknown, record: ApiProduct) => (
        <Button
          className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
          icon={<DeleteOutlined />}
          onClick={() => handleRemoveSingleProduct(record)}
          type="text"
        >
          {t('common.remove')}
        </Button>
      ),
      title: t('common.operation'),
      width: 120,
    },
  ];

  if (categoryLoading) {
    return (
      <div className="space-y-6">
        <Skeleton.Input style={{ height: 32, width: 300 }} />
        <Card>
          <div className="flex items-start space-x-6">
            <Skeleton.Avatar size={80} />
            <div className="flex-1">
              <Skeleton.Input style={{ height: 24, marginBottom: 12, width: 200 }} />
              <Skeleton paragraph={{ rows: 3 }} />
            </div>
          </div>
        </Card>
      </div>
    );
  }

  if (!category) {
    return (
      <div className="flex items-center justify-center h-64">
        <Empty description={t('page.categoryDetail.categoryMissing')} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 导航栏 */}
      <div className="flex items-center justify-between">
        <Button
          className="text-gray-600 hover:text-gray-800"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/product-categories')}
          type="text"
        >
          {t('common.back')}
        </Button>

        <Button icon={<EditOutlined />} onClick={() => setEditModalVisible(true)} type="primary">
          {t('common.edit')}
        </Button>
      </div>

      {/* 类别详情卡片 */}
      <Card className="bg-gradient-to-br from-white to-gray-50/30 border border-gray-100 shadow-sm">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center space-x-4">
            {/* 类别图标 */}
            <div className="flex-shrink-0">{renderCategoryIcon(category, 64)}</div>

            {/* 类别信息 */}
            <div className="flex-1 min-w-0">
              <h1 className="text-xl font-bold text-gray-800 mb-2">{category.name}</h1>
              <p className="text-sm text-gray-500 mb-3">
                {category.description || (
                  <span className="italic text-gray-400">{t('common.noDescription')}</span>
                )}
              </p>
              <div className="flex items-center space-x-4 text-xs text-gray-400">
                <span className="font-mono">
                  {t('common.id')}: {category.categoryId}
                </span>
                {category.createAt && (
                  <span>{t('common.createdAt', { time: formatDateTime(category.createAt) })}</span>
                )}
              </div>
            </div>
          </div>
        </div>
      </Card>

      <div className="mb-6">
        <div className="flex items-center justify-between">
          <span className="text-lg font-medium">{t('page.categoryDetail.productAssociation')}</span>
          <div className="flex items-center gap-2">
            {selectedProductIds.size > 0 && (
              <Button
                danger
                icon={<DeleteOutlined />}
                loading={removeLoading}
                onClick={handleRemoveProducts}
                type="primary"
              >
                {t('page.categoryDetail.removeSelected', { count: selectedProductIds.size })}
              </Button>
            )}
            <Button icon={<PlusOutlined />} onClick={() => setAddModalVisible(true)} type="primary">
              {t('page.categoryDetail.addProduct')}
            </Button>
          </div>
        </div>
        <Divider className="mt-2" />
      </div>

      {viewMode === 'CARD' ? (
        <div>
          {productsLoading ? (
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              {Array.from({ length: Math.min(pagination.pageSize || 6, 6) }).map((_, index) => (
                <div
                  className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
                  key={index}
                >
                  <div className="flex items-start gap-3">
                    <Skeleton.Avatar active shape="square" size={44} />
                    <div className="min-w-0 flex-1">
                      <Skeleton.Input active size="small" style={{ width: '70%' }} />
                      <Skeleton.Input active size="small" style={{ marginTop: 8, width: '45%' }} />
                    </div>
                  </div>
                  <Skeleton active paragraph={{ rows: 2 }} title={false} />
                </div>
              ))}
            </div>
          ) : products.length > 0 ? (
            <>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                {products.map((product) => (
                  <CategoryProductCard
                    checked={selectedProductIds.has(product.productId)}
                    key={product.productId}
                    onOpenDetail={handleOpenProductDetail}
                    onRemove={handleRemoveSingleProduct}
                    onToggleSelect={handleToggleProductSelect}
                    product={product}
                  />
                ))}
              </div>
              {pagination.total > 0 && (
                <div className="flex justify-end px-1 py-3">
                  <Pagination
                    current={pagination.current}
                    onChange={(page, pageSize) => fetchCategoryProducts(page, pageSize)}
                    pageSize={pagination.pageSize}
                    pageSizeOptions={['10', '20', '50', '100']}
                    showQuickJumper
                    showSizeChanger
                    showTotal={(total) => t('common.totalItems', { total })}
                    total={pagination.total}
                  />
                </div>
              )}
            </>
          ) : (
            <div className="rounded-lg border border-gray-200 bg-white py-14">
              <Empty description={t('page.categoryDetail.noLinkedProducts')} />
            </div>
          )}
        </div>
      ) : (
        <DataTable<ApiProduct>
          columns={columns}
          dataSource={products}
          loading={productsLoading}
          pagination={{
            current: pagination.current,
            onChange: (page, pageSize) => fetchCategoryProducts(page, pageSize),
            pageSize: pagination.pageSize,
            total: pagination.total,
          }}
          rowKey="productId"
          rowSelection={rowSelection}
        />
      )}

      {/* 编辑类别弹窗 */}
      <CategoryFormModal
        category={category}
        isEdit={true}
        onCancel={() => setEditModalVisible(false)}
        onSuccess={handleEditSuccess}
        visible={editModalVisible}
      />

      {/* 添加产品弹窗 */}
      {categoryId && (
        <AddProductModal
          categoryId={categoryId}
          onCancel={() => setAddModalVisible(false)}
          onSuccess={handleAddSuccess}
          visible={addModalVisible}
        />
      )}
    </div>
  );
}
