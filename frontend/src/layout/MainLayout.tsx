// src/layout/MainLayout.tsx
import type { ReactNode } from "react";
import Sidebar from "./Sidebar";
import { BillingProvider } from "../contexts/BillingContext";
import { ThemeProvider } from "../hooks/useTheme";

type Props = {
  children: ReactNode;
};

export default function MainLayout({ children }: Props) {
  return (
    <ThemeProvider>
      <BillingProvider>
        <div className="flex min-h-screen bg-bg text-ink">
          <Sidebar />
          <main className="flex-1">{children}</main>
        </div>
      </BillingProvider>
    </ThemeProvider>
  );
}
