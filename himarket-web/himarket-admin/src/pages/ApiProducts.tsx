import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { MenuProps } from 'antd';
import { Button, Card, Dropdown, Modal, message, Pagination, Skeleton, Input, Select, Tag, Space } from 'antd';
import type { ApiProduct, ProductIcon } from '@/types/api-product';
import { ApiOutlined, MoreOutlined, PlusOutlined, ExclamationCircleOutlined, ExclamationCircleFilled, ClockCircleFilled, CheckCircleFilled, SearchOutlined, RobotOutlined, BulbOutlined } from '@ant-design/icons';
import McpServerIcon from '@/components/icons/McpServerIcon';
import { apiProductApi } from '@/lib/api';
import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import { getProductCategories } from '@/lib/productCategoryApi';
import type { ProductCategory } from '@/types/product-category';

// 优化的产品卡片组件
const ProductCard = memo(({ product, onNavigate, handleRefresh, onEdit }: {
  product: ApiProduct;
  onNavigate: (productId: string) => void;
  handleRefresh: () => void;
  onEdit: (product: ApiProduct) => void;
}) => {
  // 处理产品图标的函数
  const getTypeIcon = (icon: ProductIcon | null | undefined, type: string) => {
    if (icon) {
      switch (icon.type) {
        case "URL":
          return <img src={icon.value} alt="icon" style={{ borderRadius: '8px', minHeight: '40px', width: '40px', height: '40px', objectFit: 'cover' }} />
        case "BASE64":
          // 如果value已经包含data URL前缀，直接使用；否则添加前缀
          const src = icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`;
          return <img src={src} alt="icon" style={{ borderRadius: '8px', minHeight: '40px', width: '40px', height: '40px', objectFit: 'cover' }} />
        default:
          return getDefaultIcon(type)
      }
    } else {
      return getDefaultIcon(type)
    }
  }

  const getDefaultIcon = (type: string) => {
    if (type === "REST_API") {
      return <ApiOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} />
    } else if (type === "MCP_SERVER") {
      return <McpServerIcon style={{ fontSize: '16px', width: '16px', height: '16px' }} />
    } else if (type === "AGENT_API") {
      return <RobotOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} />
    } else if (type === "MODEL_API") {
      return <BulbOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} />
    }
    return <ApiOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} />
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

  const getTypeLabel = (type: string) => {
    switch (type) {
      case 'REST_API': return 'REST API';
      case 'MCP_SERVER': return 'MCP Server';
      case 'AGENT_API': return 'Agent API';
      case 'MODEL_API': return 'Model API';
      default: return type;
    }
  };

  return (
    <Card
      className="hover:shadow-lg transition-shadow cursor-pointer rounded-xl border border-gray-200 shadow-sm hover:border-blue-300"
      onClick={handleClick}
      bodyStyle={{ padding: '16px' }}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100">
            {getTypeIcon(product.icon, product.type)}
          </div>
          <div>
            <h3 className="text-lg font-semibold">{product.name}</h3>
            <div className="flex items-center gap-3 mt-1 flex-wrap">
              <div className="flex items-center">
                {product.type === "REST_API" ? (
                  <ApiOutlined className="text-blue-500 mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                ) : product.type === "AGENT_API" ? (
                  <RobotOutlined className="text-gray-600 mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                ) : product.type === "MODEL_API" ? (
                  <BulbOutlined className="text-gray-600 mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                ) : (
                  <McpServerIcon className="text-black mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                )}
                <span className="text-xs text-gray-700">
                  {getTypeLabel(product.type)}
                </span>
              </div>
              <div className="flex items-center">
                {product.status === "PENDING" ? (
                  <ExclamationCircleFilled className="text-yellow-500 mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                ) : product.status === "READY" ? (
                  <ClockCircleFilled className="text-blue-500 mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                ) : (
                  <CheckCircleFilled className="text-green-500 mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                )}
                <span className="text-xs text-gray-700">
                  {product.status === "PENDING" ? "待配置" : product.status === "READY" ? "待发布" : "已发布"}
                </span>
              </div>
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

      </div>
    </Card>
  )
})

ProductCard.displayName = 'ProductCard'

export default function ApiProducts() {
  const navigate = useNavigate();
  const [apiProducts, setApiProducts] = useState<ApiProduct[]>([]);
  const [filters, setFilters] = useState<{ type?: string, name?: string, categoryIds?: string }>({});
  const [loading, setLoading] = useState(true); // 初始状态为 loading
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 12,
    total: 0,
  });

  const [modalVisible, setModalVisible] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ApiProduct | null>(null);
  const [productCategories, setProductCategories] = useState<ProductCategory[]>([]);

  // 搜索状态
  const [searchValue, setSearchValue] = useState('');
  const [searchType, setSearchType] = useState<'name' | 'type' | 'category'>('name');
  const [activeFilters, setActiveFilters] = useState<Array<{ type: string; value: string; label: string }>>([]);

  // 获取产品类别列表
  const fetchProductCategories = async () => {
    try {
      const response = await getProductCategories();
      setProductCategories(response.data.content || []);
    } catch (error) {
      console.error("获取产品类别失败:", error);
      message.error("获取产品类别失败");
    }
  };

  const fetchApiProducts = useCallback((page = 1, size = 12, queryFilters?: { type?: string, name?: string, categoryIds?: string }) => {
    setLoading(true);
    const params = { page, size, ...(queryFilters || {}) };
    apiProductApi.getApiProducts(params).then((res: any) => {
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
  }, []); // 不依赖任何状态，避免无限循环

  useEffect(() => {
    fetchApiProducts(1, 12);
    fetchProductCategories();
  }, []); // 只在组件初始化时执行一次

  // 产品类型选项
  const typeOptions = [
    { label: 'REST API', value: 'REST_API' },
    { label: 'MCP Server', value: 'MCP_SERVER' },
    { label: 'Agent API', value: 'AGENT_API' },
    { label: 'Model API', value: 'MODEL_API' },
  ];

  // 搜索类型选项
  const searchTypeOptions = [
    { label: '产品名称', value: 'name' as const },
    { label: '产品类型', value: 'type' as const },
    { label: '产品类别', value: 'category' as const },
  ];

  // 搜索处理函数
  const handleSearch = () => {
    if (searchValue.trim()) {
      let labelText = '';
      let filterValue = searchValue.trim();
      
      if (searchType === 'name') {
        labelText = `产品名称：${searchValue.trim()}`;
      } else if (searchType === 'type') {
        const typeLabel = typeOptions.find(opt => opt.value === searchValue.trim())?.label || searchValue.trim();
        labelText = `产品类型：${typeLabel}`;
      } else if (searchType === 'category') {
        const categoryLabel = productCategories.find(cat => cat.categoryId === searchValue)?.name || searchValue.trim();
        labelText = `产品类别：${categoryLabel}`;
        // 对于类别搜索，使用 categoryIds 作为参数名
        filterValue = searchValue.trim();
      }
      
      const newFilter = { type: searchType, value: filterValue, label: labelText };
      const updatedFilters = activeFilters.filter(f => f.type !== searchType);
      updatedFilters.push(newFilter);
      setActiveFilters(updatedFilters);
      
      const filters: { type?: string, name?: string, categoryIds?: string } = {};
      updatedFilters.forEach(filter => {
        if (filter.type === 'type' || filter.type === 'name') {
          filters[filter.type] = filter.value;
        } else if (filter.type === 'category') {
          // 类别筛选使用 categoryIds 作为参数名
          filters.categoryIds = filter.value;
        }
      });
      
      setFilters(filters);
      fetchApiProducts(1, pagination.pageSize, filters);
      setSearchValue('');
    }
  };

  // 移除单个筛选条件
  const removeFilter = (filterType: string) => {
    const updatedFilters = activeFilters.filter(f => f.type !== filterType);
    setActiveFilters(updatedFilters);
    
    const newFilters: { type?: string, name?: string, categoryIds?: string } = {};
    updatedFilters.forEach(filter => {
      if (filter.type === 'type' || filter.type === 'name') {
        newFilters[filter.type] = filter.value;
      } else if (filter.type === 'category') {
        // 类别筛选使用 categoryIds 作为参数名
        newFilters.categoryIds = filter.value;
      }
    });
    
    setFilters(newFilters);
    fetchApiProducts(1, pagination.pageSize, newFilters);
  };

  // 清空所有筛选条件
  const clearAllFilters = () => {
    setActiveFilters([]);
    setFilters({});
    fetchApiProducts(1, pagination.pageSize, {});
  };

  // 处理分页变化
  const handlePaginationChange = (page: number, pageSize: number) => {
    fetchApiProducts(page, pageSize, filters); // 传递当前filters
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
    fetchApiProducts(pagination.current, pagination.pageSize, filters);
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

      {/* 搜索和筛选 */}
      <div className="space-y-4">
        {/* 搜索框 */}
        <div className="flex max-w-xl border border-gray-300 rounded-md overflow-hidden hover:border-blue-500 focus-within:border-blue-500 focus-within:shadow-sm">
          {/* 左侧：搜索类型选择器 */}
          <Select
            value={searchType}
            onChange={setSearchType}
            style={{ 
              width: 120,
              backgroundColor: '#f5f5f5',
            }}
            className="h-10 border-0 rounded-none"
            size="large"
            variant="borderless"
          >
            {searchTypeOptions.map(option => (
              <Select.Option key={option.value} value={option.value}>
                {option.label}
              </Select.Option>
            ))}
          </Select>

          {/* 分隔线 */}
          <div className="w-px bg-gray-300 self-stretch"></div>

          {/* 中间：搜索值输入框或选择框 */}
          {searchType === 'type' ? (
            <Select
              placeholder="请选择产品类型"
              value={searchValue}
              onChange={(value) => {
                setSearchValue(value);
                // 对于类型选择，立即执行搜索
                if (value) {
                  const typeLabel = typeOptions.find(opt => opt.value === value)?.label || value;
                  const labelText = `产品类型：${typeLabel}`;
                  const newFilter = { type: 'type', value, label: labelText };
                  const updatedFilters = activeFilters.filter(f => f.type !== 'type');
                  updatedFilters.push(newFilter);
                  setActiveFilters(updatedFilters);
                  
                  const filters: { type?: string, name?: string, categoryIds?: string } = {};
                  updatedFilters.forEach(filter => {
                    if (filter.type === 'type' || filter.type === 'name') {
                      filters[filter.type] = filter.value;
                    } else if (filter.type === 'category') {
                      // 类别筛选使用 categoryIds 作为参数名
                      filters.categoryIds = filter.value;
                    }
                  });
                  
                  setFilters(filters);
                  fetchApiProducts(1, pagination.pageSize, filters);
                  setSearchValue('');
                }
              }}
              style={{ 
                flex: 1,
              }}
              allowClear
              onClear={clearAllFilters}
              className="h-10 border-0 rounded-none"
              size="large"
              variant="borderless"
            >
              {typeOptions.map(option => (
                <Select.Option key={option.value} value={option.value}>
                  {option.label}
                </Select.Option>
              ))}
            </Select>
          ) : searchType === 'category' ? (
            <Select
              placeholder="请选择产品类别"
              value={searchValue}
              onChange={(value) => {
                setSearchValue(value);
                // 对于类别选择，立即执行搜索
                if (value) {
                  const categoryLabel = productCategories.find(cat => cat.categoryId === value)?.name || value;
                  const labelText = `产品类别：${categoryLabel}`;
                  const newFilter = { type: 'category', value, label: labelText };
                  const updatedFilters = activeFilters.filter(f => f.type !== 'category');
                  updatedFilters.push(newFilter);
                  setActiveFilters(updatedFilters);

                  const filters: { type?: string, name?: string, categoryIds?: string } = {};
                  updatedFilters.forEach(filter => {
                    if (filter.type === 'type' || filter.type === 'name') {
                      filters[filter.type] = filter.value;
                    } else if (filter.type === 'category') {
                      // 类别筛选使用 categoryIds 作为参数名
                      filters.categoryIds = filter.value;
                    }
                  });

                  setFilters(filters);
                  fetchApiProducts(1, pagination.pageSize, filters);
                  setSearchValue('');
                }
              }}
              style={{
                flex: 1,
              }}
              allowClear
              onClear={clearAllFilters}
              className="h-10 border-0 rounded-none"
              size="large"
              variant="borderless"
            >
              {productCategories.map(category => (
                <Select.Option key={category.categoryId} value={category.categoryId}>
                  {category.name}
                </Select.Option>
              ))}
            </Select>
          ) : (
            <Input
              placeholder="请输入要检索的产品名称"
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              style={{ 
                flex: 1,
              }}
              onPressEnter={handleSearch}
              allowClear
              onClear={() => setSearchValue('')}
              size="large"
              className="h-10 border-0 rounded-none"
              variant="borderless"
            />
          )}

          {/* 分隔线 */}
          <div className="w-px bg-gray-300 self-stretch"></div>

          {/* 右侧：搜索按钮 */}
          <Button
            icon={<SearchOutlined />}
            onClick={handleSearch}
            style={{
              width: 48,
            }}
            className="h-10 border-0 rounded-none"
            size="large"
            type="text"
          />
        </div>

        {/* 筛选条件标签 */}
        {activeFilters.length > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">筛选条件：</span>
            <Space wrap>
              {activeFilters.map(filter => (
                <Tag
                  key={filter.type}
                  closable
                  onClose={() => removeFilter(filter.type)}
                  style={{
                    backgroundColor: '#f5f5f5',
                    border: '1px solid #d9d9d9',
                    borderRadius: '16px',
                    color: '#666',
                    fontSize: '12px',
                    padding: '4px 12px',
                  }}
                >
                  {filter.label}
                </Tag>
              ))}
            </Space>
            <Button
              type="link"
              size="small"
              onClick={clearAllFilters}
              className="text-blue-500 hover:text-blue-600 text-sm"
            >
              清除筛选条件
            </Button>
          </div>
        )}
      </div>

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
                handleRefresh={() => fetchApiProducts(pagination.current, pagination.pageSize, filters)}
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
                showTotal={(total) => `共 ${total} 条`}
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