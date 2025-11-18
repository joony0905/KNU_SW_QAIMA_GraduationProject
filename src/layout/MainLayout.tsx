// src/layout/MainLayout.tsx
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Sidebar from "./Sidebar";

type Props = {
  children: React.ReactNode;
};

export default function MainLayout({ children }: Props) {
  const navigate = useNavigate();

  // 아주 간단한 가짜 인증
  useEffect(() => {
    const token = localStorage.getItem("qaima_token");
    if (!token) {
      navigate("/login");
    }
  }, [navigate]);

  return (
    <div style={{ display: "flex", minHeight: "100vh", background: "#f4f5f6" }}>
      <Sidebar />
      <main style={{ flex: 1, padding: "16px" }}>{children}</main>
    </div>
  );
}
