import { SearchOutlined, ExclamationCircleOutlined, MoreOutlined } from '@ant-design/icons';
import { Table, Input, Button, Modal, Tooltip, Dropdown, message, Empty } from 'antd';
import { useCallback, useEffect, useState, useImperativeHandle, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';

import CategoryFormModal from '@/components/product-category/CategoryFormModal';
import { getProductCategoriesByPage, deleteProductCategory } from '@/lib/productCategoryApi';
import type { ProductCategory, QueryProductCategoryParam } from '@/types/product-category';

import type { TableProps, MenuProps } from 'antd';

export interface CategoryTableRef {
  handleCreate: () => void;
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

const CategoryTable = forwardRef<CategoryTableRef>((_, ref) => {
  const navigate = useNavigate();
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [nameFilter, setNameFilter] = useState('');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [modalVisible, setModalVisible] = useState(false);
  const [editingCategory, setEditingCategory] = useState<ProductCategory | null>(null);

  const fetchCategories = useCallback(
    (page = 1, size = 20, name = nameFilter) => {
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

  const handleClearSearch = () => {
    setSearchInput('');
    setNameFilter('');
    fetchCategories(1, pagination.pageSize, '');
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

  const columns: TableProps<ProductCategory>['columns'] = [
    {
      dataIndex: 'name',
      render: (_: unknown, record: ProductCategory) => (
        <button
          className="text-colorPrimary hover:text-colorPrimary/80 font-medium cursor-pointer bg-transparent border-none p-0"
          onClick={() => navigate(`/product-categories/${record.categoryId}`)}
          type="button"
        >
          {record.name}
        </button>
      ),
      title: '分类名称',
    },
    {
      dataIndex: 'description',
      ellipsis: { showTitle: false },
      render: (description: string) => (
        <Tooltip placement="topLeft" title={description}>
          {description || ''}
        </Tooltip>
      ),
      title: '描述',
      width: 300,
    },
    {
      dataIndex: 'createAt',
      render: (val: string) => formatDate(val),
      title: '创建时间',
      width: 180,
    },
    {
      dataIndex: 'updatedAt',
      render: (val: string) => formatDate(val),
      title: '更新时间',
      width: 180,
    },
    {
      render: (_: unknown, record: ProductCategory) => {
        const items: MenuProps['items'] = [
          { key: 'edit', label: '编辑', onClick: () => handleEdit(record) },
          { type: 'divider' },
          {
            danger: true,
            key: 'delete',
            label: '删除',
            onClick: () => handleDelete(record.categoryId, record.name),
          },
        ];
        return (
          <Dropdown menu={{ items }} trigger={['click']}>
            <Button icon={<MoreOutlined />} onClick={(e) => e.stopPropagation()} type="text" />
          </Dropdown>
        );
      },
      title: '操作',
      width: 120,
    },
  ];

  return (
    <div>
      {/* Search toolbar */}
      <div className="flex items-center mb-4">
        <div
          className="flex items-center border border-gray-300 rounded-md overflow-hidden hover:border-colorPrimary focus-within:border-colorPrimary"
          style={{ minWidth: 260 }}
        >
          <Input
            allowClear
            className="border-0"
            onChange={(e) => setSearchInput(e.target.value)}
            onClear={handleClearSearch}
            onPressEnter={handleSearch}
            placeholder="搜索类别名称"
            size="middle"
            value={searchInput}
            variant="borderless"
          />
          <Button
            className="border-0 rounded-none"
            icon={<SearchOutlined />}
            onClick={handleSearch}
            style={{ width: 40 }}
            type="text"
          />
        </div>
      </div>

      {/* Table */}
      <Table<ProductCategory>
        columns={columns}
        dataSource={categories}
        loading={loading}
        locale={{
          emptyText: <Empty description="暂无产品类别" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
        }}
        pagination={{
          current: pagination.current,
          onChange: (page, pageSize) => fetchCategories(page, pageSize),
          pageSize: pagination.pageSize,
          pageSizeOptions: ['10', '20', '50', '100'],
          showQuickJumper: true,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
          total: pagination.total,
        }}
        rowKey="categoryId"
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
