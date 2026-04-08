import { Button } from 'antd';
import { MessageSquare, Code2, Sparkles, Zap, Bot, Globe } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '../hooks/useAuth';

interface WelcomeViewProps {
  type: 'chat' | 'coding';
}

const chatFeatures = [
  {
    desc: '支持多种主流 AI 大模型，一站式智能问答体验',
    icon: <Bot size={20} />,
    title: 'AI 多模型对话',
  },
  {
    desc: '同时发送多模型对比回答，选择最优结果',
    icon: <Sparkles size={20} />,
    title: '多模型对比',
  },
  {
    desc: '集成 MCP 服务能力，AI 可调用外部工具增强回答',
    icon: <Globe size={20} />,
    title: 'MCP 工具集成',
  },
];

const codingFeatures = [
  {
    desc: '通过自然语言描述需求，AI 自动生成代码方案',
    icon: <Code2 size={20} />,
    title: 'AI 辅助编程',
  },
  {
    desc: '代码在安全沙箱中实时运行，即时查看执行结果',
    icon: <Zap size={20} />,
    title: '沙箱执行',
  },
  {
    desc: '与 AI 持续对话迭代代码，逐步完善项目',
    icon: <MessageSquare size={20} />,
    title: '交互式对话',
  },
];

export function WelcomeView({ type }: WelcomeViewProps) {
  const { login } = useAuth();
  const navigate = useNavigate();

  const isChatType = type === 'chat';
  const title = isChatType ? 'HiChat' : 'HiCoding';
  const subtitle = isChatType
    ? '与 AI 模型智能对话，探索无限可能'
    : 'AI 驱动的智能编程助手，让代码触手可及';
  const features = isChatType ? chatFeatures : codingFeatures;
  const ctaText = isChatType ? '登录后开始对话' : '登录后开始编码';

  return (
    <div className="flex flex-col items-center justify-center py-16 px-4">
      <div className="max-w-2xl w-full text-center">
        <h1 className="text-4xl font-bold mb-3 bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
          {title}
        </h1>
        <p className="text-gray-500 text-lg mb-10">{subtitle}</p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5 mb-10">
          {features.map((f, i) => (
            <div
              className="bg-white/60 backdrop-blur-sm rounded-xl p-5 text-left border border-gray-100 hover:shadow-md transition-shadow"
              key={i}
            >
              <div className="text-blue-500 mb-3">{f.icon}</div>
              <div className="font-medium text-gray-800 mb-1">{f.title}</div>
              <div className="text-gray-500 text-sm">{f.desc}</div>
            </div>
          ))}
        </div>

        <div className="flex items-center justify-center gap-4">
          <Button onClick={() => login()} size="large" type="primary">
            {ctaText}
          </Button>
          <Button onClick={() => navigate('/register')} size="large">
            注册新账号
          </Button>
        </div>
      </div>
    </div>
  );
}
