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

const viewModeRequests = new Map<string, Promise<ViewMode | null>>();

function loadViewMode(settingKey: string) {
  const cachedRequest = viewModeRequests.get(settingKey);
  if (cachedRequest) {
    return cachedRequest;
  }

  const request = adminSettingApi
    .getSetting(settingKey)
    .then((res: AdminSettingResponse) => {
      const settingValue = res.data?.settingValue;
      return isViewMode(settingValue) ? settingValue : null;
    })
    .catch(() => null)
    .finally(() => {
      viewModeRequests.delete(settingKey);
    });

  viewModeRequests.set(settingKey, request);
  return request;
}

export function useAdminViewMode(settingKey: string) {
  const [viewMode, setViewModeState] = useState<ViewMode>('TABLE');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    loadViewMode(settingKey)
      .then((nextViewMode) => {
        if (!cancelled && nextViewMode) {
          setViewModeState(nextViewMode);
        }
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
