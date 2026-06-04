import { ReloadOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Tip } from '../icon';

const questionKeys = [
  'reactHooks',
  'typescriptType',
  'tailwindBestPractices',
  'viteEnv',
  'reactRouter',
  'customHook',
  'antdTheme',
  'bundleSize',
  'stateReducer',
];

interface SuggestedQuestionsProps {
  onSelectQuestion: (question: string) => void;
}

function getRandomQuestions(count: number): string[] {
  const shuffled = [...questionKeys].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, count);
}

export function SuggestedQuestions({ onSelectQuestion }: SuggestedQuestionsProps) {
  const { t } = useTranslation('chat');
  const [displayedQuestions, setDisplayedQuestions] = useState(() => {
    return getRandomQuestions(3);
  });
  const [isRefreshing, setIsRefreshing] = useState(false);

  const handleRefresh = () => {
    setIsRefreshing(true);
    setTimeout(() => {
      setDisplayedQuestions(getRandomQuestions(3));
      setIsRefreshing(false);
    }, 300);
  };

  return (
    <div>
      {/* 标题和刷新按钮 */}
      <div className="mb-4 flex items-center gap-2">
        <h3 className="text-sm font-semibold text-gray-600">{t('suggestions.title')}</h3>
        <button
          className="rounded-full p-1.5 text-gray-500 transition-all duration-200 hover:bg-white/80 hover:text-gray-800"
          onClick={handleRefresh}
          title={t('suggestions.refresh')}
        >
          <ReloadOutlined
            className={`text-xs transition-transform duration-300 ${isRefreshing ? 'animate-spin' : ''}`}
          />
        </button>
      </div>

      {/* 问题列表 */}
      <div className="mx-auto flex max-w-[720px] flex-col gap-2.5">
        {displayedQuestions.map((questionKey, index) => {
          const question = t(`suggestions.questions.${questionKey}`);

          return (
            <button
              className={`
              min-h-[54px] cursor-pointer rounded-[12px] px-4 py-3
              border border-[#DDE5F0]
              transition-all duration-300 ease-in-out w-full text-left
              hover:-translate-y-0.5 hover:border-colorPrimary/30 hover:bg-white hover:shadow-[0_8px_22px_rgba(37,56,88,0.05)]
              active:scale-[0.98]
              group
              ${isRefreshing ? 'opacity-0 translate-y-2' : 'opacity-100 translate-y-0'}
            `}
              key={`${questionKey}-${index}`}
              onClick={() => onSelectQuestion(question)}
              style={{
                animationDelay: `${index * 100}ms`,
                backgroundColor: 'rgba(255, 255, 255, 0.72)',
              }}
              type="button"
            >
              <p className="flex items-center gap-2 text-sm leading-6 text-gray-700 transition-colors duration-300 group-hover:text-colorPrimary">
                <Tip className="flex-shrink-0 fill-colorPrimary" />
                {question}
              </p>
            </button>
          );
        })}
      </div>
    </div>
  );
}
