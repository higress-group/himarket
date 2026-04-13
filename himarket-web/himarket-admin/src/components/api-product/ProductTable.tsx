import { useCallback, useEffect, useState, useImperativeHandle, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Input, Button, Select, Modal, Tooltip, message } from 'antd';
import type { TableProps } from 'antd';
import {
  SearchOutlined,
  ExclamationCircleOutlined,
  ApiOutlined,
  RobotOutlined,
  BulbOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import McpServerIcon from '@/components/icons/McpServerIcon';
import { apiProductApi } from '@/lib/api';
import BatchActionBar from '@/components/api-product/BatchActionBar';
import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import type { ApiProduct } from '@/types/api-product';

// 产品类型标签映射
const TYPE_LABELS: Record<string, string> = {
  MODEL_API: 'Model API',
  MCP_SERVER: 'MCP Server',
  AGENT_SKILL: 'Agent Skill',
  WORKER: 'Worker',
  AGENT_API: 'Agent API',
  REST_API: 'REST API',
};

// 状态配置
const STATUS_CONFIG: Record<string, { color: string; text: string }> = {
  PENDING: { color: '#faad14', text: '待配置' },
  READY: { color: '#1677ff', text: '待发布' },
  PUBLISHED: { color: '#52c41a', text: '已发布' },
};

export interface ProductTableProps {
  productType: 'MODEL_API' | 'MCP_SERVER' | 'AGENT_SKILL' | 'WORKER' | 'AGENT_API' | 'REST_API';
}

export interface ProductTableRef {
  handleCreate: () => void;
  refresh: () => void;
}

function getTypeIcon(type: string, fontSize = '14px') {
  const style = { fontSize, color: '#6366f1' };
  switch (type) {
    case 'REST_API': return <ApiOutlined style={style} />;
    case 'MCP_SERVER': return <McpServerIcon style={style} />;
    case 'AGENT_API': return <RobotOutlined style={style} />;
    case 'MODEL_API': return <BulbOutlined style={style} />;
    case 'AGENT_SKILL': return <ThunderboltOutlined style={style} />;
    case 'WORKER': return <UserOutlined style={style} />;
    default: return <ApiOutlined style={style} />;
  }
}

function getEmptyIcon(type: string) {
  const style = { fontSize: '48px', color: '#d9d9d9' };
  switch (type) {
    case 'REST_API': return <ApiOutlined style={style} />;
    case 'MCP_SERVER': return <McpServerIcon style={style} />;
    case 'AGENT_API': return <RobotOutlined style={style} />;
    case 'MODEL_API': return <BulbOutlined style={style} />;
    case 'AGENT_SKILL': return <ThunderboltOutlined style={style} />;
    case 'WORKER': return <UserOutlined style={style} />;
    default: return <ApiOutlined style={style} />;
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
    (productType === 'AGENT_SKILL' || productType === 'WORKER') ? 'UPDATED_AT' : undefined
  );
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [modalVisible, setModalVisible] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ApiProduct | null>(null);

  const showSortControl = productType === 'AGENT_SKILL' || productType === 'WORKER';

  const fetchProducts = useCallback((page = 1, size = 20, name = nameFilter) => {
    setLoading(true);
    const params: Record<string, any> = { page, size, type: productType };
    if (name.trim()) params.name = name.trim();
    if (showSortControl && sortBy) params.sortBy = sortBy;

    apiProductApi.getApiProducts(params).then((res: any) => {
      setProducts(res.data.content);
      setPagination({ current: page, pageSize: size, total: res.data.totalElements || 0 });
    }).finally(() => setLoading(false));
  }, [productType, nameFilter, sortBy, showSortControl]);

  useEffect(() => {
    setSearchInput('');
    setNameFilter('');
    setSelectedIds(new Set());
    setSortBy(showSortControl ? 'UPDATED_AT' : undefined);
    fetchProducts(1, 20, '');
  }, [productType]);

  useEffect(() => {
    if (sortBy !== undefined) {
      fetchProducts(1, pagination.pageSize, nameFilter);
    }
  }, [sortBy]);

  const handleSearch = () => {
    setNameFilter(searchInput);
    fetchProducts(1, pagination.pageSize, searchInput);
  };

  const handleClearSearch = () => {
    setSearchInput('');
    setNameFilter('');
    fetchProducts(1, pagination.pageSize, '');
  };

  const handleDelete = useCallback((productId: string, productName: string) => {
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除API产品 "${productName}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk() {
        return apiProductApi.deleteApiProduct(productId).then(() => {
          message.success('API Product 删除成功');
          fetchProducts(pagination.current, pagination.pageSize);
        });
      },
    });
  }, [fetchProducts, pagination]);

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

  useImperativeHandle(ref, () => ({
    handleCreate,
    refresh: () => fetchProducts(pagination.current, pagination.pageSize),
  }), [handleCreate, fetchProducts, pagination]);

  // Row selection for batch operations
  const rowSelection: TableProps<ApiProduct>['rowSelection'] = {
    selectedRowKeys: [...selectedIds],
    onChange: (selectedRowKeys) => {
      setSelectedIds(new Set(selectedRowKeys as string[]));
    },
  };

  // Table columns definition
  const columns: TableProps<ApiProduct>['columns'] = [
    {
      title: '产品名称',
      dataIndex: 'name',
      width: 200,
      ellipsis: { showTitle: false },
      render: (_: any, record: ApiProduct) => (
        <Tooltip placement="topLeft" title={record.name}>
          <a
            className="text-colorPrimary hover:text-colorPrimary/80 font-medium cursor-pointer"
            onClick={() => navigate(`/api-products/${record.productId}`)}
          >
            {record.name}
          </a>
        </Tooltip>
      ),
    },
    {
      title: '产品状态',
      dataIndex: 'status',
      width: 120,
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
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: { showTitle: false },
      render: (description: string) => (
        <Tooltip placement="topLeft" title={description}>
          {description || ''}
        </Tooltip>
      ),
    },
    ...(productType === 'AGENT_SKILL' || productType === 'WORKER' ? [{
      title: '下载计数',
      width: 100,
      render: (_: any, record: ApiProduct) => getDownloadCount(record),
    }] : []),
    {
      title: '操作',
      width: 120,
      render: (_: any, record: ApiProduct) => (
        <div className="flex items-center gap-1">
          <Button type="link" size="small" onClick={() => handleEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger onClick={() => handleDelete(record.productId, record.name)}>删除</Button>
        </div>
      ),
    },
  ];

  return (
    <div>
      {/* Search & Sort toolbar */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          {showSortControl && (
            <Select
              value={sortBy || 'UPDATED_AT'}
              onChange={(value) => setSortBy(value)}
              size="middle"
              style={{ width: 140 }}
              options={[
                { label: '最多下载', value: 'DOWNLOAD_COUNT' },
                { label: '最近更新', value: 'UPDATED_AT' },
              ]}
            />
          )}
          <div
            className="flex items-center border border-gray-300 rounded-md overflow-hidden hover:border-colorPrimary focus-within:border-colorPrimary"
            style={{ minWidth: 260 }}
          >
            <Input
              placeholder="搜索产品名称"
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
      </div>

      {/* Batch action bar */}
      {selectedIds.size > 0 && (
        <BatchActionBar
          selectedIds={selectedIds}
          products={products}
          onComplete={() => {
            setSelectedIds(new Set());
            fetchProducts(pagination.current, pagination.pageSize);
          }}
          onCancel={() => setSelectedIds(new Set())}
        />
      )}

      {/* Table */}
      <Table<ApiProduct>
        rowKey="productId"
        columns={columns}
        dataSource={products}
        loading={loading}
        rowSelection={rowSelection}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          pageSizeOptions: ['10', '20', '50', '100'],
          onChange: (page, pageSize) => fetchProducts(page, pageSize),
        }}
        locale={{
          emptyText: (
            <div className="flex flex-col items-center justify-center py-12 text-gray-400">
              {getEmptyIcon(productType)}
              <p className="text-base mt-3">暂无 {TYPE_LABELS[productType] || productType} 产品</p>
            </div>
          ),
        }}
      />

      {/* Create/Edit modal */}
      <ApiProductFormModal
        visible={modalVisible}
        onCancel={handleModalCancel}
        onSuccess={handleModalSuccess}
        productId={editingProduct?.productId}
        initialData={editingProduct || undefined}
      />
    </div>
  );
});

ProductTable.displayName = 'ProductTable';

export default ProductTable;
