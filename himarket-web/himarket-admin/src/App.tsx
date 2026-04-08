import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { RouterProvider } from 'react-router-dom';

import aliyunThemeToken from './aliyunThemeToken';
import { LoadingProvider } from './contexts/LoadingContext';
import { router } from './routes';
import './App.css';

function App() {
  return (
    <LoadingProvider>
      <ConfigProvider
        locale={zhCN}
        theme={{
          components: {
            Button: {
              primaryShadow: '0 2px 4px rgba(99, 102, 241, 0.3)',
            },
            Card: {
              borderRadiusLG: 12,
            },
            Table: {
              rowHoverBg: '#EEF2FF',
            },
          },
          token: aliyunThemeToken,
        }}
      >
        <RouterProvider router={router} />
      </ConfigProvider>
    </LoadingProvider>
  );
}

export default App;
