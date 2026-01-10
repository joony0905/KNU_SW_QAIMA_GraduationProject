// src/layout/MainLayout.tsx
import { Navigate } from "react-router-dom";
import Sidebar from "./Sidebar";

type Props = {
  children: React.ReactNode;
};

export default function MainLayout({ children }: Props) {
  //const token = localStorage.getItem("qaima_token");

  //if (!token) {
  //  return <Navigate to="/login" replace />;
  //}

  return (
    <div
      style={{
        display: "flex",
        minHeight: "100vh",
        background: "#f4f5f6",
      }}
    >
      <Sidebar />
      <main style={{ flex: 1, padding: "16px" }}>{children}</main>
    </div>
  );
}
