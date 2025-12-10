import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Button, 
  Card, 
  Skeleton, 
  Empty, 
  Divider,
  message,
  Checkbox,
  Modal,
  Space
} from 'antd';
import {
  ArrowLeftOutlined,
  EditOutlined,
  FolderOutlined,
  ApiOutlined,
  RobotOutlined,
  BulbOutlined,
  ExclamationCircleFilled,
  ClockCircleFilled,
  CheckCircleFilled,
  DeleteOutlined,
  ExclamationCircleOutlined,
  PlusOutlined
} from '@ant-design/icons';
import { getProductCategory, unbindProductsFromCategory } from '@/lib/productCategoryApi';
import { apiProductApi } from '@/lib/api';
import { formatDateTime } from '@/lib/utils';
import type { ProductCategory } from '@/types/product-category';
import type { ApiProduct } from '@/types/api-product';
import CategoryFormModal from '@/components/product-category/CategoryFormModal';
import AddProductModal from '@/components/product-category/AddProductModal';
import McpServerIcon from '@/components/icons/McpServerIcon';

export default function ProductCategoryDetail() {
  const { categoryId } = useParams<{ categoryId: string }>();
  const navigate = useNavigate();
  
  const [category, setCategory] = useState<ProductCategory | null>(null);
  const [products, setProducts] = useState<ApiProduct[]>([]);
  const [categoryLoading, setCategoryLoading] = useState(true);
  const [productsLoading, setProductsLoading] = useState(true);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [addModalVisible, setAddModalVisible] = useState(false);
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [removeLoading, setRemoveLoading] = useState(false);

  useEffect(() => {
    if (categoryId) {
      fetchCategoryDetail();
      fetchCategoryProducts();
    }
  }, [categoryId]);

  // 获取类别详情
  const fetchCategoryDetail = async () => {
    if (!categoryId) return;
    
    try {
      setCategoryLoading(true);
      const response = await getProductCategory(categoryId);
      setCategory(response.data);
    } catch (error) {
      console.error('获取类别详情失败:', error);
      message.error('获取类别详情失败');
    } finally {
      setCategoryLoading(false);
    }
  };

  // 获取该类别下的产品
  const fetchCategoryProducts = async () => {
    if (!categoryId) return;
    
    try {
      setProductsLoading(true);
      const response = await apiProductApi.getApiProducts({
        categoryIds: categoryId,
        page: 0,
        size: 100 // 获取所有产品
      });
      setProducts(response.data.content || []);
    } catch (error) {
      console.error('获取类别产品失败:', error);
      message.error('获取类别产品失败');
    } finally {
      setProductsLoading(false);
    }
  };

  // 渲染类别图标
  const renderCategoryIcon = (category: ProductCategory, size: number = 64) => {
    if (!category.icon) {
      return (
        <div 
          className="flex items-center justify-center rounded-lg bg-gradient-to-br from-gray-100 to-gray-200 shadow-sm"
          style={{ width: size, height: size }}
        >
          <FolderOutlined style={{ color: '#666', fontSize: size * 0.4 }} />
        </div>
      );
    }

    if (category.icon.type === 'URL') {
      return (
        <img 
          src={category.icon.value}
          alt={category.name}
          className="rounded-lg object-cover shadow-sm"
          style={{ width: size, height: size }}
          onError={(e) => {
            e.currentTarget.outerHTML = `
              <div class="w-16 h-16 rounded-lg bg-gradient-to-br from-gray-100 to-gray-200 flex items-center justify-center shadow-sm">
                <svg class="w-6 h-6 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M10 4H4c-1.11 0-2 .89-2 2v12c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2h-8l-2-2z"/>
                </svg>
              </div>
            `;
          }}
        />
      );
    } else {
      // BASE64 类型，可能是emoji或图片
      if (category.icon.value.length <= 10 && /\p{Emoji}/u.test(category.icon.value)) {
        // 是emoji
        return (
          <div 
            className="flex items-center justify-center rounded-lg bg-gradient-to-br from-blue-50 to-indigo-50 shadow-sm"
            style={{ width: size, height: size, fontSize: size * 0.4 }}
          >
            {category.icon.value}
          </div>
        );
      } else {
        // 是base64图片
        return (
          <img 
            src={category.icon.value} 
            alt={category.name}
            className="rounded-lg object-cover shadow-sm"
            style={{ width: size, height: size }}
          />
        );
      }
    }
  };

  // 获取产品类型图标 - 与API Products页保持一致
  const getTypeIcon = (icon: any, type: string) => {
    if (icon) {
      switch (icon.type) {
        case "URL":
          return <img src={icon.value} alt="icon" style={{ borderRadius: '8px', minHeight: '40px', width: '40px', height: '40px', objectFit: 'cover' }} />
        case "BASE64":
          const src = icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`;
          return <img src={src} alt="icon" style={{ borderRadius: '8px', minHeight: '40px', width: '40px', height: '40px', objectFit: 'cover' }} />
        default:
          return type === "REST_API" ? <ApiOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} /> : 
                 type === "AGENT_API" ? <RobotOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} /> :
                 type === "MODEL_API" ? <BulbOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} /> :
                 <McpServerIcon style={{ fontSize: '16px', width: '16px', height: '16px' }} />
      }
    } else {
      return type === "REST_API" ? <ApiOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} /> : 
             type === "AGENT_API" ? <RobotOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} /> :
             type === "MODEL_API" ? <BulbOutlined style={{ fontSize: '16px', width: '16px', height: '16px' }} /> :
             <McpServerIcon style={{ fontSize: '16px', width: '16px', height: '16px' }} />
    }
  };

  // 获取产品类型标签
  const getTypeLabel = (type: string) => {
    switch (type) {
      case 'REST_API': return 'REST API';
      case 'MCP_SERVER': return 'MCP Server';
      case 'AGENT_API': return 'Agent API';
      case 'MODEL_API': return 'Model API';
      default: return type;
    }
  };



  // 编辑成功回调
  const handleEditSuccess = () => {
    setEditModalVisible(false);
    fetchCategoryDetail();
    fetchCategoryProducts();
  };

  // 添加产品成功回调
  const handleAddSuccess = () => {
    setAddModalVisible(false);
    fetchCategoryProducts(); // Refresh product list
  };

  // 处理产品选择
  const handleProductSelect = (productId: string, checked: boolean) => {
    if (checked) {
      setSelectedProductIds(prev => [...prev, productId]);
    } else {
      setSelectedProductIds(prev => prev.filter(id => id !== productId));
    }
  };

  // 全选/取消全选
  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedProductIds(products.map(p => p.productId));
    } else {
      setSelectedProductIds([]);
    }
  };

  // 从类别中移除选中的产品
  const handleRemoveProducts = () => {
    if (selectedProductIds.length === 0) {
      message.warning('请先选择要移除的产品');
      return;
    }

    Modal.confirm({
      title: '确认移除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要从该类别中移除选中的 ${selectedProductIds.length} 个产品吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        if (!categoryId) return;
        
        try {
          setRemoveLoading(true);
          await unbindProductsFromCategory(categoryId, selectedProductIds);
          message.success('移除成功');
          setSelectedProductIds([]);
          fetchCategoryProducts(); // 重新获取产品列表
        } catch (error) {
          console.error('移除产品失败:', error);
          message.error('移除产品失败');
        } finally {
          setRemoveLoading(false);
        }
      }
    });
  };

  if (categoryLoading) {
    return (
      <div className="space-y-6">
        <Skeleton.Input style={{ width: 300, height: 32 }} />
        <Card>
          <div className="flex items-start space-x-6">
            <Skeleton.Avatar size={80} />
            <div className="flex-1">
              <Skeleton.Input style={{ width: 200, height: 24, marginBottom: 12 }} />
              <Skeleton paragraph={{ rows: 3 }} />
            </div>
          </div>
        </Card>
      </div>
    );
  }

  if (!category) {
    return (
      <div className="flex items-center justify-center h-64">
        <Empty description="类别不存在或已被删除" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 导航栏 */}
      <div className="flex items-center justify-between">
        <Button 
          type="text" 
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/product-categories')}
          className="text-gray-600 hover:text-gray-800"
        >
          返回
        </Button>

        <Button 
          type="primary" 
          icon={<EditOutlined />}
          onClick={() => setEditModalVisible(true)}
        >
          编辑
        </Button>
      </div>

      {/* 类别详情卡片 */}
      <Card className="bg-gradient-to-br from-white to-gray-50/30 border border-gray-100 shadow-sm">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center space-x-4">
            {/* 类别图标 */}
            <div className="flex-shrink-0">
              {renderCategoryIcon(category, 64)}
            </div>
            
            {/* 类别信息 */}
            <div className="flex-1 min-w-0">
              <h1 className="text-xl font-bold text-gray-800 mb-2">
                {category.name}
              </h1>
              <p className="text-sm text-gray-500 mb-3">
                {category.description || <span className="italic text-gray-400">暂无描述</span>}
              </p>
              <div className="flex items-center space-x-4 text-xs text-gray-400">
                <span className="font-mono">ID: {category.categoryId}</span>
                {category.createAt && (
                  <span>创建于 {formatDateTime(category.createAt)}</span>
                )}
              </div>
            </div>
          </div>
        </div>
      </Card>

      <div className="mb-6">
        <div className="flex items-center justify-between">
          <span className="text-lg font-medium">关联产品</span>
          <Space size="large">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setAddModalVisible(true)}
            >
              添加产品
            </Button>
            {products.length > 0 && (
              <>
                <Checkbox
                  indeterminate={selectedProductIds.length > 0 && selectedProductIds.length < products.length}
                  checked={selectedProductIds.length === products.length}
                  onChange={(e) => handleSelectAll(e.target.checked)}
                >
                  全选 ({selectedProductIds.length}/{products.length})
                </Checkbox>
                <Button
                  type="primary"
                  danger
                  icon={<DeleteOutlined />}
                  loading={removeLoading}
                  disabled={selectedProductIds.length === 0}
                  onClick={handleRemoveProducts}
                >
                  移除选中
                </Button>
              </>
            )}
          </Space>
        </div>
        <Divider className="mt-2" />
      </div>

      {/* 产品列表 */}
      {productsLoading ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <Card key={index} className="bg-gradient-to-br from-white to-gray-50/30 border border-gray-100">
              <div className="flex items-center space-x-4">
                <Skeleton.Avatar size={48} />
                <div className="flex-1">
                  <Skeleton.Input style={{ width: '60%', marginBottom: 8 }} />
                  <Skeleton paragraph={{ rows: 2, width: '100%' }} />
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : products.length > 0 ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {products.map((product) => (
            <Card
              key={product.productId}
              className="hover:shadow-lg transition-shadow cursor-pointer rounded-xl border border-gray-200 shadow-sm hover:border-blue-300"
              onClick={() => navigate(`/api-products/detail?productId=${product.productId}`)}
              bodyStyle={{ padding: '16px' }}
            >
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center space-x-3">
                  {/* 复选框 */}
                  <Checkbox
                    checked={selectedProductIds.includes(product.productId)}
                    onChange={(e) => {
                      e.stopPropagation();
                      handleProductSelect(product.productId, e.target.checked);
                    }}
                    onClick={(e) => e.stopPropagation()}
                  />
                  {/* 产品图标 */}
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100">
                    {getTypeIcon(product.icon, product.type)}
                  </div>
                  {/* 产品信息 */}
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
              </div>

              <div className="space-y-4">
                {product.description && (
                  <p className="text-sm text-gray-600">{product.description}</p>
                )}
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="bg-gradient-to-br from-white to-gray-50/30 border border-gray-100 shadow-sm">
          <div className="text-center py-8">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="该类别下暂无产品"
            />
          </div>
        </Card>
      )}

      {/* 编辑类别弹窗 */}
      <CategoryFormModal
        visible={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        onSuccess={handleEditSuccess}
        category={category}
        isEdit={true}
      />

      {/* 添加产品弹窗 */}
      {categoryId && (
        <AddProductModal
          visible={addModalVisible}
          categoryId={categoryId}
          onCancel={() => setAddModalVisible(false)}
          onSuccess={handleAddSuccess}
        />
      )}
    </div>
  );
}
