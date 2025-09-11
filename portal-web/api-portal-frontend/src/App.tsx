import { BrowserRouter } from "react-router-dom";
import Router from "./router";
import { ConfigProvider } from 'antd';
import './App.css'
import aliyunThemeToken from './aliyunThemeToken.ts';

function App() {
  return (
    <ConfigProvider
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
