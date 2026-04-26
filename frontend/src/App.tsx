// src/App.tsx
import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import MainLayout from "./layout/MainLayout";
import LoginPage from "./pages/LoginPage";
import MainPage from "./pages/MainPage";
import StocksMockPage from "./pages/StocksMockPage";
import Feature2MockPage from "./pages/Feature2MockPage";
import PortfolioMockPage from "./pages/PortfolioMockPage";
import DictionaryMockPage from "./pages/DictionaryMockPage";
import SignupPage from "./pages/SignupPage";
import SettingPage from "./pages/SettingPage";
import SurveyPage from "./pages/SurveyPage";
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
    <Routes>
      {/* 로그인은 단독 화면 nav바 없이*/}
      <Route path="/login" element={<LoginPage />} />

      {/* 나머지는 레이아웃으로 감싼다 */}
      <Route
        path="/"
        element={
          <MainLayout>
            <MainPage />
          </MainLayout>
        }
      />
      
      <Route path="/signup" element={<SignupPage />} />

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
        path="/survey"
        element={
          <MainLayout>
            <SurveyPage />
          </MainLayout>
        }
      />

      {/* 없는 주소는 로그인으로 */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
