import { LeftOutlined, MoreOutlined } from '@ant-design/icons';
import { Button, Dropdown } from 'antd';

import type { MenuProps } from 'antd';
import type { ReactNode } from 'react';

export interface AdminDetailSidebarItem {
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  key: string;
  label: string;
}

interface AdminDetailSidebarProps {
  activeKey: string;
  backLabel?: string;
  icon?: ReactNode;
  items: AdminDetailSidebarItem[];
  loading?: boolean;
  menuItems?: MenuProps['items'];
  onBack: () => void;
  onItemClick: (key: string) => void;
  subtitle?: string;
  title?: string;
}

export function AdminDetailSidebar({
  activeKey,
  backLabel = '返回',
  icon,
  items,
  loading,
  menuItems,
  onBack,
  onItemClick,
  subtitle,
  title,
}: AdminDetailSidebarProps) {
  return (
    <aside className="flex w-64 shrink-0 flex-col border-r bg-white">
      <div className="border-b px-4 py-4">
        <Button
          className="-ml-2 mb-4 text-gray-600 hover:text-gray-900"
          icon={<LeftOutlined />}
          onClick={onBack}
          size="small"
          type="text"
        >
          {backLabel}
        </Button>

        <div className="flex items-start justify-between gap-3">
          {loading ? (
            <div className="flex min-w-0 flex-1 items-start gap-3">
              {icon ? <div className="h-10 w-10 shrink-0 rounded-lg bg-gray-100" /> : null}
              <div className="min-w-0 flex-1 space-y-2">
                <div className="h-4 w-32 animate-pulse rounded bg-gray-200" />
                <div className="h-3 w-20 animate-pulse rounded bg-gray-100" />
              </div>
            </div>
          ) : (
            <div className="flex min-w-0 flex-1 items-start gap-3">
              {icon}
              <div className="min-w-0">
                <h2 className="line-clamp-2 text-base font-semibold leading-5 text-gray-900">
                  {title}
                </h2>
                {subtitle && (
                  <p className="mt-0.5 truncate text-[11px] leading-4 text-gray-400">{subtitle}</p>
                )}
              </div>
            </div>
          )}

          {menuItems && menuItems.length > 0 && (
            <Dropdown menu={{ items: menuItems }} trigger={['click']}>
              <Button className="-mr-2 shrink-0" icon={<MoreOutlined />} size="small" type="text" />
            </Dropdown>
          )}
        </div>
      </div>

      <nav className="flex-1 space-y-1 p-4">
        {loading ? (
          <div className="space-y-2">
            {[1, 2, 3, 4].map((item) => (
              <div className="flex h-11 items-center gap-3 px-3" key={item}>
                <div className="h-4 w-4 shrink-0 animate-pulse rounded bg-gray-200" />
                <div className="flex-1 space-y-1">
                  <div className="h-4 w-20 animate-pulse rounded bg-gray-200" />
                </div>
              </div>
            ))}
          </div>
        ) : (
          items.map((item) => {
            const Icon = item.icon;
            const isActive = activeKey === item.key;
            return (
              <button
                aria-current={isActive ? 'page' : undefined}
                className={`relative flex w-full items-center gap-3 rounded-lg text-left transition-all duration-200 ${
                  isActive
                    ? 'min-h-[64px] bg-gray-50 px-3 py-2.5 text-gray-950 shadow-[inset_0_0_0_1px_rgba(17,24,39,0.06)]'
                    : 'h-11 px-3 text-gray-700 hover:bg-gray-100'
                }`}
                key={item.key}
                onClick={() => onItemClick(item.key)}
              >
                <Icon className={`h-4 w-4 shrink-0 ${isActive ? 'text-blue-500' : ''}`} />
                <div className="min-w-0 flex-1">
                  <div
                    className={`truncate font-medium ${
                      isActive ? 'text-[15px] leading-5' : 'text-sm leading-5'
                    }`}
                  >
                    {item.label}
                  </div>
                  {isActive && (
                    <div className="mt-0.5 truncate text-xs leading-4 text-gray-500">
                      {item.description}
                    </div>
                  )}
                </div>
              </button>
            );
          })
        )}
      </nav>
    </aside>
  );
}
