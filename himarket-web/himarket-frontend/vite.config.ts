import path from 'path';

import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import monacoEditorModule from 'vite-plugin-monaco-editor';

// vite-plugin-monaco-editor 的 ESM 导出存在兼容性问题
// @ts-expect-error - 模块导出不完全符合类型定义
const monacoEditor = monacoEditorModule.default || monacoEditorModule;

// https://vite.dev/config/
const env = loadEnv(process.env.NODE_ENV || 'development', process.cwd(), '');
const apiPrefix = env.VITE_API_BASE_URL;
const tempApiUrl = env.VITE_TEMP_API_URL || 'http://localhost:8080';

export default defineConfig({
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-antd': ['antd', '@ant-design/icons'],
          'vendor-markdown': ['react-markdown', 'remark-gfm', 'rehype-highlight', 'highlight.js'],
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-swagger': ['swagger-ui-react'],
          'vendor-xterm': ['@xterm/xterm', '@xterm/addon-fit'],
        },
      },
    },
  },
  define: {
    'process.env': {},
  },
  optimizeDeps: {},
  plugins: [
    react({
      babel: {
        plugins: ['babel-plugin-react-compiler'],
      },
    }),
    monacoEditor({}),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    allowedHosts: true,
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/ws/acp': {
        changeOrigin: true,
        target: tempApiUrl,
        ws: true,
      },
      '/ws/terminal': {
        changeOrigin: true,
        target: tempApiUrl,
        ws: true,
      },
      [apiPrefix]: {
        changeOrigin: true,
        rewrite: (p) => p.replace(new RegExp(`^${apiPrefix}`), ''),
        target: tempApiUrl,
      },
    },
  },
});
