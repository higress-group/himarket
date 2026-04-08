import { GlobalOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export function LanguageSwitcher() {
  const { i18n } = useTranslation();

  const toggle = () => {
    const next = i18n.language === 'zh-CN' ? 'en-US' : 'zh-CN';
    i18n.changeLanguage(next);
  };

  // 显示要切换到的目标语言：当前中文时显示 EN，当前英文时显示 中文
  const label = i18n.language === 'zh-CN' ? 'EN' : '中文';

  return (
    <button
      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-gray-500 hover:bg-white/60 hover:text-gray-900 hover:shadow-sm transition-all duration-300 cursor-pointer"
      onClick={toggle}
    >
      <GlobalOutlined className="text-base" />
      <span>{label}</span>
    </button>
  );
}
