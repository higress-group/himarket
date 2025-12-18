import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Skeleton, message, Dropdown, MenuProps, Modal } from 'antd';
import {
  LeftOutlined,
  EyeOutlined,
  ApiOutlined,
  CloudServerOutlined,
  HistoryOutlined,
  MoreOutlined
} from '@ant-design/icons';
import { apiDefinitionApi } from '@/lib/api';
import { ApiDefinitionOverview } from '@/components/api-definition/ApiDefinitionOverview';
import EndpointListTab from '@/components/api-definition/EndpointListTab';
import PublishRecordsTab from '@/components/api-definition/PublishRecordsTab';
import PublishHistoryTab from '@/components/api-definition/PublishHistoryTab';

interface ApiDefinition {
  apiDefinitionId: string;
  name: string;
  description: string;
  type: string;
  status: string;
  version: string;
  properties?: Array<Record<string, any>>;
  createdAt: string;
  updatedAt: string;
}

const menuItems = [
  {
    key: 'overview',
    label: 'Overview',
    description: 'API概览',
    icon: EyeOutlined
  },
  {
    key: 'endpoints',
    label: 'Endpoints',
    description: '端点配置',
    icon: ApiOutlined
  },
  {
    key: 'publish-records',
    label: 'Publish Records',
    description: '发布记录',
    icon: CloudServerOutlined
  },
  {
    key: 'publish-history',
    label: 'Publish History',
    description: '发布历史',
    icon: HistoryOutlined
  }
];

export default function ApiDefinitionDetail() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [apiDefinition, setApiDefinition] = useState<ApiDefinition | null>(null);
  
  // 从URL query参数获取当前tab，默认为overview
  const currentTab = searchParams.get('tab') || 'overview';
  // 验证tab值是否有效，如果无效则使用默认值
  const validTab = menuItems.some(item => item.key === currentTab) ? currentTab : 'overview';
  const [activeTab, setActiveTab] = useState(validTab);
  
  const publishRecordsTabRef = useRef<{ handlePublish: () => void }>(null);

  useEffect(() => {
    const id = searchParams.get('id');
    if (id) {
      fetchApiDefinition(id);
    }
  }, [searchParams.get('id')]);

  // 同步URL参数和activeTab状态
  useEffect(() => {
    setActiveTab(validTab);
  }, [validTab, searchParams]);

  const fetchApiDefinition = async (id: string) => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getApiDefinitionDetail(id);
      // 后端返回的数据结构: { code, message, data: { ...apiDefinition } }
      if (response && response.data) {
        setApiDefinition(response.data);
      } else if (response && !response.code) {
        // 兼容旧的数据结构（直接返回数据）
        setApiDefinition(response);
      } else {
        setApiDefinition(null);
      }
    } catch (error) {
      message.error('获取 API 详情失败');
      setApiDefinition(null);
    } finally {
      setLoading(false);
    }
  };

  const handleBack = () => {
    navigate('/api-definitions');
  };

  const handleEdit = () => {
    navigate(`/api-definitions/edit?id=${apiDefinition?.apiDefinitionId}`);
  };

  const handlePublish = () => {
    // 切换到发布记录 Tab
    setActiveTab('publish-records');
    // 更新URL参数
    const newSearchParams = new URLSearchParams(searchParams);
    newSearchParams.set('tab', 'publish-records');
    setSearchParams(newSearchParams);
    // 等待 Tab 切换完成后触发发布
    setTimeout(() => {
      publishRecordsTabRef.current?.handlePublish();
    }, 100);
  };

  const handleTabChange = (tabKey: string) => {
    setActiveTab(tabKey);
    // 更新URL query参数
    const newSearchParams = new URLSearchParams(searchParams);
    newSearchParams.set('tab', tabKey);
    setSearchParams(newSearchParams);
  };

  const handleDelete = () => {
    if (!apiDefinition) return;
    
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该 API Definition 吗？',
      onOk: async () => {
        try {
          await apiDefinitionApi.deleteApiDefinition(apiDefinition.apiDefinitionId);
          message.success('删除成功');
          navigate('/api-definitions');
        } catch (error) {
          message.error('删除失败');
        }
      },
    });
  };

  const dropdownItems: MenuProps['items'] = [
    {
      key: 'delete',
      label: '删除',
      onClick: handleDelete,
      danger: true,
    },
  ];

  const renderContent = () => {
    // 加载中状态
    if (loading) {
      return (
        <div className="p-6">
          <Skeleton active />
        </div>
      );
    }

    // 数据不存在
    if (!apiDefinition) {
      return (
        <div className="flex items-center justify-center h-full">
          <div className="text-center">
            <p className="text-gray-500 mb-4">API Definition 不存在</p>
            <Button type="primary" onClick={handleBack}>
              返回列表
            </Button>
          </div>
        </div>
      );
    }

    switch (activeTab) {
      case 'overview':
        return (
          <ApiDefinitionOverview
            apiDefinition={apiDefinition}
            endpointCount={0}
            publishedTargetCount={0}
            onEdit={handleEdit}
            onPublish={handlePublish}
          />
        );
      case 'endpoints':
        return (
          <EndpointListTab
            apiDefinitionId={apiDefinition.apiDefinitionId}
            apiType={apiDefinition.type}
          />
        );
      case 'publish-records':
        return (
          <PublishRecordsTab
            ref={publishRecordsTabRef}
            apiDefinitionId={apiDefinition.apiDefinitionId}
            status={apiDefinition.status}
          />
        );
      case 'publish-history':
        return (
          <PublishHistoryTab apiDefinitionId={apiDefinition.apiDefinitionId} />
        );
      default:
        return (
          <ApiDefinitionOverview
            apiDefinition={apiDefinition}
            endpointCount={0}
            publishedTargetCount={0}
            onEdit={handleEdit}
            onPublish={handlePublish}
          />
        );
    }
  };

  return (
    <div className="flex h-full w-full overflow-hidden">
      {/* API Definition 详情侧边栏 */}
      <div className="w-64 border-r bg-white flex flex-col flex-shrink-0">
        {/* 返回按钮 */}
        <div className="pb-4 border-b">
          <Button
            type="text"
            onClick={handleBack}
            icon={<LeftOutlined />}
          >
            返回
          </Button>
        </div>

        {/* API Definition 信息 */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-semibold truncate">
              {loading ? 'Loading...' : apiDefinition?.name || 'Unknown'}
            </h2>
            <Dropdown menu={{ items: dropdownItems }} trigger={['click']} disabled={loading || !apiDefinition}>
              <Button type="text" icon={<MoreOutlined />} disabled={loading || !apiDefinition} />
            </Dropdown>
          </div>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 space-y-1">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.key}
                onClick={() => handleTabChange(item.key)}
                className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left transition-colors ${
                  activeTab === item.key
                    ? 'bg-blue-500 text-white'
                    : 'hover:bg-gray-100'
                }`}
              >
                <Icon className="h-4 w-4 flex-shrink-0" />
                <div>
                  <div className="font-medium">{item.label}</div>
                  <div className="text-xs opacity-70">{item.description}</div>
                </div>
              </button>
            );
          })}
        </nav>
      </div>

      {/* 主内容区域 */}
      <div className="flex-1 overflow-auto min-w-0">
        <div className="w-full max-w-full">
          {renderContent()}
        </div>
      </div>
    </div>
  );
}
