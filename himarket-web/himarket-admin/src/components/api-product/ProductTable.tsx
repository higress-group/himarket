import { ExclamationCircleOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Checkbox, Empty, Modal, Pagination, Skeleton, Tooltip, message } from 'antd';
import { useCallback, useEffect, useRef, useState, useImperativeHandle, forwardRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import BatchActionBar from '@/components/api-product/BatchActionBar';
import { DataTable } from '@/components/common/DataTable';
import { SearchInput } from '@/components/common/SearchInput';
import { StatusIndicator } from '@/components/common/StatusIndicator';
import { ViewModeToggle } from '@/components/common/ViewModeToggle';
import { ProductIconRenderer } from '@/components/icons/ProductIconRenderer';
import { useLocale } from '@/contexts/LocaleContext';
import { useAdminViewMode } from '@/hooks/useAdminViewMode';
import { apiProductApi } from '@/lib/api';
import { getIconString } from '@/lib/iconUtils';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';

import type { TableProps } from 'antd';

export interface ProductTableProps {
  productType: 'MODEL_API' | 'MCP_SERVER' | 'AGENT_SKILL' | 'WORKER' | 'AGENT_API' | 'REST_API';
}

export interface ProductTableRef {
  handleCreate: () => void;
  refresh: () => void;
}

const API_PRODUCTS_VIEW_MODE_SETTING_KEY = 'api-products.view-mode';

const PRODUCT_TYPE_LABELS: Record<ApiProduct['type'], string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

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

interface ProductCardProps {
  checked: boolean;
  onDelete: (productId: string, productName: string) => void;
  onEdit: (product: ApiProduct) => void;
  onOpenDetail: (productId: string) => void;
  onToggleSelect: (productId: string, checked: boolean) => void;
  product: ApiProduct;
}

function ProductCard({
  checked,
  onDelete,
  onEdit,
  onOpenDetail,
  onToggleSelect,
  product,
}: ProductCardProps) {
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
              <span className="text-xs text-gray-500">
                {PRODUCT_TYPE_LABELS[product.type] || product.type}
              </span>
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
            icon={<EditOutlined />}
            onClick={(event) => {
              event.stopPropagation();
              onEdit(product);
            }}
            size="small"
            type="text"
          >
            {t('common.edit')}
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={(event) => {
              event.stopPropagation();
              onDelete(product.productId, product.name);
            }}
            size="small"
            type="text"
          >
            {t('common.delete')}
          </Button>
        </div>
      </div>
    </article>
  );
}

const ProductTable = forwardRef<ProductTableRef, ProductTableProps>(({ productType }, ref) => {
  const { t } = useLocale();
  const navigate = useNavigate();
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}${location.hash}`;
  const {
    loading: viewModeLoading,
    setViewMode,
    viewMode,
  } = useAdminViewMode(API_PRODUCTS_VIEW_MODE_SETTING_KEY);
  const [products, setProducts] = useState<ApiProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [modalVisible, setModalVisible] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ApiProduct | null>(null);
  const lastFetchedTypeRef = useRef<string | null>(null);

  const fetchProducts = useCallback(
    (page = 1, size = 10, name = '') => {
      setLoading(true);
      const params: Record<string, string | number | undefined> = {
        page,
        size,
        type: productType,
      };
      if (name.trim()) params.name = name.trim();

      apiProductApi
        .getApiProducts(params)
        .then((res: { data: { content: ApiProduct[]; totalElements: number } }) => {
          setProducts(res.data.content);
          setPagination({
            current: page,
            pageSize: size,
            total: res.data.totalElements || 0,
          });
        })
        .finally(() => setLoading(false));
    },
    [productType],
  );

  useEffect(() => {
    if (lastFetchedTypeRef.current === productType) return;
    lastFetchedTypeRef.current = productType;
    setSearchInput('');
    setSelectedIds(new Set());
    fetchProducts(1, 10, '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productType]);

  const handleSearch = () => {
    fetchProducts(1, pagination.pageSize, searchInput);
  };

  const handleDelete = useCallback(
    (productId: string, productName: string) => {
      Modal.confirm({
        cancelText: t('common.cancel'),
        content: t('product.table.deleteConfirm', { name: productName }),
        icon: <ExclamationCircleOutlined />,
        okText: t('common.confirmDelete'),
        okType: 'danger',
        onOk() {
          return apiProductApi.deleteApiProduct(productId).then(() => {
            message.success(t('product.table.deleteSuccess'));
            fetchProducts(pagination.current, pagination.pageSize);
          });
        },
        title: t('common.confirmDelete'),
      });
    },
    [fetchProducts, pagination, t],
  );

  const handleEdit = useCallback((product: ApiProduct) => {
    setEditingProduct(product);
    setModalVisible(true);
  }, []);

  const handleCreate = useCallback(() => {
    setEditingProduct(null);
    setModalVisible(true);
  }, []);

  const handleOpenDetail = useCallback(
    (productId: string) => {
      navigate(`/api-products/${productId}`, {
        state: { from: currentPath },
      });
    },
    [currentPath, navigate],
  );

  const handleModalSuccess = () => {
    setModalVisible(false);
    setEditingProduct(null);
    fetchProducts(pagination.current, pagination.pageSize);
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingProduct(null);
  };

  useImperativeHandle(
    ref,
    () => ({
      handleCreate,
      refresh: () => fetchProducts(pagination.current, pagination.pageSize),
    }),
    [handleCreate, fetchProducts, pagination],
  );

  // Row selection for batch operations
  const rowSelection: TableProps<ApiProduct>['rowSelection'] = {
    onChange: (selectedRowKeys) => {
      setSelectedIds(new Set(selectedRowKeys as string[]));
    },
    selectedRowKeys: [...selectedIds],
  };

  const handleToggleSelect = useCallback((productId: string, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(productId);
      } else {
        next.delete(productId);
      }
      return next;
    });
  }, []);

  // Table columns definition
  const columns: TableProps<ApiProduct>['columns'] = [
    {
      dataIndex: 'name',
      render: (_text: unknown, record: ApiProduct) => (
        <div className="min-w-0">
          <Tooltip placement="topLeft" title={record.name}>
            <button
              className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
              onClick={() => handleOpenDetail(record.productId)}
              type="button"
            >
              {record.name}
            </button>
          </Tooltip>
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
      dataIndex: 'status',
      render: (status: string) => renderStatusTag(status, t),
      title: t('common.status'),
      width: 110,
    },
    {
      dataIndex: 'description',
      ellipsis: { showTitle: false },
      render: (description: string) => (
        <Tooltip placement="topLeft" title={description}>
          <span className="text-gray-600 text-xs">{description || '-'}</span>
        </Tooltip>
      ),
      title: t('common.description'),
    },
    {
      dataIndex: 'createAt',
      render: (createAt: string) => (
        <span className="text-xs text-gray-500">{createAt ? formatDateTime(createAt) : '-'}</span>
      ),
      title: t('product.overview.createAt'),
      width: 160,
    },
    {
      render: (_text: unknown, record: ApiProduct) => (
        <div className="flex items-center gap-2">
          <Button
            className="text-blue-600 hover:text-blue-700 hover:bg-blue-50 !px-2 text-xs"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            type="text"
          >
            {t('common.edit')}
          </Button>
          <Button
            className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.productId, record.name)}
            type="text"
          >
            {t('common.delete')}
          </Button>
        </div>
      ),
      title: t('common.operation'),
      width: 160,
    },
  ];

  const batchAction =
    selectedIds.size > 0 ? (
      <BatchActionBar
        inline
        onCancel={() => setSelectedIds(new Set())}
        onComplete={() => {
          setSelectedIds(new Set());
          fetchProducts(pagination.current, pagination.pageSize);
        }}
        products={products}
        selectedIds={selectedIds}
      />
    ) : null;

  const viewModeToggle = (
    <ViewModeToggle disabled={viewModeLoading} onChange={setViewMode} value={viewMode} />
  );

  const productFormModal = (
    <ApiProductFormModal
      defaultProductType={productType}
      initialData={editingProduct || undefined}
      onCancel={handleModalCancel}
      onSuccess={handleModalSuccess}
      productId={editingProduct?.productId}
      visible={modalVisible}
    />
  );

  if (viewMode === 'CARD') {
    return (
      <div>
        <div className="mb-2 flex items-center justify-between px-1 py-3">
          <SearchInput
            onChange={(value) => {
              setSearchInput(value);
              if (!value) {
                fetchProducts(1, pagination.pageSize, '');
              }
            }}
            onClear={() => {
              setSearchInput('');
              fetchProducts(1, pagination.pageSize, '');
            }}
            onSearch={handleSearch}
            placeholder={t('product.table.searchByName')}
            value={searchInput}
          />
          <div className="flex items-center gap-2">
            {batchAction}
            {viewModeToggle}
          </div>
        </div>

        {loading ? (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: Math.min(pagination.pageSize || 6, 6) }).map((_, index) => (
              <div className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm" key={index}>
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
                <ProductCard
                  checked={selectedIds.has(product.productId)}
                  key={product.productId}
                  onDelete={handleDelete}
                  onEdit={handleEdit}
                  onOpenDetail={handleOpenDetail}
                  onToggleSelect={handleToggleSelect}
                  product={product}
                />
              ))}
            </div>
            {pagination.total > 0 && (
              <div className="flex justify-end px-1 py-3">
                <Pagination
                  current={pagination.current}
                  onChange={(page, pageSize) => fetchProducts(page, pageSize)}
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
            <Empty
              description={t('product.table.emptyByType', {
                type: PRODUCT_TYPE_LABELS[productType] || productType,
              })}
            />
          </div>
        )}

        {productFormModal}
      </div>
    );
  }

  return (
    <div>
      <DataTable<ApiProduct>
        columns={columns}
        dataSource={products}
        loading={loading}
        pagination={{
          current: pagination.current,
          onChange: (page, pageSize) => fetchProducts(page, pageSize),
          pageSize: pagination.pageSize,
          total: pagination.total,
        }}
        rowKey="productId"
        rowSelection={rowSelection}
        search={{
          onChange: (value) => {
            setSearchInput(value);
            if (!value) {
              fetchProducts(1, pagination.pageSize, '');
            }
          },
          onSearch: handleSearch,
          placeholder: t('product.table.searchByName'),
          value: searchInput,
        }}
        toolbarRight={
          <>
            {batchAction}
            {viewModeToggle}
          </>
        }
      />

      {productFormModal}
    </div>
  );
});

ProductTable.displayName = 'ProductTable';

export default ProductTable;
