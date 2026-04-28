import {
  ExclamationCircleOutlined,
  CheckCircleFilled,
  ClockCircleFilled,
  ExclamationCircleFilled,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { Button, Modal, Tooltip, message } from 'antd';
import { useCallback, useEffect, useRef, useState, useImperativeHandle, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';

import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import BatchActionBar from '@/components/api-product/BatchActionBar';
import { DataTable } from '@/components/common/DataTable';
import { apiProductApi } from '@/lib/api';
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

function renderStatusTag(status: string) {
  if (status === 'PUBLISHED') {
    return (
      <div className="flex items-center gap-1.5 text-xs">
        <CheckCircleFilled className="text-green-500" style={{ fontSize: '12px' }} />
        <span className="text-gray-700">已发布</span>
      </div>
    );
  }
  if (status === 'READY') {
    return (
      <div className="flex items-center gap-1.5 text-xs">
        <ClockCircleFilled className="text-blue-500" style={{ fontSize: '12px' }} />
        <span className="text-gray-700">待发布</span>
      </div>
    );
  }
  if (status === 'PENDING') {
    return (
      <div className="flex items-center gap-1.5 text-xs">
        <ExclamationCircleFilled className="text-yellow-500" style={{ fontSize: '12px' }} />
        <span className="text-gray-700">待配置</span>
      </div>
    );
  }
  return <span className="text-xs text-gray-700">{status}</span>;
}

const ProductTable = forwardRef<ProductTableRef, ProductTableProps>(({ productType }, ref) => {
  const navigate = useNavigate();
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
      render: (_text: unknown, record: ApiProduct) => (
        <div className="min-w-0">
          <Tooltip placement="topLeft" title={record.name}>
            <button
              className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
              onClick={() => navigate(`/api-products/${record.productId}`)}
              type="button"
            >
              {record.name}
            </button>
          </Tooltip>
          <Tooltip title="点击复制">
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.productId).then(() => {
                  message.success('已复制到剪贴板');
                })
              }
              type="button"
            >
              {record.productId}
            </button>
          </Tooltip>
        </div>
      ),
      title: '产品名称/ID',
      width: 280,
    },
    {
      dataIndex: 'status',
      render: (status: string) => renderStatusTag(status),
      title: '状态',
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
      title: '描述',
    },
    {
      dataIndex: 'createAt',
      render: (createAt: string) => (
        <span className="text-xs text-gray-500">{createAt ? formatDateTime(createAt) : '-'}</span>
      ),
      title: '创建时间',
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
            编辑
          </Button>
          <Button
            className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.productId, record.name)}
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
          placeholder: '搜索产品名称',
          value: searchInput,
        }}
        toolbarRight={
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
          ) : undefined
        }
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
