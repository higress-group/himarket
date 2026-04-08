import path from 'path';
import { fileURLToPath } from 'url';

import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import monacoEditorModule from 'vite-plugin-monaco-editor';
// vite-plugin-monaco-editor may export the plugin as .default in some module resolutions
// @ts-expect-error -- module default export compatibility
var monacoEditor = monacoEditorModule.default || monacoEditorModule;
var __dirname = path.dirname(fileURLToPath(import.meta.url));
// https://vite.dev/config/
export default defineConfig(function (_a) {
  var _b;
  var mode = _a.mode;
  var env = loadEnv(mode, process.cwd(), '');
  var apiPrefix = env.VITE_API_BASE_URL || '/api/v1';
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
      proxy:
        ((_b = {}),
        (_b[apiPrefix] = {
          changeOrigin: true,
          rewrite: function (path) {
            return path.replace(new RegExp('^'.concat(apiPrefix)), '');
          },
          target: 'http://localhost:8080',
        }),
        _b),
    },
  };
});
