import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import React from 'react';
import ReactDOM from 'react-dom/client';

// import { RouterProvider } from 'react-router-dom'
import App from './App.tsx';
import './index.css';

loader.config({ monaco });

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
