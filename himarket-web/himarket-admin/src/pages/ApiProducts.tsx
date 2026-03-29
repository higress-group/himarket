import { memo, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { MenuProps } from 'antd';
import { Button, Dropdown, Modal, message, Pagination, Skeleton, Input, Tabs, Tag, Select } from 'antd';
import type { ApiProduct, ProductIcon } from '@/types/api-product';
import { ApiOutlined, MoreOutlined, PlusOutlined, ExclamationCircleOutlined, ExclamationCircleFilled, ClockCircleFilled, CheckCircleFilled, SearchOutlined, RobotOutlined, BulbOutlined, ThunderboltOutlined, UserOutlined, ImportOutlined, DownloadOutlined } from '@ant-design/icons';
import McpServerIcon from '@/components/icons/McpServerIcon';
import { apiProductApi, nacosApi, workerApi, skillApi } from '@/lib/api';
import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import { ProductIconRenderer } from '@/components/icons/ProductIconRenderer';
import { getIconString } from '@/lib/iconUtils';

// 产品类型定义
const PRODUCT_TYPES = [
  { key: 'ALL', label: '全部' },
  { key: 'MODEL_API', label: 'Model API' },
  { key: 'MCP_SERVER', label: 'MCP Server' },
  { key: 'AGENT_SKILL', label: 'Agent Skill' },
  { key: 'WORKER', label: 'Worker' },
  { key: 'AGENT_API', label: 'Agent API' },
  { key: 'REST_API', label: 'REST API' },
];

const getDefaultIcon = (type: string) => {
  if (type === 'REST_API') return <ApiOutlined style={{ fontSize: '16px' }} />;
  if (type === 'MCP_SERVER') return <McpServerIcon style={{ fontSize: '16px' }} />;
  if (type === 'AGENT_API') return <RobotOutlined style={{ fontSize: '16px' }} />;
  if (type === 'MODEL_API') return <BulbOutlined style={{ fontSize: '16px' }} />;
  if (type === 'AGENT_SKILL') return <ThunderboltOutlined style={{ fontSize: '16px' }} />;
  if (type === 'WORKER') return <UserOutlined style={{ fontSize: '16px' }} />;
  return <ApiOutlined style={{ fontSize: '16px' }} />;
};

const getEmptyIcon = (type: string) => {
  if (type === 'REST_API') return <ApiOutlined style={{ fontSize: '48px' }} />;
  if (type === 'MCP_SERVER') return <McpServerIcon style={{ fontSize: '48px' }} />;
  if (type === 'AGENT_API') return <RobotOutlined style={{ fontSize: '48px' }} />;
  if (type === 'MODEL_API') return <BulbOutlined style={{ fontSize: '48px' }} />;
  if (type === 'AGENT_SKILL') return <ThunderboltOutlined style={{ fontSize: '48px' }} />;
  if (type === 'WORKER') return <UserOutlined style={{ fontSize: '48px' }} />;
  return <ApiOutlined style={{ fontSize: '48px' }} />;
};

const getTypeLabel = (type: string) => {
  return PRODUCT_TYPES.find(t => t.key === type)?.label || type;
};

// 优化的产品卡片组件
const ProductCard = memo(({ product, onNavigate, handleRefresh, onEdit }: {
  product: ApiProduct;
  onNavigate: (productId: string) => void;
  handleRefresh: () => void;
  onEdit: (product: ApiProduct) => void;
}) => {
  const handleClick = useCallback(() => {
    onNavigate(product.productId);
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
    { key: 'edit', label: '编辑', onClick: handleEdit },
    { type: 'divider' },
    { key: 'delete', label: '删除', danger: true, onClick: (info: any) => handleDelete(product.productId, product.name, info?.domEvent) },
  ];

  return (
    <div
      className="
        bg-white/60 backdrop-blur-sm rounded-2xl p-5
        border cursor-pointer
        transition-all duration-300 ease-in-out
        hover:bg-white hover:shadow-md hover:scale-[1.02] hover:border-colorPrimary/50
        border-colorPrimary/30 active:scale-[0.98]
        relative overflow-hidden group
        h-[168px]
      "
      onClick={handleClick}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5">
            <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(product.icon)} type={product.type} />
          </div>
          <div>
            <h3 className="text-lg font-semibold">{product.name}</h3>
            <div className="flex items-center gap-3 mt-1 flex-wrap">
              <div className="flex items-center">
                {product.type === 'REST_API' ? (
                  <ApiOutlined className="text-colorPrimary mr-1" style={{ fontSize: '12px' }} />
                ) : product.type === 'AGENT_API' ? (
                  <RobotOutlined className="text-gray-600 mr-1" style={{ fontSize: '12px' }} />
                ) : product.type === 'MODEL_API' ? (
                  <BulbOutlined className="text-gray-600 mr-1" style={{ fontSize: '12px' }} />
                ) : product.type === 'AGENT_SKILL' ? (
                  <ThunderboltOutlined className="text-gray-600 mr-1" style={{ fontSize: '12px' }} />
                ) : product.type === 'WORKER' ? (
                  <UserOutlined className="text-gray-600 mr-1" style={{ fontSize: '12px' }} />
                ) : (
                  <McpServerIcon className="text-black mr-1" style={{ fontSize: '12px' }} />
                )}
                <span className="text-xs text-gray-700">{getTypeLabel(product.type)}</span>
              </div>
              <div className="flex items-center">
                {product.status === 'PENDING' ? (
                  <ExclamationCircleFilled className="text-yellow-500 mr-1" style={{ fontSize: '12px' }} />
                ) : product.status === 'READY' ? (
                  <ClockCircleFilled className="text-colorPrimary/50 mr-1" style={{ fontSize: '12px' }} />
                ) : (
                  <CheckCircleFilled className="text-green-500 mr-1" style={{ fontSize: '12px' }} />
                )}
                <span className="text-xs text-gray-700">
                  {product.status === 'PENDING' ? '待配置' : product.status === 'READY' ? '待发布' : '已发布'}
                </span>
              </div>
              {(product.type === 'AGENT_SKILL' || product.type === 'WORKER') && (
                <div className="flex items-center">
                  <DownloadOutlined className="text-gray-400 mr-1" style={{ fontSize: '12px' }} />
                  <span className="text-xs text-gray-500">
                    {product.type === 'AGENT_SKILL'
                      ? (product.skillConfig?.downloadCount ?? 0)
                      : (product.workerConfig?.downloadCount ?? 0)}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
        <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
          <Button type="text" icon={<MoreOutlined />} onClick={(e) => e.stopPropagation()} />
        </Dropdown>
      </div>
      <div className="space-y-4">
        {product.description && (
          <p className="max-h-17 text-sm line-clamp-3 leading-relaxed flex-1 text-[#a3a3a3]">{product.description}</p>
        )}
      </div>
    </div>
  );
});

ProductCard.displayName = 'ProductCard';

export default function ApiProducts() {
  const navigate = useNavigate();
  const [apiProducts, setApiProducts] = useState<ApiProduct[]>([]);
  const [activeTab, setActiveTab] = useState('ALL');
  const [nameFilter, setNameFilter] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 12, total: 0 });
  const [modalVisible, setModalVisible] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ApiProduct | null>(null);
  const [importLoading, setImportLoading] = useState(false);
  const [defaultNacos, setDefaultNacos] = useState<any>(null);
  const [sortBy, setSortBy] = useState<string | undefined>(undefined);

  const showSortControl = activeTab === 'AGENT_SKILL' || activeTab === 'WORKER';

  const buildParams = useCallback((page: number, size: number, tab: string, name: string) => {
    const params: Record<string, any> = { page, size };
    if (tab !== 'ALL') params.type = tab;
    if (name.trim()) params.name = name.trim();
    if ((tab === 'AGENT_SKILL' || tab === 'WORKER') && sortBy) {
      params.sortBy = sortBy;
    }
    return params;
  }, [sortBy]);

  const fetchApiProducts = useCallback((page = 1, size = 12, tab = activeTab, name = nameFilter) => {
    setLoading(true);
    apiProductApi.getApiProducts(buildParams(page, size, tab, name)).then((res: any) => {
      setApiProducts(res.data.content);
      setPagination({ current: page, pageSize: size, total: res.data.totalElements || 0 });
    }).finally(() => setLoading(false));
  }, [activeTab, nameFilter, buildParams]);

  useEffect(() => {
    fetchApiProducts(1, 12, 'ALL', '');
  }, []);

  // Re-fetch when sortBy changes
  useEffect(() => {
    if (sortBy !== undefined) {
      fetchApiProducts(1, pagination.pageSize, activeTab, nameFilter);
    }
  }, [sortBy]);

  // Fetch default Nacos instance for import feature
  useEffect(() => {
    if (activeTab === 'AGENT_SKILL' || activeTab === 'WORKER') {
      nacosApi.getDefaultNacos().then((res: any) => {
        setDefaultNacos(res.data);
      }).catch(() => {
        setDefaultNacos(null);
      });
    }
  }, [activeTab]);

  const handleImportFromNacos = async () => {
    if (!defaultNacos) {
      message.warning('请先配置默认 Nacos 实例');
      return;
    }

    const isWorker = activeTab === 'WORKER';
    const typeName = isWorker ? 'Workers' : 'Skills';

    Modal.confirm({
      title: `从 Nacos 导入 ${typeName}`,
      content: `将从默认 Nacos 实例 "${defaultNacos.nacosName || defaultNacos.nacosId}" 导入所有 ${typeName}，是否继续？`,
      okText: '确认导入',
      cancelText: '取消',
      onOk: async () => {
        setImportLoading(true);
        try {
          const namespace = defaultNacos.defaultNamespace || 'public';
          const res = isWorker
            ? await workerApi.importFromNacos(defaultNacos.nacosId, namespace)
            : await skillApi.importFromNacos(defaultNacos.nacosId, namespace);

          const importResult = res.data;
          if (importResult.successCount > 0) {
            message.success(`成功导入 ${importResult.successCount} 个 ${typeName}`);
            fetchApiProducts(pagination.current, pagination.pageSize);
          } else {
            message.info(`没有新的 ${typeName} 需要导入`);
          }
        } catch (error: any) {
          message.error(error.response?.data?.message || `导入 ${typeName} 失败`);
        } finally {
          setImportLoading(false);
        }
      },
    });
  };

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    setNameFilter('');
    setSearchInput('');
    setSortBy((tab === 'AGENT_SKILL' || tab === 'WORKER') ? 'UPDATED_AT' : undefined);
    fetchApiProducts(1, pagination.pageSize, tab, '');
  };

  const handleSearch = () => {
    setNameFilter(searchInput);
    fetchApiProducts(1, pagination.pageSize, activeTab, searchInput);
  };

  const handleClearSearch = () => {
    setSearchInput('');
    setNameFilter('');
    fetchApiProducts(1, pagination.pageSize, activeTab, '');
  };

  const handlePaginationChange = (page: number, pageSize: number) => {
    fetchApiProducts(page, pageSize, activeTab, nameFilter);
  };

  const handleNavigateToProduct = useCallback((productId: string) => {
    navigate(`/api-products/detail?productId=${productId}`);
  }, [navigate]);

  const handleCreate = () => {
    setEditingProduct(null);
    setModalVisible(true);
  };

  const handleEdit = (product: ApiProduct) => {
    setEditingProduct(product);
    setModalVisible(true);
  };

  const handleModalSuccess = () => {
    setModalVisible(false);
    setEditingProduct(null);
    fetchApiProducts(pagination.current, pagination.pageSize);
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingProduct(null);
  };

  const tabItems = PRODUCT_TYPES.map(t => ({
    key: t.key,
    label: t.label,
  }));

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">API Products</h1>
          <p className="text-gray-500 mt-2">管理和配置您的API产品</p>
        </div>
        <div className="flex items-center gap-3">
          {(activeTab === 'AGENT_SKILL' || activeTab === 'WORKER') && (
            <Button
              onClick={handleImportFromNacos}
              loading={importLoading}
              disabled={!defaultNacos}
              icon={<ImportOutlined />}
            >
              从 Nacos 导入
            </Button>
          )}
          <Button onClick={handleCreate} type="primary" icon={<PlusOutlined />}>
            创建 API Product
          </Button>
        </div>
      </div>

      {/* Tabs 按类型分组 */}
      <div>
        <div className="flex items-center justify-between">
          <Tabs
            activeKey={activeTab}
            onChange={handleTabChange}
            items={tabItems}
            className="flex-1"
          />
          {/* 排序 + 名称搜索框 */}
          <div className="flex items-center mb-3 ml-4 gap-2">
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
            <div className="flex items-center border border-gray-300 rounded-md overflow-hidden hover:border-colorPrimary focus-within:border-colorPrimary" style={{ minWidth: 260 }}>
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

        {/* 当前筛选提示 */}
        {nameFilter && (
          <div className="flex items-center gap-2 pb-3">
            <span className="text-sm text-gray-500">筛选条件：</span>
            <Tag
              closable
              onClose={handleClearSearch}
              style={{ backgroundColor: '#f5f5f5', border: '1px solid #d9d9d9', borderRadius: '16px', color: '#666', fontSize: '12px', padding: '2px 10px' }}
            >
              产品名称：{nameFilter}
            </Tag>
          </div>
        )}

        {/* 产品列表 */}
        <div className="pb-6">
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
                      <Skeleton.Input active size="small" style={{ width: '80%' }} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : apiProducts.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-gray-400">
              {getEmptyIcon(activeTab)}
              <p className="text-base">暂无{activeTab !== 'ALL' ? ` ${getTypeLabel(activeTab)} ` : ''}产品</p>
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
                    showTotal={(total) => `共 ${total} 条`}
                    pageSizeOptions={['6', '12', '24', '48']}
                  />
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <ApiProductFormModal
        visible={modalVisible}
        onCancel={handleModalCancel}
        onSuccess={handleModalSuccess}
        productId={editingProduct?.productId}
        initialData={editingProduct || undefined}
      />
    </div>
  );
}
