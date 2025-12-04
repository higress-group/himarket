import { Typography } from "antd";
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
        <div className="mb-10">
          <Title level={1} className="text-5xl font-bold text-gray-900 mb-8">
            <span style={{
              background: "linear-gradient(249deg, #8FA1FF 0%, #2B2B3B 25%)",
              backgroundClip: "text",
              WebkitTextFillColor: "transparent",
            }}>
              HiMarket 企业级AI开放平台
            </span>
          </Title>
          <Paragraph className="text-xl text-subTitle">
            开箱即用，快速集成
          </Paragraph>
        </div>

        {/* 特色功能卡片 */}
        <div className="flex flex-wrap gap-4 justify-center">
          <FunModelCard onClick={() => { }} onFunArtClick={() => { }} />
          <AgentCard onClick={() => { }} />
          <FunAppCenterCard onClick={() => { }} />
        </div>
      </div>
    </Layout>
  );
}

export default HomePage; 