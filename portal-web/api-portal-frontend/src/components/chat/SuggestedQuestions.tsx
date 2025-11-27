import { useState } from "react";
import { ReloadOutlined } from "@ant-design/icons";
import { Tip } from "../icon";

// Mock 推荐问题数据
const allQuestions = [
  "如何使用 React Hooks 优化组件性能？",
  "TypeScript 中 interface 和 type 的区别是什么？",
  "Tailwind CSS 的最佳实践有哪些？",
  "如何在 Vite 中配置环境变量？",
  "React Router v6 的主要变化有哪些？",
  "如何实现一个自定义的 React Hook？",
  "Ant Design 如何进行主题定制？",
  "如何优化 React 应用的打包体积？",
  "useState 和 useReducer 的使用场景有什么区别？",
];

interface SuggestedQuestionsProps {
  onSelectQuestion: (question: string) => void;
}

export function SuggestedQuestions({ onSelectQuestion }: SuggestedQuestionsProps) {
  const [displayedQuestions, setDisplayedQuestions] = useState(() => {
    return getRandomQuestions(3);
  });
  const [isRefreshing, setIsRefreshing] = useState(false);

  function getRandomQuestions(count: number): string[] {
    const shuffled = [...allQuestions].sort(() => Math.random() - 0.5);
    return shuffled.slice(0, count);
  }

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
      <div className="flex items-center gap-2 mb-4">
        <h3 className="text-sm font-medium text-[#737373]">推荐问题</h3>
        <button
          onClick={handleRefresh}
          className="p-1.5 text-[#737373] hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all duration-200"
          title="刷新推荐"
        >
          <ReloadOutlined className={`text-xs transition-transform duration-300 ${isRefreshing ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* 问题列表 */}
      <div className="space-y-3">
        {displayedQuestions.map((question, index) => (
          <div
            key={`${question}-${index}`}
            onClick={() => onSelectQuestion(question)}
            className={`
              px-4 py-2.5 rounded-2xl cursor-pointer
              border border-[#E0E7FF]
              transition-all duration-300 ease-in-out
              hover:bg-white hover:shadow-md hover:scale-[1.02] hover:border-[#C7D2FE]
              active:scale-[0.98]
              group
              ${isRefreshing ? 'opacity-0 translate-y-2' : 'opacity-100 translate-y-0'}
            `}
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.6)',
              animationDelay: `${index * 100}ms`,
            }}
          >
            <p className="flex items-center gap-2 text-sm text-gray-700 leading-relaxed group-hover:text-colorPrimary transition-colors duration-300">
              <Tip className="fill-colorPrimary" />
              {question}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
