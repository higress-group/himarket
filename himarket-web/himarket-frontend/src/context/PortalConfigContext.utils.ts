import { createContext } from 'react';

export interface TabItem {
  key: string;
  path: string;
  label: string;
}

export const ALL_TABS: TabItem[] = [
  { key: 'chat', label: 'tabs.chat', path: '/chat' },
  { key: 'coding', label: 'tabs.coding', path: '/coding' },
  { key: 'agents', label: 'tabs.agents', path: '/agents' },
  { key: 'mcp', label: 'tabs.mcp', path: '/mcp' },
  { key: 'models', label: 'tabs.models', path: '/models' },
  { key: 'apis', label: 'tabs.apis', path: '/apis' },
  { key: 'skills', label: 'tabs.skills', path: '/skills' },
  { key: 'workers', label: 'tabs.workers', path: '/workers' },
];

export interface PortalConfigContextValue {
  portalId: string;
  isMenuVisible: (key: string) => boolean;
  visibleTabs: TabItem[];
  firstVisiblePath: string;
  loading: boolean;
}

export const PortalConfigContext = createContext<PortalConfigContextValue>({
  firstVisiblePath: '/models',
  isMenuVisible: () => true,
  loading: true,
  portalId: '',
  visibleTabs: ALL_TABS,
});
