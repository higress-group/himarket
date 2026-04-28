import {
  MoreOutlined,
  LeftOutlined,
  EyeOutlined,
  LinkOutlined,
  BookOutlined,
  GlobalOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Modal, message } from 'antd';
import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import { ApiProductLinkApi } from '@/components/api-product/ApiProductLinkApi';
import { ApiProductOverview } from '@/components/api-product/ApiProductOverview';
import { ApiProductPortal } from '@/components/api-product/ApiProductPortal';
import { ApiProductSkillPackage } from '@/components/api-product/ApiProductSkillPackage';
import { ApiProductUsageGuide } from '@/components/api-product/ApiProductUsageGuide';
import { ApiProductWorkerPackage } from '@/components/api-product/ApiProductWorkerPackage';
// import { ApiProductDashboard } from '@/components/api-product/ApiProductDashboard'
import { apiProductApi } from '@/lib/api';
import type { ApiProduct, LinkedService } from '@/types/api-product';

import type { MenuProps } from 'antd';

interface MenuItem {
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  key: string;
  label: string;
}

const BASE_MENU_ITEMS: MenuItem[] = [
  {
    description: '产品概览',
    icon: EyeOutlined,
    key: 'overview',
    label: 'Overview',
  },
  {
    description: 'API关联',
    icon: LinkOutlined,
    key: 'link-api',
    label: 'Link API',
  },
  {
    description: '使用指南',
    icon: BookOutlined,
    key: 'usage-guide',
    label: 'Usage Guide',
  },
  {
    description: '发布的门户',
    icon: GlobalOutlined,
    key: 'portal',
    label: 'Portal',
  },
];

export default function ApiProductDetail() {
  const navigate = useNavigate();
  const { productId } = useParams<{ productId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [apiProduct, setApiProduct] = useState<ApiProduct | null>(null);
  const [linkedService, setLinkedService] = useState<LinkedService | null>(null);
  const [, setLoading] = useState(true); // 添加 loading 状态

  // 动态计算 menuItems（AGENT_SKILL / WORKER 类型：隐藏 Link API 和 Usage Guide，插入包管理和 Link Nacos）
  const menuItems = useMemo(
    () =>
      apiProduct?.type === 'AGENT_SKILL'
        ? [
            BASE_MENU_ITEMS[0], // overview
            {
              description: '技能包管理',
              icon: InboxOutlined,
              key: 'skill-package',
              label: 'Skill Package',
            },
            BASE_MENU_ITEMS[3], // portal
          ]
        : apiProduct?.type === 'WORKER'
          ? [
              BASE_MENU_ITEMS[0], // overview
              {
                description: 'Worker 包管理',
                icon: InboxOutlined,
                key: 'worker-package',
                label: 'Worker Package',
              },
              BASE_MENU_ITEMS[3], // portal
            ]
          : apiProduct?.type === 'MCP_SERVER'
            ? [
                BASE_MENU_ITEMS[0], // overview
                {
                  description: 'MCP Server 配置',
                  icon: LinkOutlined,
                  key: 'link-api',
                  label: '配置MCP',
                },
                BASE_MENU_ITEMS[2], // usage-guide
                BASE_MENU_ITEMS[3], // portal
              ]
            : BASE_MENU_ITEMS,
    [apiProduct?.type],
  );

  // 从URL query参数获取当前tab，默认为overview
  const currentTab = searchParams.get('tab') || 'overview';
  // 验证tab值是否有效，如果无效则使用默认值
  const validTab = menuItems.some((item) => item?.key === currentTab) ? currentTab : 'overview';
  const [activeTab, setActiveTab] = useState(validTab);

  const [editModalVisible, setEditModalVisible] = useState(false);
  const lastFetchedProductIdRef = useRef<string | null>(null);

  const fetchApiProduct = useCallback(async () => {
    if (productId) {
      setLoading(true);
      try {
        // 并行获取Product详情和关联信息
        const [productRes, refRes] = await Promise.all([
          apiProductApi.getApiProductDetail(productId),
          apiProductApi.getApiProductRef(productId).catch(() => ({ data: null })), // 关联信息获取失败不影响页面显示
        ]);

        setApiProduct(productRes.data);
        setLinkedService(refRes.data || null);
      } catch (error) {
        console.error('获取Product详情失败:', error);
      } finally {
        setLoading(false);
      }
    }
  }, [productId]);

  // 更新关联信息的回调函数
  const handleLinkedServiceUpdate = (newLinkedService: LinkedService | null) => {
    setLinkedService(newLinkedService);
  };

  useEffect(() => {
    if (!productId || lastFetchedProductIdRef.current === productId) {
      return;
    }
    lastFetchedProductIdRef.current = productId;
    fetchApiProduct();
  }, [productId, fetchApiProduct]);

  // 同步URL参数和activeTab状态
  useEffect(() => {
    const currentTab = searchParams.get('tab') || 'overview';
    const valid = menuItems.some((item) => item?.key === currentTab) ? currentTab : 'overview';
    setActiveTab(valid);
  }, [searchParams, menuItems]);

  const handleBackToApiProducts = () => {
    navigate('/api-products');
  };

  const handleTabChange = (tabKey: string) => {
    setActiveTab(tabKey);
    // 更新URL query参数
    const newSearchParams = new URLSearchParams(searchParams);
    newSearchParams.set('tab', tabKey);
    setSearchParams(newSearchParams);
  };

  const renderContent = () => {
    if (!apiProduct) {
      return <div className="p-6">Loading...</div>;
    }

    switch (activeTab) {
      case 'overview':
        return (
          <ApiProductOverview
            apiProduct={apiProduct}
            linkedService={linkedService}
            onEdit={handleEdit}
          />
        );
      case 'link-api':
        return (
          <ApiProductLinkApi
            apiProduct={apiProduct}
            handleRefresh={fetchApiProduct}
            linkedService={linkedService}
            onLinkedServiceUpdate={handleLinkedServiceUpdate}
          />
        );
      case 'usage-guide':
        return <ApiProductUsageGuide apiProduct={apiProduct} handleRefresh={fetchApiProduct} />;
      case 'portal':
        return <ApiProductPortal apiProduct={apiProduct} />;
      case 'skill-package':
        return (
          <ApiProductSkillPackage
            apiProduct={apiProduct}
            handleRefresh={fetchApiProduct}
            onUploadSuccess={fetchApiProduct}
          />
        );
      case 'worker-package':
        return (
          <ApiProductWorkerPackage
            apiProduct={apiProduct}
            handleRefresh={fetchApiProduct}
            onUploadSuccess={fetchApiProduct}
          />
        );
      // case "dashboard":
      //   return <ApiProductDashboard apiProduct={apiProduct} />
      default:
        return (
          <ApiProductOverview
            apiProduct={apiProduct}
            linkedService={linkedService}
            onEdit={handleEdit}
          />
        );
    }
  };

  const dropdownItems: MenuProps['items'] = [
    {
      danger: true,
      key: 'delete',
      label: '删除',
      onClick: () => {
        Modal.confirm({
          content: '确定要删除该产品吗？',
          onOk: () => {
            handleDeleteApiProduct();
          },
          title: '确认删除',
        });
      },
    },
  ];

  const handleDeleteApiProduct = () => {
    if (!apiProduct) return;

    apiProductApi
      .deleteApiProduct(apiProduct.productId)
      .then(() => {
        message.success('删除成功');
        navigate('/api-products');
      })
      .catch(() => {
        // message.error(error.response?.data?.message || '删除失败')
      });
  };

  const handleEdit = () => {
    setEditModalVisible(true);
  };

  const handleEditSuccess = () => {
    setEditModalVisible(false);
    fetchApiProduct();
  };

  const handleEditCancel = () => {
    setEditModalVisible(false);
  };

  return (
    <div className="flex h-full w-full overflow-hidden">
      {/* API Product 详情侧边栏 */}
      <div className="w-64 border-r bg-white flex flex-col flex-shrink-0">
        {/* 返回按钮 */}
        <div className="pb-4 border-b">
          <Button
            icon={<LeftOutlined />}
            // className="w-full justify-start"
            onClick={handleBackToApiProducts}
            type="text"
          >
            返回
          </Button>
        </div>

        {/* API Product 信息 */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-2">
            {apiProduct ? (
              <h2 className="text-lg font-semibold">{apiProduct.name}</h2>
            ) : (
              <div className="h-6 bg-gray-200 rounded animate-pulse w-32" />
            )}
            <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
              <Button icon={<MoreOutlined />} type="text" />
            </Dropdown>
          </div>
        </div>

        {/* 导航菜单 - 等待产品数据加载后再渲染，避免菜单项闪烁 */}
        <nav className="flex-1 p-4 space-y-1">
          {apiProduct ? (
            menuItems
              .filter((item): item is MenuItem => !!item)
              .map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left transition-colors ${
                      activeTab === item.key ? 'bg-blue-500 text-white' : 'hover:bg-gray-100'
                    }`}
                    key={item.key}
                    onClick={() => handleTabChange(item.key)}
                  >
                    <Icon className="h-4 w-4 flex-shrink-0" />
                    <div>
                      <div className="font-medium">{item.label}</div>
                      <div className="text-xs opacity-70">{item.description}</div>
                    </div>
                  </button>
                );
              })
          ) : (
            <div className="space-y-2">
              {[1, 2, 3, 4].map((i) => (
                <div className="flex items-center gap-3 px-3 py-2" key={i}>
                  <div className="w-4 h-4 rounded bg-gray-200 animate-pulse flex-shrink-0" />
                  <div className="flex-1 space-y-1">
                    <div className="h-4 bg-gray-200 rounded animate-pulse w-20" />
                    <div className="h-3 bg-gray-100 rounded animate-pulse w-14" />
                  </div>
                </div>
              ))}
            </div>
          )}
        </nav>
      </div>

      {/* 主内容区域 */}
      <div className="flex-1 overflow-auto min-w-0">
        <div className="w-full max-w-full">{renderContent()}</div>
      </div>

      {apiProduct && (
        <ApiProductFormModal
          initialData={apiProduct}
          onCancel={handleEditCancel}
          onSuccess={handleEditSuccess}
          productId={apiProduct.productId}
          visible={editModalVisible}
        />
      )}
    </div>
  );
}
