import { useCallback, useEffect, useState, useImperativeHandle, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Input, Button, Modal, Tooltip, Dropdown, message, Empty } from 'antd';
import type { TableProps, MenuProps } from 'antd';
import {
  SearchOutlined,
  ExclamationCircleOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import { getProductCategoriesByPage, deleteProductCategory } from '@/lib/productCategoryApi';
import CategoryFormModal from '@/components/product-category/CategoryFormModal';
import type { ProductCategory, QueryProductCategoryParam } from '@/types/product-category';

export interface CategoryTableRef {
  handleCreate: () => void;
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
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

  const fetchCategories = useCallback((page = 1, size = 20, name = nameFilter) => {
    setLoading(true);
    const params: QueryProductCategoryParam = name.trim() ? { name: name.trim() } : {};
    getProductCategoriesByPage(page - 1, size, params)
      .then((res) => {
        setCategories(res.data.content || []);
        setPagination({
          current: res.data.number + 1,
          pageSize: res.data.size,
          total: res.data.totalElements,
        });
      })
      .catch(() => {
        message.error('获取产品类别失败');
      })
      .finally(() => setLoading(false));
  }, [nameFilter]);

  useEffect(() => {
    fetchCategories();
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

  const handleDelete = useCallback((categoryId: string, categoryName: string) => {
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除类别 "${categoryName}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk() {
        return deleteProductCategory(categoryId).then(() => {
          message.success('类别删除成功');
          fetchCategories(pagination.current, pagination.pageSize);
        }).catch(() => {
          message.error('删除类别失败，可能该类别正在使用中');
        });
      },
    });
  }, [fetchCategories, pagination]);

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

  useImperativeHandle(ref, () => ({
    handleCreate,
  }), [handleCreate]);

  const columns: TableProps<ProductCategory>['columns'] = [
    {
      title: '分类名称',
      dataIndex: 'name',
      render: (_: any, record: ProductCategory) => (
        <a
          className="text-colorPrimary hover:text-colorPrimary/80 font-medium cursor-pointer"
          onClick={() => navigate(`/product-categories/${record.categoryId}`)}
        >
          {record.name}
        </a>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 300,
      ellipsis: { showTitle: false },
      render: (description: string) => (
        <Tooltip placement="topLeft" title={description}>
          {description || ''}
        </Tooltip>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createAt',
      width: 180,
      render: (val: string) => formatDate(val),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 180,
      render: (val: string) => formatDate(val),
    },
    {
      title: '操作',
      width: 120,
      render: (_: any, record: ProductCategory) => {
        const items: MenuProps['items'] = [
          { key: 'edit', label: '编辑', onClick: () => handleEdit(record) },
          { type: 'divider' },
          {
            key: 'delete',
            label: '删除',
            danger: true,
            onClick: () => handleDelete(record.categoryId, record.name),
          },
        ];
        return (
          <Dropdown menu={{ items }} trigger={['click']}>
            <Button type="text" icon={<MoreOutlined />} onClick={(e) => e.stopPropagation()} />
          </Dropdown>
        );
      },
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
            placeholder="搜索类别名称"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
            onClear={handleClearSearch}
            size="middle"
            variant="borderless"
            className="border-0"
          />
          <Button
            icon={<SearchOutlined />}
            onClick={handleSearch}
            style={{ width: 40 }}
            className="border-0 rounded-none"
            type="text"
          />
        </div>
      </div>

      {/* Table */}
      <Table<ProductCategory>
        rowKey="categoryId"
        columns={columns}
        dataSource={categories}
        loading={loading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          pageSizeOptions: ['10', '20', '50', '100'],
          onChange: (page, pageSize) => fetchCategories(page, pageSize),
        }}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无产品类别"
            />
          ),
        }}
      />

      {/* Create/Edit modal */}
      <CategoryFormModal
        visible={modalVisible}
        onCancel={handleModalCancel}
        onSuccess={handleModalSuccess}
        category={editingCategory}
        isEdit={!!editingCategory}
      />
    </div>
  );
});

CategoryTable.displayName = 'CategoryTable';

export default CategoryTable;
