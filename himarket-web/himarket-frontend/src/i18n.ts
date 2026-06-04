import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// zh-CN resources
import enAgentDetail from './locales/en-US/agentDetail.json';
import enApiDetail from './locales/en-US/apiDetail.json';
import enChat from './locales/en-US/chat.json';
import enCoding from './locales/en-US/coding.json';
import enCommon from './locales/en-US/common.json';
import enConsumer from './locales/en-US/consumer.json';
import enEmptyState from './locales/en-US/emptyState.json';
import enGettingStarted from './locales/en-US/gettingStarted.json';
import enHeader from './locales/en-US/header.json';
import enLogin from './locales/en-US/login.json';
import enLoginPrompt from './locales/en-US/loginPrompt.json';
import enMcpDetail from './locales/en-US/mcpDetail.json';
import enModelDetail from './locales/en-US/modelDetail.json';
import enProductHeader from './locales/en-US/productHeader.json';
import enProfile from './locales/en-US/profile.json';
import enRegister from './locales/en-US/register.json';
import enSkillDetail from './locales/en-US/skillDetail.json';
import enSquare from './locales/en-US/square.json';
import enUserInfo from './locales/en-US/userInfo.json';
import enWelcome from './locales/en-US/welcome.json';
import enWorkerDetail from './locales/en-US/workerDetail.json';
import zhAgentDetail from './locales/zh-CN/agentDetail.json';
import zhApiDetail from './locales/zh-CN/apiDetail.json';
import zhChat from './locales/zh-CN/chat.json';
import zhCoding from './locales/zh-CN/coding.json';
import zhCommon from './locales/zh-CN/common.json';
import zhConsumer from './locales/zh-CN/consumer.json';
import zhEmptyState from './locales/zh-CN/emptyState.json';
import zhGettingStarted from './locales/zh-CN/gettingStarted.json';
import zhHeader from './locales/zh-CN/header.json';
import zhLogin from './locales/zh-CN/login.json';
import zhLoginPrompt from './locales/zh-CN/loginPrompt.json';
import zhMcpDetail from './locales/zh-CN/mcpDetail.json';
import zhModelDetail from './locales/zh-CN/modelDetail.json';
import zhProductHeader from './locales/zh-CN/productHeader.json';
import zhProfile from './locales/zh-CN/profile.json';
import zhRegister from './locales/zh-CN/register.json';
import zhSkillDetail from './locales/zh-CN/skillDetail.json';
import zhSquare from './locales/zh-CN/square.json';
import zhUserInfo from './locales/zh-CN/userInfo.json';
import zhWelcome from './locales/zh-CN/welcome.json';
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
      agentDetail: enAgentDetail,
      apiDetail: enApiDetail,
      chat: enChat,
      coding: enCoding,
      common: enCommon,
      consumer: enConsumer,
      emptyState: enEmptyState,
      gettingStarted: enGettingStarted,
      header: enHeader,
      login: enLogin,
      loginPrompt: enLoginPrompt,
      mcpDetail: enMcpDetail,
      modelDetail: enModelDetail,
      productHeader: enProductHeader,
      profile: enProfile,
      register: enRegister,
      skillDetail: enSkillDetail,
      square: enSquare,
      userInfo: enUserInfo,
      welcome: enWelcome,
      workerDetail: enWorkerDetail,
    },
    'zh-CN': {
      agentDetail: zhAgentDetail,
      apiDetail: zhApiDetail,
      chat: zhChat,
      coding: zhCoding,
      common: zhCommon,
      consumer: zhConsumer,
      emptyState: zhEmptyState,
      gettingStarted: zhGettingStarted,
      header: zhHeader,
      login: zhLogin,
      loginPrompt: zhLoginPrompt,
      mcpDetail: zhMcpDetail,
      modelDetail: zhModelDetail,
      productHeader: zhProductHeader,
      profile: zhProfile,
      register: zhRegister,
      skillDetail: zhSkillDetail,
      square: zhSquare,
      userInfo: zhUserInfo,
      welcome: zhWelcome,
      workerDetail: zhWorkerDetail,
    },
  },
});

// 语言变更时持久化
i18n.on('languageChanged', (lng) => {
  localStorage.setItem(STORAGE_KEY, lng);
});

export default i18n;
