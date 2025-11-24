import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
const env = loadEnv(process.env.NODE_ENV || 'development', process.cwd(), '')
const apiPrefix = env.VITE_API_BASE_URL
const backendUrl = env.VITE_API_BASE_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    allowedHosts: true,
    port: 5173,
    proxy: {
      [apiPrefix]: {
        target: backendUrl,
        changeOrigin: true,
        rewrite: (p) => p.replace(new RegExp(`^${apiPrefix}`), ''),
      },
    },
  },
  optimizeDeps: {
    include: ['monaco-editor/esm/vs/editor/editor.api']
  },
  build: {
    rollupOptions: {
      external: ['monaco-editor']
    }
  },
  define: {
    'process.env': {}
  }
})
