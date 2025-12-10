import { Typography } from "antd";
import { Layout } from "../components/Layout";
import { useEffect } from "react";
import { getTokenFromCookie } from "../lib/utils";
import HomeModelCard from "../components/card/ModelCard";
import HomeMCPCard from "../components/card/HomeMcpCard";
import HomeAgentCard from "../components/card/AgentCard";
import HomeAPICard from "../components/card/APICard";
import HomeChatCard from "../components/card/ChatCard";
import TextType from "../components/TextType";

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
      <div className="h-[calc(100vh-64px)] grid items-center">
        <div className="h-[68%] flex flex-col">
          {/* 标题区域 */}
          <div className="mb-10">
            <Title level={1} className="text-5xl font-bold text-gray-900 mb-8">
              <span style={{
                background: "linear-gradient(249deg, #8FA1FF 0%, #2B2B3B 25%)",
                backgroundClip: "text",
                WebkitTextFillColor: "transparent",
              }}>
                Himarket 企业级AI开放平台
              </span>
            </Title>
            <Paragraph className="text-xl text-subTitle">
              <TextType
                text={["开箱即用，快速集成"]}
                typingSpeed={120}
                showCursor={true}
                cursorCharacter="_"
              />
            </Paragraph>
          </div>

          {/* 特色功能卡片 */}
          <div className="grid grid-cols-5 grid-rows-1 gap-3 flex-1">
            <div className="animate-[fadeInUp_0.6s_ease-out_0.1s_both]">
              <HomeModelCard />
            </div>
            <div className="animate-[fadeInUp_0.6s_ease-out_0.2s_both]">
              <HomeMCPCard />
            </div>
            <div className="animate-[fadeInUp_0.6s_ease-out_0.3s_both]">
              <HomeAgentCard />
            </div>
            <div className="animate-[fadeInUp_0.6s_ease-out_0.4s_both]">
              <HomeAPICard />
            </div>
            <div className="animate-[fadeInUp_0.6s_ease-out_0.5s_both]">
              <HomeChatCard />
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
}

export default HomePage; 