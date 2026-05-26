import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";

import koCommon from "./locales/ko/common.json";
import enCommon from "./locales/en/common.json";
import koSettingPage from "./locales/ko/settingPage.json";
import enSettingPage from "./locales/en/settingPage.json";
import koSidebar from "./locales/ko/sidebar.json";
import enSidebar from "./locales/en/sidebar.json";
import koMainPage from "./locales/ko/mainPage.json";
import enMainPage from "./locales/en/mainPage.json";
import koLoginPage from "./locales/ko/loginPage.json";
import enLoginPage from "./locales/en/loginPage.json";
import koSignupPage from "./locales/ko/signupPage.json";
import enSignupPage from "./locales/en/signupPage.json";
import koFindAccountPage from "./locales/ko/findAccountPage.json";
import enFindAccountPage from "./locales/en/findAccountPage.json";
import koOauthPage from "./locales/ko/oauthPage.json";
import enOauthPage from "./locales/en/oauthPage.json";
import koAnalysisPanel from "./locales/ko/analysisPanel.json";
import enAnalysisPanel from "./locales/en/analysisPanel.json";
import koStocksPage from "./locales/ko/stocksPage.json";
import enStocksPage from "./locales/en/stocksPage.json";
import koFeature2Page from "./locales/ko/feature2Page.json";
import enFeature2Page from "./locales/en/feature2Page.json";
import koPortfolioPage from "./locales/ko/portfolioPage.json";
import enPortfolioPage from "./locales/en/portfolioPage.json";
import koFeatureIntro from "./locales/ko/featureIntro.json";
import enFeatureIntro from "./locales/en/featureIntro.json";
import koReportHeader from "./locales/ko/reportHeader.json";
import enReportHeader from "./locales/en/reportHeader.json";
import koEditProfilePage from "./locales/ko/editProfilePage.json";
import enEditProfilePage from "./locales/en/editProfilePage.json";
import koFinancialModal from "./locales/ko/financialModal.json";
import enFinancialModal from "./locales/en/financialModal.json";
import koSurveyPage from "./locales/ko/surveyPage.json";
import enSurveyPage from "./locales/en/surveyPage.json";
import koInvestLevelPage from "./locales/ko/investLevelPage.json";
import enInvestLevelPage from "./locales/en/investLevelPage.json";
import koBillingModal from "./locales/ko/billingModal.json";
import enBillingModal from "./locales/en/billingModal.json";
import koStockSearch from "./locales/ko/stockSearch.json";
import enStockSearch from "./locales/en/stockSearch.json";
import koMarketSnapshot from "./locales/ko/marketSnapshot.json";
import enMarketSnapshot from "./locales/en/marketSnapshot.json";
import koDictionaryPage from "./locales/ko/dictionaryPage.json";
import enDictionaryPage from "./locales/en/dictionaryPage.json";

export const SUPPORTED_LANGUAGES = ["ko", "en"] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

export const LANGUAGE_STORAGE_KEY = "qaima_language";

const resources = {
  ko: {
    common: koCommon,
    settingPage: koSettingPage,
    sidebar: koSidebar,
    mainPage: koMainPage,
    loginPage: koLoginPage,
    signupPage: koSignupPage,
    findAccountPage: koFindAccountPage,
    oauthPage: koOauthPage,
    analysisPanel: koAnalysisPanel,
    stocksPage: koStocksPage,
    feature2Page: koFeature2Page,
    portfolioPage: koPortfolioPage,
    featureIntro: koFeatureIntro,
    reportHeader: koReportHeader,
    editProfilePage: koEditProfilePage,
    financialModal: koFinancialModal,
    surveyPage: koSurveyPage,
    investLevelPage: koInvestLevelPage,
    billingModal: koBillingModal,
    stockSearch: koStockSearch,
    marketSnapshot: koMarketSnapshot,
    dictionaryPage: koDictionaryPage,
  },
  en: {
    common: enCommon,
    settingPage: enSettingPage,
    sidebar: enSidebar,
    mainPage: enMainPage,
    loginPage: enLoginPage,
    signupPage: enSignupPage,
    findAccountPage: enFindAccountPage,
    oauthPage: enOauthPage,
    analysisPanel: enAnalysisPanel,
    stocksPage: enStocksPage,
    feature2Page: enFeature2Page,
    portfolioPage: enPortfolioPage,
    featureIntro: enFeatureIntro,
    reportHeader: enReportHeader,
    editProfilePage: enEditProfilePage,
    financialModal: enFinancialModal,
    surveyPage: enSurveyPage,
    investLevelPage: enInvestLevelPage,
    billingModal: enBillingModal,
    stockSearch: enStockSearch,
    marketSnapshot: enMarketSnapshot,
    dictionaryPage: enDictionaryPage,
  },
} as const;

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: "ko",
    supportedLngs: SUPPORTED_LANGUAGES as unknown as string[],
    ns: [
      "common", "settingPage", "sidebar", "mainPage", "loginPage", "signupPage",
      "findAccountPage", "oauthPage", "analysisPanel", "stocksPage", "feature2Page",
      "portfolioPage", "featureIntro", "reportHeader", "editProfilePage", "financialModal",
      "surveyPage", "investLevelPage", "billingModal", "stockSearch", "marketSnapshot",
      "dictionaryPage",
    ],
    defaultNS: "common",
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: LANGUAGE_STORAGE_KEY,
      caches: ["localStorage"],
    },
  });

export default i18n;
