import { GlobalOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export function LanguageSwitcher() {
  const { i18n } = useTranslation();

  const toggle = () => {
    const next = i18n.language === 'zh-CN' ? 'en-US' : 'zh-CN';
    i18n.changeLanguage(next);
  };

  const label = i18n.language === 'zh-CN' ? 'ZH' : 'EN';

  return (
    <button
      aria-label="Switch language"
      className="group inline-flex h-8 items-center gap-1.5 rounded-full border border-white/45 bg-white/35 px-2.5 pr-3 text-sm font-medium text-gray-500 shadow-[0_4px_14px_rgba(15,23,42,0.035)] backdrop-blur-md transition-all duration-300 hover:border-white/65 hover:bg-white/55 hover:text-gray-800 hover:shadow-[0_8px_20px_rgba(15,23,42,0.055)] active:scale-[0.98]"
      onClick={toggle}
      type="button"
    >
      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-gray-900/[0.025] text-gray-500 transition-colors duration-300 group-hover:text-gray-700">
        <GlobalOutlined className="text-[13px]" />
      </span>
      <span className="font-mono text-[12px] leading-none tracking-normal">{label}</span>
    </button>
  );
}
