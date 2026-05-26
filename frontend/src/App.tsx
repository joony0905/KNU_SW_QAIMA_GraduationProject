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

export default function App() {
  const [authReady, setAuthReady] = useState(false);

  useEffect(() => {
    let mounted = true;
    bootstrapAccessToken(API_BASE_URL).finally(() => {
      if (mounted) setAuthReady(true);
    });
    return () => {
      mounted = false;
    };
  }, []);

  if (!authReady) {
    return null;
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
