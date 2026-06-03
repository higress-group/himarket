import { useCallback, useEffect, useState } from 'react';

import { adminSettingApi } from '@/lib/api';
import type { AdminSettingResult } from '@/lib/api';

export type ViewMode = 'TABLE' | 'CARD';

interface AdminSettingResponse {
  data?: AdminSettingResult;
}

function isViewMode(value?: string): value is ViewMode {
  return value === 'TABLE' || value === 'CARD';
}

export function useAdminViewMode(settingKey: string) {
  const [viewMode, setViewModeState] = useState<ViewMode>('TABLE');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    adminSettingApi
      .getSetting(settingKey)
      .then((res: AdminSettingResponse) => {
        const settingValue = res.data?.settingValue;
        if (!cancelled && isViewMode(settingValue)) {
          setViewModeState(settingValue);
        }
      })
      .catch(() => {
        // Keep the default view mode when the setting cannot be loaded.
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [settingKey]);

  const setViewMode = useCallback(
    (nextViewMode: ViewMode) => {
      setViewModeState(nextViewMode);
      adminSettingApi.saveSetting(settingKey, nextViewMode).catch(() => {
        // The global interceptor already reports the error to the user.
      });
    },
    [settingKey],
  );

  return {
    loading,
    setViewMode,
    viewMode,
  };
}
