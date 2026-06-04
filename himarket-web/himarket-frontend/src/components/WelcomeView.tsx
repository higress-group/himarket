import { Button } from 'antd';
import { MessageSquare, Code2, Sparkles, Zap, Bot, Globe } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '../hooks/useAuth';

interface WelcomeViewProps {
  type: 'chat' | 'coding';
}

const chatFeatures = [
  {
    descKey: 'chat.features.modelConversation.desc',
    icon: <Bot size={20} />,
    titleKey: 'chat.features.modelConversation.title',
  },
  {
    descKey: 'chat.features.modelCompare.desc',
    icon: <Sparkles size={20} />,
    titleKey: 'chat.features.modelCompare.title',
  },
  {
    descKey: 'chat.features.mcpIntegration.desc',
    icon: <Globe size={20} />,
    titleKey: 'chat.features.mcpIntegration.title',
  },
];

const codingFeatures = [
  {
    descKey: 'coding.features.aiCoding.desc',
    icon: <Code2 size={20} />,
    titleKey: 'coding.features.aiCoding.title',
  },
  {
    descKey: 'coding.features.sandbox.desc',
    icon: <Zap size={20} />,
    titleKey: 'coding.features.sandbox.title',
  },
  {
    descKey: 'coding.features.interactive.desc',
    icon: <MessageSquare size={20} />,
    titleKey: 'coding.features.interactive.title',
  },
];

export function WelcomeView({ type }: WelcomeViewProps) {
  const { login } = useAuth();
  const navigate = useNavigate();
  const { t } = useTranslation('welcome');

  const isChatType = type === 'chat';
  const title = isChatType ? 'HiChat' : 'HiCoding';
  const subtitle = isChatType ? t('chat.subtitle') : t('coding.subtitle');
  const features = isChatType ? chatFeatures : codingFeatures;
  const ctaText = isChatType ? t('chat.cta') : t('coding.cta');

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
              className="bg-white/60 backdrop-blur-sm rounded-[10px] p-5 text-left border border-gray-100 hover:shadow-md transition-shadow"
              key={i}
            >
              <div className="text-blue-500 mb-3">{f.icon}</div>
              <div className="font-medium text-gray-800 mb-1">{t(f.titleKey)}</div>
              <div className="text-gray-500 text-sm">{t(f.descKey)}</div>
            </div>
          ))}
        </div>

        <div className="flex items-center justify-center gap-4">
          <Button onClick={() => login()} size="large" type="primary">
            {ctaText}
          </Button>
          <Button onClick={() => navigate('/register')} size="large">
            {t('register')}
          </Button>
        </div>
      </div>
    </div>
  );
}
