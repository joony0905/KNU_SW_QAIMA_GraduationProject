// src/layout/MainLayout.tsx
import type { ReactNode } from "react";
import Sidebar from "./Sidebar";
import PageTransition from "../components/PageTransition";
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
          <main className="flex-1 pt-12 md:pt-0">
            <PageTransition>{children}</PageTransition>
          </main>
        </div>
      </BillingProvider>
    </ThemeProvider>
  );
}
