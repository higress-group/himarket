import {
  MoreOutlined,
  LeftOutlined,
  EyeOutlined,
  ApiOutlined,
  TeamOutlined,
  SafetyOutlined,
  CloudOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Spin, Modal, message } from 'antd';
import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { PortalConsumers } from '@/components/portal/PortalConsumers';
import { PortalDashboard } from '@/components/portal/PortalDashboard';
import { PortalDevelopers } from '@/components/portal/PortalDevelopers';
import { PortalDomain } from '@/components/portal/PortalDomain';
import PortalFormModal from '@/components/portal/PortalFormModal';
import { PortalMenuSettings } from '@/components/portal/PortalMenuSettings';
import { PortalOverview } from '@/components/portal/PortalOverview';
import { PortalPublishedApis } from '@/components/portal/PortalPublishedApis';
import { PortalSecurity } from '@/components/portal/PortalSecurity';
import { portalApi } from '@/lib/api';
import type { ApiResponse, Portal } from '@/types';

import type { MenuProps } from 'antd';

const menuItems = [
  {
    description: 'Portal概览',
    icon: EyeOutlined,
    key: 'overview',
    label: 'Overview',
  },
  {
    description: '已发布的API产品',
    icon: ApiOutlined,
    key: 'published-apis',
    label: 'Products',
  },
  {
    description: '开发者管理',
    icon: TeamOutlined,
    key: 'developers',
    label: 'Developers',
  },
  {
    description: '安全设置',
    icon: SafetyOutlined,
    key: 'security',
    label: 'Security',
  },
  {
    description: '域名管理',
    icon: CloudOutlined,
    key: 'domain',
    label: 'Domain',
  },
  {
    description: '菜单管理',
    icon: MenuUnfoldOutlined,
    key: 'menu',
    label: 'Menu',
  },
  // {
  //   key: "consumers",
  //   label: "Consumers",
  //   icon: UserOutlined,
  //   description: "消费者管理"
  // },
  // {
  //   key: "dashboard",
  //   label: "Dashboard",
  //   icon: DashboardOutlined,
  //   description: "监控面板"
  // }
];

export default function PortalDetail() {
  const navigate = useNavigate();
  const { portalId } = useParams<{ portalId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [portal, setPortal] = useState<Portal | null>(null);
  const [loading, setLoading] = useState(true); // 初始状态为 loading
  const [error, setError] = useState<string | null>(null);
  const [editModalVisible, setEditModalVisible] = useState(false);

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
        setError(response?.message || '获取Portal信息失败');
      }
    } catch (err) {
      console.error('获取Portal信息失败:', err);
      setError('获取Portal信息失败');
    } finally {
      setLoading(false);
    }
  }, [portalId]);

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
      label: '删除',
      onClick: () => {
        Modal.confirm({
          content: '确定要删除该Portal吗？',
          onOk: () => {
            return handleDeletePortal();
          },
          title: '删除Portal',
        });
      },
    },
  ];
  const handleDeletePortal = () => {
    return portalApi
      .deletePortal(portalId || '')
      .then(() => {
        message.success('删除成功');
        navigate('/portals');
      })
      .catch((error) => {
        message.error(error?.response?.data?.message || '删除失败，请稍后重试');
        throw error; // 重新抛出错误，让Modal保持loading状态
      });
  };

  if (error || !portal) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="text-center">
          {error && (
            <>
              <p className=" mb-4">{error || 'Portal信息不存在'}</p>
              <Button onClick={() => navigate('/portals')}>返回Portal列表</Button>
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
      <div className="w-64 border-r bg-white flex flex-col">
        {/* 返回按钮 */}
        <div className="pb-4 border-b">
          <Button
            icon={<LeftOutlined />}
            // className="w-full justify-start text-gray-600 hover:text-gray-900"
            onClick={handleBackToPortals}
            type="text"
          >
            返回
          </Button>
        </div>

        {/* Portal 信息 */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between">
            <p className="font-medium text-sm mb-0">{portal.name}</p>
            <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
              <Button icon={<MoreOutlined />} size="small" type="text" />
            </Dropdown>
          </div>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                className={`w-full flex items-center gap-3 px-3 py-3 rounded-lg text-left transition-colors ${
                  currentTab === item.key
                    ? 'bg-blue-50 text-blue-600 border border-blue-200'
                    : 'hover:bg-gray-50 text-gray-700'
                }`}
                key={item.key}
                onClick={() => handleTabChange(item.key)}
              >
                <Icon className="h-4 w-4 flex-shrink-0" />
                <div className="flex-1">
                  <div className="font-medium">{item.label}</div>
                  <div className="text-xs text-gray-500 mt-1">{item.description}</div>
                </div>
              </button>
            );
          })}
        </nav>
      </div>

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
