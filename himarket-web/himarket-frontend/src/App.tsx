import './i18n';

import { ConfigProvider } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { useTranslation } from 'react-i18next';
import { BrowserRouter } from 'react-router-dom';

import './App.css';
import './styles/table.css';
import aliyunThemeToken from './aliyunThemeToken.ts';
import { PortalConfigProvider } from './context/PortalConfigContext';
import { Router } from './router';

function App() {
  const { i18n } = useTranslation();
  const antdLocale = i18n.language === 'zh-CN' ? zhCN : enUS;

  return (
    <ConfigProvider
      locale={antdLocale}
      theme={{
        token: aliyunThemeToken,
      }}
    >
      <BrowserRouter>
        <PortalConfigProvider>
          <Router />
        </PortalConfigProvider>
      </BrowserRouter>
    </ConfigProvider>
  );
}

export default App;
