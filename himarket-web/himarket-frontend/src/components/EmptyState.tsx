import { InboxOutlined } from "@ant-design/icons";

const TYPE_DESCRIPTIONS: Record<string, { title: string; desc: string }> = {
  MODEL_API: {
    title: "AI 模型市场",
    desc: "这里汇聚各类 AI 大模型服务，开发者可以发现和订阅适合自己的模型 API。",
  },
  MCP_SERVER: {
    title: "MCP 服务市场",
    desc: "这里是 MCP 服务市场，开发者可以发现和订阅各类 MCP 服务，扩展 AI 的能力边界。",
  },
  AGENT_API: {
    title: "智能体市场",
    desc: "这里是 AI 智能体市场，开发者可以发现并集成各类智能体服务。",
  },
  REST_API: {
    title: "API 市场",
    desc: "这里是 REST API 市场，开发者可以发现和使用各类开放 API 服务。",
  },
  AGENT_SKILL: {
    title: "Skill 市场",
    desc: "这里是 Agent Skill 市场，开发者可以发现和下载各类 AI Agent 技能包。",
  },
};

interface EmptyStateProps {
  productType: string;
}

export function EmptyState({ productType }: EmptyStateProps) {
  const info = TYPE_DESCRIPTIONS[productType] || {
    title: "产品市场",
    desc: "这里是产品市场，敬请期待更多内容。",
  };

  return (
    <div className="col-span-full flex flex-col items-center justify-center py-24 text-center">
      <InboxOutlined className="text-5xl text-gray-300 mb-4" />
      <h3 className="text-lg font-medium text-gray-600 mb-2">{info.title}</h3>
      <p className="text-gray-400 max-w-md mb-3">{info.desc}</p>
      <p className="text-gray-400 text-sm">暂无产品，敬请期待</p>
    </div>
  );
}
