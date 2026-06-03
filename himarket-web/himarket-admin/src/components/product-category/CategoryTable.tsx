import {
  EditOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  FolderOutlined,
} from '@ant-design/icons';
import { Button, Checkbox, Empty, Modal, Pagination, Skeleton, Tooltip, message } from 'antd';
import { useCallback, useEffect, useState, useImperativeHandle, forwardRef, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

import { DataTable } from '@/components/common/DataTable';
import { SearchInput } from '@/components/common/SearchInput';
import { ViewModeToggle } from '@/components/common/ViewModeToggle';
import { ProductIconRenderer } from '@/components/icons/ProductIconRenderer';
import CategoryFormModal from '@/components/product-category/CategoryFormModal';
import { useLocale } from '@/contexts/LocaleContext';
import { useAdminViewMode } from '@/hooks/useAdminViewMode';
import { getIconString } from '@/lib/iconUtils';
import { getProductCategoriesByPage, deleteProductCategory } from '@/lib/productCategoryApi';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { ProductCategory, QueryProductCategoryParam } from '@/types/product-category';

import type { TableProps } from 'antd';

export interface CategoryTableRef {
  handleCreate: () => void;
}

const PRODUCT_CATEGORIES_VIEW_MODE_SETTING_KEY = 'product-categories.view-mode';

interface CategoryCardProps {
  category: ProductCategory;
  checked: boolean;
  onDelete: (categoryId: string, categoryName: string) => void;
  onEdit: (category: ProductCategory) => void;
  onOpenDetail: (categoryId: string) => void;
  onToggleSelect: (categoryId: string, checked: boolean) => void;
}

function CategoryIcon({ category }: { category: ProductCategory }) {
  if (!category.icon?.value) {
    return <FolderOutlined className="text-blue-500" style={{ fontSize: 22 }} />;
  }
  return <ProductIconRenderer className="h-8 w-8" iconType={getIconString(category.icon)} />;
}

function CategoryCard({
  category,
  checked,
  onDelete,
  onEdit,
  onOpenDetail,
  onToggleSelect,
}: CategoryCardProps) {
  const { t } = useLocale();

  return (
    <article
      className={`group flex min-h-[164px] cursor-pointer flex-col rounded-lg border bg-white p-4 shadow-sm transition-all duration-150 hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-md ${
        checked ? 'border-blue-300 ring-1 ring-blue-100' : 'border-gray-200'
      }`}
      onClick={() => onOpenDetail(category.categoryId)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onOpenDetail(category.categoryId);
        }
      }}
      role="button"
      tabIndex={0}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-blue-50">
            <CategoryIcon category={category} />
          </div>
          <div className="min-w-0">
            <Tooltip placement="topLeft" title={category.name}>
              <h3 className="truncate text-sm font-semibold text-gray-900">{category.name}</h3>
            </Tooltip>
            <Tooltip title={t('common.copyToClipboard')}>
              <button
                className="mt-1 block max-w-[190px] truncate border-none bg-transparent p-0 text-left text-xs text-gray-400 hover:text-blue-500"
                onClick={(event) => {
                  event.stopPropagation();
                  copyToClipboard(category.categoryId).then(() => {
                    message.success(t('common.copiedToClipboard'));
                  });
                }}
                type="button"
              >
                {category.categoryId}
              </button>
            </Tooltip>
          </div>
        </div>
        <Checkbox
          checked={checked}
          onChange={(event) => onToggleSelect(category.categoryId, event.target.checked)}
          onClick={(event) => event.stopPropagation()}
        />
      </div>

      <Tooltip placement="topLeft" title={category.description}>
        <p className="mt-4 line-clamp-2 min-h-[40px] text-xs leading-5 text-gray-600">
          {category.description || t('common.noDescription')}
        </p>
      </Tooltip>

      <div className="mt-auto flex items-end justify-between gap-3 pt-4">
        <span className="text-xs text-gray-400">
          {category.createAt ? formatDateTime(category.createAt) : '-'}
        </span>
        <div className="flex shrink-0 translate-y-1 items-center gap-1 opacity-0 transition-all duration-150 group-hover:translate-y-0 group-hover:opacity-100 group-focus:translate-y-0 group-focus:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100">
          <Button
            icon={<EditOutlined />}
            onClick={(event) => {
              event.stopPropagation();
              onEdit(category);
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
              onDelete(category.categoryId, category.name);
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

const CategoryTable = forwardRef<CategoryTableRef>((_, ref) => {
  const { t } = useLocale();
  const navigate = useNavigate();
  const {
    loading: viewModeLoading,
    setViewMode,
    viewMode,
  } = useAdminViewMode(PRODUCT_CATEGORIES_VIEW_MODE_SETTING_KEY);
  const fetchedRef = useRef(false);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [nameFilter, setNameFilter] = useState('');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [modalVisible, setModalVisible] = useState(false);
  const [editingCategory, setEditingCategory] = useState<ProductCategory | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const fetchCategories = useCallback(
    (page = 1, size = 10, name = nameFilter) => {
      setLoading(true);
      const params: QueryProductCategoryParam = name.trim() ? { name: name.trim() } : {};
      getProductCategoriesByPage(page, size, params)
        .then((res) => {
          setCategories(res.data.content || []);
          setPagination({
            current: res.data.number,
            pageSize: res.data.size,
            total: res.data.totalElements,
          });
        })
        .catch(() => {
          message.error(t('page.categoryTable.fetchFailed'));
        })
        .finally(() => setLoading(false));
    },
    [nameFilter, t],
  );

  useEffect(() => {
    if (fetchedRef.current) return;
    fetchedRef.current = true;
    fetchCategories();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = () => {
    setNameFilter(searchInput);
    fetchCategories(1, pagination.pageSize, searchInput);
  };

  const handleDelete = useCallback(
    (categoryId: string, categoryName: string) => {
      Modal.confirm({
        cancelText: t('common.cancel'),
        content: t('page.categoryTable.deleteConfirm', { name: categoryName }),
        icon: <ExclamationCircleOutlined />,
        okText: t('common.confirmDelete'),
        okType: 'danger',
        onOk() {
          return deleteProductCategory(categoryId)
            .then(() => {
              message.success(t('page.categoryTable.deleteSuccess'));
              fetchCategories(pagination.current, pagination.pageSize);
            })
            .catch(() => {
              message.error(t('page.categoryTable.deleteFailed'));
            });
        },
        title: t('common.confirmDelete'),
      });
    },
    [fetchCategories, pagination, t],
  );

  const handleBatchDelete = useCallback(() => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('page.categoryTable.unrecoverable'),
      okText: t('common.confirmDelete'),
      okType: 'danger',
      onOk: async () => {
        const results = await Promise.allSettled(
          [...selectedIds].map((id) => deleteProductCategory(id)),
        );
        const succeeded = results.filter((r) => r.status === 'fulfilled').length;
        const failedResults = results
          .map((r, i) => ({ id: [...selectedIds][i], result: r }))
          .filter((item) => item.result.status === 'rejected');

        if (failedResults.length > 0) {
          const failedDetails = failedResults.map((item) => {
            const category = categories.find((c) => c.categoryId === item.id);
            const reason = (item.result as PromiseRejectedResult).reason;
            const errorMsg =
              reason?.response?.data?.message || reason?.message || t('common.unknown');
            return `${category?.name || item.id}: ${errorMsg}`;
          });
          Modal.warning({
            content: (
              <div className="mt-2">
                <p className="font-medium mb-1">{t('page.categoryTable.failureDetail')}</p>
                <ul className="list-disc pl-4 text-sm text-gray-600">
                  {failedDetails.map((detail, i) => (
                    <li key={i}>{detail}</li>
                  ))}
                </ul>
              </div>
            ),
            title: t('page.categoryTable.partialFailed', {
              failed: failedResults.length,
              success: succeeded,
            }),
          });
        } else {
          message.success(t('page.categoryTable.successDeleted', { count: succeeded }));
        }
        setSelectedIds(new Set());
        fetchCategories(pagination.current, pagination.pageSize);
      },
      title: t('page.categoryTable.batchDeleteConfirm', { count: selectedIds.size }),
    });
  }, [selectedIds, categories, fetchCategories, pagination, t]);

  const handleEdit = useCallback((category: ProductCategory) => {
    setEditingCategory(category);
    setModalVisible(true);
  }, []);

  const handleCreate = useCallback(() => {
    setEditingCategory(null);
    setModalVisible(true);
  }, []);

  const handleOpenDetail = useCallback(
    (categoryId: string) => {
      navigate(`/product-categories/${categoryId}`);
    },
    [navigate],
  );

  const handleModalSuccess = () => {
    setModalVisible(false);
    setEditingCategory(null);
    fetchCategories(pagination.current, pagination.pageSize);
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingCategory(null);
  };

  useImperativeHandle(
    ref,
    () => ({
      handleCreate,
    }),
    [handleCreate],
  );

  const rowSelection: TableProps<ProductCategory>['rowSelection'] = {
    onChange: (selectedRowKeys) => setSelectedIds(new Set(selectedRowKeys as string[])),
    selectedRowKeys: [...selectedIds],
  };

  const handleToggleSelect = useCallback((categoryId: string, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(categoryId);
      } else {
        next.delete(categoryId);
      }
      return next;
    });
  }, []);

  const columns: TableProps<ProductCategory>['columns'] = [
    {
      dataIndex: 'name',
      render: (_: unknown, record: ProductCategory) => (
        <div className="min-w-0">
          <Tooltip placement="topLeft" title={record.name}>
            <button
              className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
              onClick={() => handleOpenDetail(record.categoryId)}
              type="button"
            >
              {record.name}
            </button>
          </Tooltip>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.categoryId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.categoryId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.categoryTable.nameAndId'),
      width: 280,
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
      width: 300,
    },
    {
      dataIndex: 'createAt',
      render: (val: string) => (
        <span className="text-xs text-gray-500">{val ? formatDateTime(val) : '-'}</span>
      ),
      title: t('product.overview.createAt'),
      width: 160,
    },
    {
      render: (_: unknown, record: ProductCategory) => (
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
            onClick={() => handleDelete(record.categoryId, record.name)}
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
      <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete} type="primary">
        {t('page.categoryTable.batchDelete')}
      </Button>
    ) : null;

  const viewModeToggle = (
    <ViewModeToggle disabled={viewModeLoading} onChange={setViewMode} value={viewMode} />
  );

  const categoryFormModal = (
    <CategoryFormModal
      category={editingCategory}
      isEdit={!!editingCategory}
      onCancel={handleModalCancel}
      onSuccess={handleModalSuccess}
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
                setNameFilter('');
                fetchCategories(1, pagination.pageSize, '');
              }
            }}
            onClear={() => {
              setSearchInput('');
              setNameFilter('');
              fetchCategories(1, pagination.pageSize, '');
            }}
            onSearch={handleSearch}
            placeholder={t('page.categoryTable.searchByName')}
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
        ) : categories.length > 0 ? (
          <>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              {categories.map((category) => (
                <CategoryCard
                  category={category}
                  checked={selectedIds.has(category.categoryId)}
                  key={category.categoryId}
                  onDelete={handleDelete}
                  onEdit={handleEdit}
                  onOpenDetail={handleOpenDetail}
                  onToggleSelect={handleToggleSelect}
                />
              ))}
            </div>
            {pagination.total > 0 && (
              <div className="flex justify-end px-1 py-3">
                <Pagination
                  current={pagination.current}
                  onChange={(page, pageSize) => fetchCategories(page, pageSize)}
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
            <Empty description={t('page.categoryTable.empty')} />
          </div>
        )}

        {categoryFormModal}
      </div>
    );
  }

  return (
    <div>
      <DataTable<ProductCategory>
        columns={columns}
        dataSource={categories}
        loading={loading}
        pagination={{
          current: pagination.current,
          onChange: (page, pageSize) => fetchCategories(page, pageSize),
          pageSize: pagination.pageSize,
          total: pagination.total,
        }}
        rowKey="categoryId"
        rowSelection={rowSelection}
        search={{
          onChange: (value) => {
            setSearchInput(value);
            if (!value) {
              setNameFilter('');
              fetchCategories(1, pagination.pageSize, '');
            }
          },
          onSearch: handleSearch,
          placeholder: t('page.categoryTable.searchByName'),
          value: searchInput,
        }}
        toolbarRight={
          <>
            {batchAction}
            {viewModeToggle}
          </>
        }
      />

      {categoryFormModal}
    </div>
  );
});

CategoryTable.displayName = 'CategoryTable';

export default CategoryTable;
