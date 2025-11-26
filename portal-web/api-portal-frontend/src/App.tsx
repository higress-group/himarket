import { BrowserRouter } from "react-router-dom";
import { Router } from "./router";
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import './App.css'
import "./styles/table.css";
import aliyunThemeToken from './aliyunThemeToken.ts';

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: aliyunThemeToken
      }}
    >
      <BrowserRouter>
        <Router />
      </BrowserRouter>
    </ConfigProvider>
  );
}

export default App;
