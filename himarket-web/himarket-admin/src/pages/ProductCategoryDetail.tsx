import {
  ArrowLeftOutlined,
  EditOutlined,
  FolderOutlined,
  CheckCircleFilled,
  ClockCircleFilled,
  ExclamationCircleFilled,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Button, Card, Skeleton, Empty, Divider, message, Modal } from 'antd';
import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

import { DataTable } from '@/components/common/DataTable';
import AddProductModal from '@/components/product-category/AddProductModal';
import CategoryFormModal from '@/components/product-category/CategoryFormModal';
import { apiProductApi } from '@/lib/api';
import { getProductCategory, unbindProductsFromCategory } from '@/lib/productCategoryApi';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';
import type { ProductCategory } from '@/types/product-category';

import type { TableProps } from 'antd';

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

function getTypeLabel(type: string) {
  switch (type) {
    case 'REST_API':
      return 'REST API';
    case 'MCP_SERVER':
      return 'MCP Server';
    case 'AGENT_API':
      return 'Agent API';
    case 'AGENT_SKILL':
      return 'Agent Skill';
    case 'MODEL_API':
      return 'Model API';
    case 'WORKER':
      return 'Worker';
    default:
      return type;
  }
}

export default function ProductCategoryDetail() {
  const { categoryId } = useParams<{ categoryId: string }>();
  const navigate = useNavigate();

  const [category, setCategory] = useState<ProductCategory | null>(null);
  const [products, setProducts] = useState<ApiProduct[]>([]);
  const [categoryLoading, setCategoryLoading] = useState(true);
  const [productsLoading, setProductsLoading] = useState(true);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [addModalVisible, setAddModalVisible] = useState(false);
  const [selectedProductIds, setSelectedProductIds] = useState<Set<string>>(new Set());
  const [removeLoading, setRemoveLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  // 获取类别详情
  const fetchCategoryDetail = useCallback(async () => {
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
  }, [categoryId]);

  // 获取该类别下的产品
  const fetchCategoryProducts = useCallback(
    async (page = 1, size = 10) => {
      if (!categoryId) return;

      try {
        setProductsLoading(true);
        const response = await apiProductApi.getApiProducts({
          categoryIds: categoryId,
          page,
          size,
        });
        setProducts(response.data.content || []);
        setPagination({
          current: response.data.number ?? page,
          pageSize: response.data.size ?? size,
          total: response.data.totalElements ?? 0,
        });
      } catch (error) {
        console.error('获取类别产品失败:', error);
        message.error('获取类别产品失败');
      } finally {
        setProductsLoading(false);
      }
    },
    [categoryId],
  );

  useEffect(() => {
    if (categoryId) {
      fetchCategoryDetail();
      fetchCategoryProducts();
    }
  }, [categoryId, fetchCategoryDetail, fetchCategoryProducts]);

  // 渲染类别图标
  const renderCategoryIcon = (category: ProductCategory, size: number = 64) => {
    if (!category.icon) {
      return (
        <div
          className="flex items-center justify-center rounded-lg bg-gradient-to-br from-gray-100 to-gray-200 shadow-sm"
          style={{ height: size, width: size }}
        >
          <FolderOutlined style={{ color: '#666', fontSize: size * 0.4 }} />
        </div>
      );
    }

    if (category.icon.type === 'URL') {
      return (
        <img
          alt={category.name}
          className="rounded-lg object-cover shadow-sm"
          onError={(e) => {
            e.currentTarget.outerHTML = `
              <div class="w-16 h-16 rounded-lg bg-gradient-to-br from-gray-100 to-gray-200 flex items-center justify-center shadow-sm">
                <svg class="w-6 h-6 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M10 4H4c-1.11 0-2 .89-2 2v12c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2h-8l-2-2z"/>
                </svg>
              </div>
            `;
          }}
          src={category.icon.value}
          style={{ height: size, width: size }}
        />
      );
    } else {
      // BASE64 类型，可能是emoji或图片
      if (category.icon.value.length <= 10 && /\p{Emoji}/u.test(category.icon.value)) {
        // 是emoji
        return (
          <div
            className="flex items-center justify-center rounded-lg bg-gradient-to-br from-blue-50 to-indigo-50 shadow-sm"
            style={{ fontSize: size * 0.4, height: size, width: size }}
          >
            {category.icon.value}
          </div>
        );
      } else {
        // 是base64图片
        return (
          <img
            alt={category.name}
            className="rounded-lg object-cover shadow-sm"
            src={category.icon.value}
            style={{ height: size, width: size }}
          />
        );
      }
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
    fetchCategoryProducts();
  };

  // 从类别中移除选中的产品
  const handleRemoveProducts = () => {
    if (selectedProductIds.size === 0) {
      message.warning('请先选择要移除的产品');
      return;
    }

    Modal.confirm({
      cancelText: '取消',
      content: `确定要从该类别中移除选中的 ${selectedProductIds.size} 个产品吗？`,
      icon: <ExclamationCircleFilled />,
      okText: '确认',
      onOk: async () => {
        if (!categoryId) return;

        try {
          setRemoveLoading(true);
          await unbindProductsFromCategory(categoryId, [...selectedProductIds]);
          message.success('移除成功');
          setSelectedProductIds(new Set());
          fetchCategoryProducts(pagination.current, pagination.pageSize);
        } catch (error) {
          console.error('移除产品失败:', error);
          message.error('移除产品失败');
        } finally {
          setRemoveLoading(false);
        }
      },
      title: '确认移除',
    });
  };

  const rowSelection: TableProps<ApiProduct>['rowSelection'] = {
    onChange: (selectedRowKeys) => {
      setSelectedProductIds(new Set(selectedRowKeys as string[]));
    },
    selectedRowKeys: [...selectedProductIds],
  };

  const columns: TableProps<ApiProduct>['columns'] = [
    {
      dataIndex: 'name',
      render: (_text: unknown, record: ApiProduct) => (
        <div className="min-w-0">
          <button
            className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
            onClick={() => navigate(`/api-products/${record.productId}`)}
            type="button"
          >
            {record.name}
          </button>
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
        </div>
      ),
      title: '产品名称/ID',
      width: 280,
    },
    {
      dataIndex: 'type',
      render: (type: string) => <span className="text-xs text-gray-600">{getTypeLabel(type)}</span>,
      title: '类型',
      width: 120,
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
        <span className="text-gray-600 text-xs" title={description}>
          {description || '-'}
        </span>
      ),
      title: '描述',
    },
    {
      render: (_text: unknown, record: ApiProduct) => (
        <Button
          className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
          icon={<DeleteOutlined />}
          onClick={() => {
            Modal.confirm({
              cancelText: '取消',
              content: `确定要从该类别中移除 "${record.name}" 吗？`,
              icon: <ExclamationCircleFilled />,
              okText: '确认',
              onOk: async () => {
                if (!categoryId) return;
                try {
                  await unbindProductsFromCategory(categoryId, [record.productId]);
                  message.success('移除成功');
                  fetchCategoryProducts(pagination.current, pagination.pageSize);
                } catch (error) {
                  console.error('移除产品失败:', error);
                  message.error('移除产品失败');
                }
              },
              title: '确认移除',
            });
          }}
          type="text"
        >
          移除
        </Button>
      ),
      title: '操作',
      width: 120,
    },
  ];

  if (categoryLoading) {
    return (
      <div className="space-y-6">
        <Skeleton.Input style={{ height: 32, width: 300 }} />
        <Card>
          <div className="flex items-start space-x-6">
            <Skeleton.Avatar size={80} />
            <div className="flex-1">
              <Skeleton.Input style={{ height: 24, marginBottom: 12, width: 200 }} />
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
          className="text-gray-600 hover:text-gray-800"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/product-categories')}
          type="text"
        >
          返回
        </Button>

        <Button icon={<EditOutlined />} onClick={() => setEditModalVisible(true)} type="primary">
          编辑
        </Button>
      </div>

      {/* 类别详情卡片 */}
      <Card className="bg-gradient-to-br from-white to-gray-50/30 border border-gray-100 shadow-sm">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center space-x-4">
            {/* 类别图标 */}
            <div className="flex-shrink-0">{renderCategoryIcon(category, 64)}</div>

            {/* 类别信息 */}
            <div className="flex-1 min-w-0">
              <h1 className="text-xl font-bold text-gray-800 mb-2">{category.name}</h1>
              <p className="text-sm text-gray-500 mb-3">
                {category.description || <span className="italic text-gray-400">暂无描述</span>}
              </p>
              <div className="flex items-center space-x-4 text-xs text-gray-400">
                <span className="font-mono">ID: {category.categoryId}</span>
                {category.createAt && <span>创建于 {formatDateTime(category.createAt)}</span>}
              </div>
            </div>
          </div>
        </div>
      </Card>

      <div className="mb-6">
        <div className="flex items-center justify-between">
          <span className="text-lg font-medium">关联产品</span>
          <div className="flex items-center gap-2">
            {selectedProductIds.size > 0 && (
              <Button
                danger
                icon={<DeleteOutlined />}
                loading={removeLoading}
                onClick={handleRemoveProducts}
                type="primary"
              >
                移除选中 ({selectedProductIds.size})
              </Button>
            )}
            <Button icon={<PlusOutlined />} onClick={() => setAddModalVisible(true)} type="primary">
              添加产品
            </Button>
          </div>
        </div>
        <Divider className="mt-2" />
      </div>

      {/* 产品表格 */}
      <DataTable<ApiProduct>
        columns={columns}
        dataSource={products}
        loading={productsLoading}
        pagination={{
          current: pagination.current,
          onChange: (page, pageSize) => fetchCategoryProducts(page, pageSize),
          pageSize: pagination.pageSize,
          total: pagination.total,
        }}
        rowKey="productId"
        rowSelection={rowSelection}
      />

      {/* 编辑类别弹窗 */}
      <CategoryFormModal
        category={category}
        isEdit={true}
        onCancel={() => setEditModalVisible(false)}
        onSuccess={handleEditSuccess}
        visible={editModalVisible}
      />

      {/* 添加产品弹窗 */}
      {categoryId && (
        <AddProductModal
          categoryId={categoryId}
          onCancel={() => setAddModalVisible(false)}
          onSuccess={handleAddSuccess}
          visible={addModalVisible}
        />
      )}
    </div>
  );
}
