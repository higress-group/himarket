import React, { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { message, Spin } from "antd";
import api from "../lib/api";

const Callback: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const code = searchParams.get("code");
    const state = searchParams.get("state");

    if (!code || !state) {
      message.error("缺少 code 或 state 参数");
      return;
    }

    // 调用后端获取token
    api
      .post("/developers/token", { code, state })
      .then((res: any) => {
        if (res && res.data && res.data.access_token) {
          message.success("登录成功！");
          // 存储access_token
          localStorage.setItem('access_token', res.data.access_token);
          // 跳转首页
          navigate('/');
        } else {
          message.error("登录失败，未获取到 access_token");
        }
      })
      .catch(() => {
        message.error("登录失败，请重试");
      });
  }, [location.search, navigate]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <Spin tip="登录中，请稍候..." />
    </div>
  );
};

export default Callback;