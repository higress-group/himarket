import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import { createRoot } from 'react-dom/client';

import 'antd/dist/reset.css';
import './index.css';
import App from './App.tsx';

// 使用本地 monaco-editor 包，避免从 CDN (unpkg.com) 加载导致国内网络卡在 Loading...
loader.config({ monaco });

const rootElement = document.getElementById('root');
if (rootElement) {
  createRoot(rootElement).render(<App />);
}
