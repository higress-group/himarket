import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import monacoEditorModule from "vite-plugin-monaco-editor";
import path from "path";
import { fileURLToPath } from "url";

const monacoEditor = (monacoEditorModule as any).default || monacoEditorModule;

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiPrefix = env.VITE_API_BASE_URL || "/api/v1";

  return {
    plugins: [react(), monacoEditor({})],
    server: {
      host: "0.0.0.0",
      port: 5174,
      proxy: {
        [apiPrefix]: {
          target: "http://localhost:8080",
          changeOrigin: true,
          rewrite: (path) => path.replace(new RegExp(`^${apiPrefix}`), ""),
        },
      },
    },
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    build: {
      rollupOptions: {
        output: {
          entryFileNames: "index.js",
          chunkFileNames: "chunk-[name].js",
          assetFileNames: "assets/[name].[ext]",
        },
      },
    },
    define: {
      "process.env": {},
    },
  };
});
