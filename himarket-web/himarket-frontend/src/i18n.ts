import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// zh-CN resources
import enCommon from './locales/en-US/common.json';
import enEmptyState from './locales/en-US/emptyState.json';
import enGettingStarted from './locales/en-US/gettingStarted.json';
import enHeader from './locales/en-US/header.json';
import enLogin from './locales/en-US/login.json';
import enLoginPrompt from './locales/en-US/loginPrompt.json';
import enProfile from './locales/en-US/profile.json';
import enRegister from './locales/en-US/register.json';
import enSkillDetail from './locales/en-US/skillDetail.json';
import enSquare from './locales/en-US/square.json';
import enUserInfo from './locales/en-US/userInfo.json';
import enWorkerDetail from './locales/en-US/workerDetail.json';
import zhCommon from './locales/zh-CN/common.json';
import zhEmptyState from './locales/zh-CN/emptyState.json';
import zhGettingStarted from './locales/zh-CN/gettingStarted.json';
import zhHeader from './locales/zh-CN/header.json';
import zhLogin from './locales/zh-CN/login.json';
import zhLoginPrompt from './locales/zh-CN/loginPrompt.json';
import zhProfile from './locales/zh-CN/profile.json';
import zhRegister from './locales/zh-CN/register.json';
import zhSkillDetail from './locales/zh-CN/skillDetail.json';
import zhSquare from './locales/zh-CN/square.json';
import zhUserInfo from './locales/zh-CN/userInfo.json';
import zhWorkerDetail from './locales/zh-CN/workerDetail.json';

// en-US resources

const STORAGE_KEY = 'i18nLng';

i18n.use(initReactI18next).init({
  defaultNS: 'common',
  fallbackLng: 'zh-CN',
  interpolation: { escapeValue: false },
  lng: localStorage.getItem(STORAGE_KEY) || 'zh-CN',
  resources: {
    'en-US': {
      common: enCommon,
      emptyState: enEmptyState,
      gettingStarted: enGettingStarted,
      header: enHeader,
      login: enLogin,
      loginPrompt: enLoginPrompt,
      profile: enProfile,
      register: enRegister,
      skillDetail: enSkillDetail,
      square: enSquare,
      userInfo: enUserInfo,
      workerDetail: enWorkerDetail,
    },
    'zh-CN': {
      common: zhCommon,
      emptyState: zhEmptyState,
      gettingStarted: zhGettingStarted,
      header: zhHeader,
      login: zhLogin,
      loginPrompt: zhLoginPrompt,
      profile: zhProfile,
      register: zhRegister,
      skillDetail: zhSkillDetail,
      square: zhSquare,
      userInfo: zhUserInfo,
      workerDetail: zhWorkerDetail,
    },
  },
});

// 语言变更时持久化
i18n.on('languageChanged', (lng) => {
  localStorage.setItem(STORAGE_KEY, lng);
});

export default i18n;
