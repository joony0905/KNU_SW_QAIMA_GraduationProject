// src/App.tsx
import { Navigate, Route, Routes } from "react-router-dom";
import MainLayout from "./layout/MainLayout";
import LoginPage from "./pages/LoginPage";
import PingPage from "./pages/PingPage";
import StocksMockPage from "./pages/StocksMockPage";
import Feature2MockPage from "./pages/Feature2MockPage";
import PortfolioMockPage from "./pages/PortfolioMockPage";
import DictionaryMockPage from "./pages/DictionaryMockPage";

export default function App() {
  return (
    <Routes>
      {/* 로그인은 단독 화면 nav바 없이*/}
      <Route path="/login" element={<LoginPage />} />

      {/* 나머지는 레이아웃으로 감싼다 */}
      <Route
        path="/"
        element={
          <MainLayout>
            <PingPage />
          </MainLayout>
        }
      />
      <Route
        path="/ping"
        element={
          <MainLayout>
            <PingPage />
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

      {/* 없는 주소는 로그인으로 */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
