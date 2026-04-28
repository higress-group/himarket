import { EditOutlined, DeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { Button, Modal, Tooltip, message } from 'antd';
import { useCallback, useEffect, useState, useImperativeHandle, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';

import { DataTable } from '@/components/common/DataTable';
import CategoryFormModal from '@/components/product-category/CategoryFormModal';
import { getProductCategoriesByPage, deleteProductCategory } from '@/lib/productCategoryApi';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { ProductCategory, QueryProductCategoryParam } from '@/types/product-category';

import type { TableProps } from 'antd';

export interface CategoryTableRef {
  handleCreate: () => void;
}

const CategoryTable = forwardRef<CategoryTableRef>((_, ref) => {
  const navigate = useNavigate();
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
          message.error('获取产品类别失败');
        })
        .finally(() => setLoading(false));
    },
    [nameFilter],
  );

  useEffect(() => {
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
        cancelText: '取消',
        content: `确定要删除类别 "${categoryName}" 吗？此操作不可恢复。`,
        icon: <ExclamationCircleOutlined />,
        okText: '确认删除',
        okType: 'danger',
        onOk() {
          return deleteProductCategory(categoryId)
            .then(() => {
              message.success('类别删除成功');
              fetchCategories(pagination.current, pagination.pageSize);
            })
            .catch(() => {
              message.error('删除类别失败，可能该类别正在使用中');
            });
        },
        title: '确认删除',
      });
    },
    [fetchCategories, pagination],
  );

  const handleBatchDelete = useCallback(() => {
    Modal.confirm({
      cancelText: '取消',
      content: '此操作不可恢复。',
      okText: '确认删除',
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
            const errorMsg = reason?.response?.data?.message || reason?.message || '未知错误';
            return `${category?.name || item.id}: ${errorMsg}`;
          });
          Modal.warning({
            content: (
              <div className="mt-2">
                <p className="font-medium mb-1">失败详情：</p>
                <ul className="list-disc pl-4 text-sm text-gray-600">
                  {failedDetails.map((detail, i) => (
                    <li key={i}>{detail}</li>
                  ))}
                </ul>
              </div>
            ),
            title: `成功 ${succeeded} 个，失败 ${failedResults.length} 个`,
          });
        } else {
          message.success(`成功删除 ${succeeded} 个类别`);
        }
        setSelectedIds(new Set());
        fetchCategories(pagination.current, pagination.pageSize);
      },
      title: `确认批量删除 ${selectedIds.size} 个类别？`,
    });
  }, [selectedIds, categories, fetchCategories, pagination]);

  const handleEdit = useCallback((category: ProductCategory) => {
    setEditingCategory(category);
    setModalVisible(true);
  }, []);

  const handleCreate = useCallback(() => {
    setEditingCategory(null);
    setModalVisible(true);
  }, []);

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

  const columns: TableProps<ProductCategory>['columns'] = [
    {
      dataIndex: 'name',
      render: (_: unknown, record: ProductCategory) => (
        <div className="min-w-0">
          <Tooltip placement="topLeft" title={record.name}>
            <button
              className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
              onClick={() => navigate(`/product-categories/${record.categoryId}`)}
              type="button"
            >
              {record.name}
            </button>
          </Tooltip>
          <Tooltip title="点击复制">
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.categoryId).then(() => {
                  message.success('已复制到剪贴板');
                })
              }
              type="button"
            >
              {record.categoryId}
            </button>
          </Tooltip>
        </div>
      ),
      title: '类别名称/ID',
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
      title: '描述',
      width: 300,
    },
    {
      dataIndex: 'createAt',
      render: (val: string) => (
        <span className="text-xs text-gray-500">{val ? formatDateTime(val) : '-'}</span>
      ),
      title: '创建时间',
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
            编辑
          </Button>
          <Button
            className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.categoryId, record.name)}
            type="text"
          >
            删除
          </Button>
        </div>
      ),
      title: '操作',
      width: 160,
    },
  ];

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
          placeholder: '搜索类别名称',
          value: searchInput,
        }}
        toolbarRight={
          selectedIds.size > 0 ? (
            <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete} type="primary">
              批量删除
            </Button>
          ) : null
        }
      />

      {/* Create/Edit modal */}
      <CategoryFormModal
        category={editingCategory}
        isEdit={!!editingCategory}
        onCancel={handleModalCancel}
        onSuccess={handleModalSuccess}
        visible={modalVisible}
      />
    </div>
  );
});

CategoryTable.displayName = 'CategoryTable';

export default CategoryTable;
