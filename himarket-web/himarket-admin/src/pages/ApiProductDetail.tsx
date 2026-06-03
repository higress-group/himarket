import {
  EyeOutlined,
  LinkOutlined,
  BookOutlined,
  GlobalOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import { Modal, message } from 'antd';
import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';
import { ApiProductLinkApi } from '@/components/api-product/ApiProductLinkApi';
import { ApiProductOverview } from '@/components/api-product/ApiProductOverview';
import { ApiProductPortal } from '@/components/api-product/ApiProductPortal';
import { ApiProductSkillPackage } from '@/components/api-product/ApiProductSkillPackage';
import { ApiProductUsageGuide } from '@/components/api-product/ApiProductUsageGuide';
import { ApiProductWorkerPackage } from '@/components/api-product/ApiProductWorkerPackage';
import { AdminDetailSidebar } from '@/components/common';
// import { ApiProductDashboard } from '@/components/api-product/ApiProductDashboard'
import { ProductIconRenderer } from '@/components/icons/ProductIconRenderer';
import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi } from '@/lib/api';
import { getIconString } from '@/lib/iconUtils';
import type { ApiProduct, LinkedService } from '@/types/api-product';

import type { MenuProps } from 'antd';

interface MenuItem {
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  key: string;
  label: string;
}

interface ApiProductDetailLocationState {
  from?: string;
}

const PRODUCT_TYPE_PATHS: Record<ApiProduct['type'], string> = {
  AGENT_API: '/api-products/agent-api',
  AGENT_SKILL: '/api-products/agent-skill',
  MCP_SERVER: '/api-products/mcp-server',
  MODEL_API: '/api-products/model-api',
  REST_API: '/api-products/rest-api',
  WORKER: '/api-products/worker',
};

const PRODUCT_TYPE_LABELS: Record<ApiProduct['type'], string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

export default function ApiProductDetail() {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useLocale();
  const { productId } = useParams<{ productId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [apiProduct, setApiProduct] = useState<ApiProduct | null>(null);
  const [linkedService, setLinkedService] = useState<LinkedService | null>(null);
  const [, setLoading] = useState(true); // 添加 loading 状态

  const baseMenuItems = useMemo<MenuItem[]>(
    () => [
      {
        description: t('product.detail.menu.overview.description'),
        icon: EyeOutlined,
        key: 'overview',
        label: t('product.detail.menu.overview.label'),
      },
      {
        description: t('product.detail.menu.linkApi.description'),
        icon: LinkOutlined,
        key: 'link-api',
        label: t('product.detail.menu.linkApi.label'),
      },
      {
        description: t('product.detail.menu.usageGuide.description'),
        icon: BookOutlined,
        key: 'usage-guide',
        label: t('product.detail.menu.usageGuide.label'),
      },
      {
        description: t('product.detail.menu.portal.description'),
        icon: GlobalOutlined,
        key: 'portal',
        label: t('product.detail.menu.portal.label'),
      },
    ],
    [t],
  );

  // 动态计算 menuItems（AGENT_SKILL / WORKER 类型：隐藏 Link API 和 Usage Guide，插入包管理和 Link Nacos）
  const menuItems = useMemo(
    () =>
      apiProduct?.type === 'AGENT_SKILL'
        ? [
            baseMenuItems[0], // overview
            {
              description: t('product.detail.menu.skillPackage.description'),
              icon: InboxOutlined,
              key: 'skill-package',
              label: t('product.detail.menu.skillPackage.label'),
            },
            baseMenuItems[3], // portal
          ]
        : apiProduct?.type === 'WORKER'
          ? [
              baseMenuItems[0], // overview
              {
                description: t('product.detail.menu.workerPackage.description'),
                icon: InboxOutlined,
                key: 'worker-package',
                label: t('product.detail.menu.workerPackage.label'),
              },
              baseMenuItems[3], // portal
            ]
          : apiProduct?.type === 'MCP_SERVER'
            ? [
                baseMenuItems[0], // overview
                {
                  description: t('product.detail.menu.linkApi.description'),
                  icon: LinkOutlined,
                  key: 'link-api',
                  label: t('product.detail.menu.linkApi.label'),
                },
                baseMenuItems[2], // usage-guide
                baseMenuItems[3], // portal
              ]
            : baseMenuItems,
    [apiProduct?.type, baseMenuItems, t],
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
    const from = (location.state as ApiProductDetailLocationState | null)?.from;
    const currentPath = `${location.pathname}${location.search}${location.hash}`;
    const fallbackPath = apiProduct
      ? PRODUCT_TYPE_PATHS[apiProduct.type]
      : '/api-products/model-api';
    const targetPath = from && from.startsWith('/') && from !== currentPath ? from : fallbackPath;

    navigate(targetPath, { replace: true });
  };

  const handleTabChange = (tabKey: string) => {
    setActiveTab(tabKey);
    // 更新URL query参数
    const newSearchParams = new URLSearchParams(searchParams);
    newSearchParams.set('tab', tabKey);
    setSearchParams(newSearchParams, { state: location.state });
  };

  const renderContent = () => {
    if (!apiProduct) {
      return <div className="p-6">{t('common.loading')}</div>;
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
      label: t('common.delete'),
      onClick: () => {
        Modal.confirm({
          content: t('product.detail.deleteConfirm'),
          onOk: () => {
            handleDeleteApiProduct();
          },
          title: t('product.detail.deleteTitle'),
        });
      },
    },
  ];

  const handleDeleteApiProduct = () => {
    if (!apiProduct) return;

    apiProductApi
      .deleteApiProduct(apiProduct.productId)
      .then(() => {
        message.success(t('common.deleteSuccess'));
        handleBackToApiProducts();
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
      <AdminDetailSidebar
        activeKey={activeTab}
        backLabel={t('common.back')}
        icon={
          apiProduct ? (
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-50">
              <ProductIconRenderer
                className="h-7 w-7"
                iconType={getIconString(apiProduct.icon)}
                type={apiProduct.type}
              />
            </div>
          ) : undefined
        }
        items={apiProduct ? menuItems.filter((item): item is MenuItem => !!item) : []}
        loading={!apiProduct}
        menuItems={dropdownItems}
        onBack={handleBackToApiProducts}
        onItemClick={handleTabChange}
        subtitle={apiProduct ? PRODUCT_TYPE_LABELS[apiProduct.type] || apiProduct.type : undefined}
        title={apiProduct?.name}
      />

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
