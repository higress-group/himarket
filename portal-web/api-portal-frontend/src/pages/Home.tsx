import { Button, Typography } from "antd";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useEffect } from "react";
import { getTokenFromCookie } from "../lib/utils";
import { FunModelCard, AgentCard, FunAppCenterCard } from '../components/card';

const { Title, Paragraph } = Typography;

function HomePage() {

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const fromCookie = params.get("fromCookie");
    const token = getTokenFromCookie();
    if (fromCookie && token) {
      localStorage.setItem("access_token", token);
    }
  }, []);

  return (
    <Layout>
      <div className="text-center pt-12 pb-16">
        {/* 标题区域 */}
        <div className="mb-16">
          <Title level={1} className="text-5xl font-bold text-gray-900 mb-8">
            HiMarket 企业级AI开放平台
          </Title>
          <Paragraph className="text-xl text-gray-600">
            开箱即用，快速集成
          </Paragraph>
        </div>
        
        {/* Get Started 按钮 */}
        <div className="mb-24">
          <Link to="/models">
            <Button 
              type="primary" 
              size="large" 
              className="px-6 py-2 text-base h-auto rounded-lg"
            >
              Get Started
            </Button>
          </Link>
        </div>
        
        {/* 特色功能卡片 */}
        <div className="flex flex-wrap gap-4 justify-center">
          <FunModelCard onClick={()=>{}} onFunArtClick={()=>{}} />
          <AgentCard onClick={()=>{}} />
          <FunAppCenterCard onClick={()=>{}} />
        </div>
      </div>
    </Layout>
  );
}

export default HomePage; 