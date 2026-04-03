import i18n from "i18next";
import { initReactI18next } from "react-i18next";

// zh-CN resources
import zhCommon from "./locales/zh-CN/common.json";
import zhHeader from "./locales/zh-CN/header.json";
import zhLogin from "./locales/zh-CN/login.json";
import zhRegister from "./locales/zh-CN/register.json";
import zhSquare from "./locales/zh-CN/square.json";
import zhProfile from "./locales/zh-CN/profile.json";
import zhUserInfo from "./locales/zh-CN/userInfo.json";
import zhEmptyState from "./locales/zh-CN/emptyState.json";
import zhGettingStarted from "./locales/zh-CN/gettingStarted.json";
import zhLoginPrompt from "./locales/zh-CN/loginPrompt.json";
import zhSkillDetail from "./locales/zh-CN/skillDetail.json";
import zhWorkerDetail from "./locales/zh-CN/workerDetail.json";

// en-US resources
import enCommon from "./locales/en-US/common.json";
import enHeader from "./locales/en-US/header.json";
import enLogin from "./locales/en-US/login.json";
import enRegister from "./locales/en-US/register.json";
import enSquare from "./locales/en-US/square.json";
import enProfile from "./locales/en-US/profile.json";
import enUserInfo from "./locales/en-US/userInfo.json";
import enEmptyState from "./locales/en-US/emptyState.json";
import enGettingStarted from "./locales/en-US/gettingStarted.json";
import enLoginPrompt from "./locales/en-US/loginPrompt.json";
import enSkillDetail from "./locales/en-US/skillDetail.json";
import enWorkerDetail from "./locales/en-US/workerDetail.json";

const STORAGE_KEY = "i18nLng";

i18n.use(initReactI18next).init({
  resources: {
    "zh-CN": {
      common: zhCommon,
      header: zhHeader,
      login: zhLogin,
      register: zhRegister,
      square: zhSquare,
      profile: zhProfile,
      userInfo: zhUserInfo,
      emptyState: zhEmptyState,
      gettingStarted: zhGettingStarted,
      loginPrompt: zhLoginPrompt,
      skillDetail: zhSkillDetail,
      workerDetail: zhWorkerDetail,
    },
    "en-US": {
      common: enCommon,
      header: enHeader,
      login: enLogin,
      register: enRegister,
      square: enSquare,
      profile: enProfile,
      userInfo: enUserInfo,
      emptyState: enEmptyState,
      gettingStarted: enGettingStarted,
      loginPrompt: enLoginPrompt,
      skillDetail: enSkillDetail,
      workerDetail: enWorkerDetail,
    },
  },
  lng: localStorage.getItem(STORAGE_KEY) || "zh-CN",
  fallbackLng: "zh-CN",
  defaultNS: "common",
  interpolation: { escapeValue: false },
});

// 语言变更时持久化
i18n.on("languageChanged", lng => {
  localStorage.setItem(STORAGE_KEY, lng);
});

export default i18n;
