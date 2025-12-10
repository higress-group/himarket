import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Button, 
  Card, 
  message, 
  Input, 
  Empty,
  Skeleton,
  Pagination,
  Dropdown,
  Modal
} from 'antd';
import { 
  PlusOutlined, 
  EditOutlined, 
  DeleteOutlined, 
  FolderOutlined,
  MoreOutlined,
  SearchOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons';
import { 
  getProductCategoriesByPage, 
  deleteProductCategory 
} from '@/lib/productCategoryApi';
import type { 
  ProductCategory, 
  ProductCategoryPage, 
  QueryProductCategoryParam 
} from '@/types/product-category';
import CategoryFormModal from '@/components/product-category/CategoryFormModal';


export default function ProductCategories() {
  const navigate = useNavigate();
  
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingCategory, setEditingCategory] = useState<ProductCategory | null>(null);
  const [searchValue, setSearchValue] = useState('');
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 12,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条记录`,
  });

  // 获取产品类别列表
  const fetchCategories = useCallback(async (
    page: number = 0, 
    pageSize: number = 12,
    searchParams?: QueryProductCategoryParam
  ) => {
    try {
      setLoading(true);
      const response = await getProductCategoriesByPage(page, pageSize, searchParams);
      const pageData: ProductCategoryPage = response.data;
      setCategories(pageData.content || []);
      setPagination(prev => ({
        ...prev,
        current: pageData.number + 1, // 后端从0开始，前端从1开始
        pageSize: pageData.size,
        total: pageData.totalElements,
      }));
    } catch (error) {
      console.error('获取产品类别失败:', error);
      message.error('获取产品类别失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  // 搜索处理
  const handleSearch = (value: string) => {
    setSearchValue(value);
    const searchParams: QueryProductCategoryParam = value ? { name: value } : {};
    fetchCategories(0, pagination.pageSize, searchParams);
  };

  // 刷新数据
  const handleRefresh = () => {
    const searchParams: QueryProductCategoryParam = searchValue ? { name: searchValue } : {};
    fetchCategories(pagination.current - 1, pagination.pageSize, searchParams);
  };

  // 处理分页变化
  const handlePaginationChange = (page: number, pageSize: number) => {
    const searchParams: QueryProductCategoryParam = searchValue ? { name: searchValue } : {};
    fetchCategories(page - 1, pageSize, searchParams);
  };

  // 这些函数已经不需要了，因为API调用已经移到了CategoryFormModal中

  // 处理删除类别
  const handleDelete = async (categoryId: string) => {
    // 找到要删除的类别
    const category = categories.find(cat => cat.categoryId === categoryId);
    const categoryName = category?.name || '该类别';

    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除类别 "${categoryName}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteProductCategory(categoryId);
          message.success('类别删除成功');
          handleRefresh();
        } catch (error) {
          console.error('删除类别失败:', error);
          message.error('删除类别失败，可能该类别正在使用中');
        }
      },
    });
  };

  // 打开创建弹窗
  const handleOpenCreateModal = () => {
    setEditingCategory(null);
    setModalVisible(true);
  };

  // 打开编辑弹窗
  const handleOpenEditModal = (category: ProductCategory) => {
    setEditingCategory(category);
    setModalVisible(true);
  };

  // 关闭弹窗
  const handleCloseModal = () => {
    setModalVisible(false);
    setEditingCategory(null);
  };

  // 表单提交成功回调
  const handleFormSuccess = () => {
    handleRefresh();
    handleCloseModal();
  };

  // 渲染图标 - 与产品页面保持一致的样式
  const renderCategoryIcon = (category: ProductCategory) => {
    if (!category.icon) {
      return <FolderOutlined style={{ fontSize: '16px' }} />;
    }

    if (category.icon.type === 'URL') {
      return (
        <img 
          src={category.icon.value}
          alt={category.name}
          className="w-6 h-6 rounded object-cover"
        />
      );
    } else {
      // BASE64 类型，可能是emoji或图片
      if (category.icon.value.length <= 10 && /\p{Emoji}/u.test(category.icon.value)) {
        // 是emoji
        return (
          <span style={{ fontSize: '16px' }}>
            {category.icon.value}
          </span>
        );
      } else {
        // 是base64图片
        return (
          <img 
            src={category.icon.value} 
            alt={category.name}
            className="w-6 h-6 rounded object-cover"
          />
        );
      }
    }
  };

  // 导航到类别详情
  const handleNavigateToCategory = (categoryId: string) => {
    navigate(`/product-categories/${categoryId}`);
  };

  // 渲染分类卡片
  const CategoryCard = ({ category }: { category: ProductCategory }) => {
    const dropdownItems = [
      {
        key: 'edit',
        label: '编辑',
        icon: <EditOutlined />,
        onClick: (e: any) => {
          e?.domEvent?.stopPropagation();
          handleOpenEditModal(category);
        },
      },
      {
        type: 'divider' as const,
      },
      {
        key: 'delete',
        label: '删除',
        icon: <DeleteOutlined />,
        danger: true,
        onClick: (e: any) => {
          e?.domEvent?.stopPropagation();
          handleDelete(category.categoryId);
        },
      },
    ];

    return (
      <Card
        className="hover:shadow-lg transition-shadow cursor-pointer rounded-xl border border-gray-200 shadow-sm hover:border-blue-300"
        onClick={() => handleNavigateToCategory(category.categoryId)}
        bodyStyle={{ padding: '16px' }}
      >
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center space-x-3">
            {/* 类别图标 */}
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100">
              {renderCategoryIcon(category)}
            </div>
            
            {/* 类别信息 */}
            <div>
              <h3 className="text-lg font-semibold">{category.name}</h3>
            </div>
          </div>

          {/* 操作菜单 */}
          <Dropdown menu={{ items: dropdownItems }} trigger={["click"]}>
            <Button
              type="text"
              icon={<MoreOutlined />}
              onClick={(e) => e.stopPropagation()}
            />
          </Dropdown>
        </div>

        {/* 描述区域 */}
        <div className="space-y-4">
          {category.description && (
            <p className="text-sm text-gray-600">{category.description}</p>
          )}
        </div>
      </Card>
    );
  };

  return (
    <div className="space-y-6">
      {/* 页面头部 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Categories</h1>
          <p className="text-gray-500 mt-2">
            管理产品分类，帮助用户更好地发现和组织API产品
          </p>
        </div>
        <Button 
          type="primary" 
          icon={<PlusOutlined />}
          onClick={handleOpenCreateModal}
        >
          创建 Category
        </Button>
      </div>

      {/* 搜索区域 */}
      <div className="space-y-4">
        <div className="flex max-w-md border border-gray-300 rounded-md overflow-hidden hover:border-blue-500 focus-within:border-blue-500 focus-within:shadow-sm">
          <Input
            placeholder="搜索类别名称"
            allowClear
            style={{ 
              flex: 1,
            }}
            size="large"
            className="h-10 border-0 rounded-none"
            variant="borderless"
            value={searchValue}
            onChange={(e) => {
              setSearchValue(e.target.value);
              if (!e.target.value) {
                handleSearch('');
              }
            }}
            onPressEnter={() => handleSearch(searchValue)}
          />
          
          {/* 分隔线 */}
          <div className="w-px bg-gray-300 self-stretch"></div>
          
          <Button
            icon={<SearchOutlined />}
            onClick={() => handleSearch(searchValue)}
            style={{
              width: 48,
            }}
            className="h-10 border-0 rounded-none"
            size="large"
            type="text"
          />
        </div>
      </div>

      {/* 类别网格 */}
      {loading ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: pagination.pageSize || 12 }).map((_, index) => (
            <Card key={index} className="bg-gradient-to-br from-white to-gray-50/30 border border-gray-100">
              <div className="flex items-center space-x-4">
                <Skeleton.Avatar size={48} />
                <div className="flex-1">
                  <Skeleton.Input size="small" className="mb-2" style={{ width: '60%' }} />
                  <Skeleton paragraph={{ rows: 2, width: '100%' }} />
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : categories.length > 0 ? (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {categories.map((category) => (
              <CategoryCard key={category.categoryId} category={category} />
            ))}
          </div>
          
          {/* 分页 */}
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
      ) : (
        <div className="text-center py-12">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无产品类别"
          />
        </div>
      )}

      {/* 创建/编辑弹窗 */}
      <CategoryFormModal
        visible={modalVisible}
        onCancel={handleCloseModal}
        onSuccess={handleFormSuccess}
        category={editingCategory}
        isEdit={!!editingCategory}
      />
    </div>
  );
}