import { message } from 'antd';
import axios from 'axios';
import qs from 'qs';

import { notifyAuthInvalidated } from '../hooks/useAuth';

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
      arrayFormat: 'repeat', // 数组格式: ids=1&ids=2（而不是 ids[]=1）
      encode: true, // 确保特殊字符被正确编码
      skipNulls: true, // 跳过 null 和 undefined 值
    });
  },
  timeout: 10000,
});

// 请求拦截器
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

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  (error) => {
    const status = error.response?.status;

    switch (status) {
      case 401:
        if (isPublicPage()) {
          // 公开页面：如果有过期 token 则清除，并通知组件更新登录状态
          if (localStorage.getItem('access_token')) {
            localStorage.removeItem('access_token');
            notifyAuthInvalidated();
          }
          break;
        }
        message.error('未登录或登录已过期，请重新登录');
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
        message.error('请求的资源不存在');
        break;
      case 500:
        message.error('服务器异常，请稍后再试');
        break;
      default:
        message.error(error.response?.data?.message || '请求发生错误');
    }
    return Promise.reject(error);
  },
);

export default request;
