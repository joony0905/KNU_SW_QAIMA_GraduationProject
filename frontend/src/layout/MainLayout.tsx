// src/layout/MainLayout.tsx
import Sidebar from "./Sidebar";
import { BillingProvider } from "../contexts/BillingContext";

type Props = {
  children: React.ReactNode;
};

export default function MainLayout({ children }: Props) {
  return (
    <BillingProvider>
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
    </BillingProvider>
  );
}
