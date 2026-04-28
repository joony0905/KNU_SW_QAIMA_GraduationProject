import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import BillingModal from "../components/BillingModal";

type BillingContextValue = {
  openBilling: () => void;
  closeBilling: () => void;
  isOpen: boolean;
};

const BillingContext = createContext<BillingContextValue | null>(null);

export function BillingProvider({ children }: { children: ReactNode }) {
  const [isOpen, setIsOpen] = useState(false);

  const openBilling = useCallback(() => setIsOpen(true), []);
  const closeBilling = useCallback(() => setIsOpen(false), []);

  return (
    <BillingContext.Provider value={{ openBilling, closeBilling, isOpen }}>
      {children}
      <BillingModal isOpen={isOpen} onClose={closeBilling} />
    </BillingContext.Provider>
  );
}

export function useBilling(): BillingContextValue {
  const ctx = useContext(BillingContext);
  if (!ctx) {
    throw new Error("useBilling must be used inside BillingProvider");
  }
  return ctx;
}
