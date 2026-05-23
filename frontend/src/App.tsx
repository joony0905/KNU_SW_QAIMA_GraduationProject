// src/App.tsx
import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import MainLayout from "./layout/MainLayout";
import LoginPage from "./pages/LoginPage";
import OAuth2SuccessPage from "./pages/OAuth2SuccessPage";
import FindAccountPage from "./pages/FindAccountPage";
import RequireAuth from "./components/RequireAuth";
import ScrollToTop from "./components/ScrollToTop";
import MainPage from "./pages/MainPage";
import StocksMockPage from "./pages/StocksMockPage";
import Feature2MockPage from "./pages/Feature2MockPage";
import PortfolioMockPage from "./pages/PortfolioMockPage";
import DictionaryMockPage from "./pages/DictionaryMockPage";
import SignupPage from "./pages/SignupPage";
import SocialProfileCompletePage from "./pages/SocialProfileCompletePage";
import SettingPage from "./pages/SettingPage";
import EditProfilePage from "./pages/EditProfilePage";
import SurveyPage from "./pages/SurveyPage";
import InvestLevelSurveyPage from "./pages/InvestLevelSurveyPage";
import { bootstrapAccessToken } from "./api/tokenStore";

const API_BASE_URL = "http://localhost:8080/api/v1";
const MOBILE_MEDIA_QUERY = "(max-width: 767px)";
const FORCE_DESKTOP_KEY = "qaima_force_desktop";

function MobilePcNotice({ onContinue }: { onContinue: () => void }) {
  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-5">
      <div className="w-full max-w-sm rounded-2xl border border-line bg-surface shadow-card px-6 py-7 text-center">
        <h1 className="text-xl font-bold text-ink tracking-tight">
          현재는 PC 환경만 제공합니다
        </h1>
        <p className="mt-3 text-sm leading-6 text-ink-3">
          현재 QAIMA는 PC 화면 기준으로 제공됩니다. 원활한 이용을 위해 데스크톱 브라우저에서 접속해주세요.
        </p>
        <button
          type="button"
          onClick={onContinue}
          className="mt-6 w-full py-3 rounded-lg bg-accent text-white text-sm font-semibold hover:opacity-90 transition-opacity"
        >
          PC버전으로 보기
        </button>
      </div>
    </div>
  );
}

export default function App() {
  const [authReady, setAuthReady] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [forceDesktop, setForceDesktop] = useState(
    () => sessionStorage.getItem(FORCE_DESKTOP_KEY) === "true",
  );

  useEffect(() => {
    let mounted = true;
    bootstrapAccessToken(API_BASE_URL).finally(() => {
      if (mounted) setAuthReady(true);
    });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    const media = window.matchMedia(MOBILE_MEDIA_QUERY);
    const update = () => setIsMobile(media.matches);
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  if (!authReady) {
    return null;
  }

  if (isMobile && !forceDesktop) {
    return (
      <MobilePcNotice
        onContinue={() => {
          sessionStorage.setItem(FORCE_DESKTOP_KEY, "true");
          setForceDesktop(true);
        }}
      />
    );
  }

  return (
    <>
      <ScrollToTop />
      <Routes>
      <Route
        path="/login"
        element={
          <MainLayout>
            <LoginPage />
          </MainLayout>
        }
      />

      <Route
        path="/login/oauth2/success"
        element={
          <MainLayout>
            <OAuth2SuccessPage />
          </MainLayout>
        }
      />

      <Route
        path="/find-account"
        element={
          <MainLayout>
            <FindAccountPage />
          </MainLayout>
        }
      />

      <Route
        path="/"
        element={
          <MainLayout>
            <MainPage />
          </MainLayout>
        }
      />

      <Route
        path="/signup"
        element={
          <MainLayout>
            <SignupPage />
          </MainLayout>
        }
      />

      <Route
        path="/signup/social-complete"
        element={
          <MainLayout>
            <RequireAuth>
              <SocialProfileCompletePage />
            </RequireAuth>
          </MainLayout>
        }
      />

      <Route
        path="/main"
        element={
          <MainLayout>
            <MainPage />
          </MainLayout>
        }
      />
      <Route
        path="/feature/1"
        element={
          <MainLayout>
            <StocksMockPage />
          </MainLayout>
        }
      />
      <Route
        path="/feature/2"
        element={
          <MainLayout>
            <Feature2MockPage />
          </MainLayout>
        }
      />
      <Route
        path="/feature/3"
        element={
          <MainLayout>
            <PortfolioMockPage />
          </MainLayout>
        }
      />
      <Route
        path="/feature/4"
        element={
          <MainLayout>
            <DictionaryMockPage />
          </MainLayout>
        }
      />
      <Route
        path="/setting"
        element={
          <MainLayout>
            <SettingPage />
          </MainLayout>
        }
      />
      <Route
        path="/setting/edit"
        element={
          <MainLayout>
            <EditProfilePage />
          </MainLayout>
        }
      />
      <Route
        path="/survey"
        element={
          <MainLayout>
            <SurveyPage />
          </MainLayout>
        }
      />
      <Route
        path="/invest-level-survey"
        element={
          <MainLayout>
            <RequireAuth>
              <InvestLevelSurveyPage />
            </RequireAuth>
          </MainLayout>
        }
      />

      {/* 없는 주소는 로그인으로 */}
      <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </>
  );
}
