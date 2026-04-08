import path from 'path';
import { fileURLToPath } from 'url';

import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import monacoEditorModule from 'vite-plugin-monaco-editor';

// vite-plugin-monaco-editor may export the plugin as .default in some module resolutions
// @ts-expect-error -- module default export compatibility
const monacoEditor = monacoEditorModule.default || monacoEditorModule;

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiPrefix = env.VITE_API_BASE_URL || '/api/v1';

  return {
    build: {
      rollupOptions: {
        output: {
          assetFileNames: 'assets/[name].[ext]',
          chunkFileNames: 'chunk-[name].js',
          entryFileNames: 'index.js',
        },
      },
    },
    define: {
      'process.env': {},
    },
    plugins: [react(), monacoEditor({})],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5174,
      proxy: {
        [apiPrefix]: {
          changeOrigin: true,
          rewrite: (path) => path.replace(new RegExp(`^${apiPrefix}`), ''),
          target: 'http://localhost:8080',
        },
      },
    },
  };
});
