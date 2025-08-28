import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { MenuProps } from 'antd';
import { Badge, Button, Card, Dropdown, Modal, message, Pagination, Skeleton } from 'antd';
import type { ApiProduct } from '@/types/api-product';
import { ApiOutlined, ClockCircleOutlined, MoreOutlined, PlusOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { apiProductApi } from '@/lib/api';
import { getStatusBadgeVariant } from '@/lib/utils';
import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import { AdvancedSearch, SearchParam } from '@/components/common/AdvancedSearch';

// 优化的产品卡片组件
const ProductCard = memo(({ product, onNavigate, handleRefresh, onEdit }: {
  product: ApiProduct;
  onNavigate: (productId: string) => void;
  handleRefresh: () => void;
  onEdit: (product: ApiProduct) => void;
}) => {
  const getTypeIcon = (icon: string, type: string) => {
    if (icon && icon.includes("value=")) {
      const startIndex = icon.indexOf("value=") + 6;
      const endIndex = icon.length - 1;
      const base64Data = icon.substring(startIndex, endIndex).trim();
      return <img src={base64Data} alt="icon" style={{ borderRadius: '8px', minHeight: '40px' }} />
    } else {
       return type === "REST_API" ? <ApiOutlined className="h-4 w-4" /> : <ClockCircleOutlined className="h-4 w-4" />
     }
  }

  const getTypeBadgeVariant = (type: string) => {
    return type === "REST_API" ? "blue" : "purple"
  }
  const handleClick = useCallback(() => {
    onNavigate(product.productId)
  }, [product.productId, onNavigate]);

  const handleDelete = useCallback((productId: string, productName: string, e?: React.MouseEvent | any) => {
    if (e && e.stopPropagation) e.stopPropagation();
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除API产品 "${productName}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk() {
        apiProductApi.deleteApiProduct(productId).then(() => {
          message.success('API Product 删除成功');
          handleRefresh();
        });
      },
    });
  }, [handleRefresh]);

  const handleEdit = useCallback((e?: React.MouseEvent | any) => {
    if (e && e?.domEvent?.stopPropagation) e.domEvent.stopPropagation();
    onEdit(product);
  }, [product, onEdit]);

  const dropdownItems: MenuProps['items'] = [
    {
      key: 'edit',
      label: '编辑',
      onClick: handleEdit,
    },
    {
      type: 'divider',
    },
    {
      key: 'delete',
      label: '删除',
      danger: true,
      onClick: (info: any) => handleDelete(product.productId, product.name, info?.domEvent),
    },
  ]

  return (
    <Card
      className="hover:shadow-lg transition-shadow cursor-pointer"
      onClick={handleClick}
      bodyStyle={{ padding: '16px' }}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100">
            {getTypeIcon(product.icon || '', product.type)}
          </div>
          <div>
            <h3 className="text-lg font-semibold">{product.name}</h3>
            <div className="flex items-center gap-2 mt-1 flex-wrap">
              {product.category && <Badge color="green" text={product.category} />}
              <Badge color={getTypeBadgeVariant(product.type)} text={product.type === "REST_API" ? "REST API" : "MCP Server"} />
              <Badge color={getStatusBadgeVariant(product.status)} text={product.status === "PENDING" ? "待配置" : product.status === "READY" ? "待发布" : "已发布"} />
            </div>
          </div>
        </div>
        <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
          <Button
            type="text"
            icon={<MoreOutlined />}
            onClick={(e) => e.stopPropagation()}
          />
        </Dropdown>
      </div>

      <div className="space-y-4">
        {product.description && (
          <p className="text-sm text-gray-600">{product.description}</p>
        )}

        <div className="space-y-2 text-sm">

          <div className="flex justify-between">
            <span className="text-gray-500">Product ID</span>
            <span>{product.productId}</span>
          </div>
        </div>
      </div>
    </Card>
  )
})

ProductCard.displayName = 'ProductCard'

export default function ApiProducts() {
  const navigate = useNavigate();
  const [apiProducts, setApiProducts] = useState<ApiProduct[]>([]);
  const [filters, setFilters] = useState<{ type?: string, name?: string }>({});
  const [loading, setLoading] = useState(true); // 初始状态为 loading
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 12,
    total: 0,
  });

  const [modalVisible, setModalVisible] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ApiProduct | null>(null);

  const fetchApiProducts = useCallback((page = 1, size = 12, extraFilters?: { type?: string }) => {
    setLoading(true);
    const effective = extraFilters ?? filters;
    apiProductApi.getApiProducts({ page, size, ...effective }).then((res: any) => {
      const products = res.data.content;
      setApiProducts(products);
      setPagination({
        current: page,
        pageSize: size,
        total: res.data.totalElements || 0,
      });
    }).finally(() => {
      setLoading(false);
    });
  }, [filters]);

  useEffect(() => {
    fetchApiProducts(1, 12);
  }, [fetchApiProducts]);

  // 预设的产品类型（无 "All"）
  const typeOptions = useMemo(() => (
    [
      { label: 'REST API', value: 'REST_API' },
      { label: 'MCP Server', value: 'MCP_SERVER' },
    ]
  ), [])

  // 高级搜索配置
  const searchParamsList: SearchParam[] = useMemo(() => [
    {
      label: '产品名称',
      name: 'name',
      placeholder: '请输入产品名称',
      type: 'input'
    },
    // {
    //   label: '产品分类',
    //   name: 'category',
    //   placeholder: '选择分类',
    //   type: 'select',
    //   optionList: categories.filter(cat => cat !== 'All').map(cat => ({
    //     label: cat,
    //     value: cat
    //   }))
    // },
    {
      label: '产品类型',
      name: 'type',
      placeholder: '选择类型',
      type: 'select',
      optionList: typeOptions,
    }
  ], [typeOptions]);

  // 搜索处理函数（仅服务端过滤）
  const handleSearch = (searchName: string, searchValue: string) => {
      const next = { [searchName]: searchValue || undefined };
      setFilters(next);
      fetchApiProducts(1, pagination.pageSize, next);
  };

  const handleClearSearch = () => {
    // 清空筛选并重新请求列表
    const cleared = {} as { type?: string };
    setFilters(cleared);
    fetchApiProducts(1, pagination.pageSize, cleared);
  };

  // 处理分页变化
  const handlePaginationChange = (page: number, pageSize: number) => {
    fetchApiProducts(page, pageSize);
  };

  // 直接使用服务端返回的列表

  // 优化的导航处理函数
  const handleNavigateToProduct = useCallback((productId: string) => {
    navigate(`/api-products/detail?productId=${productId}`);
  }, [navigate]);

  // 处理创建
  const handleCreate = () => {
    setEditingProduct(null);
    setModalVisible(true);
  };

  // 处理编辑
  const handleEdit = (product: ApiProduct) => {
    setEditingProduct(product);
    setModalVisible(true);
  };

  // 处理模态框成功
  const handleModalSuccess = () => {
    setModalVisible(false);
    setEditingProduct(null);
    fetchApiProducts(pagination.current, pagination.pageSize);
  };

  // 处理模态框取消
  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingProduct(null);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">API Products</h1>
          <p className="text-gray-500 mt-2">
            管理和配置您的API产品
          </p>
        </div>
        <Button onClick={handleCreate} type="primary" icon={<PlusOutlined/>}>
          创建 API Product
        </Button>
      </div>

      {/* 高级搜索 */}
      <AdvancedSearch
        searchParamsList={searchParamsList}
        onSearch={handleSearch}
        onClear={handleClearSearch}
        className="mb-4"
      />

      {loading ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: pagination.pageSize || 12 }).map((_, index) => (
            <div key={index} className="h-full rounded-lg shadow-lg bg-white p-4">
              <div className="flex items-start space-x-4">
                <Skeleton.Avatar size={48} active />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-2">
                    <Skeleton.Input active size="small" style={{ width: 120 }} />
                    <Skeleton.Input active size="small" style={{ width: 60 }} />
                  </div>
                  <Skeleton.Input active size="small" style={{ width: '100%', marginBottom: 12 }} />
                  <Skeleton.Input active size="small" style={{ width: '80%', marginBottom: 8 }} />
                  <div className="flex items-center justify-between">
                    <Skeleton.Input active size="small" style={{ width: 60 }} />
                    <Skeleton.Input active size="small" style={{ width: 80 }} />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {apiProducts.map((product) => (
              <ProductCard
                key={product.productId}
                product={product}
                onNavigate={handleNavigateToProduct}
                handleRefresh={() => fetchApiProducts(pagination.current, pagination.pageSize)}
                onEdit={handleEdit}
              />
            ))}
          </div>

          {pagination.total > 0 && (
            <div className="flex justify-center mt-6">
              <Pagination
                current={pagination.current}
                pageSize={pagination.pageSize}
                total={pagination.total}
                onChange={handlePaginationChange}
                showSizeChanger
                showQuickJumper
                showTotal={(total, range) => `第 ${range[0]}-${range[1]} 条/共 ${total} 条`}
                pageSizeOptions={['6', '12', '24', '48']}
              />
            </div>
          )}
        </>
      )}

      <ApiProductFormModal
        visible={modalVisible}
        onCancel={handleModalCancel}
        onSuccess={handleModalSuccess}
        productId={editingProduct?.productId}
        initialData={editingProduct || undefined}
      />
    </div>
  )
}
