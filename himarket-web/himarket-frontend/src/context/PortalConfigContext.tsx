import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { ALL_TABS, PortalConfigContext } from './PortalConfigContext.utils';
import { getPortalProfile } from '../lib/apis/portal';

export function PortalConfigProvider({ children }: { children: ReactNode }) {
  const [portalId, setPortalId] = useState('');
  const [menuVisibility, setMenuVisibility] = useState<Record<string, boolean> | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchConfig = () => {
    getPortalProfile()
      .then((res) => {
        setPortalId(res.data?.portalId || '');
        const mv = res.data?.portalUiConfig?.menuVisibility ?? null;
        setMenuVisibility(mv);
      })
      .catch((err) => {
        console.warn('[PortalConfig] API failed:', err);
        setMenuVisibility(null);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  const isMenuVisible = useCallback(
    (key: string): boolean => {
      if (menuVisibility === null || menuVisibility === undefined) return true;
      return menuVisibility[key] ?? true;
    },
    [menuVisibility],
  );

  const visibleTabs = useMemo(() => {
    return ALL_TABS.filter((tab) => isMenuVisible(tab.key));
  }, [isMenuVisible]);

  const firstVisiblePath = useMemo(() => {
    const first = visibleTabs[0];
    return first ? first.path : '/models';
  }, [visibleTabs]);

  return (
    <PortalConfigContext.Provider
      value={{ firstVisiblePath, isMenuVisible, loading, portalId, visibleTabs }}
    >
      {children}
    </PortalConfigContext.Provider>
  );
}
