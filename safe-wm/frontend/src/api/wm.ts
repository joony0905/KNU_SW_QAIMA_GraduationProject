import type { AnalysisResponse, CustomerAlert, CustomerSummary, DashboardSummary } from "../types/wm";

const API_BASE_URL = import.meta.env.VITE_SAFE_WM_API_BASE ?? "http://127.0.0.1:8100";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json", ...(options?.headers ?? {}) },
    ...options,
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function fetchRiskSummary() {
  return request<DashboardSummary>("/api/wm/dashboard/risk-summary");
}

export function fetchHighRiskCustomers() {
  return request<{ customers: CustomerSummary[] }>("/api/wm/dashboard/high-risk-customers");
}

export function fetchCustomerAlerts() {
  return request<{ alerts: CustomerAlert[] }>("/api/wm/dashboard/customer-alerts");
}

export function analyzeCustomer(customerId: string, includeDebug: boolean) {
  return request<AnalysisResponse>(`/api/wm/customers/${customerId}/analysis`, {
    method: "POST",
    body: JSON.stringify({ includeDebug }),
  });
}
