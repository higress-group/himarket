import { message } from 'antd';
import axios from 'axios';
import qs from 'qs';

import { notifyAuthInvalidated } from '../hooks/useAuth';
import i18n from '../i18n';

import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';

export interface RespI<T> {
  code: string;
  message?: string;
  data: T;
}

/** Public page paths that allow anonymous access — 401/403 errors are silently ignored */
const PUBLIC_PATHS = [
  '/models',
  '/mcp',
  '/agents',
  '/apis',
  '/skills',
  '/workers',
  '/chat',
  '/coding',
  '/quest',
];

/** Check if current page is a public page that allows anonymous browsing */
function isPublicPage(): boolean {
  const pathname = window.location.pathname;
  if (pathname === '/') return true;
  return PUBLIC_PATHS.some((path) => pathname === path || pathname.startsWith(path + '/'));
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  paramsSerializer: (params) => {
    return qs.stringify(params, {
      arrayFormat: 'repeat',
      encode: true,
      skipNulls: true,
    });
  },
  timeout: 10000,
});

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const accessToken = localStorage.getItem('access_token');

    if (config.headers && accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  (error) => {
    const status = error.response?.status;

    switch (status) {
      case 401:
        if (isPublicPage()) {
          if (localStorage.getItem('access_token')) {
            localStorage.removeItem('access_token');
            notifyAuthInvalidated();
          }
          break;
        }
        message.error(i18n.t('error.authExpired'));
        localStorage.removeItem('access_token');
        if (window.location.pathname !== '/login') {
          const returnUrl = encodeURIComponent(
            window.location.pathname + window.location.search + window.location.hash,
          );
          window.location.href = `/login?returnUrl=${returnUrl}`;
        }
        break;
      case 403:
        if (isPublicPage()) {
          if (localStorage.getItem('access_token')) {
            localStorage.removeItem('access_token');
            notifyAuthInvalidated();
          }
          break;
        }
        localStorage.removeItem('access_token');
        if (window.location.pathname !== '/login') {
          const returnUrl = encodeURIComponent(
            window.location.pathname + window.location.search + window.location.hash,
          );
          window.location.href = `/login?returnUrl=${returnUrl}`;
        }
        break;
      case 404:
        message.error(i18n.t('error.notFound'));
        break;
      case 500:
        message.error(i18n.t('error.serverError'));
        break;
      default:
        message.error(error.response?.data?.message || i18n.t('error.requestFailed'));
    }
    return Promise.reject(error);
  },
);

export default request;
