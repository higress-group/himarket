import {
  SearchOutlined,
  ExclamationCircleOutlined,
  ApiOutlined,
  RobotOutlined,
  BulbOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Table, Input, Button, Select, Modal, Tooltip, message } from 'antd';
import { useCallback, useEffect, useState, useImperativeHandle, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';

import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import BatchActionBar from '@/components/api-product/BatchActionBar';
import McpServerIcon from '@/components/icons/McpServerIcon';
import { apiProductApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';

import type { TableProps } from 'antd';

// 产品类型标签映射
const TYPE_LABELS: Record<string, string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

// 状态配置
const STATUS_CONFIG: Record<string, { color: string; text: string }> = {
  PENDING: { color: '#faad14', text: '待配置' },
  PUBLISHED: { color: '#52c41a', text: '已发布' },
  READY: { color: '#1677ff', text: '待发布' },
};

export interface ProductTableProps {
  productType: 'MODEL_API' | 'MCP_SERVER' | 'AGENT_SKILL' | 'WORKER' | 'AGENT_API' | 'REST_API';
}

export interface ProductTableRef {
  handleCreate: () => void;
  refresh: () => void;
}

function getEmptyIcon(type: string) {
  const style = { color: '#d9d9d9', fontSize: '48px' };
  switch (type) {
    case 'REST_API':
      return <ApiOutlined style={style} />;
    case 'MCP_SERVER':
      return <McpServerIcon style={style} />;
    case 'AGENT_API':
      return <RobotOutlined style={style} />;
    case 'MODEL_API':
      return <BulbOutlined style={style} />;
    case 'AGENT_SKILL':
      return <ThunderboltOutlined style={style} />;
    case 'WORKER':
      return <UserOutlined style={style} />;
    default:
      return <ApiOutlined style={style} />;
  }
}

function getDownloadCount(product: ApiProduct): string | number {
  if (product.type === 'AGENT_SKILL') return product.skillConfig?.downloadCount ?? 0;
  if (product.type === 'WORKER') return product.workerConfig?.downloadCount ?? 0;
  return '-';
}

const ProductTable = forwardRef<ProductTableRef, ProductTableProps>(({ productType }, ref) => {
  const navigate = useNavigate();
  const [products, setProducts] = useState<ApiProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [nameFilter, setNameFilter] = useState('');
  const [sortBy, setSortBy] = useState<string | undefined>(
    productType === 'AGENT_SKILL' || productType === 'WORKER' ? 'UPDATED_AT' : undefined,
  );
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [modalVisible, setModalVisible] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ApiProduct | null>(null);

  const showSortControl = productType === 'AGENT_SKILL' || productType === 'WORKER';

  const fetchProducts = useCallback(
    (page = 1, size = 20, name = '') => {
      setLoading(true);
      const params: Record<string, string | number | undefined> = {
        page,
        size,
        type: productType,
      };
      if (name.trim()) params.name = name.trim();
      if (showSortControl && sortBy) params.sortBy = sortBy;

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
    [productType, showSortControl, sortBy],
  );

  useEffect(() => {
    setSearchInput('');
    setNameFilter('');
    setSelectedIds(new Set());
    setSortBy(showSortControl ? 'UPDATED_AT' : undefined);
    fetchProducts(1, 20, '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productType]);

  useEffect(() => {
    if (sortBy !== undefined) {
      fetchProducts(1, pagination.pageSize, nameFilter);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortBy, nameFilter, pagination.pageSize]);

  const handleSearch = () => {
    setNameFilter(searchInput);
    fetchProducts(1, pagination.pageSize, searchInput);
  };

  const handleClearSearch = () => {
    setSearchInput('');
    setNameFilter('');
    fetchProducts(1, pagination.pageSize, '');
  };

  const handleDelete = useCallback(
    (productId: string, productName: string) => {
      Modal.confirm({
        cancelText: '取消',
        content: `确定要删除API产品 "${productName}" 吗？此操作不可恢复。`,
        icon: <ExclamationCircleOutlined />,
        okText: '确认删除',
        okType: 'danger',
        onOk() {
          return apiProductApi.deleteApiProduct(productId).then(() => {
            message.success('API Product 删除成功');
            fetchProducts(pagination.current, pagination.pageSize);
          });
        },
        title: '确认删除',
      });
    },
    [fetchProducts, pagination],
  );

  const handleEdit = useCallback((product: ApiProduct) => {
    setEditingProduct(product);
    setModalVisible(true);
  }, []);

  const handleCreate = useCallback(() => {
    setEditingProduct(null);
    setModalVisible(true);
  }, []);

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

  // Table columns definition
  const columns: TableProps<ApiProduct>['columns'] = [
    {
      dataIndex: 'name',
      ellipsis: { showTitle: false },
      render: (_text: unknown, record: ApiProduct) => (
        <Tooltip placement="topLeft" title={record.name}>
          <button
            className="text-colorPrimary hover:text-colorPrimary/80 font-medium cursor-pointer bg-transparent border-none p-0"
            onClick={() => navigate(`/api-products/${record.productId}`)}
            type="button"
          >
            {record.name}
          </button>
        </Tooltip>
      ),
      title: '产品名称',
      width: 200,
    },
    {
      dataIndex: 'status',
      render: (status: string) => {
        const config = STATUS_CONFIG[status] || { color: '#d9d9d9', text: status };
        return (
          <div className="flex items-center gap-2">
            <span
              className="inline-block w-2 h-2 rounded-full"
              style={{ backgroundColor: config.color }}
            />
            <span className="text-sm">{config.text}</span>
          </div>
        );
      },
      title: '产品状态',
      width: 120,
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
    },
    ...(productType === 'AGENT_SKILL' || productType === 'WORKER'
      ? [
          {
            render: (_text: unknown, record: ApiProduct) => getDownloadCount(record),
            title: '下载计数',
            width: 100,
          },
        ]
      : []),
    {
      render: (_text: unknown, record: ApiProduct) => (
        <div className="flex items-center gap-1">
          <Button onClick={() => handleEdit(record)} size="small" type="link">
            编辑
          </Button>
          <Button
            danger
            onClick={() => handleDelete(record.productId, record.name)}
            size="small"
            type="link"
          >
            删除
          </Button>
        </div>
      ),
      title: '操作',
      width: 120,
    },
  ];

  return (
    <div>
      {/* Search & Sort toolbar */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          {showSortControl && (
            <Select
              onChange={(value) => setSortBy(value)}
              options={[
                { label: '最多下载', value: 'DOWNLOAD_COUNT' },
                { label: '最近更新', value: 'UPDATED_AT' },
              ]}
              size="middle"
              style={{ width: 140 }}
              value={sortBy || 'UPDATED_AT'}
            />
          )}
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
              placeholder="搜索产品名称"
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
      </div>

      {/* Batch action bar */}
      {selectedIds.size > 0 && (
        <BatchActionBar
          onCancel={() => setSelectedIds(new Set())}
          onComplete={() => {
            setSelectedIds(new Set());
            fetchProducts(pagination.current, pagination.pageSize);
          }}
          products={products}
          selectedIds={selectedIds}
        />
      )}

      {/* Table */}
      <Table<ApiProduct>
        columns={columns}
        dataSource={products}
        loading={loading}
        locale={{
          emptyText: (
            <div className="flex flex-col items-center justify-center py-12 text-gray-400">
              {getEmptyIcon(productType)}
              <p className="text-base mt-3">暂无 {TYPE_LABELS[productType] || productType} 产品</p>
            </div>
          ),
        }}
        pagination={{
          current: pagination.current,
          onChange: (page, pageSize) => fetchProducts(page, pageSize),
          pageSize: pagination.pageSize,
          pageSizeOptions: ['10', '20', '50', '100'],
          showQuickJumper: true,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
          total: pagination.total,
        }}
        rowKey="productId"
        rowSelection={rowSelection}
      />

      {/* Create/Edit modal */}
      <ApiProductFormModal
        initialData={editingProduct || undefined}
        onCancel={handleModalCancel}
        onSuccess={handleModalSuccess}
        productId={editingProduct?.productId}
        visible={modalVisible}
      />
    </div>
  );
});

ProductTable.displayName = 'ProductTable';

export default ProductTable;
