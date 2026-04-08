import {
  ApiOutlined,
  RobotOutlined,
  BulbOutlined,
  ThunderboltOutlined,
  UserOutlined,
  MessageOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import { Switch, message } from 'antd';

import McpServerIcon from '@/components/icons/McpServerIcon';
import { portalApi } from '@/lib/api';
import type { Portal } from '@/types';

interface PortalMenuSettingsProps {
  portal: Portal;
  onRefresh?: () => void;
}

interface MenuItemConfig {
  key: string;
  label: string;
  description: string;
  icon: React.ReactNode;
}

const MENU_ITEMS: MenuItemConfig[] = [
  {
    description: 'AI 对话',
    icon: <MessageOutlined />,
    key: 'chat',
    label: 'HiChat',
  },
  {
    description: '在线编程',
    icon: <CodeOutlined />,
    key: 'coding',
    label: 'HiCoding',
  },
  {
    description: 'AI Agent 市场',
    icon: <RobotOutlined />,
    key: 'agents',
    label: '智能体',
  },
  {
    description: 'MCP 服务器',
    icon: <McpServerIcon />,
    key: 'mcp',
    label: 'MCP',
  },
  {
    description: 'LLM 模型',
    icon: <BulbOutlined />,
    key: 'models',
    label: '模型',
  },
  {
    description: 'REST API 产品',
    icon: <ApiOutlined />,
    key: 'apis',
    label: 'API',
  },
  {
    description: 'Agent 技能',
    icon: <ThunderboltOutlined />,
    key: 'skills',
    label: 'Skills',
  },
  {
    description: 'Worker 模板',
    icon: <UserOutlined />,
    key: 'workers',
    label: 'Workers',
  },
];

export function PortalMenuSettings({ onRefresh, portal }: PortalMenuSettingsProps) {
  const getMenuVisibility = (key: string): boolean => {
    return portal.portalUiConfig?.menuVisibility?.[key] ?? true;
  };

  const handleToggle = async (key: string, checked: boolean) => {
    const currentVisibility = { ...(portal.portalUiConfig?.menuVisibility || {}) };
    const newVisibility = { ...currentVisibility, [key]: checked };

    // 至少保留一个菜单项可见
    const visibleCount = MENU_ITEMS.filter((item) => newVisibility[item.key] ?? true).length;
    if (visibleCount === 0) {
      message.warning('至少保留一个菜单项为可见状态');
      return;
    }

    try {
      await portalApi.updatePortal(portal.portalId, {
        description: portal.description,
        name: portal.name,
        portalDomainConfig: portal.portalDomainConfig,
        portalSettingConfig: portal.portalSettingConfig,
        portalUiConfig: {
          ...portal.portalUiConfig,
          menuVisibility: newVisibility,
        },
      });
      message.success('菜单配置保存成功');
      onRefresh?.();
    } catch {
      message.error('保存菜单配置失败');
    }
  };

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">菜单管理</h1>
        <p className="text-gray-600">配置导航菜单</p>
      </div>

      <div className="mb-4">
        <h3 className="text-sm font-medium text-gray-600 mb-4">导航菜单项</h3>
        <div className="grid grid-cols-3 gap-2">
          {MENU_ITEMS.map((item) => {
            const enabled = getMenuVisibility(item.key);
            return (
              <div
                className={`
                  flex items-center gap-2 px-3 py-2 rounded border transition-all duration-200 cursor-pointer
                  hover:border-blue-300
                  ${enabled ? 'border-blue-300 bg-blue-50/50' : 'border-gray-200 bg-white'}
                `}
                key={item.key}
              >
                <span className="text-base text-gray-600 flex-shrink-0">{item.icon}</span>
                <div className="min-w-0 flex-1">
                  <div className="font-medium text-gray-900 text-sm truncate">{item.label}</div>
                  <div className="text-xs text-gray-500 truncate">{item.description}</div>
                </div>
                <Switch
                  checked={enabled}
                  onChange={(checked) => handleToggle(item.key, checked)}
                  size="small"
                />
              </div>
            );
          })}
        </div>
      </div>

      <div className="mt-6 p-4 bg-gray-50 rounded-lg">
        <div className="text-sm text-gray-600">
          <strong>提示：</strong>至少保留一个菜单项为可见状态
        </div>
      </div>
    </div>
  );
}
