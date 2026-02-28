import path from "path"
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import monacoEditorModule from 'vite-plugin-monaco-editor'

const monacoEditor = (monacoEditorModule as any).default || monacoEditorModule

// https://vite.dev/config/
const env = loadEnv(process.env.NODE_ENV || 'development', process.cwd(), '')
const apiPrefix = env.VITE_API_BASE_URL
const tempApiUrl = env.VITE_TEMP_API_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react({
    babel: {
      plugins: ['babel-plugin-react-compiler']
    }
  }), monacoEditor({})],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: '0.0.0.0',
    allowedHosts: true,
    port: 5173,
    proxy: {
      '/ws/acp': {
        target: tempApiUrl,
        ws: true,
        changeOrigin: true,
      },
      '/ws/terminal': {
        target: tempApiUrl,
        ws: true,
        changeOrigin: true,
      },
      [apiPrefix]: {
        target: tempApiUrl,
        changeOrigin: true,
        rewrite: (p) => p.replace(new RegExp(`^${apiPrefix}`), ''),
      },
    },
  },
  optimizeDeps: {
  },
  build: {
    rollupOptions: {
    }
  },
  define: {
    'process.env': {}
  },
})
