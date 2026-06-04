import {
  EyeOutlined,
  ApiOutlined,
  TeamOutlined,
  SafetyOutlined,
  CloudOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { Button, Spin, Modal, message } from 'antd';
import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { AdminDetailSidebar } from '@/components/common';
import { PortalConsumers } from '@/components/portal/PortalConsumers';
import { PortalDashboard } from '@/components/portal/PortalDashboard';
import { PortalDevelopers } from '@/components/portal/PortalDevelopers';
import { PortalDomain } from '@/components/portal/PortalDomain';
import PortalFormModal from '@/components/portal/PortalFormModal';
import { PortalMenuSettings } from '@/components/portal/PortalMenuSettings';
import { PortalOverview } from '@/components/portal/PortalOverview';
import { PortalPublishedApis } from '@/components/portal/PortalPublishedApis';
import { PortalSecurity } from '@/components/portal/PortalSecurity';
import { useLocale } from '@/contexts/LocaleContext';
import { portalApi } from '@/lib/api';
import type { ApiResponse, Portal } from '@/types';

import type { MenuProps } from 'antd';

export default function PortalDetail() {
  const navigate = useNavigate();
  const { t } = useLocale();
  const { portalId } = useParams<{ portalId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [portal, setPortal] = useState<Portal | null>(null);
  const [loading, setLoading] = useState(true); // 初始状态为 loading
  const [error, setError] = useState<string | null>(null);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const menuItems = useMemo(
    () => [
      {
        description: t('portal.detail.menu.overview.description'),
        icon: EyeOutlined,
        key: 'overview',
        label: t('portal.detail.menu.overview.label'),
      },
      {
        description: t('portal.detail.menu.products.description'),
        icon: ApiOutlined,
        key: 'published-apis',
        label: t('portal.detail.menu.products.label'),
      },
      {
        description: t('portal.detail.menu.developers.description'),
        icon: TeamOutlined,
        key: 'developers',
        label: t('portal.detail.menu.developers.label'),
      },
      {
        description: t('portal.detail.menu.security.description'),
        icon: SafetyOutlined,
        key: 'security',
        label: t('portal.detail.menu.security.label'),
      },
      {
        description: t('portal.detail.menu.domain.description'),
        icon: CloudOutlined,
        key: 'domain',
        label: t('portal.detail.menu.domain.label'),
      },
      {
        description: t('portal.detail.menu.menu.description'),
        icon: MenuUnfoldOutlined,
        key: 'menu',
        label: t('portal.detail.menu.menu.label'),
      },
    ],
    [t],
  );

  // 从URL查询参数获取当前tab，默认为overview
  const currentTab = searchParams.get('tab') || 'overview';

  const fetchPortalData = useCallback(async () => {
    try {
      setLoading(true);
      const id = portalId || '';
      const response = (await portalApi.getPortalDetail(id)) as unknown as ApiResponse<Portal>;
      if (response && response.code === 'SUCCESS') {
        setPortal(response.data);
      } else {
        setError(response?.message || t('portal.detail.loadFailed'));
      }
    } catch (err) {
      console.error(t('portal.detail.loadFailed'), err);
      setError(t('portal.detail.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [portalId, t]);

  useEffect(() => {
    fetchPortalData();
  }, [fetchPortalData]);

  const handleBackToPortals = () => {
    navigate('/portals');
  };

  // 处理tab切换，同时更新URL查询参数
  const handleTabChange = (tabKey: string) => {
    const newSearchParams = new URLSearchParams(searchParams);
    newSearchParams.set('tab', tabKey);
    setSearchParams(newSearchParams);
  };

  const handleEdit = () => {
    setEditModalVisible(true);
  };

  const handleEditSuccess = () => {
    setEditModalVisible(false);
    fetchPortalData();
  };

  const handleEditCancel = () => {
    setEditModalVisible(false);
  };

  const renderContent = () => {
    if (!portal) return null;

    switch (currentTab) {
      case 'overview':
        return <PortalOverview onEdit={handleEdit} portal={portal} />;
      case 'published-apis':
        return <PortalPublishedApis portal={portal} />;
      case 'developers':
        return <PortalDevelopers portal={portal} />;
      case 'security':
        return <PortalSecurity onRefresh={fetchPortalData} portal={portal} />;
      case 'domain':
        return <PortalDomain onRefresh={fetchPortalData} portal={portal} />;
      case 'menu':
        return <PortalMenuSettings onRefresh={fetchPortalData} portal={portal} />;
      case 'consumers':
        return <PortalConsumers portal={portal} />;
      case 'dashboard':
        return <PortalDashboard portal={portal} />;
      default:
        return <PortalOverview onEdit={handleEdit} portal={portal} />;
    }
  };

  const dropdownItems: MenuProps['items'] = [
    {
      danger: true,
      key: 'delete',
      label: t('common.delete'),
      onClick: () => {
        Modal.confirm({
          content: t('portal.detail.deleteConfirm'),
          onOk: () => {
            return handleDeletePortal();
          },
          title: t('portal.detail.deleteTitle'),
        });
      },
    },
  ];
  const handleDeletePortal = () => {
    return portalApi
      .deletePortal(portalId || '')
      .then(() => {
        message.success(t('portal.detail.deleteSuccess'));
        navigate('/portals');
      })
      .catch((error) => {
        message.error(error?.response?.data?.message || t('portal.detail.deleteFailed'));
        throw error; // 重新抛出错误，让Modal保持loading状态
      });
  };

  if (error || !portal) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="text-center">
          {error && (
            <>
              <p className=" mb-4">{error || t('portal.detail.infoMissing')}</p>
              <Button onClick={() => navigate('/portals')}>{t('portal.detail.backToList')}</Button>
            </>
          )}
          {!error && <Spin fullscreen spinning={loading} />}
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full">
      <Spin fullscreen spinning={loading} />
      {/* Portal详情侧边栏 */}
      <AdminDetailSidebar
        activeKey={currentTab}
        backLabel={t('common.back')}
        items={menuItems}
        menuItems={dropdownItems}
        onBack={handleBackToPortals}
        onItemClick={handleTabChange}
        subtitle="Portal"
        title={portal.name}
      />

      {/* 主内容区域 */}
      <div className="flex-1 overflow-auto">{renderContent()}</div>

      {portal && (
        <PortalFormModal
          onCancel={handleEditCancel}
          onSuccess={handleEditSuccess}
          portal={portal}
          visible={editModalVisible}
        />
      )}
    </div>
  );
}
