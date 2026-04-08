import { useCallback, useSyncExternalStore } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * 全局 auth 失效通知。
 * 当 request 拦截器在公开页面检测到 401/403 并清除了过期 token 后，
 * 通过 `notifyAuthInvalidated()` 通知所有 useAuth 消费者刷新状态。
 */
let authVersion = 0;
const listeners = new Set<() => void>();

export function notifyAuthInvalidated() {
  authVersion++;
  listeners.forEach((l) => l());
}

function subscribeAuth(callback: () => void) {
  listeners.add(callback);
  return () => {
    listeners.delete(callback);
  };
}

function getAuthSnapshot() {
  return authVersion;
}

export function useAuth() {
  const navigate = useNavigate();

  // 订阅 auth 失效事件，触发重新读取 token
  useSyncExternalStore(subscribeAuth, getAuthSnapshot);

  const token = localStorage.getItem('access_token');
  const isLoggedIn = !!token;

  const login = useCallback(
    (returnUrl?: string) => {
      const url = returnUrl || window.location.pathname + window.location.search;
      navigate(`/login?returnUrl=${encodeURIComponent(url)}`);
    },
    [navigate],
  );

  return { isLoggedIn, login, token };
}
