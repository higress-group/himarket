import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
  },
  optimizeDeps: {
    include: ['monaco-editor/esm/vs/editor/editor.api']
  },
  build: {
    rollupOptions: {
      external: ['monaco-editor']
    }
  }
})
