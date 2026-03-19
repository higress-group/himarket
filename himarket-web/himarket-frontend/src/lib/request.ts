import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { message } from 'antd';
import qs from 'qs';

export interface RespI<T> {
  code: string;
  message?: string;
  data: T;
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
  paramsSerializer: params => {
    return qs.stringify(params, {
      arrayFormat: 'repeat',  // 数组格式: ids=1&ids=2（而不是 ids[]=1）
      skipNulls: true,         // 跳过 null 和 undefined 值
      encode: true             // 确保特殊字符被正确编码
    });
  }
})


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
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  (error) => {
    const status = error.response?.status;

    // 公开浏览页面路径前缀列表 — 这些页面允许匿名访问，401/403 不跳转登录页
    const PUBLIC_PATH_PREFIXES = ['/models', '/mcp', '/agents', '/apis', '/skills', '/chat', '/coding', '/quest'];
    const currentPath = window.location.pathname;
    const isPublicPage = currentPath === '/' || PUBLIC_PATH_PREFIXES.some(prefix => currentPath.startsWith(prefix));

    switch (status) {
      case 401:
        if (isPublicPage) {
          // 公开页面静默处理，不跳转、不清除 Token
          break;
        }
        message.error('未登录或登录已过期，请重新登录');
        localStorage.removeItem('access_token');
        if (window.location.pathname !== '/login') {
          const returnUrl = encodeURIComponent(window.location.pathname + window.location.search + window.location.hash);
          window.location.href = `/login?returnUrl=${returnUrl}`;
        }
        break;
      case 403:
        if (isPublicPage) {
          break;
        }
        localStorage.removeItem('access_token');
        if (window.location.pathname !== '/login') {
          const returnUrl = encodeURIComponent(window.location.pathname + window.location.search + window.location.hash);
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
  }
)

export default request