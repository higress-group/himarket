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
import { useLocale } from '@/contexts/LocaleContext';
import type { TranslationKey } from '@/i18n';
import { portalApi } from '@/lib/api';
import type { Portal } from '@/types';

interface PortalMenuSettingsProps {
  portal: Portal;
  onRefresh?: () => void;
}

interface MenuItemConfig {
  key: string;
  labelKey: TranslationKey;
  descriptionKey: TranslationKey;
  icon: React.ReactNode;
}

const MENU_ITEMS: MenuItemConfig[] = [
  {
    descriptionKey: 'portal.menu.item.chat.description',
    icon: <MessageOutlined />,
    key: 'chat',
    labelKey: 'portal.menu.item.chat.label',
  },
  {
    descriptionKey: 'portal.menu.item.coding.description',
    icon: <CodeOutlined />,
    key: 'coding',
    labelKey: 'portal.menu.item.coding.label',
  },
  {
    descriptionKey: 'portal.menu.item.agents.description',
    icon: <RobotOutlined />,
    key: 'agents',
    labelKey: 'portal.menu.item.agents.label',
  },
  {
    descriptionKey: 'portal.menu.item.mcp.description',
    icon: <McpServerIcon />,
    key: 'mcp',
    labelKey: 'portal.menu.item.mcp.label',
  },
  {
    descriptionKey: 'portal.menu.item.models.description',
    icon: <BulbOutlined />,
    key: 'models',
    labelKey: 'portal.menu.item.models.label',
  },
  {
    descriptionKey: 'portal.menu.item.apis.description',
    icon: <ApiOutlined />,
    key: 'apis',
    labelKey: 'portal.menu.item.apis.label',
  },
  {
    descriptionKey: 'portal.menu.item.skills.description',
    icon: <ThunderboltOutlined />,
    key: 'skills',
    labelKey: 'portal.menu.item.skills.label',
  },
  {
    descriptionKey: 'portal.menu.item.workers.description',
    icon: <UserOutlined />,
    key: 'workers',
    labelKey: 'portal.menu.item.workers.label',
  },
];

export function PortalMenuSettings({ onRefresh, portal }: PortalMenuSettingsProps) {
  const { t } = useLocale();

  const getMenuVisibility = (key: string): boolean => {
    return portal.portalUiConfig?.menuVisibility?.[key] ?? true;
  };

  const handleToggle = async (key: string, checked: boolean) => {
    const currentVisibility = { ...(portal.portalUiConfig?.menuVisibility || {}) };
    const newVisibility = { ...currentVisibility, [key]: checked };

    // 至少保留一个菜单项可见
    const visibleCount = MENU_ITEMS.filter((item) => newVisibility[item.key] ?? true).length;
    if (visibleCount === 0) {
      message.warning(t('portal.menu.keepOne'));
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
      message.success(t('portal.menu.saveSuccess'));
      onRefresh?.();
    } catch {
      message.error(t('portal.menu.saveFailed'));
    }
  };

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">{t('portal.menu.title')}</h1>
        <p className="text-gray-600">{t('portal.menu.description')}</p>
      </div>

      <div className="mb-4">
        <h3 className="text-sm font-medium text-gray-600 mb-4">{t('portal.menu.itemsTitle')}</h3>
        <div className="grid grid-cols-4 gap-2">
          {MENU_ITEMS.map((item) => {
            const enabled = getMenuVisibility(item.key);
            return (
              <div
                className="flex items-center gap-2 px-3 py-2 rounded border border-gray-200 bg-white transition-all duration-200 cursor-pointer hover:border-blue-300"
                key={item.key}
              >
                <span className="text-base text-gray-600 flex-shrink-0">{item.icon}</span>
                <div className="min-w-0 flex-1">
                  <div className="font-medium text-gray-900 text-sm truncate">
                    {t(item.labelKey)}
                  </div>
                  <div className="text-xs text-gray-500 truncate">{t(item.descriptionKey)}</div>
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
          <strong>{t('portal.menu.tip')}</strong>
          {t('portal.menu.keepOne')}
        </div>
      </div>
    </div>
  );
}
