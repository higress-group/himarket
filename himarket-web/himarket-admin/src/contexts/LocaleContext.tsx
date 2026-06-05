import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import {
  ADMIN_LOCALE_SETTING_KEY,
  ADMIN_LOCALE_STORAGE_KEY,
  DEFAULT_LOCALE,
  isLocale,
  messages,
  type Locale,
  type TranslationKey,
} from '@/i18n';
import { adminSettingApi, type AdminSettingResult } from '@/lib/api';
import { ADMIN_AUTH_CHANGED_EVENT, isAuthenticated } from '@/lib/utils';

interface LocaleContextValue {
  locale: Locale;
  setLocale: (nextLocale: Locale) => void;
  t: (key: TranslationKey, values?: Record<string, number | string>) => string;
}

interface AdminSettingResponse {
  data?: AdminSettingResult;
}

const LocaleContext = createContext<LocaleContextValue | null>(null);
let remoteLocaleRequest: Promise<Locale | null> | null = null;

function getStoredLocale(): Locale {
  if (typeof window === 'undefined') {
    return DEFAULT_LOCALE;
  }

  const storedLocale = window.localStorage.getItem(ADMIN_LOCALE_STORAGE_KEY) || undefined;
  return isLocale(storedLocale) ? storedLocale : DEFAULT_LOCALE;
}

function loadRemoteLocale() {
  if (remoteLocaleRequest) {
    return remoteLocaleRequest;
  }

  remoteLocaleRequest = adminSettingApi
    .getSetting(ADMIN_LOCALE_SETTING_KEY)
    .then((res: AdminSettingResponse) => {
      const nextLocale = res.data?.settingValue;
      return isLocale(nextLocale) ? nextLocale : null;
    })
    .catch(() => null)
    .finally(() => {
      remoteLocaleRequest = null;
    });

  return remoteLocaleRequest;
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(getStoredLocale);

  useEffect(() => {
    let cancelled = false;

    const syncRemoteLocale = () => {
      if (!isAuthenticated()) {
        return;
      }

      loadRemoteLocale().then((nextLocale) => {
        if (cancelled) {
          return;
        }

        if (nextLocale) {
          setLocaleState(nextLocale);
          window.localStorage.setItem(ADMIN_LOCALE_STORAGE_KEY, nextLocale);
        }
      });
    };

    syncRemoteLocale();
    window.addEventListener(ADMIN_AUTH_CHANGED_EVENT, syncRemoteLocale);

    return () => {
      cancelled = true;
      window.removeEventListener(ADMIN_AUTH_CHANGED_EVENT, syncRemoteLocale);
    };
  }, []);

  const setLocale = useCallback((nextLocale: Locale) => {
    setLocaleState(nextLocale);
    window.localStorage.setItem(ADMIN_LOCALE_STORAGE_KEY, nextLocale);
    if (!isAuthenticated()) {
      return;
    }

    adminSettingApi.saveSetting(ADMIN_LOCALE_SETTING_KEY, nextLocale).catch(() => {
      // The global interceptor reports the error; the local preference still applies immediately.
    });
  }, []);

  const value = useMemo<LocaleContextValue>(
    () => ({
      locale,
      setLocale,
      t: (key, values) => {
        const template = messages[locale][key] || messages[DEFAULT_LOCALE][key] || key;
        if (!values) {
          return template;
        }
        return template.replace(/\{(\w+)\}/g, (match, name: string) => {
          const value = values[name];
          return value === undefined ? match : String(value);
        });
      },
    }),
    [locale, setLocale],
  );

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useLocale() {
  const context = useContext(LocaleContext);
  if (!context) {
    throw new Error('useLocale must be used within LocaleProvider');
  }
  return context;
}
