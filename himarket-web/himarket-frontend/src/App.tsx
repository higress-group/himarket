import "./i18n";
import { BrowserRouter } from "react-router-dom";
import { Router } from "./router";
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import enUS from "antd/locale/en_US";
import { useTranslation } from "react-i18next";
import "./App.css";
import "./styles/table.css";
import aliyunThemeToken from "./aliyunThemeToken.ts";
import { PortalConfigProvider } from "./context/PortalConfigContext";

function App() {
  const { i18n } = useTranslation();
  const antdLocale = i18n.language === "zh-CN" ? zhCN : enUS;

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
